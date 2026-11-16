package com.scottcaldwell.industriousvillagers.inventory;

import com.scottcaldwell.industriousvillagers.needs.Need;
import com.scottcaldwell.industriousvillagers.IndustriousVillagers;
import com.scottcaldwell.industriousvillagers.needs.ProfessionNeeds;
import net.minecraft.resources.Identifier;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.function.Predicate;

/**
 * The inventory screen of a profession villager: one dedicated tool slot (container slot 0) and as many general
 * slots as the profession is allowed, laid out in a single row above the player's inventory.
 */
public class VillagerMenu extends AbstractContainerMenu {

    /** Slots in a villager's inventory; the tool slot plus general slots never exceed this. */
    public static final int CONTAINER_SIZE = 8;
    /** Columns in the single container row (matches the vanilla chest texture). */
    public static final int ROW_COLUMNS = 9;
    public static final int ROW_Y = 18;
    public static final int IMAGE_HEIGHT = 132;

    private final Container container;
    private final int toolSlots;
    private final int generalSlots;

    public VillagerMenu(MenuType<VillagerMenu> type, int id, Inventory playerInventory, Container container, ProfessionNeeds needs) {
        super(type, id);
        this.container = container;
        java.util.List<Need> tools = needs.toolNeeds();
        this.toolSlots = tools.size();
        this.generalSlots = Math.max(0, Math.min(CONTAINER_SIZE - toolSlots, ROW_COLUMNS - firstGeneralColumn()));
        container.startOpen(playerInventory.player);

        // one dedicated slot per tool, then a gap column, then the general slots
        for (int i = 0; i < toolSlots; i++) {
            Need tool = tools.get(i);
            addSlot(new ToolSlot(container, i, slotX(i), ROW_Y, tool.match(),
                    Identifier.fromNamespaceAndPath(IndustriousVillagers.MODID, "container/slot/" + tool.id())));
        }
        for (int i = 0; i < this.generalSlots; i++) {
            addSlot(new Slot(container, toolSlots + i, slotX(firstGeneralColumn() + i), ROW_Y));
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
    }

    public static int slotX(int column) {
        return 8 + column * 18;
    }

    /** Number of dedicated tool slots (columns 0..n-1). */
    public int toolSlots() {
        return toolSlots;
    }

    /** General slots start after the tool slots, leaving one column as a gap. */
    public int firstGeneralColumn() {
        return toolSlots == 0 ? 0 : toolSlots + 1;
    }

    public int generalSlots() {
        return generalSlots;
    }

    /** Server side: the menu over a real villager's inventory. */
    public static VillagerMenu forVillager(MenuType<VillagerMenu> type, int id, Inventory playerInventory, Villager villager) {
        return new VillagerMenu(type, id, playerInventory, new VillagerInventoryContainer(villager), ProfessionNeeds.effective(villager));
    }

    /** Client side: contents arrive through normal slot sync; the layout comes from the profession's menu type. */
    public static VillagerMenu client(MenuType<VillagerMenu> type, int id, Inventory playerInventory, ProfessionNeeds needs) {
        return new VillagerMenu(type, id, playerInventory, new SimpleContainer(CONTAINER_SIZE), needs);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack moved = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot.hasItem()) {
            ItemStack stack = slot.getItem();
            moved = stack.copy();
            int villagerSlots = toolSlots + generalSlots;
            if (index < villagerSlots) {
                if (!moveItemStackTo(stack, villagerSlots, this.slots.size(), true)) return ItemStack.EMPTY;
            } else if (!moveItemStackTo(stack, 0, villagerSlots, false)) {
                return ItemStack.EMPTY;
            }
            if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
            else slot.setChanged();
        }
        return moved;
    }

    /** The dedicated tool slot: only accepts the profession's tool, and shows an outline of it when empty. */
    public static class ToolSlot extends Slot {
        private final Predicate<ItemStack> rule;

        ToolSlot(Container container, int index, int x, int y, Predicate<ItemStack> rule, Identifier emptyIcon) {
            super(container, index, x, y);
            this.rule = rule;
            setBackground(emptyIcon);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return rule.test(stack);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
