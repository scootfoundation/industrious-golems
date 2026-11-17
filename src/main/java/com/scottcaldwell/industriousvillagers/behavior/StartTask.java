package com.scottcaldwell.industriousvillagers.behavior;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousvillagers.ModActivities;
import com.scottcaldwell.industriousvillagers.ModAttachments;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import com.scottcaldwell.industriousvillagers.golem.WorkGolem;

/**
 * Decides what the golem should do next and hands it over to the exclusive task activity, in priority order: eat,
 * visit a container, then the profession's own work. Lives in the work activity (and the idle activity for golems
 * without a job, who only ever eat).
 */
public class StartTask extends Behavior<WorkGolem> {

    private static final long VISIT_INTERVAL = ContainerInteraction.COOLDOWN_TICKS;
    private static final long WORK_INTERVAL = 60;

    public StartTask() {
        super(ImmutableMap.of(), 1);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, WorkGolem golem) {
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        TaskState state = golem.getData(ModAttachments.TASK);
        if (needs == null || state.plan != null) return false;

        long now = level.getGameTime();
        Object plan = EatFood.plan(golem);
        if (plan == null && state.due("visit", now, VISIT_INTERVAL)) plan = ContainerInteraction.plan(level, golem);
        if (plan == null && needs.work() != null && !needs.workBlocked(golem) && state.due("work", now, WORK_INTERVAL)) {
            plan = needs.work().planner().plan(level, golem);
        }
        state.plan = plan;
        return plan != null;
    }

    @Override
    protected void start(ServerLevel level, WorkGolem golem, long time) {
        golem.getBrain().setActiveActivityIfPossible(ModActivities.TASK.get());
        if (!Tasks.active(golem)) golem.getData(ModAttachments.TASK).clear();
    }
}