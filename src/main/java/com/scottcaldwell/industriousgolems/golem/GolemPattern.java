package com.scottcaldwell.industriousgolems.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * The shape of blocks under a carved pumpkin that builds a golem. {@link #match} returns the blocks the shape is made of; the lowest one
 * (the {@link Match#chest() chest}) becomes the golem's chest and the rest are used up.
 */
@FunctionalInterface
public interface GolemPattern {

    /** The blocks of a pattern found under a pumpkin: where the chest goes, and every block the pattern is made of (the chest included). */
    record Match(BlockPos chest, List<BlockPos> blocks) { }

    /** The pattern under the pumpkin at {@code pumpkin}, or null if the blocks there do not make it. */
    Match match(Level level, BlockPos pumpkin);

    /** A column straight down from the pumpkin: the first predicate is the block under it, the last is the bottom one, which becomes the chest. */
    @SafeVarargs
    static GolemPattern column(Predicate<BlockState>... fromTop) {
        return (level, pumpkin) -> {
            List<BlockPos> blocks = new ArrayList<>();
            BlockPos at = pumpkin;
            for (Predicate<BlockState> block : fromTop) {
                at = at.below();
                if (!block.test(level.getBlockState(at))) return null;
                blocks.add(at);
            }
            return new Match(at, blocks);
        };
    }

    /**
     * Like the iron golem: the pumpkin on the middle of a row of three, with one more block hanging under that middle. The row may run
     * either way; the block at the foot of the T becomes the chest.
     */
    static GolemPattern tee(Predicate<BlockState> block) {
        return (level, pumpkin) -> {
            BlockPos middle = pumpkin.below();
            BlockPos foot = middle.below();
            if (!block.test(level.getBlockState(middle)) || !block.test(level.getBlockState(foot))) return null;
            for (Direction side : new Direction[] {Direction.EAST, Direction.SOUTH}) {
                BlockPos a = middle.relative(side), b = middle.relative(side.getOpposite());
                if (block.test(level.getBlockState(a)) && block.test(level.getBlockState(b))) {
                    return new Match(foot, List.of(middle, a, b, foot));
                }
            }
            return null;
        };
    }
}
