package com.scottcaldwell.industriousgolems.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/** Ground navigation that treats a shut fence gate as a door the golem can go through; {@link Gates} does the opening and shutting. */
public class GolemPathing extends GroundPathNavigation {

    public GolemPathing(Mob mob, Level level) {
        super(mob, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new WalkNodeEvaluator() {
            @Override
            public PathType getPathType(PathfindingContext context, int x, int y, int z) {
                BlockState state = context.getBlockState(new BlockPos(x, y, z));
                if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) return PathType.WALKABLE_DOOR;
                return super.getPathType(context, x, y, z);
            }
        };
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }
}
