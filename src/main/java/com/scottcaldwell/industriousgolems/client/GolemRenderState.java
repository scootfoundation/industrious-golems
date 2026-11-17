package com.scottcaldwell.industriousgolems.client;

import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;

/** What the model needs to know about a work golem: the held item (from the parent) and the head-shake timer. */
public class GolemRenderState extends ArmedEntityRenderState {
    public int unhappy;
}
