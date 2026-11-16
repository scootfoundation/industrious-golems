package com.scottcaldwell.industriousvillagers.herd;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousvillagers.ModAttachments;
import com.scottcaldwell.industriousvillagers.behavior.TaskState;
import com.scottcaldwell.industriousvillagers.behavior.Tasks;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import com.scottcaldwell.industriousvillagers.needs.ToolWear;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The herder's work, deliberately slower and more limited than a player: it manages the animals inside an enclosed
 * pen near its job site, keeping the population in a density band. Above the band it culls the most numerous
 * species (never below a breeding pair); below it, it feeds adults so they breed. It also shears ready sheep and
 * milks cows. Each job needs its tool (sword, shears, bucket, feed) and is skipped without it.
 */
public class HerdWork extends Behavior<Villager> {

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

    private HerdPlan plan;
    private Phase phase = Phase.DONE;
    private long deadline;
    private int timer;

    public HerdWork() {
        super(ImmutableMap.of(), 600);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /** Picks the next herding job, or null. Used as the herder's {@link ProfessionNeeds.WorkPlanner}. */
    public static Object plan(ServerLevel level, Villager villager) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (needs == null || site == null || !site.dimension().equals(level.dimension())) return null;

        boolean sword = hasTool(needs, villager, "sword");
        boolean shears = hasTool(needs, villager, "shears");
        boolean bucket = hasTool(needs, villager, "bucket");

        for (HerdPen pen : HerdPen.find(level, site.pos())) {
            HerdPlan plan = planFor(level, villager, pen, sword, shears, bucket);
            if (plan != null) return plan;
        }
        return null;
    }

    private static HerdPlan planFor(ServerLevel level, Villager villager, HerdPen pen, boolean sword, boolean shears, boolean bucket) {
        int total = pen.animals.size();
        int cells = pen.cells.size();
        Map<EntityType<?>, List<Animal>> adults = new HashMap<>();
        for (Animal animal : pen.animals) {
            if (!animal.isBaby()) adults.computeIfAbsent(animal.getType(), t -> new ArrayList<>()).add(animal);
        }

        // too crowded: cull the most numerous species, but always keep a breeding pair
        if (sword && total * CULL_ABOVE_CELLS_PER_ANIMAL > cells) {
            List<Animal> crowded = null;
            for (List<Animal> species : adults.values()) {
                if (species.size() > MIN_ADULTS && (crowded == null || species.size() > crowded.size())) crowded = species;
            }
            Animal victim = crowded == null ? null : nearest(villager, crowded, null);
            if (victim != null) return new HerdPlan(HerdPlan.Action.CULL, victim.getId());
        }

        if (shears) {
            List<Animal> ready = new ArrayList<>();
            for (Animal animal : pen.animals) {
                if (animal instanceof Sheep sheep && !sheep.isBaby() && sheep.readyForShearing()) ready.add(animal);
            }
            Animal sheep = nearest(villager, ready, null);
            if (sheep != null) return new HerdPlan(HerdPlan.Action.SHEAR, sheep.getId());
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
            Animal cow = nearest(villager, milkable, null);
            if (cow != null) return new HerdPlan(HerdPlan.Action.MILK, cow.getId());
        }

        // room to grow: feed a pair of the least common species that can breed
        if (total * BREED_BELOW_CELLS_PER_ANIMAL < cells) {
            List<List<Animal>> species = new ArrayList<>(adults.values());
            species.sort(Comparator.comparingInt(List::size));
            for (List<Animal> group : species) {
                if (group.size() < MIN_ADULTS) continue;
                long inLove = group.stream().filter(Animal::isInLove).count();
                if (inLove >= 2) continue;
                List<Animal> candidates = new ArrayList<>();
                for (Animal animal : group) {
                    if (animal.getAge() == 0 && !animal.isInLove() && animal.canFallInLove()
                            && feedSlot(villager, animal) >= 0) {
                        candidates.add(animal);
                    }
                }
                Animal pick = nearest(villager, candidates, null);
                if (pick != null) return new HerdPlan(HerdPlan.Action.BREED, pick.getId());
            }
        }
        return null;
    }

    private static Animal nearest(Villager villager, List<Animal> animals, Animal exclude) {
        Animal best = null;
        for (Animal animal : animals) {
            if (animal == exclude) continue;
            if (best == null || villager.distanceToSqr(animal) < villager.distanceToSqr(best)) best = animal;
        }
        return best;
    }

    private static boolean hasTool(ProfessionNeeds needs, Villager villager, String id) {
        for (ProfessionNeeds.Need need : needs.toolNeeds()) {
            if (need.id().equals(id)) return need.have(villager.getInventory()) > 0;
        }
        return false;
    }

    private static int slotOf(Villager villager, java.util.function.Predicate<ItemStack> match) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty() && match.test(inv.getItem(i))) return i;
        }
        return -1;
    }

    private static int feedSlot(Villager villager, Animal animal) {
        return slotOf(villager, s -> s.is(ProfessionNeeds.ANIMAL_FEED) && animal.isFood(s));
    }

    // ---- the job (runs in the exclusive task activity) ---------------------------------------------------------

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        TaskState state = villager.getData(ModAttachments.TASK);
        return state.type == TaskState.Type.WORK && state.work instanceof HerdPlan;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        plan = (HerdPlan) villager.getData(ModAttachments.TASK).work;
        Tasks.stopOthers(level, villager, this, time);
        phase = Phase.APPROACH;
        deadline = time + APPROACH_TICKS;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return phase != Phase.DONE && plan != null && Tasks.active(villager) && Tasks.scheduleAllowsWork(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        Entity target = level.getEntity(plan.targetId());
        if (phase == Phase.PAUSE) {
            holdStill(villager);
            if (target != null) villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
            if (--timer <= 0) phase = Phase.DONE;
            return;
        }
        if (!(target instanceof Animal animal) || !animal.isAlive()) { phase = Phase.DONE; return; }

        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(animal, true));
        if (villager.distanceToSqr(animal) > REACH_SQR) {
            if (time > deadline) { phase = Phase.DONE; return; }
            if (phase == Phase.LOOK) phase = Phase.APPROACH; // it moved out of reach: follow it
            if (!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
                villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new EntityTracker(animal, false), 0.5F, 3));
            }
            return;
        }
        holdStill(villager);
        if (phase == Phase.APPROACH) { // arrived: stop and look at the animal before doing anything to it
            phase = Phase.LOOK;
            timer = LOOK_TICKS;
        }
        if (--timer > 0) return;
        villager.swing(InteractionHand.MAIN_HAND);
        if (perform(level, villager, animal, time)) {
            phase = Phase.PAUSE;
            timer = PAUSE_TICKS;
        } else {
            phase = Phase.DONE;
        }
    }

    private boolean perform(ServerLevel level, Villager villager, Animal animal, long time) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        if (needs == null) return false;
        switch (plan.action()) {
            case CULL -> {
                if (!hasTool(needs, villager, "sword")) return false;
                ToolWear.use(level, villager, "sword");
                animal.hurtServer(level, level.damageSources().mobAttack(villager), animal.getMaxHealth() * 2);
                collectDrops(level, villager, animal.position());
                return true;
            }
            case SHEAR -> {
                int slot = slotOf(villager, s -> s.is(Items.SHEARS));
                if (slot < 0 || !(animal instanceof Sheep sheep) || !sheep.readyForShearing()) return false;
                sheep.shear(level, SoundSource.NEUTRAL, villager.getInventory().getItem(slot).copyWithCount(1));
                ToolWear.use(level, villager, "shears");
                collectDrops(level, villager, animal.position());
                return true;
            }
            case MILK -> {
                int slot = slotOf(villager, s -> s.is(Items.BUCKET));
                if (slot < 0 || !(animal instanceof Cow)) return false;
                villager.getInventory().removeItem(slot, 1);
                give(level, villager, new ItemStack(Items.MILK_BUCKET));
                animal.setData(ModAttachments.MILKED_AT, time);
                animal.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
                return true;
            }
            case BREED -> {
                int slot = feedSlot(villager, animal);
                if (slot < 0 || animal.isInLove()) return false;
                villager.getInventory().removeItem(slot, 1);
                animal.setInLove(null);
                animal.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 1.0F);
                return true;
            }
        }
        return false;
    }

    /** Picks up what the animal just dropped (wool, meat, leather, feathers). */
    private static void collectDrops(ServerLevel level, Villager villager, Vec3 at) {
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0))) {
            if (drop.tickCount > 1) continue; // only fresh drops, never items that were already lying there
            ItemStack rest = villager.getInventory().addItem(drop.getItem().copy());
            if (rest.isEmpty()) drop.discard();
            else drop.setItem(rest);
        }
        villager.getInventory().setChanged();
    }

    private static void give(ServerLevel level, Villager villager, ItemStack stack) {
        ItemStack rest = villager.getInventory().addItem(stack);
        if (!rest.isEmpty()) villager.spawnAtLocation(level, rest);
        villager.getInventory().setChanged();
    }

    private static void holdStill(Villager villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        plan = null;
        phase = Phase.DONE;
        Tasks.finish(level, villager);
    }
}