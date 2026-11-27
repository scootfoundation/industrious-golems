package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.behavior.Task;
import com.scottcaldwell.industriousgolems.behavior.ContainerInteraction;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.PickUp;
import com.scottcaldwell.industriousgolems.behavior.Tasks;
import com.scottcaldwell.industriousgolems.ModAttachments;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;

/**
 * What a golem is doing at the top level. The brain's activities and the task runners do the work; this is the one place
 * that says which of them is in charge and what lets it hand over to another. {@link #of} reads it off the golem, and each
 * change is checked against {@link #GRAPH}, so a hand-over nobody declared shows up at once.
 */
public enum GolemMode {
    DORMANT, UNEMPLOYED, SEARCHING, DEPOSITING, FETCHING_SUPPLY, PICKING_UP, WORKING, PANICKING;

    public static final Machine.Graph<GolemMode> GRAPH = build();

    private static Machine.Graph<GolemMode> build() {
        Machine.Graph<GolemMode> g = new Machine.Graph<>("golem", "Every golem: who is in charge",
                "The top level, shared by all four golems. The brain's activities pick between these; the task states below say what "
                        + "each task does once it runs.", GolemMode.class)
                .start(UNEMPLOYED)
                .state(DORMANT, "No charge: stands where it is and does nothing. Only the core behaviours (swimming, looking) run.")
                .state(UNEMPLOYED, "Charged but without a chest to work from: wanders and looks for one. Chests are shared, never claimed.")
                .state(SEARCHING, "Charged, with a chest. Every few seconds the dispatcher looks for the next thing to do, in a fixed order "
                        + "(see the dispatcher). Strolls near the chest between looks (not the hay golem, who stands still).")
                .state(DEPOSITING, "Task: carrying a stack, so takes it to the chest and puts it in. Always the first thing to do with a full hand.")
                .state(PICKING_UP, "Task: walks to a stack lying near the chest and picks the whole stack up.")
                .state(FETCHING_SUPPLY, "Task: empty-handed and the work needs a supply, so takes a stack of it out of the chest.")
                .state(WORKING, "Task: the profession's own job (farm, fell trees, dig rock, tend the pen). Which runner has its own diagram.")
                .state(PANICKING, "Hurt: runs away. Whatever task was running is dropped.");
        g.edge(UNEMPLOYED, SEARCHING, "takes up a chest to work from");
        g.edge(SEARCHING, UNEMPLOYED, "its chest is gone");
        g.edge(DORMANT, SEARCHING, "charged again, with a chest");
        g.edge(DORMANT, UNEMPLOYED, "charged again, no chest");
        g.edge(PANICKING, SEARCHING, "calmed down");
        g.anyFrom("any task", SEARCHING, "task finished or given up", UNEMPLOYED, DORMANT, PANICKING, SEARCHING);
        g.edge(PANICKING, UNEMPLOYED, "calmed down, its chest is gone");
        g.edge(SEARCHING, WORKING, "the profession has work (dispatcher step 1)");
        g.edge(SEARCHING, DEPOSITING, "carrying something and no work step applied (step 2)");
        g.edge(SEARCHING, PICKING_UP, "a stack lies near the chest (step 3)");
        g.edge(SEARCHING, FETCHING_SUPPLY, "the work waits for a supply the chest holds (step 4)");
        for (GolemMode task : new GolemMode[]{DEPOSITING, PICKING_UP, FETCHING_SUPPLY, WORKING}) {
            g.quiet(UNEMPLOYED, task, "a task starts from the stored chest position before the job-site memory is (re)acquired");
            g.quiet(task, UNEMPLOYED, "task ends and the job-site memory is gone");
            for (GolemMode other : new GolemMode[]{DEPOSITING, PICKING_UP, FETCHING_SUPPLY, WORKING}) {
                if (other != task) g.quiet(task, other, "the next task starts the moment this one ends");
            }
        }
        g.anyTo(PANICKING, "hurt (any running task is dropped)", DORMANT);
        g.anyTo(DORMANT, "charge runs out");
        return g;
    }

    /** Reads the mode off the golem. */
    public static GolemMode of(WorkGolem golem) {
        if (!golem.isCharged()) return DORMANT;
        if (golem.getBrain().hasMemoryValue(MemoryModuleType.IS_PANICKING)) return PANICKING;
        if (Tasks.active(golem)) {
            Task plan = golem.getData(ModAttachments.TASK).plan;
            if (plan instanceof ContainerInteraction.Plan visit) return visit.supply() == null ? DEPOSITING : FETCHING_SUPPLY;
            if (plan instanceof PickUp.Plan) return PICKING_UP;
            return WORKING;
        }
        return golem.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE) ? SEARCHING : UNEMPLOYED;
    }
}
