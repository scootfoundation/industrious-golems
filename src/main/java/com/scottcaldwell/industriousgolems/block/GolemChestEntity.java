package com.scottcaldwell.industriousgolems.block;

import com.scottcaldwell.industriousgolems.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The container of a {@link GolemChestBlock}. A type of its own so no chest renderer draws a vanilla chest over it. The lid
 * is part of the block model, so when the number of openers changes the block switches between its closed and open models.
 */
public class GolemChestEntity extends ChestBlockEntity {

    public GolemChestEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.GOLEM_CHEST.get(), pos, state);
    }

    @Override
    public boolean triggerEvent(int id, int count) {
        if (id == 1 && level != null && !level.isClientSide()) {
            BlockState state = getBlockState();
            if (state.hasProperty(GolemChestBlock.OPEN) && state.getValue(GolemChestBlock.OPEN) != count > 0) {
                level.setBlock(worldPosition, state.setValue(GolemChestBlock.OPEN, count > 0), 3);
            }
        }
        return super.triggerEvent(id, count);
    }
}
