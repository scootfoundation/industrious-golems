package com.scottcaldwell.industriousgolems.client.anim;

import java.util.ArrayList;
import java.util.List;

/** One animation: a pose as a function of time since it began. Sequences string clips together. */
public final class Clip {

    /** Who the clip is for, and what it is told about the world. */
    public static final class Ctx {
        /** The item being handled: "log", "sapling", "stick", "wheat", "seeds", "bread", "ore", ... or null. */
        public String item;
        public int block;
        public float stand;
        public boolean thrown;
        /** Per-entity animation memory (cap lag, ik caches). */
        public AnimState state;
        public float len;

        Ctx copy() {
            Ctx c = new Ctx();
            c.item = item;
            c.block = block;
            c.stand = stand;
            c.thrown = thrown;
            c.state = state;
            c.len = len;
            return c;
        }
    }

    @FunctionalInterface
    public interface Run {
        void run(Pose p, double t, Ctx c);
    }

    public final String id;
    public final float len;
    public final boolean loop;
    /** Ground speed in 1/16 blocks per second for walks (so the feet can keep pace), else 0. */
    public final float move;
    public final Run run;
    /** How a held item is carried: "front", "hand", "both", "tuck" or null; shown from {@code holdFrom} to {@code holdUntil}. */
    public String hold;
    public float holdFrom, holdUntil = 1e9F;
    /** Overrides of the hold mode and window per segment, for sequences: {from, until, mode}. */
    public final List<Object[]> holds = new ArrayList<>();
    public final List<Seg> segs = new ArrayList<>();

    public record Seg(Clip clip, float t0, float t1, Ctx opts) { }

    public Clip(String id, float len, boolean loop, float move, Run run) {
        this.id = id;
        this.len = len;
        this.loop = loop;
        this.move = move;
        this.run = run;
    }

    public Clip hold(String mode) {
        this.hold = mode;
        return this;
    }

    public Clip hold(String mode, float from, float until) {
        this.hold = mode;
        this.holdFrom = from;
        this.holdUntil = until;
        return this;
    }

    /** Which hold mode applies at time t, or null. */
    public String holdAt(double t) {
        if (!segs.isEmpty()) {
            for (Seg s : segs) if (t < s.t1) return s.clip.holdAt(t - s.t0);
            return null;
        }
        return hold != null && t >= holdFrom && t < holdUntil ? hold : null;
    }

    /** A sequence of clips played back to back. */
    public static Clip seq(String id, Seg... parts) {
        float end = parts[parts.length - 1].t1;
        Clip c = new Clip(id, end, false, 0, null);
        c.segs.addAll(List.of(parts));
        return c;
    }

    public static Seg seg(Clip clip, float t0, Ctx opts) {
        return new Seg(clip, t0, t0 + clip.len, opts);
    }

    /** Evaluates the clip at t (looping clips wrap). */
    public void eval(Pose p, double t, Ctx c) {
        if (segs.isEmpty()) {
            run.run(p, loop && len > 0 ? t % len : t, c);
            return;
        }
        for (Seg s : segs) {
            if (t < s.t1 || s == segs.get(segs.size() - 1)) {
                Ctx o = s.opts == null ? c.copy() : s.opts.copy();
                o.item = c.item;
                o.state = c.state;
                o.len = s.t1 - s.t0;
                s.clip.run.run(p, t - s.t0, o);
                return;
            }
        }
    }
}
