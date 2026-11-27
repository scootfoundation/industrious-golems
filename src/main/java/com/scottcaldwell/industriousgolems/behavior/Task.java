package com.scottcaldwell.industriousgolems.behavior;

import java.util.Collection;
import java.util.List;

/**
 * Something a golem has decided to do: what {@link StartTask} hands to the task activity. Each runner takes one kind of task
 * (see {@link TaskRunner}); every plan record implements this.
 */
public interface Task {

    /** What this task is working on (block positions, entity ids), which other golems then leave alone; see {@link Claims}. */
    default Collection<?> claims() {
        return List.of();
    }
}
