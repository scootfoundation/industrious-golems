package com.scottcaldwell.industriousgolems.inventory;

import com.scottcaldwell.industriousgolems.needs.Need;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import net.minecraft.world.SimpleContainer;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Keeps the golem's dedicated tool slots (inventory slots 0..n-1, one per tool in the profession's order) holding
 * their tools. Pickups and chest transfers fill slots in any order, so this tidies up: each tool moves into its slot
 * and anything else sitting there moves out.
 */
public final class InventoryLayout {
    private InventoryLayout() { }

    public static void organize(WorkGolem golem) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        if (needs == null || golem.getTradingPlayer() != null) return;
        List<Need> tools = needs.toolNeeds();
        if (tools.isEmpty()) return;
        SimpleContainer inv = golem.getInventory();
        int reserved = tools.size();
        boolean changed = false;

        for (int k = 0; k < reserved; k++) {
            var isTool = tools.get(k).match();
            ItemStack current = inv.getItem(k);
            if (!current.isEmpty() && isTool.test(current)) continue; // already in place

            int toolAt = -1;
            for (int i = reserved; i < inv.getContainerSize(); i++) {
                if (!inv.getItem(i).isEmpty() && isTool.test(inv.getItem(i))) { toolAt = i; break; }
            }
            if (toolAt >= 0) { // swap the tool into its slot
                inv.setItem(k, inv.getItem(toolAt));
                inv.setItem(toolAt, current);
                changed = true;
            } else if (!current.isEmpty()) { // no tool for it: clear the reserved slot for one
                for (int i = reserved; i < inv.getContainerSize(); i++) {
                    if (inv.getItem(i).isEmpty()) {
                        inv.setItem(i, current);
                        inv.setItem(k, ItemStack.EMPTY);
                        changed = true;
                        break;
                    }
                }
            }
        }
        if (changed) inv.setChanged();
    }
}