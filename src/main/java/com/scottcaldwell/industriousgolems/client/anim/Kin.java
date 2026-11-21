package com.scottcaldwell.industriousgolems.client.anim;

import org.joml.Matrix4d;
import org.joml.Vector3d;

/**
 * Forward kinematics for the stone golem's rig, so its clips can ask where a shoulder or a fist is and how low a limb reaches, as the workshop's do of its
 * scene. Model coordinates (y down, the golem's feet at y = 0); {@link #world} turns a point into the workshop's frame (x right, y up, z forward is negative).
 */
final class Kin {
    private Kin() { }

    static final double LEG = 7.5;

    private static Matrix4d part(Pose.Part p, double px, double py, double pz) {
        return new Matrix4d().translate(px + p.x, py + p.y, pz + p.z).rotateZYX(p.rz, p.ry, p.rx).scale(1 + p.sx, 1 + p.sy, 1 + p.sz);
    }

    static Matrix4d body(Pose P) {
        return part(P.body, 0, -LEG, 0);
    }

    static Matrix4d arm(Pose P, boolean left) {
        return body(P).mul(part(left ? P.larm : P.rarm, left ? 12 : -12, -13, 0));
    }

    static Matrix4d fore(Pose P, boolean left) {
        return arm(P, left).mul(part(left ? P.lfore : P.rfore, left ? 3.5 : -3.5, 8, 0));
    }

    /** The workshop-frame position of a point in a part's own space. */
    static Vector3d world(Matrix4d m, double x, double y, double z) {
        Vector3d v = m.transformPosition(new Vector3d(x, y, z));
        return new Vector3d(-v.x, -v.y, v.z);
    }

    /** Where the shoulder is (its pivot). */
    static Vector3d shoulder(Pose P, boolean left) {
        return world(arm(P, left), 0, 0, 0);
    }

    /** Where the fist is: the middle of the forearm's lower end. */
    static Vector3d fist(Pose P, boolean left) {
        return world(fore(P, left), 0, 5, 0);
    }

    private static double lowestOf(Matrix4d m, double[] b) {
        double low = Double.MAX_VALUE;
        for (int i = 0; i < 8; i++) {
            Vector3d v = world(m, b[0] + ((i & 1) != 0 ? b[3] : 0), b[1] + ((i & 2) != 0 ? b[4] : 0), b[2] + ((i & 4) != 0 ? b[5] : 0));
            low = Math.min(low, v.y);
        }
        return low;
    }

    private static final double[] ARM_BOX = {-7, -1, -3.5, 7, 10, 7}, LARM_BOX = {0, -1, -3.5, 7, 10, 7}, FORE_BOX = {-5.5, 0, -5.5, 11, 8, 11}, LEG_BOX = {-3, 0, -3, 6, LEG, 6};

    /** The lowest point of the arm and forearm. */
    static double lowestArm(Pose P, boolean left) {
        return Math.min(lowestOf(arm(P, left), left ? LARM_BOX : ARM_BOX), lowestOf(fore(P, left), FORE_BOX));
    }

    static double lowestLeg(Pose P, boolean left) {
        return lowestOf(part(left ? P.lleg : P.rleg, left ? 3 : -3, -LEG, 0), LEG_BOX);
    }
}
