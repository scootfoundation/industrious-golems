package com.scottcaldwell.industriousvillagers.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The villager's own inventory as a menu container. While open, the villager stops and is marked as interacting with
 * the player.
 */
public class VillagerInventoryContainer implements Container {

    private final Villager villager;
    private final SimpleContainer inventory;

    public VillagerInventoryContainer(Villager villager) {
        this.villager = villager;
        this.inventory = villager.getInventory();
    }

    @Override public int getContainerSize() { return inventory.getContainerSize(); }

    @Override public boolean isEmpty() { return inventory.isEmpty(); }

    @Override public ItemStack getItem(int slot) { return inventory.getItem(slot); }

    @Override public ItemStack removeItem(int slot, int amount) { return inventory.removeItem(slot, amount); }

    @Override public ItemStack removeItemNoUpdate(int slot) { return inventory.removeItemNoUpdate(slot); }

    @Override public void setItem(int slot, ItemStack stack) { inventory.setItem(slot, stack); }

    @Override public void setChanged() { inventory.setChanged(); }

    @Override
    public boolean stillValid(Player player) {
        return villager.isAlive() && player.distanceToSqr(villager) <= 8.0 * 8.0;
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (user instanceof Player player) {
            villager.setTradingPlayer(player); // stops the villager walking off while the screen is open
            villager.getNavigation().stop();
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (user instanceof Player player && villager.getTradingPlayer() == player) villager.setTradingPlayer(null);
    }

    @Override public void clearContent() { inventory.clearContent(); }
}
