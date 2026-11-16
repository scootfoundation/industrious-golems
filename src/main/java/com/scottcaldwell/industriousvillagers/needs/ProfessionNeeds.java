package com.scottcaldwell.industriousvillagers.needs;

import com.scottcaldwell.industriousvillagers.ModProfessions;
import net.minecraft.core.Holder;
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
public record ProfessionNeeds(List<Need> needs, Predicate<ItemStack> outputs) {

    /**
     * @param target         how many the villager tops up to when restocking
     * @param refillBelow    restocking starts when it holds fewer than this
     * @param depositSurplus items beyond {@code target} are deposited
     * @param blocking       work cannot (fully) proceed without it, so a shortfall is shown to the player
     */
    public record Need(String id, Predicate<ItemStack> match, ItemStack icon, int target, int refillBelow,
                       boolean depositSurplus, boolean blocking) {

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
            new Need("hoe", s -> s.is(ItemTags.HOES), new ItemStack(Items.IRON_HOE), 1, 1, false, true),
            new Need("seeds", s -> s.is(ItemTags.VILLAGER_PLANTABLE_SEEDS), new ItemStack(Items.WHEAT_SEEDS), 16, 8, true, true),
            new Need("food", s -> s.is(Items.BREAD), new ItemStack(Items.BREAD), 6, 3, false, false)
    ), s -> s.is(Items.WHEAT) || s.is(Items.BEETROOT) || s.is(Items.MELON_SLICE) || s.is(Items.PUMPKIN)
            || s.is(Items.MELON) || s.is(Items.SWEET_BERRIES));

    private static final Map<ResourceKey<VillagerProfession>, ProfessionNeeds> BY_PROFESSION =
            Map.of(ModProfessions.FARMER_KEY, FARMER);

    /** The needs of this villager's profession, or null when it has none (vanilla or unemployed). */
    public static ProfessionNeeds of(Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        return profession.unwrapKey().map(BY_PROFESSION::get).orElse(null);
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
