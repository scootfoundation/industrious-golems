package com.scottcaldwell.industriousvillagers.behavior;

import java.util.HashMap;
import java.util.Map;

/**
 * Transient per-golem hand-over between {@link StartTask} and the runners of the task activity: the plan chosen
 * (whose type says which runner takes it) and when each kind of task may next be planned.
 */
public class TaskState {

    /** What to do next, or null when idle. Runners recognise their own plan type. */
    public Object plan;
    private final Map<String, Long> nextAt = new HashMap<>();

    public void clear() {
        plan = null;
    }

    /** True (and re-arms the cooldown) when a task of this kind may be planned now. */
    boolean due(String kind, long now, long interval) {
        if (now < nextAt.getOrDefault(kind, 0L)) return false;
        nextAt.put(kind, now + interval);
        return true;
    }
}