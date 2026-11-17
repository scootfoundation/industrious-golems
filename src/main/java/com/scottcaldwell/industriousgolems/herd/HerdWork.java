package com.scottcaldwell.industriousgolems.herd;

import com.scottcaldwell.industriousgolems.ModTags;
import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
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

    /** Cull above one animal per this many pen cells; breed below one per {@link #BREED_BELOW_CELLS_PER_ANIMAL}. */
    static final int CULL_ABOVE_CELLS_PER_ANIMAL = 5;
    static final int BREED_BELOW_CELLS_PER_ANIMAL = 6;
    /** Never cull a species down to fewer adults than this. */
    static final int MIN_ADULTS = 2;
    static final long MILK_COOLDOWN = 6000;
    private static final double REACH_SQR = 4.0 * 4.0;
    private static final int APPROACH_TICKS = 400;
    private static final int LOOK_TICKS = 15;
    private static final int PAUSE_TICKS = 25;

    private enum Phase { APPROACH, LOOK, PAUSE, DONE }

    private Phase phase = Phase.DONE;
    private long deadline;
    private int timer;

    public HerdWork() {
        super(HerdPlan.class, 600);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /** Picks the next herding job, or null. Milking and breeding need the bucket or feed in the golem's hand. */
    public static Object plan(ServerLevel level, WorkGolem golem) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        GlobalPos site = golem.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (needs == null || site == null || !site.dimension().equals(level.dimension())) return null;
        ItemStack carried = golem.carried();
        boolean bucket = needs.isSupply("bucket", carried);
        boolean feed = needs.isSupply("feed", carried);
        if (golem.isCarrying() && !bucket && !feed) return null; // something to take to the chest first
        return findPlan(level, golem, site, bucket, feed, !golem.isCarrying());
    }

    /** The supply that would let the golem milk or breed, when nothing else is worth doing. */
    public static String wants(ServerLevel level, WorkGolem golem) {
        GlobalPos site = golem.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (site == null || !site.dimension().equals(level.dimension()) || golem.isCarrying()) return null;
        HerdPlan plan = findPlan(level, golem, site, true, true, false);
        if (plan == null) return null;
        return plan.action() == HerdPlan.Action.MILK ? "bucket" : plan.action() == HerdPlan.Action.BREED ? "feed" : null;
    }

    private static HerdPlan findPlan(ServerLevel level, WorkGolem golem, GlobalPos site, boolean bucket, boolean feed,
                                     boolean bareHanded) {
        for (HerdPen pen : HerdPen.find(level, site.pos())) {
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
        phase = Phase.APPROACH;
        deadline = time + APPROACH_TICKS;
    }

    @Override
    protected boolean allowedNow(ServerLevel level, WorkGolem golem) {
        return phase != Phase.DONE && super.allowedNow(level, golem);
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        Entity target = level.getEntity(plan.targetId());
        if (phase == Phase.PAUSE) {
            holdStill(golem);
            if (target != null) lookAt(golem, new EntityTracker(target, true));
            if (--timer <= 0) finish();
            return;
        }
        if (!(target instanceof Animal animal) || !animal.isAlive()) { finish(); return; }

        lookAt(golem, new EntityTracker(animal, true));
        if (golem.distanceToSqr(animal) > REACH_SQR) {
            if (time > deadline) { finish(); return; }
            if (phase == Phase.LOOK) phase = Phase.APPROACH; // it moved out of reach: follow it
            walkTo(golem, new EntityTracker(animal, false), 3);
            return;
        }
        holdStill(golem);
        if (phase == Phase.APPROACH) { // arrived: look at the animal before doing anything to it
            phase = Phase.LOOK;
            timer = LOOK_TICKS;
        }
        if (--timer > 0) return;
        golem.swing(InteractionHand.MAIN_HAND);
        if (perform(level, golem, animal, time)) {
            phase = Phase.PAUSE;
            timer = PAUSE_TICKS;
        } else {
            finish();
        }
    }

    private boolean perform(ServerLevel level, WorkGolem golem, Animal animal, long time) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        if (needs == null) return false;
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
        phase = Phase.DONE;
    }
}