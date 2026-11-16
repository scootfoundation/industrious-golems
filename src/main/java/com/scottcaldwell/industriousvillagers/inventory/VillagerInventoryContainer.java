package com.scottcaldwell.industriousvillagers.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Presents a villager's 8-slot inventory as a 9-slot (one row) chest container so the vanilla chest menu can be
 * used. The ninth slot is inert. While open, the villager stops and is marked as interacting with the player.
 */
public class VillagerInventoryContainer implements Container {

    public static final int ROW_SIZE = 9;

    private final Villager villager;
    private final SimpleContainer inventory;

    public VillagerInventoryContainer(Villager villager) {
        this.villager = villager;
        this.inventory = villager.getInventory();
    }

    private boolean real(int slot) { return slot >= 0 && slot < inventory.getContainerSize(); }

    @Override public int getContainerSize() { return ROW_SIZE; }

    @Override public boolean isEmpty() { return inventory.isEmpty(); }

    @Override public ItemStack getItem(int slot) { return real(slot) ? inventory.getItem(slot) : ItemStack.EMPTY; }

    @Override public ItemStack removeItem(int slot, int amount) { return real(slot) ? inventory.removeItem(slot, amount) : ItemStack.EMPTY; }

    @Override public ItemStack removeItemNoUpdate(int slot) { return real(slot) ? inventory.removeItemNoUpdate(slot) : ItemStack.EMPTY; }

    @Override public void setItem(int slot, ItemStack stack) { if (real(slot)) inventory.setItem(slot, stack); }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return real(slot); }

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
