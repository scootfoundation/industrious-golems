package com.scottcaldwell.industriousgolems.needs;

import com.scottcaldwell.industriousgolems.ModProfessions;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * What a profession uses up and produces. Declared once per profession in {@link ModProfessions}.
 *
 * @param needs   supplies it fetches from its chest
 * @param outputs what its work produces; golems pick these up and carry them, one stack at a time, to the chest
 * @param work    its own kind of work
 */
public record ProfessionNeeds(List<Need> needs, Predicate<ItemStack> outputs, Work work) {

    /** A profession's own work: the jobs it could do, and the task that does them. */
    public record Work(Candidates jobs, Supplier<Behavior<WorkGolem>> runner, List<Supplier<Behavior<WorkGolem>>> more) {
        public Work(Candidates jobs, Supplier<Behavior<WorkGolem>> runner) {
            this(jobs, runner, List.of());
        }

        /** @param more further runners of the profession (their plan types route to them) */
        public Work { }

        @FunctionalInterface
        public interface Candidates {
            /**
             * Every job the golem could do now, best first, given what it holds. Read lazily, so a search that is only needed when the
             * jobs before it fall through is not made otherwise. This is the one place the work is decided: see {@link Job}.
             */
            Stream<Job> jobs(ServerLevel level, WorkGolem golem);
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
