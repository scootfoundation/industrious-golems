package com.scottcaldwell.industriousvillagers.behavior;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Picking up the loot of the villager's own work. */
public final class Drops {
    private Drops() { }

    /** Picks up what was just dropped near {@code at} (never items that were already lying there). */
    public static void collectFresh(ServerLevel level, Villager villager, Vec3 at) {
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(at, at).inflate(3.0))) {
            if (drop.tickCount > 1) continue;
            ItemStack rest = villager.getInventory().addItem(drop.getItem().copy());
            if (rest.isEmpty()) drop.discard();
            else drop.setItem(rest);
        }
        villager.getInventory().setChanged();
    }
}