package com.scottcaldwell.industriousgolems.client;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;

/** What the model needs to know about a work golem: the held item (from the parent), the head-shake timer and dormancy. */
public class GolemRenderState extends ArmedEntityRenderState {
    public int unhappy;
    /** Out of charge: the golem hangs its head and does not move. */
    public boolean dormant;
}
