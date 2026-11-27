package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * What other golems are already doing, so that two do not go for the same crop, tree, animal, dropped stack or block. Nothing is
 * recorded anywhere: a golem's claim is the task it holds ({@link Task#claims}), so it ends with the task, however that ends. Planners
 * leave claimed targets out when they choose.
 *
 * <p>A claim only holds against a golem that is no nearer the target than its holder (give or take {@link #SLACK}): one that is
 * far off, or behind a wall in the next pen, must not keep a golem standing right by it from the work.
 */
public final class Claims {
    private Claims() { }

    /** Farther apart than this, two golems cannot be after the same thing (the miner works up to 66 blocks from its chest). */
    private static final double RANGE = 140;
    /** How much nearer the target a golem has to be than the claim's holder to take no notice of the claim, in blocks. */
    private static final double SLACK = 6;

    /** The targets other golems hold: block positions and entity ids. */
    public static Set<Object> others(ServerLevel level, WorkGolem golem) {
        Set<Object> taken = new HashSet<>();
        for (WorkGolem other : level.getEntitiesOfClass(WorkGolem.class, golem.getBoundingBox().inflate(RANGE), o -> o != golem && o.isAlive())) {
            Task plan = other.getData(ModAttachments.TASK).plan;
            if (plan == null) continue;
            for (Object claim : plan.claims()) {
                Vec3 at = where(level, claim);
                if (at == null || other.position().distanceTo(at) <= golem.position().distanceTo(at) + SLACK) taken.add(claim);
            }
        }
        return taken;
    }

    /** Where a claimed target is: a block position, or an entity by id; null when the entity is gone. */
    private static Vec3 where(ServerLevel level, Object claim) {
        if (claim instanceof BlockPos pos) return Vec3.atCenterOf(pos);
        Entity entity = claim instanceof Integer id ? level.getEntity(id) : null;
        return entity == null ? null : entity.position();
    }
}
