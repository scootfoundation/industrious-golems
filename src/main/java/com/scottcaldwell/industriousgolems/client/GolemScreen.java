package com.scottcaldwell.industriousgolems.client;

import com.scottcaldwell.industriousgolems.inventory.GolemMenu;
import com.scottcaldwell.industriousgolems.offering.Offering;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Draws the golem inventory on the vanilla one-row chest texture, blanks the gap column, tints the offering slot and
 * shows what the golem craves (a faint item in the empty offering slot, and its name) and how long it will run.
 */
public class GolemScreen extends AbstractContainerScreen<GolemMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PANEL = 0xFFC6C6C6;
    private static final int OFFERING_TINT = 0xFFD6C28A;
    private static final int GHOST = 0xB0C6C6C6;
    private static final int TEXT = 0xFF404040;

    public GolemScreen(GolemMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = GolemMenu.IMAGE_HEIGHT;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int left = (this.width - this.imageWidth) / 2;
        int top = (this.height - this.imageHeight) / 2;
        // same two-part blit as the vanilla one-row container screen
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F, this.imageWidth, 18 + 17, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top + 18 + 17, 0.0F, 126.0F, this.imageWidth, 96, 256, 256);

        // blank every container column that is not a slot of this golem (the gap, and any unused columns)
        for (int col = 0; col < GolemMenu.ROW_COLUMNS; col++) {
            boolean isOffering = col == GolemMenu.OFFERING_COLUMN;
            boolean isGeneral = col >= GolemMenu.FIRST_GENERAL_COLUMN && col < GolemMenu.FIRST_GENERAL_COLUMN + this.menu.generalSlots();
            if (isOffering || isGeneral) continue;
            int x = left + GolemMenu.slotX(col) - 1;
            int y = top + GolemMenu.ROW_Y - 1;
            graphics.fill(x, y, x + 18, y + 18, PANEL);
        }

        // the offering slot: tinted, with a faint picture of what the golem craves while it is empty
        int slotX = left + GolemMenu.slotX(GolemMenu.OFFERING_COLUMN);
        int slotY = top + GolemMenu.ROW_Y;
        graphics.fill(slotX, slotY, slotX + 16, slotY + 16, OFFERING_TINT);
        Offering craving = this.menu.craving();
        if (craving != null && this.menu.acceptsMore() && !this.menu.getSlot(0).hasItem()) {
            graphics.renderFakeItem(craving.icon(), slotX, slotY);
            graphics.fill(slotX, slotY, slotX + 16, slotY + 16, GHOST);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        int seconds = this.menu.chargeSeconds();
        Component charge = seconds > 0
                ? Component.translatable("gui.industriousgolems.charge", seconds / 60, String.format("%02d", seconds % 60))
                : Component.translatable("gui.industriousgolems.dormant");
        graphics.drawString(this.font, charge, this.imageWidth - 8 - this.font.width(charge), this.titleLabelY, TEXT, false);

        Offering craving = this.menu.craving();
        if (craving != null && this.menu.acceptsMore()) {
            Component wants = Component.translatable("gui.industriousgolems.wants", craving.displayName());
            graphics.drawString(this.font, wants, this.imageWidth - 8 - this.font.width(wants), this.inventoryLabelY, TEXT, false);
        }
    }
}
