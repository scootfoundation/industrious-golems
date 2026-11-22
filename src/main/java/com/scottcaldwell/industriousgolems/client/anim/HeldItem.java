package com.scottcaldwell.industriousgolems.client.anim;

/**
 * Where a held item is drawn. After {@code translateToHand} the item layer turns the frame and steps the item (1, 2, -10) pixels
 * along it, which lands it {@link #LAYER} from the point the model left the pose at: 10 pixels <em>down</em>. A "front" position (an
 * item held in front of the body rather than in a hand) is therefore given by where the item should end up, minus that step.
 * Forgetting the step put the mushroom's item underground.
 */
public final class HeldItem {
    private HeldItem() { }

    /** The item layer's own step from the pose it is handed, in pixels (x, y down, z forward is negative), for the right hand. */
    public static final float[] LAYER = {-1F, 10F, -2F};

    /** The pose offset (body pixels, y down) that leaves an item at {@code x, y, z} relative to the body's pivot. */
    public static float[] inFront(float x, float y, float z) {
        return new float[]{x - LAYER[0], y - LAYER[1], z - LAYER[2]};
    }

    /** Where an item ends up relative to the body's pivot, given the pose offset. */
    public static float[] landing(float[] front) {
        return new float[]{front[0] + LAYER[0], front[1] + LAYER[1], front[2] + LAYER[2]};
    }

    /** The mushroom golem's: in front of its belly (its body pivot is 2 pixels above the ground, the body 4 pixels tall and 6 deep). */
    public static final float[] MUSHROOM_FRONT = inFront(0F, -2F, -6F);
}
