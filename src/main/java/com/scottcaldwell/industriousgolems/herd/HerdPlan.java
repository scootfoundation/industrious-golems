package com.scottcaldwell.industriousgolems.herd;

/** One piece of herding work: do {@code action} to the animal with entity id {@code targetId}. */
public record HerdPlan(Action action, int targetId) {

    public enum Action { CULL, SHEAR, MILK, BREED }
}