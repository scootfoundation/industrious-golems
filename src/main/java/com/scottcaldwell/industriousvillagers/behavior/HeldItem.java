package com.scottcaldwell.industriousvillagers.behavior;

import com.scottcaldwell.industriousvillagers.needs.NeedsIndicator;
import net.minecraft.world.entity.EquipmentSlot;
import com.scottcaldwell.industriousvillagers.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;

/**
 * The tool a golem visibly holds while it works with it. The held stack is a display-only copy (never dropped, not
 * a real item), shown in the main hand until the task ends.
 */
public final class HeldItem {
    private HeldItem() { }

    public static void hold(WorkGolem golem, ItemStack source) {
        if (source.isEmpty()) return;
        ItemStack current = golem.getItemBySlot(EquipmentSlot.MAINHAND);
        if (NeedsIndicator.isIcon(current) && ItemStack.isSameItem(current, source)) return;
        golem.setItemSlot(EquipmentSlot.MAINHAND, NeedsIndicator.markAsDisplay(source.copyWithCount(1)));
    }

    public static void release(WorkGolem golem) {
        if (NeedsIndicator.isIcon(golem.getItemBySlot(EquipmentSlot.MAINHAND))) {
            golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        }
    }

    /** True when a display item is held but nothing is using it any more (a task was cut short). */
    public static boolean stale(WorkGolem golem) {
        return NeedsIndicator.isIcon(golem.getItemBySlot(EquipmentSlot.MAINHAND))
                && !Tasks.active(golem);
    }
}
