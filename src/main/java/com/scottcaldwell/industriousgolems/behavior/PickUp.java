package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * Walks to a stack lying near the job site (something the work dropped, or a supply left out) and picks it up: the whole
 * stack, since a golem carries one at a time.
 */
public class PickUp extends TaskRunner<PickUp.Plan> {

    private static final int RADIUS = 12;
    private static final double REACH_SQR = 1.2 * 1.2;
    private static final int PAUSE = 8;

    /** @param itemId entity id of the stack to fetch */
    public record Plan(int itemId) { }

    private int timer;

    public PickUp() {
        super(Plan.class, 600);
    }

    /** The nearest stack worth taking, or null. Needs a free hand. */
    public static Plan plan(ServerLevel level, WorkGolem golem) {
        if (golem.isCarrying()) return null;
        GlobalPos site = golem.getBrain().getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (site == null || !site.dimension().equals(level.dimension())) return null;
        BlockPos at = site.pos();
        ItemEntity best = null;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class,
                new AABB(at).inflate(RADIUS, 4, RADIUS), i -> golem.wantsToPickUp(level, i.getItem()))) {
            if (best == null || golem.distanceToSqr(item) < golem.distanceToSqr(best)) best = item;
        }
        return best == null ? null : new Plan(best.getId());
    }

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        timer = -1;
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (timer >= 0) { // the stack is in hand: a moment, then back to work
            holdStill(golem);
            if (--timer <= 0) finish();
            return;
        }
        if (!(level.getEntity(plan.itemId()) instanceof ItemEntity item) || item.isRemoved() || golem.isCarrying()) {
            finish();
            return;
        }
        lookAt(golem, new EntityTracker(item, true));
        if (golem.distanceToSqr(item) > REACH_SQR) {
            walkTo(golem, new EntityTracker(item, false), 0);
            return;
        }
        holdStill(golem);
        golem.pickUp(item);
        timer = PAUSE;
    }
}
