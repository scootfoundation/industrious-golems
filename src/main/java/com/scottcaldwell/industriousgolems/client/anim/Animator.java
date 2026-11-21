package com.scottcaldwell.industriousgolems.client.anim;

import com.scottcaldwell.industriousgolems.golem.Anim;

/** Chooses a clip for a golem and evaluates it into a {@link Pose}. */
public final class Animator {
    private Animator() { }

    public static final Kit MUSHROOM = new Kit(MushroomAnims.CLIPS, Anim.M_WALK, Anim.M_WALK_ITEM, Anim.M_NAP, Anim.M_AWAIT, false, 0);

    /**
     * @param clipId   the clip the server asked for, or null for the default (walk / idle / waiting)
     * @param clipTime seconds since that clip began
     * @param moving   whether the golem is walking
     * @param walkDist how far the golem has walked, in 1/16 blocks (drives the stride)
     */
    public static String evaluate(Kit kit, Pose pose, String clipId, double clipTime, boolean moving, double walkDist, double ageSeconds,
                                  boolean waiting, String item, AnimState state) {
        pose.clear();
        Clip clip = kit.get(clipId);
        double t = clipTime;
        if (clip == null) {
            if (waiting && kit.get(kit.await()) != null) {
                clip = kit.get(kit.await());
                t = ageSeconds;
            } else if (moving) {
                clip = item != null && kit.get(kit.walkItem()) != null ? kit.get(kit.walkItem()) : kit.get(kit.walk());
                t = clip.move > 0 ? walkDist / clip.move : ageSeconds;
            } else {
                clip = kit.get(kit.idle());
                t = ageSeconds;
            }
        }
        if (kit.armTilt() != 0) {
            pose.rarm.rz += (float) kit.armTilt();
            pose.larm.rz -= (float) kit.armTilt();
        }
        Clip.Ctx c = new Clip.Ctx();
        c.item = item;
        c.state = state;
        c.len = clip.len;
        clip.eval(pose, t, c);
        if (clip.segs.isEmpty() && clip.loop) t = clip.len > 0 ? t % clip.len : t;
        String hold = clip.holdAt(t);
        // the cap lags the head and body, then settles (the mushroom's quirk)
        if (state != null && kit == MUSHROOM) lagCap(pose, state, ageSeconds);
        if (kit.hasHips()) {
            pose.hips.rx += pose.body.rx;
            pose.hips.rz += pose.body.rz;
            pose.hips.y += pose.body.y;
        }
        return hold;
    }

    private static void lagCap(Pose P, AnimState s, double now) {
        double dt = Double.isNaN(s.lastTime) ? 0.05 : Math.max(0, Math.min(0.05, now - s.lastTime));
        s.lastTime = now;
        double kk = 1 - Math.exp(-dt * 9);
        double hx = P.head.rx + P.body.rx, hz = P.head.rz + P.body.rz, hy = P.body.y;
        s.capX += (hx - s.capX) * kk;
        s.capZ += (hz - s.capZ) * kk;
        s.capY += (hy - s.capY) * kk;
        P.cap.rx += (s.capX - hx) * 1.4;
        P.cap.rz += (s.capZ - hz) * 1.4;
        P.cap.y += (s.capY - hy) * 0.9;
    }
}
