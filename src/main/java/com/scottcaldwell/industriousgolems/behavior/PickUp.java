package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.ModAttachments;
import net.minecraft.world.phys.Vec3;
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
    /** Walking this long without getting hold of the stack means it cannot be reached. */
    private static final int GIVE_UP_TICKS = 240;
    private static final int IGNORE_TICKS = 6000;

    /** @param itemId entity id of the stack to fetch */
    public record Plan(int itemId) { }

    private int timer;
    private int walking;
    private Anim.Pick spec;
    private Vec3 spot;
    private boolean grabbed;

    public PickUp() {
        super(Plan.class, 900);
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
        grabbed = false;
        spot = null;
        spec = Anim.pick(golem.profession().id());
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (timer >= 0) { // crouching over the stack: it reaches the hand partway through the clip
            holdStill(golem);
            timer++;
            if (!grabbed && timer >= Math.round(spec.grab() * 20)) {
                grabbed = true;
                if (level.getEntity(plan.itemId()) instanceof ItemEntity item && !item.isRemoved() && !golem.isCarrying()) golem.pickUp(item);
            }
            if (timer >= Math.round(spec.len() * 20)) finish();
            return;
        }
        if (!(level.getEntity(plan.itemId()) instanceof ItemEntity item) || item.isRemoved() || golem.isCarrying()) {
            finish();
            return;
        }
        lookAt(golem, new EntityTracker(item, true));
        // stand where the clip expects the stack to lie: a set distance in front of the golem
        if (spot == null || golem.position().distanceToSqr(spot.x, golem.getY(), spot.z) > 2.0) {
            Vec3 away = golem.position().subtract(item.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(0, 0, 1);
            spot = item.position().add(away.normalize().scale(spec.dist()));
        }
        if (walking > 0 && golem.distanceToSqr(item) > 12 * 12) { finish(); return; }
        if (!standAt(golem, spot, 0.9)) {
            if (++walking > GIVE_UP_TICKS) { // stuck behind something: leave it, so it does not hold up the rest of the work
                golem.getData(ModAttachments.TASK).ignoreItem(item.getId(), time + IGNORE_TICKS);
                finish();
            }
            return;
        }
        Face.toward(golem, item.position());
        holdStill(golem);
        golem.playClip(spec.clip());
        timer = 0;
    }
}
