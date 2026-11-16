package com.scottcaldwell.industriousvillagers.needs;

import com.scottcaldwell.industriousvillagers.ModAttachments;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
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
        // An earlier version showed the missing item as a head-slot item; clear any left over in saved villagers.
        if (isIcon(villager.getItemBySlot(EquipmentSlot.HEAD))) villager.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);

        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        com.scottcaldwell.industriousvillagers.inventory.InventoryLayout.organize(villager);
        if (needs == null || villager.isBaby()) {
            if (!villager.getData(ModAttachments.STATUS).equals(StatusData.NONE)) villager.setData(ModAttachments.STATUS, StatusData.NONE);
            return;
        }

        // Synced for the client's status plate: hunger fullness, whether blocked, and the tool carried.
        Hunger.isHungry(villager); // starts the hunger clock for a villager seen for the first time
        long last = villager.getData(ModAttachments.LAST_MEAL);
        long now = villager.level().getGameTime();
        int full = last == Long.MIN_VALUE ? 10
                : (int) Math.round(10.0 * (1.0 - Math.min(1.0, (double) (now - last) / Hunger.INTERVAL)));
        boolean blocked = false;
        for (ProfessionNeeds.Need need : needs.needs()) {
            if (need.missing(villager)) { blocked = true; break; }
        }
        int status = Math.max(0, Math.min(10, full)) | StatusData.ENABLED | (blocked ? StatusData.BLOCKED : 0);
        int tool = 0;
        ProfessionNeeds.Need toolNeed = needs.toolNeed();
        if (toolNeed != null) {
            for (int i = 0; i < villager.getInventory().getContainerSize(); i++) {
                ItemStack s = villager.getInventory().getItem(i);
                if (!s.isEmpty() && toolNeed.match().test(s)) { tool = BuiltInRegistries.ITEM.getId(s.getItem()) + 1; break; }
            }
        }
        StatusData updated = new StatusData(status, tool);
        if (!villager.getData(ModAttachments.STATUS).equals(updated)) villager.setData(ModAttachments.STATUS, updated);

        if (blocked && !villager.isSleeping() && villager.tickCount % SHAKE_INTERVAL == 0 && villager.getUnhappyCounter() == 0) {
            villager.setUnhappyCounter(40);
            villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, villager.getVoicePitch());
        }
    }

    /** The icon is not a real item: never let it drop on death. */
    public static void clearOnDeath(Villager villager) {
        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.MAINHAND}) {
            if (isIcon(villager.getItemBySlot(slot))) villager.setItemSlot(slot, ItemStack.EMPTY);
        }
    }
}
