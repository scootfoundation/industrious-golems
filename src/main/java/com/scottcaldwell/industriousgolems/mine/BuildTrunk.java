package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

/**
 * Finishes the slice of the mine the golem has just dug: bridges missing floor, stands the fence posts and lays the plank beam of a support,
 * hangs the torch. Each block is placed from the pack, one blow of the same arm swing the golem digs with.
 */
public class BuildTrunk extends TaskRunner<BuildTrunk.Plan> {

    private static final int STALL_TICKS = 160;
    private static final int IGNORE_TICKS = 6000;

    public record Plan(List<Trunk.Placement> builds) { }

    private enum State { SEEK, APPROACH, PLACE, SETTLE, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("build-trunk", "Miner: finish a slice of the mine",
            "Stone golem. Places what a slice needs from its pack: cobblestone over missing floor and into liquid that would flood the tunnel, a support's "
                    + "fence posts and plank beam, a torch.", State.class)
            .start(State.SEEK)
            .state(State.SEEK, "Taking the next block to place.")
            .state(State.APPROACH, "Walking to a spot within reach of it (not standing where it goes).")
            .state(State.PLACE, "In reach: the arm comes down and the block goes in at the hit moment, taken from the pack.")
            .state(State.SETTLE, "The block is in and the golem is settling from the swing.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.SEEK, State.APPROACH, "a block still needs placing and the pack has it")
            .edge(State.SEEK, State.DONE, "nothing left to place, or the pack ran out")
            .edge(State.APPROACH, State.PLACE, "within reach with the block's place free of the golem")
            .edge(State.APPROACH, State.SEEK, "the place is already filled, or unreachable for 8 s (ignored for 5 min)")
            .edge(State.PLACE, State.SETTLE, "hit moment: the block is placed")
            .edge(State.SETTLE, State.SEEK, "the swing has settled")
            .anyTo(State.DONE, "a hostile mob comes near: the golem drops what it is doing to cower", State.DONE);

    private final Machine.Run<State> machine = GRAPH.run();
    private final Queue<Trunk.Placement> queue = new ArrayDeque<>();
    private Trunk.Placement current;
    private BlockPos stand;
    private int stalled;
    private int progress;
    private double nearest;

    public BuildTrunk() {
        super(Plan.class, 2400);
    }

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        machine.reset();
        queue.clear();
        queue.addAll(plan.builds());
        current = null;
        stand = null;
        stalled = 0;
        progress = 0;
        nearest = Double.MAX_VALUE;
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
            case SEEK -> seek(level, golem);
            case APPROACH -> approach(level, golem, time);
            case PLACE -> place(level, golem);
            case SETTLE -> {
                holdStill(golem);
                if (++progress / 20.0F >= Anim.S_MINE_LEN) { progress = 0; current = null; machine.to(State.SEEK); }
            }
            case DONE -> { }
        }
    }

    private void seek(ServerLevel level, WorkGolem golem) {
        while ((current = queue.poll()) != null) {
            if (golem.packCount(current.part().match()) <= 0) { done(); return; } // short of it: the planner asks the chest for more
            if (!canPlace(level, current)) continue;
            stalled = 0;
            nearest = Double.MAX_VALUE;
            stand = null;
            machine.to(State.APPROACH);
            return;
        }
        done();
    }

    /** The spot is still empty (a floor gap, or air for a post, beam or torch); cobblestone also goes into liquid, to plug it. */
    private static boolean canPlace(ServerLevel level, Trunk.Placement placement) {
        BlockState state = level.getBlockState(placement.pos());
        return state.getCollisionShape(level, placement.pos()).isEmpty() && (state.getFluidState().isEmpty() || placement.part() == Kit.COBBLE);
    }

    private void approach(ServerLevel level, WorkGolem golem, long time) {
        BlockPos at = current.pos();
        if (!canPlace(level, current)) { machine.to(State.SEEK); return; }
        if (stand == null) {
            stand = MineFinder.standingSpot(level, at, golem.blockPosition(), spot -> clearOf(spot, at));
            if (stand == null) { giveUp(golem, time); return; }
        }
        lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(at)));
        boolean near = standAt(golem, Vec3.atBottomCenterOf(stand), 0.5) && golem.getEyePosition().distanceTo(Vec3.atCenterOf(at)) <= MineFinder.REACH && clearOf(golem.blockPosition(), at);
        if (!near) {
            double distance = golem.position().distanceTo(Vec3.atCenterOf(stand));
            if (distance < nearest - 0.25) { nearest = distance; stalled = 0; } // a long way is fine so long as it gets nearer
            if (++stalled > STALL_TICKS) { giveUp(golem, time); return; }
            return;
        }
        holdStill(golem);
        Face.toward(golem, Vec3.atCenterOf(at));
        golem.playClip(Anim.S_MINE);
        progress = 0;
        machine.to(State.PLACE);
        place(level, golem);
    }

    /** The golem (one block wide, a block and a half tall) is not in the way of the block going in at {@code at}: it stands in another column, or well below it. */
    private static boolean clearOf(BlockPos standing, BlockPos at) {
        return standing.getX() != at.getX() || standing.getZ() != at.getZ() || at.getY() >= standing.getY() + 2;
    }

    private void giveUp(WorkGolem golem, long time) {
        golem.getData(ModAttachments.TASK).ignoreBlock(current.pos(), time + IGNORE_TICKS);
        current = null;
        done();
    }

    private void place(ServerLevel level, WorkGolem golem) {
        holdStill(golem);
        if (++progress / 20.0F < Anim.S_MINE_HIT) return;
        ItemStack item = golem.packTake(current.part().match());
        if (!item.isEmpty() && canPlace(level, current)) {
            BlockState base = current.part().state(item);
            if (current.facing() != null && base.hasProperty(HorizontalDirectionalBlock.FACING)) base = base.setValue(HorizontalDirectionalBlock.FACING, current.facing());
            BlockState state = Block.updateFromNeighbourShapes(base, level, current.pos());
            level.setBlock(current.pos(), state, 3);
            level.playSound(null, current.pos(), state.getSoundType().getPlaceSound(), SoundSource.BLOCKS, 1.0F, 0.8F);
            golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
        } else if (!item.isEmpty()) golem.packAdd(item);
        machine.to(State.SETTLE);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        golem.setPickupSuspended(false);
        queue.clear();
        current = null;
    }
}
