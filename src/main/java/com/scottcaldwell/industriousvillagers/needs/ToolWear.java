package com.scottcaldwell.industriousvillagers.needs;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** Tool wear for any profession: each use damages the villager's tool by one, removing it (with a break sound) when spent. */
public final class ToolWear {
    private ToolWear() {}

    /** Wears the profession's first tool. */
    public static void use(ServerLevel level, Villager villager) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        use(level, villager, needs == null ? null : needs.toolNeed());
    }

    /** Wears the tool matching the need with this id. */
    public static void use(ServerLevel level, Villager villager, String toolId) {
        ProfessionNeeds needs = ProfessionNeeds.of(villager);
        if (needs == null) return;
        for (ProfessionNeeds.Need need : needs.toolNeeds()) {
            if (need.id().equals(toolId)) { use(level, villager, need); return; }
        }
    }

    private static void use(ServerLevel level, Villager villager, ProfessionNeeds.Need tool) {
        if (tool == null) return;
        SimpleContainer inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty() || !tool.match().test(stack) || !stack.isDamageableItem()) continue;
            stack.setDamageValue(stack.getDamageValue() + 1);
            if (stack.getDamageValue() >= stack.getMaxDamage()) {
                inv.setItem(i, ItemStack.EMPTY);
                level.playSound(null, villager.blockPosition(), SoundEvents.ITEM_BREAK.value(), SoundSource.NEUTRAL, 0.8F, 0.8F);
            }
            inv.setChanged();
            return;
        }
    }
}