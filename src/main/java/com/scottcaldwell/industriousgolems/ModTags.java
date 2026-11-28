package com.scottcaldwell.industriousgolems;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

/** Item tags that profession behaviour is data-driven by (see data/industriousgolems/tags/item). */
public final class ModTags {
    private ModTags() { }

    /** What the herder feeds animals to make them breed. */
    public static final TagKey<Item> ANIMAL_FEED = item("animal_feed");
    public static final TagKey<Item> HERDER_OUTPUT = item("herder_output");
    public static final TagKey<Item> LUMBERJACK_OUTPUT = item("lumberjack_output");
    public static final TagKey<Item> MINER_OUTPUT = item("miner_output");

    /** Plain rock the stone golem digs through, and the ores it prefers (see data/industriousgolems/tags/block). */
    public static final TagKey<Block> MINER_STONE = block("miner_stone");
    public static final TagKey<Block> MINER_ORES = block("miner_ores");

    /** The blocks a golem will fetch supplies from and put things away in: chests, barrels and the like, never a furnace or hopper. */
    public static final TagKey<Block> GOLEM_STORAGE = block("golem_storage");

    private static TagKey<Item> item(String name) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(IndustriousGolems.MODID, name));
    }

    private static TagKey<Block> block(String name) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(IndustriousGolems.MODID, name));
    }
}
