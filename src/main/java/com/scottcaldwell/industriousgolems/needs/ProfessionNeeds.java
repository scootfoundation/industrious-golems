package com.scottcaldwell.industriousgolems.needs;

import com.scottcaldwell.industriousgolems.ModProfessions;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * What a profession uses up and produces. Declared once per profession in {@link ModProfessions}.
 *
 * @param needs   supplies it fetches from its chest
 * @param outputs what its work produces; golems pick these up and carry them, one stack at a time, to the chest
 * @param work    its own kind of work
 */
public record ProfessionNeeds(List<Need> needs, Predicate<ItemStack> outputs, Work work) {

    /** A profession's own work: what to do next, the task that does it, and which supply would let it do more. */
    public record Work(Planner planner, Supplier<Behavior<WorkGolem>> runner, Wants wants, List<Supplier<Behavior<WorkGolem>>> more,
                       Planner after) {
        public Work(Planner planner, Supplier<Behavior<WorkGolem>> runner, Wants wants) {
            this(planner, runner, wants, List.of(), null);
        }

        /**
         * @param more  further runners of the profession (their plan types route to them)
         * @param after work that comes only once the dispatcher has found nothing to put away, pick up or fetch; may be null
         */
        public Work { }

        @FunctionalInterface
        public interface Planner {
            /** @return a plan for the runner (a type it recognises), or null when there is nothing to do right now */
            Object plan(ServerLevel level, WorkGolem golem);
        }

        @FunctionalInterface
        public interface Wants {
            /** @return the id of the {@link Need} the golem would work with if it had it, or null */
            String need(ServerLevel level, WorkGolem golem);
        }
    }

    /** The needs of this golem's profession. */
    public static ProfessionNeeds of(WorkGolem golem) {
        return golem.profession().needs();
    }

    public Need need(String id) {
        for (Need need : needs) {
            if (need.id().equals(id)) return need;
        }
        return null;
    }

    /** Whether the stack is one of this profession's supplies, with this id. */
    public boolean isSupply(String id, ItemStack stack) {
        Need need = need(id);
        return need != null && !stack.isEmpty() && need.match().test(stack);
    }

    /** Whether golems of this profession pick this up off the ground: what it produces and what it works with. */
    public boolean picksUp(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (outputs.test(stack)) return true;
        for (Need need : needs) {
            if (need.match().test(stack)) return true;
        }
        return false;
    }
}
