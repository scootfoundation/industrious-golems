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
    public static Vec3 cardinal(ServerLevel level, BlockPos target, Vec3 from, double dist) {
        Vec3 centre = Vec3.atCenterOf(target);
        Vec3 rel = from.subtract(centre);
        Vec3 best = null;
        double bestScore = -1e9;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            Vec3 at = new Vec3(centre.x + d.getStepX() * dist, target.getY(), centre.z + d.getStepZ() * dist);
            double score = rel.x * d.getStepX() + rel.z * d.getStepZ() - (free(level, at) ? 0 : 100);
            if (score > bestScore) {
                bestScore = score;
                best = at;
            }
        }
        return best;
    }

    /** Whether a golem can stand with its feet at this point: the blocks it would fill have no collision. */
    public static boolean free(ServerLevel level, Vec3 at) {
        BlockPos feet = BlockPos.containing(at);
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }
}
