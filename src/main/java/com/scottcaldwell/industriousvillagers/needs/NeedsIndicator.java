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

    /** Called every tick on the server for each villager. */
    public static void tick(Villager villager) {
        if (villager.tickCount % CHECK_INTERVAL != 0) return;
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        ItemStack head = villager.getItemBySlot(EquipmentSlot.HEAD);
        boolean showing = isIcon(head);
        ProfessionNeeds.Need missing = null;
        if (needs != null && !villager.isSleeping() && !villager.isBaby()) {
            for (ProfessionNeeds.Need need : needs.needs()) {
                if (need.blocking() && need.have(villager.getInventory()) == 0) { missing = need; break; }
            }
        }

        if (missing == null) {
            if (showing) villager.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
            return;
        }
        if (!showing || !head.is(missing.icon().getItem())) {
            ItemStack icon = missing.icon().copy();
            CompoundTag tag = new CompoundTag();
            tag.putBoolean(MARKER, true);
            icon.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
            villager.setItemSlot(EquipmentSlot.HEAD, icon);
        }
        if (villager.tickCount % SHAKE_INTERVAL == 0 && villager.getUnhappyCounter() == 0) {
            villager.setUnhappyCounter(40);
            villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, villager.getVoicePitch());
        }
    }

    /** The icon is not a real item: never let it drop on death. */
    public static void clearOnDeath(Villager villager) {
        if (isIcon(villager.getItemBySlot(EquipmentSlot.HEAD))) villager.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
    }
}
