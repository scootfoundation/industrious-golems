package com.scottcaldwell.industriousgolems.needs;

import com.scottcaldwell.industriousgolems.behavior.Task;
import java.util.function.Predicate;
import net.minecraft.world.item.ItemStack;

/**
 * One thing a golem could do next, and what it takes to do it. A profession lists its candidate jobs best first (see {@link ProfessionNeeds.Work});
 * the dispatcher takes the first it can afford. That is the whole decision: whether a job can be done with the hand as it is, needs a supply
 * fetched from the chest first, or waits until everything else is tidy is a property of the job, so there is no second question that could
 * disagree with the first.
 *
 * @param plan      what the profession's runner is given; null for a job that first needs a supply (the plan is made once the supply is in hand)
 * @param supply    the id of the {@link Need} to fetch from a container before this job can be done, or null when the hand is already right
 * @param accepts   with a supply: which of the container's stacks will do (a cow cannot be fed carrots, say)
 * @param tidyFirst the job only comes after anything carried is put away and anything lying about picked up
 */
public record Job(Task plan, String supply, Predicate<ItemStack> accepts, boolean tidyFirst) {

    /** Doable now, with the hand as it is. */
    public static Job now(Task plan) {
        return new Job(plan, null, s -> true, false);
    }

    /** Doable with a free hand, but only once everything carried or lying about is dealt with. */
    public static Job whenTidy(Task plan) {
        return new Job(plan, null, s -> true, true);
    }

    /** Doable once the golem holds a stack of this supply; any stack of it will do. */
    public static Job needing(String supply) {
        return needing(supply, s -> true);
    }

    /** Doable once the golem holds a stack of this supply that {@code accepts}. */
    public static Job needing(String supply, Predicate<ItemStack> accepts) {
        return new Job(null, supply, accepts, false);
    }

    /** Whether the plan can be started as soon as the hand is in order. */
    public boolean ready() {
        return supply == null && !tidyFirst;
    }

    /** Whether it has to wait for the housekeeping. */
    public boolean afterTidying() {
        return supply == null && tidyFirst;
    }

    public boolean needsSupply() {
        return supply != null;
    }
}
