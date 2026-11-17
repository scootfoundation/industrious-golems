package com.scottcaldwell.industriousgolems.behavior;

import java.util.HashMap;
import java.util.Map;

/**
 * Transient per-golem hand-over between {@link StartTask} and the runners of the task activity: the plan chosen
 * (whose type says which runner takes it) and when the golem may next look for one.
 */
public class TaskState {

    /** What to do next, or null when idle. Runners recognise their own plan type. */
    public Object plan;
    private long nextPlanAt;
    private final Map<Integer, Long> ignoredItems = new HashMap<>();

    public void clear() {
        plan = null;
    }

    boolean planDue(long now) {
        return now >= nextPlanAt;
    }

    void planAt(long time) {
        nextPlanAt = time;
    }

    /** Stops the golem going after this dropped stack until {@code until} (it could not get there). */
    void ignoreItem(int entityId, long until) {
        ignoredItems.put(entityId, until);
    }

    boolean ignores(int entityId, long now) {
        Long until = ignoredItems.get(entityId);
        if (until == null) return false;
        if (now >= until) ignoredItems.remove(entityId);
        return now < until;
    }
}