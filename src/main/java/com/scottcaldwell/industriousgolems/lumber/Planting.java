package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.golem.Stand;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where the lumberjack plants a tree when there is nothing to fell: open ground near its chest, with room overhead to grow, and well
 * away from every other tree and sapling, so the grove it keeps stays spread out. It stops planting once there are enough trees about.
 */
public final class Planting {
    private Planting() { }

    /** How far from the chest, sideways, it plants. */
    public static final int RADIUS = 10;
    /** No other tree or sapling within this many blocks of a new one, either way sideways. */
    private static final int SPACING = 3;
    /** Free air needed above the sapling for the tree to grow into. */
    private static final int HEADROOM = 5;
    /** Trees and saplings around the chest, at which it plants no more. */
    private static final int CAP = 9;

    /** A spot to plant, nearest to {@code from}, or null when the ground is full, or none is free. */
    public static BlockPos find(ServerLevel level, WorkGolem golem, BlockPos chest, Predicate<BlockPos> ignored) {
        BlockPos from = golem.blockPosition();
        List<BlockPos> planted = new ArrayList<>();
        List<BlockPos> candidates = new ArrayList<>();
        int trees = 0;
        for (BlockPos pos : BlockPos.betweenClosed(chest.offset(-RADIUS - SPACING, -3, -RADIUS - SPACING),
                chest.offset(RADIUS + SPACING, 8, RADIUS + SPACING))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(BlockTags.LOGS) || state.is(BlockTags.SAPLINGS)) {
                planted.add(pos.immutable());
                if (state.is(BlockTags.SAPLINGS) || level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD)) trees++;
                continue;
            }
            if (Math.abs(pos.getX() - chest.getX()) > RADIUS || Math.abs(pos.getZ() - chest.getZ()) > RADIUS || pos.getY() > chest.getY() + 3) continue;
            if (state.isAir() && level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD) && !ignored.test(pos)
                    && Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, pos)
                    && pos.distSqr(chest) >= 6.25) candidates.add(pos.immutable());
        }
        if (trees >= CAP) return null;
        candidates.sort(java.util.Comparator.comparingDouble(pos -> pos.distSqr(from)));
        int tried = 0;
        for (BlockPos pos : candidates) {
            if (!crowdFree(planted, pos) || !roomAbove(level, pos)) continue;
            if (Stand.reachable(level, golem, Vec3.atBottomCenterOf(pos))) return pos; // one it can walk to
            if (++tried >= 5) break;
        }
        return null;
    }

    private static boolean crowdFree(List<BlockPos> planted, BlockPos pos) {
        for (BlockPos other : planted) {
            if (Math.abs(other.getX() - pos.getX()) <= SPACING && Math.abs(other.getZ() - pos.getZ()) <= SPACING
                    && other.getY() >= pos.getY() - 1) return false;
        }
        return true;
    }

    private static boolean roomAbove(ServerLevel level, BlockPos pos) {
        for (int i = 1; i <= HEADROOM; i++) {
            if (!level.getBlockState(pos.above(i)).isAir()) return false;
        }
        return true;
    }
}
