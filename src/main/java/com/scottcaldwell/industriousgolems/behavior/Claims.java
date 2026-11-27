package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.server.level.ServerLevel;

import java.util.HashSet;
import java.util.Set;

/**
 * What other golems are already doing, so that two do not go for the same crop, tree, animal, dropped stack or block. Nothing is
 * recorded anywhere: a golem's claim is the task it holds ({@link Task#claims}), so it ends with the task, however that ends. Planners
 * leave claimed targets out when they choose.
 */
public final class Claims {
    private Claims() { }

    /** Farther apart than this, two golems cannot be after the same thing (the miner works up to 66 blocks from its chest). */
    private static final double RANGE = 140;

    /** The targets other golems hold: block positions and entity ids. */
    public static Set<Object> others(ServerLevel level, WorkGolem golem) {
        Set<Object> taken = new HashSet<>();
        for (WorkGolem other : level.getEntitiesOfClass(WorkGolem.class, golem.getBoundingBox().inflate(RANGE), o -> o != golem && o.isAlive())) {
            Task plan = other.getData(ModAttachments.TASK).plan;
            if (plan != null) taken.addAll(plan.claims());
        }
        return taken;
    }
}
