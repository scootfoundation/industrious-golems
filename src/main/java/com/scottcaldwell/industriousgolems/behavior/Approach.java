package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

import java.util.function.BooleanSupplier;

/**
 * Getting somewhere, for every task. One of these is one errand: it moves the golem and says whether it is still on its way, has
 * arrived, or is not going to. "Not going to" means the same thing everywhere: for {@code patience} ticks it has come no nearer than
 * it already had, so a long walk is fine so long as it makes ground. A runner keeps one, {@link #reset}s it for a new destination and
 * decides what giving up means (end the task, ignore the target for a while, do the thing from where it is).
 *
 * <p>The static methods are the two ways of moving, usable on their own: {@link #walkTo} and {@link #standAt}.
 */
public final class Approach {

    public enum Outcome { MOVING, ARRIVED, UNREACHABLE }

    /** Coming this much nearer than ever before counts as making ground. */
    private static final double GAIN = 0.25;
    private static final float WALK_SPEED = 0.5F;

    private final int patience;
    private double nearest = Double.MAX_VALUE;
    private int stalled;

    /** @param patience ticks without getting any nearer before the destination counts as unreachable */
    public Approach(int patience) {
        this.patience = patience;
    }

    /** Forgets the progress so far: for a new destination. */
    public void reset() {
        nearest = Double.MAX_VALUE;
        stalled = 0;
    }

    /** Walks to the spot and settles exactly onto it, see {@link #standAt}. */
    public Outcome stand(WorkGolem golem, Vec3 spot, double tolerance) {
        return stand(golem, spot, tolerance, () -> true);
    }

    /** As {@link #stand(WorkGolem, Vec3, double)}, arrived only once {@code ready} holds there too (the block is in reach, say). */
    public Outcome stand(WorkGolem golem, Vec3 spot, double tolerance, BooleanSupplier ready) {
        if (standAt(golem, spot, tolerance) && ready.getAsBoolean()) return Outcome.ARRIVED;
        return progress(golem.position().distanceTo(spot));
    }

    /** Walks towards the target until {@code there} holds. */
    public Outcome walk(WorkGolem golem, PositionTracker target, int closeEnough, BooleanSupplier there) {
        if (there.getAsBoolean()) return Outcome.ARRIVED;
        walkTo(golem, target, closeEnough);
        return progress(golem.position().distanceTo(target.currentPosition()));
    }

    private Outcome progress(double distance) {
        if (distance < nearest - GAIN) {
            nearest = distance;
            stalled = 0;
        }
        return ++stalled > patience ? Outcome.UNREACHABLE : Outcome.MOVING;
    }

    // ---- moving -----------------------------------------------------------------------------------------------

    /**
     * Starts walking to the target, unless a walk to that column (or the one beside it) is already under way. The height is not compared:
     * a golem climbing a step asks for the same place at a new height, which is no reason to start the path over.
     */
    public static void walkTo(WorkGolem golem, PositionTracker target, int closeEnough) {
        WalkTarget current = golem.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (current != null) {
            BlockPos going = current.getTarget().currentBlockPosition(), wanted = target.currentBlockPosition();
            if (Math.abs(going.getX() - wanted.getX()) <= 1 && Math.abs(going.getZ() - wanted.getZ()) <= 1) return;
        }
        golem.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, WALK_SPEED, closeEnough));
    }

    /**
     * Walks to within {@code tolerance} of the spot, then settles exactly onto it, so a clip that assumes a precise distance from its
     * target (the chest, a crop, a sapling) fits. Only measures across the ground. Returns true once it is standing there.
     */
    public static boolean standAt(WorkGolem golem, Vec3 spot, double tolerance) {
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
    public static boolean fenceBetween(WorkGolem golem, Vec3 spot) {
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
}
