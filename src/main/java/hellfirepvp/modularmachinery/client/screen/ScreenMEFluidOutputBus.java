/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuMEFluidOutputBus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;

/** Client screen for the source mod's nine read-only ME fluid output tanks. */
public class ScreenMEFluidOutputBus extends AbstractContainerScreen<MenuMEFluidOutputBus> {
    private static final int SCREEN_WIDTH = 220;
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            Modularmachinery.MODID, "textures/gui/mefluidoutputbus.png");

    public ScreenMEFluidOutputBus(MenuMEFluidOutputBus menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = SCREEN_WIDTH;
        imageHeight = MenuMEFluidOutputBus.PANEL_HEIGHT;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, MenuMEFluidOutputBus.PANEL_WIDTH, imageHeight, 256, 256);
        int panelX = leftPos + MenuMEFluidOutputBus.PANEL_WIDTH;
        graphics.fill(panelX, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF3A3A3A);
        graphics.fill(panelX + 1, topPos + 1, leftPos + imageWidth - 1, topPos + imageHeight - 1, 0xFFC6C6C6);
        var atlas = net.minecraft.client.Minecraft.getInstance()
                .getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS);
        for (int i = 0; i < MenuMEFluidOutputBus.TANK_SLOTS; i++) {
            FluidStack fluid = menu.fluidFor(i);
            if (fluid.isEmpty()) continue;
            var extension = IClientFluidTypeExtensions.of(fluid.getFluid());
            ResourceLocation still = extension.getStillTexture();
            if (still == null) continue;
            int tint = extension.getTintColor();
            var sprite = atlas.apply(still);
            int x = leftPos + 8 + i * 18;
            int y = topPos + 26;
            int height = Math.max(1, Math.min(68, Math.round(68.0f * fluid.getAmount()
                    / (float) menu.capacityForDisplay())));
            graphics.blit(x, y + 68 - height, 0, 16, height, sprite,
                    ((tint >> 16) & 255) / 255.0f, ((tint >> 8) & 255) / 255.0f,
                    (tint & 255) / 255.0f, 1.0f);
        }
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.mefluidoutputbus.title"), 8, 8, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.mefluidoutputbus.inventory"), 8,
                MenuMEFluidOutputBus.PLAYER_INV_Y - 12, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.mefluidinputbus.upgrades"),
                MenuMEFluidOutputBus.PANEL_WIDTH + 3, 3, 0x404040, false);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = 0; index < MenuMEFluidOutputBus.TANK_SLOTS; index++) {
            int x = leftPos + 8 + index * 18;
            int y = topPos + 26;
            if (mouseX < x || mouseX >= x + 16 || mouseY < y || mouseY >= y + 68) continue;
            FluidStack fluid = menu.fluidFor(index);
            if (!fluid.isEmpty()) {
                graphics.renderTooltip(font, Component.translatable("gui.mefluidoutputbus.fluid.tooltip",
                        fluid.getAmount(), Component.translatable(fluid.getFluid().getFluidType().getDescriptionId())),
                        mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
