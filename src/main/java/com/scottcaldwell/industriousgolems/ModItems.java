package com.scottcaldwell.industriousgolems;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The golems are obtained from spawn eggs for now. */
public final class ModItems {

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IndustriousGolems.MODID);

    public static final DeferredItem<SpawnEggItem> HAY_GOLEM_SPAWN_EGG = ITEMS.registerItem("hay_golem_spawn_egg",
            SpawnEggItem::new, props -> props.spawnEgg(ModEntities.FARMER_GOLEM.get()));
    public static final DeferredItem<SpawnEggItem> MUSHROOM_GOLEM_SPAWN_EGG = ITEMS.registerItem("mushroom_golem_spawn_egg",
            SpawnEggItem::new, props -> props.spawnEgg(ModEntities.LUMBERJACK_GOLEM.get()));
    public static final DeferredItem<SpawnEggItem> TERRACOTTA_GOLEM_SPAWN_EGG = ITEMS.registerItem("terracotta_golem_spawn_egg",
            SpawnEggItem::new, props -> props.spawnEgg(ModEntities.HERDER_GOLEM.get()));

    private ModItems() { }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(CreativeModeTabs.SPAWN_EGGS)) {
                event.accept(HAY_GOLEM_SPAWN_EGG);
                event.accept(MUSHROOM_GOLEM_SPAWN_EGG);
                event.accept(TERRACOTTA_GOLEM_SPAWN_EGG);
            }
        });
    }
}
