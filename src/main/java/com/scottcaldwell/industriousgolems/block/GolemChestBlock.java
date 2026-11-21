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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * A golem's chest: a normal 27-slot chest (golems and players both use it) that is also the golem's job site and the
 * place it deposits what it produces. It never joins into a double chest, and is drawn from a plain block model, so
 * it is drawn from a block model, and its lid is lifted by switching to the {@link #OPEN} model while anything has it open.
 */
public class GolemChestBlock extends ChestBlock {

    public static final MapCodec<GolemChestBlock> CODEC = simpleCodec(GolemChestBlock::new);

    /** Whether the lid is up: set while a golem or a player has the chest open. */
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;

    public GolemChestBlock(BlockBehaviour.Properties properties) {
        super(() -> ModBlocks.GOLEM_CHEST.get(), SoundEvents.CHEST_OPEN, SoundEvents.CHEST_CLOSE, properties);
        registerDefaultState(defaultBlockState().setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(OPEN);
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
