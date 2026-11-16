package com.scottcaldwell.industriousvillagers.needs;

import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Something a villager keeps topped up from containers: a tool, a supply or food. Declared per profession (see
 * {@link ProfessionNeeds}) and used by container visits, the work gate, the inventory screen and the status plate.
 *
 * @param target         how many the villager tops up to when restocking
 * @param refillBelow    restocking starts when it holds fewer than this
 * @param depositSurplus items beyond {@code target} are deposited
 * @param blocks         when this holds and the villager has none, the shortfall is shown to the player
 * @param gatesWork      such a shortfall also stops the villager working
 * @param tool           gets a dedicated inventory slot and a spot on the status plate
 */
public record Need(String id, Predicate<ItemStack> match, ItemStack icon, int target, int refillBelow,
                   boolean depositSurplus, Predicate<Villager> blocks, boolean gatesWork, boolean tool) {

    public static final String FOOD = "food";

    /** A single tool the profession works with; {@code gatesWork} when it cannot work at all without it. */
    public static Need tool(String id, Predicate<ItemStack> match, Item icon, boolean gatesWork) {
        return new Need(id, match, new ItemStack(icon), 1, 1, false, v -> true, gatesWork, true);
    }

    /** A tool that is used up and restocked in quantity, such as buckets. Never gates work. */
    public static Need consumableTool(String id, Predicate<ItemStack> match, Item icon, int target, int refillBelow) {
        return new Need(id, match, new ItemStack(icon), target, refillBelow, false, v -> true, false, true);
    }

    /** Materials the work uses up; whatever is carried beyond {@code target} is deposited. */
    public static Need supply(String id, Predicate<ItemStack> match, Item icon, int target, int refillBelow) {
        return new Need(id, match, new ItemStack(icon), target, refillBelow, true, v -> true, false, false);
    }

    /** Food for the villager itself; the shortfall is reported when {@code reportMissing} holds. */
    public static Need food(Predicate<ItemStack> match, Item icon, Predicate<Villager> reportMissing) {
        return new Need(FOOD, match, new ItemStack(icon), 6, 3, false, reportMissing, false, false);
    }

    public boolean missing(Villager villager) {
        return blocks.test(villager) && have(villager.getInventory()) == 0;
    }

    public int have(Container inventory) {
        int n = 0;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && match.test(s)) n += s.getCount();
        }
        return n;
    }

    public boolean unmet(Container inventory) {
        return have(inventory) < refillBelow;
    }

    /** Index of the first slot holding something that satisfies this need, or -1. */
    public int slot(Container inventory) {
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack s = inventory.getItem(i);
            if (!s.isEmpty() && match.test(s)) return i;
        }
        return -1;
    }
}