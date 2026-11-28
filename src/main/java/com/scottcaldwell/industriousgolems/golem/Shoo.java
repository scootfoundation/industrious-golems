package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.behavior.ClipRun;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The terracotta golem's way of not letting animals out: before it opens a gate with animals close by, it stops and waves them back.
 * Nothing is hurt or frightened; they are only pushed and walked a few blocks off, on the side of the fence they are on. After a couple
 * of tries it opens anyway, and the {@link Gates gate's guard} holds them back while it goes through.
 */
public class Shoo {

    /** Animals nearer than this to the middle of the gate are shooed before it opens. */
    private static final double NEAR = 2.2;
    private static final int TRIES = 2;
    /** After this long a gate is a fresh matter again. */
    private static final int FORGET_TICKS = 200;
    private static final double PUSH = 0.3, WALK_OFF = 4.0;

    private enum State { WATCH, SHOOING }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("shoo", "Terracotta golem: shoo animals from a gate",
            "Herder only. About to open a gate with animals close by, it stands and waves them back, then goes on. The brain is paused "
                    + "meanwhile.", State.class)
            .start(State.WATCH)
            .state(State.WATCH, "Going about its business.")
            .state(State.SHOOING, "Standing at the gate, facing it, playing the shoo clip; twice in it the animals nearby are pushed back.")
            .edge(State.WATCH, State.SHOOING, "its path is about to open a gate with animals close by (at most twice running)")
            .edge(State.SHOOING, State.WATCH, "the clip ends, or the charge runs out");

    private final WorkGolem golem;
    private final Machine.Run<State> machine = GRAPH.run();
    private ClipRun clip;
    private BlockPos gate, wanted;
    private int tries;
    private long last;

    public Shoo(WorkGolem golem) {
        this.golem = golem;
    }

    /** Asked by {@link Gates} before it opens a gate: false while the animals there are still to be shooed, which then happens next tick. */
    boolean mayOpen(ServerLevel level, BlockPos pos) {
        if (!golem.profession().traits().shoosAnimals() || !golem.isCharged()) return true;
        if (!pos.equals(gate) || level.getGameTime() - last > FORGET_TICKS) {
            gate = pos.immutable();
            tries = 0;
        }
        if (tries >= TRIES || Gates.animalsNear(level, pos, NEAR).isEmpty()) return true;
        wanted = gate;
        return false;
    }

    /** Runs once per tick before the brain. Returns true while shooing, when the brain must not run. */
    boolean tick(ServerLevel level) {
        if (machine.is(State.WATCH)) {
            if (wanted == null || !golem.isCharged() || !golem.clip().isEmpty() && !golem.clip().equals(Anim.T_SHOO)) return false;
            tries++;
            last = level.getGameTime();
            clip = ClipRun.play(golem, Anim.T_SHOO, Anim.T_SHOO_LEN)
                    .at(Anim.T_SHOO_AT, () -> push(level))
                    .at(Anim.T_SHOO_AGAIN, () -> push(level));
            machine.to(State.SHOOING);
        }
        golem.getNavigation().stop();
        golem.setDeltaMovement(0, golem.getDeltaMovement().y, 0);
        Face.toward(golem, Vec3.atCenterOf(wanted));
        if (clip.tick() || !golem.isCharged()) {
            golem.stopClip();
            wanted = null;
            machine.to(State.WATCH);
        }
        return true;
    }

    private void push(ServerLevel level) {
        BlockState state = level.getBlockState(wanted);
        if (!(state.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock)) return;
        for (Animal animal : Gates.animalsNear(level, wanted, NEAR + 0.8)) {
            Vec3 away = golem.gates().away(animal.position(), wanted, state);
            animal.getNavigation().moveTo(animal.getX() + away.x * WALK_OFF, animal.getY(), animal.getZ() + away.z * WALK_OFF, 1.0);
            animal.setDeltaMovement(away.x * PUSH, 0.1, away.z * PUSH);
        }
    }
}
