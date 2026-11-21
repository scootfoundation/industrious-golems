package com.scottcaldwell.industriousgolems.client.anim;

/** Per-golem animation memory on the client: whatever a clip needs from one frame to the next. */
public final class AnimState {
    /** The mushroom cap's lagging tilt and bounce. */
    public double capX, capY, capZ;
    public double lastTime = Double.NaN;
}
