package com.scottcaldwell.industriousvillagers.needs;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Shows what a blocked villager is missing: the first blocking need it holds none of appears on its head (an
 * equipment-slot item, so it renders with no client code), and every few seconds it shakes its head.
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
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        ItemStack head = villager.getItemBySlot(EquipmentSlot.HEAD);
        boolean showing = isIcon(head);
        ProfessionNeeds.Need missing = null;
        if (needs != null && !villager.isSleeping() && !villager.isBaby()) {
            for (ProfessionNeeds.Need need : needs.needs()) {
                if (need.missing(villager)) { missing = need; break; }
            }
        }

        if (missing == null) {
            if (showing) villager.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            return;
        }
        if (!showing || !head.is(missing.icon().getItem())) {
            villager.setItemSlot(EquipmentSlot.HEAD, markAsDisplay(missing.icon().copy()));
        }
        if (villager.tickCount % SHAKE_INTERVAL == 0 && villager.getUnhappyCounter() == 0) {
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
