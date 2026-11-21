package com.scottcaldwell.industriousgolems.herd;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
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

/**
 * The herder's work, deliberately slower and more limited than a player: it manages the animals inside an enclosed
 * pen near its job site, keeping the population in a density band. Above the band it culls the most numerous
 * species (never below a breeding pair); below it, it feeds adults so they breed. It also shears ready sheep and
 * milks cows. It works bare-handed; milking uses up a bucket and breeding a unit of feed, and a job is skipped
 * without its material.
 */
public class HerdWork extends TaskRunner<HerdPlan> {

    /**
     * Cull above one animal per this many pen cells; breed below one per {@link #BREED_BELOW_CELLS_PER_ANIMAL}. The gap between them
     * is what stops it breeding a calf only to cull it again: in a 16-cell pen it breeds up to four and culls from five.
     */
    static final double CULL_ABOVE_CELLS_PER_ANIMAL = 3.5;
    static final double BREED_BELOW_CELLS_PER_ANIMAL = 4.0;
    /** Never cull a species down to fewer adults than this. */
    static final int MIN_ADULTS = 2;
    static final long MILK_COOLDOWN = 6000;
    private static final double REACH_SQR = 4.0 * 4.0;
    private static final int APPROACH_TICKS = 400;

    private enum State { WALK, STAGE, PLAY, FOLLOW_THROUGH, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("herd-work", "Herder: cull, shear, milk, feed",
            "Terracotta golem. Walks to an animal in a pen, stands where the clip wants it (the animal is held where the arms go), "
                    + "and plays the clip for the job; the deed itself lands at a moment in the clip.", State.class)
            .start(State.WALK)
            .state(State.WALK, "Walking towards the animal, until within 4 blocks.")
            .state(State.STAGE, "Within reach: choosing and settling onto the spot (one of eight facings) the clip is written for.")
            .state(State.PLAY, "Clip playing, the animal frozen in place; the deed has not happened yet.")
            .state(State.FOLLOW_THROUGH, "The deed is done (culled, sheared, milked or fed); the clip is finishing.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.WALK, State.STAGE, "within 4 blocks of the animal")
            .edge(State.WALK, State.DONE, "the animal is gone, or 20 s without reaching it")
            .edge(State.STAGE, State.WALK, "the animal wandered out of reach")
            .edge(State.STAGE, State.PLAY, "standing on the spot facing the right way (milking needs no spot)")
            .edge(State.STAGE, State.DONE, "no room to stage it (does the job without the show), the animal is gone, or 20 s")
            .edge(State.PLAY, State.FOLLOW_THROUGH, "the clip reaches the deed's moment")
            .edge(State.PLAY, State.DONE, "the animal vanished first, or the clip ends")
            .edge(State.FOLLOW_THROUGH, State.DONE, "the clip ends");

    private final Machine.Run<State> machine = GRAPH.run();
    private long deadline;
    private int timer;
    private Vec3 spot;
    private Animal frozen;
    private float yaw;

    public HerdWork() {
        super(HerdPlan.class, 600);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /** Picks the next herding job, or null. Milking and breeding need the bucket or feed in the golem's hand. */
    public static Object plan(ServerLevel level, WorkGolem golem) {
        BlockPos site = golem.jobSite(level);
        if (site == null) return null;
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        ItemStack carried = golem.carried();
        boolean bucket = needs.isSupply("bucket", carried);
        boolean feed = needs.isSupply("feed", carried);
        if (golem.isCarrying() && !bucket && !feed) return null; // something to take to the chest first
        return findPlan(level, golem, site, bucket, feed, !golem.isCarrying());
    }

    /** The supply that would let the golem milk or breed, when nothing else is worth doing. */
    public static String wants(ServerLevel level, WorkGolem golem) {
        BlockPos site = golem.jobSite(level);
        if (site == null || golem.isCarrying()) return null;
        HerdPlan plan = findPlan(level, golem, site, true, true, false);
        if (plan == null) return null;
        return plan.action() == HerdPlan.Action.MILK ? "bucket" : plan.action() == HerdPlan.Action.BREED ? "feed" : null;
    }

    private static HerdPlan findPlan(ServerLevel level, WorkGolem golem, BlockPos site, boolean bucket, boolean feed,
                                     boolean bareHanded) {
        for (HerdPen pen : HerdPen.find(level, site)) {
            HerdPlan plan = planFor(level, golem, pen, bucket, feed, bareHanded);
            if (plan != null) return plan;
        }
        return null;
    }

    private static HerdPlan planFor(ServerLevel level, WorkGolem golem, HerdPen pen, boolean bucket, boolean feed,
                                    boolean bareHanded) {
        int total = pen.animals.size();
        int cells = pen.cells.size();
        Map<EntityType<?>, List<Animal>> adults = new HashMap<>();
        for (Animal animal : pen.animals) {
            if (!animal.isBaby()) adults.computeIfAbsent(animal.getType(), t -> new ArrayList<>()).add(animal);
        }

        // too crowded: cull the most numerous species, but always keep a breeding pair
        if (bareHanded && total * CULL_ABOVE_CELLS_PER_ANIMAL > cells) {
            List<Animal> crowded = null;
            for (List<Animal> species : adults.values()) {
                if (species.size() > MIN_ADULTS && (crowded == null || species.size() > crowded.size())) crowded = species;
            }
            Animal victim = crowded == null ? null : nearest(golem, crowded, null);
            if (victim != null) return new HerdPlan(HerdPlan.Action.CULL, victim.getId());
        }

        if (bareHanded) {
            List<Animal> ready = new ArrayList<>();
            for (Animal animal : pen.animals) {
                if (animal instanceof Sheep sheep && !sheep.isBaby() && sheep.readyForShearing()) ready.add(animal);
            }
            Animal shearable = nearest(golem, ready, null);
            if (shearable != null) return new HerdPlan(HerdPlan.Action.SHEAR, shearable.getId());
        }

        if (bucket) {
            long now = level.getGameTime();
            List<Animal> milkable = new ArrayList<>();
            for (Animal animal : pen.animals) {
                if (animal instanceof Cow cow && !cow.isBaby()) {
                    long last = cow.getData(ModAttachments.MILKED_AT);
                    if (last == Long.MIN_VALUE || now - last >= MILK_COOLDOWN) milkable.add(animal);
                }
            }
            Animal cow = nearest(golem, milkable, null);
            if (cow != null) return new HerdPlan(HerdPlan.Action.MILK, cow.getId());
        }

        // room to grow: feed a pair of the least common species that can breed
        if (feed && total * BREED_BELOW_CELLS_PER_ANIMAL < cells) {
            List<List<Animal>> species = new ArrayList<>(adults.values());
            species.sort(Comparator.comparingInt(List::size));
            for (List<Animal> group : species) {
                if (group.size() < MIN_ADULTS) continue;
                long inLove = group.stream().filter(Animal::isInLove).count();
                if (inLove >= 2) continue;
                List<Animal> candidates = new ArrayList<>();
                for (Animal animal : group) {
                    if (animal.getAge() == 0 && !animal.isInLove() && animal.canFallInLove()
                            && (!golem.isCarrying() || animal.isFood(golem.carried()))) {
                        candidates.add(animal);
                    }
                }
                Animal pick = nearest(golem, candidates, null);
                if (pick != null) return new HerdPlan(HerdPlan.Action.BREED, pick.getId());
            }
        }
        return null;
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
        deadline = time + APPROACH_TICKS;
        spot = null;
        frozen = null;
        yaw = 0;
        golem.setPickupSuspended(true);
    }

    /** The clip this job plays, or null (milking has none). */
    private String clipFor(Animal animal) {
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
            Vec3 at = new Vec3(animal.getX() - off.x, golem.getY(), animal.getZ() - off.z);
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
            play(level, golem, target, time);
            return;
        }
        if (!(target instanceof Animal animal) || !animal.isAlive()) { done(); return; }

        lookAt(golem, new EntityTracker(animal, true));
        if (golem.distanceToSqr(animal) > REACH_SQR) {
            machine.to(State.WALK);
            if (time > deadline) { done(); return; }
            spot = null;
            walkTo(golem, new EntityTracker(animal, false), 3);
            return;
        }
        // in reach: settle on a spot so that the animal, set where the clip wants it, is exactly where the arms go
        machine.to(State.STAGE);
        double[] lay = layout(animal);
        if (lay == null) { // milking: just stand at the cow
            holdStill(golem);
            Face.toward(golem, animal.position());
            if (clipFor(animal) != null) golem.playClip(clipFor(animal));
            machine.to(State.PLAY);
            timer = 0;
            frozen = animal;
            yaw = golem.getYRot();
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
        if (!standAt(golem, spot, 1.0)) {
            if (time > deadline) done();
            return;
        }
        Face.toward(golem, animal.position().multiply(1, 0, 1).add(0, golem.getY(), 0));
        golem.setYRot(yaw);
        golem.setYBodyRot(yaw);
        golem.setYHeadRot(yaw);
        holdStill(golem);
        animal.getNavigation().stop();
        if (clipFor(animal) != null) golem.playClip(clipFor(animal));
        machine.to(State.PLAY);
        timer = 0;
        frozen = animal;
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    /** The clip is playing: the animal is held where the arms go, and the deed is done at its moment in the clip. */
    private void play(ServerLevel level, WorkGolem golem, Entity target, long time) {
        holdStill(golem);
        timer++;
        boolean acted = machine.is(State.FOLLOW_THROUGH);
        Animal animal = target instanceof Animal a && a.isAlive() ? a : null;
        if (animal == null && !acted) { done(); return; }
        if (animal != null && frozen != null) {
            golem.setYRot(yaw);
            golem.setYBodyRot(yaw);
            golem.setYHeadRot(yaw);
            double[] lay = layout(animal);
            if (lay != null) {
                Vec3 at = Face.local(golem.position(), yaw, lay[0], lay[1]);
                animal.setPos(at.x, golem.getY(), at.z);
                animal.setDeltaMovement(Vec3.ZERO);
                Vec3 face = Face.local(Vec3.ZERO, yaw, lay[2], lay[3]);
                float ay = (float) (Mth.atan2(-face.x, face.z) * Mth.RAD_TO_DEG);
                animal.setYRot(ay);
                animal.setYBodyRot(ay);
                animal.setYHeadRot(ay);
            }
        }
        float at = switch (plan.action()) {
            case CULL -> Anim.T_CULL_HIT + 0.05F;
            case BREED -> Anim.T_FEED_BITE + 0.2F;
            case SHEAR -> Anim.T_SHEAR_AT;
            case MILK -> 0.8F;
        };
        float len = switch (plan.action()) {
            case CULL -> Anim.T_CULL_LEN;
            case BREED -> Anim.T_FEED_LEN;
            case SHEAR -> Anim.T_SHEAR_LEN;
            case MILK -> 1.6F;
        };
        if (!acted && animal != null && timer >= Math.round(at * 20)) {
            machine.to(State.FOLLOW_THROUGH);
            golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            perform(level, golem, animal, time);
        }
        if (timer >= Math.round(len * 20)) done();
    }

    private boolean perform(ServerLevel level, WorkGolem golem, Animal animal, long time) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        ItemStack carried = golem.carried();
        switch (plan.action()) {
            case CULL -> {
                if (golem.isCarrying()) return false;
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
        golem.setPickupSuspended(false);
    }
}
