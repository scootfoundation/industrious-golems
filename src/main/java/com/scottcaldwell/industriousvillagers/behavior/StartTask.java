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
 * Lives in the work activity. Decides whether the villager should now eat or visit a container and, if so, hands it
 * over to the exclusive task activity.
 */
public class StartTask extends Behavior<Villager> {

    public StartTask() {
        super(ImmutableMap.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT), 1);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        if (ProfessionNeeds.of(villager) == null) return false;
        TaskState state = villager.getData(ModAttachments.TASK);
        if (state.type != TaskState.Type.NONE) return false;

        if (EatFood.shouldEat(villager)) {
            state.type = TaskState.Type.EAT;
            return true;
        }
        long now = level.getGameTime();
        if (now >= state.nextVisit) {
            state.nextVisit = now + ContainerInteraction.COOLDOWN_TICKS;
            ContainerInteraction.Plan plan = ContainerInteraction.plan(level, villager);
            if (plan != null) {
                state.type = TaskState.Type.VISIT;
                state.chest = plan.pos();
                state.deposit = plan.deposit();
                state.take = plan.take();
                return true;
            }
        }
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        if (needs.planner() != null && now >= state.nextWork && !needs.workBlocked(villager)) {
            state.nextWork = now + ProfessionNeeds.WORK_INTERVAL;
            Object work = needs.planner().plan(level, villager);
            if (work != null) {
                state.type = TaskState.Type.WORK;
                state.work = work;
                return true;
            }
        }
        return false;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        villager.getBrain().setActiveActivityIfPossible(ModActivities.TASK.get());
        if (!Tasks.active(villager)) villager.getData(ModAttachments.TASK).clear();
    }
}
