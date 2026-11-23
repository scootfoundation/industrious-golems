package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.Machine;
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
import net.minecraft.world.item.component.SwingAnimation;
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
 * Digs the next rock of the mine (see {@link Trunk} for which) a block at a time: walk to a spot within reach, look at it, stand beside it, wind up and strike it (it breaks as the fist lands
 * lands), then take the next, for a dozen blocks before the work is carried off. The rock yields what an iron
 * pickaxe would get from it. While it digs the golem leaves the drops lying; it gathers them afterwards, a stack at a time (see PickUp). The
 * golem does not start a slice it cannot finish: when the pack is short of the supports and torches for it, it asks the chest for the kit.
 */
public class MineRock extends TaskRunner<MineRock.MinePlan> {

    /** Blocks dug before the golem stops to carry the drops to its chest. */
    private static final int BATCH = 12;
    /** Ticks spent trying to get within reach of one block before giving up on it. */
    private static final int STALL_TICKS = 160;
    private static final int IGNORE_TICKS = 6000;
    /** The tool the golem digs as if it held; it carries none. */

    /** The block chosen by {@link #plan}, the first of the batch. */
    public record MinePlan(MineFinder.Target first) { }

    private enum State { SEEK, APPROACH, SWING, SETTLE, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("mine-rock", "Miner: dig a batch of rock",
            "Stone golem. Digs up to a dozen exposed blocks bare-handed, one swing each, leaving the drops lying; then the pick-up and "
                    + "deposit tasks carry them to the chest.", State.class)
            .start(State.SEEK)
            .state(State.SEEK, "Choosing the next exposed block to dig (nearest first, skipping ones recently given up on).")
            .state(State.APPROACH, "Walking to a spot within reach of the block, looking at it.")
            .state(State.SWING, "Standing beside it: the smash clip plays and the block breaks as the fist lands.")
            .state(State.SETTLE, "The block is gone and the golem is settling from the blow.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.SEEK, State.APPROACH, "a block was found (the first one comes with the plan)")
            .edge(State.SEEK, State.DONE, "twelve blocks dug, or the next step is not digging, or the kit for it is short")
            .edge(State.APPROACH, State.SWING, "within reach with a clear line to the block")
            .edge(State.APPROACH, State.SEEK, "the block is already gone or no longer safe to cut, or unreachable for 8 s (ignored for 5 min)")
            .edge(State.SWING, State.SETTLE, "hit moment: the block breaks, the drops fall")
            .edge(State.SETTLE, State.SEEK, "the swing has settled")
            .anyTo(State.DONE, "a hostile mob comes near: the golem drops what it is doing to cower", State.DONE);

    private final Machine.Run<State> machine = GRAPH.run();
    private MineFinder.Target target;
    private int mined;
    private int stalled;
    private int progress;

    public MineRock() {
        super(MinePlan.class, 2400);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /**
     * The next thing the mine needs, once what lies about is carried off: rock to dig (this runner), blocks to place (see {@link BuildTrunk}),
     * or the kit to do either, fetched from the chest. When the chest cannot supply it the golem has nothing to do.
     */
    public static java.util.stream.Stream<com.scottcaldwell.industriousgolems.needs.Job> jobs(ServerLevel level, WorkGolem golem) {
        if (Cower.threatened(level, golem)) return Cower.jobs(level, golem);
        if (golem.isCarrying() || !EventHooks.canEntityGrief(level, golem)) return java.util.stream.Stream.empty();
        return com.scottcaldwell.industriousgolems.needs.Jobs.of(() -> {
            BlockPos chest = golem.jobSite(level);
            if (chest == null) return null;
            Trunk.Step step = next(level, golem, chest);
            if (step == null) return null;
            if (!step.affordable(part -> golem.packCount(part.match()))) return com.scottcaldwell.industriousgolems.needs.Job.needing("kit");
            if (!step.digs()) return com.scottcaldwell.industriousgolems.needs.Job.whenTidy(new BuildTrunk.Plan(step.builds()));
            BlockPos stand = MineFinder.fistSpot(level, step.dig(), golem.blockPosition());
            if (stand == null) { // nowhere to stand to cut it: leave it for a while
                golem.getData(ModAttachments.TASK).ignoreBlock(step.dig(), level.getGameTime() + IGNORE_TICKS);
                return null;
            }
            return com.scottcaldwell.industriousgolems.needs.Job.whenTidy(new MinePlan(new MineFinder.Target(step.dig(), stand)));
        });
    }

    private static Trunk.Step next(ServerLevel level, WorkGolem golem, BlockPos chest) {
        TaskState state = golem.getData(ModAttachments.TASK);
        long now = level.getGameTime();
        return Trunk.next(level, chest, golem.blockPosition(), pos -> state.ignoresBlock(pos, now));
    }

    /** The next block of the batch, or null when the next step is not digging (or cannot be afforded). */
    private static MineFinder.Target findTarget(ServerLevel level, WorkGolem golem) {
        BlockPos chest = golem.jobSite(level);
        if (chest == null) return null;
        Trunk.Step step = next(level, golem, chest);
        if (step == null || !step.digs() || !step.affordable(part -> golem.packCount(part.match()))) return null;
        BlockPos stand = MineFinder.fistSpot(level, step.dig(), golem.blockPosition());
        if (stand == null) {
            golem.getData(ModAttachments.TASK).ignoreBlock(step.dig(), level.getGameTime() + IGNORE_TICKS);
            return null;
        }
        return new MineFinder.Target(step.dig(), stand);
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        machine.reset();
        target = plan.first();
        machine.to(State.APPROACH);
        mined = 0;
        stalled = 0;
        progress = 0;
        golem.setPickupSuspended(true);
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (Cower.threatened(level, golem)) { done(); return; } // afraid: the cower job takes over
        switch (machine.state()) {
            case SEEK -> {
                if (!nextTarget(level, golem)) done();
                else machine.to(State.APPROACH);
            }
            case APPROACH -> approach(level, golem, time);
            case SWING -> swing(level, golem);
            case SETTLE -> {
                holdStill(golem);
                progress++;
                if (progress / 20.0F >= Anim.S_MINE_LEN) { target = null; progress = 0; machine.to(State.SEEK); }
            }
            case DONE -> { }
        }
    }

    private void approach(ServerLevel level, WorkGolem golem, long time) {
        BlockPos block = target.block();
        if (!MineFinder.isMineable(level, block, true)) { // already gone, or no longer safe to cut
            clearCrack(level, golem);
            target = null;
            machine.to(State.SEEK);
            return;
        }
        lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(block)));
        if (!inReach(level, golem, block)) {
            if (++stalled > STALL_TICKS) { // cannot get there: leave it alone for a while
                golem.getData(ModAttachments.TASK).ignoreBlock(block, time + IGNORE_TICKS);
                clearCrack(level, golem);
                target = null;
                machine.to(State.SEEK);
                return;
            }
            walkTo(golem, new BlockPosTracker(target.stand()), 1);
            return;
        }
        holdStill(golem);
        stalled = 0;
        Face.toward(golem, Vec3.atCenterOf(block)); // in reach: square up to the block and bring a fist down on it
        golem.playClip(Anim.S_MINE);
        progress = 0;
        machine.to(State.SWING);
        swing(level, golem);
    }

    private void swing(ServerLevel level, WorkGolem golem) {
        BlockPos block = target.block();
        holdStill(golem);
        progress++;
        float t = progress / 20.0F;
        if (t >= Anim.S_MINE_HIT) {
            BlockState state = level.getBlockState(block);
            golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            level.playSound(null, block, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.7F);
            clearCrack(level, golem);
            dig(level, golem, block, state);
            mined++;
            machine.to(State.SETTLE);
        } // no crack while it winds up: the block gives way only when the fist lands
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
        if (eye.distanceTo(center) > MineFinder.REACH || !MineFinder.fistReaches(golem, block)) return false;
        HitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, golem));
        return hit.getType() == HitResult.Type.MISS || (hit instanceof BlockHitResult b && b.getBlockPos().equals(block));
    }

    /** Breaks the block; what it drops is what an iron pickaxe would get from it. */
    private static void dig(ServerLevel level, WorkGolem golem, BlockPos pos, BlockState state) {
        BlockEntity entity = level.getBlockEntity(pos);
        Block.dropResources(state, level, pos, entity, golem, new ItemStack(Items.IRON_PICKAXE));
        level.destroyBlock(pos, false, golem);
    }

    private void clearCrack(ServerLevel level, WorkGolem golem) {
        if (target != null) level.destroyBlockProgress(golem.getId(), target.block(), -1);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        clearCrack(level, golem);
        golem.setPickupSuspended(false);
        target = null;
    }
}
