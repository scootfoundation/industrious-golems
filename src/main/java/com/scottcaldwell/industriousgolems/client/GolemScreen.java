package com.scottcaldwell.industriousgolems.client;

import com.scottcaldwell.industriousgolems.inventory.GolemMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Draws the golem inventory on the vanilla one-row chest texture, then blanks every container slot the
 * profession is not allowed to use and gives the dedicated tool slot its own tint.
 */
public class GolemScreen extends AbstractContainerScreen<GolemMenu> {

    private static final Identifier BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int PANEL = 0xFFC6C6C6;
    private static final int TOOL_TINT = 0xFF9DB4D6;

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

        // blank every container column that is not a slot of this golem
        int firstGeneral = this.menu.firstGeneralColumn();
        for (int col = 0; col < GolemMenu.ROW_COLUMNS; col++) {
            boolean isTool = col < this.menu.toolSlots();
            boolean isGeneral = col >= firstGeneral && col < firstGeneral + this.menu.generalSlots();
            if (isTool || isGeneral) continue;
            int x = left + GolemMenu.slotX(col) - 1;
            int y = top + GolemMenu.ROW_Y - 1;
            graphics.fill(x, y, x + 18, y + 18, PANEL);
        }
        // tool slots: tinted so they read as special
        for (int i = 0; i < this.menu.toolSlots(); i++) {
            int toolX = left + GolemMenu.slotX(i);
            int toolY = top + GolemMenu.ROW_Y;
            graphics.fill(toolX, toolY, toolX + 16, toolY + 16, TOOL_TINT);
        }
    }
}
