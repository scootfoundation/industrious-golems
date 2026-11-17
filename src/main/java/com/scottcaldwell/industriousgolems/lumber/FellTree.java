package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.behavior.Drops;
import com.scottcaldwell.industriousgolems.behavior.HeldItem;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import com.scottcaldwell.industriousgolems.needs.ToolWear;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Fells a natural tree log by log, bottom first: walk up, stop and look at the log, chop it (the axe wears), pause,
 * and repeat; then replant a sapling where the trunk stood. Runs in the exclusive task activity.
 */
public class FellTree extends TaskRunner<FellTree.FellPlan> {

    private static final double REACH = 5.0;
    private static final int LOOK_TICKS = 12;
    private static final int PAUSE_TICKS = 8;
    /** Ticks spent trying to reach one log before skipping it. */
    private static final int STALL_TICKS = 200;

    private enum Phase { WORK, LOOK, PAUSE, DONE }

    /** The tree chosen by {@link #plan}: chop {@code logs}, then replant at {@code replant}. */
    public record FellPlan(List<BlockPos> logs, BlockPos replant, net.minecraft.world.level.block.Block logType) { }

    private final List<BlockPos> remaining = new ArrayList<>();
    private BlockPos current;
    private boolean planting;
    private Phase phase = Phase.DONE;
    private int timer;
    private int stalled;

    public FellTree() {
        super(FellPlan.class, 1800);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /** Picks a tree to fell, or null (no axe, nothing worth felling, or mob griefing is off). */
    public static Object plan(ServerLevel level, WorkGolem golem) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        GlobalPos site = golem.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (needs == null || site == null || !site.dimension().equals(level.dimension())) return null;
        if (!net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, golem)) return null;
        if (!needs.has(golem, "axe")) return null;
        TreeFinder.Tree tree = TreeFinder.find(level, site.pos(), golem.blockPosition());
        return tree == null ? null : new FellPlan(tree.logs(), tree.base(), tree.logType());
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        remaining.clear();
        remaining.addAll(plan.logs());
        current = null;
        planting = false;
        phase = Phase.WORK;
        stalled = 0;
    }

    @Override
    protected boolean allowedNow(ServerLevel level, WorkGolem golem) {
        return phase != Phase.DONE && super.allowedNow(level, golem);
    }
    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (phase == Phase.PAUSE) {
            holdStill(golem);
            if (--timer <= 0) { phase = Phase.WORK; current = null; }
            return;
        }
        if (current == null && !nextTarget(level, golem)) { finish(); return; }

        lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(current)));
        double reach = golem.getEyePosition().distanceTo(Vec3.atCenterOf(current));
        if (reach > REACH) {
            phase = Phase.WORK;
            if (++stalled > STALL_TICKS) { // cannot get there: give up on this one
                stalled = 0;
                if (planting) planting = false; else remaining.remove(current);
                current = null;
                return;
            }
            walkTo(golem, new BlockPosTracker(current), 2);
            return;
        }

        holdStill(golem);
        stalled = 0;
        if (phase == Phase.WORK) { // arrived: take up the axe (or a sapling) and look before swinging
            phase = Phase.LOOK;
            timer = LOOK_TICKS;
            int slot = planting ? findSapling(golem) : ProfessionNeeds.of(golem).need("axe").slot(golem.getInventory());
            if (slot >= 0) HeldItem.hold(golem, golem.getInventory().getItem(slot));
        }
        if (--timer > 0) return;
        golem.swing(InteractionHand.MAIN_HAND);
        if (planting) {
            plant(level, golem, current);
            finish();
            return;
        }
        chop(level, golem, current);
        remaining.remove(current);
        phase = Phase.PAUSE;
        timer = PAUSE_TICKS;
    }

    /** Next log still standing, then (when the logs are gone) the spot to replant. False when there is nothing left. */
    private boolean nextTarget(ServerLevel level, WorkGolem golem) {
        while (!remaining.isEmpty()) {
            BlockPos log = remaining.get(0);
            if (level.getBlockState(log).is(net.minecraft.tags.BlockTags.LOGS) && hasAxe(golem)) {
                current = log;
                return true;
            }
            remaining.remove(0);
        }
        if (!planting && plan.replant() != null && findSapling(golem) >= 0
                && level.getBlockState(plan.replant()).isAir()) {
            planting = true;
            current = plan.replant();
            return true;
        }
        return false;
    }

    private boolean hasAxe(WorkGolem golem) {
        return ProfessionNeeds.of(golem).has(golem, "axe");
    }

    private void chop(ServerLevel level, WorkGolem golem, BlockPos log) {
        level.destroyBlock(log, true, golem);
        ToolWear.use(level, golem, "axe");
        Drops.collectFresh(level, golem, Vec3.atCenterOf(log));
    }

    private void plant(ServerLevel level, WorkGolem golem, BlockPos pos) {
        int slot = findSapling(golem);
        if (slot < 0 || !level.getBlockState(pos).isAir()) return;
        ItemStack stack = golem.getInventory().getItem(slot);
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;
        BlockState sapling = blockItem.getBlock().defaultBlockState();
        if (!sapling.canSurvive(level, pos)) return;
        level.setBlockAndUpdate(pos, sapling);
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        golem.getInventory().removeItem(slot, 1);
    }

    /** The sapling of the felled species if carried, otherwise any sapling. */
    private int findSapling(WorkGolem golem) {
        Identifier logId = BuiltInRegistries.BLOCK.getKey(plan.logType());
        String species = logId.getPath().replace("stripped_", "").replace("_log", "").replace("_wood", "");
        Item matching = BuiltInRegistries.ITEM.getOptional(logId.withPath(species + "_sapling")).orElse(null);
        int any = -1;
        SimpleContainer inv = golem.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !stack.is(ItemTags.SAPLINGS)) continue;
            if (matching != null && stack.is(matching)) return i;
            if (any < 0) any = i;
        }
        return any;
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        current = null;
        remaining.clear();
        phase = Phase.DONE;
    }
}