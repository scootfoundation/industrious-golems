package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.behavior.Task;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.Approach;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.needs.Job;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.Vec3;

import java.util.stream.Stream;

/**
 * The stone golem is a lumbering giant who is scared of everything: when a hostile mob comes near it drops what it is doing, runs back to its chest,
 * covers its head with its arms and shakes until the mob has been gone a while. It never fights.
 */
public class Cower extends TaskRunner<Cower.Plan> {

    /** How near a hostile mob has to come (a creeper: in sight at all, nearly). */
    public static final double FEAR = 10.0, FEAR_CREEPER = 16.0;
    /** How long no mob has been near before it dares to get on with its work. */
    private static final int CALM_TICKS = 100;
    private static final int STALL_TICKS = 160;

    public record Plan() implements Task { }

    private enum State { RUN, COWER, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("cower", "Miner: cower from a mob",
            "Stone golem. A hostile mob comes near: it runs to its chest, covers its head with its arms and shakes until the mob has been gone a while.", State.class)
            .start(State.RUN)
            .state(State.RUN, "Running back to the chest.")
            .state(State.COWER, "Crouched by the chest, arms over its head, shaking. Waiting for the mob to go.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.RUN, State.COWER, "next to the chest (or no nearer for 8 s: it cowers where it is)")
            .edge(State.RUN, State.DONE, "the chest is gone")
            .edge(State.COWER, State.DONE, "no hostile mob has been near for 5 s");

    private final Machine.Run<State> machine = GRAPH.run();
    private int calm;
    private final Approach approach = new Approach(STALL_TICKS);

    public Cower() {
        super(Plan.class, 12000);
    }

    /** Whether a hostile mob is near enough to frighten the golem. */
    public static boolean threatened(ServerLevel level, WorkGolem golem) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, golem.getBoundingBox().inflate(FEAR_CREEPER), e -> e instanceof Enemy && e.isAlive())) {
            if (mob.distanceTo(golem) <= (mob instanceof Creeper ? FEAR_CREEPER : FEAR)) return true;
        }
        return false;
    }

    /** The job of being afraid: first in the miner's list, so it beats everything else. */
    public static Stream<Job> jobs(ServerLevel level, WorkGolem golem) {
        return threatened(level, golem) ? Stream.of(Job.now(new Plan())) : Stream.empty();
    }

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        machine.reset();
        calm = 0;
        approach.reset();
        golem.setPickupSuspended(true);
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        switch (machine.state()) {
            case RUN -> {
                BlockPos chest = golem.jobSite(level);
                if (chest == null) { machine.to(State.DONE); finish(); return; }
                // arrived, or as near as it is going to get: either way it does it here
                if (approach.walk(golem, new BlockPosTracker(Vec3.atCenterOf(chest)), 1,
                        () -> golem.position().distanceTo(Vec3.atCenterOf(chest)) <= 2.2) != Approach.Outcome.MOVING) {
                    holdStill(golem);
                    golem.playClip(Anim.S_COWER);
                    machine.to(State.COWER);
                }
            }
            case COWER -> {
                holdStill(golem);
                calm = threatened(level, golem) ? 0 : calm + 1;
                if (calm >= CALM_TICKS) { machine.to(State.DONE); finish(); }
            }
            case DONE -> { }
        }
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        golem.setPickupSuspended(false);
    }
}
