package com.scottcaldwell.industriousvillagers.inventory;

import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps the villager's dedicated tool slot (inventory slot 0) holding its tool. Pickups and chest transfers fill
 * slots in any order, so this tidies up: the tool moves into slot 0 and anything else sitting there moves out.
 */
public final class InventoryLayout {
    private InventoryLayout() { }

    public static void organize(Villager villager) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        if (needs == null || needs.toolNeed() == null || villager.getTradingPlayer() != null) return;
        SimpleContainer inv = villager.getInventory();
        var isTool = needs.toolNeed().match();

        ItemStack first = inv.getItem(0);
        if (!first.isEmpty() && isTool.test(first)) return; // already in place

        int toolAt = -1;
        for (int i = 1; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty() && isTool.test(inv.getItem(i))) { toolAt = i; break; }
        }
        if (toolAt >= 0) { // swap the tool into slot 0
            inv.setItem(0, inv.getItem(toolAt));
            inv.setItem(toolAt, first);
        } else if (!first.isEmpty()) { // no tool: just clear the reserved slot for one
            for (int i = 1; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).isEmpty()) {
                    inv.setItem(i, first);
                    inv.setItem(0, ItemStack.EMPTY);
                    break;
                }
            }
        }
        inv.setChanged();
    }
}
