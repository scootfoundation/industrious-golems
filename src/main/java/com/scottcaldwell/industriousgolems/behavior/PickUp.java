package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import com.scottcaldwell.industriousgolems.golem.Stand;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.ModAttachments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;

/**
 * Walks to a stack lying near the job site (something the work dropped, or a supply left out) and picks it up. A golem carries one
 * stack at a time, so with a stack already in hand it collects more of the same item, up to a full stack, before heading for the chest.
 */
public class PickUp extends TaskRunner<PickUp.Plan> {

    /** How far from the job site stacks are collected: past the lumberjack's tree search (16) and the canopy that drops leaves around it. */
    public static final int RADIUS = 24;
    private static final double REACH_SQR = 1.2 * 1.2;
    /** Walking this long without getting hold of the stack means it cannot be reached. */
    private static final int GIVE_UP_TICKS = 240;
    private static final int IGNORE_TICKS = 6000;

    private enum State { WALK, CROUCH, RISE, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("pick-up", "Pick up a lying stack",
            "Any golem with a free hand. Stands where the clip expects the stack to lie, crouches, and takes the whole stack.", State.class)
            .start(State.WALK)
            .state(State.WALK, "Walking to the spot a set distance from the stack, facing it.")
            .state(State.CROUCH, "On the spot, clip playing, hand still empty.")
            .state(State.RISE, "The stack has reached the hand; the clip is finishing.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.WALK, State.CROUCH, "settled on the spot")
            .edge(State.WALK, State.DONE, "stack gone, hand already full, strayed far from the job site, or no path for 12 s (the stack is then ignored for 5 min)")
            .edge(State.CROUCH, State.RISE, "the clip reaches its grab time: the stack moves into the hand")
            .edge(State.CROUCH, State.DONE, "the clip ends without a grab (nothing left to take)")
            .edge(State.RISE, State.DONE, "the clip ends");

    /** @param itemId entity id of the stack to fetch */
    public record Plan(int itemId) { }

    private final Machine.Run<State> machine = GRAPH.run();
    private int timer;
    private int walking;
    private Anim.Pick spec;
    private Vec3 spot;

    public PickUp() {
        super(Plan.class, 900);
    }

    /** The nearest stack worth taking, or null. Needs a free hand. */
    public static Plan plan(ServerLevel level, WorkGolem golem) {
        if (golem.isCarrying() && !golem.canTake(golem.carried())) return null; // a full stack: off to the chest
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
        machine.reset();
        timer = -1;
        walking = 0;
        spot = null;
        spec = Anim.pick(golem.profession().id());
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        switch (machine.state()) {
            case WALK -> walk(level, golem, time);
            case CROUCH, RISE -> crouch(level, golem);
            case DONE -> { }
        }
    }

    private void walk(ServerLevel level, WorkGolem golem, long time) {
        if (!(level.getEntity(plan.itemId()) instanceof ItemEntity item) || item.isRemoved() || !golem.canTake(item.getItem())) {
            done();
            return;
        }
        lookAt(golem, new EntityTracker(item, true));
        // stand where the clip expects the stack to lie: a set distance in front of the golem
        if (spot == null || golem.position().distanceToSqr(spot.x, golem.getY(), spot.z) > 2.0) {
            Vec3 away = golem.position().subtract(item.position()).multiply(1, 0, 1);
            if (away.lengthSqr() < 1.0E-4) away = new Vec3(0, 0, 1);
            spot = item.position().add(away.normalize().scale(spec.dist()));
            if (!Stand.reachable(level, golem, spot)) spot = item.position(); // no room to stand back from it
        }
        if (walking > 0 && golem.distanceToSqr(item) > (RADIUS + 8) * (RADIUS + 8)) { done(); return; }
        if (!standAt(golem, spot, 0.9)) {
            if (++walking > GIVE_UP_TICKS) { // stuck behind something: leave it, so it does not hold up the rest of the work
                golem.getData(ModAttachments.TASK).ignoreItem(item.getId(), time + IGNORE_TICKS);
                done();
            }
            return;
        }
        Face.toward(golem, item.position());
        holdStill(golem);
        if (spec.clip() != null) golem.playClip(spec.clip());
        timer = 0;
        machine.to(State.CROUCH);
    }

    /** Crouching over the stack: it reaches the hand partway through the clip. */
    private void crouch(ServerLevel level, WorkGolem golem) {
        holdStill(golem);
        timer++;
        if (machine.is(State.CROUCH) && timer >= Math.round(spec.grab() * 20)) {
            if (level.getEntity(plan.itemId()) instanceof ItemEntity item && !item.isRemoved() && golem.canTake(item.getItem())) golem.pickUp(item);
            machine.to(State.RISE);
        }
        if (timer >= Math.round(spec.len() * 20)) done();
    }
}
