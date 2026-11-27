package com.scottcaldwell.industriousgolems.golem;

import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

/** Putting stacks into slots: the one place that knows how stacks merge, for chests and the pack alike. */
public final class Slots {
    private Slots() { }

    /**
     * Inserts into slots {@code from} up to {@code to}, topping up matching stacks before using empty slots; returns whatever did not
     * fit. The stack passed in is used up as it goes.
     */
    public static ItemStack insert(Container c, int from, int to, ItemStack stack) {
        for (int pass = 0; pass < 2 && !stack.isEmpty(); pass++) {
            for (int i = from; i < to && !stack.isEmpty(); i++) {
                if (!c.canPlaceItem(i, stack)) continue;
                ItemStack there = c.getItem(i);
                if (pass == 0) {
                    if (there.isEmpty() || !ItemStack.isSameItemSameComponents(there, stack)) continue;
                    int n = Math.min(Math.min(there.getMaxStackSize(), c.getMaxStackSize()) - there.getCount(), stack.getCount());
                    if (n > 0) {
                        there.grow(n);
                        stack.shrink(n);
                    }
                } else if (there.isEmpty()) {
                    int n = Math.min(Math.min(stack.getMaxStackSize(), c.getMaxStackSize()), stack.getCount());
                    c.setItem(i, stack.copyWithCount(n));
                    stack.shrink(n);
                }
            }
        }
        return stack;
    }

    /** Inserts anywhere in the container. */
    public static ItemStack insert(Container c, ItemStack stack) {
        return insert(c, 0, c.getContainerSize(), stack);
    }

    /** Whether the whole stack would fit; changes nothing. */
    public static boolean fits(Container c, ItemStack stack) {
        int left = stack.getCount();
        for (int i = 0; i < c.getContainerSize() && left > 0; i++) {
            if (!c.canPlaceItem(i, stack)) continue;
            ItemStack there = c.getItem(i);
            if (there.isEmpty()) left -= Math.min(stack.getMaxStackSize(), c.getMaxStackSize());
            else if (ItemStack.isSameItemSameComponents(there, stack)) {
                left -= Math.min(there.getMaxStackSize(), c.getMaxStackSize()) - there.getCount();
            }
        }
        return left <= 0;
    }
}
