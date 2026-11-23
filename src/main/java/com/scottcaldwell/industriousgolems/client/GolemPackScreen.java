package com.scottcaldwell.industriousgolems.client;

import com.scottcaldwell.industriousgolems.mine.GolemPackMenu;
import com.scottcaldwell.industriousgolems.mine.Kit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** The stone golem's inventory: its four supply slots, an empty one showing a faint picture of what belongs in it, over the player's inventory. */
public class GolemPackScreen extends AbstractContainerScreen<GolemPackMenu> {

    private static final int PANEL = 0xFFC6C6C6, LIGHT = 0xFFFFFFFF, DARK = 0xFF555555, WELL = 0xFF8B8B8B;
    private static final String[] LABELS = {"Posts", "Beams", "Torches", "Cobble"};

    public GolemPackScreen(GolemPackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 144);
        inventoryLabelY = 1000; // the player's inventory carries no label
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, DARK);
        graphics.fill(x, y, x + imageWidth - 2, y + imageHeight - 2, LIGHT);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, PANEL);
        for (Slot slot : menu.slots) well(graphics, x + slot.x, y + slot.y);
        for (Kit part : Kit.values()) {
            String label = LABELS[part.ordinal()];
            int width = font.width(label);
            graphics.text(font, label, x + GolemPackMenu.x(part) + 8 - width / 2, y + GolemPackMenu.KIT_Y + 20, 0xFF404040, false);
        }
    }

    private static void well(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, DARK);
        graphics.fill(x, y, x + 18, y + 18, LIGHT);
        graphics.fill(x, y, x + 17, y + 17, WELL);
    }

    /** An empty supply slot shows what goes there, faintly. */
    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        super.extractSlot(graphics, slot, mouseX, mouseY);
        if (slot.index < Kit.values().length && !slot.hasItem()) {
            graphics.fakeItem(new ItemStack(Kit.values()[slot.index].icon()), slot.x, slot.y);
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0xA08B8B8B);
        }
    }
}
