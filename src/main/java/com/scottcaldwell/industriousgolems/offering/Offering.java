package com.scottcaldwell.industriousgolems.offering;

import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.function.Predicate;

/**
 * One thing a golem kind is willing to be given to run on. Each kind has a fixed list (see {@link
 * com.scottcaldwell.industriousgolems.ModProfessions}); the golem craves one entry of it at a time.
 *
 * @param match accepts the stacks that satisfy the craving
 * @param icon  shown in the menu and used for the name unless {@code name} is set
 * @param name  overrides the icon's name, for an entry that stands for a group of items
 */
public record Offering(Predicate<ItemStack> match, ItemStack icon, @Nullable Component name) {

    public static Offering of(Item item) {
        return new Offering(stack -> stack.is(item), new ItemStack(item), null);
    }

    /** Any item of the tag, shown as one entry under the given translation key. */
    public static Offering anyOf(TagKey<Item> tag, Item icon, String translationKey) {
        return new Offering(stack -> stack.is(tag), new ItemStack(icon), Component.translatable(translationKey));
    }

    public Component displayName() {
        return name != null ? name : icon.getHoverName();
    }
}
