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

import hellfirepvp.modularmachinery.common.menu.MenuMachineController;
import hellfirepvp.modularmachinery.common.menu.MachineStatusViewport;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The machine controller's screen.
 *
 * <p><b>1.12.2 → 1.20.1.</b> The whole client GUI stack was replaced. Upstream extended
 * {@code GuiContainer} and drew with {@code GlStateManager} + {@code Tessellator} calls against an
 * atlas. Here {@code GuiContainer} no longer exists in the old form:
 * <ul>
 *   <li>{@code AbstractContainerScreen} is the base, and drawing goes through
 *       {@link GuiGraphics} — a batched wrapper that replaced the raw {@code GlStateManager} calls;</li>
 *   <li>the texture's <i>size</i> is a constructor argument ({@code imageWidth}/{@code imageHeight}),
 *       not something read from a resource;</li>
 *   <li>{@link #renderBg} receives the partial tick and the mouse position, so the old pattern of
 *       stashing them in fields is gone.</li>
 * </ul>
 *
 * <h2>CORRECTION: it uses the mod's own texture</h2>
 *
 * <p>This screen <b>blits {@code textures/gui/guicontroller_large.png}</b>, the texture the jar's own
 * {@code GuiMachineController} names. An earlier version drew a generic code panel instead, justified by a
 * claim in this very comment that "the port has no GUI art". <b>That claim was never checked and was
 * false</b> -- 23 GUI textures, including this one, were present in the converted assets the whole time.
 * Hand-drawing was therefore avoiding a missing-texture problem that did not exist, and the result looked
 * nothing like the mod. The remaining {@code fill(...)} calls are only for the few dynamic overlays the
 * texture does not carry.
 */
public class ScreenMachineController extends AbstractContainerScreen<MenuMachineController> {

    private static final int PANEL_WIDTH = MenuMachineController.PANEL_WIDTH;
    private static final int PANEL_HEIGHT = MenuMachineController.PANEL_HEIGHT;
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/guicontroller_large.png");
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private final MachineStatusViewport statusViewport = new MachineStatusViewport();

    public ScreenMachineController(MenuMachineController menu, Inventory playerInventory,
                                   Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelX = MenuMachineController.PLAYER_INV_X;
        this.inventoryLabelY = MenuMachineController.PLAYER_INV_Y - 12;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // The mod's own controller art, drawn at the panel origin exactly as upstream does.
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        drawControllerStatus(graphics);
        drawPerformanceFooter(graphics);
    }

    private record StatusRow(FormattedCharSequence text, int y) { }

    private void drawControllerStatus(GuiGraphics graphics) {
        var tile = minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getControllerPos())
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller ? controller : null;
        var rows = new ArrayList<StatusRow>();
        int y = 0;
        if (menu.getRedstoneStopped()) {
            // Preserve the source's redstone-only status branch, while keeping diagnostics accessible below.
            y = addWrapped(rows, Component.translatable("gui.controller.status.redstone_stopped"), y + 10);
        } else {
            var blueprint = hellfirepvp.modularmachinery.common.item.ItemBlueprint
                    .getAssociatedMachine(menu.getSlot(0).getItem());
            if (blueprint != null) {
                y = addWrapped(rows, Component.translatable("gui.controller.blueprint", ""), y);
                y = addWrapped(rows, machineName(blueprint), y) + 5;
            } else if (!menu.isFormed()) {
                y = addWrapped(rows, Component.translatable("gui.controller.blueprint",
                        Component.translatable("gui.controller.blueprint.none")), y) + 5;
            }
            var found = tile == null || !menu.isFormed() ? null :
                    hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader()
                            .findByName(tile.getMachineName());
            if (found != null) {
                y = addWrapped(rows, Component.translatable("gui.controller.structure", ""), y);
                y = addWrapped(rows, machineName(found), y);
                var event = new hellfirepvp.modularmachinery.common.event.machine.ControllerGUIRenderEvent(tile);
                event.postEvent();
                for (String line : event.getExtraInfo())
                    if (line != null) y = addWrapped(rows, Component.literal(line), y);
            } else {
                y = addWrapped(rows, Component.translatable("gui.controller.structure",
                        Component.translatable("gui.controller.structure.none")), y);
            }
            y += 5;
            y = addWrapped(rows, Component.translatable("gui.controller.status"), y);
            String holding = tile == null ? null : tile.getStatusMessage();
            String status = !menu.getChunkLoaded() ? "gui.controller.status.chunk_unloaded"
                    : !menu.isFormed() ? "gui.controller.status.missing_structure"
                    : holding != null ? holding
                    : menu.getDuration() <= 0 ? "gui.controller.status.no_recipe"
                    : "gui.controller.status.crafting";
            y = addWrapped(rows, Component.translatable(status), y) + 5;
            if (menu.getDuration() > 0) {
                long percent = Math.min(100L, Math.max(0L, (long) menu.getProgress() * 100 / menu.getDuration()));
                y = addWrapped(rows, Component.translatable("gui.controller.status.crafting.progress",
                        percent + "%"), y) + 5;
                if (tile != null) {
                    for (Component line : tile.parallelismReadoutLines()) y = addWrapped(rows, line, y);
                }
            }
        }
        statusViewport.updateHeight(y);
        graphics.enableScissor(leftPos + MachineStatusViewport.X, topPos + MachineStatusViewport.Y,
                leftPos + MachineStatusViewport.X + MachineStatusViewport.WIDTH,
                topPos + MachineStatusViewport.Y + MachineStatusViewport.HEIGHT);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(MachineStatusViewport.X + 1, MachineStatusViewport.Y, 0);
            graphics.pose().scale((float) MachineStatusViewport.SCALE, (float) MachineStatusViewport.SCALE, 1F);
            for (StatusRow row : rows) {
                int lineY = row.y() - statusViewport.offset();
                if (lineY + font.lineHeight > 0 && lineY < MachineStatusViewport.LOGICAL_HEIGHT)
                    graphics.drawString(font, row.text(), 0, lineY, COLOR_WHITE, true);
            }
        } finally {
            graphics.pose().popPose();
            graphics.disableScissor();
        }
        if (statusViewport.maxOffset() > 0) {
            graphics.fill(MachineStatusViewport.SCROLL_X, MachineStatusViewport.Y,
                    MachineStatusViewport.SCROLL_X + MachineStatusViewport.SCROLL_WIDTH,
                    MachineStatusViewport.Y + MachineStatusViewport.HEIGHT, 0xFF303030);
            graphics.fill(MachineStatusViewport.SCROLL_X, statusViewport.thumbY(),
                    MachineStatusViewport.SCROLL_X + MachineStatusViewport.SCROLL_WIDTH,
                    statusViewport.thumbY() + statusViewport.thumbHeight(), 0xFFAAAAAA);
        }
    }

    private int addWrapped(List<StatusRow> rows, Component text, int y) {
        for (var line : font.split(text, MachineStatusViewport.TEXT_WIDTH)) {
            rows.add(new StatusRow(line, y));
            y += 10;
        }
        return y;
    }

    private Component machineName(hellfirepvp.modularmachinery.common.machine.DynamicMachine machine) {
        return Component.translatableWithFallback(machine.getTranslationKey(), machine.getLocalizedName());
    }

    private List<Component> performanceLines() {
        return List.of(Component.translatable("gui.factory.performance.tick", menu.performanceTickText()),
                Component.translatable("gui.factory.performance.search", menu.performanceSearchText()),
                Component.translatable("gui.factory.performance.mode", Component.translatable(menu.workModeTranslationKey())));
    }

    private void drawPerformanceFooter(GuiGraphics graphics) {
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(MachineStatusViewport.X + 1, MachineStatusViewport.FOOTER_Y, 0);
            graphics.pose().scale((float) MachineStatusViewport.SCALE, (float) MachineStatusViewport.SCALE, 1F);
            int row = 0;
            for (Component line : performanceLines()) {
                String text = line.getString();
                if (font.width(text) > MachineStatusViewport.TEXT_WIDTH)
                    text = font.plainSubstrByWidth(text, MachineStatusViewport.TEXT_WIDTH - font.width("…")) + "…";
                graphics.drawString(font, text, 0, (int) (row++ * MachineStatusViewport.FOOTER_STRIDE / MachineStatusViewport.SCALE),
                        COLOR_WHITE, true);
            }
        } finally { graphics.pose().popPose(); }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + MachineStatusViewport.X && mouseX < leftPos + MachineStatusViewport.X + MachineStatusViewport.WIDTH
                && mouseY >= topPos + MachineStatusViewport.FOOTER_Y
                && mouseY < topPos + MachineStatusViewport.FOOTER_Y + MachineStatusViewport.FOOTER_ROWS * MachineStatusViewport.FOOTER_STRIDE) {
            graphics.renderComponentTooltip(font, performanceLines(), mouseX, mouseY);
        } else if (mouseX >= leftPos + MachineStatusViewport.X && mouseX < leftPos + MachineStatusViewport.X + MachineStatusViewport.WIDTH
                && mouseY >= topPos + MachineStatusViewport.Y && mouseY < topPos + MachineStatusViewport.Y + MachineStatusViewport.HEIGHT
                && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getControllerPos())
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController tile) {
            var recipe = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getRecipeLoader()
                    .getRecipe(tile.getRecipeName());
            if (recipe != null && !recipe.displayName().isBlank())
                graphics.renderComponentTooltip(font, List.of(Component.literal(recipe.displayName()),
                        Component.literal("ID: " + recipe.registryName())), mouseX, mouseY);
        }
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        return statusViewport.scroll(x - leftPos, y - topPos, delta) || super.mouseScrolled(x, y, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        return statusViewport.click(x - leftPos, y - topPos, button) || super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return statusViewport.drag(y - topPos, button) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return statusViewport.release(button) || super.mouseReleased(x, y, button);
    }
}
