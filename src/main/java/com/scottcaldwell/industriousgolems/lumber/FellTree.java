package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Fells a natural tree log by log, bottom first: walk up, stop and look at the log, chop it bare-handed (the logs drop
 * as for a player and are carried off a stack at a time, see PickUp), pause, and repeat; then replant a sapling where the
 * trunk stood, if the golem is holding one. Runs in the exclusive task activity.
 */
public class FellTree extends TaskRunner<FellTree.FellPlan> {

    private static final double REACH = 5.0;
    private static final int LOOK_TICKS = 12;
    private static final int PAUSE_TICKS = 8;
    /** Ticks spent trying to reach one log before skipping it. */
    private static final int STALL_TICKS = 200;

    private enum Phase { WORK, LOOK, PAUSE, DONE }

    /** The tree chosen by {@link #plan}: chop {@code logs}, then replant at {@code replant}. */
    public record FellPlan(List<BlockPos> logs, BlockPos replant) { }

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

    /**
     * With a free hand or a sapling in it: a tree to fell (the sapling then goes in where it stood). With a sapling in
     * hand and a stump to replant: just the planting. Null when there is nothing to do, or mob griefing is off.
     */
    public static Object plan(ServerLevel level, WorkGolem golem) {
        BlockPos site = golem.jobSite(level);
        if (site == null || !EventHooks.canEntityGrief(level, golem)) return null;
        boolean sapling = golem.carried().is(ItemTags.SAPLINGS);
        if (golem.isCarrying() && !sapling) return null;
        BlockPos stump = golem.replantPos();
        if (sapling && stump != null && canPlantAt(level, stump, golem.carried())) return new FellPlan(List.of(), stump);
        TreeFinder.Tree tree = TreeFinder.find(level, site, golem.blockPosition());
        return tree == null ? null : new FellPlan(tree.logs(), tree.base());
    }

    /** A sapling from the chest would replant the last tree's stump; a tree still to fell wants one too. */
    public static String wants(ServerLevel level, WorkGolem golem) {
        BlockPos stump = golem.replantPos();
        if (golem.isCarrying()) return null;
        if (stump != null && level.getBlockState(stump).isAir()) return "saplings";
        BlockPos site = golem.jobSite(level);
        if (site == null) return null;
        return TreeFinder.find(level, site, golem.blockPosition()) == null ? null : "saplings";
    }

    private static boolean canPlantAt(ServerLevel level, BlockPos pos, ItemStack sapling) {
        return sapling.getItem() instanceof BlockItem item && level.getBlockState(pos).isAir()
                && item.getBlock().defaultBlockState().canSurvive(level, pos);
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        remaining.clear();
        remaining.addAll(plan.logs());
        golem.setReplantPos(plan.replant());
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
        if (phase == Phase.WORK) { // arrived: take up a sapling to plant, or just look at the log before swinging
            phase = Phase.LOOK;
            timer = LOOK_TICKS;
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
            if (level.getBlockState(log).is(BlockTags.LOGS)) {
                current = log;
                return true;
            }
            remaining.remove(0);
        }
        if (!planting && plan.replant() != null && golem.carried().is(ItemTags.SAPLINGS)
                && canPlantAt(level, plan.replant(), golem.carried())) {
            planting = true;
            current = plan.replant();
            return true;
        }
        return false;
    }

    private void chop(ServerLevel level, WorkGolem golem, BlockPos log) {
        level.destroyBlock(log, true, golem); // drops like a player's break; the stacks are carried off afterwards
    }

    private void plant(ServerLevel level, WorkGolem golem, BlockPos pos) {
        ItemStack stack = golem.carried();
        if (!stack.is(ItemTags.SAPLINGS) || !canPlantAt(level, pos, stack)) return;
        level.setBlockAndUpdate(pos, ((BlockItem) stack.getItem()).getBlock().defaultBlockState());
        level.playSound(null, pos, SoundEvents.CROP_PLANTED, SoundSource.BLOCKS, 1.0F, 1.0F);
        stack.shrink(1);
        if (stack.isEmpty()) golem.setCarried(ItemStack.EMPTY);
        golem.setReplantPos(null);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        current = null;
        remaining.clear();
        phase = Phase.DONE;
    }
}