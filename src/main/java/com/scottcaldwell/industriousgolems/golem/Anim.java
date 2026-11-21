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
    public static final float M_PICK_LEN = 3.2F, M_PICK_GRAB = 1.3F, M_PLANT_LEN = 2.8F, M_PLANT_AT = 1.2F, M_RECEIVE_LEN = 3.8F;
    /** The chest sequences: length, when the lid opens, when the item goes in / comes out, when the lid shuts. */
    public static final float M_PUT_LEN = 9.6F, M_PUT_OPEN = 1.05F, M_PUT_DROP = 1.8F + 0.95F + 0.55F, M_PUT_CLOSE = 1.8F + 2.1F + 4.1F + 0.8F;
    public static final float M_FETCH_LEN = 12.3F, M_FETCH_OPEN = 1.05F, M_FETCH_TAKE = 1.8F + 4.1F + 3.5F, M_FETCH_CLOSE = 1.8F + 4.1F + 4.8F + 0.8F;
    /** How far the golem stands from the chest's centre, in blocks (13 pixels), facing it. */
    public static final double M_CHEST_DIST = 13.0 / 16.0;

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

    public static Pick pick(String profession) {
        return switch (profession) {
            case "lumberjack" -> new Pick(M_PICK_UP, M_PICK_LEN, M_PICK_GRAB, 8.5 / 16.0);
            default -> new Pick(M_PICK_UP, M_PICK_LEN, M_PICK_GRAB, 8.5 / 16.0);
        };
    }

    public static Chest chest(String profession) {
        return switch (profession) {
            case "lumberjack" -> new Chest(M_PUT_IN, M_PUT_LEN, M_PUT_OPEN, 1.8F + 0.95F, M_PUT_DROP, M_PUT_CLOSE,
                    M_FETCH, M_FETCH_LEN, M_FETCH_OPEN, 1.8F + 4.1F + 2.5F, M_FETCH_TAKE, M_FETCH_CLOSE, M_CHEST_DIST, 15.0 / 16.0, 7.0 / 16.0);
            default -> new Chest(M_PUT_IN, M_PUT_LEN, M_PUT_OPEN, 1.8F + 0.95F, M_PUT_DROP, M_PUT_CLOSE,
                    M_FETCH, M_FETCH_LEN, M_FETCH_OPEN, 1.8F + 4.1F + 2.5F, M_FETCH_TAKE, M_FETCH_CLOSE, M_CHEST_DIST, 15.0 / 16.0, 7.0 / 16.0);
        };
    }

    /** The clip played while the golem is handed an offering after running down, and how long it takes; null when there is none. */
    public record Receive(String clip, float len) { }

    public static Receive receive(String profession) {
        return switch (profession) {
            case "lumberjack" -> new Receive(M_RECEIVE, M_RECEIVE_LEN);
            default -> null;
        };
    }
}
