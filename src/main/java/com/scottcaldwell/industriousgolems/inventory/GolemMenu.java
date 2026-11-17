package com.scottcaldwell.industriousgolems.inventory;

import com.scottcaldwell.industriousgolems.ModProfessions;
import com.scottcaldwell.industriousgolems.golem.WorkGolem;
import com.scottcaldwell.industriousgolems.offering.Offering;
import com.scottcaldwell.industriousgolems.offering.Offerings;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The inventory screen of a work golem: the offering slot (menu slot 0) that takes what the golem craves, a gap, and
 * the general slots over the golem's inventory, laid out in a single row above the player's inventory.
 */
public class GolemMenu extends AbstractContainerMenu {

    /** Slots in a golem's inventory; they are all shown as general slots. */
    public static final int CONTAINER_SIZE = 7;
    /** Columns in the single container row (matches the vanilla chest texture). */
    public static final int ROW_COLUMNS = 9;
    public static final int ROW_Y = 18;
    public static final int IMAGE_HEIGHT = 132;
    /** The offering slot's column; the next column is a gap and the general slots follow. */
    public static final int OFFERING_COLUMN = 0;
    public static final int FIRST_GENERAL_COLUMN = 2;

    private final Container container;
    private final Player player;
    private final List<Offering> offerings;
    private final int generalSlots;
    /** The offering slot's own one-slot box: an item placed there is taken by the golem on the next sync. */
    private final SimpleContainer offeringBox = new SimpleContainer(1);
    private final DataSlot craving = DataSlot.standalone();
    private final DataSlot charge = DataSlot.standalone();
    private final @Nullable WorkGolem golem;

    private GolemMenu(MenuType<GolemMenu> type, int id, Inventory playerInventory, Container container,
                      ModProfessions.Profession profession, @Nullable WorkGolem golem) {
        super(type, id);
        this.container = container;
        this.player = playerInventory.player;
        this.offerings = profession.offerings();
        this.golem = golem;
        this.generalSlots = Math.min(CONTAINER_SIZE, ROW_COLUMNS - FIRST_GENERAL_COLUMN);
        container.startOpen(playerInventory.player);

        addSlot(new OfferingSlot(offeringBox, slotX(OFFERING_COLUMN), ROW_Y));
        for (int i = 0; i < generalSlots; i++) {
            addSlot(new Slot(container, i, slotX(FIRST_GENERAL_COLUMN + i), ROW_Y));
        }
        // player inventory and hotbar, positioned as in a one-row chest
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, slotX(col), 49 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, slotX(col), 107));
        }
        addDataSlot(craving);
        addDataSlot(charge);
        if (golem != null) syncFromGolem();
    }

    public static int slotX(int column) {
        return 8 + column * 18;
    }

    /** Number of general slots, starting at column {@link #FIRST_GENERAL_COLUMN}. */
    public int generalSlots() {
        return generalSlots;
    }

    /** Server side: the menu over a real golem's inventory. */
    public static GolemMenu forGolem(MenuType<GolemMenu> type, int id, Inventory playerInventory, WorkGolem golem) {
        return new GolemMenu(type, id, playerInventory, new GolemInventoryContainer(golem), golem.profession(), golem);
    }

    /** Client side: contents arrive through normal slot sync; the craving and charge through data slots. */
    public static GolemMenu client(MenuType<GolemMenu> type, int id, Inventory playerInventory, ModProfessions.Profession profession) {
        return new GolemMenu(type, id, playerInventory, new SimpleContainer(CONTAINER_SIZE), profession, null);
    }

    // ---- what the golem craves ------------------------------------------------------------------------------------

    /** The offering the golem craves now, or null before it has chosen. */
    public @Nullable Offering craving() {
        int i = craving.get();
        return i >= 0 && i < offerings.size() ? offerings.get(i) : null;
    }

    public int chargeSeconds() {
        return charge.get();
    }

    /** Whether the golem has room for another offering. */
    public boolean acceptsMore() {
        return charge.get() <= Offerings.ACCEPTS_BELOW_SECONDS;
    }

    private void syncFromGolem() {
        craving.set(golem.cravingIndex());
        charge.set(golem.chargeSeconds());
    }

    @Override
    public void broadcastChanges() {
        if (golem != null) {
            ItemStack offered = offeringBox.getItem(0);
            if (!offered.isEmpty()) { // the golem takes it; anything it will not take goes back to the player
                Offerings.offer(golem, offered);
                if (offered.isEmpty()) offeringBox.setItem(0, ItemStack.EMPTY);
                else player.getInventory().placeItemBackInInventory(offeringBox.removeItemNoUpdate(0));
            }
            syncFromGolem();
        }
        super.broadcastChanges();
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        clearContainer(player, offeringBox);
        container.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            int golemSlots = 1 + generalSlots;
            if (index < golemSlots) {
                if (!moveItemStackTo(stack, golemSlots, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 0, golemSlots, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return moved;
    }

    /** Takes only the item the golem craves right now (and only while it has room), one at a time. */
    private class OfferingSlot extends Slot {
        OfferingSlot(Container container, int x, int y) {
            super(container, 0, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            Offering wanted = craving();
            return wanted != null && acceptsMore() && wanted.match().test(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
