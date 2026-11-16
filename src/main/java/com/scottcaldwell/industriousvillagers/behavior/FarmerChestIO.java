package com.scottcaldwell.industriousvillagers.behavior;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousvillagers.ModProfessions;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.HopperBlockEntity;

import java.util.function.Predicate;

/**
 * PRD: roles "pull inputs from and deposit outputs to adjacent chests". The farmer periodically visits a
 * container near its job site, deposits harvest and surplus seeds, and takes seeds / bread / a hoe when it
 * is running low.
 */
public class FarmerChestIO extends Behavior<Villager> {

    /** Containers within this many blocks (horizontally; +-2 vertically) of the job site count as "adjacent". */
    public static final int SEARCH_RADIUS = 3;
    private static final int COOLDOWN_TICKS = 200;
    private static final int KEEP_SEEDS = 16;
    private static final int LOW_SEEDS = 8;
    private static final int DURATION = 400;
    private static final double INTERACT_DIST_SQR = 2.5 * 2.5;

    private long nextAttempt = 0;
    private BlockPos chestPos;
    private boolean done;

    public FarmerChestIO() {
        super(ImmutableMap.of(
                MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT,
                MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_ABSENT
        ), DURATION);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (!villager.getVillagerData().profession().is(ModProfessions.FARMER_KEY)) return false;
        if (level.getGameTime() < nextAttempt) return false;
        nextAttempt = level.getGameTime() + COOLDOWN_TICKS;

        GlobalPos site = villager.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (site == null || !site.dimension().equals(level.dimension())) return false;

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(site.pos().offset(-SEARCH_RADIUS, -2, -SEARCH_RADIUS),
                site.pos().offset(SEARCH_RADIUS, 2, SEARCH_RADIUS))) {
            if (level.getBlockEntity(p) == null) continue;
            Container c = HopperBlockEntity.getContainerAt(level, p);
            if (c == null || !hasWork(villager, c)) continue;
            double d = p.distSqr(villager.blockPosition());
            if (d < bestDist) {
                bestDist = d;
                best = p.immutable();
            }
        }
        chestPos = best;
        return best != null;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        done = false;
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(chestPos));
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(new BlockPosTracker(chestPos), 0.5F, 1));
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return !done && chestPos != null;
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        if (chestPos == null) return;
        if (villager.distanceToSqr(chestPos.getX() + 0.5, chestPos.getY() + 0.5, chestPos.getZ() + 0.5) > INTERACT_DIST_SQR) return;
        Container chest = HopperBlockEntity.getContainerAt(level, chestPos);
        if (chest != null) transfer(villager, chest);
        done = true;
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        nextAttempt = time + COOLDOWN_TICKS;
        chestPos = null;
    }

    // ---- what moves where -------------------------------------------------------------------------------------

    private static boolean isSeed(ItemStack s) { return s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS); }
    private static boolean isHoe(ItemStack s) { return s.is(ItemTags.HOES); }
    private static boolean isOutput(ItemStack s) {
        return s.is(Items.WHEAT) || s.is(Items.BEETROOT) || s.is(Items.MELON_SLICE) || s.is(Items.PUMPKIN)
                || s.is(Items.MELON) || s.is(Items.SWEET_BERRIES);
    }

    private static int count(Container c, Predicate<ItemStack> p) {
        int n = 0;
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack s = c.getItem(i);
            if (!s.isEmpty() && p.test(s)) n += s.getCount();
        }
        return n;
    }

    private static boolean hasWork(Villager v, Container chest) {
        SimpleContainer inv = v.getInventory();
        if (count(inv, FarmerChestIO::isOutput) > 0) return true;
        if (count(inv, FarmerChestIO::isSeed) > KEEP_SEEDS) return true;
        if (count(inv, FarmerChestIO::isSeed) < LOW_SEEDS && count(chest, FarmerChestIO::isSeed) > 0) return true;
        if (count(inv, FarmerChestIO::isHoe) == 0 && count(chest, FarmerChestIO::isHoe) > 0) return true;
        return v.wantsMoreFood() && count(chest, s -> s.is(Items.BREAD)) > 0;
    }

    private static void transfer(Villager v, Container chest) {
        SimpleContainer inv = v.getInventory();

        // Deposit: all harvest, and seeds beyond what the farmer keeps for replanting.
        int seedsToKeep = KEEP_SEEDS;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            int move = 0;
            if (isOutput(s)) move = s.getCount();
            else if (isSeed(s)) {
                int keep = Math.min(seedsToKeep, s.getCount());
                seedsToKeep -= keep;
                move = s.getCount() - keep;
            }
            if (move <= 0) continue;
            ItemStack rest = insert(chest, s.copyWithCount(move));
            s.shrink(move - rest.getCount());
            if (s.isEmpty()) inv.setItem(i, ItemStack.EMPTY);
        }

        // Withdraw: seeds when low, a hoe when none, bread when hungry.
        if (count(inv, FarmerChestIO::isSeed) < LOW_SEEDS) {
            take(v, chest, FarmerChestIO::isSeed, KEEP_SEEDS - count(inv, FarmerChestIO::isSeed));
        }
        if (count(inv, FarmerChestIO::isHoe) == 0) take(v, chest, FarmerChestIO::isHoe, 1);
        if (v.wantsMoreFood()) take(v, chest, s -> s.is(Items.BREAD), 6);

        inv.setChanged();
        chest.setChanged();
    }

    private static void take(Villager v, Container chest, Predicate<ItemStack> wanted, int max) {
        for (int i = 0; i < chest.getContainerSize() && max > 0; i++) {
            ItemStack s = chest.getItem(i);
            if (s.isEmpty() || !wanted.test(s)) continue;
            int n = Math.min(max, s.getCount());
            ItemStack rest = v.getInventory().addItem(s.copyWithCount(n));
            int moved = n - rest.getCount();
            s.shrink(moved);
            if (s.isEmpty()) chest.setItem(i, ItemStack.EMPTY);
            max -= moved;
        }
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
