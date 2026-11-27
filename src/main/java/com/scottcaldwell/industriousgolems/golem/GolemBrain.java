package com.scottcaldwell.industriousgolems.golem;

import com.google.common.collect.ImmutableList;
import com.mojang.datafixers.util.Pair;
import com.scottcaldwell.industriousgolems.ModActivities;
import com.scottcaldwell.industriousgolems.ModProfessions;
import com.scottcaldwell.industriousgolems.behavior.ContainerInteraction;
import com.scottcaldwell.industriousgolems.behavior.PickUp;
import com.scottcaldwell.industriousgolems.behavior.StartTask;
import com.scottcaldwell.industriousgolems.behavior.Tasks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.ActivityData;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.AnimalPanic;
import net.minecraft.world.entity.ai.behavior.Behavior;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.sensing.Sensor;
import net.minecraft.world.entity.ai.sensing.SensorType;
import net.minecraft.world.entity.schedule.Activity;

import java.util.List;
import java.util.Set;

/**
 * The work golems' brain. Core behaviours (movement, looking, doors, panic) always run; the idle activity
 * loiters near its chest; the work activity (it has a chest and is charged) lets {@link StartTask} hand over to
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

    /** Strolling about near the chest, only with free hands: a golem holding something with nowhere to put it (a full chest) stands and holds it. */
    private record Wander(BehaviorControl<WorkGolem> stroll) implements BehaviorControl<WorkGolem> {
        @Override public Behavior.Status getStatus() { return stroll.getStatus(); }
        @Override public Set<MemoryModuleType<?>> getRequiredMemories() { return stroll.getRequiredMemories(); }
        @Override public boolean tryStart(ServerLevel level, WorkGolem golem, long time) { return !golem.isCarrying() && stroll.tryStart(level, golem, time); }
        @Override public void tickOrStop(ServerLevel level, WorkGolem golem, long time) {
            if (golem.isCarrying()) stroll.doStop(level, golem, time);
            else stroll.tickOrStop(level, golem, time);
        }
        @Override public void doStop(ServerLevel level, WorkGolem golem, long time) { stroll.doStop(level, golem, time); }
        @Override public String debugString() { return stroll.debugString(); }
    }

    private GolemBrain() { }

    public static Brain.Provider<WorkGolem> provider() {
        return Brain.provider(MEMORY_TYPES, SENSOR_TYPES, GolemBrain::activities);
    }

    private static List<ActivityData<WorkGolem>> activities(WorkGolem golem) {
        ModProfessions.Profession profession = golem.profession();
        ActivityData<WorkGolem> core = ActivityData.<WorkGolem>create(Activity.CORE, 0, ImmutableList.of(
                new Swim<>(0.8F),
                new AnimalPanic<>(1.5F),
                new LookAtTargetSink(45, 90),
                new MoveToTargetSink(),
                InteractWithDoor.create(),
                new CountDownCooldownTicks(MemoryModuleType.GAZE_COOLDOWN_TICKS))); // (which chest it works from is looked after by Workplace: shared, never claimed)

        // some stand still when they have nothing to do (the hay golem); the others wander near their chest
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super WorkGolem>>> idle = ImmutableList.builder();
        idle.add(Pair.of(5, new StartTask()));
        idle.add(Pair.of(6, SetEntityLookTargetSometimes.create(EntityTypes.PLAYER, 6.0F, UniformInt.of(40, 80))));
        if (profession.traits().strolls()) {
            idle.add(Pair.of(7, new Wander(new RunOne<>(ImmutableList.of(
                    Pair.of(StrollAroundPoi.create(MemoryModuleType.JOB_SITE, STROLL_SPEED, 4), 3),
                    Pair.of(StrollToPoi.create(MemoryModuleType.JOB_SITE, STROLL_SPEED, 1, 10), 5),
                    Pair.of(RandomStroll.stroll(STROLL_SPEED, 3, 2), 1),
                    Pair.of(new DoNothing(30, 60), 2))))));
        }
        ImmutableList<Pair<Integer, ? extends BehaviorControl<? super WorkGolem>>> loiter = idle.build();
        ActivityData<WorkGolem> idleActivity = ActivityData.<WorkGolem>create(Activity.IDLE, loiter);
        // A golem that has run out of charge is dormant: it stands where it is and does nothing at all.
        ActivityData<WorkGolem> rest = ActivityData.<WorkGolem>create(Activity.REST, ImmutableList.of(Pair.of(0, new DoNothing(20, 40))));
        ActivityData<WorkGolem> work = ActivityData.<WorkGolem>create(Activity.WORK, loiter,
                Set.of(Pair.of(MemoryModuleType.JOB_SITE, MemoryStatus.VALUE_PRESENT)));

        // The exclusive task activity: only core behaviours and the task itself run while it is active.
        ImmutableList.Builder<Pair<Integer, ? extends BehaviorControl<? super WorkGolem>>> tasks = ImmutableList.builder();
        tasks.add(Pair.of(0, new ContainerInteraction()));
        tasks.add(Pair.of(0, new PickUp()));
        tasks.add(Pair.of(0, profession.needs().work().runner().get()));
        for (var more : profession.needs().work().more()) tasks.add(Pair.of(0, more.get()));
        ActivityData<WorkGolem> task = ActivityData.<WorkGolem>create(ModActivities.TASK.get(), tasks.build());
        return List.of(core, idleActivity, rest, work, task);
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
