package com.scottcaldwell.industriousgolems.client.anim;

import static com.scottcaldwell.industriousgolems.client.anim.Pose.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The hay golem's clips, ported from the animation workshop (the farmer: tall, stiff, on stilts, a little wrong). Every clip is a
 * list of poses with a time each; the golem holds a pose, then snaps to the next with no easing in, each part a beat after the one
 * before it (legs, torso, right arm, left arm, head).
 */
public final class HayAnims {
    private HayAnims() { }

    public static final Map<String, Clip> CLIPS = new LinkedHashMap<>();

    private static final double HB = 7, HAL = 16, HHIP = 18, HL = 18;
    private static final String[] CHN = {"bx", "by", "bz", "hx", "hy", "hz", "rx", "rz", "lx", "lz", "rl", "ll", "rlz", "llz", "dy", "gz", "wz", "wy", "rk", "lk", "la"};
    private static final Map<String, Integer> IDX = new HashMap<>();
    private static final int[] GD = new int[CHN.length];
    private static final double SNAP = 0.07, STAG = 0.1, ARM_D = 2 * STAG;

    static {
        String[][] groups = {{"gz", "dy", "rl", "ll", "rlz", "llz"}, {"bx", "by", "bz"}, {"rx", "rz", "rk", "wz", "wy", "la"}, {"lx", "lz", "lk"}, {"hx", "hy", "hz"}};
        for (int i = 0; i < CHN.length; i++) IDX.put(CHN[i], i);
        for (int g = 0; g < groups.length; g++) for (String c : groups[g]) GD[IDX.get(c)] = g;
    }

    private record Fr(double t, double[] v, double s) { }

    /** A pose, as channel names and values. */
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
        return new Object[]{t, pose, SNAP};
    }

    private static Object[] e(double t, Map<String, Double> pose, double snap) {
        return new Object[]{t, pose, snap};
    }

    /** Each pose keeps the channels it does not mention from the one before. */
    @SuppressWarnings("unchecked")
    private static Fr[] frm(Map<String, Double> init, Object[]... list) {
        Map<String, Double> cur = new HashMap<>(init);
        Fr[] out = new Fr[list.length];
        for (int i = 0; i < list.length; i++) {
            cur.putAll((Map<String, Double>) list[i][1]);
            double[] v = new double[CHN.length];
            for (int c = 0; c < CHN.length; c++) v[c] = cur.getOrDefault(CHN[c], 0.0);
            out[i] = new Fr(((Number) list[i][0]).doubleValue(), v, ((Number) list[i][2]).doubleValue());
        }
        return out;
    }

    private static double[] stepAt(Fr[] fr, double t, double len) {
        int n = fr.length;
        double[] v = new double[CHN.length];
        for (int c = 0; c < CHN.length; c++) {
            double tt = t - GD[c] * STAG;
            if (tt < 0) tt += len;
            int i = 0;
            for (int j = 0; j < n; j++) if (tt >= fr[j].t) i = j;
            Fr f = fr[i], pr = fr[(i + n - 1) % n];
            double k = Math.min(1, Math.max(0, (tt - f.t) / f.s)), ee = 1 - Math.pow(1 - k, 3);
            v[c] = pr.v[c] + (f.v[c] - pr.v[c]) * ee;
        }
        return v;
    }

    private static double sr(double n) {
        double x = Math.sin(n * 127.1 + 311.7) * 43758.5453;
        return (x - Math.floor(x)) * 2 - 1;
    }

    private static double twitch(double t, double seed, double rate) {
        return sr(Math.floor(t * rate) + seed * 17);
    }

    /** Two-link reach from the hip: the torso (7) and the arm (16). f is forward and u up from the hip. Returns {torso fold, arm angle from hanging}. */
    private static double[] reach2(double f, double u) {
        double d = Math.sqrt(f * f + u * u), dm = Math.min(22.6, Math.max(9.4, d)), al = Math.atan2(f, u), s = dm / (d == 0 ? 1 : d);
        f *= s;
        u *= s;
        d = dm;
        double cg = (HB * HB + d * d - HAL * HAL) / (2 * HB * d), ga = Math.acos(Math.max(-1, Math.min(1, cg))), th = al - ga;
        double sf = HB * Math.sin(th), su = HB * Math.cos(th), beta = Math.atan2(f - sf, u - su);
        return new double[]{th, Math.PI - beta};
    }

    private static double ch(double[] v, String c) {
        return v[IDX.get(c)];
    }

    /** Applies a posed vector to the golem; P.offZ is where it stands. */
    private static void applyV(Pose P, double[] v) {
        double dy = ch(v, "dy"), gz = ch(v, "gz"), rk = ch(v, "rk"), lk = ch(v, "lk"), wz = ch(v, "wz"), wy = ch(v, "wy");
        P.offY += dy;
        P.offZ += gz;
        double kr = Math.max(rk, lk), th = 0, ph = 0;
        if (kr > 0.001) {
            double[] r = reach2(P.offZ - wz, wy - (HHIP + P.offY));
            th = r[0];
            ph = r[1];
        }
        P.body.rx += ch(v, "bx") + th * kr;
        P.body.ry += ch(v, "by");
        P.body.rz += ch(v, "bz");
        P.head.rx += ch(v, "hx") - 0.5 * th * kr;
        P.head.ry += ch(v, "hy");
        P.head.rz += ch(v, "hz");
        P.rarm.rx += ch(v, "rx") + rk * (-ph - th);
        P.larm.rx += ch(v, "lx") + lk * (-ph - th);
        P.rarm.rz += ch(v, "rz");
        P.larm.rz -= ch(v, "lz");
        P.rleg.rx += ch(v, "rl");
        P.lleg.rx += ch(v, "ll");
        P.rleg.rz += ch(v, "rlz");
        P.lleg.rz -= ch(v, "llz");
    }

    private interface Extra {
        void run(Pose P, double t, double[] v);
    }

    private static Clip hat(String id, double len, boolean loop, double move, Fr[] fr, Extra extra) {
        Clip c = new Clip(id, (float) len, loop, (float) move, (P, t, ctx) -> {
            double[] v = stepAt(fr, t, len);
            applyV(P, v);
            if (extra != null) extra.run(P, t, v);
        });
        CLIPS.put(id, c);
        return c;
    }

    private static Map<String, Double> carryPose(double k) {
        return p("rx", -1.35 * k, "lx", -1.35 * k, "rz", -0.22 * k, "lz", -0.22 * k);
    }

    // ---- moving about: a lurch ----
    private static final double STEP_A = 0.34, STEP_S = 2 * HL * Math.sin(STEP_A), STEP_D = 0.1;
    private static final double[] STEP_T = {0.4, 0.85};

    private static Fr[] walkFrames(boolean carry) {
        double arm = carry ? 0 : 0.5;
        Map<String, Double> init = carry ? mix(carryPose(1), p("hx", 0.12)) : p("rx", arm, "lx", -arm);
        java.util.function.BiFunction<Boolean, Map<String, Double>, Map<String, Double>> pose = (rf, o) ->
                mix(p("rl", rf ? -STEP_A : STEP_A, "ll", rf ? STEP_A : -STEP_A, "dy", -0.7, "bx", 0.05), o);
        java.util.function.Function<Boolean, Map<String, Double>> armR = rf -> carry ? p() : p("rx", rf ? arm : -arm, "lx", rf ? -arm : arm);
        return frm(init,
                e(0.0, pose.apply(true, mix(armR.apply(true), p("hz", -0.1)))),
                e(STEP_T[0], pose.apply(false, p("bx", 0.22)), STEP_D),
                e(STEP_T[0] + 0.12, pose.apply(false, mix(armR.apply(false), p("bx", 0.05, "hz", 0.1))), 0.06),
                e(STEP_T[1], pose.apply(true, p("bx", 0.22)), STEP_D),
                e(STEP_T[1] + 0.12, pose.apply(true, mix(armR.apply(true), p("bx", 0.05, "hz", -0.1))), 0.06),
                e(1.15, p("hy", 0.5, "hz", 0.05), 0.05),
                e(1.5, p("hy", 0, "hz", -0.1), 0.05));
    }

    private static void walkLift(Pose P, double t, double[] v) {
        for (double st : STEP_T) {
            double k = (t - st) / STEP_D;
            if (k > 0 && k < 1) P.offY += 1.3 * Math.sin(Math.PI * k);
        }
    }

    // ---- the chest: low, it never has to climb; it stands back and works the lid and contents with straight arms ----
    private static final double HCZ = -24, HINGE_Z = HCZ - 7, HINGE_Y = 10;

    private static double[] lidGrip(double a) {
        double k = sstep(0.6, 1.4, a), r = 14 - 5 * k, nn = 2.5 - 4.1 * k, d = 1.7 * (1 - k), c = Math.cos(a), s = Math.sin(a);
        return new double[]{HINGE_Z + (r + d) * c - nn * s, HINGE_Y + (r + d) * s + nn * c};
    }

    private static Map<String, Double> gp(double a, Map<String, Double> o) {
        double[] g = lidGrip(a);
        return mix(p("wz", g[0], "wy", g[1], "la", a), o);
    }

    private static Map<String, Double> gp(double a) {
        return gp(a, p());
    }

    private static final Map<String, Double> LEGS_A = p("rl", -0.2, "ll", 0.2, "dy", -0.2), LEGS_B = p("rl", 0.2, "ll", -0.2, "dy", -0.2), LEGS_0 = p("rl", 0, "ll", 0, "dy", 0);

    // ---- the farm: the crop block is 12 pixels ahead and the dropped item lies 9 ahead ----
    private static final double CROP_Z = -12, DROP_Z = -9;
    public static final double HARV_BREAK = 2.55;

    static {
        Fr[] walk = walkFrames(false), walkCarry = walkFrames(true);
        double move = 2 * STEP_S / 1.7;
        hat("shamble", 1.7, true, move, walk, HayAnims::walkLift);
        hat("shamble-carry", 1.7, true, move, walkCarry, HayAnims::walkLift).hold("hand");

        hat("idle-wait", 10.0, true, 0, frm(p(),
                e(0.0, p("hy", 0, "rz", 1.45, "lz", 1.45)),
                e(1.2, p("hy", 0.55, "hz", 0.05)), e(2.1, p("hy", -0.1, "hz", 0.16)),
                e(3.0, p("bx", 0.08, "hx", 0.1, "rz", 1.38, "lz", 1.38), 0.09), e(3.7, p("bx", 0.17, "hx", 0.2, "rz", 1.3, "lz", 1.3), 0.09),
                e(4.4, p("bx", 0.26, "hx", 0.3, "hz", 0.05, "rz", 1.2, "lz", 1.2), 0.09),
                e(5.5, p("bx", -0.04, "hx", -0.18, "hz", 0, "hy", 0, "rz", 1.5, "lz", 1.5), 0.05), e(5.9, p("bx", 0, "hx", 0, "rz", 1.45, "lz", 1.45), 0.05),
                e(6.5, p("rz", 1.0), 0.05), e(6.65, p("rz", 1.45), 0.05),
                e(7.3, p("hy", -0.65), 0.05), e(8.1, p("hy", 0.65), 0.05), e(8.9, p("hy", 0, "hz", 0), 0.05)),
                (P, t, v) -> {
                    P.head.rz += 0.025 * twitch(t, 3, 7);
                    P.larm.rx += 0.03 * twitch(t, 9, 5);
                });

        // ---- offerings ----
        hat("await-offering", 5.0, true, 0, frm(p(),
                e(0.0, mix(carryPose(1), p("hz", 0.1, "hx", 0.05))),
                e(0.9, p("hz", -0.18, "hy", 0.15)),
                e(1.7, p("rx", -1.55, "lx", -1.55, "bx", 0.14, "hz", 0), 0.05), e(2.0, p("rx", -1.35, "lx", -1.35, "bx", 0), 0.05),
                e(2.7, p("rx", -1.55, "lx", -1.55, "bx", 0.14, "hz", 0.25), 0.05), e(3.0, p("rx", -1.35, "lx", -1.35, "bx", 0), 0.05),
                e(3.7, p("hy", -0.55, "hz", 0.05)), e(4.4, p("hy", 0.1, "hz", 0.1))),
                (P, t, v) -> P.head.rz += 0.02 * twitch(t, 5, 6)).hold("hand");
        hat("receive-offering", 5.0, false, 0, frm(p(),
                e(0.0, mix(carryPose(0.85), p("hx", -0.1))),
                e(0.5, p("rx", -1.15, "lx", -1.15), 0.05),
                e(1.0, p("hx", 0.45), 0.05),
                e(1.8, p("rx", -2.0, "lx", -2.0, "bx", -0.06, "hx", 0.3, "hz", 0.28), 0.09),
                e(2.6, p("hz", 0, "hx", 0.5), 0.05), e(2.85, p("hx", 0), 0.05), e(3.1, p("hx", 0.5), 0.05), e(3.35, p("hx", 0), 0.05),
                e(3.7, p("dy", 1.6, "rx", -1.0, "lx", -1.0, "bx", 0.06), 0.05), e(3.85, p("dy", 0, "bx", 0), 0.05),
                e(4.3, p("rx", 0, "lx", 0, "rz", 0, "lz", 0, "hx", -0.1), 0.05), e(4.55, carryPose(0.85), 0.05)),
                (P, t, v) -> P.head.rz += 0.015 * twitch(t, 2, 6)).hold("hand", (float) (0.5 + ARM_D), (float) (4.3 + ARM_D));

        // ---- breaking a crop ----
        hat("harvest", 4.4, false, 0, frm(p(),
                e(0.0, p("hx", 0.15)),
                e(0.4, p("bx", 0.3, "rx", -0.45, "lx", -0.45), 0.09),
                e(0.9, p("rk", 1, "lk", 1, "bx", 0, "rx", 0, "lx", 0, "wz", CROP_Z + 1, "wy", 9), 0.08),
                e(1.4, p("wy", 8.2), 0.04),
                e(1.8, p("wy", 14, "wz", CROP_Z + 3), 0.06), e(2.1, p("wy", 9, "wz", CROP_Z + 1), 0.05),
                e(2.5, p("wy", 22, "wz", CROP_Z + 6, "bx", -0.05), 0.05),
                e(HARV_BREAK, p("rk", 0, "lk", 0, "wy", 0, "rx", -2.25, "lx", -2.0, "bx", -0.22, "hx", -0.3, "dy", 0.7), 0.04),
                e(2.75, p("dy", 0), 0.05),
                e(3.3, p("hx", 0.5, "hy", 0.4, "rx", -1.0, "lx", -0.8, "bx", 0), 0.06),
                e(3.9, p("hx", 0.15, "hy", 0, "rx", 0, "lx", 0), 0.06)),
                (P, t, v) -> P.head.rz += 0.03 * twitch(t, 6, 8) * win(t, 2.8, 3.0, 3.6, 3.8));

        // ---- picking up ----
        hat("pick-up", 4.2, false, 0, frm(p(),
                e(0.0, p("hx", 0.2)),
                e(0.5, p("bx", 0.4, "rx", -0.3, "lx", -0.3, "hx", 0.1), 0.09),
                e(1.0, p("rk", 1, "lk", 1, "bx", 0, "rx", 0, "lx", 0, "wz", DROP_Z, "wy", 8), 0.08),
                e(1.3, p("wy", 4.8), 0.05),
                e(2.1, p("wy", 9, "wz", DROP_Z + 2), 0.07), e(2.5, p("wy", 17, "wz", DROP_Z + 4), 0.07),
                e(3.0, mix(p("rk", 0, "lk", 0, "wy", 0, "hx", 0.4), carryPose(1)), 0.07), e(3.5, p("hx", 0), 0.05)),
                null).hold("hand", (float) (1.75 + ARM_D), 1e9F);

        // ---- replanting ----
        hat("replant", 5.8, false, 0, frm(p(),
                e(0.0, carryPose(1)),
                e(0.6, p("bx", 0.35, "rx", -0.5, "lx", -0.5, "rz", 0, "lz", 0, "hx", 0.2), 0.09),
                e(1.1, p("rk", 1, "lk", 1, "bx", 0, "rx", 0, "lx", 0, "wz", CROP_Z + 1, "wy", 4), 0.08),
                e(1.5, p("wy", 1), 0.04), e(1.75, p("wy", 4.5), 0.04), e(2.0, p("wy", 1), 0.04), e(2.25, p("wy", 4.5), 0.04),
                e(3.0, p("wy", 1.4), 0.04), e(3.25, p("wy", 4.5), 0.04), e(3.55, p("wy", 1.4), 0.04), e(3.8, p("wy", 4.5), 0.04),
                e(4.3, p("rk", 0, "lk", 0, "wy", 0, "bx", 0.3, "rx", -0.5, "lx", -0.5), 0.08),
                e(4.75, p("bx", 0, "rx", 0, "lx", 0, "hx", 0.45, "hy", 0.25), 0.06),
                e(5.2, p("hx", 0, "hy", 0, "hz", 0.12), 0.05), e(5.45, carryPose(1), 0.05)),
                null).hold("hand", 0, (float) (2.7 + ARM_D));

        // ---- the chest ----
        Fr[] openFr = frm(p(),
                e(0.0, p("gz", -6, "hx", 0.2, "la", 0)),
                e(0.45, mix(gp(0), p("rk", 1, "lk", 1)), 0.08),
                e(0.95, gp(0.5), 0.1),
                e(1.25, gp(0.42), 0.04), e(1.5, gp(0.52), 0.04), e(1.7, gp(0.45), 0.04),
                e(2.05, mix(LEGS_A, p("gz", -9.5)), 0.1), e(2.25, LEGS_0, 0.06),
                e(2.5, gp(1.3), 0.06),
                e(2.95, mix(LEGS_B, p("gz", -12.5)), 0.1), e(3.15, mix(LEGS_0, gp(1.95)), 0.06), e(3.3, gp(2.08), 0.04), e(3.45, gp(1.95), 0.05),
                e(3.8, p("rk", 0, "lk", 0, "wy", 0, "rx", -2.0, "lx", -1.8, "rz", 0.5, "lz", 0.5, "bx", -0.12), 0.05),
                e(4.3, p("rx", 0, "lx", 0, "rz", 0, "lz", 0, "bx", 0, "hx", 0.5), 0.07));
        Clip open = hat("open-lid", 5.0, false, 0, openFr, (P, t, v) -> P.head.rz += 0.03 * twitch(t, 8, 9) * win(t, 1.2, 1.25, 1.7, 1.75));

        Fr[] closeFr = frm(p(),
                e(0.0, p("gz", -12.5, "la", 1.95, "hx", 0.3)),
                e(0.45, mix(gp(1.95), p("rk", 1, "lk", 1)), 0.08),
                e(0.95, gp(1.35), 0.08), e(1.3, gp(1.35, p("hz", 0.15)), 0.04), e(1.5, gp(1.38, p("hz", -0.12)), 0.04),
                e(1.8, gp(0.7, p("hz", 0)), 0.08), e(2.1, gp(0.7), 0.05),
                e(2.5, gp(0), 0.045),
                e(2.58, p("rk", 0, "lk", 0, "wy", 0, "rx", -2.3, "lx", -2.1, "bx", -0.15, "la", 0.2), 0.04), e(2.72, p("la", 0), 0.05),
                e(3.2, p("rx", 0, "lx", 0, "bx", 0, "hx", 0.5), 0.07));
        Clip close = hat("close-lid", 4.0, false, 0, closeFr, (P, t, v) -> P.head.rz += 0.03 * twitch(t, 11, 9) * win(t, 1.25, 1.3, 1.7, 1.75));

        Object[][] rum = new Object[8][];
        for (int i = 0; i < 8; i++) rum[i] = e(0.95 + i * 0.16, p("wz", HCZ + 2.6 * sr(i + 3), "wy", 7.5 + 1.8 * sr(i + 31), "hz", 0.18 * sr(i + 7), "by", 0.07 * sr(i + 13)), 0.04);
        double thRel = 4.05;
        java.util.List<Object[]> rl = new java.util.ArrayList<>();
        rl.add(e(0.0, p("gz", -12, "la", 1.95, "hx", 0.3)));
        rl.add(e(0.5, p("rk", 1, "lk", 1, "wz", HCZ, "wy", 8), 0.09));
        for (Object[] r : rum) rl.add(r);
        rl.add(e(2.35, p("wz", HCZ, "wy", 6, "hz", 0, "by", 0), 0.05));
        rl.add(e(2.9, p("lk", 0, "lx", -0.25, "wz", HCZ + 1, "wy", 26, "hx", -0.1), 0.1));
        rl.add(e(3.45, p("hx", -0.3), 0.05));
        rl.add(e(3.65, p("rk", 0, "wy", 0, "rx", 0.9, "rz", 0.9, "by", -0.9, "hx", 0, "hy", 0.5), 0.07));
        rl.add(e(thRel, p("rx", -1.9, "rz", 1.0, "by", 0.6, "bx", 0.2, "hy", -0.3), 0.045));
        rl.add(e(4.7, p("hy", 0.8, "hx", 0.3), 0.05));
        rl.add(e(5.4, p("rx", 0, "rz", 0, "lx", 0, "by", 0, "bx", 0, "hy", 0, "hx", 0.3), 0.07));
        Clip rummage = hat("rummage-throw", 6.0, false, 0, frm(p(), rl.toArray(new Object[0][])),
                (P, t, v) -> P.head.rz += 0.025 * twitch(t, 12, 8) * win(t, 2.4, 2.5, 2.8, 2.9)).hold("hand", (float) (2.9 + ARM_D), (float) (thRel + ARM_D));

        double putRel = 2.0;
        Clip put = hat("put-in-chest", 4.2, false, 0, frm(p(),
                e(0.0, mix(carryPose(1), p("gz", -12.5, "la", 1.95, "hx", 0.3))),
                e(0.45, mix(LEGS_A, p("gz", -10, "hx", 0.15)), 0.1), e(0.65, LEGS_0, 0.06),
                e(1.1, p("rk", 1, "lk", 1, "rx", 0, "lx", 0, "rz", 0, "lz", 0, "wz", HCZ, "wy", 17.5), 0.09),
                e(1.5, p("hx", 0.45), 0.05),
                e(putRel, p("rz", 0.6, "lz", 0.6, "wy", 18), 0.05),
                e(2.6, p("hx", 0.6, "rz", 0.95, "lz", 0.95), 0.07),
                e(3.2, p("rk", 0, "lk", 0, "wy", 0, "rx", 0, "lx", 0, "rz", 0, "lz", 0, "hx", 0), 0.07),
                e(3.6, p("hx", 0.4), 0.05)),
                (P, t, v) -> P.head.rz += 0.02 * twitch(t, 14, 7) * win(t, 1.5, 1.55, 1.95, 2.0)).hold("hand", 0, (float) (putRel + ARM_D));

        hat("chest-full", 7.0, true, 0, frm(p(),
                e(0.0, mix(carryPose(1), p("gz", -10, "la", 1.95, "hx", 0.3))),
                e(0.5, p("rk", 1, "lk", 1, "rx", 0, "lx", 0, "rz", 0, "lz", 0, "wz", HCZ, "wy", 17), 0.09),
                e(1.0, p("wy", 13.5), 0.05), e(1.2, p("wy", 17, "bx", -0.1), 0.04), e(1.35, p("bx", 0), 0.05),
                e(1.8, p("wy", 13), 0.05), e(2.0, p("wy", 18, "bx", -0.12), 0.04), e(2.15, p("bx", 0), 0.05),
                e(2.6, p("wy", 12.5), 0.05), e(2.95, p("wy", 20, "bx", -0.22, "hx", -0.1), 0.04),
                e(3.3, mix(p("rk", 0, "lk", 0, "wy", 0, "bx", 0, "hx", 0.45), carryPose(1)), 0.07),
                e(3.9, p("hy", -0.8, "hx", 0.3), 0.05), e(4.25, p("hy", 0, "hx", 0.45), 0.05),
                e(4.6, p("hy", 0.65), 0.05), e(4.8, p("hy", -0.65), 0.05), e(5.0, p("hy", 0.5), 0.05), e(5.2, p("hy", 0), 0.05),
                e(5.7, p("bx", 0.25, "hx", 0.55, "rx", -0.2, "lx", -0.2, "rz", 0.1, "lz", 0.1), 0.12),
                e(6.6, mix(p("bx", 0, "hx", 0.3), carryPose(1)), 0.07)),
                (P, t, v) -> P.head.rz += 0.02 * twitch(t, 16, 7)).hold("hand");

        CLIPS.put("stow-item", Clip.seq("stow-item", Clip.seg(open, 0, null), Clip.seg(put, 5.0F, null), Clip.seg(close, 9.2F, null)));
        CLIPS.put("fetch-item", Clip.seq("fetch-item", Clip.seg(open, 0, null), Clip.seg(rummage, 5.0F, null), Clip.seg(close, 11.0F, null)));
    }
}
