package com.scottcaldwell.industriousgolems.client.anim;

/**
 * One frame of a golem's pose, built by a clip. Everything is additive on the rest pose, exactly as in the golem animation
 * workshop: rotations in radians, positions in model pixels (negative y is up), scales as a change from 1. The {@code off*}
 * fields move the whole golem in the workshop's frame (units of 1/16 block; y up; z negative is forward; the golem faces -z).
 */
public final class Pose {

    public static final class Part {
        public float rx, ry, rz, x, y, z, sx, sy, sz;

        void clear() {
            rx = ry = rz = x = y = z = sx = sy = sz = 0.0F;
        }
    }

    public final Part body = new Part(), head = new Part(), rarm = new Part(), larm = new Part(), rleg = new Part(),
            lleg = new Part(), hips = new Part(), cap = new Part(), rfore = new Part(), lfore = new Part();
    public float offX, offY, offZ, offRy;
    public float scale = 1.0F;
    public boolean hide;
    /** Where the ground is under the golem, in 1/16 blocks (used while it stands on top of something). */
    public float floor;

    public Part[] all() {
        return new Part[]{body, head, rarm, larm, rleg, lleg, hips, cap, rfore, lfore};
    }

    public void clear() {
        for (Part p : all()) p.clear();
        offX = offY = offZ = offRy = floor = 0.0F;
        scale = 1.0F;
        hide = false;
    }

    // ---- the workshop's helpers (doubles throughout, so the ported clips read like the originals) ----------------

    public static double sstep(double a, double b, double x) {
        double t = Math.max(0.0, Math.min(1.0, (x - a) / (b - a)));
        return t * t * (3.0 - 2.0 * t);
    }

    /** Rises over a..b, falls over c..d. */
    public static double win(double t, double a, double b, double c, double d) {
        return sstep(a, b, t) * (1.0 - sstep(c, d, t));
    }

    public static double lerp(double a, double b, double x) {
        return a + (b - a) * x;
    }

    public static double clamp01(double x) {
        return Math.max(0.0, Math.min(1.0, x));
    }

    public static double sin(double x) {
        return Math.sin(x);
    }

    public static double cos(double x) {
        return Math.cos(x);
    }

    public static double abs(double x) {
        return Math.abs(x);
    }

    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    public static double pow(double a, double b) {
        return Math.pow(a, b);
    }

    public static double exp(double a) {
        return Math.exp(a);
    }

    /** Both arms swing by x and splay out by z (the left arm mirrored). */
    public void arms(double x, double z) {
        rarm.rx += x;
        larm.rx += x;
        rarm.rz += z;
        larm.rz -= z;
    }

    /** Legs swing opposite ways: s times amp. */
    public void legs(double s, double amp) {
        rleg.rx += s * amp;
        lleg.rx -= s * amp;
    }

    /** Easing used by the workshop's keyframe helper: 'o' out, 'i' in, otherwise smooth. */
    public static double ease(double k, char m) {
        k = clamp01(k);
        return m == 'o' ? 1 - (1 - k) * (1 - k) : (m == 'i' ? k * k : k * k * (3 - 2 * k));
    }

    /** Keys are {time, value} with an optional third entry: the easing char (as a number) into that key. */
    public static double kf(double u, double[]... keys) {
        if (u <= keys[0][0]) return keys[0][1];
        for (int i = 1; i < keys.length; i++) {
            if (u <= keys[i][0]) {
                double[] a = keys[i - 1], b = keys[i];
                char m = b.length > 2 ? (char) b[2] : 's';
                return a[1] + (b[1] - a[1]) * ease((u - a[0]) / (b[0] - a[0]), m);
            }
        }
        return keys[keys.length - 1][1];
    }

    public static double[] k(double t, double v) {
        return new double[]{t, v, 's'};
    }

    public static double[] k(double t, double v, char m) {
        return new double[]{t, v, m};
    }
}
