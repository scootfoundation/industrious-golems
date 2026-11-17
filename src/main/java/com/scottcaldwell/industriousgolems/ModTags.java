package com.scottcaldwell.industriousgolems;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/** Item tags that profession behaviour is data-driven by (see data/industriousgolems/tags/item). */
public final class ModTags {
    private ModTags() { }

    /** Food golems eat and fetch from containers. */
    public static final TagKey<Item> GOLEM_FOOD = item("golem_food");
    /** What the herder feeds animals to make them breed. */
    public static final TagKey<Item> ANIMAL_FEED = item("animal_feed");
    public static final TagKey<Item> HERDER_OUTPUT = item("herder_output");
    public static final TagKey<Item> LUMBERJACK_OUTPUT = item("lumberjack_output");

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IndustriousGolems.MODID, name));
    }
}