package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.ModTags;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Finds the next rock for the stone golem to mine: plain stone or ore around its chest that is already exposed to the
 * air (so it digs inward from the faces it can see rather than boring through the ground), never below the chest's
 * own level (so it cuts a tunnel or quarry face, not a pit it could fall into), and never where the cut would let a
 * liquid in or bring sand down.
 */
public final class MineFinder {

    /** How far from the chest, sideways, the golem digs. */
    public static final int RADIUS = 7;
    /** How many blocks above the chest's level it digs; the floor it walks on is never touched. */
    public static final int HEIGHT = 2;
    /** Furthest the golem can mine from where it stands, measured from its eyes. */
    public static final double REACH = 5.0;
    /** An ore counts as this many blocks (squared) closer than it is, so a vein is worked before plain rock. */
    private static final double ORE_BONUS = 36.0;
    private static final int CANDIDATES = 10;

    private MineFinder() { }

    /** A block to mine and the spot to stand on while doing it. */
    public record Target(BlockPos block, BlockPos stand) { }

    /** Whether the golem digs this kind of block at all. */
    public static boolean isRock(BlockState state) {
        return state.is(ModTags.MINER_STONE) || state.is(ModTags.MINER_ORES);
    }

    public static Target find(ServerLevel level, BlockPos chest, BlockPos from, Predicate<BlockPos> ignored) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(chest.offset(-RADIUS, 0, -RADIUS), chest.offset(RADIUS, HEIGHT, RADIUS))) {
            if (!ignored.test(pos) && isMineable(level, pos)) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(p -> score(level, p, from)));

        int tried = 0;
        for (BlockPos block : candidates) {
            if (++tried > CANDIDATES) break;
            BlockPos stand = standingSpot(level, block, from);
            if (stand != null) return new Target(block, stand);
        }
        return null;
    }

    private static double score(ServerLevel level, BlockPos pos, BlockPos from) {
        double distance = pos.distSqr(from);
        return level.getBlockState(pos).is(ModTags.MINER_ORES) ? distance - ORE_BONUS : distance;
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
    static BlockPos standingSpot(ServerLevel level, BlockPos block, BlockPos from) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(block.offset(-2, -3, -2), block.offset(2, 0, 2))) {
            if (!canStandAt(level, pos)) continue;
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
