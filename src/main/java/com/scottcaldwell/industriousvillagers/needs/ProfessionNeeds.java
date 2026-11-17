package com.scottcaldwell.industriousvillagers.needs;

import com.scottcaldwell.industriousvillagers.ModProfessions;
import com.scottcaldwell.industriousvillagers.ModTags;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.Behavior;
import com.scottcaldwell.industriousvillagers.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
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
    public record Work(Planner planner, Supplier<Behavior<WorkGolem>> runner) {
        @FunctionalInterface
        public interface Planner {
            /** @return a plan for the runner (a type it recognises), or null when there is nothing to do right now */
            Object plan(ServerLevel level, WorkGolem golem);
        }
    }

    /** The needs of this golem's profession. */
    public static ProfessionNeeds of(WorkGolem golem) {
        return golem.profession().needs();
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

    /** The golem's own food need (every profession and the basic golem have one). */
    public Need food() {
        return need(Need.FOOD);
    }

    /** Whether the golem carries something that satisfies the need with this id. */
    public boolean has(WorkGolem golem, String id) {
        Need need = need(id);
        return need != null && need.have(golem.getInventory()) > 0;
    }

    /** True when the golem will not work: too hungry (whatever it carries), or a work-gating need is missing. */
    public boolean workBlocked(WorkGolem golem) {
        if (Hunger.tooHungryToWork(golem)) return true;
        for (Need need : needs) {
            if (need.gatesWork() && need.missing(golem)) return true;
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