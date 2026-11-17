package com.scottcaldwell.industriousgolems;

import com.scottcaldwell.industriousgolems.farm.TendCrops;
import com.scottcaldwell.industriousgolems.herd.HerdWork;
import com.scottcaldwell.industriousgolems.lumber.FellTree;
import com.scottcaldwell.industriousgolems.needs.Hunger;
import com.scottcaldwell.industriousgolems.needs.Need;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The golems' professions. Each is declared once: the job site it claims, the items it picks up, its work sound and its
 * {@link ProfessionNeeds}, from which all behaviour is derived. Every profession is carried out by its own golem kind
 * (see {@link ModEntities}).
 */
public final class ModProfessions {

    /** @param id also the entity id and the translation key suffix */
    public record Profession(String id, Predicate<Holder<PoiType>> jobSite, Set<Item> picksUp, SoundEvent workSound,
                             ProfessionNeeds needs) { }

    private static final List<Profession> ALL = new ArrayList<>();

    /** The composter is the farmer's job site (PoiTypes.FARMER is the Composter POI). */
    public static final Profession FARMER = define("farmer",
            holder -> holder.is(PoiTypes.FARMER),
            Set.of(Items.WOODEN_HOE, Items.STONE_HOE, Items.IRON_HOE, Items.GOLDEN_HOE, Items.DIAMOND_HOE, Items.NETHERITE_HOE),
            SoundEvents.VILLAGER_WORK_FARMER,
            new ProfessionNeeds(List.of(
                    Need.tool("hoe", s -> s.is(ItemTags.HOES), Items.IRON_HOE, true),
                    Need.supply("seeds", s -> s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS), Items.WHEAT_SEEDS, 16, 8),
                    Need.food(s -> s.is(ModTags.GOLEM_FOOD), Items.BREAD, Hunger::isHungry)
            ), s -> s.is(Items.WHEAT) || s.is(Items.BEETROOT) || s.is(Items.MELON_SLICE) || s.is(Items.PUMPKIN)
                    || s.is(Items.MELON) || s.is(Items.SWEET_BERRIES) || s.is(Items.CARROT) || s.is(Items.POTATO),
                    new ProfessionNeeds.Work(TendCrops::plan, TendCrops::new)));

    /** The shepherd's loom is the herder's job site. */
    public static final Profession HERDER = define("herder",
            holder -> holder.is(PoiTypes.SHEPHERD),
            Set.of(Items.SHEARS, Items.BUCKET, Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS),
            SoundEvents.VILLAGER_WORK_SHEPHERD,
            new ProfessionNeeds(List.of(
                    Need.tool("shears", s -> s.is(Items.SHEARS), Items.SHEARS, false),
                    Need.tool("sword", s -> s.is(ItemTags.SWORDS), Items.IRON_SWORD, false),
                    Need.consumableTool("bucket", s -> s.is(Items.BUCKET), Items.BUCKET, 3, 2),
                    Need.supply("feed", s -> s.is(ModTags.ANIMAL_FEED), Items.WHEAT, 16, 8),
                    Need.food(s -> s.is(ModTags.GOLEM_FOOD), Items.BREAD, Hunger::isHungry)
            ), s -> s.is(ModTags.HERDER_OUTPUT), new ProfessionNeeds.Work(HerdWork::plan, HerdWork::new)));

    /** The fletching table is the lumberjack's job site. */
    public static final Profession LUMBERJACK = define("lumberjack",
            holder -> holder.is(PoiTypes.FLETCHER),
            Set.of(Items.WOODEN_AXE, Items.STONE_AXE, Items.IRON_AXE, Items.GOLDEN_AXE, Items.DIAMOND_AXE,
                    Items.NETHERITE_AXE, Items.OAK_SAPLING),
            SoundEvents.VILLAGER_WORK_FLETCHER,
            new ProfessionNeeds(List.of(
                    Need.tool("axe", s -> s.is(ItemTags.AXES), Items.IRON_AXE, true),
                    Need.supply("saplings", s -> s.is(ItemTags.SAPLINGS), Items.OAK_SAPLING, 8, 4),
                    Need.food(s -> s.is(ModTags.GOLEM_FOOD), Items.BREAD, Hunger::isHungry)
            ), s -> s.is(ModTags.LUMBERJACK_OUTPUT), new ProfessionNeeds.Work(FellTree::plan, FellTree::new)));

    private ModProfessions() { }

    private static Profession define(String id, Predicate<Holder<PoiType>> jobSite, Set<Item> picksUp,
                                     SoundEvent workSound, ProfessionNeeds needs) {
        Profession profession = new Profession(id, jobSite, picksUp, workSound, needs);
        ALL.add(profession);
        return profession;
    }

    public static List<Profession> all() {
        return ALL;
    }
}
