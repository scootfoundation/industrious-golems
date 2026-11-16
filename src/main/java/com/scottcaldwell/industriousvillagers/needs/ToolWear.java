package com.scottcaldwell.industriousvillagers.needs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** Tool wear for any profession: each use damages the villager's tool by one, removing it (with a break sound) when spent. */
public final class ToolWear {
    private ToolWear() {}

    /** Wears the profession's first tool. */
    public static void use(ServerLevel level, Villager villager) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        if (needs != null && !needs.toolNeeds().isEmpty()) use(level, villager, needs.toolNeeds().get(0));
    }

    /** Wears the tool matching the need with this id. */
    public static void use(ServerLevel level, Villager villager, String toolId) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        Need tool = needs == null ? null : needs.need(toolId);
        if (tool != null) use(level, villager, tool);
    }

    private static void use(ServerLevel level, Villager villager, Need tool) {
        SimpleContainer inv = villager.getInventory();
        int slot = tool.slot(inv);
        if (slot < 0) return;
        ItemStack stack = inv.getItem(slot);
        if (!stack.isDamageableItem()) return;
        stack.setDamageValue(stack.getDamageValue() + 1);
        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            inv.setItem(slot, ItemStack.EMPTY);
            level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 0.8F);
        }
        inv.setChanged();
    }
}