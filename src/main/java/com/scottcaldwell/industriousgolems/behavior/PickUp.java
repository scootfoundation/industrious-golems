package com.scottcaldwell.industriousgolems.behavior;

import java.util.Set;
import com.scottcaldwell.industriousgolems.golem.Carry;
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

    /** Farther than this from the stack it was going for and the golem forgets it. */
    private static final double STRAYED = 32;
    /** Walking this long without getting any nearer the stack means it cannot be reached. */
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
            .edge(State.WALK, State.DONE, "stack gone, hand already full, the stack is now far away, or no nearer for 12 s (the stack is then ignored for 5 min)")
            .edge(State.CROUCH, State.RISE, "the clip reaches its grab time: the stack moves into the hand")
            .edge(State.CROUCH, State.DONE, "the clip ends without a grab (nothing left to take)")
            .edge(State.RISE, State.DONE, "the clip ends");

    /** @param itemId entity id of the stack to fetch */
    public record Plan(int itemId) implements Task {
        @Override public java.util.Collection<?> claims() { return java.util.List.of(itemId); }
    }

    private final Machine.Run<State> machine = GRAPH.run();
    private ClipRun clip;
    private final Approach approach = new Approach(GIVE_UP_TICKS);
    private Anim.Pick spec;
    private Vec3 spot;

    public PickUp() {
        super(Plan.class, 900);
    }

    /** The nearest stack worth taking, or null. Needs a free hand. */
    public static Plan plan(ServerLevel level, WorkGolem golem) {
        if (golem.isCarrying() && !golem.canTake(golem.carried())) return null; // a full stack: off to the chest
        Carry carry = golem.carry();
        BlockPos at = carry.aroundSelf() ? golem.blockPosition() : golem.jobSite(level);
        if (at == null) return null;
        TaskState state = golem.getData(ModAttachments.TASK);
        ItemEntity best = null;
        Set<Object> taken = Claims.others(level, golem);
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(carry.reach(), carry.reachHeight(), carry.reach()),
                i -> golem.wantsToPickUp(level, i.getItem()) && !taken.contains(i.getId()) && !state.ignoresEntity(i.getId(), level.getGameTime())
                        && !(carry.dryOnly() && i.isInLiquid()) && !(carry.aroundSelf() && golem.distanceTo(i) > carry.reach()))) {
            if (best == null || golem.distanceToSqr(item) < golem.distanceToSqr(best)) best = item;
        }
        return best == null ? null : new Plan(best.getId());
    }

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        machine.reset();
        approach.reset();
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
            case CROUCH, RISE -> crouch(golem);
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
        if (golem.distanceToSqr(item) > STRAYED * STRAYED) { done(); return; } // it has been carried or has rolled well out of range
        Approach.Outcome outcome = approach.stand(golem, spot, 0.9);
        if (outcome != Approach.Outcome.ARRIVED) {
            if (outcome == Approach.Outcome.UNREACHABLE) { // stuck behind something: leave it, so it does not hold up the rest of the work
                golem.getData(ModAttachments.TASK).ignoreEntity(item.getId(), time + IGNORE_TICKS);
                done();
            }
            return;
        }
        Face.toward(golem, item.position());
        holdStill(golem);
        clip = ClipRun.play(golem, spec.clip(), spec.len()).at(spec.grab(), () -> grab(level, golem));
        machine.to(State.CROUCH);
    }

    /** Crouching over the stack: it reaches the hand partway through the clip. */
    private void crouch(WorkGolem golem) {
        holdStill(golem);
        if (clip.tick()) done();
    }

    private void grab(ServerLevel level, WorkGolem golem) {
        if (level.getEntity(plan.itemId()) instanceof ItemEntity item && !item.isRemoved() && golem.canTake(item.getItem())) {
            golem.pickUp(item);
            double scoop = golem.carry().scoop(); // one movement takes what lies about it too
            if (scoop > 0) {
                for (ItemEntity near : level.getEntitiesOfClass(ItemEntity.class, item.getBoundingBox().inflate(scoop), i -> golem.wantsToPickUp(level, i.getItem()))) golem.pickUp(near);
            }
        }
        machine.to(State.RISE);
    }
}
