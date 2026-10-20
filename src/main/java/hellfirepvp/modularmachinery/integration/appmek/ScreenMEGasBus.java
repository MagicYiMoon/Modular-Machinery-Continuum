/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.appmek;

import mekanism.api.chemical.gas.GasStack;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.InventoryMenu;

/** The source gas-bus geometry: nine config slots over nine vertical buffer gauges. */
public final class ScreenMEGasBus extends AbstractContainerScreen<MenuMEGasBus> {
    public ScreenMEGasBus(MenuMEGasBus menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = 210; imageHeight = menu.panelHeight(); inventoryLabelY = imageHeight - 94;
    }
    public static void register() { net.minecraft.client.gui.screens.MenuScreens.register(GasBusRegistration.MENU.get(), ScreenMEGasBus::new); }
    @Override public void render(GuiGraphics graphics, int x, int y, float partial) {
        renderBackground(graphics); super.render(graphics, x, y, partial); renderTooltip(graphics, x, y);
        for (int slot = 0; slot < 9; slot++) {
            int sx = leftPos + 8 + 18 * slot;
            boolean configured = menu.isInput() && y >= topPos + 35 && y < topPos + 51;
            if (x >= sx && x < sx + 16 && (configured || y >= topPos + menu.tankY() && y < topPos + menu.tankY() + 68)) {
                var gas = menu.gas(slot, configured);
                if (!gas.isEmpty()) graphics.renderTooltip(font, java.util.List.of(gas.getTextComponent(),
                        Component.literal((configured ? menu.capacity(slot) : gas.getAmount()) + " / " + menu.capacity(slot) + " mB")), java.util.Optional.empty(), x, y);
            }
        }
    }
    @Override protected void renderBg(GuiGraphics graphics, float partial, int mouseX, int mouseY) {
        // The source gas screens use the fluid-bus textures, with distinct input/output heights.
        var texture = new net.minecraft.resources.ResourceLocation("modularmachinery", "textures/gui/" +
                (menu.isInput() ? "mefluidinputbus.png" : "mefluidoutputbus.png"));
        graphics.blit(texture, leftPos, topPos, 0, 0, 176, imageHeight, 256, 256);
        graphics.fill(leftPos + 176, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFFC6C6C6);
        for (int slot = 0; slot < 9; slot++) {
            int x = leftPos + 8 + slot * 18;
            if (menu.isInput()) drawGas(graphics, menu.gas(slot, true), x, topPos + 35, 16);
            var stored = menu.gas(slot, false);
            int height = (int) Math.min(68, stored.isEmpty() ? 0 : Math.max(1, (double) stored.getAmount() / Math.max(1, menu.capacity(slot)) * 68));
            drawGas(graphics, stored, x, topPos + menu.tankY() + 68 - height, height);
        }
        for (var slot : menu.slots) if (slot.index >= 45) {
            graphics.fill(leftPos + slot.x - 1, topPos + slot.y - 1, leftPos + slot.x + 17, topPos + slot.y + 17, 0xFF888888);
            graphics.fill(leftPos + slot.x, topPos + slot.y, leftPos + slot.x + 16, topPos + slot.y + 16, 0xFF444444);
        }
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, font.plainSubstrByWidth(title.getString(), 162), 8, 6, 0x404040, false);
        graphics.drawString(font, Component.translatable(menu.online() ? "gui.modularmachinery.gas_bus.online" : "gui.modularmachinery.gas_bus.offline"), 8, menu.isInput() ? 20 : 98, 0x404040, false);
        if (menu.isInput()) graphics.drawString(font, Component.translatable("gui.modularmachinery.gas_bus.configure"), 8, 126, 0x404040, false);
        graphics.drawString(font, playerInventoryTitle, 8, imageHeight - 94, 0x404040, false);
    }
    private void drawGas(GuiGraphics graphics, GasStack gas, int x, int y, int height) {
        if (gas.isEmpty() || height <= 0) return;
        var sprite = minecraft.getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(gas.getType().getIcon());
        int tint = gas.getType().getTint();
        graphics.setColor(((tint >> 16) & 255) / 255F, ((tint >> 8) & 255) / 255F, (tint & 255) / 255F, 1F);
        for (int row = 0; row < height; row += 16) graphics.blit(x, y + row, 0, 16, Math.min(16, height - row), sprite);
        graphics.setColor(1, 1, 1, 1);
    }
}
