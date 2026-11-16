package com.scottcaldwell.industriousvillagers.behavior;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousvillagers.ModAttachments;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * A task that runs in the exclusive task activity, so nothing else can pull the villager away from it. It starts when
 * {@link StartTask} hands over a plan of type {@code P}, stops everything else, and on any end (finished, night, panic,
 * timeout) cleans up and returns the villager to whatever its schedule says.
 *
 * <p>Subclasses implement {@link #begin}, {@link #tick}, {@link #end}, and call {@link #finish()} when done.
 */
public abstract class TaskRunner<P> extends Behavior<Villager> {

    private final Class<P> planType;
    private boolean finished = true;
    /** The plan being carried out; null while not running. */
    protected P plan;

    protected TaskRunner(Class<P> planType, int maxDuration) {
        super(ImmutableMap.of(), maxDuration);
        this.planType = planType;
    }

    /** Called once when the task starts; {@link #plan} is set. */
    protected void begin(ServerLevel level, Villager villager, long time) { }

    /** Called when the task ends for any reason, before the villager is handed back to its schedule. */
    protected void end(ServerLevel level, Villager villager, long time) { }

    /** Whether the schedule still allows this task; by default only while it is time to work. */
    protected boolean allowedNow(ServerLevel level, Villager villager) {
        return Tasks.scheduleAllowsWork(level, villager);
    }

    /** Ends the task at the next opportunity. */
    protected final void finish() {
        finished = true;
    }

    @Override
    protected final boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return planType.isInstance(villager.getData(ModAttachments.TASK).plan);
    }

    @Override
    protected final void start(ServerLevel level, Villager villager, long time) {
        plan = planType.cast(villager.getData(ModAttachments.TASK).plan);
        finished = false;
        Tasks.stopOthers(level, villager, this, time);
        begin(level, villager, time);
    }

    @Override
    protected final boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return !finished && plan != null && Tasks.active(villager) && allowedNow(level, villager);
    }

    @Override
    protected final void stop(ServerLevel level, Villager villager, long time) {
        end(level, villager, time);
        HeldItem.release(villager);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        plan = null;
        finished = true;
        Tasks.finish(level, villager);
    }

    // ---- shared movement helpers ----------------------------------------------------------------------------

    /** Stand still: nothing else is running, but make sure no leftover target pulls the villager away. */
    protected static void holdStill(Villager villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    protected static void lookAt(Villager villager, PositionTracker target) {
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, target);
    }

    /** Starts walking to the target unless a walk is already under way. */
    protected static void walkTo(Villager villager, PositionTracker target, int closeEnough) {
        if (!villager.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, closeEnough));
        }
    }
}