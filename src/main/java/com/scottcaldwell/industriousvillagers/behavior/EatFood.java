package com.scottcaldwell.industriousvillagers.behavior;

import com.scottcaldwell.industriousvillagers.needs.Hunger;
import com.scottcaldwell.industriousvillagers.needs.NeedsIndicator;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * A hungry villager stops, holds up a piece of food from its inventory and eats it (sound and crumbs), restoring the
 * food's nutrition. Interrupting the meal (sleep, damage) wastes nothing.
 */
public class EatFood extends TaskRunner<EatFood.Meal> {

    /** The plan: nothing to say but "eat". */
    public record Meal() { }

    private static final Meal MEAL = new Meal();
    private static final int EAT_TICKS = 32;

    private long endsAt;
    private ItemStack previousMainHand = ItemStack.EMPTY;

    public EatFood() {
        super(Meal.class, EAT_TICKS);
    }

    private static int foodSlot(Villager villager) {
        ProfessionNeeds needs = ProfessionNeeds.effective(villager);
        return needs == null ? -1 : needs.food().slot(villager.getInventory());
    }

    /** A meal when the villager is awake, has food, and wants it (see {@link Hunger#wantsToEat}); otherwise null. */
    public static Meal plan(Villager villager) {
        if (villager.isSleeping()) return null;
        int slot = foodSlot(villager);
        return slot >= 0 && Hunger.wantsToEat(villager, villager.getInventory().getItem(slot)) ? MEAL : null;
    }

    @Override
    protected void begin(ServerLevel level, Villager villager, long time) {
        endsAt = time + EAT_TICKS;
        previousMainHand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        int slot = foodSlot(villager);
        if (slot >= 0) {
            villager.setItemSlot(EquipmentSlot.MAINHAND,
                    NeedsIndicator.markAsDisplay(villager.getInventory().getItem(slot).copyWithCount(1)));
        }
        holdStill(villager);
    }

    @Override
    protected boolean allowedNow(ServerLevel level, Villager villager) {
        return !villager.isSleeping() && foodSlot(villager) >= 0 && Tasks.scheduleAllowsEating(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        holdStill(villager);
        if ((endsAt - time) % 4 == 0) {
            ItemStack held = villager.getItemBySlot(EquipmentSlot.MAINHAND);
            villager.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 0.9F + villager.getRandom().nextFloat() * 0.2F);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, held.copyWithCount(1)),
                    villager.getX(), villager.getEyeY() - 0.2, villager.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
        }
    }

    @Override
    protected void end(ServerLevel level, Villager villager, long time) {
        villager.setItemSlot(EquipmentSlot.MAINHAND, previousMainHand);
        previousMainHand = ItemStack.EMPTY;
        if (time >= endsAt) { // not interrupted before swallowing
            int slot = foodSlot(villager);
            if (slot >= 0) {
                ItemStack eaten = villager.getInventory().removeItem(slot, 1);
                Hunger.ate(villager, eaten);
                villager.playSound(SoundEvents.PLAYER_BURP, 0.5F, 1.0F);
            }
        }
    }
}