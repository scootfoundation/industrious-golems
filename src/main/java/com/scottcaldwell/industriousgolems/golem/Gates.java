package com.scottcaldwell.industriousgolems.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A golem's way through fence gates. Its paths run through shut gates (see {@link GolemPathing}); as it comes up to one on its path it
 * opens it, and once it is clear of the gap it shuts it again. It only shuts gates it opened itself, so one a player left open stays
 * open.
 */
public class Gates {

    /** How near (squared) a gate on the path has to be before the golem opens it. */
    private static final double OPEN_DIST_SQR = 4.0;

    private final WorkGolem golem;
    private final Set<BlockPos> opened = new HashSet<>();

    public Gates(WorkGolem golem) {
        this.golem = golem;
    }

    public void tick(ServerLevel level) {
        Set<BlockPos> ahead = new HashSet<>();
        Path path = golem.getNavigation().getPath();
        if (path != null && !path.isDone()) {
            int next = path.getNextNodeIndex();
            for (int i = Math.max(0, next - 1); i < Math.min(path.getNodeCount(), next + 2); i++) {
                BlockPos pos = path.getNodePos(i);
                BlockState state = level.getBlockState(pos);
                if (!(state.getBlock() instanceof FenceGateBlock) || golem.position().distanceToSqr(Vec3.atBottomCenterOf(pos)) > OPEN_DIST_SQR) continue;
                ahead.add(pos);
                if (!state.getValue(FenceGateBlock.OPEN)) {
                    set(level, pos, state, true);
                    opened.add(pos.immutable());
                }
            }
        }
        for (Iterator<BlockPos> it = opened.iterator(); it.hasNext(); ) {
            BlockPos pos = it.next();
            if (ahead.contains(pos)) continue;
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof FenceGateBlock && state.getValue(FenceGateBlock.OPEN)) {
                // not on top of itself or another golem coming through
                if (!level.getEntitiesOfClass(WorkGolem.class, new AABB(pos).inflate(0.1)).isEmpty()) continue;
                set(level, pos, state, false);
            }
            it.remove();
        }
    }

    /** As a hand would: an opening gate swings away from the golem. */
    private void set(ServerLevel level, BlockPos pos, BlockState state, boolean open) {
        Direction heading = golem.getDirection();
        if (open && state.getValue(FenceGateBlock.FACING) == heading.getOpposite()) state = state.setValue(FenceGateBlock.FACING, heading);
        level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, open), 10);
        level.playSound(null, pos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(golem, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
    }
}
