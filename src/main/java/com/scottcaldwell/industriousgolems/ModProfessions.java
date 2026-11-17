package com.scottcaldwell.industriousgolems;

import com.scottcaldwell.industriousgolems.farm.TendCrops;
import com.scottcaldwell.industriousgolems.herd.HerdWork;
import com.scottcaldwell.industriousgolems.lumber.FellTree;
import com.scottcaldwell.industriousgolems.needs.Need;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import com.scottcaldwell.industriousgolems.offering.Offering;
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
 * The golems' professions. Each is declared once: the job site it claims, the items it picks up, its work sound, the
 * supplies its work uses ({@link ProfessionNeeds}) and the eight offerings it can be given to run on. Every profession
 * is carried out by its own golem kind (see {@link ModEntities}).
 *
 * <p>The offerings are deliberately strange, uncommon finds from all over the world and unrelated to the golem's
 * trade: they send the player exploring. None is something the golem produces, so a golem cannot feed itself.
 */
public final class ModProfessions {

    /** @param id also the entity id and the translation key suffix */
    public record Profession(String id, Predicate<Holder<PoiType>> jobSite, Set<Item> picksUp, SoundEvent workSound,
                             ProfessionNeeds needs, List<Offering> offerings) { }

    private static final List<Profession> ALL = new ArrayList<>();

    /** The composter is the farmer's job site (PoiTypes.FARMER is the Composter POI). */
    public static final Profession FARMER = define("farmer",
            holder -> holder.is(PoiTypes.FARMER),
            Set.of(Items.WHEAT, Items.WHEAT_SEEDS, Items.BEETROOT, Items.BEETROOT_SEEDS, Items.POTATO, Items.CARROT,
                    Items.TORCHFLOWER_SEEDS, Items.PITCHER_POD),
            SoundEvents.VILLAGER_WORK_FARMER,
            new ProfessionNeeds(List.of(
                    Need.supply("seeds", s -> s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS), Items.WHEAT_SEEDS, 16, 8)
            ), s -> s.is(Items.WHEAT) || s.is(Items.BEETROOT) || s.is(Items.MELON_SLICE) || s.is(Items.PUMPKIN)
                    || s.is(Items.MELON) || s.is(Items.SWEET_BERRIES) || s.is(Items.CARROT) || s.is(Items.POTATO),
                    new ProfessionNeeds.Work(TendCrops::plan, TendCrops::new)),
            offerings(Items.SLIME_BALL, Items.GLOW_INK_SAC, Items.PUFFERFISH, Items.COBWEB, Items.RABBIT_FOOT,
                    Items.CACTUS_FLOWER, Items.POINTED_DRIPSTONE, Items.GLOW_BERRIES));

    /** The shepherd's loom is the herder's job site. */
    public static final Profession HERDER = define("herder",
            holder -> holder.is(PoiTypes.SHEPHERD),
            Set.of(Items.BUCKET, Items.WHEAT, Items.CARROT, Items.WHEAT_SEEDS),
            SoundEvents.VILLAGER_WORK_SHEPHERD,
            new ProfessionNeeds(List.of(
                    // buckets are material, not a tool: each milking turns one into a bucket of milk
                    Need.supply("bucket", s -> s.is(Items.BUCKET), Items.BUCKET, 3, 2),
                    Need.supply("feed", s -> s.is(ModTags.ANIMAL_FEED), Items.WHEAT, 16, 8)
            ), s -> s.is(ModTags.HERDER_OUTPUT), new ProfessionNeeds.Work(HerdWork::plan, HerdWork::new)),
            List.of(Offering.of(Items.TURTLE_SCUTE), Offering.of(Items.SEA_PICKLE), Offering.of(Items.COCOA_BEANS),
                    Offering.of(Items.MANGROVE_PROPAGULE), Offering.of(Items.POWDER_SNOW_BUCKET),
                    Offering.anyOf(ItemTags.DECORATED_POT_SHERDS, Items.SNORT_POTTERY_SHERD, "offering.industriousgolems.any_sherd"),
                    Offering.of(Items.SHROOMLIGHT), Offering.of(Items.BIG_DRIPLEAF)));

    /** The fletching table is the lumberjack's job site. */
    public static final Profession LUMBERJACK = define("lumberjack",
            holder -> holder.is(PoiTypes.FLETCHER),
            Set.of(Items.OAK_SAPLING),
            SoundEvents.VILLAGER_WORK_FLETCHER,
            new ProfessionNeeds(List.of(
                    Need.supply("saplings", s -> s.is(ItemTags.SAPLINGS), Items.OAK_SAPLING, 8, 4)
            ), s -> s.is(ModTags.LUMBERJACK_OUTPUT), new ProfessionNeeds.Work(FellTree::plan, FellTree::new)),
            offerings(Items.AMETHYST_SHARD, Items.RESIN_CLUMP, Items.SPORE_BLOSSOM, Items.PINK_PETALS, Items.GOAT_HORN,
                    Items.ARMADILLO_SCUTE, Items.HONEY_BOTTLE, Items.OPEN_EYEBLOSSOM));

    private ModProfessions() { }

    private static List<Offering> offerings(Item... items) {
        List<Offering> list = new ArrayList<>();
        for (Item item : items) list.add(Offering.of(item));
        return List.copyOf(list);
    }

    private static Profession define(String id, Predicate<Holder<PoiType>> jobSite, Set<Item> picksUp,
                                     SoundEvent workSound, ProfessionNeeds needs, List<Offering> offerings) {
        Profession profession = new Profession(id, jobSite, picksUp, workSound, needs, offerings);
        ALL.add(profession);
        return profession;
    }

    public static List<Profession> all() {
        return ALL;
    }
}
