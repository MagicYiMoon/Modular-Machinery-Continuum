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
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The upgrade bus screen.
 *
 * <p>Blits the texture the jar's own {@code GuiContainerUpgradeBus} names — {@code guiupgradebus.png} —
 * read out of that class's constant pool rather than guessed. Nothing is drawn in code: the frame, the
 * upgrade-slot wells and the player-inventory area all come from the mod's own art, which is authored
 * against the same 3-column layout at (8,17) that {@link MenuUpgradeBus} places its slots from.
 *
 * <p>The panel is <b>176x213</b>, taller than the 176x166 used elsewhere in this mod, because the upgrade
 * grid pushes the player inventory down to y=131 and the hotbar to y=189. Taking the standard height here
 * would clip both.
 */
public class ScreenUpgradeBus extends AbstractContainerScreen<MenuUpgradeBus> {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/guiupgradebus.png");
    private static final int DESCRIPTION_X = 92;
    private static final int DESCRIPTION_Y = 23;
    private static final int DESCRIPTION_WIDTH = 80;
    private static final int DESCRIPTION_LINES = 15;
    private static final int SCROLLBAR_X = 156;
    private static final int SCROLLBAR_Y = 17;
    private static final int SCROLLBAR_HEIGHT = 106;
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
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        if (minecraft == null || minecraft.level == null) return;
        if (!(minecraft.level.getBlockEntity(menu.getBusPos()) instanceof TileUpgradeBus bus)) return;
        List<FormattedCharSequence> lines = descriptionLines(bus);
        maxScroll = Math.max(0, lines.size() - DESCRIPTION_LINES);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        int y = DESCRIPTION_Y;
        for (int index = scrollOffset; index < Math.min(lines.size(), scrollOffset + DESCRIPTION_LINES); index++) {
            graphics.drawString(font, lines.get(index), DESCRIPTION_X, y, 0xFFFFFF, true);
            y += 10;
        }
        drawScrollbar(graphics);
    }

    /** Builds the same bounded-machine section and per-upgrade section as the source GUI. */
    private List<FormattedCharSequence> descriptionLines(TileUpgradeBus bus) {
        List<FormattedCharSequence> result = new ArrayList<>();
        String machineName = bus.getBoundMachineName();
        if (machineName == null || machineName.isBlank()) {
            addWrapped(result, Component.translatable("gui.upgradebus.bounded.empty"));
        } else {
            addWrapped(result, Component.translatable("gui.upgradebus.bounded", machineName));
            addWrapped(result, Component.literal("  " + bus.getControllerPos().toShortString()));
            for (var upgrade : bus.getInstalledUpgrades()) {
                if (!upgrade.getType().isCompatible(machineName)) {
                    addWrapped(result, Component.translatable("gui.upgradebus.incompatible",
                            upgrade.getType().getLocalizedName()));
                }
            }
            result.add(FormattedCharSequence.EMPTY);
        }

        // The source GUI still lists every installed upgrade; incompatibility is a warning in the bounded-machine
        // section, not a filter that hides the item from the bus screen.
        for (var upgrade : bus.getInstalledUpgrades()) {
            addWrapped(result, Component.literal(upgrade.getStackSize() + "x ")
                    .append(upgrade.getType().getLocalizedName()));
            for (String line : upgrade.getBusGUIDescriptions()) addWrapped(result, Component.literal(line));
            if (!upgrade.getBusGUIDescriptions().isEmpty()) result.add(FormattedCharSequence.EMPTY);
        }
        return result;
    }

    private void addWrapped(List<FormattedCharSequence> lines, Component text) {
        lines.addAll(font.split(text, DESCRIPTION_WIDTH));
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
