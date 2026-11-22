package com.scottcaldwell.industriousgolems.lumber;

import com.scottcaldwell.industriousgolems.ModAttachments;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.PickUp;
import com.scottcaldwell.industriousgolems.behavior.TaskRunner;
import com.scottcaldwell.industriousgolems.behavior.TaskState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import com.scottcaldwell.industriousgolems.golem.Anim;
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
 * bare-handed: the block under its feet breaks (leaves and logs alike, dropped as for a player and carried off a stack
 * at a time, see PickUp), it drops one block, and again, until it lands on the ground. The stump is left for {@link PlantSapling}. Runs in the exclusive task activity.
 */
public class FellTree extends TaskRunner<FellTree.FellPlan> {

    /** How near the golem gets to the foot of the tree before it sneezes. */
    private static final double SNEEZE_DIST = 3.5;
    /** Ticks spent trying to get to the foot of the tree before giving up. */
    private static final int STALL_TICKS = 600;

    /** Most sneezes (extra visits after the first) in one felling. */
    private static final int MAX_VISITS = 12;

    private enum State { WALK, SNEEZE, APPEAR, BREAK, DESCEND, DONE }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("fell-tree", "Lumberjack: fell a tree",
            "Mushroom golem, empty-handed. Walks to the foot of a tree, sneezes, vanishes and reappears on top of the crown, then mines straight down "
                    + "through the trunk. Branches and extra trunks are felled the same way: each time he lands with logs still standing he sneezes up onto the highest. The stump is left for a sapling to be planted in (a separate task, see plant-sapling).",
            State.class)
            .start(State.WALK)
            .state(State.WALK, "Walking to within 3.5 blocks of the foot of the tree.")
            .state(State.SNEEZE, "Sneeze-and-vanish clip; a puff of spores at the end.")
            .state(State.APPEAR, "Teleported onto the crown; the appear clip plays.")
            .state(State.BREAK, "Standing on the top block of the trunk's column, the break clip playing; the block below breaks at the hit moment.")
            .state(State.DESCEND, "Swing done and the block is gone: waiting to land on the next block down.")
            .state(State.DONE, "(end) Task over. If interrupted up in the tree he poofs back to the ground.")
            .edge(State.WALK, State.SNEEZE, "within 3.5 blocks of the tree and there is room on top of the crown")
            .edge(State.WALK, State.DONE, "no room on top of the crown, or no path for 30 s (tree ignored for 2.5 min)")
            .edge(State.SNEEZE, State.APPEAR, "vanish moment reached: teleports onto the crown")
            .edge(State.APPEAR, State.BREAK, "appear clip ends")
            .edge(State.BREAK, State.DESCEND, "break clip ends")
            .edge(State.DESCEND, State.BREAK, "landed, and another log or leaf is directly below")
            .edge(State.DESCEND, State.SNEEZE, "landed on something that is not part of the tree, but logs of the tree remain (a branch, another trunk): vanishes again to reach them")
            .edge(State.DESCEND, State.DONE, "landed on something that is not part of the tree and no log of it is left (or none can be stood over)");

    /** The tree chosen by {@link #plan}: chop {@code logs}, then replant at {@code replant}. */
    public record FellPlan(List<BlockPos> logs, BlockPos replant) { }

    private final List<BlockPos> remaining = new ArrayList<>();
    private final Machine.Run<State> machine = GRAPH.run();
    private int timer;
    private int stalled;
    /** The spot on top of the crown the golem pops out at (its feet), or null when there is none. */
    private BlockPos top;
    /** Sneezes so far: a bound, so a tree that will not come down cannot keep him at it forever. */
    private int visits;
    /** Where the golem stood on the ground when it first sneezed: where it is put back if the felling is cut short up in the tree. */
    private Vec3 ground;
    /** Ticks spent waiting to land after a swing. */
    private int falling;

    public FellTree() {
        super(FellPlan.class, 6000); // a big tree with branches takes a while: every log is a sneeze, a drop and a swing
    }

    // ---- the job ------------------------------------------------------------------------------------------------

    @Override
    protected void begin(ServerLevel level, WorkGolem golem, long time) {
        remaining.clear();
        remaining.addAll(plan.logs());
        golem.setReplantPos(plan.replant());
        visits = 0;
        ground = null;
        falling = 0;
        aim(level);
        stalled = 0;
        machine.reset();
        timer = 0;
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

    /** The spot over the log's column, above whatever leaves and logs sit directly over it; null when there is no room to stand there. */
    private static BlockPos standAbove(ServerLevel level, BlockPos log) {
        BlockPos column = log;
        while (isTreeBlock(level, column.above())) column = column.above();
        BlockPos feet = column.above();
        if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                || !level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) return null;
        return feet;
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
                if (Math.sqrt(dx * dx + dz * dz) > SNEEZE_DIST) {
                    if (++stalled > STALL_TICKS) { giveUp(golem, time); return; }
                    walkTo(golem, new BlockPosTracker(base), 2);
                    return;
                }
                if (top == null) { giveUp(golem, time); return; } // no room to stand on top
                holdStill(golem);
                if (ground == null) ground = golem.position();
                golem.playClip(Anim.M_SNEEZE_VANISH);
                machine.to(State.SNEEZE);
                timer = 0;
            }
            case SNEEZE -> {
                holdStill(golem);
                if (timer == Math.round(Anim.M_VANISH_AT * 20) - 2) puff(level, golem.position().add(0, 0.6, 0));
                if (++timer < Math.round(Anim.M_VANISH_AT * 20)) return;
                // gone in a puff of spores, and out again on top of the crown
                golem.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                golem.getNavigation().stop();
                golem.setDeltaMovement(Vec3.ZERO);
                puff(level, golem.position().add(0, 0.6, 0));
                golem.playClip(Anim.M_SNEEZE_APPEAR);
                machine.to(State.APPEAR);
                timer = 0;
            }
            case APPEAR -> {
                holdStill(golem);
                if (++timer < Math.round(Anim.M_APPEAR_LEN * 20)) return;
                startBreak(golem);
            }
            case BREAK -> {
                holdStill(golem);
                timer++;
                if (timer == Math.round(Anim.M_BREAK_AT * 20)) {
                    golem.swing(InteractionHand.MAIN_HAND, SwingAnimation.DEFAULT);
                    BlockPos below = golem.blockPosition().below();
                    if (isTreeBlock(level, below)) level.destroyBlock(below, true, golem);
                }
                if (timer >= Math.round(Anim.M_BREAK_LEN * 20)) { machine.to(State.DESCEND); falling = 0; }
            }
            case DESCEND -> {
                holdStill(golem);
                if (!golem.onGround()) {
                    if (++falling > STALL_TICKS / 6) { golem.stopClip(); done(); } // hung up on something: let go
                    return;
                }
                if (isTreeBlock(level, golem.blockPosition().below())) startBreak(golem);
                else if (visits < MAX_VISITS && aim(level)) {
                    // logs still stand (a branch, a second trunk): vanish and reappear over the highest
                    visits++;
                    holdStill(golem);
                    golem.playClip(Anim.M_SNEEZE_VANISH);
                    machine.to(State.SNEEZE);
                    timer = 0;
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

    private void startBreak(WorkGolem golem) {
        golem.playClip(Anim.M_BREAK_BELOW);
        machine.to(State.BREAK);
        timer = 0;
    }

    private static void puff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 24, 0.35, 0.3, 0.35, 0.05);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.03);
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
