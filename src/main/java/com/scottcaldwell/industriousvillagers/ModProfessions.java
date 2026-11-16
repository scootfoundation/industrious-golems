package com.scottcaldwell.industriousvillagers;

import com.google.common.collect.ImmutableSet;
import com.scottcaldwell.industriousvillagers.herd.HerdWork;
import com.scottcaldwell.industriousvillagers.lumber.FellTree;
import com.scottcaldwell.industriousvillagers.needs.Hunger;
import com.scottcaldwell.industriousvillagers.needs.Need;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The mod's professions. Each is declared once, in {@link #define}: its registry entry (job site, items it picks
 * up, work sound) together with its {@link ProfessionNeeds}, from which all behaviour is derived.
 */
public class ModProfessions {

    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, IndustriousVillagers.MODID);

    private static final Map<ResourceKey<VillagerProfession>, ProfessionNeeds> NEEDS = new LinkedHashMap<>();

    // PoiTypes.FARMER is the Composter POI (named after the vanilla profession it served).
    public static final DeferredHolder<VillagerProfession, VillagerProfession> FARMER = define("farmer",
            holder -> holder.is(PoiTypes.FARMER),
            Set.of(Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE),
            SoundEvents.VILLAGER_WORK_FARMER,
            new ProfessionNeeds(List.of(
                    Need.tool("hoe", s -> s.is(ItemTags.HOES), Items.IRON_HOE, true),
                    Need.supply("seeds", s -> s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS), Items.WHEAT_SEEDS, 16, 8),
                    Need.food(s -> s.is(ModTags.VILLAGER_FOOD), Items.BREAD, Hunger::isHungry)
            ), s -> s.is(Items.WHEAT) || s.is(Items.BEETROOT) || s.is(Items.MELON_SLICE) || s.is(Items.PUMPKIN)
                    || s.is(Items.MELON) || s.is(Items.SWEET_BERRIES), null));

    /** The shepherd's loom is the herder's job site. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> HERDER = define("herder",
            holder -> holder.is(PoiTypes.SHEPHERD),
            Set.of(Items.SHEARS, Items.BUCKET, Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS),
            SoundEvents.VILLAGER_WORK_SHEPHERD,
            new ProfessionNeeds(List.of(
                    Need.tool("shears", s -> s.is(Items.SHEARS), Items.SHEARS, false),
                    Need.tool("sword", s -> s.is(ItemTags.SWORDS), Items.IRON_SWORD, false),
                    Need.consumableTool("bucket", s -> s.is(Items.BUCKET), Items.BUCKET, 3, 2),
                    Need.supply("feed", s -> s.is(ModTags.ANIMAL_FEED), Items.WHEAT, 16, 8),
                    Need.food(s -> s.is(ModTags.VILLAGER_FOOD), Items.BREAD, Hunger::isHungry)
            ), s -> s.is(ModTags.HERDER_OUTPUT), new ProfessionNeeds.Work(HerdWork::plan, HerdWork::new)));

    /** The fletching table is the lumberjack's job site. */
    public static final DeferredHolder<VillagerProfession, VillagerProfession> LUMBERJACK = define("lumberjack",
            holder -> holder.is(PoiTypes.FLETCHER),
            Set.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE,
                    Items.NETHERITE_AXE, Items.OAK_SAPLING),
            SoundEvents.VILLAGER_WORK_FLETCHER,
            new ProfessionNeeds(List.of(
                    Need.tool("axe", s -> s.is(ItemTags.AXES), Items.IRON_AXE, true),
                    Need.supply("saplings", s -> s.is(ItemTags.SAPLINGS), Items.OAK_SAPLING, 8, 4),
                    Need.food(s -> s.is(ModTags.VILLAGER_FOOD), Items.BREAD, Hunger::isHungry)
            ), s -> s.is(ModTags.LUMBERJACK_OUTPUT), new ProfessionNeeds.Work(FellTree::plan, FellTree::new)));

    // Pre-built keys so mixins can reference them without touching the DeferredHolders.
    public static final ResourceKey<VillagerProfession> FARMER_KEY = FARMER.getKey();
    public static final ResourceKey<VillagerProfession> HERDER_KEY = HERDER.getKey();
    public static final ResourceKey<VillagerProfession> LUMBERJACK_KEY = LUMBERJACK.getKey();

    private static DeferredHolder<VillagerProfession, VillagerProfession> define(
            String name, Predicate<Holder<PoiType>> jobSite, Set<Item> picksUp, SoundEvent workSound, ProfessionNeeds needs) {
        DeferredHolder<VillagerProfession, VillagerProfession> holder = PROFESSIONS.register(name,
                () -> new VillagerProfession(
                        Component.translatable("entity.minecraft.villager." + IndustriousVillagers.MODID + "." + name),
                        jobSite, jobSite, ImmutableSet.copyOf(picksUp), ImmutableSet.of(), workSound));
        NEEDS.put(holder.getKey(), needs);
        return holder;
    }

    public static ProfessionNeeds needsFor(ResourceKey<VillagerProfession> key) {
        return NEEDS.get(key);
    }

    public static Set<ResourceKey<VillagerProfession>> withNeeds() {
        return NEEDS.keySet();
    }

    public static void register(IEventBus modEventBus) {
        PROFESSIONS.register(modEventBus);
    }
}