package com.scottcaldwell.industriousgolems;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.schedule.Activity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModActivities {

    public static final DeferredRegister<Activity> ACTIVITIES =
            DeferredRegister.create(Registries.ACTIVITY, IndustriousGolems.MODID);

    /**
     * An exclusive brain activity for deliberate tasks (visiting a container, eating). While it is active only the
     * core behaviours (movement, looking) and the task itself run, so nothing else in the work routine can walk the
     * golem away or turn its head.
     */
    public static final DeferredHolder<Activity, Activity> TASK =
            ACTIVITIES.register("task", () -> new Activity(IndustriousGolems.MODID + ":task"));

    public static void register(IEventBus modEventBus) {
        ACTIVITIES.register(modEventBus);
    }
}
