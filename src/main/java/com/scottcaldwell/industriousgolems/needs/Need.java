package com.scottcaldwell.industriousgolems.needs;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;

/**
 * A supply a golem's work uses up, such as seeds. When the work could go on if the golem had one, it fetches a stack
 * from its chest and carries it ({@link #carry} items at most), see {@link com.scottcaldwell.industriousgolems.behavior.ContainerInteraction}.
 * Golems work bare-handed, so there are no tools, and what keeps them running is an offering, not a need (see
 * {@link com.scottcaldwell.industriousgolems.offering.Offerings}).
 *
 * @param carry the most the golem takes from a chest in one go (a golem carries a single stack)
 */
public record Need(String id, Predicate<ItemStack> match, Item item, int carry, List<Part> parts) {

    /** One kind of item in a kit and how many the golem keeps in its pack. */
    public record Part(Predicate<ItemStack> match, int target) { }

    public static Need supply(String id, Predicate<ItemStack> match, Item icon, int carry) {
        return new Need(id, match, icon, carry, List.of());
    }

    /**
     * A kit: several kinds of item fetched together into the golem's pack (not its hand) in one visit, each topped up to its target. It is
     * not picked up off the ground.
     */
    public static Need kit(String id, Item icon, List<Part> parts) {
        return new Need(id, s -> false, icon, 0, List.copyOf(parts));
    }

    public boolean isKit() {
        return !parts.isEmpty();
    }

    /** Built on demand: item stacks cannot exist before the registries are bound, and the professions are declared before that. */
    public ItemStack icon() {
        return new ItemStack(item);
    }
}
