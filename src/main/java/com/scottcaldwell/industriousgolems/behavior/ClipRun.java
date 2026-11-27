package com.scottcaldwell.industriousgolems.behavior;

import com.scottcaldwell.industriousgolems.golem.WorkGolem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * A clip playing, with things happening at set moments of it: the blow lands, the lid opens. Start it, add the moments, then
 * {@link #tick} it once a tick until it says the clip is over. Times are seconds into the clip.
 */
public final class ClipRun {

    private record Cue(int tick, Runnable act) { }

    private final List<Cue> cues = new ArrayList<>();
    private final int length;
    private int tick;
    private int next;

    private ClipRun(float seconds) {
        this.length = ticks(seconds);
    }

    /** Starts the clip on the golem; {@code clip} may be null for a job that only takes the time. */
    public static ClipRun play(WorkGolem golem, String clip, float seconds) {
        if (clip != null) golem.playClip(clip);
        return new ClipRun(seconds);
    }

    /** Does {@code act} at this moment. A negative time means the clip has no such moment. Add all moments before the first tick. */
    public ClipRun at(float seconds, Runnable act) {
        if (seconds >= 0) {
            cues.add(new Cue(ticks(seconds), act));
            cues.sort(Comparator.comparingInt(Cue::tick)); // (stable: moments at the same time run in the order given)
        }
        return this;
    }

    /** Advances one tick, doing whatever has come due. True once the clip is over. */
    public boolean tick() {
        tick++;
        while (next < cues.size() && cues.get(next).tick() <= tick) cues.get(next++).act().run();
        return tick >= length;
    }

    private static int ticks(float seconds) {
        return Math.round(seconds * 20);
    }
}
