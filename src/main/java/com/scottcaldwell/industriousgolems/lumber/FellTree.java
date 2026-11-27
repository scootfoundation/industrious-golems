package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.golem.Hop;
import com.scottcaldwell.industriousgolems.behavior.ClipRun;
import com.scottcaldwell.industriousgolems.behavior.Task;
import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.PickUp;
import com.scottcaldwell.industriousgolems.behavior.Approach;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.behavior.TaskState;
import net.minecraft.core.BlockPos;
import com.scottcaldwell.industriousgolems.golem.Anim;
import com.scottcaldwell.industriousgolems.golem.Face;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;

/**
 * Fells a natural tree from the top down. The lumberjack walks to the foot of the tree, sneezes and vanishes in a puff
 * of spores, and reappears standing on top of the crown. From there it mines straight down through the trunk's column,
 * bare-handed, one block a swing (dropped as for a player and carried off a stack at a time, see PickUp): first any log
 * of the tree level with it within {@link #LOG_REACH} blocks across, then the block under its feet (leaves and logs
 * alike), dropping one, and again, until it lands on the ground. Runs in the exclusive task activity.
 */
public class FellTree extends TaskRunner<FellTree.FellPlan> {

    /** How near the golem gets to the foot of the tree before it sneezes. */
    private static final double SNEEZE_DIST = 3.5;
    /** Ticks without getting any nearer the foot of the tree before giving up. */
    private static final int STALL_TICKS = 600;

    /** How far across it reaches for a log level with it, which it breaks before the block under its feet. */
    private static final double LOG_REACH = 3.0;

    /** Most sneezes (extra visits after the first) in one felling. */
    private static final int MAX_VISITS = 24;
    /** How far above a log it looks for room to stand. */
    private static final int CLEARANCE = 10;

    private enum State { WALK, SNEEZE, APPEAR, BREAK, DESCEND, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("fell-tree", "Lumberjack: fell a tree",
            "Mushroom golem, empty-handed. Walks to the foot of a tree, sneezes, vanishes and reappears on top of the crown, then mines straight down "
                    + "through the trunk. Branches and extra trunks are felled the same way: each time he lands with logs still standing he sneezes up onto the highest.",
            State.class)
            .start(State.WALK)
            .state(State.WALK, "Walking to within 3.5 blocks of the foot of the tree.")
            .state(State.SNEEZE, "Sneeze-and-vanish clip; a puff of spores at the end.")
            .state(State.APPEAR, "Teleported onto the crown; the appear clip plays.")
            .state(State.BREAK, "The break clip playing; at the hit moment one block breaks: a log of the tree level with him within 3 blocks across if there is one, else the block below.")
            .state(State.DESCEND, "Swing done and the block is gone: waiting to land on the next block down.")
            .state(State.DONE, "(end) Task over. If interrupted up in the tree he poofs back to the ground.")
            .edge(State.WALK, State.SNEEZE, "within 3.5 blocks of the tree and there is room on top of the crown")
            .edge(State.WALK, State.DONE, "no room on top of the crown, or no nearer for 30 s (tree ignored for 2.5 min)")
            .edge(State.SNEEZE, State.APPEAR, "vanish moment reached: teleports onto the crown")
            .edge(State.APPEAR, State.BREAK, "appear clip ends")
            .edge(State.BREAK, State.DESCEND, "break clip ends")
            .edge(State.DESCEND, State.BREAK, "landed, and a log is level with him within reach, or a log or leaf is directly below")
            .edge(State.DESCEND, State.SNEEZE, "landed on something that is not part of the tree, but logs of the tree remain (a branch, another trunk): vanishes again to reach them")
            .edge(State.DESCEND, State.DONE, "landed on something that is not part of the tree and no log of it is left (or none can be stood over)");

    /** The tree chosen by {@link #plan}: chop {@code logs}, then replant at {@code replant}. */
    public record FellPlan(List<BlockPos> logs, BlockPos replant) implements Task {
        @Override public java.util.Collection<?> claims() { return java.util.List.of(replant); }
    }

    private final List<BlockPos> remaining = new ArrayList<>();
    private final Machine.Run<State> machine = GRAPH.run();
    private ClipRun clip;
    private Hop hop;
    private final Approach approach = new Approach(STALL_TICKS);
    /** The one block this swing breaks, or null. */
    private BlockPos target;
    /** The spot on top of the crown the golem pops out at (its feet), or null when there is none. */
    private BlockPos top;
    /** Sneezes so far: a bound, so a tree that will not come down cannot keep him at it forever. */
    private int visits;
    /** Where the golem stood on the ground when it first sneezed: where it is put back if the felling is cut short up in the tree. */
    private Vec3 ground;
    /** Ticks spent waiting to land after a swing. */
    private int falling;

    public FellTree() {
        super(FellPlan.class, 12000); // a big tree takes a while: a swing for every log, and a sneeze up to each branch
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        remaining.clear();
        remaining.addAll(plan.logs());
        visits = 0;
        ground = null;
        falling = 0;
        aim(level);
        approach.reset();
        machine.reset();
        golem.setPickupSuspended(true); // the drops are left lying until the felling is done, then picked up in turn
    }

    /** The highest log (the one nearest the base among equals). */
    private static BlockPos highest(List<BlockPos> logs, BlockPos base) {
        BlockPos high = logs.get(0);
        for (BlockPos log : logs) {
            if (log.getY() > high.getY() || (log.getY() == high.getY() && log.distSqr(base) < high.distSqr(base))) high = log;
        }
        return high;
    }

    /**
     * The lowest spot over the log with room to stand, on leaves or logs of its column; null when there is none. There may be a gap on
     * the way up, where a log of the column has already been taken from the side.
     */
    private static BlockPos standAbove(ServerLevel level, BlockPos log) {
        for (BlockPos feet = log.above(); feet.getY() <= log.getY() + CLEARANCE; feet = feet.above()) {
            boolean solid = !level.getBlockState(feet).getCollisionShape(level, feet).isEmpty();
            if (solid && !isTreeBlock(level, feet)) return null; // roofed over by something that is not the tree
            if (!solid && isTreeBlock(level, feet.below())
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return feet;
        }
        return null;
    }

    /** Drops the logs that are gone, then aims at the highest one that can be stood over (giving up on those that cannot). */
    private boolean aim(ServerLevel level) {
        remaining.removeIf(pos -> !level.getBlockState(pos).is(BlockTags.LOGS));
        while (!remaining.isEmpty()) {
            BlockPos log = highest(remaining, plan.replant());
            top = standAbove(level, log);
            if (top != null) return true;
            remaining.remove(log);
        }
        top = null;
        return false;
    }

    private static boolean isTreeBlock(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES);
    }

    @Override
    protected void tick(ServerLevel level, WorkGolem golem, long time) {
        switch (machine.state()) {
            case WALK -> {
                BlockPos base = plan.replant();
                lookAt(golem, new BlockPosTracker(Vec3.atCenterOf(base)));
                double dx = golem.getX() - (base.getX() + 0.5), dz = golem.getZ() - (base.getZ() + 0.5);
                Approach.Outcome outcome = approach.walk(golem, new BlockPosTracker(base), 2, () -> Math.sqrt(dx * dx + dz * dz) <= SNEEZE_DIST);
                if (outcome != Approach.Outcome.ARRIVED) {
                    if (outcome == Approach.Outcome.UNREACHABLE) giveUp(golem, time);
                    return;
                }
                if (top == null) { giveUp(golem, time); return; } // no room to stand on top
                holdStill(golem);
                if (ground == null) ground = golem.position();
                sneeze(level, golem);
            }
            case SNEEZE -> {
                holdStill(golem);
                hop.tick(); // gone in a puff of spores, and out again on top of the crown
                if (hop.landed()) machine.to(State.APPEAR);
            }
            case APPEAR -> {
                holdStill(golem);
                if (hop.tick()) startBreak(level, golem);
            }
            case BREAK -> {
                holdStill(golem);
                if (clip.tick()) { machine.to(State.DESCEND); falling = 0; }
            }
            case DESCEND -> {
                holdStill(golem);
                if (!golem.onGround()) {
                    if (++falling > STALL_TICKS / 6) { golem.stopClip(); done(); } // hung up on something: let go
                    return;
                }
                if (nextBlock(level, golem) != null) startBreak(level, golem);
                else if (visits < MAX_VISITS && aim(level)) {
                    // logs still stand (a branch, a second trunk): vanish and reappear over the highest
                    visits++;
                    holdStill(golem);
                    sneeze(level, golem);
                } else { golem.stopClip(); done(); }
            }
            case DONE -> { }
        }
    }

    private void done() {
        machine.to(State.DONE);
        finish();
    }

    /** It cannot get there: leave this tree or spot alone for a while rather than trying it again at once. */
    private void giveUp(WorkGolem golem, long time) {
        golem.getData(ModAttachments.TASK).ignoreBlock(plan.replant(), time + 3000);
        done();
    }

    /**
     * What the next swing takes: a log of the tree level with the golem (from the block it stands on up to its head) within reach across,
     * the highest and then nearest first; with none, the tree block under its feet; or null.
     */
    private BlockPos nextBlock(ServerLevel level, WorkGolem golem) {
        BlockPos here = golem.blockPosition(), below = here.below();
        BlockPos next = null;
        double nextDist = 0;
        for (BlockPos log : plan.logs()) {
            if (log.equals(below) || log.getY() < below.getY() || log.getY() > here.getY() + 1) continue;
            double dx = log.getX() - here.getX(), dz = log.getZ() - here.getZ(), dist = dx * dx + dz * dz;
            if (dist > LOG_REACH * LOG_REACH || !level.getBlockState(log).is(BlockTags.LOGS)) continue;
            if (next == null || log.getY() > next.getY() || (log.getY() == next.getY() && dist < nextDist)) {
                next = log;
                nextDist = dist;
            }
        }
        if (next != null) return next;
        return isTreeBlock(level, below) ? below : null;
    }

    private void startBreak(ServerLevel level, WorkGolem golem) {
        target = nextBlock(level, golem);
        if (target != null && !target.equals(golem.blockPosition().below())) Face.toward(golem, Vec3.atCenterOf(target));
        clip = ClipRun.play(golem, Anim.M_BREAK_BELOW, Anim.M_BREAK_LEN).at(Anim.M_BREAK_AT, () -> {
            golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
            if (target != null && isTreeBlock(level, target)) level.destroyBlock(target, true, golem);
        });
        machine.to(State.BREAK);
    }

    /** Sneezes up to the spot on top of the crown it is aiming at. */
    private void sneeze(ServerLevel level, WorkGolem golem) {
        hop = new Hop(level, golem, new Vec3(top.getX() + 0.5, top.getY(), top.getZ() + 0.5));
        machine.to(State.SNEEZE);
    }

    @Override
    protected void end(ServerLevel level, WorkGolem golem, long time) {
        // interrupted up in the tree: poof back to where it stood on the ground (the trunk is still standing there, so never to the base)
        if (ground != null && golem.getY() > ground.y + 1.5) golem.teleportTo(ground.x, ground.y, ground.z);
        golem.setPickupSuspended(false);
        remaining.clear();
        top = null;
    }
}
