package com.scottcaldwell.industriousgolems.behavior;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousgolems.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;

/**
 * A task that runs in the exclusive task activity, so nothing else can pull the golem away from it. It starts when
 * {@link StartTask} hands over a plan of type {@code P}, stops everything else, and on any end (finished, night, panic,
 * timeout) cleans up and returns the golem to whatever its schedule says.
 *
 * <p>Subclasses implement {@link #begin}, {@link #tick}, {@link #end}, and call {@link #finish()} when done.
 */
public abstract class TaskRunner<P> extends Behavior<WorkGolem> {

    private final Class<P> planType;
    private boolean finished = true;
    /** The plan being carried out; null while not running. */
    protected P plan;

    protected TaskRunner(Class<P> planType, int maxDuration) {
        super(ImmutableMap.of(), maxDuration);
        this.planType = planType;
    }

    /** Called once when the task starts; {@link #plan} is set. */
    protected void begin(ServerLevel level, WorkGolem golem, long time) { }

    /** Called when the task ends for any reason, before the golem is handed back to its schedule. */
    protected void end(ServerLevel level, WorkGolem golem, long time) { }

    /** Whether the task may still continue; by default only while the golem is charged. */
    protected boolean allowedNow(ServerLevel level, WorkGolem golem) {
        return Tasks.mayWork(golem);
    }

    /** Ends the task at the next opportunity. */
    protected final void finish() {
        finished = true;
    }

    @Override
    protected final boolean checkExtraStartConditions(ServerLevel level, WorkGolem golem) {
        return planType.isInstance(golem.getData(ModAttachments.TASK).plan);
    }

    @Override
    protected final void start(ServerLevel level, WorkGolem golem, long time) {
        plan = planType.cast(golem.getData(ModAttachments.TASK).plan);
        finished = false;
        Tasks.stopOthers(level, golem, this, time);
        begin(level, golem, time);
    }

    @Override
    protected final boolean canStillUse(ServerLevel level, WorkGolem golem, long time) {
        return !finished && plan != null && Tasks.active(golem) && allowedNow(level, golem);
    }

    @Override
    protected final void stop(ServerLevel level, WorkGolem golem, long time) {
        end(level, golem, time);
        golem.stopClip();
        golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        golem.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        plan = null;
        finished = true;
        Tasks.finish(level, golem);
    }

    // ---- shared movement helpers ----------------------------------------------------------------------------

    /** Stand still: nothing else is running, but make sure no leftover target pulls the golem away. */
    protected static void holdStill(WorkGolem golem) {
        golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        golem.getNavigation().stop();
    }

    protected static void lookAt(WorkGolem golem, PositionTracker target) {
        golem.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, target);
    }

    /**
     * Walks to within {@code tolerance} of the spot, then settles exactly onto it, so a clip that assumes a precise distance from its
     * target (the chest, a crop, a sapling) fits. Returns true once it is standing there.
     */
    protected static boolean standAt(WorkGolem golem, Vec3 spot, double tolerance) {
        double dx = spot.x - golem.getX(), dz = spot.z - golem.getZ();
        double d = Math.sqrt(dx * dx + dz * dz);
        // (within the block the walk would end in, the last bit is a shuffle; never through a fence, which it goes round by the gate)
        if (d <= Math.max(tolerance, 1.25) && !fenceBetween(golem, spot)) {
            golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
            golem.getNavigation().stop();
            if (d > 0.03) { // a last shuffle onto the exact spot
                double step = Math.min(0.1, d);
                golem.setPos(golem.getX() + dx / d * step, golem.getY(), golem.getZ() + dz / d * step);
                golem.setDeltaMovement(dx / d * step * 0.5, golem.getDeltaMovement().y, dz / d * step * 0.5);
                return false;
            }
            return true;
        }
        walkTo(golem, new BlockPosTracker(BlockPos.containing(spot.x, golem.getY(), spot.z)), 0);
        return false;
    }

    /** Whether a fence, gate or wall stands on the straight line from the golem to the spot. */
    protected static boolean fenceBetween(WorkGolem golem, Vec3 spot) {
        double dx = spot.x - golem.getX(), dz = spot.z - golem.getZ();
        int steps = (int) Math.ceil(Math.sqrt(dx * dx + dz * dz) / 0.2);
        BlockPos own = golem.blockPosition();
        for (int i = 1; i <= steps; i++) {
            BlockPos at = BlockPos.containing(golem.getX() + dx * i / steps, golem.getY() + 0.1, golem.getZ() + dz * i / steps);
            if (at.equals(own)) continue;
            var state = golem.level().getBlockState(at);
            if (state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.WALLS)) return true;
        }
        return false;
    }

    /** Starts walking to the target unless a walk is already under way. */
    protected static void walkTo(WorkGolem golem, PositionTracker target, int closeEnough) {
        if (!golem.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)) {
            golem.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.5F, closeEnough));
        }
    }
}