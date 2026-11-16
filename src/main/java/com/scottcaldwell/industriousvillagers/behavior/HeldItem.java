package com.scottcaldwell.industriousvillagers.behavior;

import com.scottcaldwell.industriousvillagers.needs.NeedsIndicator;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * The tool a villager visibly holds while it works with it. The held stack is a display-only copy (never dropped, not
 * a real item), shown in the main hand until the task ends.
 */
public final class HeldItem {
    private HeldItem() { }

    public static void hold(Villager villager, ItemStack source) {
        if (source.isEmpty()) return;
        ItemStack current = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        if (NeedsIndicator.isIcon(current) && ItemStack.isSameItem(current, source)) return;
        villager.setItemSlot(EquipmentSlot.MAINHAND, NeedsIndicator.markAsDisplay(source.copyWithCount(1)));
    }

    public static void release(Villager villager) {
        if (NeedsIndicator.isIcon(villager.getItemBySlot(EquipmentSlot.MAINHAND))) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    /** True when a display item is held but nothing is using it any more (a task was cut short). */
    public static boolean stale(Villager villager) {
        return NeedsIndicator.isIcon(villager.getItemBySlot(EquipmentSlot.MAINHAND))
                && !Tasks.active(villager) && !runningFarmWork(villager);
    }

    @SuppressWarnings("deprecation")
    private static boolean runningFarmWork(Villager villager) {
        return villager.getBrain().getRunningBehaviors().stream()
                .anyMatch(b -> b instanceof net.minecraft.world.entity.ai.behavior.HarvestFarmland);
    }
}