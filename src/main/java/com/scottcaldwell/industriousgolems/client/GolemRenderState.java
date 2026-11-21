package com.scottcaldwell.industriousgolems.client;

import com.scottcaldwell.industriousgolems.client.anim.Pose;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;

/** What the model needs to know about a work golem: the held item (from the parent), the pose its clip gives it, and how it is held. */
public class GolemRenderState extends ArmedEntityRenderState {
    public int unhappy;
    /** Out of charge: the golem hangs its head and does not move. */
    public boolean dormant;
    /** The pose built from the golem's current animation clip. */
    public final Pose pose = new Pose();
    /** How the carried item is held right now: "front", "hand", "both", "tuck", or null (in the hand). */
    public String hold;
    public float headYaw;
}
