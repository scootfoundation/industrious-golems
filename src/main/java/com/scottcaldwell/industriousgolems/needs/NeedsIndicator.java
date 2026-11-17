package com.scottcaldwell.industriousgolems.needs;

import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.offering.Offering;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.List;

/**
 * Small upkeep for each golem: it holds the item it craves in its off hand while it is dormant (the only sign of what
 * to give it) and charged golems slowly mend. The off-hand item is a display copy that never drops.
 */
public final class NeedsIndicator {

    private static final String MARKER = "industriousgolems_need_icon";
    private static final int CHECK_INTERVAL = 10;
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
        mend(golem);
        showCraving(golem);
    }

    /** A dormant golem holds out what it craves; a charged one has an empty off hand. */
    private static void showCraving(WorkGolem golem) {
        ItemStack held = golem.getItemBySlot(EquipmentSlot.OFFHAND);
        if (golem.isCharged()) {
            if (isIcon(held)) golem.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
            return;
        }
        List<Offering> offerings = golem.profession().offerings();
        int craving = golem.cravingIndex();
        if (craving < 0 || craving >= offerings.size()) return;
        ItemStack icon = offerings.get(craving).icon();
        if (isIcon(held) && ItemStack.isSameItem(held, icon)) return;
        golem.setItemSlot(EquipmentSlot.OFFHAND, markAsDisplay(icon.copyWithCount(1)));
    }

    /** Charged golems slowly mend. */
    private static void mend(WorkGolem golem) {
        if (golem.isCharged() && !golem.isDeadOrDying() && golem.getHealth() < golem.getMaxHealth()
                && golem.tickCount % REGEN_INTERVAL == 0) {
            golem.heal(1.0F);
        }
    }

    /** The icon is not a real item: never let it drop on death. */
    public static void clearOnDeath(WorkGolem golem) {
        if (isIcon(golem.getItemBySlot(EquipmentSlot.OFFHAND))) golem.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }
}
