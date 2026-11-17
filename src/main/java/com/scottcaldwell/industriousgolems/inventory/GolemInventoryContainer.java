package com.scottcaldwell.industriousgolems.inventory;

import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ContainerUser;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The golem's own inventory as a menu container. While open, the golem stops and is marked as interacting with
 * the player.
 */
public class GolemInventoryContainer implements Container {

    private final WorkGolem golem;
    private final SimpleContainer inventory;

    public GolemInventoryContainer(WorkGolem golem) {
        this.golem = golem;
        this.inventory = golem.getInventory();
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
        return golem.isAlive() && player.distanceToSqr(golem) <= 8.0 * 8.0;
    }

    @Override
    public void startOpen(ContainerUser user) {
        if (user instanceof Player player) {
            golem.setTradingPlayer(player); // stops the golem walking off while the screen is open
            golem.getNavigation().stop();
        }
    }

    @Override
    public void stopOpen(ContainerUser user) {
        if (user instanceof Player player && golem.getTradingPlayer() == player) golem.setTradingPlayer(null);
    }

    @Override public void clearContent() { inventory.clearContent(); }
}
