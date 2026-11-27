package com.scottcaldwell.industriousgolems.behavior;

import net.minecraft.core.BlockPos;

import java.util.HashMap;
import java.util.Map;

/**
 * Transient per-golem hand-over between {@link StartTask} and the runners of the task activity: the plan chosen
 * (whose type says which runner takes it) and when the golem may next look for one.
 */
public class TaskState {

    /** What to do next, or null when idle. Runners recognise their own plan type. */
    public Task plan;
    private long nextPlanAt;
    private final Map<Integer, Long> ignoredEntities = new HashMap<>();
    private final Map<Long, Long> ignoredBlocks = new HashMap<>();

    private BlockPos plantSpot;
    private long plantSpotAt = Long.MIN_VALUE / 2;

    /** Whether the last planting-place search is younger than {@code maxAge} ticks. */
    public boolean plantSpotFresh(long now, long maxAge) {
        return now - plantSpotAt <= maxAge;
    }

    /** Its result: null when it found nothing. */
    public BlockPos plantSpot() {
        return plantSpot;
    }

    public void cachePlantSpot(BlockPos pos, long now) {
        plantSpot = pos;
        plantSpotAt = now;
    }

    public void clear() {
        plan = null;
    }

    boolean planDue(long now) {
        return now >= nextPlanAt;
    }

    void planAt(long time) {
        nextPlanAt = time;
    }

    /** Stops the golem going after this entity (a dropped stack, an animal) until {@code until} (it could not get there). */
    public void ignoreEntity(int entityId, long until) {
        ignoredEntities.put(entityId, until);
    }

    public boolean ignoresEntity(int entityId, long now) {
        Long until = ignoredEntities.get(entityId);
        if (until == null) return false;
        if (now >= until) ignoredEntities.remove(entityId);
        return now < until;
    }

    /** Stops the golem going after this block until {@code until} (it could not get within reach of it). */
    public void ignoreBlock(BlockPos pos, long until) {
        ignoredBlocks.put(pos.asLong(), until);
    }

    public boolean ignoresBlock(BlockPos pos, long now) {
        Long until = ignoredBlocks.get(pos.asLong());
        if (until == null) return false;
        if (now >= until) ignoredBlocks.remove(pos.asLong());
        return now < until;
    }
}
