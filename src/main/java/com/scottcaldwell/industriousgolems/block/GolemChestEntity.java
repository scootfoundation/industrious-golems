package com.scottcaldwell.industriousgolems.block;

import com.scottcaldwell.industriousgolems.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** The container of a {@link GolemChestBlock}: a chest of its own type, so it gets its own renderer and textures. */
public class GolemChestEntity extends ChestBlockEntity {

    public GolemChestEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.GOLEM_CHEST.get(), pos, state);
    }
}
