package com.scottcaldwell.industriousvillagers.behavior;

import com.scottcaldwell.industriousvillagers.ModActivities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import com.scottcaldwell.industriousvillagers.golem.WorkGolem;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.attribute.EnvironmentAttributes;

import java.util.ArrayList;

public final class Tasks {
    private Tasks() { }

    /** Stops everything else that is running so the task starts from a clean slate (core behaviours restart next tick). */
    @SuppressWarnings("deprecation")
    public static void stopOthers(ServerLevel level, WorkGolem golem, BehaviorControl<?> self, long time) {
        for (BehaviorControl<? super WorkGolem> running : new ArrayList<>(golem.getBrain().getRunningBehaviors())) {
            if (running != self) running.doStop(level, golem, time);
        }
    }

    public static boolean active(WorkGolem golem) {
        return golem.getBrain().isActive(ModActivities.TASK.get());
    }

    /** The task only continues while the golem's schedule still says it is time to work. */
    public static boolean scheduleAllowsWork(WorkGolem golem) {
        return golem.level() instanceof ServerLevel level && scheduleAllowsWork(level, golem);
    }

    public static boolean scheduleAllowsWork(ServerLevel level, WorkGolem golem) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.VILLAGER_ACTIVITY, golem.position()) == Activity.WORK;
    }

    /** Meals are allowed whenever the golem is not meant to be asleep. */
    public static boolean scheduleAllowsEating(ServerLevel level, WorkGolem golem) {
        return true; // golems never sleep, so they eat whenever they are hungry
    }

    /** Ends the task: forgets it and hands the golem back to work or idling. */
    public static void finish(ServerLevel level, WorkGolem golem) {
        golem.getData(com.scottcaldwell.industriousvillagers.ModAttachments.TASK).clear();
        if (!active(golem)) return; // something else (panic) already took over
        com.scottcaldwell.industriousvillagers.golem.GolemBrain.resume(golem);
    }
}
