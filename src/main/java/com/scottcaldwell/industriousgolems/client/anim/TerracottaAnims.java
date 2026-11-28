package com.scottcaldwell.industriousgolems.client.anim;

import static com.scottcaldwell.industriousgolems.client.anim.Pose.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The terracotta golem's clips, ported from the animation workshop (the herder: built like a stack of fired-clay rings, the feet,
 * the hips, the chest and the head each turning on their own, like a totem). A pose change does not happen at once: the rings turn
 * one after another, each easing into place with a tiny overshoot, like a ring clicking home.
 */
public final class TerracottaAnims {
    private TerracottaAnims() { }

    public static final Map<String, Clip> CLIPS = new LinkedHashMap<>();

    private static final double T2 = Math.PI * 2, TSTG = 0.11, TBASE = 3, TTORSO = 18, TARMLEN = 17, TTILT = 0.36;
    private static final String[] TCH = {"fy", "py", "by", "hy", "bx", "bz", "hx", "hz", "rx", "rz", "lx", "lz", "ry", "ly", "rl", "ll", "dy", "gz", "gx", "wx", "wz", "wy", "rk", "lk", "la", "gk"};
    private static final Map<String, Integer> IDX = new HashMap<>();
    private static final double[] OV = new double[TCH.length];
    private static final double TSH = TBASE + TTORSO, TREACH = 16.5;

    private static final Map<String, Integer> UP = new HashMap<>(), DOWN = new HashMap<>();

    static {
        for (int i = 0; i < TCH.length; i++) IDX.put(TCH[i], i);
        for (String c : new String[]{"py"}) UP.put(c, 1);
        for (String c : new String[]{"by", "bx", "bz"}) UP.put(c, 2);
        for (String c : new String[]{"rx", "rz", "lx", "lz", "ry", "ly", "wx", "wz", "wy", "rk", "lk", "la", "gk"}) UP.put(c, 3);
        for (String c : new String[]{"hy", "hx", "hz"}) UP.put(c, 4);
        for (String c : new String[]{"rx", "rz", "lx", "lz", "ry", "ly", "wx", "wz", "wy", "rk", "lk", "la", "gk"}) DOWN.put(c, 1);
        for (String c : new String[]{"by", "bx", "bz"}) DOWN.put(c, 2);
        DOWN.put("py", 3);
        DOWN.put("fy", 4);
        DOWN.put("rl", 4);
        DOWN.put("ll", 4);
        Object[][] ov = {{"fy", 0.3}, {"py", 0.4}, {"by", 0.55}, {"hy", 1.0}, {"bx", 0.5}, {"bz", 0.6}, {"hx", 1.0}, {"hz", 1.0}, {"rx", 1.1}, {"rz", 1.1},
                {"lx", 1.1}, {"lz", 1.1}, {"ry", 1.0}, {"ly", 1.0}};
        for (Object[] o : ov) OV[IDX.get((String) o[0])] = (Double) o[1];
    }

    /** A pose of the rings: when, the values, how long it takes to move there, and the delay of each channel (feet first, head first, or all together). */
    private record Fr(double t, double[] v, double s, double[] d) { }

    private static Map<String, Double> p(Object... kv) {
        Map<String, Double> m = new HashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], ((Number) kv[i + 1]).doubleValue());
        return m;
    }

    private static Map<String, Double> mix(Map<String, Double> a, Map<String, Double> b) {
        Map<String, Double> m = new HashMap<>(a);
        m.putAll(b);
        return m;
    }

    private static Object[] e(double t, Map<String, Double> pose) {
        return new Object[]{t, pose, 0.45, "up"};
    }

    private static Object[] e(double t, Map<String, Double> pose, double secs) {
        return new Object[]{t, pose, secs, "up"};
    }

    private static Object[] e(double t, Map<String, Double> pose, double secs, String order) {
        return new Object[]{t, pose, secs, order};
    }

    @SuppressWarnings("unchecked")
    private static Fr[] tfrm(Map<String, Double> init, Object[]... list) {
        Map<String, Double> cur = new HashMap<>(init);
        Fr[] out = new Fr[list.length];
        for (int i = 0; i < list.length; i++) {
            cur.putAll((Map<String, Double>) list[i][1]);
            String ord = (String) list[i][3];
            double[] v = new double[TCH.length], d = new double[TCH.length];
            for (int c = 0; c < TCH.length; c++) {
                v[c] = cur.getOrDefault(TCH[c], 0.0);
                Integer rank = "up".equals(ord) ? UP.get(TCH[c]) : ("down".equals(ord) ? DOWN.get(TCH[c]) : null);
                d[c] = (rank == null ? 0 : rank) * TSTG;
            }
            out[i] = new Fr(((Number) list[i][0]).doubleValue(), v, ((Number) list[i][2]).doubleValue(), d);
        }
        return out;
    }

    /** The ringing after a pose arrives: it swings past, comes back and settles. */
    private static double twob(Fr f, Fr pr, int c, double tt) {
        double d = f.v[c] - pr.v[c], u = tt - f.t - f.d[c] - f.s;
        if (OV[c] == 0 || f.s < 0.01 || u <= 0 || u >= 1.0) return 0;
        return Math.signum(d) * OV[c] * Math.min(0.22, 0.2 * Math.abs(d)) * Math.exp(-5.5 * u) * Math.sin(15 * u);
    }

    private static double[] tsample(Fr[] fr, double t, boolean smooth) {
        int n = fr.length;
        double[] v = new double[TCH.length];
        for (int c = 0; c < TCH.length; c++) {
            int j = -1;
            for (int q = 0; q < n; q++) if (fr[q].t + fr[q].d[c] <= t) j = q;
            if (j <= 0) {
                v[c] = fr[0].v[c];
                continue;
            }
            Fr f = fr[j], pr = fr[j - 1];
            double k = Math.min(1, Math.max(0, (t - f.t - f.d[c]) / f.s)), val = pr.v[c] + (f.v[c] - pr.v[c]) * sstep(0, 1, k);
            if (!smooth) {
                val += twob(f, pr, c, t);
                if (k < 1 && j > 1) val += twob(pr, fr[j - 2], c, t) * (1 - k);
            }
            v[c] = val;
        }
        return v;
    }

    private static double ch(double[] v, String c) {
        return v[IDX.get(c)];
    }

    private static double[] tGripPt(double a, double hingeZ) {
        double k = sstep(0.6, 1.4, a), r = 14 - 5 * k, nn = 2.5 - 4.1 * k, d = 1.7 * (1 - k), c = Math.cos(a), s = Math.sin(a);
        return new double[]{hingeZ + (r + d) * c - nn * s, 10 + (r + d) * s + nn * c};
    }

    private static final double TCZ = -15.5, THINGE = TCZ - 7;

    private static double[] tGrip(double a) {
        return tGripPt(a, THINGE);
    }

    private static double tGripGz(double a) {
        double[] p = tGrip(a);
        return p[0] + Math.sqrt(Math.max(0, TREACH * TREACH - (TSH - p[1]) * (TSH - p[1])));
    }

    /** The golem never leans. A hand is sent to a point by swinging the arm straight at it; the golem moves its feet to bring the point into reach. */
    private static void tapply(Pose P, double[] v) {
        double gz = ch(v, "gz"), wz = ch(v, "wz"), wy = ch(v, "wy"), gk = ch(v, "gk");
        if (gk > 0.001) {
            double[] gr = tGrip(ch(v, "la"));
            double gzg = gr[0] + Math.sqrt(Math.max(0, TREACH * TREACH - (TSH - gr[1]) * (TSH - gr[1])));
            gz = ch(v, "gz") * (1 - gk) + gzg * gk;
            wz = ch(v, "wz") * (1 - gk) + gr[0] * gk;
            wy = ch(v, "wy") * (1 - gk) + gr[1] * gk;
        }
        P.offY += ch(v, "dy");
        P.offZ += gz;
        P.offX += ch(v, "gx");
        double by = ch(v, "by");
        double f = (ch(v, "wx") - P.offX) * Math.sin(by) - (wz - P.offZ) * Math.cos(by), th = Math.atan2(f, TSH + P.offY - wy);
        P.rleg.ry += ch(v, "fy");
        P.lleg.ry += ch(v, "fy");
        P.hips.ry += ch(v, "py");
        P.body.ry += by;
        P.body.rx += ch(v, "bx");
        P.body.rz += ch(v, "bz");
        P.head.ry += ch(v, "hy") - by;
        P.head.rx += ch(v, "hx");
        P.head.rz += ch(v, "hz");
        P.rarm.rx += ch(v, "rx") - ch(v, "rk") * th;
        P.larm.rx += ch(v, "lx") - ch(v, "lk") * th;
        P.rarm.ry += ch(v, "ry");
        P.larm.ry += ch(v, "ly");
        P.rarm.rz += ch(v, "rz");
        P.larm.rz -= ch(v, "lz");
        P.rleg.rx += ch(v, "rl");
        P.lleg.rx += ch(v, "ll");
    }

    private static Map<String, Double> ringAll(double a) {
        return p("fy", a, "py", a, "by", a, "hy", a);
    }

    private static final Map<String, Double> TZERO = p();
    private static final Map<String, Double> T_CARRY = p("rx", -0.95, "lx", -0.95, "rz", -0.52, "lz", -0.52);

    private interface Extra {
        void run(Pose P, double t, double[] v);
    }

    private static Clip tat(String id, double len, boolean loop, double move, Fr[] fr, Extra extra) {
        Clip c = new Clip(id, (float) len, loop, (float) move, (P, t, ctx) -> {
            double[] v = tsample(fr, t, false);
            tapply(P, v);
            if (extra != null) extra.run(P, t, v);
        });
        CLIPS.put(id, c);
        return c;
    }

    // ---- moving about: a flowing glide ----
    private static final double TWALK = 1.4;

    private static void tWalk(Pose P, double t, boolean carry) {
        double w0 = T2 * t / TWALK, w = w0 + 0.45 * Math.sin(w0);
        double[] v = new double[TCH.length];
        double side = Math.sin(w0 / 2), A = (carry ? 0.1 : 0.17) * (1 + 0.05 * side), land = Math.max(0, Math.cos(2 * w - 0.2));
        set(v, "rl", 0.42 * Math.sin(w));
        set(v, "ll", -0.42 * Math.sin(w));
        set(v, "fy", A * 0.8 * Math.sin(w + 0.3));
        set(v, "py", A * 1.1 * Math.sin(w - 0.2));
        set(v, "by", -A * 0.9 * Math.sin(w - 0.7));
        set(v, "hy", A * 0.6 * Math.sin(w - 1.4));
        set(v, "bz", 0.045 * Math.sin(w - 0.5));
        set(v, "hz", 0.07 * Math.sin(w - 1.3));
        set(v, "dy", 0.85 + 0.55 * Math.pow(1 - Math.abs(Math.sin(w)), 1.4));
        if (carry) {
            set(v, "rx", -0.9 + 0.04 * Math.sin(w - 1));
            set(v, "lx", -0.9 + 0.04 * Math.sin(w - 1));
            set(v, "rz", -0.5);
            set(v, "lz", -0.5);
            set(v, "hx", 0.1 + 0.05 * Math.sin(2 * w - 1.6));
        } else {
            set(v, "rx", -0.5 * Math.sin(w - 0.7));
            set(v, "lx", 0.5 * Math.sin(w - 0.7));
            set(v, "rz", 0.06 + 0.08 * Math.sin(w - 1.4));
            set(v, "lz", 0.06 - 0.08 * Math.sin(w - 1.4));
            set(v, "ry", 0.14 * Math.sin(w - 1.5));
            set(v, "ly", -0.14 * Math.sin(w - 1.5));
            set(v, "hx", 0.06 * Math.sin(2 * w - 1.4));
        }
        tapply(P, v);
        P.body.sy -= 0.035 * land;
        P.body.sx += 0.02 * land;
        P.body.sz += 0.02 * land;
        P.hips.sy -= 0.02 * land;
    }

    private static void set(double[] v, String c, double x) {
        v[IDX.get(c)] = x;
    }

    // ---- the chest: it walks up, and its straight arms swing to wherever the lid is ----
    private static final double TOPENA = 1.45;
    private static final double G_SHUT = tGripGz(0), G_OPEN = tGripGz(TOPENA);
    private static final double T_IN = -TTILT;
    private static final double T_IN_Z = G_OPEN - TREACH * Math.sin(0.69), T_IN_Y = TSH - TREACH * Math.cos(0.69);
    private static final double T_ARMD = 3 * TSTG;

    /** While it moves, the legs step in time with how fast it is travelling along z. */
    private static void tStride(Pose P, double t, Fr[] fr) {
        double[] a = tsample(fr, Math.max(0, t - 0.03), false), b = tsample(fr, t + 0.03, false);
        double za = gzOf(a), zb = gzOf(b);
        double sp = Math.min(1, Math.abs(zb - za) / 0.06 / 5), ph = (P.offZ + 100) * 0.55;
        P.rleg.rx += 0.4 * Math.sin(ph) * sp;
        P.lleg.rx -= 0.4 * Math.sin(ph) * sp;
        P.offY += 0.9 * sp;
        P.hips.ry += 0.12 * Math.cos(ph) * sp;
        P.body.ry -= 0.1 * Math.cos(ph - 0.5) * sp;
    }

    private static double gzOf(double[] v) {
        double gk = ch(v, "gk"), gz = ch(v, "gz");
        if (gk > 0.001) {
            double[] f = tGrip(ch(v, "la"));
            double z = f[0] + Math.sqrt(Math.max(0, TREACH * TREACH - (TSH - f[1]) * (TSH - f[1])));
            return z * gk + gz * (1 - gk);
        }
        return gz;
    }

    private static Clip tch2(String id, double len, Fr[] fr, Extra extra) {
        return tat(id, len, false, 0, fr, (P, t, v) -> {
            tStride(P, t, fr);
            if (extra != null) extra.run(P, t, v);
        });
    }

    public static final double T_PUT_REL = 2.7, T_TAKE_GRAB = 2.8, T_TAKE_REL = 4.5;

    // ---- the herd ----
    public static final double T_CULL_PHI = 1.35, T_CULL_LEN = 6.2, T_FEED_LEN = 9.0, T_SHEAR_LEN = 10.2, T_FEED_NX = 16, T_BITE = 4.4;

    /** Animals as the clips see them: height of the back, height of the nose. */
    public static final double[][] ANIMALS = {{18, 13}, {20, 15}, {11, 8}};
    public static final String[] ANIMAL_IDS = {"sheep", "cow", "pig"};

    private static Fr[] cullFrames(double rz) {
        return tfrm(TZERO,
                e(0.0, TZERO),
                e(0.5, p("hy", 0.4, "hx", 0.2), 0.5, "down"),
                e(1.2, p("by", -T2, "hy", -T2, "rz", rz * 0.75, "lz", rz * 0.75, "hx", 0), 0.7, "down"),
                e(2.6, p("by", 0, "hy", 0), 0.001, "all"),
                e(2.65, p("rz", 0, "lz", 0, "hx", 0.25), 0.9, "up"),
                e(3.8, p("hy", 0.95, "hx", 0.3), 0.7, "down"),
                e(5.2, p("hy", 0, "hx", 0), 0.8, "down"));
    }

    /** The arm angle that sweeps at the animal's back, and how far out to stand the animal so the hand arrives in its flank. */
    public static double[] cullGeom(double back) {
        double y = 0.78 * back, th = Math.acos(Math.min(1, (21 - y) / TARMLEN));
        return new double[]{th - TTILT, 4 + TARMLEN * Math.sin(th) + 2 * Math.cos(th) + 1.5};
    }

    private static Fr[] feedFrames(double noseY) {
        double ny = noseY - 0.5, dyv = TSH - ny, fx = Math.sqrt(Math.max(1, TREACH * TREACH - dyv * dyv)), gx = T_FEED_NX - 1.2 - fx;
        return tfrm(TZERO,
                e(0.0, p("gz", 0, "gx", gx, "rx", -0.9, "lx", -0.9, "rz", -0.5, "lz", -0.5, "hx", 0.15, "wx", T_FEED_NX - 1.2, "wz", 0, "wy", ny)),
                e(0.7, mix(ringAll(Math.PI / 2), p("hx", 0.2)), 0.9, "down"),
                e(2.4, p("rk", 1, "lk", 1, "rx", 0, "lx", 0, "rz", -0.45, "lz", -0.45, "hx", 0.3), 0.8, "up"),
                e(3.6, p("wx", T_FEED_NX - 1.8, "wy", ny - 0.6), 0.5),
                e(T_BITE, p("wx", T_FEED_NX - 0.3, "wy", ny - 1.4), 0.15),
                e(5.0, p("rk", 0, "lk", 0, "rx", -0.3, "lx", -0.3, "rz", 0.1, "lz", 0.1, "hx", -0.15, "hz", 0.12), 0.7, "up"),
                e(5.6, mix(ringAll(Math.PI / 2 - T2), p("rx", -0.3, "lx", -0.3)), 1.3, "up"),
                e(7.5, mix(ringAll(Math.PI / 2), p("hz", 0)), 0.001, "all"),
                e(7.7, mix(ringAll(0), p("rx", -0.9, "lx", -0.9, "rz", -0.5, "lz", -0.5, "hx", 0.15, "hz", 0)), 0.9, "up"));
    }

    /** When the arm swings through the animal: the first time the chest passes this heading. */
    private static double tcross(Fr[] fr, String c, double val, double t0, double t1) {
        Double last = null;
        for (double t = t0; t <= t1; t += 0.005) {
            double x = ch(tsample(fr, t, false), c) - val;
            if (last != null && (x <= 0) != (last <= 0)) return t;
            last = x;
        }
        return t1;
    }

    /** When the cull's blow lands (the arm passes the animal), for each animal. */
    public static final double[] CULL_HIT = new double[3];

    static {
        for (int i = 0; i < 3; i++) CULL_HIT[i] = tcross(cullFrames(cullGeom(ANIMALS[i][0])[0]), "by", -(T_CULL_PHI - 0.3), 1.2, 2.5);
    }

    private static final Fr[] T_SHEAR = tfrm(TZERO,
            e(0.0, TZERO),
            e(0.5, p("hy", Math.PI / 2, "hx", 0.3), 0.8, "down"),
            e(1.4, mix(ringAll(Math.PI / 2), p("hx", 0.3)), 0.8, "down"),
            e(2.4, p("rx", -1.25, "lx", -1.25, "rz", -0.45, "lz", -0.45, "by", Math.PI / 2 + 0.42, "py", Math.PI / 2 + 0.14), 0.7, "up"),
            e(3.2, p("by", Math.PI / 2 - 0.42, "py", Math.PI / 2 - 0.14), 3.0, "up"),
            e(6.6, p("rx", 0, "lx", 0, "rz", 0, "lz", 0, "by", Math.PI / 2, "py", Math.PI / 2, "hx", 0.25), 0.8, "up"),
            e(7.7, p("hy", 1.97), 0.6, "down"),
            e(8.9, mix(ringAll(0), p("hx", 0)), 0.9, "up"));

    static {
        // ---- walking ----
        add("clomp", TWALK, true, 10, (P, t, c) -> tWalk(P, t, false));
        add("clomp-carry", TWALK, true, 10, (P, t, c) -> tWalk(P, t, true)).hold("both");

        // ---- idling: it looks out over the pasture in rings, and now and then swivels all the way round ----
        Fr[] idle = tfrm(TZERO,
                e(0.0, TZERO),
                e(1.0, p("hy", 0.9), 0.8),
                e(2.7, ringAll(0.9), 0.6, "down"),
                e(4.4, p("hx", -0.3), 0.6), e(5.2, p("hx", 0), 0.5),
                e(6.0, p("hy", -0.9), 1.1),
                e(7.7, ringAll(-0.9), 0.6, "down"),
                e(9.3, p("hy", 0), 0.8), e(10.3, ringAll(0), 0.6, "down"),
                e(11.6, mix(ringAll(T2), p("rz", 0.4, "lz", 0.4)), 1.5, "up"),
                e(13.65, mix(ringAll(0), p("rz", 0, "lz", 0)), 0.001, "all"));
        tat("idle-wait", 14.0, true, 0, idle, (P, t, v) -> {
            double br = Math.sin(t * 1.4);
            P.body.sy += 0.018 * br;
            P.body.sx -= 0.008 * br;
            P.body.rx += 0.012 * br;
            P.head.rx += 0.02 * Math.sin(t * 1.4 - 0.6);
            P.head.ry += 0.05 * Math.sin(t * 0.83) + 0.03 * Math.sin(t * 2.1 + 1);
            P.head.rz += 0.03 * Math.sin(t * 0.61 + 2);
            double a = 0.03 * Math.sin(t * 1.4 - 1.2) + 0.015 * Math.sin(t * 3.1);
            P.rarm.rx += a;
            P.larm.rx += a;
            P.rarm.rz += 0.03 * Math.sin(t * 0.9);
            P.larm.rz -= 0.03 * Math.sin(t * 0.9);
        });

        // ---- shooing: both arms out low in front, sweeping apart twice ----
        Map<String, Double> in = p("rx", -0.75, "lx", -0.75, "rz", -0.4, "lz", -0.4, "hx", 0.3);
        Map<String, Double> out = p("rx", -1.2, "lx", -1.2, "rz", 0.55, "lz", 0.55, "hx", 0.15);
        tat("shoo", 1.6, false, 0, tfrm(TZERO,
                e(0.0, TZERO),
                e(0.05, in, 0.25, "all"),
                e(0.4, out, 0.18, "all"),
                e(0.7, in, 0.2, "all"),
                e(1.0, out, 0.18, "all"),
                e(1.3, mix(ringAll(0), p("rx", 0, "lx", 0, "rz", 0, "lz", 0, "hx", 0)), 0.25, "all")), null);

        // ---- offerings ----
        Fr[] await = tfrm(TZERO,
                e(0.0, mix(T_CARRY, p("hx", 0.15))),
                e(1.0, mix(ringAll(0.55), p("hx", 0.1)), 0.7, "down"),
                e(2.7, mix(ringAll(0), p("hx", 0.15)), 0.7, "up"),
                e(3.6, ringAll(-0.55), 0.7, "down"),
                e(5.2, ringAll(0), 0.7, "up"));
        tat("await-offering", 6.0, true, 0, await, (P, t, v) -> {
            P.rarm.rx += 0.025 * Math.sin(t * 3);
            P.larm.rx += 0.025 * Math.sin(t * 3);
        }).hold("both");
        Fr[] recv = tfrm(TZERO,
                e(0.0, p("rx", -0.8, "lx", -0.8, "rz", -0.1, "lz", -0.1, "hx", -0.1)),
                e(0.9, p("rx", -0.9, "lx", -0.9, "rz", -0.52, "lz", -0.52, "hx", 0.05), 0.5),
                e(1.8, p("hx", 0.4), 0.5),
                e(2.6, p("rx", -1.35, "lx", -1.35, "hx", 0.3), 0.6),
                e(3.4, mix(T_CARRY, p("hx", 0.1)), 0.6),
                e(3.9, mix(ringAll(-T2), p("rx", -1.0, "lx", -1.0)), 1.3, "up"),
                e(5.75, mix(ringAll(0), T_CARRY), 0.001, "all"));
        tat("receive-offering", 6.0, false, 0, recv, null).hold("both", (float) (0.9 + 0.33), 6.0F);

        // ---- the chest ----
        Fr[] open = tfrm(TZERO,
                e(0.0, p("gz", G_SHUT, "hx", 0.15)),
                e(0.7, p("rk", 1, "lk", 1, "gk", 1, "rz", T_IN, "lz", T_IN, "hx", 0.25), 0.9),
                e(1.7, p("la", TOPENA), 2.0),
                e(4.0, p("rk", 0, "lk", 0, "gk", 0, "gz", G_OPEN, "rx", -1.2, "lx", -1.2, "rz", 0.2, "lz", 0.2, "hx", 0.3), 0.8),
                e(5.2, p("rx", 0, "lx", 0, "rz", 0, "lz", 0, "hx", 0.3), 0.9));
        Clip openLid = tch2("open-lid", 6.5, open, null);
        Fr[] close = tfrm(TZERO,
                e(0.0, p("gz", G_OPEN, "la", TOPENA, "hx", 0.3)),
                e(0.7, p("rk", 1, "lk", 1, "gk", 1, "rz", T_IN, "lz", T_IN), 0.9),
                e(1.7, p("la", 0), 2.0),
                e(4.0, p("rk", 0, "lk", 0, "gk", 0, "gz", G_SHUT, "rx", -1.2, "lx", -1.2, "rz", 0.2, "lz", 0.2, "hx", 0), 0.8),
                e(5.2, p("rx", 0, "lx", 0, "rz", 0, "lz", 0, "hx", 0), 0.9));
        Clip closeLid = tch2("close-lid", 6.5, close, null);
        Fr[] putFr = tfrm(TZERO,
                e(0.0, p("gz", G_OPEN, "la", TOPENA, "hx", 0.3, "wx", 0, "wz", T_IN_Z, "wy", T_IN_Y)),
                e(0.8, p("rk", 1, "lk", 1, "rz", -0.5, "lz", -0.5, "hx", 0.35), 1.0),
                e(2.0, p("hx", 0.5), 0.6),
                e(T_PUT_REL, p("rz", -0.1, "lz", -0.1), 0.4),
                e(3.6, p("hx", 0.45, "rz", -0.1, "lz", -0.1), 0.6),
                e(4.6, p("rk", 0, "lk", 0, "rx", 0, "lx", 0, "rz", 0, "lz", 0, "hx", 0.3), 0.9));
        Clip put = tch2("put-in-chest", 6.0, putFr, null).hold("both", 0, (float) (T_PUT_REL + T_ARMD));
        Fr[] takeFr = tfrm(TZERO,
                e(0.0, p("gz", G_OPEN, "la", TOPENA, "hx", 0.3, "wx", 0, "wz", T_IN_Z, "wy", T_IN_Y)),
                e(0.8, p("rk", 1, "lk", 1, "rz", -0.45, "lz", -0.45, "hx", 0.45), 1.0),
                e(1.8, p("wz", T_IN_Z - 1.2, "wy", T_IN_Y + 0.3), 0.4), e(2.2, p("wz", T_IN_Z + 1.0), 0.4), e(2.5, p("wz", T_IN_Z - 0.4, "wy", T_IN_Y), 0.3),
                e(T_TAKE_GRAB, p("wy", T_IN_Y - 0.3), 0.2),
                e(3.3, p("wz", T_IN_Z + 4, "wy", 26, "hx", 0.25), 1.0),
                e(T_TAKE_REL, p("rk", 0, "lk", 0, "rx", -1.7, "lx", -1.7, "rz", 0.3, "lz", 0.3, "hx", 0.15), 0.5),
                e(5.4, p("rx", 0, "lx", 0, "rz", 0, "lz", 0, "hx", 0.3), 0.8));
        Clip take = tch2("take-from-chest", 6.5, takeFr, null).hold("both", (float) (T_TAKE_GRAB + T_ARMD), (float) (T_TAKE_REL + T_ARMD));
        CLIPS.put("stow-item", Clip.seq("stow-item", Clip.seg(openLid, 0, null), Clip.seg(put, 6.5F, null), Clip.seg(closeLid, 12.5F, null)));
        CLIPS.put("fetch-item", Clip.seq("fetch-item", Clip.seg(openLid, 0, null), Clip.seg(take, 6.5F, null), Clip.seg(closeLid, 13.0F, null)));

        // ---- the herd ----
        for (int i = 0; i < 3; i++) {
            final int a = i;
            double[] ge = cullGeom(ANIMALS[a][0]);
            Fr[] cf = cullFrames(ge[0]);
            double hit = CULL_HIT[a];
            tat("cull-" + ANIMAL_IDS[a], T_CULL_LEN, false, 0, cf, (P, t, v) -> {
                double a1 = ch(tsample(cf, t - 0.02, true), "by"), b2 = ch(tsample(cf, t + 0.02, true), "by");
                double om = ((b2 - a1) - T2 * Math.round((b2 - a1) / T2)) / 0.04, sp = Math.min(1, Math.abs(om) / 13);
                double rz = ge[0], u = t - hit;
                P.rarm.rz += 0.5 * rz * sp;
                P.larm.rz -= 0.5 * rz * sp;
                P.rarm.ry += -0.045 * om;
                P.larm.ry += -0.045 * om;
                P.body.sy += 0.05 * sp;
                P.body.sx -= 0.025 * sp;
                P.body.sz -= 0.025 * sp;
                P.head.rz += -0.12 * om / 13;
                P.body.rz += 0.05 * om / 13;
                if (u > 0 && u < 0.7) {
                    double ee = Math.exp(-7 * u);
                    P.rarm.rx += -0.45 * ee * Math.sin(u * 22);
                    P.larm.rx += -0.45 * ee * Math.sin(u * 22);
                    P.body.y += 1.1 * ee;
                    P.head.rx += 0.18 * ee;
                }
                double te = 1.2 + 0.22 + 0.7 + 0.1, ue = t - te;
                if (ue > 0 && ue < 1.6) {
                    double d2 = Math.exp(-2.6 * ue);
                    P.body.ry += 0.22 * d2 * Math.sin(ue * 9);
                    P.head.ry += -0.3 * d2 * Math.sin(ue * 9 + 0.7);
                    P.head.rz += 0.12 * d2 * Math.sin(ue * 7);
                    P.body.sy -= 0.05 * Math.exp(-6 * ue) * (1 + Math.sin(ue * 16));
                }
            });
            tat("feed-" + ANIMAL_IDS[a], T_FEED_LEN, false, 0, feedFrames(ANIMALS[a][1]), null).hold("both", 0, (float) (T_BITE + T_ARMD + 0.1));
        }
        tat("shear", T_SHEAR_LEN, false, 0, T_SHEAR, (P, t, v) -> {
            double sc = win(t, 3.2, 3.5, 6.4, 6.8), q = Math.sin(t * 24);
            P.body.ry += 0.05 * q * sc;
            P.rarm.rx += 0.05 * Math.sin(t * 24 + 1) * sc;
            P.larm.rx += 0.05 * Math.sin(t * 24 + 1) * sc;
        });
    }

    private static Clip add(String id, double len, boolean loop, double move, Clip.Run run) {
        Clip c = new Clip(id, (float) len, loop, (float) move, run);
        CLIPS.put(id, c);
        return c;
    }
}
