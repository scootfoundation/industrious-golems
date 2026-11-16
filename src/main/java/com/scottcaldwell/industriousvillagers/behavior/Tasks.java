package com.scottcaldwell.industriousvillagers.behavior;

import com.scottcaldwell.industriousvillagers.ModActivities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.attribute.EnvironmentAttributes;

import java.util.ArrayList;

public final class Tasks {
    private Tasks() { }

    /** Stops everything else that is running so the task starts from a clean slate (core behaviours restart next tick). */
    @SuppressWarnings("deprecation")
    public static void stopOthers(ServerLevel level, Villager villager, BehaviorControl<?> self, long time) {
        for (BehaviorControl<? super Villager> running : new ArrayList<>(villager.getBrain().getRunningBehaviors())) {
            if (running != self) running.doStop(level, villager, time);
        }
    }

    public static boolean active(Villager villager) {
        return villager.getBrain().isActive(ModActivities.TASK.get());
    }

    /** The task only continues while the villager's schedule still says it is time to work. */
    public static boolean scheduleAllowsWork(ServerLevel level, Villager villager) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.VILLAGER_ACTIVITY, villager.position()) == Activity.WORK;
    }

    /** Meals are allowed whenever the villager is not meant to be asleep. */
    public static boolean scheduleAllowsEating(ServerLevel level, Villager villager) {
        return level.environmentAttributes().getValue(EnvironmentAttributes.VILLAGER_ACTIVITY, villager.position()) != Activity.REST;
    }

    /** Ends the task: forgets it and hands the villager back to whatever its schedule says. */
    public static void finish(ServerLevel level, Villager villager) {
        villager.getData(com.scottcaldwell.industriousvillagers.ModAttachments.TASK).clear();
        if (!active(villager)) return; // something else (panic, raid) already took over
        Activity scheduled = level.environmentAttributes().getValue(EnvironmentAttributes.VILLAGER_ACTIVITY, villager.position());
        villager.getBrain().setActiveActivityIfPossible(scheduled);
    }
}
