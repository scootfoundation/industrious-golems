package com.scottcaldwell.industriousvillagers.needs;

import com.scottcaldwell.industriousvillagers.ModAttachments;
import net.minecraft.world.entity.npc.villager.Villager;

/** Villagers with a profession get hungry a fixed time after their last meal. */
public final class Hunger {

    /** Ticks between meals (half a Minecraft day). */
    public static final long INTERVAL = 12000;

    private Hunger() { }

    public static boolean isHungry(Villager villager) {
        long now = villager.level().getGameTime();
        long last = villager.getData(ModAttachments.LAST_MEAL);
        if (last == Long.MIN_VALUE) { // first time seen: start the clock fed
            villager.setData(ModAttachments.LAST_MEAL, now);
            return false;
        }
        return now - last >= INTERVAL;
    }

    public static void ate(Villager villager) {
        villager.setData(ModAttachments.LAST_MEAL, villager.level().getGameTime());
    }
}
