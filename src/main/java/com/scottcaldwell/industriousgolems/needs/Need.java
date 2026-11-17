package com.scottcaldwell.industriousgolems.needs;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * A supply a golem's work uses up, such as seeds. When the work could go on if the golem had one, it fetches a stack
 * from its chest and carries it ({@link #carry} items at most), see {@link com.scottcaldwell.industriousgolems.behavior.ContainerInteraction}.
 * Golems work bare-handed, so there are no tools, and what keeps them running is an offering, not a need (see
 * {@link com.scottcaldwell.industriousgolems.offering.Offerings}).
 *
 * @param carry the most the golem takes from a chest in one go (a golem carries a single stack)
 */
public record Need(String id, Predicate<ItemStack> match, ItemStack icon, int carry) {

    public static Need supply(String id, Predicate<ItemStack> match, Item icon, int carry) {
        return new Need(id, match, new ItemStack(icon), carry);
    }
}
