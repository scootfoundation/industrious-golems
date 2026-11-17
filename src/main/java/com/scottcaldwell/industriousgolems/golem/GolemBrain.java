package com.scottcaldwell.industriousgolems.golem;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.mojang.datafixers.util.Pair;
import com.scottcaldwell.industriousgolems.ModActivities;
import com.scottcaldwell.industriousgolems.ModProfessions;
import com.scottcaldwell.industriousgolems.behavior.ContainerInteraction;
import com.scottcaldwell.industriousgolems.behavior.StartTask;
import com.scottcaldwell.industriousgolems.behavior.Tasks;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AcquirePoi;
import net.minecraft.world.entity.ai.behavior.AnimalPanic;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.CountDownCooldownTicks;
import net.minecraft.world.entity.ai.behavior.DoNothing;
import net.minecraft.world.entity.ai.behavior.InteractWithDoor;
import net.minecraft.world.entity.ai.behavior.LookAtTargetSink;
import net.minecraft.world.entity.ai.behavior.MoveToTargetSink;
import net.minecraft.world.entity.ai.behavior.RandomStroll;
import net.minecraft.world.entity.ai.behavior.RunOne;
import net.minecraft.world.entity.ai.behavior.SetEntityLookTargetSometimes;
import net.minecraft.world.entity.ai.behavior.StrollAroundPoi;
import net.minecraft.world.entity.ai.behavior.StrollToPoi;
import net.minecraft.world.entity.ai.behavior.Swim;
import net.minecraft.world.entity.ai.behavior.ValidateNearbyPoi;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;

import java.util.Optional;
import java.util.Set;

/**
 * The work golems' brain. Core behaviours (movement, looking, doors, panic) always run; the idle activity looks for a
 * job site and loiters near it; the work activity (job site claimed, golem charged) lets {@link StartTask} hand over to
 * the exclusive task activity, which carries out container visits and the profession's own work. A golem without
 * charge rests: nothing but the core behaviours runs.
 */
public final class GolemBrain {

    private static final float STROLL_SPEED = 0.4F;

    private static final ImmutableList<MemoryModuleType<?>> MEMORY_TYPES = ImmutableList.of(
            MemoryModuleType.IS_PANICKING,
            MemoryModuleType.HURT_BY,
            MemoryModuleType.HURT_BY_ENTITY,
            MemoryModuleType.NEAREST_LIVING_ENTITIES,
            MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES,
            MemoryModuleType.WALK_TARGET,
            MemoryModuleType.LOOK_TARGET,
            MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
            MemoryModuleType.PATH,
            MemoryModuleType.GAZE_COOLDOWN_TICKS,
            MemoryModuleType.DOORS_TO_CLOSE,
            MemoryModuleType.JOB_SITE);

    private static final ImmutableList<SensorType<? extends Sensor<? super WorkGolem>>> SENSOR_TYPES = ImmutableList.of(
            SensorType.NEAREST_LIVING_ENTITIES, SensorType.HURT_BY);

    private GolemBrain() { }

    public static Brain.Provider<WorkGolem> provider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES);
    }

    public static Brain<?> makeBrain(ModProfessions.Profession profession, Brain<WorkGolem> brain) {
        brain.addActivity(Activity.CORE, 0, ImmutableList.of(
                new Swim<>(0.8F),
                new AnimalPanic<>(1.5F),
                new LookAtTargetSink(45, 90),
                new MoveToTargetSink(),
                InteractWithDoor.create(),
                new CountDownCooldownTicks(MemoryModuleType.GAZE_COOLDOWN_TICKS),
                ValidateNearbyPoi.create(profession.jobSite(), MemoryModuleType.JOB_SITE),
                AcquirePoi.create(profession.jobSite(), MemoryModuleType.JOB_SITE, false, Optional.empty())));

        ImmutableList<Pair<Integer, ? extends BehaviorControl<? super WorkGolem>>> loiter = ImmutableList.of(
                Pair.of(5, new StartTask()),
                Pair.of(6, SetEntityLookTargetSometimes.create(EntityType.PLAYER, 6.0F, UniformInt.of(40, 80))),
                Pair.of(7, new RunOne<>(ImmutableList.of(
                        Pair.of(StrollAroundPoi.create(MemoryModuleType.JOB_SITE, STROLL_SPEED, 4), 3),
                        Pair.of(StrollToPoi.create(MemoryModuleType.JOB_SITE, STROLL_SPEED, 1, 10), 5),
                        Pair.of(RandomStroll.stroll(STROLL_SPEED, 3, 2), 1),
                        Pair.of(new DoNothing(30, 60), 2)))));
        brain.addActivity(Activity.IDLE, loiter);
        // A golem that has run out of charge is dormant: it stands where it is and does nothing at all.
        brain.addActivity(Activity.REST, ImmutableList.of(Pair.of(0, new DoNothing(20, 40))));
        brain.addActivityWithConditions(Activity.WORK, loiter,
                Set.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));

        // The exclusive task activity: only core behaviours and the task itself run while it is active.
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super WorkGolem>>> tasks = ImmutableList.builder();
        tasks.add(Pair.of(0, new ContainerInteraction()));
        tasks.add(Pair.of(0, new com.scottcaldwell.industriousgolems.behavior.PickUp()));
        if (profession.needs().work() != null) tasks.add(Pair.of(0, profession.needs().work().runner().get()));
        brain.addActivity(ModActivities.TASK.get(), tasks.build());

        brain.setCoreActivities(Set.of(Activity.CORE));
        brain.setDefaultActivity(Activity.IDLE);
        brain.useDefaultActivity();
        return brain;
    }

    /** Picks work or dormancy from the charge, unless a task is running (it hands back via {@link #resume}). */
    public static void updateActivity(WorkGolem golem) {
        if (Tasks.active(golem)) return;
        resume(golem);
    }

    public static void resume(WorkGolem golem) {
        golem.getBrain().setActiveActivityToFirstValid(
                Tasks.mayWork(golem) ? ImmutableList.of(Activity.WORK, Activity.IDLE) : ImmutableList.of(Activity.REST));
    }
}
