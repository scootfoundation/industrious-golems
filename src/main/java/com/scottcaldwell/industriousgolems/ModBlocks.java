package com.scottcaldwell.industriousgolems;

import com.google.common.collect.ImmutableSet;
import com.scottcaldwell.industriousgolems.block.GolemChestBlock;
import com.scottcaldwell.industriousgolems.block.GolemChestEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The golems' chests. Building one of the patterns in {@link com.scottcaldwell.industriousgolems.golem.ChestSpawn}
 * turns the base block into the golem's chest. The chest is the golem's job site (it has a point of interest of its
 * own) and where it deposits and restocks.
 */
public final class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(IndustriousGolems.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IndustriousGolems.MODID);
    public static final DeferredRegister<PoiType> POIS =
            DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, IndustriousGolems.MODID);

    public static final DeferredBlock<GolemChestBlock> HAY_CHEST = chest("hay_chest", 0.6F, SoundType.GRASS);
    public static final DeferredBlock<GolemChestBlock> MUSHROOM_CHEST = chest("mushroom_chest", 1.0F, SoundType.WOOD);
    public static final DeferredBlock<GolemChestBlock> TERRACOTTA_CHEST = chest("terracotta_chest", 1.25F, SoundType.STONE);

    public static final DeferredItem<BlockItem> HAY_CHEST_ITEM = ModItems.ITEMS.registerSimpleBlockItem(HAY_CHEST);
    public static final DeferredItem<BlockItem> MUSHROOM_CHEST_ITEM = ModItems.ITEMS.registerSimpleBlockItem(MUSHROOM_CHEST);
    public static final DeferredItem<BlockItem> TERRACOTTA_CHEST_ITEM = ModItems.ITEMS.registerSimpleBlockItem(TERRACOTTA_CHEST);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GolemChestEntity>> GOLEM_CHEST =
            BLOCK_ENTITIES.register("golem_chest", () -> new BlockEntityType<>(GolemChestEntity::new,
                    HAY_CHEST.get(), MUSHROOM_CHEST.get(), TERRACOTTA_CHEST.get()));

    public static final DeferredHolder<PoiType, PoiType> HAY_CHEST_POI = poi("hay_chest", HAY_CHEST);
    public static final DeferredHolder<PoiType, PoiType> MUSHROOM_CHEST_POI = poi("mushroom_chest", MUSHROOM_CHEST);
    public static final DeferredHolder<PoiType, PoiType> TERRACOTTA_CHEST_POI = poi("terracotta_chest", TERRACOTTA_CHEST);

    private ModBlocks() { }

    private static DeferredBlock<GolemChestBlock> chest(String id, float strength, SoundType sound) {
        return BLOCKS.registerBlock(id, GolemChestBlock::new,
                props -> props.strength(strength).sound(sound));
    }

    private static DeferredHolder<PoiType, PoiType> poi(String id, DeferredBlock<GolemChestBlock> chest) {
        return POIS.register(id, () -> new PoiType(
                ImmutableSet.copyOf(chest.get().getStateDefinition().getPossibleStates()), 1, 1));
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        POIS.register(modEventBus);
        modEventBus.addListener(BuildCreativeModeTabContentsEvent.class, event -> {
            if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
                event.accept(HAY_CHEST_ITEM);
                event.accept(MUSHROOM_CHEST_ITEM);
                event.accept(TERRACOTTA_CHEST_ITEM);
            }
        });
    }
}
