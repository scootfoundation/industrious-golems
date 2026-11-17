package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.ModActivities;
import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.golem.GolemBrain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;

import java.util.ArrayList;

/** Helpers shared by the golem's task activity and its runners. */
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

    /** Golems work whenever they are charged, day or night; a task only continues while that holds. */
    public static boolean mayWork(WorkGolem golem) {
        return golem.isCharged();
    }

    /** Ends the task: forgets it and hands the golem back to work or idling. */
    public static void finish(ServerLevel level, WorkGolem golem) {
        golem.getData(ModAttachments.TASK).clear();
        if (!active(golem)) return; // something else (panic) already took over
        GolemBrain.resume(golem);
    }
}
