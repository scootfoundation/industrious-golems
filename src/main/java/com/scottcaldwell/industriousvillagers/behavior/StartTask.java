package com.scottcaldwell.industriousvillagers.behavior;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousvillagers.ModActivities;
import com.scottcaldwell.industriousvillagers.ModAttachments;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Decides what the villager should do next and hands it over to the exclusive task activity, in priority order: eat,
 * visit a container, then the profession's own work. Lives in the work activity (and the idle activity for villagers
 * without a job, who only ever eat).
 */
public class StartTask extends Behavior<Villager> {

    private static final long VISIT_INTERVAL = ContainerInteraction.COOLDOWN_TICKS;
    private static final long WORK_INTERVAL = 60;

    public StartTask() {
        this(true);
    }

    /** @param requiresJobSite false for villagers without a job */
    public StartTask(boolean requiresJobSite) {
        super(requiresJobSite ? ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT) : ImmutableMap.of(), 1);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        ProfessionNeeds needs = ProfessionNeeds.effective(villager);
        TaskState state = villager.getData(ModAttachments.TASK);
        if (needs == null || state.plan != null) return false;

        long now = level.getGameTime();
        Object plan = EatFood.plan(villager);
        if (plan == null && state.due("visit", now, VISIT_INTERVAL)) plan = ContainerInteraction.plan(level, villager);
        if (plan == null && needs.work() != null && !needs.workBlocked(villager) && state.due("work", now, WORK_INTERVAL)) {
            plan = needs.work().planner().plan(level, villager);
        }
        state.plan = plan;
        return plan != null;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        villager.getBrain().setActiveActivityIfPossible(ModActivities.TASK.get());
        if (!Tasks.active(villager)) villager.getData(ModAttachments.TASK).clear();
    }
}