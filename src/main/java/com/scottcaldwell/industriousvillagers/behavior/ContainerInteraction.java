package com.scottcaldwell.industriousvillagers.behavior;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousvillagers.ModAttachments;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Profession-agnostic container visit, driven by the profession's {@link ProfessionNeeds}. The villager behaves like a
 * player: walks over, looks at the chest, pauses, opens it (lid animation and sound), moves one stack at a time with
 * pauses between, closes it, looks away and leaves.
 *
 * <p>Chest preference: deposits go to copper chests and supplies (tools, food, inputs) are preferred from regular
 * chests, each falling back to the other kind. Vanilla copper golems carry items from copper chests into regular
 * chests, so this makes a golem ferry the villager's output to a regular chest that the villager restocks from.
 */
public class ContainerInteraction extends Behavior<Villager> {

    /** Containers within this many blocks (horizontally; +-2 vertically) of the job site count as "adjacent". */
    public static final int SEARCH_RADIUS = 3;
    public static final int COOLDOWN_TICKS = 200;
    private static final int DURATION = 1200;
    private static final double INTERACT_DIST_SQR = 2.5 * 2.5;

    private static final int PAUSE_LOOK = 15;
    private static final int PAUSE_OPEN = 12;
    private static final int PAUSE_STEP = 10;
    private static final int PAUSE_CLOSE = 12;
    private static final int PAUSE_AWAY = 15;

    private enum Phase { APPROACH, LOOK, TRANSFER, CLOSING, LOOK_AWAY, DONE }

    private BlockPos chestPos;
    private boolean deposit;
    private boolean take;
    private Phase phase = Phase.DONE;
    private int timer;
    private Container opened;
    private final Set<Integer> failedSlots = new HashSet<>();
    private final Set<String> doneNeeds = new HashSet<>();
    private final Set<String> restocking = new HashSet<>();

    public ContainerInteraction() {
        super(ImmutableMap.of(), DURATION);
    }

    // ---- choosing a container ---------------------------------------------------------------------------------

    /** Which container to visit and what to do there. */
    public record Plan(BlockPos pos, boolean deposit, boolean take) { }

    /** Picks the container worth visiting right now, or null. Used by {@link StartTask}. */
    public static Plan plan(ServerLevel level, Villager villager) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        if (needs == null) return null;
        GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (site == null || !site.dimension().equals(level.dimension())) return null;

        boolean hasDeposits = false;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize() && !hasDeposits; i++) hasDeposits = needs.depositAmount(inv, i) > 0;

        record Candidate(BlockPos pos, int depositPref, int takePref, double dist) { }
        java.util.List<Candidate> candidates = new java.util.ArrayList<>();
        int bestDeposit = 0;
        int bestTake = 0;
        for (BlockPos p : BlockPos.betweenClosed(site.pos().offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                site.pos().offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
            if (level.getBlockEntity(p) == null) continue;
            Container c = HopperBlockEntity.getContainerAt(level, p);
            if (c == null) continue;
            boolean copper = level.getBlockState(p).is(BlockTags.COPPER_CHESTS);
            int depositPref = hasDeposits ? (copper ? 2 : 1) : 0;
            int takePref = canTakeSomething(needs, inv, c) ? (copper ? 1 : 2) : 0;
            if (depositPref == 0 && takePref == 0) continue;
            bestDeposit = Math.max(bestDeposit, depositPref);
            bestTake = Math.max(bestTake, takePref);
            candidates.add(new Candidate(p.immutable(), depositPref, takePref, p.distSqr(villager.blockPosition())));
        }

        Candidate best = null;
        for (Candidate c : candidates) {
            boolean serves = (c.depositPref() > 0 && c.depositPref() == bestDeposit)
                    || (c.takePref() > 0 && c.takePref() == bestTake);
            if (serves && (best == null || c.dist() < best.dist())) best = c;
        }
        if (best == null) return null;
        return new Plan(best.pos(),
                best.depositPref() > 0 && best.depositPref() == bestDeposit,
                best.takePref() > 0 && best.takePref() == bestTake);
    }

    private static boolean canTakeSomething(ProfessionNeeds needs, Container inv, Container chest) {
        for (ProfessionNeeds.Need need : needs.needs()) {
            if (!need.unmet(inv)) continue;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                if (!s.isEmpty() && need.match().test(s)) return true;
            }
        }
        return false;
    }

    // ---- the visit (runs in the exclusive task activity) --------------------------------------------------------

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        TaskState state = villager.getData(ModAttachments.TASK);
        return state.type == TaskState.Type.VISIT && state.chest != null;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        TaskState state = villager.getData(ModAttachments.TASK);
        chestPos = state.chest;
        deposit = state.deposit;
        take = state.take;
        Tasks.stopOthers(level, villager, this, time);
        phase = Phase.APPROACH;
        failedSlots.clear();
        doneNeeds.clear();
        restocking.clear();
        lookAtChest(villager);
        walkToChest(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return phase != Phase.DONE && chestPos != null && Tasks.active(villager)
                && Tasks.scheduleAllowsWork(level, villager);
    }

    private void lookAtChest(Villager villager) {
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(Vec3.atCenterOf(chestPos)));
    }

    private void walkToChest(Villager villager) {
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(chestPos), 0.5F, 1));
    }

    /** Stand still: nothing else is running, but make sure no leftover target pulls the villager away. */
    private static void holdStill(Villager villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        if (chestPos == null) return;
        switch (phase) {
            case APPROACH -> {
                lookAtChest(villager);
                if (villager.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5) > INTERACT_DIST_SQR) {
                    if (!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) walkToChest(villager);
                    return;
                }
                holdStill(villager);
                phase = Phase.LOOK;
                timer = PAUSE_LOOK;
            }
            case LOOK -> {
                holdStill(villager);
                lookAtChest(villager);
                if (--timer > 0) return;
                Container chest = HopperBlockEntity.getContainerAt(level, chestPos);
                if (chest == null) { phase = Phase.DONE; return; }
                opened = chest;
                chest.startOpen((ContainerUser) villager);
                ((ChestOpener) villager).industriousvillagers$setOpenedChestPos(chestPos);
                villager.swing(InteractionHand.MAIN_HAND);
                phase = Phase.TRANSFER;
                timer = PAUSE_OPEN;
            }
            case TRANSFER -> {
                holdStill(villager);
                lookAtChest(villager);
                if (--timer > 0) return;
                ProfessionNeeds needs = ProfessionNeeds.of(villager);
                boolean moved = needs != null && opened != null
                        && ((deposit && depositOne(needs, villager, opened)) || (take && takeOne(needs, villager, opened)));
                if (moved) {
                    villager.getInventory().setChanged();
                    opened.setChanged();
                    timer = PAUSE_STEP;
                } else {
                    phase = Phase.CLOSING;
                    timer = PAUSE_CLOSE;
                }
            }
            case CLOSING -> {
                holdStill(villager);
                lookAtChest(villager);
                if (--timer > 0) return;
                closeChest(villager);
                Vec3 away = villager.position().subtract(Vec3.atCenterOf(chestPos)).multiply(1, 0, 1).normalize().scale(4);
                villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET,
                        new BlockPosTracker(BlockPos.containing(villager.position().add(away))));
                phase = Phase.LOOK_AWAY;
                timer = PAUSE_AWAY;
            }
            case LOOK_AWAY -> {
                holdStill(villager);
                if (--timer > 0) return;
                phase = Phase.DONE;
            }
            case DONE -> { }
        }
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
        closeChest(villager); // also covers interruptions (night, panic, damage, timeout)
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        chestPos = null;
        phase = Phase.DONE;
        Tasks.finish(level, villager);
    }

    private void closeChest(Villager villager) {
        if (opened == null) return;
        opened.stopOpen((ContainerUser) villager);
        ((ChestOpener) villager).industriousvillagers$setOpenedChestPos(null);
        opened = null;
    }

    // ---- moving one stack -------------------------------------------------------------------------------------

    private boolean depositOne(ProfessionNeeds needs, Villager villager, Container chest) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (failedSlots.contains(i)) continue;
            int amount = needs.depositAmount(inv, i);
            if (amount <= 0) continue;
            ItemStack stack = inv.getItem(i);
            ItemStack rest = insert(chest, stack.copyWithCount(amount));
            int moved = amount - rest.getCount();
            if (moved <= 0) { failedSlots.add(i); continue; } // chest full for this item
            stack.shrink(moved);
            if (stack.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
            return true;
        }
        return false;
    }

    private boolean takeOne(ProfessionNeeds needs, Villager villager, Container chest) {
        SimpleContainer inv = villager.getInventory();
        for (ProfessionNeeds.Need need : needs.needs()) {
            if (doneNeeds.contains(need.id())) continue;
            int have = need.have(inv);
            boolean wanted = have < need.refillBelow() || (restocking.contains(need.id()) && have < need.target());
            if (!wanted) continue;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack s = chest.getItem(i);
                if (s.isEmpty() || !need.match().test(s)) continue;
                int n = Math.min(need.target() - have, s.getCount());
                ItemStack rest = inv.addItem(s.copyWithCount(n));
                int moved = n - rest.getCount();
                if (moved <= 0) break; // villager inventory full
                s.shrink(moved);
                if (s.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
                restocking.add(need.id());
                return true;
            }
            doneNeeds.add(need.id());
        }
        return false;
    }

    /** Inserts into the container, merging into matching stacks first; returns whatever did not fit. */
    private static ItemStack insert(Container c, ItemStack stack) {
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = 0; i < c.getContainerSize() && !stack.isEmpty(); i++) {
                if (!c.canPlaceItem(i, stack)) continue;
                ItemStack there = c.getItem(i);
                if (pass == 0) {
                    if (there.isEmpty() || !ItemStack.isSameItemSameComponents(there, stack)) continue;
                    int room = Math.min(there.getMaxStackSize(), c.getMaxStackSize()) - there.getCount();
                    int n = Math.min(room, stack.getCount());
                    if (n > 0) { there.grow(n); stack.shrink(n); }
                } else if (there.isEmpty()) {
                    int n = Math.min(Math.min(stack.getMaxStackSize(), c.getMaxStackSize()), stack.getCount());
                    c.setItem(i, stack.copyWithCount(n));
                    stack.shrink(n);
                }
            }
        }
        return stack;
    }
}
