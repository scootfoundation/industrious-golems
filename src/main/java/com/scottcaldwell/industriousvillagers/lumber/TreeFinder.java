package com.scottcaldwell.industriousvillagers.lumber;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds trees worth felling: a trunk rooted in the ground, with natural leaves, small enough to chop from the ground.
 * Log buildings (no natural leaves, or too many logs) are never touched.
 */
public final class TreeFinder {

    /** How far from the job site the lumberjack looks. */
    public static final int SEARCH_RADIUS = 16;
    /** More logs than this is a building or a giant tree, not something to fell. */
    static final int MAX_LOGS = 64;
    /** Natural leaves needed beside the logs to count as a tree. */
    static final int MIN_LEAVES = 4;
    /** Highest log above the lowest one that can be reached from the ground. */
    static final int MAX_HEIGHT = 5;
    private static final int CANDIDATES = 12;

    private TreeFinder() { }

    /** A tree's logs, chopped in this order (bottom first), and where to replant. */
    public record Tree(List<BlockPos> logs, BlockPos base, Block logType) { }

    public static Tree find(ServerLevel level, BlockPos center, BlockPos from) {
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-SEARCH_RADIUS, -3, -SEARCH_RADIUS),
                center.offset(SEARCH_RADIUS, 6, SEARCH_RADIUS))) {
            if (isLog(level.getBlockState(pos)) && isGround(level.getBlockState(pos.below()))) candidates.add(pos.immutable());
        }
        candidates.sort(Comparator.comparingDouble(p -> p.distSqr(from)));

        Set<BlockPos> rejected = new HashSet<>();
        int tried = 0;
        for (BlockPos start : candidates) {
            if (rejected.contains(start)) continue;
            if (++tried > CANDIDATES) break;
            Tree tree = cluster(level, start);
            if (tree != null) return tree;
            rejected.add(start);
        }
        return null;
    }

    private static boolean isLog(BlockState state) {
        return state.is(BlockTags.LOGS);
    }

    private static boolean isGround(BlockState state) {
        return state.is(BlockTags.DIRT);
    }

    /** The connected logs around {@code start}, or null when they do not make a fellable tree. */
    static Tree cluster(ServerLevel level, BlockPos start) {
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos next = current.offset(dx, dy, dz);
                        if (seen.contains(next) || !isLog(level.getBlockState(next))) continue;
                        seen.add(next);
                        if (seen.size() > MAX_LOGS) return null;
                        queue.add(next);
                    }
                }
            }
        }

        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (BlockPos log : seen) {
            minY = Math.min(minY, log.getY());
            maxY = Math.max(maxY, log.getY());
        }
        if (maxY - minY > MAX_HEIGHT) return null; // too tall to reach from the ground

        Set<BlockPos> leaves = new HashSet<>();
        boolean rooted = false;
        for (BlockPos log : seen) {
            if (log.getY() == minY && isGround(level.getBlockState(log.below()))) rooted = true;
            for (Direction dir : Direction.values()) {
                BlockPos side = log.relative(dir);
                BlockState state = level.getBlockState(side);
                if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)) {
                    leaves.add(side);
                }
            }
        }
        if (!rooted || leaves.size() < MIN_LEAVES) return null;

        List<BlockPos> logs = new ArrayList<>(seen);
        BlockPos anchor = start;
        logs.sort(Comparator.<BlockPos>comparingInt(BlockPos::getY).thenComparingDouble(p -> p.distSqr(anchor)));
        BlockPos base = logs.get(0);
        return new Tree(logs, base, level.getBlockState(base).getBlock());
    }
}