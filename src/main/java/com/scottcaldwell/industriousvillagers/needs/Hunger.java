package com.scottcaldwell.industriousvillagers.needs;

import com.scottcaldwell.industriousvillagers.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * A 0..20 hunger scale like the player's (10 drumsticks of 2 points). It drains steadily after the last meal and food
 * restores its nutrition points. Hungry villagers eat, a villager at {@link #WORK_REFUSAL_LEVEL} or below refuses to
 * work, and an empty bar starves them.
 *
 * <p>Stored as the game time of the last meal; eating moves that time forward by the points restored.
 */
public final class Hunger {

    public static final int MAX = 20;
    /** Ticks for a full bar to drain completely (one Minecraft day). */
    public static final long INTERVAL = 24000;
    static final long TICKS_PER_POINT = INTERVAL / MAX;
    /** At or below this (2 drumsticks) the villager will not work, whatever it is carrying. */
    public static final int WORK_REFUSAL_LEVEL = 4;
    /** At or below this (5 drumsticks) a missing food supply is reported on the status plate. */
    private static final int HUNGRY_LEVEL = 10;

    private Hunger() { }

    /** Current hunger, 0 (starving) to {@link #MAX} (full). A villager seen for the first time starts full. */
    public static int level(Villager villager) {
        long now = villager.level().getGameTime();
        long last = villager.getData(ModAttachments.LAST_MEAL);
        if (last == Long.MIN_VALUE) {
            villager.setData(ModAttachments.LAST_MEAL, now);
            return MAX;
        }
        return Math.max(0, Math.min(MAX, MAX - (int) ((now - last) / TICKS_PER_POINT)));
    }

    public static boolean isHungry(Villager villager) {
        return level(villager) <= HUNGRY_LEVEL;
    }

    public static boolean tooHungryToWork(Villager villager) {
        return level(villager) <= WORK_REFUSAL_LEVEL;
    }

    public static boolean isStarving(Villager villager) {
        return level(villager) == 0;
    }

    /** Hunger points this stack restores (0 when it is not food). */
    public static int nutrition(ItemStack stack) {
        FoodProperties food = stack.get(DataComponents.FOOD);
        return food == null ? 0 : food.nutrition();
    }

    /** Whether eating this now restores its full value, or the villager is hungry enough that it must eat anyway. */
    public static boolean wantsToEat(Villager villager, ItemStack food) {
        int nutrition = nutrition(food);
        if (nutrition <= 0) return false;
        int level = level(villager);
        return level <= MAX - nutrition || level <= WORK_REFUSAL_LEVEL;
    }

    public static void ate(Villager villager, ItemStack food) {
        long now = villager.level().getGameTime();
        level(villager); // makes sure the clock is running
        long last = villager.getData(ModAttachments.LAST_MEAL);
        villager.setData(ModAttachments.LAST_MEAL, Math.min(now, last + nutrition(food) * TICKS_PER_POINT));
    }
}