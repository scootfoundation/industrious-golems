package com.scottcaldwell.industriousgolems.client.anim;

import static com.scottcaldwell.industriousgolems.client.anim.Pose.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stand-in clips for kinds whose workshop animations are not ported yet: a plain walk and a still idle. */
public final class BasicAnims {
    private BasicAnims() { }

    /**
     * What a charged golem does between jobs while it works out what to do next: stands and looks about, glancing one way, then the
     * other, with a peek down at the ground. It never sits, so starting a job straight after costs nothing.
     */
    public static final Clip LOOK_AROUND = new Clip("look-around", 9.0F, true, 0, (P, t, c) -> {
        double yaw = kf(t, k(0, 0), k(0.7, 0.85), k(1.9, 0.85), k(2.5, -0.7), k(3.9, -0.7), k(4.5, 0), k(6.0, 0), k(6.6, 0.5), k(7.4, 0.5), k(8.0, -0.3), k(9, 0));
        double pitch = kf(t, k(0, 0), k(0.7, -0.15), k(1.9, -0.15), k(2.5, 0.05), k(3.9, 0.05), k(4.5, 0.3), k(5.4, 0.3), k(6.0, 0), k(9, 0));
        double breath = sin(t * 1.4);
        P.head.ry += yaw;
        P.head.rx += pitch;
        P.body.ry += 0.25 * yaw;
        P.body.rx += 0.012 * breath;
        P.head.rz += 0.04 * sin(t * 0.9);
    });

    public static Kit kit(boolean hips, double armTilt) {
        Map<String, Clip> m = new LinkedHashMap<>();
        m.put("walk", new Clip("walk", 1.0F, true, 20, (P, t, c) -> {
            double s = sin(t * Math.PI * 2);
            P.legs(s, 0.5);
            P.rarm.rx += -s * 0.5;
            P.larm.rx += s * 0.5;
        }));
        m.put("idle", new Clip("idle", 4.0F, true, 0, (P, t, c) -> P.head.rx += 0.02 * sin(t)));
        return new Kit(m, "walk", "walk", "idle", "idle", hips, armTilt);
    }
}
