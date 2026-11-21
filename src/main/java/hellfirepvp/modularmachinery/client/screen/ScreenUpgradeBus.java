/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuUpgradeBus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/** Source-style slot grid and a scrollable view of the installed modules. */
public class ScreenUpgradeBus extends AbstractContainerScreen<MenuUpgradeBus> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/guiupgradebus.png");
    private static final int DESCRIPTION_X = 66;
    private static final int DESCRIPTION_Y = 17;
    private static final int DESCRIPTION_WIDTH = 120;
    private static final int DESCRIPTION_LINES = 15;
    private static final int SCROLLBAR_X = 162;
    private static final int SCROLLBAR_Y = 17;
    private static final int SCROLLBAR_HEIGHT = 106;
    private static final float DESCRIPTION_SCALE = 0.72F;
    private List<Component> lastDescription;
    private List<FormattedCharSequence> wrappedDescription = List.of();
    private int scrollOffset;
    private int maxScroll;
    private boolean draggingScrollbar;

    public ScreenUpgradeBus(MenuUpgradeBus menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = MenuUpgradeBus.PANEL_WIDTH;
        this.imageHeight = MenuUpgradeBus.PANEL_HEIGHT;
        // The player-inventory label has to move with the inventory, which sits at 131 here rather than the
        // 84 every other screen uses. Vanilla's default offset would put the label over the upgrade grid.
        this.inventoryLabelY = 131 - 11;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        for (int index = 0; index < MenuUpgradeBus.GRID_SIZE; index++) {
            int x = leftPos + MenuUpgradeBus.upgradeX(index) - 1;
            int y = topPos + MenuUpgradeBus.upgradeY(index) - 1;
            graphics.blit(TEXTURE, x, y, 7, 130, 18, 18, 256, 256);
            if (index >= menu.getUpgradeSlotCount()) graphics.fill(x, y, x + 18, y + 18, 0x94000000);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        List<Component> description = menu.descriptions();
        if (lastDescription != description) {
            lastDescription = description;
            var lines = new ArrayList<FormattedCharSequence>();
            for (Component line : description) {
                if (line.getString().isEmpty()) lines.add(FormattedCharSequence.EMPTY);
                else lines.addAll(font.split(line, DESCRIPTION_WIDTH));
            }
            wrappedDescription = List.copyOf(lines);
            maxScroll = Math.max(0, lines.size() - DESCRIPTION_LINES);
            scrollOffset = Math.min(scrollOffset, maxScroll);
        }

        graphics.pose().pushPose();
        graphics.pose().translate(DESCRIPTION_X, DESCRIPTION_Y, 0);
        graphics.pose().scale(DESCRIPTION_SCALE, DESCRIPTION_SCALE, 1);
        for (int index = 0; index < DESCRIPTION_LINES && scrollOffset + index < wrappedDescription.size(); index++) {
            graphics.drawString(font, wrappedDescription.get(scrollOffset + index), 0, index * 10, 0xFFFFFF, true);
        }
        graphics.pose().popPose();
        drawScrollbar(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        for (int index = menu.getUpgradeSlotCount(); index < MenuUpgradeBus.GRID_SIZE; index++) {
            if (isHovering(MenuUpgradeBus.upgradeX(index), MenuUpgradeBus.upgradeY(index), 16, 16, mouseX, mouseY)) {
                graphics.renderTooltip(font, Component.translatable("gui.modularmachinery.upgrade_bus.locked"), mouseX, mouseY);
                break;
            }
        }
    }

    public static void receive(hellfirepvp.modularmachinery.common.network.UpgradeBusInfoPacket packet) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof MenuUpgradeBus menu
                && menu.containerId == packet.window() && menu.getBusPos().equals(packet.pos())) {
            menu.setDescriptions(packet.lines());
        }
    }

    private void drawScrollbar(GuiGraphics graphics) {
        if (maxScroll <= 0) return;
        int x = SCROLLBAR_X;
        graphics.fill(x, SCROLLBAR_Y, x + 2, SCROLLBAR_Y + SCROLLBAR_HEIGHT, 0x55333333);
        int thumbHeight = Math.max(12, SCROLLBAR_HEIGHT * DESCRIPTION_LINES / (DESCRIPTION_LINES + maxScroll));
        int thumbY = SCROLLBAR_Y + (SCROLLBAR_HEIGHT - thumbHeight) * scrollOffset / maxScroll;
        graphics.fill(x - 1, thumbY, x + 3, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (maxScroll > 0 && mouseX >= leftPos + DESCRIPTION_X - 4 && mouseX <= leftPos + SCROLLBAR_X + 8
                && mouseY >= topPos + SCROLLBAR_Y && mouseY <= topPos + SCROLLBAR_Y + SCROLLBAR_HEIGHT) {
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset - (delta > 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && maxScroll > 0 && overScrollbar(mouseX, mouseY)) {
            draggingScrollbar = true;
            setScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar && button == 0) {
            setScrollFromMouse(mouseY);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean overScrollbar(double mouseX, double mouseY) {
        return mouseX >= leftPos + SCROLLBAR_X - 4 && mouseX <= leftPos + SCROLLBAR_X + 8
                && mouseY >= topPos + SCROLLBAR_Y && mouseY <= topPos + SCROLLBAR_Y + SCROLLBAR_HEIGHT;
    }

    private void setScrollFromMouse(double mouseY) {
        double fraction = (mouseY - (topPos + SCROLLBAR_Y)) / (double) SCROLLBAR_HEIGHT;
        scrollOffset = Math.max(0, Math.min(maxScroll, (int) Math.round(fraction * maxScroll)));
    }
}
