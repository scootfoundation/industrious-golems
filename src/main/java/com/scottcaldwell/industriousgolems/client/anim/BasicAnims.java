package com.scottcaldwell.industriousgolems.client.anim;

import static com.scottcaldwell.industriousgolems.client.anim.Pose.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** Stand-in clips for kinds whose workshop animations are not ported yet: a plain walk and a still idle. */
public final class BasicAnims {
    private BasicAnims() { }

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
