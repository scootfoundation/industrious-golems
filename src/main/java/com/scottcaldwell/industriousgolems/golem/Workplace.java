package com.scottcaldwell.industriousgolems.golem;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.level.pathfinder.Path;

import java.util.List;

/**
 * Which chest a golem works from. Unlike a villager's job site it is not claimed: no ticket is taken, so any number of golems of a kind
 * can work from one chest. A golem without one takes the nearest of its kind it can walk to, and forgets it when it sees it gone.
 */
public final class Workplace {

    private static final int INTERVAL = 20, RANGE = 48, TRIES = 5;
    /** Nearer than this the golem notices that its chest is gone. */
    private static final double NOTICE_DIST = 16.0;

    private Workplace() { }

    public static void tick(ServerLevel level, WorkGolem golem) {
        if ((golem.tickCount + golem.getId()) % INTERVAL != 0) return;
        var brain = golem.getBrain();
        var wanted = golem.profession().jobSite();
        GlobalPos held = brain.getMemory(MemoryModuleType.JOB_SITE).orElse(null);
        if (held != null) {
            BlockPos pos = held.pos();
            if (held.dimension().equals(level.dimension()) && pos.closerToCenterThan(golem.position(), NOTICE_DIST)
                    && level.isLoaded(pos) && !level.getPoiManager().exists(pos, wanted)) {
                brain.eraseMemory(MemoryModuleType.JOB_SITE);
            }
            return;
        }
        List<BlockPos> near = level.getPoiManager()
                .findAllClosestFirstWithType(wanted, pos -> true, golem.blockPosition(), RANGE, PoiManager.Occupancy.ANY)
                .map(Pair::getSecond).limit(TRIES).toList();
        for (BlockPos pos : near) {
            Path path = golem.getNavigation().createPath(pos, 1);
            if (path != null && path.canReach()) {
                brain.setMemory(MemoryModuleType.JOB_SITE, GlobalPos.of(level.dimension(), pos));
                return;
            }
        }
    }
}
