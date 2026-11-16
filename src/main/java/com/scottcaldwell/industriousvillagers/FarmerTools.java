package com.scottcaldwell.industriousvillagers;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** Hoe handling for the farmer: a hoe is required to harvest, and each harvested crop wears it by 1. */
public final class FarmerTools {
    private FarmerTools() {}

    public static boolean hasHoe(Villager villager) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (inv.getItem(i).is(ItemTags.HOES)) return true;
        }
        return false;
    }

    /** Wears the farmer's hoe by one use, removing it (with the break sound) when it runs out. */
    public static void wearHoe(ServerLevel level, Villager villager) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.is(ItemTags.HOES)) continue;
            stack.setDamageValue(stack.getDamageValue() + 1);
            if (stack.getDamageValue() >= stack.getMaxDamage()) {
                inv.setItem(i, ItemStack.EMPTY);
                level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 0.8F);
            }
            inv.setChanged();
            return;
        }
    }
}
