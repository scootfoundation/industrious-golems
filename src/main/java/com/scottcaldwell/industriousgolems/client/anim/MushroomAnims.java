package com.scottcaldwell.industriousgolems.client.anim;

import static com.scottcaldwell.industriousgolems.client.anim.Pose.*;

import com.scottcaldwell.industriousgolems.golem.Anim;

import java.util.LinkedHashMap;
import java.util.Map;

/** The mushroom golem's clips, ported from the animation workshop (the lumberjack: bouncy, squelchy, and it climbs). */
public final class MushroomAnims {
    private MushroomAnims() { }

    public static final Map<String, Clip> CLIPS = new LinkedHashMap<>();
    private static final double TAU = Math.PI * 2;

    private static Clip add(String id, double len, boolean loop, double move, Clip.Run run) {
        Clip c = new Clip(id, (float) len, loop, (float) move, run);
        CLIPS.put(id, c);
        return c;
    }

    // ---- items: log, sapling or stick ----
    private static double holdX(String item) {
        return "sapling".equals(item) ? -1.05 : ("stick".equals(item) ? -1.1 : -1.25);
    }

    private static double holdZ(String item) {
        return "sapling".equals(item) ? -0.18 : ("stick".equals(item) ? -0.1 : -0.32);
    }

    private static void holdArms(Pose P, String item, double k) {
        P.arms(holdX(item) * k, holdZ(item) * k);
    }

    // ---- walking: one gait drives every walk ----
    private static final double[] WALK_HAPPY = {2.4, 0.07, 1, 0.8, 0.9, 0.5, 0.45, 0.16, 0.04, 0.12, 0.2, 0.5};
    private static final double[] WALK_SQUELCH = {0.5, 0.13, 0.3, 0.5, 0.12, 0, 0.12, 0.05, 0.06, 0.08, 0.05, 0.3};
    // order: hop, sq, stretch, leg, armSwing, armUp, armOut, sway, lean, bob, headSway, capFrac

    private static double[] mixWalk(double b) {
        double[] o = new double[12];
        for (int i = 0; i < 12; i++) o[i] = WALK_SQUELCH[i] + (WALK_HAPPY[i] - WALK_SQUELCH[i]) * b;
        return o;
    }

    private static final double[] WALK_MIX = mixWalk(0.5);
    private static final double[] WALK_CARRY = mixWalk(0.5);

    static {
        WALK_CARRY[4] = 0;
        WALK_CARRY[5] = 0;
        WALK_CARRY[6] = 0;
        WALK_CARRY[0] = 1.6;
    }

    private static void gait(Pose P, double t, double[] o, double T) {
        double hop = o[0], sqq = o[1], stretch = o[2], leg = o[3], armSwing = o[4], armUp = o[5], armOut = o[6], sway = o[7], lean = o[8],
                bob = o[9], headSway = o[10], capFrac = o[11];
        double k = Math.floor(t / T), v = (t / T) % 1;
        double air = sin(Math.PI * v), s = sin(Math.PI * (k + v));
        double sq = win(v, 0, 0.07, 0.1, 0.34), st = win(v, 0.3, 0.5, 0.5, 0.7);
        P.offY += hop * air;
        P.legs(s, leg);
        P.rleg.rx += 0.35 * air * (hop / 2.4);
        P.lleg.rx += 0.35 * air * (hop / 2.4);
        double dx = sqq * sq * 0.8 - 0.03 * st * stretch, dy = -sqq * sq + 0.06 * st * stretch;
        P.body.sx += dx;
        P.body.sz += dx;
        P.body.sy += dy;
        // the cap sits inside the body, so it inherits this; undo most of it
        P.cap.sx += (1 + capFrac * dx) / (1 + dx) - 1;
        P.cap.sz += (1 + capFrac * dx) / (1 + dx) - 1;
        P.cap.sy += (1 + capFrac * dy) / (1 + dy) - 1;
        P.body.rz += -s * sway;
        P.body.rx += lean;
        P.head.rx += bob * sq - bob * 0.5 * st;
        P.head.rz += s * headSway;
        P.rarm.rx += -s * armSwing;
        P.larm.rx += s * armSwing;
        P.arms(-armUp * air, armOut * (0.5 + 0.5 * air));
    }

    // ---- sitting ----
    private static void seat(Pose P, double k, double drop) {
        P.body.y += drop * k;
        P.rleg.rx += -1.45 * k;
        P.lleg.rx += -1.45 * k;
    }

    // ---- felling ----
    private static final double BREAK_AT = Anim.M_BREAK_AT;

    /** A strike down at the block under its feet; lands at u = 0.75. */
    private static void downChop(Pose P, double u) {
        double up = win(u, 0, 0.55, 0.6, 0.72), hit = win(u, 0.6, 0.75, 1.0, 1.4);
        P.arms(-2.6 * up - 0.5 * hit, -0.1 * up);
        P.body.rx += -0.15 * up + 0.85 * hit;
        P.head.rx += -0.1 * up + 0.2 * hit;
    }

    // ---- the chest: the golem climbs it ----
    // z is pixels toward the chest from where it stands (13 px from the chest's centre, so the front face is at -6 and the centre at -13):
    // it hangs against the front face and ends up perched on the front edge of the lid, not out over its middle
    private static final double HAND_Z = -10.6, HAND_Y = 10.3;
    private static final double STAND_Z = -6, HANG_Z = -6.6, PERCH_Z = -8.4, DOWN_Z = -8.6;
    private static final double[][] UPZ = {k(0, STAND_Z), k(0.3, STAND_Z), k(0.7, HANG_Z), k(2.4, HANG_Z), k(3.2, -7.4), k(4.2, PERCH_Z)};
    private static final double[][] UPY = {k(0, 0), k(0.3, 0), k(0.7, 3.0, 'o'), k(0.95, 2.3), k(1.4, 2.5), k(1.8, 4.2), k(2.05, 3.7), k(2.5, 5.9),
            k(2.8, 5.4), k(3.5, 10)};
    private static final double[][] UPR = {k(0, 0), k(0.7, 0.08), k(2.4, 0.08), k(3.1, 0.3), k(3.6, 0.55), k(4.3, 0.42)};
    private static final double[][] DNZ = {k(0, PERCH_Z), k(0.45, DOWN_Z)};

    /** The whole climb as one continuous timeline; the chest clips each play a slice of it. T = walk start, walk end, crouch start, unused, way down. */
    private static double[] perch(Pose P, double t, double[] T) {
        double c0 = T[2], td = T[4], ur = t - c0, u = ur < 0.95 ? ur : 0.95 + (ur - 0.95) * 1.25, d = t - td;
        boolean up = t >= c0, down = t >= td;
        double w = sstep(T[0], T[1], t);
        double z, y;
        if (!up) {
            z = STAND_Z * w;
            y = 0;
        } else if (!down) {
            z = kf(u, UPZ);
            y = kf(u, UPY);
        } else {
            double s = Math.max(0, Math.min(0.9, d - 0.5));
            z = d < 0.5 ? kf(d, DNZ) : DOWN_Z * (1 - s / 0.9);
            y = d < 0.5 ? 10 : Math.max(0, 10 + 17.97 * s - 32.3 * s * s);
        }
        double lean = (up ? kf(u, UPR) : 0) * (1 - sstep(td - 0.5, td + 0.05, t));
        double env = sstep(4.2, 4.9, u) * (1 - sstep(td - 0.5, td + 0.05, t));
        P.offZ = (float) z;
        P.offY = (float) y;
        P.floor = (float) ((up && !down && u > 3.6) || (down && d < 0.55) ? 10 : 0);
        boolean on = up && !down;
        double shz = z - 3 * sin(lean), shy = y + 5, aik = Math.atan2(shz - HAND_Z, shy - HAND_Y);
        double grip = on ? sstep(0.55, 0.7, u) * (1 - sstep(4.15, 4.4, u)) : 0;
        double aFree = up ? -0.6 * win(u, 0, 0.12, 0.28, 0.45) + 2.5 * sstep(0.3, 0.62, u) * (1 - sstep(3.9, 4.1, u)) + 0.4 * env : 0;
        aFree += down ? 1.2 * win(d, 0.55, 0.7, 1.2, 1.4) : 0;
        P.arms(-(aFree * (1 - grip) + aik * grip) - lean, 0.06 * grip);
        P.body.rx += lean;
        double effort = on ? win(u, 1.4, 1.6, 3.6, 3.9) : 0, hang = on ? win(u, 0.7, 1.0, 3.2, 3.7) : 0, cr = on ? win(u, 0, 0.25, 0.32, 0.45) : 0;
        P.body.y += 1.2 * cr + (down ? 1.2 * win(d, 0.1, 0.35, 0.45, 0.52) : 0);
        P.body.rz += 0.05 * sin(t * 28) * effort;
        P.head.rz += 0.07 * sin(t * 25 + 1) * effort;
        P.head.rx += -0.5 * hang - 0.4 * (on ? win(u, 0, 0.15, 0.4, 0.6) : 0) + 0.3 * env;
        P.cap.rx += -0.25 * hang - 0.3 * env;
        double walk = win(t, T[0], T[0] + 0.1, T[1] - 0.1, T[1]);
        P.legs(sin(t * 13) * walk, 0.7);
        P.body.y -= abs(sin(t * 13)) * walk * 0.8;
        if (on) {
            double tuck = -0.5 * win(u, 0.4, 0.55, 0.8, 1.0);
            P.rleg.rx += tuck;
            P.lleg.rx += tuck;
            P.legs(sin(t * 12), 0.35 * win(u, 0.75, 1.0, 3.0, 3.3) + 0.6 * win(u, 3.0, 3.2, 3.6, 3.9));
            P.rleg.rx += -1.2 * win(u, 3.4, 3.8, 4.2, 4.6);
            P.body.sy -= 0.1 * win(u, 4.2, 4.26, 4.34, 4.7);
            P.body.sx += 0.06 * win(u, 4.2, 4.26, 4.34, 4.7);
        }
        if (down) {
            double ls = win(d, 1.4, 1.44, 1.5, 1.8);
            P.body.sy -= 0.12 * ls;
            P.body.sx += 0.07 * ls;
        }
        double wg = env * win(t, c0 + 4.2, c0 + 4.6, td - 0.9, td - 0.5);
        P.body.rz += 0.04 * sin(t * 11) * wg;
        P.legs(sin(t * 11), 0.35 * wg);
        P.cap.rz += 0.05 * sin(t * 11 + 1) * wg;
        return new double[]{env, wg};
    }

    private static final double C0 = 0.8, UP_LEN = 4.1, RIM = C0 + UP_LEN;
    private static final double LEAP_G = 300, LEAP_VY = 32, LEAP_T0 = 0.75;
    private static final double LEAP_FLY = (LEAP_VY + Math.sqrt(LEAP_VY * LEAP_VY + 2 * LEAP_G * 10)) / LEAP_G, LEAP_LAND = LEAP_T0 + LEAP_FLY;

    private static void leapMotion(Pose P, double t, String item) {
        perch(P, 9.5 + Math.min(t, 0.55), new double[]{0, 0.7, C0, 0, 10});
        double s = Math.max(0, Math.min(LEAP_FLY, t - LEAP_T0));
        boolean air = t >= LEAP_T0 && t < LEAP_LAND;
        double cr = win(t, 0.45, 0.7, LEAP_T0, LEAP_T0 + 0.05), stretch = win(t, LEAP_T0, LEAP_T0 + 0.05, LEAP_T0 + 0.18, LEAP_T0 + 0.3),
                sq = win(t, LEAP_LAND, LEAP_LAND + 0.04, LEAP_LAND + 0.1, LEAP_LAND + 0.4);
        P.offZ = (float) (t < LEAP_T0 ? PERCH_Z : PERCH_Z * (1 - s / LEAP_FLY));
        P.offY = (float) (t < LEAP_T0 ? 10 : Math.max(0, 10 + LEAP_VY * s - 0.5 * LEAP_G * s * s));
        P.floor = t < LEAP_T0 + 0.05 ? 10 : 0;
        double lift = win(t, 0, 0.25, LEAP_LAND - 0.05, LEAP_LAND + 0.35), out = win(t, 0.2, 0.45, LEAP_LAND - 0.05, LEAP_LAND + 0.35),
                fling = win(t, LEAP_T0 - 0.05, LEAP_T0 + 0.1, LEAP_LAND - 0.05, LEAP_LAND + 0.35);
        if (item != null) holdArms(P, item, sstep(0, 0.25, t));
        else P.arms(-0.9 * lift - 0.5 * fling, 1.35 * out);
        P.body.y += 1.2 * cr;
        P.body.sy += 0.08 * stretch - 0.12 * sq;
        P.body.sx += -0.04 * stretch + 0.07 * sq;
        double tuck = air ? sin(Math.PI * (t - LEAP_T0) / LEAP_FLY) : 0;
        P.rleg.rx += -0.6 * tuck;
        P.lleg.rx += -0.6 * tuck;
        P.rleg.rx += -0.4 * cr;
        P.lleg.rx += -0.4 * cr;
        P.body.rx += -0.1 * fling;
    }

    // ---- putting an item in the chest ----
    private static final double LOB_Z = 0.5, LOB_REL = 0.95;

    private static double lobAngle(double t) {
        double hug = -1.25, dn = -1.15, up = -2.3;
        if (t < 0.35) return hug;
        if (t < 0.75) return hug + (dn - hug) * ease((t - 0.35) / 0.4, 's');
        if (t < LOB_REL) return dn + (up - dn) * ease((t - 0.75) / (LOB_REL - 0.75), 'o');
        return up * (1 - sstep(1.15, 1.7, t));
    }

    private static void lobPose(Pose P, double t, double walkTo) {
        double th = lobAngle(t), cr = win(t, 0.35, 0.65, 0.78, 0.92), st = win(t, 0.85, 0.95, 1.0, 1.15);
        P.offZ = (float) LOB_Z;
        P.offY = (float) (1.0 * st);
        P.arms(th, -0.28 * (1 - sstep(0.75, 0.95, t)) * (t < LOB_REL ? 1 : 0));
        double look = win(t, 0.5, 0.8, 1.3, 1.8), carry = 1 - sstep(0.75, 1.1, t);
        P.body.y += 1.4 * cr;
        P.body.rx += 0.1 * cr - 0.22 * st - 0.12 * look;
        P.head.rx += -0.85 * look - 0.25 * carry;
        P.rleg.rx += -0.5 * cr;
        P.lleg.rx += -0.5 * cr;
        double w = sstep(1.3, 1.95, t);
        P.offZ = (float) (LOB_Z + (walkTo - LOB_Z) * w);
        double wk = walkTo == LOB_Z ? 0 : win(t, 1.3, 1.4, 1.85, 1.95); // no steps when it stays where it is
        P.legs(sin(t * 13) * wk, 0.7);
        P.body.y -= abs(sin(t * 13)) * wk * 0.8;
    }

    private static Clip.Ctx opt() {
        return new Clip.Ctx();
    }

    static {
        // ---- walking ----
        add(Anim.M_WALK, 0.8, true, 32, (P, t, c) -> gait(P, t, WALK_MIX, 0.4));
        add(Anim.M_WALK_ITEM, 0.8, true, 26, (P, t, c) -> {
            gait(P, t, WALK_CARRY, 0.4);
            holdArms(P, c.item == null ? "log" : c.item, 1);
            P.body.rx += -0.05;
        }).hold("front");

        // ---- idling: a nap while it waits ----
        add(Anim.M_NAP, 6.0, true, 0, (P, t, c) -> {
            double k = win(t, 0, 1.2, 4.8, 5.8), br = 0.5 + 0.5 * sin(t * 1.4);
            seat(P, k, 0.5);
            P.body.rx += 0.3 * k;
            P.head.rx += 0.7 * k;
            P.arms(-1.5 * k, 0.15 * k);
            double lift = win(t, 0.3, 0.6, 0.7, 1.0) + win(t, 4.7, 5.0, 5.3, 5.8);
            P.body.y -= 0.3 * br * k + 0.9 * lift;
            P.rleg.y -= 0.9 * lift;
            P.lleg.y -= 0.9 * lift;
            P.head.rz += 0.05 * sin(t * 0.7) * k;
        });

        // ---- offerings ----
        add(Anim.M_AWAIT, 4.2, true, 0, (P, t, c) -> {
            double k = win(t, 0, 0.8, 3.5, 4.1), s = sin(t * 6);
            P.body.sy -= 0.08 * k;
            P.cap.y += 0.5 * k;
            P.cap.rz += 0.2 * k;
            P.arms((-1.5 - 0.06 * s) * k, -0.15 * k);
            P.body.rx += -0.1 * k;
            P.head.rx += -0.15 * k;
            P.head.rz += 0.1 * sin(t * 2.2) * k;
            P.body.y += 0.35 * s * k * (s > 0 ? 1 : 0.4);
        }).hold("front");
        add(Anim.M_RECEIVE, Anim.M_RECEIVE_LEN, false, 0, (P, t, c) -> {
            double d = 1 - sstep(0.5, 1.4, t), pop = win(t, 1.0, 1.2, 2.2, 3.2), sh = win(t, 1.5, 1.6, 2.6, 3.2), has = sstep(0.4, 0.9, t);
            P.body.sy -= 0.18 * d;
            P.head.rx += 0.75 * d;
            P.cap.rz += 0.6 * d;
            P.cap.y += 1.4 * d;
            P.body.y += 0.6 * d;
            P.cap.y -= 1.6 * exp(-(t - 1.1) * 4) * max(0, sin((t - 1.1) * 14)) * (t > 1.1 ? 1 : 0);
            holdArms(P, c.item == null ? "log" : c.item, has);
            P.body.rz += 0.1 * sin(t * 22) * sh;
            P.body.y -= 1.5 * pop * abs(sin(t * 7));
        }).hold("front", 0.4F, 1e9F);

        // ---- felling a tree from the top ----
        add(Anim.M_SNEEZE_VANISH, Anim.M_VANISH_LEN, false, 0, (P, t, c) -> {
            double wu = win(t, 0, 1.4, 1.5, 1.55), sn = win(t, 1.5, 1.6, 1.8, 2.2);
            P.head.rx += -0.4 * wu + 0.7 * sn;
            P.body.rx += -0.12 * wu + 0.28 * sn;
            P.cap.y -= 0.7 * wu;
            P.arms(-0.25 * wu + 0.4 * sn, 0.2 * wu + 0.2 * sn);
            P.head.rz += 0.08 * sin(t * 26) * wu * (wu > 0.5 ? 1 : 0);
            if (t >= 1.6) P.hide = true;
        });
        add(Anim.M_SNEEZE_APPEAR, Anim.M_APPEAR_LEN, false, 0, (P, t, c) -> {
            double tArr = 0.05;
            if (t < tArr) {
                P.hide = true;
                return;
            }
            double u = (t - tArr) / 0.3;
            P.scale = (float) (u < 1 ? 0.5 + 0.65 * sin(u * Math.PI / 2) : 1.15 - 0.15 * sstep(tArr + 0.3, tArr + 0.7, t));
            double gl = win(t, 0.8, 1.1, 1.5, 1.75);
            P.head.rx += 0.35 * gl;
            P.body.rx += 0.1 * gl;
        });
        add(Anim.M_BREAK_BELOW, Anim.M_BREAK_LEN, false, 0, (P, t, c) -> {
            double ci = BREAK_AT;
            downChop(P, t - (ci - 0.75));
            double sq = win(t, ci + 0.3, ci + 0.36, ci + 0.4, ci + 0.65);
            P.body.sy -= 0.14 * sq;
            P.body.sx += 0.09 * sq;
            P.body.sz += 0.09 * sq;
            P.cap.y += 0.6 * sq;
        });

        // ---- picking up ----
        add(Anim.M_PICK_UP, Anim.M_PICK_LEN, false, 0, (P, t, c) -> {
            String item = c.item == null ? "log" : c.item;
            double b = win(t, 0, 0.8, 1.0, 1.7), lift = sstep(1.2, 2.0, t), wob = "log".equals(item) ? win(t, 2.0, 2.2, 2.8, 3.0) : 0;
            P.body.rx += 0.9 * b;
            P.body.y += 1.2 * b;
            P.head.rx += -0.5 * b;
            P.arms(-0.9 * b + holdX(item) * lift, -0.1 * b + holdZ(item) * lift);
            P.rleg.rx += -0.2 * b;
            P.lleg.rx += -0.2 * b;
            P.body.rz += 0.1 * sin(t * 13) * wob;
            P.body.y -= 0.6 * win(t, 2.3, 2.45, 2.6, 2.8);
        }).hold("front", 1.3F, 1e9F);

        // ---- replanting ----
        add(Anim.M_PLANT, Anim.M_PLANT_LEN, false, 0, (P, t, c) -> {
            double hop = win(t, 0, 0.2, 0.3, 0.5), b = win(t, 0.5, 1.0, 1.9, 2.4);
            P.body.y -= 2.0 * hop;
            P.arms(0.2 * hop, 0.5 * hop);
            P.body.rx += 0.7 * b;
            P.head.rx += -0.3 * b;
            P.arms(-1.0 * b, -0.1 * b);
            double pat = win(t, 1.1, 1.2, 1.8, 1.9);
            P.arms(0.18 * sin(t * 13) * pat, 0);
        }).hold("hand", 0, 1.2F);

        // ---- the chest sequences ----
        Clip openLid = new Clip("open-lid", 1.8F, false, 0, (P, t, c) -> {
            perch(P, t, new double[]{0, 0.7, 1e9, 0, 1e9});
            if (c.stand != 0) P.offZ = (float) (c.stand * sstep(0, 0.7, t));
            double cr = win(t, 0.7, 0.95, 1.0, 1.1), up = win(t, 0.95, 1.1, 1.4, 1.75), hop = sin(Math.PI * clamp01((t - 1.0) / 0.5));
            P.body.y += cr;
            P.offY += 2.2 * hop;
            P.arms(-2.4 * up, 0.3 * up);
            P.head.rx += -0.3 * up;
            P.body.rx += -0.1 * up;
            P.rleg.rx += 0.35 * hop;
            P.lleg.rx += 0.35 * hop;
        }).hold("front"); // (the raised arms put a hand-held item inside the cap, so it stays in front of the body)
        Clip clamber = new Clip("clamber-up", (float) UP_LEN, false, 0, (P, t, c) -> perch(P, t + C0, new double[]{0, 0.7, C0, 0, 1e9}));
        Clip putIn = new Clip("put-in-chest", 2.1F, false, 0, (P, t, c) -> lobPose(P, t, LOB_Z)).hold("front");
        Clip leap = new Clip("leap-and-close", 1.6F, false, 0, (P, t, c) -> leapMotion(P, t, null));
        Clip leapItem = new Clip("leap-and-close-item", 1.6F, false, 0, (P, t, c) -> leapMotion(P, t, c.item == null ? "log" : c.item)).hold("front");
        final double RT_REL = 3.5;
        Clip rummage = new Clip("rummage-throw", 4.8F, false, 0, (P, t, c) -> {
            perch(P, t + RIM, new double[]{0, 0.7, C0, 0, RIM + c.len + 0.5});
            double lean = win(t, 0.1, 0.6, 1.9, 2.4);
            P.body.rx += 0.18 * lean;
            P.arms(0.2 * lean + 0.5 * sin(t * 9) * lean, 0.15 * lean);
            P.head.rx += 0.12 * sin(t * 5) * lean;
            double rx = kf(t, k(2.4, 0), k(2.8, -2.3, 'o'), k(3.1, -2.3), k(3.3, -2.7), k(3.5, -1.5, 'o'), k(3.9, -1.5), k(4.6, 0));
            double rz = kf(t, k(2.4, 0), k(2.8, 0.1), k(3.1, 0.1), k(3.3, 0.35), k(3.5, 1.3, 'o'), k(3.9, 1.3), k(4.6, 0));
            double lx = kf(t, k(2.4, 0), k(2.8, -2.3, 'o'), k(3.1, -2.3), k(3.6, 0));
            P.rarm.rx += rx;
            P.rarm.rz += rz;
            P.larm.rx += lx;
            P.body.ry += kf(t, k(3.0, 0), k(3.3, -0.35), k(3.5, 0.2, 'o'), k(3.9, 0.2), k(4.6, 0));
            P.head.rx += -0.3 * win(t, 2.6, 2.9, 3.2, 3.4);
        }).hold("front", 2.5F, (float) RT_REL); // (thrown from in front of the body, see ContainerInteraction.throwOut)

        Clip.Ctx stand = opt();
        stand.stand = (float) LOB_Z;
        Clip.Ctx thrown = opt();
        thrown.thrown = true;
        // putting in needs no climb: it opens the lid, lobs the item in and swats the lid shut from the floor
        Clip swat = ClipData.ref("mushroom", "swat-lid", 1.2F); // animations/mushroom/swat-lid.json
        Clip put = Clip.seq(Anim.M_PUT_IN, Clip.seg(openLid, 0, stand), Clip.seg(putIn, 1.8F, null), Clip.seg(swat, 3.9F, null));
        CLIPS.put(Anim.M_PUT_IN, put);
        Clip fetch = Clip.seq(Anim.M_FETCH, Clip.seg(openLid, 0, null), Clip.seg(clamber, 1.8F, null), Clip.seg(rummage, 5.9F, null), Clip.seg(leap, 10.7F, thrown));
        CLIPS.put(Anim.M_FETCH, fetch);

        // ---- sitting with an item when the chest is full ----
        add(Anim.M_SIT_FULL, 6.0, true, 0, (P, t, c) -> {
            double br = sin(t * TAU / 3.0), no = win(t, 0.6, 1.0, 2.6, 3.1), sigh = win(t, 3.4, 3.9, 4.6, 5.3);
            P.offZ = (float) -3.3;
            P.offRy = (float) Math.PI;
            seat(P, 1, 0.3);
            P.rleg.rz += 0.12;
            P.lleg.rz -= 0.12;
            String item = c.item == null ? "log" : c.item;
            P.arms(holdX(item) - 0.2, holdZ(item) * 1.3);
            P.body.rx += 0.03 * br;
            P.body.y -= 0.15 * br;
            P.head.rx += 0.1 + 0.04 * br + 0.14 * sigh;
            P.head.ry += 0.35 * sin(t * TAU / 1.4) * no;
            P.cap.rx += -0.18 + 0.15 * sigh;
        }).hold("front");
    }
}
