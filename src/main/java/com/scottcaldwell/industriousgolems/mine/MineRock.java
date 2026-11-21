package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.behavior.TaskState;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
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
    /** The tool the golem digs as if it held; it carries none. */
    private static final ItemStack PICKAXE = new ItemStack(Items.IRON_PICKAXE);

    /** The block chosen by {@link #plan}, the first of the batch. */
    public record MinePlan(MineFinder.Target first) { }

    private MineFinder.Target target;
    private boolean done = true;
    private int mined;
    private int stalled;
    private int progress;
    private boolean smashing;
    private boolean dug;

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
        smashing = false;
        dug = false;
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
        if (!smashing && !MineFinder.isMineable(level, block)) { // already gone, or no longer safe to cut
            clearCrack(level, golem);
            target = null;
            return;
        }
        if (smashing && dug) { // the block is gone; the golem is still settling from the blow
            holdStill(golem);
            progress++;
            if (progress / 20.0F >= Anim.S_MINE_LEN) { smashing = false; dug = false; target = null; progress = 0; }
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
        if (!smashing) { // in reach: square up to the block and bring a fist down on it
            Face.toward(golem, Vec3.atCenterOf(block));
            golem.playClip(Anim.S_MINE);
            smashing = true;
            dug = false;
            progress = 0;
        }
        progress++;
        float t = progress / 20.0F;
        if (!dug && t >= Anim.S_MINE_HIT) {
            golem.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, block, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.7F);
            clearCrack(level, golem);
            dig(level, golem, block, state);
            mined++;
            dug = true;
        } else if (!dug && progress % 6 == 0) {
            level.destroyBlockProgress(golem.getId(), block, Math.min(9, Math.round(t / Anim.S_MINE_HIT * 10.0F)));
        }
        if (t >= Anim.S_MINE_LEN) { // the swing has settled: on to the next block
            smashing = false;
            dug = false;
            target = null;
            progress = 0;
        }
    }

    /** The next block to dig, unless the batch is done or the golem has run out of rock. */
    private boolean nextTarget(ServerLevel level, WorkGolem golem) {
        if (mined >= BATCH) return false;
        target = findTarget(level, golem);
        stalled = 0;
        progress = 0;
        return target != null;
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
