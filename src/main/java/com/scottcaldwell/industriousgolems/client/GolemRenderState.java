package com.scottcaldwell.industriousgolems.client;

import com.scottcaldwell.industriousgolems.client.anim.Pose;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** What the model needs to know about a work golem: the held item (from the parent), the pose its clip gives it, and how it is held. */
public class GolemRenderState extends ArmedEntityRenderState {
    public int unhappy;
    /** Out of charge: the golem hangs its head and does not move. */
    public boolean dormant;
    /** The pose built from the golem's current animation clip. */
    public final Pose pose = new Pose();
    /** How the carried item is held right now: "front", "hand", "both", "tuck", or null (in the hand). */
    public String hold;
    /** What the carried item is, see {@code ItemKind} (null: nothing). */
    public String itemKind;
    public float headYaw;
    /** What a dormant golem is asking for, floating above its head: the item to draw (null when it asks for nothing) and its flat sprite, if it has one. */
    public final ItemStackRenderState need = new ItemStackRenderState();
    public boolean asking;
    public @Nullable TextureAtlasSprite needSprite;
    /** The name of the item asked for, shown above it while the golem is looked at. */
    public @Nullable Component needName;
}
