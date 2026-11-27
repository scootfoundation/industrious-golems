package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.IndustriousGolems;
import com.scottcaldwell.industriousgolems.ModProfessions;
import com.scottcaldwell.industriousgolems.behavior.Machine;
import com.scottcaldwell.industriousgolems.behavior.Tasks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.phys.Vec3;

/**
 * The mushroom golem's way out of a tight spot: when it has somewhere to be and cannot get any closer on foot (no path, hemmed in,
 * oscillating against an obstacle, say after felling a tree whose leaves hang close to the ground), it sneezes and pops up a few blocks
 * nearer to where it is going. Only the lumberjack does this. Where it is going is its walk goal, whoever set it (a task, a stroll); a golem
 * that is idle with no walk goal but cannot reach its own chest (boxed in, so that no job can even be planned) heads for the chest.
 * Nothing else touches the golem during the hop.
 *
 * <p>It also sneezes when it is trapped, whatever it was trying to do: it is a little over a block tall, so under low leaves there can
 * be no step it is able to take, and then no task ever sets a walk goal for the progress watch to see.
 */
public class Sneeze {

    /** Ticks without getting any nearer the goal before the golem sneezes its way out. */
    private static final int STUCK_TICKS = 40;
    /** Getting this much nearer to the goal counts as progress. */
    private static final double PROGRESS = 0.5;
    /** Longest single hop, in blocks. */
    private static final double MAX_HOP = 8.0;
    /** A walk goal farther than this is not a short hop away: the golem does not sneeze toward it (the game tests' arenas sit side by side). */
    private static final double MAX_GOAL_DIST = Boolean.getBoolean("industriousgolems.tinyGrove") ? 12.0 : 24.0;
    /** Closer to the goal than this and the golem is simply there: no hop. */
    private static final double MIN_GOAL_DIST = 2.5;
    /** A landing spot is never farther than this from the golem's chest. */
    private static final double RANGE_FROM_SITE = 24.0;
    /** Quiet period after a failed search for a landing spot, so it is not repeated every tick. */
    private static final int RETRY_TICKS = 60;
    /** How often an idle golem checks it can still get to its chest, and how near the chest counts as home. */
    private static final int HOME_CHECK_TICKS = 40;
    private static final double HOME_DIST = 4.0;
    /** How often it checks whether it is trapped, how many checks in a row it takes, and the pause after hopping out of a trap. */
    private static final int TRAP_CHECK_TICKS = 20, TRAP_CHECKS = 2, TRAP_PAUSE_TICKS = 100;

    private enum State { WATCH, VANISH, APPEAR }

    public static final Machine.Graph<State> GRAPH = new Machine.Graph<>("sneeze-hop", "Mushroom golem: sneeze out of a tight spot",
            "Lumberjack only. If a walk goal is set (or the golem is idle and cannot reach its chest) and it gets no nearer for 2 s, or it is trapped "
                    + "(no step it can take, or carrying something with no way to its chest), it sneezes, vanishes and reappears up to 8 blocks nearer the goal (on the nearest standable spot), then carries on. The brain is paused meanwhile.",
            State.class)
            .start(State.WATCH)
            .state(State.WATCH, "Watching its own progress toward the current goal.")
            .state(State.VANISH, "Sneeze-and-vanish clip; a puff of spores at the end. The brain waits.")
            .state(State.APPEAR, "Teleported onto the landing spot; the appear clip plays. The brain waits.")
            .edge(State.WATCH, State.VANISH, "no nearer the goal for 2 s with the goal 2.5 blocks or more away, or trapped for 2 s; and a standable landing spot exists")
            .edge(State.VANISH, State.APPEAR, "vanish moment reached: teleports onto the landing spot")
            .edge(State.APPEAR, State.WATCH, "appear clip ends");

    private final WorkGolem golem;
    private final Machine.Run<State> machine = GRAPH.run();
    private BlockPos goal;
    private boolean goalIsHome;
    private long goalSeen = Long.MIN_VALUE;
    private double best = Double.MAX_VALUE;
    private int stuck;
    private int timer;
    private Vec3 landing;
    private long nextHomeCheck;
    private boolean boxedIn;
    private long nextTrapCheck;
    private int trappedChecks;

    public Sneeze(WorkGolem golem) {
        this.golem = golem;
    }

    /** Runs once per tick before the brain. Returns true while a hop is under way, when the brain must not run. */
    public boolean tick(ServerLevel level) {
        if (golem.profession() != ModProfessions.LUMBERJACK) return false;
        if (!machine.is(State.WATCH)) return hop(level);
        if (!golem.isCharged() || !golem.clip().isEmpty() || golem.isPassenger()) { forget(); return false; }
        long now = level.getGameTime();
        if (trapped(level, now)) {
            BlockPos site = golem.jobSite(level);
            landing = landingSpot(level, site != null ? site : golem.blockPosition());
            IndustriousGolems.LOGGER.debug("Sneeze: {} trapped at {}: {}", golem.getId(), golem.blockPosition(), landing);
            nextTrapCheck = now + TRAP_PAUSE_TICKS;
            trappedChecks = 0;
            if (landing != null) return vanish();
        }
        BlockPos aim = currentGoal(level, now);
        if (aim == null) { forget(); return false; }
        if (goal == null || goal.distSqr(aim) > 4) { best = Double.MAX_VALUE; stuck = Math.min(stuck, 0); } // a new goal: progress starts afresh
        goal = aim;
        double dx = goal.getX() + 0.5 - golem.getX(), dz = goal.getZ() + 0.5 - golem.getZ(), dy = goal.getY() - golem.getY();
        double flat = Math.sqrt(dx * dx + dz * dz);
        // already there, or too far for a hop to be a short cut: only short distances are sneezed
        if ((flat < MIN_GOAL_DIST && Math.abs(dy) < 2) || flat > (goalIsHome ? RANGE_FROM_SITE : MAX_GOAL_DIST)) { forget(); return false; }
        double dist = Math.sqrt(flat * flat + dy * dy);
        if (dist < best - PROGRESS) { best = dist; if (stuck > 0) stuck = 0; return false; } // (a retry pause, below zero, runs on)
        if (++stuck < STUCK_TICKS) return false;
        stuck = 0;
        landing = landingSpot(level, goal);
        IndustriousGolems.LOGGER.debug("Sneeze: {} stuck at {} heading for {}{}: {}", golem.getId(), golem.blockPosition(), goal,
                goalIsHome ? " (home)" : "", landing == null ? "no landing spot" : "hopping to " + landing);
        if (landing == null) { stuck = -RETRY_TICKS; return false; }
        return vanish();
    }

    private boolean vanish() {
        golem.getNavigation().stop();
        golem.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        golem.playClip(Anim.M_SNEEZE_VANISH);
        machine.to(State.VANISH);
        timer = 0;
        return true;
    }

    /** Whether it has been unable to get out on foot for a couple of checks running. */
    private boolean trapped(ServerLevel level, long now) {
        if (now < nextTrapCheck) return false;
        nextTrapCheck = now + TRAP_CHECK_TICKS;
        if (!golem.onGround() || golem.isInLiquid() || !(walledIn(level) || cutOff(level))) {
            trappedChecks = 0;
            return false;
        }
        return ++trappedChecks >= TRAP_CHECKS;
    }

    /** Not one step to take: squashed into a block, or low leaves or walls on every side. */
    private boolean walledIn(ServerLevel level) {
        if (!level.noCollision(golem, golem.getBoundingBox().deflate(0.05))) return true;
        return !hasStep(level, BlockPos.containing(golem.getX(), golem.getY() + 0.2, golem.getZ()));
    }

    /** Whether there is somewhere beside this spot to step to: level, one up or one down. */
    private static boolean hasStep(ServerLevel level, BlockPos feet) {
        for (Direction side : Direction.Plane.HORIZONTAL) {
            for (int dy = -1; dy <= 1; dy++) {
                BlockPos to = feet.relative(side).above(dy);
                BlockPos below = to.below();
                if (Stand.free(level, Vec3.atBottomCenterOf(to)) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return true;
            }
        }
        return false;
    }

    /** Holding something for its chest and unable to get there (a carrying golem has no stroll or job to set a walk goal with). */
    private boolean cutOff(ServerLevel level) {
        BlockPos site = golem.jobSite(level);
        if (site == null || !golem.isCarrying()) return false;
        double far = golem.distanceToSqr(Vec3.atCenterOf(site));
        return far > HOME_DIST * HOME_DIST && far <= RANGE_FROM_SITE * RANGE_FROM_SITE && Stand.front(level, site, golem, 1.5) == null;
    }

    /**
     * Where the golem is trying to get to: its walk goal (kept in mind for a few ticks after a walk gives up and starts over), else,
     * when it is idle far from its chest and cannot reach it, the chest.
     */
    private BlockPos currentGoal(ServerLevel level, long now) {
        WalkTarget target = golem.getBrain().getMemory(MemoryModuleType.WALK_TARGET).orElse(null);
        if (target != null) {
            goalSeen = now;
            goalIsHome = false;
            return target.getTarget().currentBlockPosition();
        }
        if (goal != null && !goalIsHome && now - goalSeen <= 5) return goal;
        if (Tasks.active(golem) || golem.isCarrying()) return null;
        BlockPos site = golem.jobSite(level);
        if (site == null || golem.distanceToSqr(Vec3.atCenterOf(site)) <= HOME_DIST * HOME_DIST) { boxedIn = false; return null; }
        if (now >= nextHomeCheck) {
            nextHomeCheck = now + HOME_CHECK_TICKS;
            boxedIn = golem.onGround() && Stand.front(level, site, golem, 1.5) == null;
        }
        goalIsHome = true;
        return boxedIn ? site : null;
    }

    private void forget() {
        stuck = Math.min(stuck, 0);
        best = Double.MAX_VALUE;
        goal = null;
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
        best = Double.MAX_VALUE;
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
                        if (!hasStep(level, feet)) continue; // nor into another trap
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
