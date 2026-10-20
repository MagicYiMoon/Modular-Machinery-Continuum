/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ScreenLifeEssence extends AbstractContainerScreen<MenuLifeEssence> {
    public ScreenLifeEssence(MenuLifeEssence menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 176; imageHeight = 166;
    }
    public static void register() { net.minecraft.client.gui.screens.MenuScreens.register(BloodMagicRegistration.MENU.get(), ScreenLifeEssence::new); }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderBackground(graphics); super.render(graphics, x, y, partial); renderTooltip(graphics, x, y);
        if (x >= leftPos + 25 && x < leftPos + 43 && y >= topPos + 34 && y < topPos + 52 && !menu.getSlot(0).hasItem())
            graphics.renderTooltip(font, Component.translatable("gui.modularmachinery.lifeessence.orb"), x, y);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int x, int y) {
        graphics.blit(new net.minecraft.resources.ResourceLocation("minecraft", "textures/gui/container/generic_54.png"), leftPos, topPos, 0, 0, 176, 71);
        graphics.fill(leftPos + 7, topPos + 15, leftPos + 169, topPos + 71, 0xFFC6C6C6);
        graphics.blit(new net.minecraft.resources.ResourceLocation("minecraft", "textures/gui/container/generic_54.png"), leftPos, topPos + 71, 0, 126, 176, 95);
        graphics.fill(leftPos + 25, topPos + 34, leftPos + 43, topPos + 52, 0xFF555555);
        graphics.fill(leftPos + 26, topPos + 35, leftPos + 42, topPos + 51, 0xFF8B8B8B);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), 160), 8, 6, 0x404040, false);
        graphics.drawString(font, Component.translatable(menu.bound() ? "gui.modularmachinery.lifeessence.bound" : "gui.modularmachinery.lifeessence.unbound"), 8, 20, 0x404040, false);
        graphics.drawString(font, menu.amount() + " LP", 52, 34, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.modularmachinery.lifeessence.capacity", menu.capacity()), 52, 47, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, 72, 0x404040, false);
    }
}
