package com.scottcaldwell.industriousgolems.needs;

import net.minecraft.world.Container;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * Something a golem keeps topped up from containers: materials its work uses up, such as seeds. Declared per
 * profession (see {@link ProfessionNeeds}) and used by container visits and the status plate. Golems work bare-handed,
 * so there are no tools, and what keeps them running is an offering, not a need (see
 * {@link com.scottcaldwell.industriousgolems.offering.Offerings}).
 *
 * @param target         how many the golem tops up to when restocking
 * @param refillBelow    restocking starts when it holds fewer than this
 * @param depositSurplus items beyond {@code target} are deposited
 * @param blocks         when this holds and the golem has none, the shortfall is shown to the player
 */
public record Need(String id, Predicate<ItemStack> match, ItemStack icon, int target, int refillBelow,
                   boolean depositSurplus, Predicate<WorkGolem> blocks) {

    /** Materials the work uses up; whatever is carried beyond {@code target} is deposited. */
    public static Need supply(String id, Predicate<ItemStack> match, Item icon, int target, int refillBelow) {
        return new Need(id, match, new ItemStack(icon), target, refillBelow, true, v -> true);
    }

    public boolean missing(WorkGolem golem) {
        return blocks.test(golem) && have(golem.getInventory()) == 0;
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
