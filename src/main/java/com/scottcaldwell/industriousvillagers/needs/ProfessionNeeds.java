package com.scottcaldwell.industriousvillagers.needs;

import com.scottcaldwell.industriousvillagers.ModProfessions;
import com.scottcaldwell.industriousvillagers.ModTags;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a profession needs in order to work: tools, supplies and food it keeps topped up from containers, which items
 * it produces (always deposited) and, optionally, its own kind of work. Declared once per profession in
 * {@link ModProfessions}; container visits, the work gate, the inventory screen, the status plate and the brain are
 * all derived from it.
 *
 * <p>Tool needs, in declaration order, get the dedicated inventory slots 0..n-1.
 *
 * @param work what the profession does besides eating and restocking; null for none
 */
public record ProfessionNeeds(List<Need> needs, Predicate<ItemStack> outputs, Work work) {

    /** A profession's own work: decides what to do next, and the task that does it. */
    public record Work(Planner planner, Supplier<Behavior<Villager>> runner) {
        @FunctionalInterface
        public interface Planner {
            /** @return a plan for the runner (a type it recognises), or null when there is nothing to do right now */
            Object plan(ServerLevel level, Villager villager);
        }
    }

    /** Everyone without a profession of ours: just hunger and health, no tools, nothing to deposit. */
    private static final ProfessionNeeds BASIC = new ProfessionNeeds(List.of(
            Need.food(s -> s.is(ModTags.VILLAGER_FOOD) || s.is(Items.CARROT) || s.is(Items.POTATO) || s.is(Items.BEETROOT),
                    Items.BREAD, v -> false)
    ), s -> false, null);

    /** The needs shared by every villager without a profession of ours. */
    public static final ProfessionNeeds BASIC_NEEDS = BASIC;

    // ---- lookup -----------------------------------------------------------------------------------------------

    /** The needs of this villager's profession, or null when it has none (vanilla or unemployed). */
    public static ProfessionNeeds of(Villager villager) {
        Holder<VillagerProfession> profession = villager.getVillagerData().profession();
        return profession.unwrapKey().map(ModProfessions::needsFor).orElse(null);
    }

    public static ProfessionNeeds byKey(ResourceKey<VillagerProfession> key) {
        return ModProfessions.needsFor(key);
    }

    public static Set<ResourceKey<VillagerProfession>> professions() {
        return ModProfessions.withNeeds();
    }

    /**
     * The needs that apply to this villager: its profession's, or the basic hunger-and-health needs for any other
     * adult villager (unemployed, nitwit, vanilla professions). Null for babies.
     */
    public static ProfessionNeeds effective(Villager villager) {
        ProfessionNeeds own = of(villager);
        if (own != null) return own;
        return villager.isBaby() ? null : BASIC;
    }

    // ---- queries ----------------------------------------------------------------------------------------------

    /** The needs that are tools, in slot order. */
    public List<Need> toolNeeds() {
        return needs.stream().filter(Need::tool).toList();
    }

    public Need need(String id) {
        for (Need need : needs) {
            if (need.id().equals(id)) return need;
        }
        return null;
    }

    /** The villager's own food need (every profession and the basic villager have one). */
    public Need food() {
        return need(Need.FOOD);
    }

    /** Whether the villager carries something that satisfies the need with this id. */
    public boolean has(Villager villager, String id) {
        Need need = need(id);
        return need != null && need.have(villager.getInventory()) > 0;
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