/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO GuiEFabricatorPatternBus. */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import hellfirepvp.modularmachinery.common.menu.MenuEFabricatorPatternBus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public final class ScreenEFabricatorPatternBus extends AbstractContainerScreen<MenuEFabricatorPatternBus> {
    private static final ResourceLocation BG = new ResourceLocation("modularmachinery", "textures/gui/neco/efabricator_pattern_bus.png");
    public ScreenEFabricatorPatternBus(MenuEFabricatorPatternBus menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = MenuEFabricatorPatternBus.WIDTH; imageHeight = MenuEFabricatorPatternBus.HEIGHT;
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend(); com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) {
        g.drawString(font, SmoothUiFonts.compact(Component.translatable("gui.modularmachinery.ef.pattern_bus")), 8, 10, 0xFF404040, false);
        if (!menu.readable()) g.drawString(font, SmoothUiFonts.compact(Component.translatable("gui.modularmachinery.ef.pattern_bus_unknown")), 8, 20, 0xFF9F3024, false);
        g.drawString(font, SmoothUiFonts.compact(playerInventoryTitle), 35, 138, 0xFF404040, false);
    }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) { renderBackground(g); super.render(g, mx, my, partial); renderTooltip(g, mx, my); }
}
