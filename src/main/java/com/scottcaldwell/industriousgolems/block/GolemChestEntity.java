package com.scottcaldwell.industriousgolems.block;

import com.scottcaldwell.industriousgolems.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The container of a {@link GolemChestBlock}: a chest of its own type, so it gets its own renderer and textures. */
public class GolemChestEntity extends ChestBlockEntity {

    public GolemChestEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.GOLEM_CHEST.get(), pos, state);
    }

    /**
     * Vanilla only re-counts who has the chest open while it is open, and a count that gets out of step (an opener that went
     * away without closing, a close that arrives twice) is then never put right: the lid shows the opposite of the truth from
     * then on, open with nobody there and shut while someone is in it. So after every change to the count a re-count is
     * scheduled, which fixes the count and sends the lid state to the clients again.
     */
    @Override
    protected void signalOpenCount(Level level, BlockPos pos, BlockState state, int previous, int current) {
        super.signalOpenCount(level, pos, state, previous, current);
        if (previous != current) level.scheduleTick(pos, state.getBlock(), 5);
    }
}
