package com.scottcaldwell.industriousgolems.golem;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** Turning a golem square to something, and the golem's own frame (right, forward) that the animation clips are written in. */
public final class Face {
    private Face() { }

    /** Turns the whole golem, body and head, to face the point. */
    public static void toward(WorkGolem golem, Vec3 target) {
        float yaw = (float) (Mth.atan2(-(target.x - golem.getX()), target.z - golem.getZ()) * Mth.RAD_TO_DEG);
        golem.setYRot(yaw);
        golem.yRotO = yaw;
        golem.setYBodyRot(yaw);
        golem.yBodyRotO = yaw;
        golem.setYHeadRot(yaw);
        golem.yHeadRotO = yaw;
    }

    /** A point {@code right} blocks to the golem's right and {@code forward} blocks ahead of {@code origin}, for a golem facing {@code yawDeg}. */
    public static Vec3 local(Vec3 origin, float yawDeg, double right, double forward) {
        double a = yawDeg * Mth.DEG_TO_RAD;
        double fx = -Math.sin(a), fz = Math.cos(a);
        double rx = -Math.cos(a), rz = -Math.sin(a);
        return new Vec3(origin.x + rx * right + fx * forward, origin.y, origin.z + rz * right + fz * forward);
    }
}
