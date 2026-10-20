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

    /**
     * Panel size. Standard 176x166, which is what upstream's {@code GuiMachineController} effectively uses:
     * its {@code setWidthHeight()} override is empty, so {@code GuiContainerBase} keeps the vanilla default.
     */
    // Sourced from the menu: one declaration, assertable server-side (see MenuMachineController).
    private static final int PANEL_WIDTH = MenuMachineController.PANEL_WIDTH;
    private static final int PANEL_HEIGHT = MenuMachineController.PANEL_HEIGHT;
    /**
     * The texture the jar's own {@code GuiMachineController} names -- read out of its class file, not
     * guessed: {@code textures/gui/guicontroller_large.png}. Note the 1.16 branch calls this
     * {@code guicontroller.png}, a file this mod does not ship, which is why the jar is the authority.
     */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/guicontroller_large.png");

    private static final int COLOR_PANEL = 0xFFC6C6C6;
    private static final int COLOR_PANEL_EDGE = 0xFF555555;
    private static final int COLOR_SLOT = 0xFF8B8B8B;
    private static final int COLOR_BAR_TRACK = 0xFF3A3A3A;
    private static final int COLOR_BAR_FILL = 0xFF3CB43C;
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_OK = 0xFF1E7A1E;
    private static final int COLOR_BAD = 0xFF9A2020;

    public ScreenMachineController(MenuMachineController menu, Inventory playerInventory,
                                   Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        // The player inventory is not shown, so its label would float under the panel pointing at
        // nothing.
        // The label is restored: with a 213-tall panel there is room for it, and the player inventory now
        // sits where the art expects rather than being suppressed to hide the mismatch.
        // §218: derived from the slot row rather than from vanilla's imageHeight - 94. Both give 119 here,
        // but the derivation is what keeps them from disagreeing -- and on the factory controller the same
        // arithmetic put the label 58 pixels above its own grid.
        // §219: the label sits over the grid it names, on both axes.
        this.inventoryLabelX = MenuMachineController.PLAYER_INV_X;
        this.inventoryLabelY = MenuMachineController.PLAYER_INV_Y - 12;
        // NOTE: nothing may be assigned to titleLabelX/Y here -- AbstractContainerScreen.init() sets them
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // The mod's own controller art, drawn at the panel origin exactly as upstream does.
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        var tile = minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getControllerPos())
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController controller ? controller : null;
        graphics.pose().pushPose();
        graphics.pose().scale(0.72F, 0.72F, 1F);
        try {
            int y = 12;
            if (menu.getRedstoneStopped()) {
                drawWrapped(graphics, Component.translatable("gui.controller.status.redstone_stopped"), y + 10);
                return;
            }
            var blueprint = hellfirepvp.modularmachinery.common.item.ItemBlueprint
                    .getAssociatedMachine(menu.getSlot(0).getItem());
            if (blueprint != null) {
                y = drawWrapped(graphics, Component.translatable("gui.controller.blueprint", ""), y);
                y = drawWrapped(graphics, machineName(blueprint), y) + 5;
            } else if (!menu.isFormed()) {
                y = drawWrapped(graphics, Component.translatable("gui.controller.blueprint",
                        Component.translatable("gui.controller.blueprint.none")), y) + 5;
            }
            var found = tile == null || !menu.isFormed() ? null :
                    hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getLoader()
                            .findByName(tile.getMachineName());
            if (found != null) {
                y = drawWrapped(graphics, Component.translatable("gui.controller.structure", ""), y);
                y = drawWrapped(graphics, machineName(found), y);
                var event = new hellfirepvp.modularmachinery.common.event.machine.ControllerGUIRenderEvent(tile);
                event.postEvent();
                for (String line : event.getExtraInfo()) y = drawWrapped(graphics, Component.literal(line), y);
            } else {
                y = drawWrapped(graphics, Component.translatable("gui.controller.structure",
                        Component.translatable("gui.controller.structure.none")), y);
            }
            y += 5;
            y = drawWrapped(graphics, Component.translatable("gui.controller.status"), y);
            String holding = tile == null ? null : tile.getStatusMessage();
            String status = !menu.getChunkLoaded() ? "gui.controller.status.chunk_unloaded"
                    : !menu.isFormed() ? "gui.controller.status.missing_structure"
                    : holding != null ? holding
                    : menu.getDuration() <= 0 ? "gui.controller.status.no_recipe"
                    : "gui.controller.status.crafting";
            y = drawWrapped(graphics, Component.translatable(status), y) + 5;
            if (menu.getDuration() > 0) {
                long percent = Math.min(100L, Math.max(0L, (long) menu.getProgress() * 100 / menu.getDuration()));
                y = drawWrapped(graphics, Component.translatable("gui.controller.status.crafting.progress",
                        percent + "%"), y) + 5;
                if (tile != null) {
                    for (Component line : tile.parallelismReadoutLines()) y = drawWrapped(graphics, line, y);
                }
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private Component machineName(hellfirepvp.modularmachinery.common.machine.DynamicMachine machine) {
        return Component.translatableWithFallback(machine.getTranslationKey(), machine.getLocalizedName());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 6 && mouseX < leftPos + 142 && mouseY >= topPos + 6
                && mouseY < topPos + 110 && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.getControllerPos())
                instanceof hellfirepvp.modularmachinery.common.tiles.TileController tile) {
            var recipe = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getRecipeLoader()
                    .getRecipe(tile.getRecipeName());
            if (recipe != null && !recipe.displayName().isBlank())
                graphics.renderComponentTooltip(font, java.util.List.of(Component.literal(recipe.displayName()),
                        Component.literal("ID: " + recipe.registryName())), mouseX, mouseY);
        }
    }

    private int drawWrapped(GuiGraphics graphics, Component text, int y) {
        for (var line : font.split(text, (int) (135 / 0.72F))) {
            graphics.drawString(font, line, 12, y, 0xFFFFFF, true);
            y += 10;
        }
        return y;
    }
}
