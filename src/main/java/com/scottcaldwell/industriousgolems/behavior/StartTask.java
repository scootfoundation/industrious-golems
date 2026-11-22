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

    /** How soon the golem looks for the next thing to do after starting a task (this and the walking speed set how fast it works). */
    private static final long BUSY_INTERVAL = 20;
    /** How soon it looks again when it found nothing: the searches scan the surroundings. */
    private static final long IDLE_INTERVAL = 80;

    public StartTask() {
        super(ImmutableMap.of(), 1);
    }

    /**
     * In order: the profession's work (which only starts with a free hand, or with what it can use in the hand), then
     * putting whatever else is carried in the chest, then fetching a stack lying about, then a supply from the chest.
     */
    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, WorkGolem golem) {
        TaskState state = golem.getData(ModAttachments.TASK);
        if (state.plan != null || !Tasks.mayWork(golem)) return false;
        long now = level.getGameTime();
        if (!state.planDue(now)) return false;

        Object plan = ProfessionNeeds.of(golem).work().planner().plan(level, golem);
        if (plan == null) plan = ContainerInteraction.planDeposit(level, golem);
        if (plan == null) plan = PickUp.plan(level, golem);
        if (plan == null) plan = ContainerInteraction.planSupply(level, golem);
        state.plan = plan;
        // nothing to do: look again later, the searches are not free
        state.planAt(now + (plan == null && !golem.isCarrying() ? IDLE_INTERVAL : BUSY_INTERVAL)); // a full hand is never left idle for long
        return plan != null;
    }

    @Override
    protected void start(ServerLevel level, WorkGolem golem, long time) {
        golem.getBrain().setActiveActivityIfPossible(ModActivities.TASK.get());
        if (!Tasks.active(golem)) golem.getData(ModAttachments.TASK).clear();
    }
}