package com.scottcaldwell.industriousvillagers.herd;

import com.scottcaldwell.industriousvillagers.ModTags;
import com.scottcaldwell.industriousvillagers.needs.Need;
import com.scottcaldwell.industriousvillagers.ModAttachments;
import com.scottcaldwell.industriousvillagers.behavior.Drops;
import com.scottcaldwell.industriousvillagers.behavior.HeldItem;
import com.scottcaldwell.industriousvillagers.behavior.TaskRunner;
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
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.npc.villager.Villager;
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
 * milks cows. Each job needs its tool (sword, shears, bucket, feed) and is skipped without it.
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

    /** Picks the next herding job, or null. Used as the herder's {@link ProfessionNeeds.WorkPlanner}. */
    public static Object plan(ServerLevel level, Villager villager) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (needs == null || site == null || !site.dimension().equals(level.dimension())) return null;

        boolean sword = needs.has(villager, "sword");
        boolean shears = needs.has(villager, "shears");
        boolean bucket = needs.has(villager, "bucket");

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

    private static int feedSlot(Villager villager, Animal animal) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && s.is(ModTags.ANIMAL_FEED) && animal.isFood(s)) return i;
        }
        return -1;
    }

    // ---- the job (runs in the exclusive task activity) ---------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, Villager villager, long time) {
        phase = Phase.APPROACH;
        deadline = time + APPROACH_TICKS;
    }

    @Override
    protected boolean allowedNow(ServerLevel level, Villager villager) {
        return phase != Phase.DONE && super.allowedNow(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        Entity target = level.getEntity(plan.targetId());
        if (phase == Phase.PAUSE) {
            holdStill(villager);
            if (target != null) lookAt(villager, new EntityTracker(target, true));
            if (--timer <= 0) finish();
            return;
        }
        if (!(target instanceof Animal animal) || !animal.isAlive()) { finish(); return; }

        lookAt(villager, new EntityTracker(animal, true));
        if (villager.distanceToSqr(animal) > REACH_SQR) {
            if (time > deadline) { finish(); return; }
            if (phase == Phase.LOOK) phase = Phase.APPROACH; // it moved out of reach: follow it
            walkTo(villager, new EntityTracker(animal, false), 3);
            return;
        }
        holdStill(villager);
        if (phase == Phase.APPROACH) { // arrived: take up the tool and look at the animal before doing anything to it
            phase = Phase.LOOK;
            timer = LOOK_TICKS;
            HeldItem.hold(villager, toolFor(villager, animal));
        }
        if (--timer > 0) return;
        villager.swing(InteractionHand.MAIN_HAND);
        if (perform(level, villager, animal, time)) {
            phase = Phase.PAUSE;
            timer = PAUSE_TICKS;
        } else {
            finish();
        }
    }

    /** The item the villager uses for this job, so it can be seen in its hand. */
    private ItemStack toolFor(Villager villager, Animal animal) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        SimpleContainer inv = villager.getInventory();
        int slot = switch (plan.action()) {
            case CULL -> needs.need("sword").slot(inv);
            case SHEAR -> needs.need("shears").slot(inv);
            case MILK -> needs.need("bucket").slot(inv);
            case BREED -> feedSlot(villager, animal);
        };
        return slot < 0 ? ItemStack.EMPTY : inv.getItem(slot);
    }

    private boolean perform(ServerLevel level, Villager villager, Animal animal, long time) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        if (needs == null) return false;
        SimpleContainer inv = villager.getInventory();
        switch (plan.action()) {
            case CULL -> {
                if (!needs.has(villager, "sword")) return false;
                ToolWear.use(level, villager, "sword");
                animal.hurtServer(level, level.damageSources().mobAttack(villager), animal.getMaxHealth() * 2);
                Drops.collectFresh(level, villager, animal.position());
                return true;
            }
            case SHEAR -> {
                int slot = needs.need("shears").slot(inv);
                if (slot < 0 || !(animal instanceof Sheep sheep) || !sheep.readyForShearing()) return false;
                sheep.shear(level, SoundSource.NEUTRAL, inv.getItem(slot).copyWithCount(1));
                ToolWear.use(level, villager, "shears");
                Drops.collectFresh(level, villager, animal.position());
                return true;
            }
            case MILK -> {
                int slot = needs.need("bucket").slot(inv);
                if (slot < 0 || !(animal instanceof Cow)) return false;
                inv.removeItem(slot, 1);
                give(level, villager, new ItemStack(Items.MILK_BUCKET));
                animal.setData(ModAttachments.MILKED_AT, time);
                animal.playSound(SoundEvents.COW_MILK, 1.0F, 1.0F);
                return true;
            }
            case BREED -> {
                int slot = feedSlot(villager, animal);
                if (slot < 0 || animal.isInLove()) return false;
                inv.removeItem(slot, 1);
                animal.setInLove(null);
                animal.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 1.0F);
                return true;
            }
        }
        return false;
    }

    private static void give(ServerLevel level, Villager villager, ItemStack stack) {
        ItemStack rest = villager.getInventory().addItem(stack);
        if (!rest.isEmpty()) villager.spawnAtLocation(level, rest);
        villager.getInventory().setChanged();
    }

    @Override
    protected void end(ServerLevel level, Villager villager, long time) {
        phase = Phase.DONE;
    }
}