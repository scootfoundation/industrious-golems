package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.ModProfessions;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

/**
 * The mushroom golem's way out of a tight spot: when it has somewhere to be, cannot get any closer on foot (no path, or hemmed in, say
 * after felling a tree whose leaves hang close to the ground), it sneezes and pops up a few blocks nearer to where it is going.
 * Only the lumberjack does this, and only while a task is running; nothing else touches the golem during the hop.
 */
public class Sneeze {

    /** Ticks without progress toward a walk goal before the golem sneezes its way out. */
    private static final int STUCK_TICKS = 30;
    /** Longest single hop, in blocks. */
    private static final double MAX_HOP = 8.0;
    /** A goal farther than this is not a short hop away: the golem does not sneeze toward it. */
    private static final double MAX_GOAL_DIST = 12.0;
    /** Closer to the goal than this and the golem is simply there: no hop. */
    private static final double MIN_GOAL_DIST = 2.5;
    /** A landing spot is never farther than this from the golem's chest. */
    private static final double RANGE_FROM_SITE = 24.0;
    /** Quiet period after a failed search for a landing spot, so it is not repeated every tick. */
    private static final int RETRY_TICKS = 60;

    private enum State { WATCH, VANISH, APPEAR }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("sneeze-hop", "Mushroom golem: sneeze out of a tight spot",
            "Lumberjack only, while a task is running. If a walk goal is set and the golem makes no headway for 1.5 s it sneezes, vanishes and "
                    + "reappears up to 8 blocks nearer the goal (on the nearest standable spot), then carries on walking. The task is paused meanwhile.",
            State.class)
            .start(State.WATCH)
            .state(State.WATCH, "Watching its own progress toward the current walk goal.")
            .state(State.VANISH, "Sneeze-and-vanish clip; a puff of spores at the end. The task waits.")
            .state(State.APPEAR, "Teleported onto the landing spot; the appear clip plays. The task waits.")
            .edge(State.WATCH, State.VANISH, "stuck for 1.5 s with a goal 2.5 blocks or more away, and a standable landing spot exists toward it")
            .edge(State.VANISH, State.APPEAR, "vanish moment reached: teleports onto the landing spot")
            .edge(State.APPEAR, State.WATCH, "appear clip ends");

    private final WorkGolem golem;
    private final Machine.Run<State> machine = GRAPH.run();
    private Vec3 anchor;
    private BlockPos goal;
    private long goalSeen = Long.MIN_VALUE;
    private int stuck;
    private int timer;
    private Vec3 landing;

    public Sneeze(WorkGolem golem) {
        this.golem = golem;
    }

    /** Runs once per tick before the brain. Returns true while a hop is under way, when the brain must not run. */
    public boolean tick(ServerLevel level) {
        if (golem.profession() != ModProfessions.LUMBERJACK) return false;
        if (!machine.is(State.WATCH)) return hop(level);
        if (!Tasks.active(golem) || !golem.clip().isEmpty()) { stuck = 0; anchor = null; return false; }
        long now = level.getGameTime();
        WalkTarget target = golem.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (target != null) {
            goal = target.getTarget().currentBlockPosition();
            goalSeen = now;
        }
        if (goal == null || now - goalSeen > 5) { stuck = 0; anchor = null; return false; }
        double dx = goal.getX() + 0.5 - golem.getX(), dz = goal.getZ() + 0.5 - golem.getZ();
        double flat = Math.sqrt(dx * dx + dz * dz);
        // already there, or too far for a hop to be a short cut: only short distances are sneezed
        if ((flat < MIN_GOAL_DIST && Math.abs(goal.getY() - golem.getY()) < 2) || flat > MAX_GOAL_DIST) { stuck = 0; anchor = null; return false; }
        Vec3 here = golem.position();
        if (anchor == null || here.distanceToSqr(anchor) > 0.25) { anchor = here; stuck = Math.min(stuck, 0); return false; }
        if (++stuck < STUCK_TICKS) return false;
        stuck = 0;
        landing = landingSpot(level, goal);
        if (landing == null) { stuck = -RETRY_TICKS; return false; }
        golem.getNavigation().stop();
        golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        golem.playClip(Anim.M_SNEEZE_VANISH);
        machine.to(State.VANISH);
        timer = 0;
        return true;
    }

    private boolean hop(ServerLevel level) {
        if (!golem.isCharged()) { abort(); return false; }
        golem.getNavigation().stop();
        golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        golem.setDeltaMovement(0, golem.getDeltaMovement().y, 0);
        if (machine.is(State.VANISH)) {
            int vanish = Math.round(Anim.M_VANISH_AT * 20);
            if (timer == vanish - 2) puff(level, golem.position().add(0, 0.6, 0));
            if (++timer < vanish) return true;
            golem.teleportTo(landing.x, landing.y, landing.z);
            golem.setDeltaMovement(Vec3.ZERO);
            puff(level, golem.position().add(0, 0.6, 0));
            golem.playClip(Anim.M_SNEEZE_APPEAR);
            machine.to(State.APPEAR);
            timer = 0;
            return true;
        }
        if (++timer < Math.round(Anim.M_APPEAR_LEN * 20)) return true;
        abort();
        return true;
    }

    /** Back to watching, with a clean slate. */
    private void abort() {
        golem.stopClip();
        machine.reset();
        stuck = 0;
        anchor = null;
        goal = null;
    }

    /** The nearest standable spot to a point up to {@link #MAX_HOP} blocks toward the goal (the goal itself when it is within reach), or null. */
    private Vec3 landingSpot(ServerLevel level, BlockPos goal) {
        Vec3 here = golem.position();
        Vec3 to = new Vec3(goal.getX() + 0.5, goal.getY(), goal.getZ() + 0.5);
        Vec3 flat = to.subtract(here).multiply(1, 0, 1);
        double dist = flat.length();
        Vec3 ideal = dist <= MAX_HOP ? to : new Vec3(here.x + flat.x / dist * MAX_HOP, here.y, here.z + flat.z / dist * MAX_HOP);
        BlockPos base = BlockPos.containing(ideal);
        BlockPos site = golem.jobSite(level);
        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (int r = 0; r <= 2; r++) {
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    for (int y = -3; y <= 3; y++) {
                        BlockPos feet = base.offset(x, y, z);
                        Vec3 at = new Vec3(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5);
                        if (!Stand.free(level, at) || !level.getFluidState(feet).isEmpty()) continue;
                        BlockPos below = feet.below();
                        if (level.getBlockState(below).getCollisionShape(level, below).isEmpty()) continue;
                        if (at.distanceToSqr(here) < 2.25) continue; // not worth a sneeze
                        if (site != null && feet.distSqr(site) > RANGE_FROM_SITE * RANGE_FROM_SITE) continue; // never out of the work area
                        double score = at.distanceToSqr(ideal);
                        if (score < bestScore) { bestScore = score; best = at; }
                    }
                }
            }
            if (best != null) break;
        }
        return best;
    }

    public static void puff(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 24, 0.35, 0.3, 0.35, 0.05);
        level.sendParticles(ParticleTypes.POOF, at.x, at.y, at.z, 8, 0.25, 0.25, 0.25, 0.03);
    }
}
