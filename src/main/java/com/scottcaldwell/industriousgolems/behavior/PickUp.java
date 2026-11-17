package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.ModAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
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
    /** Walking this long without getting hold of the stack means it cannot be reached. */
    private static final int GIVE_UP_TICKS = 240;
    private static final int IGNORE_TICKS = 6000;

    /** @param itemId entity id of the stack to fetch */
    public record Plan(int itemId) { }

    private int timer;
    private int walking;

    public PickUp() {
        super(Plan.class, 600);
    }

    /** The nearest stack worth taking, or null. Needs a free hand. */
    public static Plan plan(ServerLevel level, WorkGolem golem) {
        if (golem.isCarrying()) return null;
        BlockPos at = golem.jobSite(level);
        if (at == null) return null;
        TaskState state = golem.getData(ModAttachments.TASK);
        ItemEntity best = null;
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(RADIUS, 4, RADIUS),
                i -> golem.wantsToPickUp(level, i.getItem()) && !state.ignores(i.getId(), level.getGameTime()))) {
            if (best == null || golem.distanceToSqr(item) < golem.distanceToSqr(best)) best = item;
        }
        return best == null ? null : new Plan(best.getId());
    }

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        timer = -1;
        walking = 0;
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
            if (++walking > GIVE_UP_TICKS) { // stuck behind something: leave it, so it does not hold up the rest of the work
                golem.getData(ModAttachments.TASK).ignoreItem(item.getId(), time + IGNORE_TICKS);
                finish();
                return;
            }
            walkTo(golem, new EntityTracker(item, false), 0);
            return;
        }
        holdStill(golem);
        golem.pickUp(item);
        timer = PAUSE;
    }
}
