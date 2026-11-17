package com.scottcaldwell.industriousgolems.block;

import com.mojang.serialization.MapCodec;
import com.scottcaldwell.industriousgolems.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A golem's chest: a normal 27-slot chest (golems and players both use it) that is also the golem's job site and the
 * place it deposits what it produces. It never joins into a double chest, and is drawn from a plain block model, so
 * its lid does not animate.
 */
public class GolemChestBlock extends ChestBlock {

    public static final MapCodec<GolemChestBlock> CODEC = simpleCodec(GolemChestBlock::new);

    public GolemChestBlock(BlockBehaviour.Properties properties) {
        super(() -> ModBlocks.GOLEM_CHEST.get(), SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
    }

    @Override
    public MapCodec<? extends ChestBlock> codec() {
        return CODEC;
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
        return RenderShape.MODEL;
    }
}
