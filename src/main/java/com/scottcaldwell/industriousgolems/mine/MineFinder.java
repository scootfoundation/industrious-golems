package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.function.Predicate;

/**
 * What the stone golem may dig and where it can stand to do it. Which block comes next is {@link Trunk}'s decision; this says whether a
 * block is safe to cut (open to the air on a side, never where the cut would let a liquid in or bring sand down).
 */
public final class MineFinder {

    /** Furthest the golem can mine from where it stands, measured from its eyes. */
    public static final double REACH = 5.0;

    private MineFinder() { }

    /** A block to mine and the spot to stand on while doing it. */
    public record Target(BlockPos block, BlockPos stand) { }

    /** Whether the golem digs this kind of block at all. */
    public static boolean isRock(BlockState state) {
        return state.is(ModTags.MINER_STONE) || state.is(ModTags.MINER_ORES);
    }

    /** Rock that is open to the air on some side, with no liquid beside it and nothing that would fall above it. */
    public static boolean isMineable(ServerLevel level, BlockPos pos) {
        if (!isRock(level.getBlockState(pos))) return false;
        if (level.getBlockState(pos.above()).getBlock() instanceof FallingBlock) return false;
        boolean exposed = false;
        for (Direction dir : Direction.values()) {
            BlockPos side = pos.relative(dir);
            BlockState state = level.getBlockState(side);
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty()) return false;
            if (state.getCollisionShape(level, side).isEmpty()) exposed = true;
        }
        return exposed;
    }

    /** The closest open floor within reach of the block, with room to stand; null when there is none. */
    public static BlockPos standingSpot(ServerLevel level, BlockPos block, BlockPos from) {
        return standingSpot(level, block, from, pos -> true);
    }

    /** As above, among the spots {@code allowed} lets it stand on. */
    public static BlockPos standingSpot(ServerLevel level, BlockPos block, BlockPos from, Predicate<BlockPos> allowed) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(block.offset(-2, -3, -2), block.offset(2, 0, 2))) {
            if (!canStandAt(level, pos) || !allowed.test(pos)) continue;
            if (Vec3.atCenterOf(pos).distanceTo(Vec3.atCenterOf(block)) > REACH - 1.5) continue;
            double distance = pos.distSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        return isOpen(level, pos) && isOpen(level, pos.above())
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
                && level.getBlockState(pos.below()).getFluidState().isEmpty();
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty();
    }
}
