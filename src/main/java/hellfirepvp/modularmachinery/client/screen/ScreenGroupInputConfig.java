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

import hellfirepvp.modularmachinery.common.menu.MenuGroupInputConfig;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The group-input configuration screen.
 *
 * <h2>Every value came from the jar, not from drawing</h2>
 *
 * <ul>
 *   <li><b>Panel 176x166</b> — upstream's {@code GuiContainerGroupInputConfig.setWidthHeight()} is empty
 *       (§158), so the vanilla default applies. §91 is why this is not assumed: the controller screen shipped
 *       47 pixels short and only the user opening it caught that.</li>
 *   <li><b>Texture {@code guismartinterface.png}</b> — the base class's {@code TEXTURES_EMPTY_GUI} constant
 *       names <i>a subclass's</i> file (§159). The subclass's own constant pool shows no texture at all, which
 *       is what makes it look like a missing asset.</li>
 *   <li><b>A text field and two buttons</b> — upstream's fields are {@code textField},
 *       {@code enableIsolation} and {@code closeIsolation}, in that order.</li>
 * </ul>
 */
public class ScreenGroupInputConfig extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<MenuGroupInputConfig> {

    /** §159: the base class's {@code TEXTURES_EMPTY_GUI}, which is the smart interface's own file. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("modularmachinery", "textures/gui/guismartinterface.png");

    private EditBox groupIdField;

    public ScreenGroupInputConfig(MenuGroupInputConfig menu, net.minecraft.world.entity.player.Inventory inventory,
                                  Component title) {
        super(menu, inventory, title);
        this.imageWidth = MenuGroupInputConfig.PANEL_WIDTH;
        this.imageHeight = MenuGroupInputConfig.PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // Upstream's widget order: the text field first, then enable, then close.
        this.groupIdField = new EditBox(this.font, this.leftPos + 8, this.topPos + 20, 120, 18,
                Component.translatable("gui.groupinputconfig.value"));
        this.groupIdField.setMaxLength(9);
        // §194: show the CURRENT value. Without this the field opened empty every time, which looks exactly
        // like the value never having been saved -- and it had been: PktGroupInputConfig.setGroupId writes it.
        // Nothing carried it back to the screen.
        this.groupIdField.setValue(Integer.toString(menu.getGroupId()));
        addRenderableWidget(this.groupIdField);
        addRenderableWidget(Button.builder(Component.translatable("gui.groupinputconfig.enable"), b -> send())
                .bounds(this.leftPos + 8, this.topPos + 46, 60, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.groupinputconfig.close"), b -> send())
                .bounds(this.leftPos + 72, this.topPos + 46, 60, 18).build());
    }

    /**
     * Sends the configured values.
     *
     * <p>The id is parsed defensively: a non-numeric entry yields the current group rather than throwing, and
     * the server clamps it regardless ({@code PktGroupInputConfig.clampGroupId}), because a client is not
     * trusted with the range.
     */
    private void send() {
        int id = -1;
        try {
            id = Integer.parseInt(this.groupIdField.getValue().trim());
        } catch (NumberFormatException ignored) {
            // Leave id at -1; the server clamps a negative id to 0.
        }
        hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktGroupInputConfig
                .send(menu.getMachinePos(), id, true);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.groupinputconfig.title"), titleLabelX, titleLabelY, 0xFF404040, false);
        // Below the buttons, not through them (§194). The buttons occupy y=46..64 in panel space and this line
        // was drawn at y=60, so it ran straight across both of them -- reported from the client as the text
        // overlapping. A 166-tall panel has room; the line simply had the wrong y.
        graphics.drawString(font, Component.translatable("gui.groupinputconfig.tooltip"),
                8, 70, 0xFF404040, false);
    }
}