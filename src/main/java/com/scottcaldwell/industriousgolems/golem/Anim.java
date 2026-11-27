package com.scottcaldwell.industriousgolems.golem;

/**
 * Names and timings shared by the client's animation clips and the server's behaviours, so a task can start a clip and
 * act at the moment the clip's blow lands. The clips themselves are in {@code client.anim}; they are a port of the golem
 * animation workshop.
 */
public final class Anim {
    private Anim() { }

    // ---- mushroom golem (lumberjack) ----
    public static final String M_WALK = "waddle-squelch";
    public static final String M_WALK_ITEM = "walk-item";
    public static final String M_NAP = "nap";
    public static final String M_AWAIT = "await-offering";
    public static final String M_RECEIVE = "receive-offering";
    public static final String M_SNEEZE_VANISH = "sneeze-vanish";
    public static final String M_SNEEZE_APPEAR = "sneeze-appear";
    public static final String M_BREAK_BELOW = "break-below";
    public static final String M_PICK_UP = "pick-up";
    public static final String M_PLANT = "plant";
    public static final String M_PUT_IN = "put-in";
    public static final String M_FETCH = "fetch";
    public static final String M_SIT_FULL = "sit-with-item";

    /** Seconds into the clip. */
    public static final float M_VANISH_LEN = 1.7F, M_VANISH_AT = 1.6F, M_APPEAR_LEN = 1.8F, M_BREAK_LEN = 1.7F, M_BREAK_AT = 0.9F;
    public static final float M_PICK_LEN = 3.2F, M_PLANT_LEN = 2.8F, M_PLANT_AT = 1.2F, M_RECEIVE_LEN = 3.8F;
    // ---- how each kind picks things up and visits a chest (timings in seconds into the clip) ----

    /**
     * Picking something up off the ground.
     *
     * @param clip  the clip to play
     * @param len   its length
     * @param grab  when the item reaches the hand
     * @param dist  how far in front of the golem the item lies, in blocks
     */
    public record Pick(String clip, float len, float grab, double dist) { }

    /**
     * A visit to the chest: one clip each for putting the carried stack away and for fetching a supply.
     * A negative time means the event does not happen in that clip.
     *
     * @param putRelease  when the hand lets go of the carried stack     @param putDrop  when it lands in the chest
     * @param fetchTake   when the stack appears in the hand            @param fetchThrow  when it is thrown out onto the grass (negative: it stays in hand)
     * @param dist        how far from the chest's centre the golem stands, in blocks
     * @param landX    where a thrown stack lands, in the golem's frame: blocks to its right
     * @param landZ    and blocks ahead
     */
    public record Chest(String put, float putLen, float putOpen, float putRelease, float putDrop, float putClose,
                        String fetch, float fetchLen, float fetchOpen, float fetchTake, float fetchThrow, float fetchClose,
                        double dist, double landX, double landZ) { }

    // ---- stone golem (miner) ----
    public static final String S_MINE = "mine-block", S_COWER = "cower", S_FUME = "fume";
    public static final float S_MINE_LEN = 7.0F, S_MINE_HIT = 3.55F, S_RECEIVE_LEN = 7.0F;

    // ---- terracotta golem (herder) ----
    public static final String T_RECEIVE = "receive-offering";
    public static final float T_RECEIVE_LEN = 6.0F, T_CULL_LEN = 6.2F, T_CULL_HIT = 1.6F, T_FEED_LEN = 9.0F, T_FEED_BITE = 4.4F, T_SHEAR_LEN = 10.2F, T_SHEAR_AT = 4.6F;
    /** Where the animals stand for each job, in the golem's frame (blocks), as the clips are written for. */
    public static final double T_CULL_PHI = 1.35;
    public static final double T_FEED_NX = 16.0, T_SHEAR_X = 17.5;
    /** Heights of an animal's back and nose in pixels, as the clips see them: sheep, cow, pig. */
    public static final double[][] T_ANIMALS = {{18, 13}, {20, 15}, {11, 8}};

    // ---- hay golem (farmer) ----
    public static final String H_HARVEST = "harvest", H_REPLANT = "replant", H_PICK_UP = "pick-up", H_RECEIVE = "receive-offering";
    public static final float H_HARVEST_LEN = 4.4F, H_HARVEST_AT = 2.55F + 0.2F, H_REPLANT_LEN = 5.8F, H_REPLANT_AT = 2.7F + 0.2F, H_RECEIVE_LEN = 5.0F;
    /** The crop block's centre is 12 pixels ahead of the golem. */
    public static final double H_CROP_DIST = 12.0 / 16.0;

    /** Read from {@code data/industriousgolems/golem_timings/<profession>.json} (see {@link Timings}). */
    public static Pick pick(String profession) {
        return Timings.pick(profession);
    }

    public static Chest chest(String profession) {
        return Timings.chest(profession);
    }

    /** The clip played while the golem is handed an offering after running down, and how long it takes; null when there is none. */
    public record Receive(String clip, float len) { }

    public static Receive receive(String profession) {
        return switch (profession) {
            case "lumberjack" -> new Receive(M_RECEIVE, M_RECEIVE_LEN);
            case "farmer" -> new Receive(H_RECEIVE, H_RECEIVE_LEN);
            case "herder" -> new Receive(T_RECEIVE, T_RECEIVE_LEN);
            case "miner" -> new Receive("receive-offering", S_RECEIVE_LEN);
            default -> null;
        };
    }
}
