/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.pneumatic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import java.util.Locale;

/** Drawn with vanilla inventory art; no copied PneumaticCraft textures. */
public final class ScreenPressureHatch extends AbstractContainerScreen<MenuPressureHatch> {
    public ScreenPressureHatch(MenuPressureHatch menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = MenuPressureHatch.WIDTH; imageHeight = MenuPressureHatch.HEIGHT;
    }
    public static void register() { net.minecraft.client.gui.screens.MenuScreens.register(PressureRegistration.MENU.get(), ScreenPressureHatch::new); }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderBackground(graphics); super.render(graphics, x, y, partial); renderTooltip(graphics, x, y);
        if (x >= leftPos + 12 && x < leftPos + 30 && y >= topPos + 22 && y < topPos + 77)
            graphics.renderTooltip(font, Component.translatable("gui.modularmachinery.pressure.limits", number(menu.danger()), number(menu.critical())), x, y);
        else if (x >= leftPos + 162 && x < leftPos + 200 && y >= topPos + 26 && y < topPos + 64 && hoveredSlot != null && !hoveredSlot.hasItem())
            graphics.renderTooltip(font, Component.translatable("gui.modularmachinery.pressure.accepted"), x, y);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int x, int y) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF373737);
        graphics.fill(leftPos + 2, topPos + 2, leftPos + imageWidth - 2, topPos + imageHeight - 2, 0xFFC6C6C6);
        graphics.blit(new net.minecraft.resources.ResourceLocation("minecraft", "textures/gui/container/generic_54.png"), leftPos + 20, topPos + 85, 0, 126, 176, 95);
        for (int i = 0; i < 4; i++) {
            int sx = leftPos + 163 + i % 2 * 18, sy = topPos + 27 + i / 2 * 18;
            graphics.fill(sx, sy, sx + 18, sy + 18, 0xFF555555);
            graphics.fill(sx + 1, sy + 1, sx + 17, sy + 17, 0xFF8B8B8B);
        }
        int gx = leftPos + 12, gy = topPos + 22;
        graphics.fill(gx, gy, gx + 18, gy + 55, 0xFF353535);
        // Include the -1 bar vacuum range. Zero pressure is deliberately above the bottom.
        float maximum = Math.max(1, menu.critical());
        float fraction = net.minecraft.util.Mth.clamp((menu.pressure() + 1) / (maximum + 1), 0, 1);
        int height = (int) (53 * fraction);
        int color = menu.pressure() < 0 ? 0xFF62BCE0 : menu.pressure() > menu.danger() ? 0xFFDB6450 : 0xFF62B56A;
        graphics.fill(gx + 1, gy + 54 - height, gx + 17, gy + 54, color);
        int marker = gy + 54 - (int) (53 * (menu.danger() + 1) / (maximum + 1));
        graphics.fill(gx + 1, marker, gx + 17, marker + 1, 0xFFFFD260);
    }
    private static String number(float value) { return String.format(Locale.ROOT, "%.2f", value); }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), imageWidth - 16), 8, 6, 0x404040, false);
        graphics.drawString(font, number(menu.pressure()) + " bar", 36, 23, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.modularmachinery.pressure.air", menu.air()), 36, 36, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.modularmachinery.pressure.volume", menu.volume()), 36, 49, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.modularmachinery.pressure.temperature", menu.temperature()), 36, 61, 0x404040, false);
        graphics.drawString(font, Component.translatable(menu.safety() ? "gui.modularmachinery.pressure.safe" : "gui.modularmachinery.pressure.unsafe"), 36, 74, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.modularmachinery.pressure.upgrades"), 164, 16, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 28, 86, 0x404040, false);
    }
}
