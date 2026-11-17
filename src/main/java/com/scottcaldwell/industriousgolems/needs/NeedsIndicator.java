package com.scottcaldwell.industriousgolems.needs;

import com.scottcaldwell.industriousgolems.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Publishes a golem's status (health, whether blocked) for the client's status plate, makes a golem that lacks a
 * supply shake its head every few seconds, and mends charged golems.
 */
public final class NeedsIndicator {

    private static final String MARKER = "industriousgolems_need_icon";
    private static final int CHECK_INTERVAL = 10;
    private static final int SHAKE_INTERVAL = 100;
    private static final int REGEN_INTERVAL = 100;

    private NeedsIndicator() { }

    public static boolean isIcon(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && data.copyTag().contains(MARKER);
    }

    /** Tags a stack as a display-only item (never dropped, never real loot). */
    public static ItemStack markAsDisplay(ItemStack stack) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean(MARKER, true);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    /** Called every tick on the server for each golem. */
    public static void tick(WorkGolem golem) {
        if (golem.tickCount % CHECK_INTERVAL != 0) return;
        if (com.scottcaldwell.industriousgolems.behavior.HeldItem.stale(golem)) {
            com.scottcaldwell.industriousgolems.behavior.HeldItem.release(golem);
        }
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        if (needs == null || golem.isBaby()) {
            publish(golem, StatusData.NONE);
            return;
        }

        mend(golem);

        // Synced for the client's status plate: health, and whether the golem is blocked (dormant or out of a supply).
        boolean dormant = !golem.isCharged();
        boolean missingSupply = false;
        for (Need need : needs.needs()) {
            if (need.missing(golem)) { missingSupply = true; break; }
        }
        publish(golem, StatusData.pack(Math.round(golem.getHealth()), dormant || missingSupply));

        // A dormant golem is just still; one that works but lacks a supply shakes its head now and then.
        if (missingSupply && !dormant && golem.tickCount % SHAKE_INTERVAL == 0 && golem.getUnhappyCounter() == 0) {
            golem.setUnhappyCounter(40);
            golem.playSound(SoundEvents.VILLAGER_NO, 1.0F, golem.getVoicePitch());
        }
    }

    /** Charged golems slowly mend. */
    private static void mend(WorkGolem golem) {
        if (golem.isCharged() && !golem.isDeadOrDying() && golem.getHealth() < golem.getMaxHealth()
                && golem.tickCount % REGEN_INTERVAL == 0) {
            golem.heal(1.0F);
        }
    }

    /**
     * Sets the synced status. A client without this mod that tracks the golem (a bot, a mock player) cannot receive
     * the sync payload and NeoForge throws; that must never crash the server, the client simply sees no status plate.
     */
    private static void publish(WorkGolem golem, StatusData status) {
        try {
            if (!golem.getData(ModAttachments.STATUS).equals(status)) golem.setData(ModAttachments.STATUS, status);
        } catch (UnsupportedOperationException ignored) {
            // unsupported tracker; the value is stored server side regardless
        }
    }

    /** The icon is not a real item: never let it drop on death. */
    public static void clearOnDeath(WorkGolem golem) {
        if (isIcon(golem.getItemBySlot(EquipmentSlot.MAINHAND))) golem.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }
}
