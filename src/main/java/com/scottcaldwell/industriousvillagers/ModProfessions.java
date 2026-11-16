package com.scottcaldwell.industriousvillagers;

import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModProfessions {

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, IndustriousVillagers.MODID);

    // Pre-built ResourceKey so mixins can reference it without touching the DeferredHolder.
    public static final ResourceKey<VillagerProfession> FARMER_KEY = ResourceKey.create(
            Registries.VILLAGER_PROFESSION,
            Identifier.fromNamespaceAndPath(IndustriousVillagers.MODID, "farmer")
    );

    public static final ResourceKey<VillagerProfession> HERDER_KEY = ResourceKey.create(
            Registries.VILLAGER_PROFESSION,
            Identifier.fromNamespaceAndPath(IndustriousVillagers.MODID, "herder")
    );

    // PoiTypes.FARMER is the ResourceKey for the Composter POI (named after the vanilla profession it served).
    public static final DeferredHolder<VillagerProfession, VillagerProfession> FARMER =
            PROFESSIONS.register("farmer", () -> new VillagerProfession(
                    Component.translatable("entity.minecraft.villager." + IndustriousVillagers.MODID + ".farmer"),
                    holder -> holder.is(PoiTypes.FARMER),
                    holder -> holder.is(PoiTypes.FARMER),
                    ImmutableSet.of(
                            Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE,
                            Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE
                    ),
                    ImmutableSet.of(Blocks.FARMLAND),
                    SoundEvents.VILLAGER_WORK_FARMER
            ));

    // The shepherd's loom is the herder's job site.
    public static final DeferredHolder<VillagerProfession, VillagerProfession> HERDER =
            PROFESSIONS.register("herder", () -> new VillagerProfession(
                    Component.translatable("entity.minecraft.villager." + IndustriousVillagers.MODID + ".herder"),
                    holder -> holder.is(PoiTypes.SHEPHERD),
                    holder -> holder.is(PoiTypes.SHEPHERD),
                    ImmutableSet.of(Items.SHEARS, Items.BUCKET, Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS),
                    ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_SHEPHERD
            ));

    public static void register(IEventBus modEventBus) {
        PROFESSIONS.register(modEventBus);
    }
}
