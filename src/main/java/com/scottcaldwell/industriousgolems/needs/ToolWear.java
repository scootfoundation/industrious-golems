package com.scottcaldwell.industriousgolems.needs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;

/** Tool wear for any profession: each use damages the golem's tool by one, removing it (with a break sound) when spent. */
public final class ToolWear {
    private ToolWear() {}

    /** Wears the profession's first tool. */
    public static void use(ServerLevel level, WorkGolem golem) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        if (needs != null && !needs.toolNeeds().isEmpty()) use(level, golem, needs.toolNeeds().get(0));
    }

    /** Wears the tool matching the need with this id. */
    public static void use(ServerLevel level, WorkGolem golem, String toolId) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        Need tool = needs == null ? null : needs.need(toolId);
        if (tool != null) use(level, golem, tool);
    }

    private static void use(ServerLevel level, WorkGolem golem, Need tool) {
        SimpleContainer inv = golem.getInventory();
        int slot = tool.slot(inv);
        if (slot < 0) return;
        ItemStack stack = inv.getItem(slot);
        if (!stack.isDamageableItem()) return;
        stack.setDamageValue(stack.getDamageValue() + 1);
        if (stack.getDamageValue() >= stack.getMaxDamage()) {
            inv.setItem(slot, ItemStack.EMPTY);
            level.playSound(null, golem.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 0.8F);
        }
        inv.setChanged();
    }
}