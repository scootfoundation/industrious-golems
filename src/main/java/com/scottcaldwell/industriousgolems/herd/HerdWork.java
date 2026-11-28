package com.scottcaldwell.industriousgolems.herd;

import net.minecraft.world.entity.animal.pig.Pig;
import com.scottcaldwell.industriousgolems.behavior.Claims;
import com.scottcaldwell.industriousgolems.behavior.TaskState;
import java.util.Set;
import com.scottcaldwell.industriousgolems.behavior.ClipRun;
import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.Approach;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.needs.Job;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import com.scottcaldwell.industriousgolems.golem.Stand;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The herder's work, deliberately slower and more limited than a player: it manages the animals inside an enclosed
 * pen near its job site, keeping the population in a density band. Above the band it culls the most numerous
 * species (never below a breeding pair); below it, it feeds adults so they breed. It also shears ready sheep and
 * milks cows. It works bare-handed; milking uses up a bucket and breeding a unit of feed, and a job is skipped
 * without its material.
 */
public class HerdWork extends TaskRunner<HerdPlan> {

    /**
     * A pen holds one animal per this many cells: it culls above that, and keeps breeding while one animal fewer would still not be
     * over it. So at the pen's capacity plus one it does both, which is the point: a farm that breeds and culls continuously. In a
     * 16-cell pen (capacity four) it breeds up to five and culls from five.
     */
    static final double CELLS_PER_ANIMAL = 3.5;
    /** Never cull a species down to fewer adults than this. */
    static final int MIN_ADULTS = 2;
    /** Below this many animals it always breeds, however small the pen. */
    static final int ALWAYS_BREED_BELOW = 3;
    static final long MILK_COOLDOWN = 6000;
    private static final double REACH_SQR = 4.0 * 4.0;
    /** How far above or below the animal a golem may be and still set it where the clip wants it. */
    private static final double LEVEL = 0.6;
    private static final int APPROACH_TICKS = 400;
    private static final int IGNORE_TICKS = 600; // short: animals move

    private enum State { WALK, STAGE, PLAY, FOLLOW_THROUGH, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("herd-work", "Herder: cull, shear, milk, feed",
            "Terracotta golem. Walks to an animal in a pen, stands where the clip wants it (the animal is held where the arms go), "
                    + "and plays the clip for the job; the deed itself lands at a moment in the clip.", State.class)
            .start(State.WALK)
            .state(State.WALK, "Walking towards the animal, until within 4 blocks; where the pen has a gate, until inside it with the gate shut.")
            .state(State.STAGE, "Within reach: choosing and settling onto the spot (one of eight facings) the clip is written for.")
            .state(State.PLAY, "Clip playing, the animal frozen in place; the deed has not happened yet.")
            .state(State.FOLLOW_THROUGH, "The deed is done (culled, sheared, milked or fed); the clip is finishing.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.WALK, State.STAGE, "within 4 blocks of the animal (inside the pen, gate shut, if it has a gate)")
            .edge(State.WALK, State.DONE, "the animal is gone, or 20 s without getting nearer (ignored for 30 s)")
            .edge(State.STAGE, State.WALK, "the animal wandered out of reach, or the golem is no longer shut in with it")
            .edge(State.STAGE, State.PLAY, "standing on the spot facing the right way (milking and chickens need no spot)")
            .edge(State.STAGE, State.DONE, "no room to stage it (does the job without the show), the animal is gone, or 20 s without getting nearer")
            .edge(State.PLAY, State.FOLLOW_THROUGH, "the clip reaches the deed's moment")
            .edge(State.PLAY, State.DONE, "the animal vanished first, or the clip ends")
            .edge(State.FOLLOW_THROUGH, State.DONE, "the clip ends");

    private final Machine.Run<State> machine = GRAPH.run();
    private final Approach approach = new Approach(APPROACH_TICKS);
    private ClipRun clip;
    private Vec3 spot;
    private Animal frozen;
    /** Where the animal is held while the clip plays; null when it is left alone. */
    private Vec3 held;
    private float yaw;

    public HerdWork() {
        super(HerdPlan.class, 600);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /**
     * The herding jobs the golem could do, best first, pen by pen. Cull, shear, milk, breed: each is offered if the pen calls for it, as
     * doable now when the hand already holds what it takes (nothing for culling and shearing, a bucket for milking, feed for breeding), or,
     * with a free hand, as a job that needs the bucket or the feed fetched first. Whether the chest has them is not asked here: that is
     * the dispatcher's business, and a job it cannot supply simply does not hold up the ones after it.
     */
    public static Stream<Job> jobs(ServerLevel level, WorkGolem golem) {
        BlockPos site = golem.jobSite(level);
        if (site == null) return Stream.empty();
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        ItemStack carried = golem.carried();
        boolean bucket = needs.isSupply("bucket", carried);
        boolean feed = needs.isSupply("feed", carried);
        if (golem.isCarrying() && !bucket && !feed) return Stream.empty(); // something to take to the chest first
        return Stream.of(site).flatMap(at -> HerdPen.find(level, at).stream())
                .flatMap(pen -> jobsFor(level, golem, pen, bucket, feed).stream());
    }

    private static List<Job> jobsFor(ServerLevel level, WorkGolem golem, HerdPen pen, boolean bucket, boolean feed) {
        boolean bareHanded = !golem.isCarrying();
        Set<Object> taken = Claims.others(level, golem); // animals another golem is already seeing to
        TaskState state = golem.getData(ModAttachments.TASK);
        long when = level.getGameTime();
        long spoken = pen.animals.stream().filter(a -> taken.contains(a.getId())).count(); // (another golem's, and may be about to be culled)
        // one it could not get to is not picked for a while either; it is still in the pen and still counts
        for (Animal animal : pen.animals) if (state.ignoresEntity(animal.getId(), when)) taken.add(animal.getId());
        int total = pen.animals.size();
        int cells = pen.cells.size();
        Map<EntityType<?>, List<Animal>> adults = new HashMap<>();
        for (Animal animal : pen.animals) {
            if (!animal.isBaby()) adults.computeIfAbsent(animal.getType(), t -> new ArrayList<>()).add(animal);
        }
        List<Job> jobs = new ArrayList<>();

        // too crowded: cull the most numerous species, but always keep a breeding pair, and never a player's pet or mount
        if (bareHanded && (total - spoken) * CELLS_PER_ANIMAL > cells) {
            List<Animal> crowded = null;
            int most = MIN_ADULTS;
            for (List<Animal> group : adults.values()) {
                List<Animal> species = group.stream().filter(a -> !taken.contains(a.getId())).toList();
                List<Animal> spare = species.stream().filter(HerdWork::cullable).toList();
                if (species.size() > most && !spare.isEmpty()) {
                    crowded = spare;
                    most = species.size();
                }
            }
            Animal victim = crowded == null ? null : nearest(golem, crowded, null);
            if (victim != null) jobs.add(Job.now(new HerdPlan(HerdPlan.Action.CULL, victim.getId())));
        }

        if (bareHanded) {
            List<Animal> ready = new ArrayList<>();
            for (Animal animal : pen.animals) {
                if (animal instanceof Sheep sheep && !sheep.isBaby() && sheep.readyForShearing() && !taken.contains(animal.getId())) ready.add(animal);
            }
            Animal shearable = nearest(golem, ready, null);
            if (shearable != null) jobs.add(Job.now(new HerdPlan(HerdPlan.Action.SHEAR, shearable.getId())));
        }

        if (bucket || bareHanded) {
            long now = level.getGameTime();
            List<Animal> milkable = new ArrayList<>();
            for (Animal animal : pen.animals) {
                if (animal instanceof Cow cow && !cow.isBaby() && !taken.contains(animal.getId())) {
                    long last = cow.getData(ModAttachments.MILKED_AT);
                    if (last == Long.MIN_VALUE || now - last >= MILK_COOLDOWN) milkable.add(animal);
                }
            }
            Animal cow = nearest(golem, milkable, null);
            if (cow != null) jobs.add(bucket ? Job.now(new HerdPlan(HerdPlan.Action.MILK, cow.getId())) : Job.needing("bucket"));
        }

        // room to grow (or nearly none to spare, so culling and breeding overlap): feed a pair of the least common species that can breed
        if ((feed || bareHanded) && (total < ALWAYS_BREED_BELOW || (total - 1) * CELLS_PER_ANIMAL <= cells)) {
            List<List<Animal>> species = new ArrayList<>(adults.values());
            species.sort(Comparator.comparingInt(List::size));
            for (List<Animal> group : species) {
                if (group.size() < MIN_ADULTS) continue;
                long inLove = group.stream().filter(Animal::isInLove).count();
                if (inLove >= 2) continue;
                List<Animal> candidates = new ArrayList<>();
                for (Animal animal : group) {
                    if (animal.getAge() == 0 && !animal.isInLove() && animal.canFallInLove() && !taken.contains(animal.getId())
                            && (!feed || animal.isFood(golem.carried()))) {
                        candidates.add(animal);
                    }
                }
                Animal pick = nearest(golem, candidates, null);
                if (pick == null) continue;
                // fetching: only feed this species eats will do (a cow cannot be fed carrots)
                jobs.add(feed ? Job.now(new HerdPlan(HerdPlan.Action.BREED, pick.getId())) : Job.needing("feed", pick::isFood));
            }
        }
        return jobs;
    }

    /** Whether the herder may kill it: a player's name tag, lead or saddle marks one to be left alone. */
    private static boolean cullable(Animal animal) {
        return !animal.hasCustomName() && !animal.isLeashed() && !animal.isSaddled();
    }

    private static Animal nearest(WorkGolem golem, List<Animal> animals, Animal exclude) {
        Animal best = null;
        for (Animal animal : animals) {
            if (animal == exclude) continue;
            if (best == null || golem.distanceToSqr(animal) < golem.distanceToSqr(best)) best = animal;
        }
        return best;
    }

    // ---- the job (runs in the exclusive task activity) ---------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        machine.reset();
        approach.reset();
        spot = null;
        frozen = null;
        held = null;
        yaw = 0;
        golem.setPickupSuspended(true);
    }

    /** Whether the animations know this animal: they are drawn for sheep, cows and pigs. */
    private static boolean hasAct(Animal animal) {
        return animal instanceof Sheep || animal instanceof Cow || animal instanceof Pig;
    }

    /** The clip this job plays, or null (milking has none, and neither does anything done to a chicken). */
    private String clipFor(Animal animal) {
        if (!hasAct(animal)) return null;
        String kind = animal instanceof Sheep ? "sheep" : animal instanceof Cow ? "cow" : "pig";
        return switch (plan.action()) {
            case CULL -> "cull-" + kind;
            case BREED -> "feed-" + kind;
            case SHEAR -> "shear";
            case MILK -> null;
        };
    }

    private static int kindIndex(Animal animal) {
        return animal instanceof Sheep ? 0 : animal instanceof Cow ? 1 : 2;
    }

    /** Where the animal stands relative to the golem for this job: blocks to its right and ahead, and which way it faces (right, ahead components). */
    private double[] layout(Animal animal) {
        if (!hasAct(animal)) return null;
        int k = kindIndex(animal);
        switch (plan.action()) {
            case CULL -> {
                double back = Anim.T_ANIMALS[k][0], y = 0.78 * back, th = Math.acos(Math.min(1, (21 - y) / 17.0));
                double r = 4 + 17 * Math.sin(th) + 2 * Math.cos(th) + 1.5;
                return new double[]{r * Math.cos(Anim.T_CULL_PHI) / 16.0, r * Math.sin(Anim.T_CULL_PHI) / 16.0, Math.sin(Anim.T_CULL_PHI), -Math.cos(Anim.T_CULL_PHI)};
            }
            case BREED -> {
                return new double[]{(Anim.T_FEED_NX + 12.0) / 16.0, 0, -1, 0};
            }
            case SHEAR -> {
                return new double[]{Anim.T_SHEAR_X / 16.0, 0, 0, 1};
            }
            default -> {
                return null;
            }
        }
    }

    /**
     * Where the golem should stand, and (through {@link #yaw}) which way it faces, so that the animal, set where the clip wants it, is
     * exactly where the clip's arms go. The facing is one of eight, the spot must have room and ground, and the nearest wins.
     */
    private Vec3 chooseSpot(ServerLevel level, WorkGolem golem, Animal animal, double[] lay) {
        Vec3 best = null;
        double bestDist = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            float y = i * 45.0F;
            Vec3 off = Face.local(Vec3.ZERO, y, lay[0], lay[1]);
            Vec3 at = new Vec3(animal.getX() - off.x, animal.getY(), animal.getZ() - off.z);
            double d = at.distanceToSqr(golem.position());
            if (d >= bestDist || !Stand.reachable(level, golem, at)) continue; // behind a fence, say: no use standing there
            if (d < bestDist) {
                bestDist = d;
                best = at;
                yaw = y;
            }
        }
        return best;
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        Entity target = level.getEntity(plan.targetId());
        if (machine.is(State.PLAY) || machine.is(State.FOLLOW_THROUGH)) {
            play(golem, target);
            return;
        }
        if (!(target instanceof Animal animal) || !animal.isAlive()) { done(); return; }

        lookAt(golem, new EntityTracker(animal, true));
        // a pen with a gate is worked from inside it, with the gate shut behind; one without, from over the fence
        Set<BlockPos> pen = HerdPen.cellsAround(level, animal.blockPosition());
        boolean gated = pen != null && HerdPen.hasGate(level, pen);
        boolean inside = !gated || pen.contains(golem.blockPosition()) || pen.contains(golem.blockPosition().above());
        if (golem.gates().busy() || !inside || golem.distanceToSqr(animal) > REACH_SQR) {
            machine.to(State.WALK);
            spot = null;
            if (approach.walk(golem, new EntityTracker(animal, false), gated ? 1 : 3, () -> false) == Approach.Outcome.UNREACHABLE) giveUp(golem, animal, time);
            return;
        }
        // in reach: settle on a spot so that the animal, set where the clip wants it, is exactly where the arms go
        machine.to(State.STAGE);
        double[] lay = layout(animal);
        if (lay == null) { // milking, or a chicken: just stand by it
            holdStill(golem);
            Face.toward(golem, animal.position());
            frozen = animal;
            yaw = golem.getYRot();
            startClip(level, golem, animal);
            return;
        }
        if (spot == null) spot = chooseSpot(level, golem, animal, lay);
        if (spot == null) { // no room to stage it as the clip wants: do the job without the show
            holdStill(golem);
            golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            perform(level, golem, animal, time);
            done();
            return;
        }
        Approach.Outcome outcome = approach.stand(golem, spot, 1.0);
        if (outcome != Approach.Outcome.ARRIVED) {
            if (outcome == Approach.Outcome.UNREACHABLE) giveUp(golem, animal, time);
            return;
        }
        // where the animal would be held for the clip: only ever where it could have walked to itself, on its own level
        Vec3 off = Face.local(Vec3.ZERO, yaw, lay[0], lay[1]);
        Vec3 hold = new Vec3(golem.getX() + off.x, animal.getY(), golem.getZ() + off.z);
        if (Math.abs(golem.getY() - animal.getY()) > LEVEL || Approach.fenceBetween(level, animal.position(), hold)
                || !level.noCollision(animal, animal.getBoundingBox().move(hold.subtract(animal.position())).deflate(0.05))) {
            holdStill(golem); // up on something, or a fence or wall in the way: do the job without the show
            golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            perform(level, golem, animal, time);
            done();
            return;
        }
        held = hold;
        Face.toward(golem, animal.position().multiply(1, 0, 1).add(0, golem.getY(), 0));
        golem.setYRot(yaw);
        golem.setYBodyRot(yaw);
        golem.setYHeadRot(yaw);
        holdStill(golem);
        animal.getNavigation().stop();
        frozen = animal;
        startClip(level, golem, animal);
    }

    /** Plays the job's clip; the deed itself lands at its moment in it. */
    private void startClip(ServerLevel level, WorkGolem golem, Animal animal) {
        float at = !hasAct(animal) ? 0.8F : switch (plan.action()) {
            case CULL -> Anim.T_CULL_HIT + 0.05F;
            case BREED -> Anim.T_FEED_BITE + 0.2F;
            case SHEAR -> Anim.T_SHEAR_AT;
            case MILK -> 0.8F;
        };
        String show = clipFor(animal);
        float len = show == null ? 1.6F : switch (plan.action()) {
            case CULL -> Anim.T_CULL_LEN;
            case BREED -> Anim.T_FEED_LEN;
            case SHEAR -> Anim.T_SHEAR_LEN;
            case MILK -> 1.6F;
        };
        clip = ClipRun.play(golem, show, len).at(at, () -> {
            if (!animal.isAlive()) return;
            machine.to(State.FOLLOW_THROUGH);
            golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            perform(level, golem, animal, level.getGameTime());
        });
        machine.to(State.PLAY);
    }

    /** It cannot get to this animal: leave it alone for a while, to its own work and to any golem that can. */
    private void giveUp(WorkGolem golem, Animal animal, long time) {
        golem.getData(ModAttachments.TASK).ignoreEntity(animal.getId(), time + IGNORE_TICKS);
        done();
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    /** The clip is playing: the animal is held where the arms go, and the deed is done at its moment in the clip. */
    private void play(WorkGolem golem, Entity target) {
        holdStill(golem);
        boolean acted = machine.is(State.FOLLOW_THROUGH);
        Animal animal = target instanceof Animal a && a.isAlive() ? a : null;
        if (animal == null && !acted) { done(); return; }
        if (animal != null && frozen != null) {
            golem.setYRot(yaw);
            golem.setYBodyRot(yaw);
            golem.setYHeadRot(yaw);
            double[] lay = layout(animal);
            if (held != null && lay != null) { // held where it was settled at the start, wherever the golem gets to
                animal.setPos(held.x, held.y, held.z);
                animal.setDeltaMovement(Vec3.ZERO);
                Vec3 face = Face.local(Vec3.ZERO, yaw, lay[2], lay[3]);
                float ay = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
                animal.setYRot(ay);
                animal.setYBodyRot(ay);
                animal.setYHeadRot(ay);
            }
        }
        if (clip.tick()) done();
    }

    private boolean perform(ServerLevel level, WorkGolem golem, Animal animal, long time) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        ItemStack carried = golem.carried();
        switch (plan.action()) {
            case CULL -> {
                if (golem.isCarrying() || !cullable(animal)) return false; // (named or leashed since it was picked)
                animal.hurtServer(level, level.damageSources().mobAttack(golem), animal.getMaxHealth() * 2);
                return true;
            }
            case SHEAR -> {
                if (golem.isCarrying() || !(animal instanceof Sheep sheep) || !sheep.readyForShearing()) return false;
                sheep.shear(level, SoundSource.NEUTRAL, new ItemStack(Items.SHEARS));
                return true;
            }
            case MILK -> {
                if (!needs.isSupply("bucket", carried) || !(animal instanceof Cow)) return false;
                long last = animal.getData(ModAttachments.MILKED_AT); // another golem may have got here first
                if (last != Long.MIN_VALUE && time - last < MILK_COOLDOWN) return false;
                carried.shrink(1);
                ItemStack milk = new ItemStack(Items.MILK_BUCKET);
                if (carried.isEmpty()) golem.setCarried(milk);
                else golem.spawnAtLocation(level, milk); // a stack of buckets: only the first is carried
                animal.setData(ModAttachments.MILKED_AT, time);
                animal.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
                return true;
            }
            case BREED -> {
                if (!needs.isSupply("feed", carried) || !animal.isFood(carried) || animal.isInLove()) return false;
                carried.shrink(1);
                if (carried.isEmpty()) golem.setCarried(ItemStack.EMPTY);
                animal.setInLove(null);
                animal.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 1.0F);
                return true;
            }
        }
        return false;
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        frozen = null;
        held = null;
        golem.setPickupSuspended(false);
    }
}
