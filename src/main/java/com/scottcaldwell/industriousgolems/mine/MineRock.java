package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.behavior.ClipRun;
import com.scottcaldwell.industriousgolems.behavior.Task;
import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.Approach;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.behavior.TaskState;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.needs.Job;
import com.scottcaldwell.industriousgolems.needs.Jobs;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Digs one rock of the mine (see {@link Trunk} for which): walk up beside it, wind up and strike it, and it breaks as the fist lands. The rock yields
 * what an iron pickaxe would get from it, left lying for the golem to pick up (see PickUp).
 *
 * <p>{@link #jobs} is the miner's whole decision, in this order, the first it can do: run from a hostile mob to the chest; seal liquid with
 * cobblestone; pick up what lies within five blocks; finish the brace of the mine (or start it); dig the next block, if its drops would fit; fetch
 * supplies and put the diggings in the chest; and when none of that can be done for want of supplies or room, fume at the chest.
 */
public class MineRock extends TaskRunner<MineRock.MinePlan> {

    /** The tool the golem digs as if it held; it carries none. */
    private static final ItemStack PICK = new ItemStack(Items.IRON_PICKAXE);
    /** Ticks of walking without getting any nearer to the block before giving up on it. */
    private static final int STALL_TICKS = 160;
    private static final int IGNORE_TICKS = 6000;

    /** The block chosen by {@link #jobs}, and where to stand to strike it. */
    public record MinePlan(MineFinder.Target first) implements Task { }

    private enum State { APPROACH, SWING, SETTLE, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("mine-rock", "Miner: dig a block",
            "Stone golem. Digs one exposed block bare-handed, leaving the drops lying; the pick-up task then takes them into its pack.", State.class)
            .start(State.APPROACH)
            .state(State.APPROACH, "Walking to the open floor beside the block, looking at it.")
            .state(State.SWING, "Beside it: the smash clip plays and the block breaks as the fist lands.")
            .state(State.SETTLE, "The block is gone and the golem is settling from the blow.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.APPROACH, State.SWING, "beside the block with a clear line to it")
            .edge(State.APPROACH, State.DONE, "the block is already gone or no longer safe to cut, or unreachable for 8 s (ignored for 5 min)")
            .edge(State.SWING, State.SETTLE, "hit moment: the block breaks, the drops fall")
            .edge(State.SETTLE, State.DONE, "the swing has settled")
            .anyTo(State.DONE, "a hostile mob comes near: the golem drops what it is doing to cower", State.DONE);

    private final Machine.Run<State> machine = GRAPH.run();
    private MineFinder.Target target;
    private final Approach approach = new Approach(STALL_TICKS);
    private ClipRun clip;

    public MineRock() {
        super(MinePlan.class, 2400);
    }

    // ---- choosing work ----------------------------------------------------------------------------------------

    /** What the mine needs next, and whether the golem has what it takes to do it. */
    private record Work(Trunk.Step step, BlockPos stand, boolean supplied) { }

    /**
     * The jobs in the order the golem prefers them; see the class comment. Each is worked out only when the ones before it fall through.
     */
    public static Stream<Job> jobs(ServerLevel level, WorkGolem golem) {
        if (Cower.threatened(level, golem)) return Cower.jobs(level, golem);
        if (!EventHooks.canEntityGrief(level, golem)) return Stream.empty();
        BlockPos chest = golem.jobSite(level);
        if (chest == null) return Stream.empty();
        Work[] memo = new Work[1];
        boolean[] known = new boolean[1];
        Supplier<Work> work = () -> {
            if (!known[0]) { memo[0] = work(level, golem, chest); known[0] = true; }
            return memo[0];
        };
        return Jobs.of(
                // liquid is sealed before anything else
                () -> {
                    Work w = work.get();
                    return w != null && w.step().plug() && w.supplied() ? Job.now(new BuildTrunk.Plan(w.step().builds())) : null;
                },
                // then, once what lies about is picked up: the brace, or the next block
                () -> {
                    Work w = work.get();
                    if (w == null || w.step().plug() || !w.supplied()) return null;
                    if (!w.step().digs()) return Job.whenTidy(new BuildTrunk.Plan(w.step().builds()));
                    return Job.whenTidy(new MinePlan(new MineFinder.Target(w.step().dig(), w.stand())));
                },
                // then off to the chest: for what it lacks, or to put the diggings away
                () -> needsChest(golem, work.get()) ? Job.needing("kit") : null,
                // and when the chest cannot help, it fumes there
                () -> stuck(work.get()) ? Job.whenTidy(new Fume.Plan()) : null);
    }

    /** The next step of the mine, and whether the pack has what it takes (the next block of the brace, cobblestone for a plug, room for the drops). */
    private static Work work(ServerLevel level, WorkGolem golem, BlockPos chest) {
        TaskState state = golem.getData(ModAttachments.TASK);
        long now = level.getGameTime();
        Trunk.Step step = Trunk.next(level, chest, golem.blockPosition(), pos -> state.ignoresBlock(pos, now));
        if (step == null) return null;
        if (step.digs()) {
            BlockPos stand = MineFinder.fistSpot(level, step.dig(), golem.blockPosition());
            if (stand == null) { // nowhere to stand beside it: leave it for a while
                state.ignoreBlock(step.dig(), now + IGNORE_TICKS);
                return null;
            }
            return new Work(step, stand, golem.pack().fitsAll(drops(level, golem, step.dig())));
        }
        return new Work(step, null, golem.pack().count(step.builds().get(0).part().match()) > 0);
    }

    /** What breaking the block would drop (as an iron pickaxe would get it). */
    private static List<ItemStack> drops(ServerLevel level, WorkGolem golem, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return Block.getDrops(state, level, pos, level.getBlockEntity(pos), golem, PICK);
    }

    /**
     * The golem has something to do at the chest: it lacks what the next step needs (supplies, or room for the drops), or there is nothing left to
     * dig or build and it has diggings to put away. It does not make the trip merely for having picked something up.
     */
    private static boolean needsChest(WorkGolem golem, Work w) {
        return w == null ? golem.pack().hasLoot() : !w.supplied();
    }

    /** The mine has work the golem cannot do for want of something (supplies, or room for the diggings). */
    private static boolean stuck(Work w) {
        return w != null && !w.supplied();
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        machine.reset();
        target = plan.first();
        machine.to(State.APPROACH);
        approach.reset();
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
            case APPROACH -> approach(level, golem, time);
            case SWING, SETTLE -> {
                holdStill(golem);
                if (clip.tick()) done();
            }
            case DONE -> { }
        }
    }

    private void approach(ServerLevel level, WorkGolem golem, long time) {
        BlockPos block = target.block();
        if (!MineFinder.isMineable(level, block, true)) { done(); return; } // already gone, or no longer safe to cut
        lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(block)));
        // squares up exactly on the spot beside it
        Approach.Outcome outcome = approach.stand(golem, Vec3.atBottomCenterOf(target.stand()), 0.5, () -> inReach(level, golem, block));
        if (outcome != Approach.Outcome.ARRIVED) {
            if (outcome == Approach.Outcome.UNREACHABLE) { // cannot get there: leave it alone for a while
                golem.getData(ModAttachments.TASK).ignoreBlock(block, time + IGNORE_TICKS);
                done();
            }
            return;
        }
        holdStill(golem);
        Face.toward(golem, Vec3.atCenterOf(block)); // beside it: square up to the block and bring a fist down on it
        // no crack while it winds up: the block gives way only when the fist lands
        clip = ClipRun.play(golem, Anim.S_MINE, Anim.S_MINE_LEN).at(Anim.S_MINE_HIT, () -> strike(level, golem, block));
        machine.to(State.SWING);
    }

    private void strike(ServerLevel level, WorkGolem golem, BlockPos block) {
        BlockState state = level.getBlockState(block);
        golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        level.playSound(null, block, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 1.0F, 0.7F);
        dig(level, golem, block, state);
        machine.to(State.SETTLE);
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
        Block.dropResources(state, level, pos, entity, golem, PICK);
        level.destroyBlock(pos, false, golem);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        golem.setPickupSuspended(false);
        target = null;
    }
}
