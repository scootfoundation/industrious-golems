package com.scottcaldwell.industriousgolems.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/** Where a golem should stand to do something to a spot: the distance its clip is written for, squarely on one side of it. */
public final class Stand {
    private Stand() { }

    /**
     * A spot {@code dist} blocks from the centre of {@code target} along an axis, on the side {@code from} is nearest to (a side with
     * room to stand is preferred). Its height is the target's.
     */
    public static Vec3 cardinal(ServerLevel level, BlockPos target, WorkGolem golem, double dist) {
        Vec3 centre = Vec3.atCenterOf(target);
        Vec3 rel = golem.position().subtract(centre);
        Vec3 best = null;
        double bestScore = -1e9;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3 at = new Vec3(centre.x + d.getStepX() * dist, target.getY(), centre.z + d.getStepZ() * dist);
            double score = rel.x * d.getStepX() + rel.z * d.getStepZ();
            if (score <= bestScore || !reachable(level, golem, at)) continue;
            bestScore = score;
            best = at;
        }
        return best;
    }

    /**
     * The spot {@code dist} blocks in front of a chest, on the side it faces, so the golem meets it face on; another side only when the
     * front has no room to stand.
     */
    public static Vec3 front(ServerLevel level, BlockPos chest, WorkGolem golem, double dist) {
        var state = level.getBlockState(chest);
        if (state.hasProperty(net.minecraft.world.level.block.ChestBlock.FACING)) {
            Direction d = state.getValue(net.minecraft.world.level.block.ChestBlock.FACING);
            Vec3 centre = Vec3.atCenterOf(chest);
            Vec3 at = new Vec3(centre.x + d.getStepX() * dist, chest.getY(), centre.z + d.getStepZ() * dist);
            if (reachable(level, golem, at)) return at;
        }
        return cardinal(level, chest, golem, dist);
    }

    /** Whether the golem can stand at the point and get there from where it is. */
    public static boolean reachable(ServerLevel level, WorkGolem golem, Vec3 at) {
        BlockPos feet = BlockPos.containing(at);
        BlockPos below = feet.below();
        if (!free(level, at) || level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return false;
        if (!golem.onGround() && !golem.isInLiquid()) return true; // a golem in the air cannot plan a path yet: judge by the ground alone
        var path = golem.getNavigation().createPath(feet, 0);
        return path != null && path.canReach();
    }

    /** Whether a golem can stand with its feet at this point: the blocks it would fill have no collision. */
    public static boolean free(ServerLevel level, Vec3 at) {
        BlockPos feet = BlockPos.containing(at);
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }
}
