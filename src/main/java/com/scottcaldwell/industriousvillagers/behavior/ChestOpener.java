package com.scottcaldwell.industriousvillagers.behavior;

import net.minecraft.core.BlockPos;

/** Implemented on Villager by VillagerContainerUserMixin: which chest the villager is currently holding open. */
public interface ChestOpener {
    void industriousvillagers$setOpenedChestPos(BlockPos pos);
}
