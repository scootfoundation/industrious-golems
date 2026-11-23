package com.scottcaldwell.industriousgolems.mine;

import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.phys.Vec3;

/**
 * The stone golem has work it cannot do (it is out of fence posts, beams or torches, or its pack is full and the chest cannot take the diggings): it
 * goes to its chest and stamps and shakes its head there, so a player passing knows to look in its inventory.
 */
public class Fume extends TaskRunner<Fume.Plan> {

    /** How long one outburst lasts; then it looks again, and goes on if it is still stuck. */
    private static final int FUME_TICKS = 160;
    private static final int STALL_TICKS = 160;

    public record Plan() { }

    private enum State { RUN, FUME, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("fume", "Miner: fume at the chest",
            "Stone golem. The mine has work it cannot do for want of supplies or room: it goes to the chest and throws a tantrum.", State.class)
            .start(State.RUN)
            .state(State.RUN, "Walking back to the chest.")
            .state(State.FUME, "By the chest, stamping and shaking its head.")
            .state(State.DONE, "(end) Task over.")
            .edge(State.RUN, State.FUME, "next to the chest (or no nearer for 8 s: it fumes where it is)")
            .edge(State.RUN, State.DONE, "the chest is gone")
            .edge(State.FUME, State.DONE, "the outburst is over (it looks again, and fumes again if it is still stuck)")
            .anyTo(State.DONE, "a hostile mob comes near: the golem cowers instead", State.DONE);

    private final Machine.Run<State> machine = GRAPH.run();
    private int timer;
    private int stalled;
    private double nearest;

    public Fume() {
        super(Plan.class, 12000);
    }

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        machine.reset();
        timer = 0;
        stalled = 0;
        nearest = Double.MAX_VALUE;
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        if (Cower.threatened(level, golem)) { done(); return; }
        switch (machine.state()) {
            case RUN -> {
                BlockPos chest = golem.jobSite(level);
                if (chest == null) { done(); return; }
                double distance = golem.position().distanceTo(Vec3.atCenterOf(chest));
                if (distance < nearest - 0.25) { nearest = distance; stalled = 0; }
                if (distance <= 2.4 || ++stalled > STALL_TICKS) {
                    holdStill(golem);
                    golem.playClip(Anim.S_FUME);
                    machine.to(State.FUME);
                } else walkTo(golem, new BlockPosTracker(Vec3.atCenterOf(chest)), 1);
            }
            case FUME -> {
                holdStill(golem);
                if (++timer >= FUME_TICKS) done();
            }
            case DONE -> { }
        }
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        golem.stopClip();
    }
}
