package com.scottcaldwell.industriousvillagers.needs;

import com.scottcaldwell.industriousvillagers.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import com.scottcaldwell.industriousvillagers.golem.WorkGolem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Publishes a golem's status (hunger, tool carried, whether blocked) for the client's status plate, and makes a
 * blocked golem shake its head every few seconds.
 */
public final class NeedsIndicator {

    private static final String MARKER = "industriousvillagers_need_icon";
    private static final int CHECK_INTERVAL = 10;
    private static final int SHAKE_INTERVAL = 100;
    private static final int STARVE_INTERVAL = 80;
    private static final int REGEN_INTERVAL = 100;
    private static final int REGEN_LEVEL = 14;
    /** Whether an empty hunger bar can kill a golem (otherwise it leaves them on half a heart). */
    private static final boolean STARVATION_IS_LETHAL = false;

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
        if (com.scottcaldwell.industriousvillagers.behavior.HeldItem.stale(golem)) {
            com.scottcaldwell.industriousvillagers.behavior.HeldItem.release(golem);
        }
        ProfessionNeeds needs = ProfessionNeeds.of(golem);
        com.scottcaldwell.industriousvillagers.inventory.InventoryLayout.organize(golem);
        if (needs == null || golem.isBaby()) {
            publish(golem, StatusData.NONE);
            return;
        }

        int hunger = Hunger.level(golem);
        applyHungerEffects(golem, hunger);

        // Synced for the client's status plate: hunger, health, whether blocked, and the tools carried.
        boolean blocked = false;
        for (Need need : needs.needs()) {
            if (need.missing(golem)) { blocked = true; break; }
        }
        int status = StatusData.pack(hunger, Math.round(golem.getHealth()), blocked);
        int tools = 0;
        java.util.List<Need> toolNeeds = needs.toolNeeds();
        for (int t = 0; t < toolNeeds.size(); t++) {
            if (toolNeeds.get(t).have(golem.getInventory()) > 0) tools |= 1 << t;
        }
        StatusData updated = new StatusData(status, tools);
        publish(golem, updated);

        if (blocked && !golem.isSleeping() && golem.tickCount % SHAKE_INTERVAL == 0 && golem.getUnhappyCounter() == 0) {
            golem.setUnhappyCounter(40);
            golem.playSound(SoundEvents.VILLAGER_NO, 1.0F, golem.getVoicePitch());
        }
    }

    /** Starving golems take damage; well-fed ones slowly heal. */
    private static void applyHungerEffects(WorkGolem golem, int hunger) {
        if (!(golem.level() instanceof net.minecraft.server.level.ServerLevel level) || golem.isDeadOrDying()) return;
        if (hunger == 0 && golem.tickCount % STARVE_INTERVAL == 0) {
            // like a player on normal difficulty, starvation never finishes the golem off
            if (STARVATION_IS_LETHAL || golem.getHealth() > 1.0F) {
                golem.hurtServer(level, level.damageSources().starve(), 1.0F);
            }
        } else if (hunger >= REGEN_LEVEL && golem.getHealth() < golem.getMaxHealth() && golem.tickCount % REGEN_INTERVAL == 0) {
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
