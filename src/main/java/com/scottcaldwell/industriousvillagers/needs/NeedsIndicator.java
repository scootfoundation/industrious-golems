package com.scottcaldwell.industriousvillagers.needs;

import com.scottcaldwell.industriousvillagers.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Publishes a villager's status (hunger, tool carried, whether blocked) for the client's status plate, and makes a
 * blocked villager shake its head every few seconds.
 */
public final class NeedsIndicator {

    private static final String MARKER = "industriousvillagers_need_icon";
    private static final int CHECK_INTERVAL = 10;
    private static final int SHAKE_INTERVAL = 100;
    private static final int STARVE_INTERVAL = 80;
    private static final int REGEN_INTERVAL = 100;
    private static final int REGEN_LEVEL = 14;
    /** Whether an empty hunger bar can kill a villager (otherwise it leaves them on half a heart). */
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

    /** Called every tick on the server for each villager. */
    public static void tick(Villager villager) {
        if (villager.tickCount % CHECK_INTERVAL != 0) return;
        ProfessionNeeds needs = ProfessionNeeds.effective(villager);
        com.scottcaldwell.industriousvillagers.inventory.InventoryLayout.organize(villager);
        if (needs == null || villager.isBaby()) {
            publish(villager, StatusData.NONE);
            return;
        }

        int hunger = Hunger.level(villager);
        applyHungerEffects(villager, hunger);

        // Synced for the client's status plate: hunger, health, whether blocked, and the tools carried.
        boolean blocked = false;
        for (Need need : needs.needs()) {
            if (need.missing(villager)) { blocked = true; break; }
        }
        int status = StatusData.pack(hunger, Math.round(villager.getHealth()), blocked);
        int tools = 0;
        java.util.List<Need> toolNeeds = needs.toolNeeds();
        for (int t = 0; t < toolNeeds.size(); t++) {
            if (toolNeeds.get(t).have(villager.getInventory()) > 0) tools |= 1 << t;
        }
        StatusData updated = new StatusData(status, tools);
        publish(villager, updated);

        if (blocked && !villager.isSleeping() && villager.tickCount % SHAKE_INTERVAL == 0 && villager.getUnhappyCounter() == 0) {
            villager.setUnhappyCounter(40);
            villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, villager.getVoicePitch());
        }
    }

    /** Starving villagers take damage; well-fed ones slowly heal. */
    private static void applyHungerEffects(Villager villager, int hunger) {
        if (!(villager.level() instanceof net.minecraft.server.level.ServerLevel level) || villager.isDeadOrDying()) return;
        if (hunger == 0 && villager.tickCount % STARVE_INTERVAL == 0) {
            // like a player on normal difficulty, starvation never finishes the villager off
            if (STARVATION_IS_LETHAL || villager.getHealth() > 1.0F) {
                villager.hurtServer(level, level.damageSources().starve(), 1.0F);
            }
        } else if (hunger >= REGEN_LEVEL && villager.getHealth() < villager.getMaxHealth() && villager.tickCount % REGEN_INTERVAL == 0) {
            villager.heal(1.0F);
        }
    }

    /**
     * Sets the synced status. A client without this mod that tracks the villager (a bot, a mock player) cannot receive
     * the sync payload and NeoForge throws; that must never crash the server, the client simply sees no status plate.
     */
    private static void publish(Villager villager, StatusData status) {
        try {
            if (!villager.getData(ModAttachments.STATUS).equals(status)) villager.setData(ModAttachments.STATUS, status);
        } catch (UnsupportedOperationException ignored) {
            // unsupported tracker; the value is stored server side regardless
        }
    }

    /** The icon is not a real item: never let it drop on death. */
    public static void clearOnDeath(Villager villager) {
        if (isIcon(villager.getItemBySlot(EquipmentSlot.MAINHAND))) villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
    }
}
