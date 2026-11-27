package com.scottcaldwell.industriousgolems.herd;

import com.scottcaldwell.industriousgolems.behavior.Task;

/** One piece of herding work: do {@code action} to the animal with entity id {@code targetId}. */
public record HerdPlan(Action action, int targetId) implements Task {

    public enum Action { CULL, SHEAR, MILK, BREED }

    @Override
    public java.util.Collection<?> claims() {
        return java.util.List.of(targetId);
    }
}