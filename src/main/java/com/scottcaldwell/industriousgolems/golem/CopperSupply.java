package com.scottcaldwell.industriousgolems.golem;

import com.scottcaldwell.industriousgolems.ModProfessions;
import com.scottcaldwell.industriousgolems.block.GolemChestEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.golem.CopperGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

/**
 * What vanilla copper golems bring to golem chests: whatever that chest's golem works with (seeds, saplings, feed, a miner's kit), until
 * the chest holds a full stack of it. Beyond that, and for everything else, they sort as usual.
 */
public final class CopperSupply {
    private CopperSupply() { }

    public static boolean carriesSupply(PathfinderMob mob) {
        if (!(mob instanceof CopperGolem) || mob.getMainHandItem().isEmpty()) return false;
        ItemStack held = mob.getMainHandItem();
        return ModProfessions.all().stream().anyMatch(p -> p.needs().isInput(held));
    }

    /** Whether the block is the chest of a golem that works with what the copper golem holds. */
    public static boolean belongsIn(PathfinderMob mob, BlockState state) {
        if (!(mob instanceof CopperGolem) || mob.getMainHandItem().isEmpty()) return false;
        ItemStack held = mob.getMainHandItem();
        return ModProfessions.all().stream().anyMatch(p -> state.is(p.chest().get()) && p.needs().isInput(held));
    }

    /** Whether the chest holds less than a stack of what the copper golem holds, and has somewhere to put more. */
    public static boolean isShort(PathfinderMob mob, Container chest) {
        ItemStack held = mob.getMainHandItem();
        int there = 0;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            if (chest.getItem(i).is(held.getItem())) there += chest.getItem(i).getCount();
        }
        return there < held.getMaxStackSize() && Slots.fits(chest, held);
    }

    /** The golem chests in the loaded chunks about the area. */
    public static List<BlockEntity> golemChests(ServerLevel level, AABB area) {
        List<BlockEntity> found = new ArrayList<>();
        ChunkPos.rangeClosed(ChunkPos.containing(net.minecraft.core.BlockPos.containing(area.getCenter())), (int) Math.ceil(area.getXsize() / 32.0))
                .forEach(chunk -> {
                    LevelChunk loaded = level.getChunkSource().getChunkNow(chunk.x(), chunk.z());
                    if (loaded == null) return;
                    for (BlockEntity entity : loaded.getBlockEntities().values()) {
                        if (entity instanceof GolemChestEntity) found.add(entity);
                    }
                });
        return found;
    }
}
