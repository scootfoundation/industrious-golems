package com.scottcaldwell.industriousgolems.golem;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** What kind of thing a golem is carrying, as far as its animations care: how it is held depends on what it is. */
public final class ItemKind {
    private ItemKind() { }

    public static String of(ItemStack stack) {
        if (stack.isEmpty()) return null;
        if (stack.is(ItemTags.LOGS)) return "log";
        if (stack.is(ItemTags.SAPLINGS)) return "sapling";
        if (stack.is(Items.STICK)) return "stick";
        if (stack.is(Items.WHEAT)) return "wheat";
        if (stack.is(Items.BREAD)) return "bread";
        if (stack.is(Items.WHEAT_SEEDS) || stack.is(Items.BEETROOT_SEEDS) || stack.is(Items.MELON_SEEDS) || stack.is(Items.PUMPKIN_SEEDS)) return "seeds";
        if (stack.is(Items.CARROT)) return "carrot";
        if (stack.is(ItemTags.WOOL)) return "wool";
        if (stack.is(Items.BEEF) || stack.is(Items.MUTTON) || stack.is(Items.PORKCHOP)) return "beef";
        return "ore";
    }
}
