package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.behavior.Machine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.util.profiling.ProfilerFiller;

/**
 * Decides, each tick, what is in charge of a golem. This is the one list, strongest first: being handed an offering, sneezing its
 * way out of a tight spot, and otherwise the brain (which picks between dormancy, panic, idling and the task in hand, see
 * {@link GolemBrain}). Anything that is to take a golem over from its brain belongs here. {@link GolemMode} records the outcome.
 */
public final class GolemControl {

    private final WorkGolem golem;
    private final Machine.Run<GolemMode> mode = GolemMode.GRAPH.run();
    private final Sneeze sneeze;
    /** While the game time is before this the golem stands still playing a one-off clip (being handed an offering); 0 when not. */
    private long receivingUntil;

    GolemControl(WorkGolem golem) {
        this.golem = golem;
        this.sneeze = new Sneeze(golem);
    }

    GolemMode mode() {
        return mode.state();
    }

    /** Holds the golem still for this long, while a one-off clip takes its whole attention. */
    void receive(float seconds) {
        receivingUntil = golem.level().getGameTime() + Math.round(seconds * 20);
        golem.getNavigation().stop();
    }

    void tick(ServerLevel level) {
        if (receivingUntil != 0 && level.getGameTime() >= receivingUntil) {
            receivingUntil = 0;
            golem.stopClip();
        }
        if (receivingUntil != 0) {
            golem.getNavigation().stop();
            mode.to(GolemMode.RECEIVING);
        } else if (sneeze.tick(level)) {
            mode.to(GolemMode.SNEEZING); // the task, if any, waits
        } else {
            ProfilerFiller profiler = Profiler.get();
            profiler.push("workGolemBrain");
            golem.getBrain().tick(level, golem);
            GolemBrain.updateActivity(golem);
            mode.to(GolemMode.of(golem));
            profiler.pop();
        }
    }
}
