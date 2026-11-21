package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.ModEntities;
import com.scottcaldwell.industriousgolems.ModProfessions;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Builds a golem the way vanilla builds a copper golem: a body block with a carved pumpkin or jack o'lantern placed on
 * top. The pumpkin goes and the body block becomes the golem's chest, with the golem standing on it already bound to
 * it as its job site. No chest is needed to start; the pattern makes one.
 *
 * <p>Each golem has its own body block (see {@link ModProfessions.Profession#bodyBlock()}): hay bale for the hay golem,
 * red mushroom block for the mushroom golem, terracotta for the terracotta golem.
 */
public final class ChestSpawn {

    /** A chest is 14/16 of a block tall; the golem stands on top of it. */
    public static final double CHEST_HEIGHT = 14.0 / 16.0;

    private ChestSpawn() { }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState placed = event.getPlacedBlock();
        if (!placed.is(Blocks.CARVED_PUMPKIN) && !placed.is(Blocks.JACK_O_LANTERN)) return;
        build(level, event.getPos(), placed);
    }

    /** Builds the golem if the pumpkin at {@code pumpkin} sits on a body block; returns it, or null. */
    public static WorkGolem build(ServerLevel level, BlockPos pumpkin, BlockState pumpkinState) {
        BlockPos base = pumpkin.below();
        BlockState baseState = level.getBlockState(base);
        for (ModProfessions.Profession profession : ModProfessions.all()) {
            if (profession.bodyBlock().test(baseState)) return build(level, profession, pumpkin, pumpkinState);
        }
        return null;
    }

    private static WorkGolem build(ServerLevel level, ModProfessions.Profession profession, BlockPos pumpkin,
                                   BlockState pumpkinState) {
        // the golem stands taller than the pumpkin's block: it needs the space above as well
        if (!level.getBlockState(pumpkin.above()).getCollisionShape(level, pumpkin.above()).isEmpty()) return null;
        WorkGolem golem = ModEntities.typeOf(profession).create(level, EntitySpawnReason.TRIGGERED);
        if (golem == null) return null;

        BlockPos base = pumpkin.below();
        Direction facing = pumpkinState.hasProperty(CarvedPumpkinBlock.FACING)
                ? pumpkinState.getValue(CarvedPumpkinBlock.FACING) : Direction.NORTH;
        BlockState oldBase = level.getBlockState(base);

        level.setBlock(pumpkin, Blocks.AIR.defaultBlockState(), 2);
        level.levelEvent(2001, pumpkin, Block.getId(pumpkinState));
        level.levelEvent(2001, base, Block.getId(oldBase));
        level.setBlock(base, profession.chest().get().defaultBlockState().setValue(ChestBlock.FACING, facing), 3);

        golem.snapTo(base.getX() + 0.5, base.getY() + CHEST_HEIGHT, base.getZ() + 0.5, facing.toYRot(), 0.0F);
        golem.setYBodyRot(facing.toYRot());
        golem.setYHeadRot(facing.toYRot());
        golem.getBrain().setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), base));
        // claim the chest at once so no other golem takes it
        level.getPoiManager().take(profession.jobSite(), (holder, pos) -> pos.equals(base), base, 1);
        level.addFreshEntity(golem);

        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, golem.getBoundingBox().inflate(5.0))) {
            CriteriaTriggers.SUMMONED_ENTITY.trigger(player, golem);
        }
        level.updateNeighborsAt(pumpkin, Blocks.AIR);
        level.updateNeighborsAt(base, level.getBlockState(base).getBlock());
        return golem;
    }
}
