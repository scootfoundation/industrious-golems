package com.scottcaldwell.industriousvillagers.mixin;

import com.scottcaldwell.industriousvillagers.behavior.ChestOpener;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/**
 * Chests only play their lid animation and sound for a {@link ContainerUser}, and re-check every few ticks that the
 * opener really has it open. Making villagers container users lets them open chests like a player (or a copper golem).
 */
@Mixin(Villager.class)
public abstract class VillagerContainerUserMixin implements ContainerUser, ChestOpener {

    @Unique
    private BlockPos industriousvillagers$openedChestPos;

    @Override
    public void industriousvillagers$setOpenedChestPos(BlockPos pos) {
        this.industriousvillagers$openedChestPos = pos;
    }

    @Override
    public boolean hasContainerOpen(ContainerOpenersCounter counter, BlockPos pos) {
        BlockPos opened = this.industriousvillagers$openedChestPos;
        if (opened == null) return false;
        if (opened.equals(pos)) return true;
        BlockState state = ((Entity) (Object) this).level().getBlockState(opened);
        return state.getBlock() instanceof ChestBlock
                && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE
                && ChestBlock.getConnectedBlockPos(opened, state).equals(pos);
    }

    @Override
    public double getContainerInteractionRange() {
        return 3.0;
    }
}
