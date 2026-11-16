package com.scottcaldwell.industriousvillagers.behavior;

import com.google.common.collect.ImmutableMap;
import com.scottcaldwell.industriousvillagers.ModAttachments;
import com.scottcaldwell.industriousvillagers.needs.Hunger;
import com.scottcaldwell.industriousvillagers.needs.NeedsIndicator;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/**
 * A hungry villager stops, holds up a piece of food from its inventory and eats it (sound and crumbs), then is fed
 * until the next hunger interval. Interrupting the meal (sleep, damage) wastes nothing.
 */
public class EatFood extends Behavior<Villager> {

    private static final int EAT_TICKS = 32;

    private long endsAt;
    private ItemStack previousMainHand = ItemStack.EMPTY;

    public EatFood() {
        super(ImmutableMap.of(), EAT_TICKS);
    }

    private static int foodSlot(Villager villager) {
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (!inv.getItem(i).isEmpty() && ProfessionNeeds.isFood(inv.getItem(i))) return i;
        }
        return -1;
    }

    /** True when the villager is hungry and has something to eat. Used by {@link StartTask}. */
    public static boolean shouldEat(Villager villager) {
        if (ProfessionNeeds.of(villager) == null || villager.isSleeping()) return false;
        int slot = foodSlot(villager);
        return slot >= 0 && Hunger.wantsToEat(villager, villager.getInventory().getItem(slot));
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Villager villager) {
        return villager.getData(ModAttachments.TASK).type == TaskState.Type.EAT;
    }

    @Override
    protected void start(ServerLevel level, Villager villager, long time) {
        Tasks.stopOthers(level, villager, this, time);
        endsAt = time + EAT_TICKS;
        previousMainHand = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        int slot = foodSlot(villager);
        if (slot >= 0) {
            villager.setItemSlot(EquipmentSlot.MAINHAND,
                    NeedsIndicator.markAsDisplay(villager.getInventory().getItem(slot).copyWithCount(1)));
        }
        pause(villager);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Villager villager, long time) {
        return !villager.isSleeping() && foodSlot(villager) >= 0 && Tasks.active(villager)
                && Tasks.scheduleAllowsWork(level, villager);
    }

    @Override
    protected void tick(ServerLevel level, Villager villager, long time) {
        pause(villager);
        if ((endsAt - time) % 4 == 0) {
            ItemStack held = villager.getItemBySlot(EquipmentSlot.MAINHAND);
            villager.playSound(SoundEvents.GENERIC_EAT.value(), 0.5F, 0.9F + villager.getRandom().nextFloat() * 0.2F);
            level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, held.copyWithCount(1)),
                    villager.getX(), villager.getEyeY() - 0.2, villager.getZ(), 4, 0.1, 0.1, 0.1, 0.05);
        }
    }

    private static void pause(Villager villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    @Override
    protected void stop(ServerLevel level, Villager villager, long time) {
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
        Tasks.finish(level, villager);
    }
}
