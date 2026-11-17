package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.behavior.TaskState;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Digs rock a block at a time: walk to a spot within reach of an exposed block, look at it, hammer at it (the crack
 * grows with each swing) and break it, then take the next one, for a dozen blocks before the work is carried off. The
 * rock yields what an iron pickaxe would get from it, so ore drops as ore but nothing harder than iron is touched.
 * While it digs the golem leaves the drops lying; it gathers them afterwards, a stack at a time (see PickUp).
 */
public class MineRock extends TaskRunner<MineRock.MinePlan> {

    /** Blocks dug before the golem stops to carry the drops to its chest. */
    private static final int BATCH = 12;
    /** Ticks spent trying to get within reach of one block before giving up on it. */
    private static final int STALL_TICKS = 160;
    private static final int IGNORE_TICKS = 6000;
    private static final int SWING_EVERY = 8;
    /** The tool the golem digs as if it held; it carries none. */
    private static final ItemStack PICKAXE = new ItemStack(Items.IRON_PICKAXE);

    /** The block chosen by {@link #plan}, the first of the batch. */
    public record MinePlan(MineFinder.Target first) { }

    private MineFinder.Target target;
    private boolean done = true;
    private int mined;
    private int stalled;
    private int progress;

    public MineRock() {
        super(MinePlan.class, 2400);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /** Rock to dig, when the golem has a free hand and may break blocks; null otherwise. */
    public static Object plan(ServerLevel level, WorkGolem golem) {
        if (golem.isCarrying() || !EventHooks.canEntityGrief(level, golem)) return null;
        MineFinder.Target target = findTarget(level, golem);
        return target == null ? null : new MinePlan(target);
    }

    /** The golem needs no supplies: it digs bare-handed. */
    public static String wants(ServerLevel level, WorkGolem golem) {
        return null;
    }

    private static MineFinder.Target findTarget(ServerLevel level, WorkGolem golem) {
        BlockPos chest = golem.jobSite(level);
        if (chest == null) return null;
        TaskState state = golem.getData(ModAttachments.TASK);
        long now = level.getGameTime();
        return MineFinder.find(level, chest, golem.blockPosition(), pos -> state.ignoresBlock(pos, now));
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        target = plan.first();
        done = false;
        mined = 0;
        stalled = 0;
        progress = 0;
        golem.setPickupSuspended(true);
    }

    @Override
    protected boolean allowedNow(ServerLevel level, WorkGolem golem) {
        return !done && super.allowedNow(level, golem);
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (target == null && !nextTarget(level, golem)) {
            finish();
            return;
        }
        BlockPos block = target.block();
        if (!MineFinder.isMineable(level, block)) { // already gone, or no longer safe to cut
            clearCrack(level, golem);
            target = null;
            return;
        }

        lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(block)));
        if (!inReach(level, golem, block)) {
            if (++stalled > STALL_TICKS) { // cannot get there: leave it alone for a while
                golem.getData(ModAttachments.TASK).ignoreBlock(block, time + IGNORE_TICKS);
                clearCrack(level, golem);
                target = null;
                return;
            }
            walkTo(golem, new BlockPosTracker(target.stand()), 1);
            return;
        }

        holdStill(golem);
        stalled = 0;
        BlockState state = level.getBlockState(block);
        int total = digTicks(level, block, state);
        if (progress % SWING_EVERY == 0) {
            golem.swing(InteractionHand.MAIN_HAND);
            SoundEvent hit = state.getSoundType().getHitSound();
            level.playSound(null, block, hit, SoundSource.BLOCKS, 0.6F, 0.8F);
        }
        progress++;
        if (progress < total) {
            level.destroyBlockProgress(golem.getId(), block, progress * 10 / total);
            return;
        }
        clearCrack(level, golem);
        dig(level, golem, block, state);
        mined++;
        target = null;
        progress = 0;
    }

    /** The next block to dig, unless the batch is done or the golem has run out of rock. */
    private boolean nextTarget(ServerLevel level, WorkGolem golem) {
        if (mined >= BATCH) return false;
        target = findTarget(level, golem);
        stalled = 0;
        progress = 0;
        return target != null;
    }

    /** Bare-handed work is slow: a stone block takes about a second and a half, ore and deepslate about twice that. */
    private static int digTicks(ServerLevel level, BlockPos pos, BlockState state) {
        return 8 + Math.round(state.getDestroySpeed(level, pos) * 12.0F);
    }

    private static boolean inReach(ServerLevel level, WorkGolem golem, BlockPos block) {
        Vec3 eye = golem.getEyePosition();
        Vec3 center = Vec3.atCenterOf(block);
        if (eye.distanceTo(center) > MineFinder.REACH) return false;
        HitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, golem));
        return hit.getType() == HitResult.Type.MISS || (hit instanceof BlockHitResult b && b.getBlockPos().equals(block));
    }

    /** Breaks the block; what it drops is what an iron pickaxe would get from it. */
    private static void dig(ServerLevel level, WorkGolem golem, BlockPos pos, BlockState state) {
        BlockEntity entity = level.getBlockEntity(pos);
        Block.dropResources(state, level, pos, entity, golem, PICKAXE);
        level.destroyBlock(pos, false, golem);
    }

    private void clearCrack(ServerLevel level, WorkGolem golem) {
        if (target != null) level.destroyBlockProgress(golem.getId(), target.block(), -1);
        progress = 0;
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        clearCrack(level, golem);
        golem.setPickupSuspended(false);
        target = null;
        done = true;
    }
}
