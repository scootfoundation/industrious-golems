package com.scottcaldwell.industriousgolems.behavior;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousgolems.ModActivities;
import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.needs.ProfessionNeeds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;

/**
 * Decides what the golem should do next and hands it over to the exclusive task activity. Lives in the work and idle activities; a dormant golem starts nothing.
 */
public class StartTask extends Behavior<WorkGolem> {

    /** How often the golem looks for something to do (this and the walking speed set how fast it works). */
    private static final long PLAN_INTERVAL = 20;

    public StartTask() {
        super(ImmutableMap.of(), 1);
    }

    /**
     * In order: the profession's work (which only starts with a free hand, or with what it can use in the hand), then
     * putting whatever else is carried in the chest, then fetching a stack lying about, then a supply from the chest.
     */
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, WorkGolem golem) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        TaskState state = golem.getData(ModAttachments.TASK);
        if (needs == null || state.plan != null || !Tasks.mayWork(golem)) return false;
        if (!state.due("plan", level.getGameTime(), PLAN_INTERVAL)) return false;

        Object plan = needs.work() == null ? null : needs.work().planner().plan(level, golem);
        if (plan == null) plan = ContainerInteraction.planDeposit(level, golem);
        if (plan == null) plan = PickUp.plan(level, golem);
        if (plan == null) plan = ContainerInteraction.planSupply(level, golem);
        state.plan = plan;
        return plan != null;
    }

    @Override
    protected void start(ServerLevel level, WorkGolem golem, long time) {
        golem.getBrain().setActiveActivityIfPossible(ModActivities.TASK.get());
        if (!Tasks.active(golem)) golem.getData(ModAttachments.TASK).clear();
    }
}