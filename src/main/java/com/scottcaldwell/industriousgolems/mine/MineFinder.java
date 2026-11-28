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
        return isMineable(level, pos, false);
    }

    /**
     * As above; with {@code allowFall} a block with sand or gravel over it is dug too. Inside the mine that is how a falling column is cleared: the
     * column drops one block into the hole and is dug again, until it is gone.
     */
    public static boolean isMineable(ServerLevel level, BlockPos pos, boolean allowFall) {
        if (!isRock(level.getBlockState(pos))) return false;
        if (!allowFall && level.getBlockState(pos.above()).getBlock() instanceof FallingBlock) return false;
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

    /** Rock that may be cut: no liquid on any side of it. (Whether it can be struck is {@link #isMineable}: it need not be in the open itself.) */
    public static boolean isSafe(ServerLevel level, BlockPos pos) {
        if (!isRock(level.getBlockState(pos))) return false;
        for (Direction dir : Direction.values()) {
            if (!level.getBlockState(pos.relative(dir)).getFluidState().isEmpty()) return false;
        }
        return true;
    }

    /** How far from its centre the golem's fist lands when it brings an arm down, in blocks. */
    public static final double FIST = 1.9;

    /**
     * Where to stand to strike a block: the open floor right beside it (a side or a corner), so the fist lands on it. The golem stands at the
     * block's height or up to three below it (as high as a swing reaches), never over it, the lower the better, since a swing takes the column from its feet up. Null when
     * there is no such spot.
     */
    public static BlockPos fistSpot(ServerLevel level, BlockPos block, BlockPos from) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(block.offset(-1, -3, -1), block.offset(1, 0, 1))) {
            if (pos.getX() == block.getX() && pos.getZ() == block.getZ()) continue;
            if (!canStandAt(level, pos)) continue;
            // cardinal neighbours first: the fist lands squarely on the face
            double distance = pos.distSqr(from) + (pos.getX() != block.getX() && pos.getZ() != block.getZ() ? 4 : 0) - 3 * (block.getY() - pos.getY());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = pos.immutable();
            }
        }
        return best;
    }

    /** Close enough to the block, on the flat, for the fist to land on it. */
    public static boolean fistReaches(net.minecraft.world.entity.Entity golem, BlockPos block) {
        double dx = golem.getX() - (block.getX() + 0.5), dz = golem.getZ() - (block.getZ() + 0.5);
        return Math.sqrt(dx * dx + dz * dz) <= FIST;
    }

    private static boolean canStandAt(ServerLevel level, BlockPos pos) {
        return isOpen(level, pos) && isOpen(level, pos.above())
                && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
                && level.getBlockState(pos.below()).getFluidState().isEmpty();
    }

    private static boolean isOpen(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getCollisionShape(level, pos).isEmpty() && state.getFluidState().isEmpty() && !state.hasBlockEntity(); // never in the chest's own cell
    }
}
