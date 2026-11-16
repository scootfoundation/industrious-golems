package com.scottcaldwell.industriousvillagers.needs;

import com.scottcaldwell.industriousvillagers.IndustriousVillagers;
import com.scottcaldwell.industriousvillagers.ModProfessions;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What a profession needs in order to work: tools, inputs and food it should keep topped up from containers, plus
 * which items it produces (always deposited). Shared by container interaction, work preconditions and the
 * "missing something" indicator, so a new profession only has to declare one of these.
 */
public record ProfessionNeeds(List<Need> needs, Predicate<ItemStack> outputs, List<String> toolIds, int generalSlots,
                              WorkPlanner planner, java.util.function.Supplier<net.minecraft.world.entity.ai.behavior.Behavior<Villager>> runner) {

    /** Decides what profession-specific work to do next; the plan is handed to that profession's task runner. */
    @FunctionalInterface
    public interface WorkPlanner {
        /** @return an opaque plan, or null when there is nothing to do right now */
        Object plan(net.minecraft.server.level.ServerLevel level, Villager villager);
    }

    /** Ticks between profession work plans. */
    public static final int WORK_INTERVAL = 60;

    /** The needs that are tools, in slot order (shown on the status plate and given dedicated slots). */
    public List<Need> toolNeeds() {
        List<Need> out = new java.util.ArrayList<>();
        for (String id : toolIds) {
            for (Need need : needs) {
                if (need.id().equals(id)) out.add(need);
            }
        }
        return out;
    }

    /** Icon of the food need (shown beside the hunger meter), defaulting to bread. */
    public Item foodIcon() {
        for (Need need : needs) {
            if (need.id().equals("food")) return need.icon().getItem();
        }
        return Items.BREAD;
    }

    /** The need that is the profession's tool (shown on the status plate), or null. */
    public Need toolNeed() {
        List<Need> tools = toolNeeds();
        return tools.isEmpty() ? null : tools.get(0);
    }

    /**
     * @param target         how many the villager tops up to when restocking
     * @param refillBelow    restocking starts when it holds fewer than this
     * @param depositSurplus items beyond {@code target} are deposited
     * @param blocks         when this holds and the villager has none, the shortfall is shown to the player
     * @param gatesWork      such a shortfall also stops the villager working
     */
    public record Need(String id, Predicate<ItemStack> match, ItemStack icon, int target, int refillBelow,
                       boolean depositSurplus, Predicate<Villager> blocks, boolean gatesWork) {

        public boolean missing(Villager villager) {
            return blocks.test(villager) && have(villager.getInventory()) == 0;
        }

        public int have(Container inventory) {
            int n = 0;
            for (int i = 0; i < inventory.getContainerSize(); i++) {
                ItemStack s = inventory.getItem(i);
                if (!s.isEmpty() && match.test(s)) n += s.getCount();
            }
            return n;
        }

        public boolean unmet(Container inventory) {
            return have(inventory) < refillBelow;
        }
    }

    private static final ProfessionNeeds FARMER = new ProfessionNeeds(List.of(
            new Need("hoe", s -> s.is(ItemTags.HOES), new ItemStack(Items.IRON_HOE), 1, 1, false, v -> true, true),
            new Need("seeds", s -> s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS), new ItemStack(Items.WHEAT_SEEDS), 16, 8, true, v -> true, false),
            new Need("food", ProfessionNeeds::isFood, new ItemStack(Items.BREAD), 6, 3, false, Hunger::isHungry, false)
    ), s -> s.is(Items.WHEAT) || s.is(Items.BEETROOT) || s.is(Items.MELON_SLICE) || s.is(Items.PUMPKIN)
            || s.is(Items.MELON) || s.is(Items.SWEET_BERRIES), List.of("hoe"), 7, null, null);

    /** Food villagers will eat and fetch from containers: the data-driven tag industriousvillagers:villager_food. */
    public static final TagKey<Item> VILLAGER_FOOD = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IndustriousVillagers.MODID, "villager_food"));

    public static boolean isFood(ItemStack stack) {
        return stack.is(VILLAGER_FOOD);
    }

    /**
     * Every profession that has needs. Adding an entry here is all the shared machinery needs: menu type and screen,
     * work-package task hook, brain activity, status plate and container visits are all derived from this map.
     */
    public static final TagKey<Item> ANIMAL_FEED = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IndustriousVillagers.MODID, "animal_feed"));
    public static final TagKey<Item> HERDER_OUTPUT = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IndustriousVillagers.MODID, "herder_output"));

    /** Herder: shears, a sword and buckets (each skipped when missing), animal feed, and food for itself. */
    private static final ProfessionNeeds HERDER = new ProfessionNeeds(List.of(
            new Need("shears", s -> s.is(Items.SHEARS), new ItemStack(Items.SHEARS), 1, 1, false, v -> true, false),
            new Need("sword", s -> s.is(ItemTags.SWORDS), new ItemStack(Items.IRON_SWORD), 1, 1, false, v -> true, false),
            new Need("bucket", s -> s.is(Items.BUCKET), new ItemStack(Items.BUCKET), 3, 2, false, v -> true, false),
            new Need("feed", s -> s.is(ANIMAL_FEED), new ItemStack(Items.WHEAT), 16, 8, true, v -> true, false),
            new Need("food", ProfessionNeeds::isFood, new ItemStack(Items.BREAD), 6, 3, false, Hunger::isHungry, false)
    ), s -> s.is(HERDER_OUTPUT), List.of("shears", "sword", "bucket"), 5,
            com.scottcaldwell.industriousvillagers.herd.HerdWork::plan, com.scottcaldwell.industriousvillagers.herd.HerdWork::new);

    public static final TagKey<Item> LUMBERJACK_OUTPUT = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IndustriousVillagers.MODID, "lumberjack_output"));

    /** Lumberjack: an axe to fell with (no axe, no felling), saplings to replant with, and food for itself. */
    private static final ProfessionNeeds LUMBERJACK = new ProfessionNeeds(List.of(
            new Need("axe", s -> s.is(ItemTags.AXES), new ItemStack(Items.IRON_AXE), 1, 1, false, v -> true, true),
            new Need("saplings", s -> s.is(ItemTags.SAPLINGS), new ItemStack(Items.OAK_SAPLING), 8, 4, true, v -> true, false),
            new Need("food", ProfessionNeeds::isFood, new ItemStack(Items.BREAD), 6, 3, false, Hunger::isHungry, false)
    ), s -> s.is(LUMBERJACK_OUTPUT), List.of("axe"), 7,
            com.scottcaldwell.industriousvillagers.lumber.FellTree::plan, com.scottcaldwell.industriousvillagers.lumber.FellTree::new);

    private static final Map<ResourceKey<VillagerProfession>, ProfessionNeeds> BY_PROFESSION =
            Map.of(ModProfessions.FARMER_KEY, FARMER, ModProfessions.HERDER_KEY, HERDER,
                    ModProfessions.LUMBERJACK_KEY, LUMBERJACK);

    public static java.util.Set<ResourceKey<VillagerProfession>> professions() {
        return BY_PROFESSION.keySet();
    }
    public static ProfessionNeeds byKey(ResourceKey<VillagerProfession> key) {
        return BY_PROFESSION.get(key);
    }

    /** The needs of this villager's profession, or null when it has none (vanilla or unemployed). */
    public static ProfessionNeeds of(Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        return profession.unwrapKey().map(BY_PROFESSION::get).orElse(null);
    }

    /** True when the villager will not work: too hungry (whatever it carries), or a work-gating need is missing. */
    public boolean workBlocked(Villager villager) {
        if (Hunger.tooHungryToWork(villager)) return true;
        for (Need need : needs) {
            if (need.gatesWork() && need.missing(villager)) return true;
        }
        return false;
    }

    /** Number of items in {@code slot} that this profession wants to hand over to a container. */
    public int depositAmount(Container inventory, int slot) {
        ItemStack s = inventory.getItem(slot);
        if (s.isEmpty()) return 0;
        if (outputs.test(s)) return s.getCount();
        for (Need need : needs) {
            if (!need.depositSurplus() || !need.match().test(s)) continue;
            int before = 0;
            for (int i = 0; i < slot; i++) {
                ItemStack other = inventory.getItem(i);
                if (!other.isEmpty() && need.match().test(other)) before += other.getCount();
            }
            int keepHere = Math.max(0, need.target() - before);
            return Math.max(0, s.getCount() - keepHere);
        }
        return 0;
    }
}
