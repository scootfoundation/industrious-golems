package com.scottcaldwell.industriousgolems.block;

import com.scottcaldwell.industriousgolems.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A golem's chest: a normal 27-slot chest (golems and players both use it) that is also the golem's job site and the
 * place it deposits what it produces. It never joins into a double chest. Like a vanilla chest it is drawn by a block
 * entity renderer from the standard chest model, with a texture of its own, and the lid swings open as vanilla's does.
 */
public class GolemChestBlock extends ChestBlock {

    public GolemChestBlock(BlockBehaviour.Properties properties) {
        super(() -> ModBlocks.GOLEM_CHEST.get(), SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
    }

    @Override
    public boolean chestCanConnectTo(BlockState state) {
        return false;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GolemChestEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }
}
