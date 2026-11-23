package com.scottcaldwell.industriousgolems.client.anim;

import static com.scottcaldwell.industriousgolems.client.anim.Pose.*;

import org.joml.Vector3d;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The stone golem's clips, ported from the animation workshop (the miner: built like a boulder, heavy, slow and deliberate, and it moves like a
 * gorilla). On the move it knuckle-walks, hunched over with its fists on the ground; its arms are rigid, so a fist can only leave the ground by rolling
 * the shoulders. To carry something it hugs the load in its right arm. Where the clips need to know where a fist or the ground is, {@link Kin} answers.
 */
public final class StoneAnims {
    private StoneAnims() { }

    public static final Map<String, Clip> CLIPS = new LinkedHashMap<>();

    private static final double S2 = Math.PI * 2, LEG = Kin.LEG;
    private static final double STWALK = 2.2, S_HIP = 7.5, S_FOLDK = 0.6, S_FOLD = 0.4, S_Y0 = 8.8, S_STRIDE = 6.0, S_ZF = -15.5, S_REACH = 1.2, S_M0 = 0;
    private static final double S_IZ = -10.5, S_FZ = -4;

    // ---- a pose from a handful of numbers (see sPose) ----
    private static final int TH = 0, BY = 1, BZ = 2, HX = 3, HY = 4, HZ = 5, RP = 6, LP = 7, RS = 8, LS = 9, RF = 10, LF = 11, HJ = 12, HP = 13, HY2 = 14, FO = 15, FL = 16, RL = 17, LL = 18, N = 19;

    private static double[] hp() {
        return new double[N];
    }

    private static double[] blend(double[] a, double[] b, double k) {
        double[] o = new double[N];
        for (int i = 0; i < N; i++) o[i] = a[i] * (1 - k) + b[i] * k;
        return o;
    }

    private static double[] with(double[] base, Object... kv) {
        double[] o = base.clone();
        for (int i = 0; i < kv.length; i += 2) o[idx((String) kv[i])] = ((Number) kv[i + 1]).doubleValue();
        return o;
    }

    private static int idx(String k) {
        return switch (k) {
            case "th" -> TH; case "by" -> BY; case "bz" -> BZ; case "hx" -> HX; case "hy" -> HY; case "hz" -> HZ; case "rp" -> RP; case "lp" -> LP;
            case "rs" -> RS; case "ls" -> LS; case "rf" -> RF; case "lf" -> LF; case "hj" -> HJ; case "hp" -> HP; case "hy2" -> HY2; case "fo" -> FO;
            case "fl" -> FL; case "rl" -> RL; case "ll" -> LL;
            default -> throw new IllegalArgumentException(k);
        };
    }

    private static double[] pose(Object... kv) {
        return with(hp(), kv);
    }

    /** th: how far the body leans forward; by/bz: twist and roll; hx/hy/hz: where the head looks (hx from the horizon); rp/lp: how far each arm points forward from hanging; rs/ls: how far each arm is drawn in. */
    private static void sPose(Pose P, double[] o) {
        double th = o[TH];
        P.body.rx += th;
        P.body.ry += o[BY];
        P.body.rz += o[BZ];
        P.head.rx += o[HX] - th;
        P.head.ry += o[HY];
        P.head.rz += o[HZ];
        P.rarm.rx += -th - o[RP];
        P.larm.rx += -th - o[LP];
        P.rarm.rz += -o[RS];
        P.larm.rz += o[LS];
        P.rfore.rx += o[RF];
        P.lfore.rx += o[LF];
        if (o[HJ] != 0) {
            P.head.y += 2.2 * o[HJ];
            P.head.z -= 5.0 * o[HJ];
        }
        if (o[HP] != 0) P.hips.rx += o[HP];
        if (o[HY2] != 0) P.hips.ry += o[HY2];
        P.rfore.rx += -o[FO] + o[FL];
        P.lfore.rx += -o[FO] + o[FL];
        P.rleg.rx += o[RL];
        P.lleg.rx += o[LL];
    }

    // ---- standing on the ground ----

    /** Puts the golem on the ground: it is moved so that whatever is planted (weights 0 to 1) just touches it. */
    private static void sGround(Pose P, double wr, double wl, double wrl, double wll, boolean legsLow) {
        double[] m = {Kin.lowestArm(P, false), Kin.lowestArm(P, true), Kin.lowestLeg(P, false), Kin.lowestLeg(P, true)};
        double f = Math.min(wr, wl);
        if (f > 0.01) { // both fists down: the two arms are not quite the same height, so roll the body a touch to level them
            P.body.rz += f * (m[1] - m[0]) / 24;
            m = new double[]{Kin.lowestArm(P, false), Kin.lowestArm(P, true), Kin.lowestLeg(P, false), Kin.lowestLeg(P, true)};
        }
        double[] w = {wr, wl, wrl, wll};
        double s = 0, d = 0;
        for (int i = 0; i < 4; i++) {
            s += w[i];
            d += w[i] * m[i];
        }
        double dy = s > 0.001 ? -d / s : 0;
        double low = Math.min(Math.min(m[0] + (wr < 0.3 ? 0.6 : 0), m[1] + (wl < 0.3 ? 0.6 : 0)), legsLow ? Math.min(m[2], m[3]) : 1e9);
        P.offY += Math.max(dy, -low);
    }

    private static double herm(double v, double p0, double m0, double p1, double m1) {
        double v2 = v * v, v3 = v2 * v;
        return (2 * v3 - 3 * v2 + 1) * p0 + (v3 - 2 * v2 + v) * m0 + (-2 * v3 + 3 * v2) * p1 + (v3 - v2) * m1;
    }

    /** Where a planted point is at phase p, in the golem's frame: it goes back at the ground speed, is then thrown forward and lands moving fast. */
    private static double footZ(double p, double z0, double plunge, double reach) {
        if (p < 0.5) return z0 + S_STRIDE * (p / 0.5);
        double v = (p - 0.5) / 0.5;
        return herm(v, z0 + S_STRIDE, S_STRIDE * S_M0, z0, plunge) - reach * Math.pow(Math.sin(Math.PI * v), 1.2);
    }

    private static double rock(double p) {
        if (p < 0.5) return 0.12 * Math.exp(-p * 18);
        double v = (p - 0.5) / 0.5;
        return -0.1 * sstep(0, 0.25, v) * (1 - sstep(0.4, 0.8, v)) + 0.1 * sstep(0.7, 0.96, v) * (1 - sstep(0.96, 1, v));
    }

    private static double fold(double p) {
        if (p < 0.5) return 0;
        double u = (p - 0.5) / 0.5;
        return S_FOLD * Math.sin(Math.PI * Math.pow(u, S_FOLDK));
    }

    private static double stance(double p) {
        return 1 - sstep(0.42, 0.52, p) + sstep(0.95, 1.0, p);
    }

    private static double asinc(double x, double lo, double hi) {
        return Math.asin(Math.max(lo, Math.min(hi, x)));
    }

    /** The shoulders' z for a body leaning th, twisted by and rolled bz. */
    private static double[] shoulders(Pose tmp, double th, double by, double bz) {
        tmp.body.rx = (float) th;
        tmp.body.ry = (float) by;
        tmp.body.rz = (float) bz;
        return new double[]{Kin.shoulder(tmp, false).z, Kin.shoulder(tmp, true).z};
    }

    private static void legsStep(Pose P, double pl, double pr) {
        double fr = footZ(pl, -3, -6, 0), fl = footZ(pr, -3, -6, 0);
        P.rleg.rx += asinc(fr / LEG, -0.95, 0.95);
        P.lleg.rx += asinc(fl / LEG, -0.95, 0.95);
        P.rleg.y -= 1.3 * Math.sin(Math.PI * Math.max(0, (pl - 0.5) / 0.5));
        P.lleg.y -= 1.3 * Math.sin(Math.PI * Math.max(0, (pr - 0.5) / 0.5));
    }

    /** The heavy knuckle-walk: low, flat and planted. Every planted fist and foot is held still on the ground. */
    private static void knuckle(Pose P, double t) {
        double p = t / STWALK, pr = p % 1, pl = (p + 0.5) % 1, ph = S2 * p, sp = ph % Math.PI, kick = Math.exp(-6 * sp), lag = Math.pow(Math.max(0, Math.cos(2 * ph - 1.0)), 3);
        double by = -0.24 * Math.sin(ph - 0.2), bz = -0.06 * Math.sin(ph - 0.5);
        double zr = footZ(pr, S_ZF, -14, S_REACH), zl = footZ(pl, S_ZF, -14, S_REACH), wr = stance(pr), wl = stance(pl);
        double th = 0.75, thT = th, pr_ = 0.5, pl_ = 0.5;
        Pose tmp = new Pose();
        for (int it = 0; it < 6; it++) {
            thT = th + 0.02 * Math.sin(2 * ph - 0.6) + 0.07 * kick;
            double[] sh = shoulders(tmp, thT, by, bz);
            pr_ = asinc((sh[0] - zr) / 9, -0.9, 0.99);
            pl_ = asinc((sh[1] - zl) / 9, -0.9, 0.99);
            double pe = (wr * pr_ + wl * pl_) / Math.max(0.001, wr + wl);
            th = 0.5 * th + 0.5 * Math.acos(Math.max(0, Math.min(1, (S_Y0 + 9 * Math.cos(pe) - S_HIP) / 14)));
        }
        sPose(P, pose("th", thT, "rp", pr_, "lp", pl_, "rs", -0.05, "ls", -0.05, "hx", 0.03 * Math.sin(2 * ph - 1.4) - 0.12 * kick, "hy", 0.16 * Math.sin(ph - 0.2),
                "hz", 0.06 * Math.sin(ph - 0.6), "by", by, "bz", bz));
        P.head.y += 2.2;
        P.head.z -= 5.0;
        P.rfore.rx += pr_ + rock(pr) - fold(pr);
        P.lfore.rx += pl_ + rock(pl) - fold(pl);
        legsStep(P, pl, pr);
        P.hips.rx += thT * 0.55 - P.body.rx;
        P.hips.ry += -0.12 * Math.sin(ph + 0.5);
        P.hips.rz += 0.05 * Math.sin(ph - 0.1) - P.body.rz;
        P.body.sy -= 0.07 * kick + 0.02 * lag;
        P.body.sx += 0.03 * kick;
        P.body.sz += 0.03 * kick;
        P.hips.sy -= 0.03 * kick;
        sGround(P, wr, wl, 0, 0, false);
        P.offY -= 0.9 * kick;
    }

    // ---- the ore curled up in the right arm ----
    private static final double CURL_RP = 0.1, CURL_RS = 0.38, CURL_RF = -1.5;

    private static void carry(Pose P, double t) {
        double p = t / STWALK, pl = (p + 0.5) % 1, pr = p % 1, ph = S2 * p, kick = Math.exp(-3 * S2 * pl);
        double by = -0.2 * Math.sin(ph - 0.2), bz = -0.05 * Math.sin(ph - 0.5), zl = footZ(pl, S_ZF, -14, S_REACH), wl = stance(pl);
        double th = 0.7, thT = th, pl_ = 0.5;
        Pose tmp = new Pose();
        for (int it = 0; it < 6; it++) {
            thT = th + 0.02 * Math.sin(2 * ph - 0.6) + 0.07 * kick;
            double[] sh = shoulders(tmp, thT, by, bz);
            pl_ = asinc((sh[1] - zl) / 9, -0.9, 0.99);
            th = 0.5 * th + 0.5 * (wl * Math.acos(Math.max(0, Math.min(1, (S_Y0 + 9 * Math.cos(pl_) - S_HIP) / 14))) + (1 - wl) * 0.7);
        }
        sPose(P, pose("th", thT, "rp", CURL_RP, "rs", CURL_RS, "rf", CURL_RF, "lp", pl_, "ls", -0.05, "lf", pl_ + rock(pl) - fold(pl), "hx", -0.1 * kick + 0.03 * Math.sin(2 * ph - 1.4),
                "hy", 0.14 * Math.sin(ph - 0.2), "hz", 0.05 * Math.sin(ph - 0.6), "by", by, "bz", bz, "hj", 0.9));
        legsStep(P, pl, pr);
        P.hips.rx += thT * 0.55;
        P.hips.ry += -0.12 * Math.sin(ph + 0.5);
        P.hips.rz += 0.05 * Math.sin(ph - 0.1);
        P.body.sy -= 0.05 * kick;
        P.body.sx += 0.02 * kick;
        P.body.sz += 0.02 * kick;
        sGround(P, 0, wl, 0.3, 0.3, true);
        P.offY -= 0.7 * kick;
    }

    /** The pose for standing on all four limbs (the same arm and lean solving as the walk, with both fists planted). */
    private static double[] hunch(double zr, double zl, double by, double bz, double wr, double wl) {
        double th = 0.75, thT = th, pr_ = 0.5, pl_ = 0.5;
        Pose tmp = new Pose();
        for (int it = 0; it < 6; it++) {
            thT = th;
            double[] sh = shoulders(tmp, thT, by, bz);
            pr_ = asinc((sh[0] - zr) / 9, -0.9, 0.99);
            pl_ = asinc((sh[1] - zl) / 9, -0.9, 0.99);
            th = 0.5 * th + 0.5 * Math.acos(Math.max(0, Math.min(1, (S_Y0 + 9 * Math.cos((wr * pr_ + wl * pl_) / Math.max(0.001, wr + wl)) - S_HIP) / 14)));
        }
        double lg = Math.asin(S_FZ / LEG);
        return pose("th", thT, "rp", pr_, "lp", pl_, "rf", pr_, "lf", pl_, "rs", 0.05, "ls", 0.05, "by", by, "bz", bz, "hj", 1, "hp", thT * 0.55, "rl", lg, "ll", lg, "hx", 0);
    }

    private static void place(Pose P, double[] o, double up, double wr, double wl) {
        sPose(P, o);
        double a = 1 - up;
        sGround(P, a * wr, a * wl, 0.3 + 0.7 * up, 0.3 + 0.7 * up, true);
    }

    private static final double[] S_TRAY = pose("th", -0.08, "rp", 0.95, "lp", 0.95, "rs", 0.22, "ls", 0.22, "fo", 0.75, "hx", 0.1);
    private static final double[] S_HUG = pose("th", -0.1, "rp", 0.55, "lp", 0.55, "rs", 0.42, "ls", 0.42, "fo", 1.15, "hx", 0.12);
    private static final double[] S_UPRIGHT = pose("th", -0.15, "rp", 0.35, "lp", 0.35, "rf", -0.45, "lf", -0.45, "rs", 0.1, "ls", 0.1, "rl", 0, "ll", 0, "hp", 0, "hx", 0.12, "hj", 0.25);

    // ---- an arm worked out to put a fist at a point: a small solver ----
    private static void setArm(Pose P, double th, double[] x, boolean left) {
        Pose.Part arm = left ? P.larm : P.rarm, fore = left ? P.lfore : P.rfore;
        arm.rx = (float) (-th - x[0]);
        arm.rz = (float) (left ? x[1] : -x[1]);
        fore.rx = (float) x[2];
    }

    private static double[] solve3(double[][] A, double[] b) {
        double[][] m = new double[3][4];
        for (int i = 0; i < 3; i++) {
            m[i][0] = A[i][0];
            m[i][1] = A[i][1];
            m[i][2] = A[i][2];
            m[i][3] = b[i];
        }
        for (int i = 0; i < 3; i++) {
            int p = i;
            for (int j = i + 1; j < 3; j++) if (Math.abs(m[j][i]) > Math.abs(m[p][i])) p = j;
            double[] f = m[i];
            m[i] = m[p];
            m[p] = f;
            for (int j = i + 1; j < 3; j++) {
                double ff = m[j][i] / m[i][i];
                for (int k = i; k < 4; k++) m[j][k] -= ff * m[i][k];
            }
        }
        double[] x = new double[3];
        for (int i = 2; i >= 0; i--) {
            x[i] = m[i][3];
            for (int j = i + 1; j < 3; j++) x[i] -= m[i][j] * x[j];
            x[i] /= m[i][i];
        }
        return x;
    }

    private static double[] ik1(Pose P, boolean left, double[] target, double[] x0, double[] prior, double wgt) {
        double th = P.body.rx;
        double[] x = x0.clone();
        double[][] J = new double[6][3];
        for (int it = 0; it < 24; it++) {
            setArm(P, th, x, left);
            Vector3d a = Kin.fist(P, left);
            double[] e = {a.x - target[0], a.y - target[1], a.z - target[2], wgt * (x[1] - prior[1]), wgt * (x[2] - prior[2]), 0.04 * (x[0] - prior[0])};
            for (int j = 0; j < 3; j++) {
                x[j] += 0.01;
                setArm(P, th, x, left);
                Vector3d b = Kin.fist(P, left);
                x[j] -= 0.01;
                J[0][j] = (b.x - a.x) / 0.01;
                J[1][j] = (b.y - a.y) / 0.01;
                J[2][j] = (b.z - a.z) / 0.01;
                J[3][j] = j == 1 ? wgt : 0;
                J[4][j] = j == 2 ? wgt : 0;
                J[5][j] = j == 0 ? 0.04 : 0;
            }
            double[][] A = {{0.3, 0, 0}, {0, 0.3, 0}, {0, 0, 0.3}};
            double[] rhs = new double[3];
            for (int i = 0; i < 3; i++) {
                for (int k = 0; k < 6; k++) {
                    rhs[i] -= J[k][i] * e[k];
                    for (int j = 0; j < 3; j++) A[i][j] += J[k][i] * J[k][j];
                }
            }
            double[] d = solve3(A, rhs);
            x[0] += Math.max(-0.5, Math.min(0.5, d[0]));
            x[1] += Math.max(-0.5, Math.min(0.5, d[1]));
            x[2] += Math.max(-0.5, Math.min(0.5, d[2]));
            x[0] = Math.max(-0.3, Math.min(1.9, x[0]));
            x[1] = Math.max(-0.8, Math.min(0.8, x[1]));
            x[2] = Math.max(-2.0, Math.min(0.15, x[2]));
        }
        return x;
    }

    private static double[] armIk(Pose P, AnimState st, double tt, boolean left, double[] target) {
        double[] last = left ? st.ikL : st.ikR;
        double lt = left ? st.ikLt : st.ikRt;
        boolean ok = last != null && Math.abs(tt - lt) < 0.25;
        double[] prior = {1.2, 0.1, -0.5};
        double[][] starts = ok ? new double[][]{{0.6, 0.1, -0.3}, {1.2, 0.1, -0.5}, {1.7, 0.1, -0.8}, last} : new double[][]{{0.6, 0.1, -0.3}, {1.2, 0.1, -0.5}, {1.7, 0.1, -0.8}};
        double[] best = null;
        double bc = 1e9;
        for (double[] s : starts) {
            double[] x = ik1(P, left, target, s, prior, 0.12);
            setArm(P, P.body.rx, x, left);
            Vector3d a = Kin.fist(P, left);
            double c = Math.pow(a.x - target[0], 2) + Math.pow(a.y - target[1], 2) + Math.pow(a.z - target[2], 2) + 0.5 * Math.pow(x[0] - prior[0], 2) + 0.3 * Math.pow(x[1] - prior[1], 2)
                    + (ok ? 3 * (Math.pow(x[0] - last[0], 2) + Math.pow(x[1] - last[1], 2) + Math.pow(x[2] - last[2], 2)) : 0);
            if (c < bc - 1e-6) {
                bc = c;
                best = x;
            }
        }
        if (ok) {
            double lim = 5 * Math.abs(tt - lt) + 0.01;
            for (int i = 0; i < 3; i++) best[i] = last[i] + Math.max(-lim, Math.min(lim, best[i] - last[i]));
        }
        if (left) {
            st.ikL = best.clone();
            st.ikLt = tt;
        } else {
            st.ikR = best.clone();
            st.ikRt = tt;
        }
        return best;
    }

    // ---- the chest: knee-high, the golem stays hunched a few steps back and works the lid and the ore with its fists ----
    private static final double S_CZ = -17.5, S_OFZ = 6, S_CZR = S_CZ - S_OFZ, S_HZR = S_CZR - 7, S_LOPEN = 1.25;

    private static double[] lidEdge(double a) {
        return new double[]{S_HZR + 14 * Math.cos(a), 10 + 14 * Math.sin(a)};
    }

    private static double[] hook(double la, boolean side) {
        double[] e = lidEdge(la);
        double[] m = {Math.cos(la - 0.9), Math.sin(la - 0.9)};
        return new double[]{side ? -3 : 3, e[1] + 6.2 * m[1], e[0] + 6.2 * m[0]};
    }

    private static double[] pushPt(double la, boolean side) {
        double[] ee = lidEdge(la);
        return new double[]{side ? -4 : 4, ee[1] - 3.5 * Math.cos(la) - 0.5, ee[0] + 3.5 * Math.sin(la) + 1};
    }

    private static final double[] S_DROPT = {2.5, 15.5, S_CZR + 1.5}, S_INT = {2.5, 13.5, S_CZR + 1.5};

    private static double heave(double u, double h0, double h1) {
        if (u < h0) return 0;
        if (u < h1) return 1.05 * sstep(h0, h1, u);
        double s = u - h1, fly = 0.3;
        if (s < fly) return 1.05 + (S_LOPEN - 1.05) * (1 - Math.pow(1 - s / fly, 2));
        double d = s - fly;
        return S_LOPEN - 0.08 * Math.exp(-d * 9) * Math.abs(Math.sin(d * 14));
    }

    private static double slam(double u, double hit, double slam) {
        if (u < hit) return S_LOPEN;
        double v = (u - hit) / (slam - hit);
        if (v < 1) return S_LOPEN * (1 - Math.pow(v, 1.8));
        double d = u - slam;
        return 0.12 * Math.exp(-d * 10) * Math.abs(Math.sin(d * 16));
    }

    /** One fist's job: how much it is aimed (k) and at what. */
    private record Aim(double k, double[] t) { }

    private static final Aim NONE = new Aim(0, new double[]{0, 0, 0});

    private record Arm(double la, Aim arm) { }

    private static Arm openWith(double u, boolean side) {
        double la = heave(u, 0.5, 1.0), k = sstep(0, 0.5, u) * (1 - sstep(1.0, 1.7, u));
        return new Arm(la, new Aim(k, hook(Math.min(la, 1.05), side)));
    }

    private static Arm slamWith(double u, boolean side) {
        double la = slam(u, 0.55, 0.95), k = sstep(0, 0.55, u) * (1 - sstep(1.1, 1.8, u)), l2 = Math.min(Math.max(la, 0), S_LOPEN);
        return new Arm(la, new Aim(k, pushPt(u < 0.55 ? S_LOPEN : l2, side)));
    }

    private record Chest(Aim L, Aim R, double cR, double wl, double wr, double th, double hx, double hy) {
        Chest(Aim L, Aim R, double cR, double wl, double wr) {
            this(L, R, cR, wl, wr, 0, 0, 0);
        }
    }

    private static void applyChest(Pose P, AnimState state, double tt, Chest st) {
        double kk = Math.max(st.L.k, st.R.k);
        double[] H = hunch(S_IZ, S_IZ, 0.22 * (st.L.k - st.R.k), 0, Math.max(0.15, st.wr), Math.max(0.15, st.wl));
        H[TH] += 0.1 * kk + st.th;
        H[HX] = 0.12 * kk + st.hx;
        H[HY] = -0.25 * (st.R.k - st.L.k) + st.hy;
        double cR = st.cR;
        double[] bR = {lerp(H[RP], CURL_RP, cR), lerp(H[RS], CURL_RS, cR), lerp(H[RF], CURL_RF, cR)}, bL = {H[LP], H[LS], H[LF]};
        sPose(P, H);
        double th = P.body.rx;
        double[] x;
        if (st.R.k > 0.001) {
            double[] xr = armIk(P, state, tt, false, st.R.t);
            x = new double[]{lerp(bR[0], xr[0], st.R.k), lerp(bR[1], xr[1], st.R.k), lerp(bR[2], xr[2], st.R.k)};
        } else x = bR;
        setArm(P, th, x, false);
        if (st.L.k > 0.001) {
            double[] xl = armIk(P, state, tt, true, st.L.t);
            x = new double[]{lerp(bL[0], xl[0], st.L.k), lerp(bL[1], xl[1], st.L.k), lerp(bL[2], xl[2], st.L.k)};
        } else x = bL;
        setArm(P, th, x, true);
        double lw = 0.3 + 0.7 * (1 - Math.max(st.wl, st.wr));
        sGround(P, st.wr, st.wl, lw, lw, true);
        P.offZ += S_OFZ;
    }

    private static double[] mixPt(double[] a, double[] b, double s) {
        return new double[]{lerp(a[0], b[0], s), lerp(a[1], b[1], s), lerp(a[2], b[2], s)};
    }

    private static Chest stowState(double t) {
        Arm o = openWith(t - 0.25, true);
        double kLd = o.arm.k, kR = sstep(1.0, 1.6, t) * (1 - sstep(3.2, 3.9, t)), s = sstep(2.2, 2.7, t);
        double[] tt = mixPt(S_DROPT, slamWith(t - 2.2, false).arm.t, s);
        return new Chest(new Aim(kLd, o.arm.t), new Aim(kR, tt), 1 - sstep(1.9, 2.3, t), 1 - kLd, sstep(3.6, 4.2, t));
    }

    private static Chest fetchState(double t) {
        Arm o = openWith(t - 0.25, true), c = slamWith(t - 2.3, true);
        double kL = Math.max(o.arm.k, c.arm.k);
        double kR = sstep(1.0, 1.6, t) * (1 - sstep(2.4, 3.0, t)), sc = win(t, 1.6, 1.7, 1.9, 2.0) * Math.sin(t * 22) * 0.4;
        double[] Lt = o.arm.k > c.arm.k ? o.arm.t : c.arm.t;
        return new Chest(new Aim(kL, Lt), new Aim(kR, new double[]{S_INT[0] + sc, S_INT[1], S_INT[2]}), sstep(2.2, 3.0, t), 1 - kL, 0);
    }

    // ---- mining a block and picking up the ore ----
    private static final double STMINE = 7.0, S_GRAB = 1.5, S_ARR = 2.9;
    private static final double SM_W0 = 0.6, SM_W1 = 2.5, SM_S0 = 3.3, SM_HIT = 3.55, SM_REC0 = 4.4, SM_REC1 = 6.2;
    private static final double[] S_WIND = {3.45, -0.5, -0.25, 0.95}, S_SMASH = {1.45, 0.05, -0.02, -0.3}, S_THRU = {1.0, 0.2, -0.05, -0.45};   // rp, rs, rf, by

    private static double[] blend4(double[] a, double[] b, double k) {
        return new double[]{lerp(a[0], b[0], k), lerp(a[1], b[1], k), lerp(a[2], b[2], k), lerp(a[3], b[3], k)};
    }

    private static Clip add(String id, double len, boolean loop, double move, Clip.Run run) {
        Clip c = new Clip(id, (float) len, loop, (float) move, run);
        CLIPS.put(id, c);
        return c;
    }

    static {
        add("knuckle", STWALK, true, 5.45, (P, t, c) -> knuckle(P, t));
        add("lumber", STWALK, true, 5.45, (P, t, c) -> carry(P, t)).hold("tuck");

        add("pick-up-ore", 5.2, false, 0, (P, t, c) -> {
            double reach = sstep(0.2, 1.2, t) * (1 - sstep(S_GRAB, S_GRAB + 0.5, t)), sc = sstep(S_GRAB, S_ARR, t);
            double[] H = hunch(S_IZ, S_IZ, 0.12 * reach, 0, (1 - reach) * (1 - sc), 1);
            H[RP] = lerp(H[RP] + 0.5 * reach, CURL_RP, sc);
            H[RF] = lerp(H[RF] + 0.1 * reach, CURL_RF, sc);
            H[RS] = CURL_RS * sc;
            H[RL] = lerp(H[RL], 0.41, sc);
            H[LL] = lerp(H[LL], -0.41, sc);
            place(P, H, 0, (1 - reach) * (1 - sc), 1);
            double u = (t - S_ARR) / 0.7;
            if (u > 0 && u < 1) P.offY -= 0.8 * Math.sin(Math.PI * u) * (1 - u);
        }).hold("tuck", (float) S_ARR, 1e9F);

        add("mine-block", STMINE, false, 0, (P, t, c) -> {
            double w = sstep(SM_W0, SM_W1, t), sl = Math.pow(Math.max(0, Math.min(1, (t - SM_S0) / (SM_HIT - SM_S0))), 1.3), rec = sstep(SM_REC0, SM_REC1, t), d = t - SM_HIT;
            double ft = sstep(SM_HIT, SM_HIT + 0.4, t);
            double[] A = t < SM_S0 ? blend4(new double[]{0, 0, 0, 0}, S_WIND, w) : blend4(blend4(S_WIND, S_SMASH, sl), S_THRU, ft);
            double trem = win(t, SM_W1, SM_W1 + 0.3, SM_S0 - 0.05, SM_S0) * Math.sin(t * 38), sh = d > 0 ? Math.exp(-d * 7) * Math.cos(d * 34) : 0;
            double by = A[3] * (1 - rec);
            double[] H = hunch(S_IZ, S_IZ, by, 0.04 * w * (1 - sl) - 0.04 * sl, 0, 1);
            double k = 1 - rec;
            H[RP] = lerp(H[RP], A[0], k);
            H[RS] = lerp(H[RS], A[1], k);
            H[RF] = lerp(H[RF], A[2], k);
            H[HY] = -0.85 * by + 0.03 * trem;
            H[HX] = 0.0 - 0.12 * w * (1 - sl) + 0.1 * sh;
            H[HZ] = d > 0 ? 0.1 * Math.exp(-d * 3) * Math.sin(d * 17) : 0;
            H[HY2] = 0.3 * by;
            H[TH] += 0.012 * trem + 0.05 * sh + 0.03 * ft * (1 - rec);
            H[RP] += 0.03 * trem;
            place(P, H, 0, sstep(SM_REC0 + 0.8, SM_REC1, t), 1);
            P.body.sy -= 0.03 * w * (1 - sl);
            P.offY += d > 0 ? -0.5 * Math.exp(-d * 8) * Math.abs(Math.cos(d * 20)) : 0;
        });

        // ---- afraid: crouched, arms crossed over its head, shaking ----
        add("cower", 1.2, true, 0, (P, t, c) -> {
            double sh = Math.sin(S2 * t * 6), sh2 = Math.sin(S2 * t * 6 + 1.3), flinch = 0.5 + 0.5 * Math.sin(S2 * t / 1.2);
            sPose(P, pose("th", 0.45 + 0.03 * sh2, "rp", 2.9, "lp", 2.9, "rs", -0.35, "ls", -0.35, "rf", -2.0, "lf", -2.0, "hx", 0.75, "bz", 0.05 * sh,
                    "by", 0.05 * sh2, "hz", 0.07 * sh, "rl", -0.5, "ll", -0.5));
            sGround(P, 0, 0, 1, 1, true);
            P.body.sy -= 0.03 * flinch;
            P.offX += 0.25 * sh;
        });

        // ---- the chest ----
        add("open-lid", 3.6, false, 0, (P, t, c) -> {
            Arm o = openWith(t - 0.3, true);
            applyChest(P, c.state, t, new Chest(o.arm, NONE, 0, 1 - o.arm.k, 1));
        });
        add("close-lid", 3.4, false, 0, (P, t, c) -> {
            Arm o = slamWith(t - 0.3, false);
            applyChest(P, c.state, t, new Chest(NONE, o.arm, 0, 1, 1 - o.arm.k));
        });
        add("stow-ore", 4.9, false, 0, (P, t, c) -> applyChest(P, c.state, t, stowState(t))).hold("tuck", 0, 2.0F);
        add("fetch-ore", 4.6, false, 0, (P, t, c) -> applyChest(P, c.state, t, fetchState(t))).hold("tuck", 3.0F, 1e9F);

        // ---- waiting for the chest ----
        add("wait-for-chest", 9, true, 0, (P, t, c) -> {
            double br = Math.sin(S2 * t / 3.3) * 0.5 + 0.5;
            double toC = win(t, -1.6, -0.9, 1.0, 1.7) + win(t, 7.4, 8.1, 10.0, 10.7) + win(t, 3.0, 3.6, 4.6, 5.2);
            double away = win(t, 1.4, 2.2, 2.8, 3.5) - win(t, 5.4, 6.0, 6.8, 7.4) * 0.6;
            double sigh = win(t, 5.0, 5.8, 6.4, 7.2), shift = win(t, 2.8, 3.2, 3.8, 4.2) + win(t, 6.8, 7.1, 7.5, 7.9);
            applyChest(P, c.state, t, new Chest(NONE, NONE, 1, 1, 1, 0.015 * br + 0.05 * sigh, 0.04 * br + 0.12 * toC - 0.1 * sigh, -0.35 * toC + 0.4 * away));
            P.body.sy += 0.02 * br - 0.03 * sigh;
            P.body.sx += 0.012 * br + 0.02 * sigh;
            P.body.sz += 0.012 * br + 0.02 * sigh;
            P.offX += 0.5 * shift * Math.sin(Math.PI * (t < 5 ? (t - 2.8) / 1.4 : (t - 6.8) / 1.1));
            double d = t - 7.3;
            P.offY -= 0.35 * (d > 0 ? win(d, 0, 0.05, 0.1, 0.35) : 0);
        }).hold("tuck");

        // ---- idling: a long, restless wait on its knuckles, ending in a chest-beating rear ----
        final double sc0 = 3.3, sc1 = 4.0, sc2 = 5.6, sc3 = 6.2, yawn0 = 6.6, up0 = 8.4, up1 = 9.9, beat0 = 10.2, bp = 0.45, dn0 = 13.9, dn1 = 15.2;
        final int nb = 8;
        final double beatEnd = beat0 + nb * bp;
        add("idle-wait", 18.0, true, 0, (P, t, c) -> {
            double br = Math.sin(S2 * t / 3.6), sway = Math.sin(S2 * t / 9);
            double up = sstep(up0, up1, t) * (1 - sstep(dn0, dn1, t));
            double kS = sstep(sc0, sc1, t) * (1 - sstep(sc2, sc3, t)), scr = win(t, sc1 + 0.1, sc1 + 0.3, sc2 - 0.3, sc2);
            double yawn = win(t, yawn0, yawn0 + 0.8, yawn0 + 1.3, yawn0 + 2.0);
            double kb = win(t, up0 + 0.9, beat0, beatEnd, beatEnd + 0.4), tb = t - beat0, amp = sstep(0, 0.3, tb) * (1 - sstep(beatEnd - beat0 - 0.5, beatEnd - beat0, tb));
            double[] H = hunch(S_IZ + 0.4 * sway, S_IZ - 0.4 * sway, 0.04 * sway, 0.035 * sway, 0.3 + 0.7 * (1 - kS), 1);
            double[] o = blend(H, S_UPRIGHT, up);
            o[TH] += 0.012 * br + 0.08 * yawn * (1 - up);
            o[HP] += 0.01 * br;
            o[BZ] += 0.03 * Math.sin(S2 * t / 7) * (1 - up);
            o[HY] = 0.5 * win(t, 0.6, 1.5, 2.0, 2.8) + 0.05 * sway - 0.45 * win(t, 15.4, 16.2, 16.6, 17.4) + 0.4 * win(t, 16.8, 17.2, 17.5, 17.9);
            o[HX] = 0.07 * br - 0.1 * win(t, 1.0, 1.6, 2.4, 3.0) - 0.5 * yawn + 0.05 * Math.sin(t * 1.3);
            o[HZ] = 0.04 * Math.sin(S2 * t / 5.2) - 0.16 * kS;
            double roar = win(t, beat0 - 0.2, beat0 + 0.3, beatEnd - 0.3, beatEnd + 0.3);
            o[HX] += -0.4 * roar * up + 0.07 * Math.abs(Math.sin(S2 * tb / (bp * 2))) * kb;
            Aim R = new Aim(Math.max(kS * (1 - up), kb * amp > 0 ? kb : 0), new double[]{5.2 + 0.9 * scr * Math.sin(t * 15), 17.6 + 0.6 * scr * Math.sin(t * 15 + 1.2), -8.5}), L = NONE;
            double wr = 1 - kS;
            if (kb > 0.001) {
                double cyc = S2 * tb / (bp * 2), yR = 16.6 + 2.9 * Math.cos(cyc) * amp, yL = 16.6 + 2.9 * Math.cos(cyc + Math.PI) * amp;
                R = new Aim(kb, new double[]{5.6, yR, -9.6 + 0.6 * Math.cos(cyc)});
                L = new Aim(kb, new double[]{-5.6, yL, -9.6 + 0.6 * Math.cos(cyc + Math.PI)});
                double hs = Math.exp(-7 * (((tb % bp) + bp) % bp)) * amp;
                o[TH] += 0.05 * hs;
                P.body.sy -= 0.03 * hs;
            }
            sPose(P, o);
            double th = P.body.rx;
            double[] x;
            if (R.k > 0.001) {
                double[] xr = armIk(P, c.state, t, false, R.t);
                x = new double[]{lerp(o[RP], xr[0], R.k), lerp(o[RS], xr[1], R.k), lerp(o[RF], xr[2], R.k)};
                setArm(P, th, x, false);
            }
            if (L.k > 0.001) {
                double[] xl = armIk(P, c.state, t, true, L.t);
                x = new double[]{lerp(o[LP], xl[0], L.k), lerp(o[LS], xl[1], L.k), lerp(o[LF], xl[2], L.k)};
                setArm(P, th, x, true);
            }
            double a = 1 - up;
            sGround(P, a * wr, a, 0.3 + 0.7 * up, 0.3 + 0.7 * up, true);
            P.body.sy += 0.015 * br * (1 - up) + 0.03 * yawn;
            P.offY -= kb > 0.1 ? 0.5 * Math.exp(-7 * (((tb % bp) + bp) % bp)) * amp : 0;
        });

        // ---- offerings ----
        add("await-offering", 6.5, true, 0, (P, t, c) -> {
            double up = sstep(0.0, 1.0, t) * (1 - sstep(5.6, 6.4, t)), pr = win(t, 1.4, 1.9, 2.1, 2.7) + win(t, 3.5, 4.0, 4.2, 4.8), sw = Math.sin(S2 * t / 3.2);
            double[] H = hunch(S_IZ, S_IZ, 0, 0, 1, 1);
            double[] U = with(S_TRAY, "th", S_TRAY[TH] + 0.16 * pr, "rp", S_TRAY[RP] + 0.12 * pr, "bz", 0.025 * sw, "hy", 0.35 * win(t, 1.0, 1.7, 2.5, 3.2) - 0.35 * win(t, 3.4, 4.1, 4.9, 5.6), "hx", 0.1 - 0.12 * pr);
            place(P, blend(H, U, up), up, 1, 1);
            P.body.sy -= 0.03 * pr;
        }).hold("both", 0.9F, 1e9F);
        add("receive-offering", 7.0, false, 0, (P, t, c) -> {
            double up = sstep(0.0, 1.0, t) * (1 - sstep(5.8, 6.8, t)), hug = sstep(1.9, 2.8, t), rock = win(t, 3.0, 3.5, 4.5, 5.0) * Math.sin((t - 3.0) * 4.2), bob = win(t, 3.0, 3.4, 4.6, 5.0) * Math.abs(Math.sin((t - 3.0) * 4.2));
            double[] H = hunch(S_IZ, S_IZ, 0, 0, 1, 1);
            double[] U = blend(with(S_TRAY, "hx", 0.0), with(S_HUG, "hx", 0.3), hug);
            U[BZ] = 0.1 * rock;
            U[BY] = 0.06 * rock;
            U[HY] = 0.1 * rock;
            place(P, blend(H, U, up), up, 1, 1);
            P.offY += 0.5 * bob * up;
            P.body.sy -= 0.02 * bob;
        }).hold("both", 1.5F, 5.2F);
    }
}
