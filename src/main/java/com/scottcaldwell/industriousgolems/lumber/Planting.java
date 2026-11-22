package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.golem.Stand;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where the lumberjack plants a tree when it has a sapling and the last stump is not the place: open ground (bare, or covered by grass
 * or flowers) near its chest but outside a clear zone around it, with room overhead to grow, and well away from every other tree and
 * sapling, so the grove it keeps stays spread out. It stops planting once there are enough trees about.
 */
public final class Planting {
    private Planting() { }

    /** How far from the chest, sideways, it plants. */
    public static final int RADIUS = TINY() ? 10 : 14;
    /** No other tree or sapling within this many blocks of a new one, either way sideways: a grown oak spreads about two blocks each way, so these never touch. */
    private static final int SPACING = TINY() ? 3 : 5;
    /** Nothing is planted within this many blocks of the chest (a square around it), so a growing tree never blocks the way to it. */
    public static final int CLEARING = TINY() ? 2 : 6;

    /** The game tests run in an 8x8 arena (build.gradle sets this for them alone), far too small for the real grove. */
    private static boolean TINY() {
        return Boolean.getBoolean("industriousgolems.tinyGrove");
    }

    /** Whether the spot is inside the clearing kept around the chest. */
    public static boolean inClearing(BlockPos pos, BlockPos chest) {
        return Math.max(Math.abs(pos.getX() - chest.getX()), Math.abs(pos.getZ() - chest.getZ())) < CLEARING;
    }
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
            if (state.is(BlockTags.LOGS) || state.is(BlockItemTags.SAPLINGS.block())) {
                planted.add(pos.immutable());
                if (state.is(BlockItemTags.SAPLINGS.block()) || level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD)) trees++;
                continue;
            }
            if (Math.abs(pos.getX() - chest.getX()) > RADIUS || Math.abs(pos.getZ() - chest.getZ()) > RADIUS || pos.getY() > chest.getY() + 3) continue;
            if (LumberWork.isFree(state) && level.getBlockState(pos.below()).is(BlockTags.SUBSTRATE_OVERWORLD) && !ignored.test(pos)
                    && Blocks.OAK_SAPLING.defaultBlockState().canSurvive(level, pos)
                    && !inClearing(pos, chest)) candidates.add(pos.immutable());
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
            if (!LumberWork.isFree(level.getBlockState(pos.above(i)))) return false;
        }
        return true;
    }
}
