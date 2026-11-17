package com.scottcaldwell.industriousgolems.needs;

import com.scottcaldwell.industriousgolems.ModProfessions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.behavior.Behavior;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a profession uses up in its work: supplies it keeps topped up from containers, which items it produces (always
 * deposited) and, optionally, its own kind of work. Declared once per profession in {@link ModProfessions}; container
 * visits, the status plate and the brain are derived from it.
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

    public Need need(String id) {
        for (Need need : needs) {
            if (need.id().equals(id)) return need;
        }
        return null;
    }

    /** Whether the golem carries something that satisfies the need with this id. */
    public boolean has(WorkGolem golem, String id) {
        Need need = need(id);
        return need != null && need.have(golem.getInventory()) > 0;
    }

    /** True when the golem will not work: it is dormant, waiting for an offering. */
    public boolean workBlocked(WorkGolem golem) {
        return !golem.isCharged();
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