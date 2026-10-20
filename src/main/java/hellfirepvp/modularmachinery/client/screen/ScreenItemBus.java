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
import hellfirepvp.modularmachinery.common.menu.MenuItemBus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The item bus screen: a slot grid plus the player inventory.
 *
 * <p>The counterpart to {@link ScreenMachineController}, and it exercises the pattern that one does not:
 * <b>real item stacks in slots</b>. The slots themselves are rendered by
 * {@code AbstractContainerScreen#render}, which walks the menu's slot list and blits each stack plus its
 * count and durability bar. This class therefore only draws the panel and the slot wells underneath —
 * overriding the item drawing would mean reimplementing stack rendering, tooltips and drag handling for
 * no gain.
 *
 * <p>Panel height is derived from the slot count rather than fixed, because a bus's size varies by tier
 * (the {@code size} blockstate property) from a single row up to a full chest. A fixed height would
 * either clip a large bus or leave a large empty gap under a small one.
 */
public class ScreenItemBus extends AbstractContainerScreen<MenuItemBus> {

    public ScreenItemBus(MenuItemBus menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = MenuItemBus.PANEL_WIDTH;
        this.imageHeight = MenuItemBus.PANEL_HEIGHT;
        // Upstream ContainerBase places the player inventory the way vanilla does, so its label belongs
        // where players expect it rather than being suppressed as the code-drawn version did.
        this.inventoryLabelY = this.imageHeight - 94;
    }

    /** @return the tier texture, e.g. modularmachinery:textures/gui/inventory_normal.png */
    private ResourceLocation getTexture() {
        return new ResourceLocation(Modularmachinery.MODID, menu.getSize().getTexturePath());
    }

    /**
     * Places the group-input button.
     *
     * <p><b>The entry point §166 found missing.</b> {@code MenuGroupInputConfig} existed with a container, a
     * screen and a packet and nothing that opened it. Upstream opens the config from a component's own GUI —
     * there is no group-input block (§157) — and this is that button.
     *
     * <p>Placed at the panel's top-right, clear of the slot grid, which starts at x=8 in every tier texture.
     * The label is a short translatable key rather than the full title, because the title does not fit a
     * 20-pixel widget and a clipped title is worse than an abbreviation.
     */
    @Override
    protected void init() {
        super.init();
        addRenderableWidget(net.minecraft.client.gui.components.Button
                .builder(Component.translatable("gui.groupinputconfig.button"),
                        b -> hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork
                                .PktOpenGroupInputConfig.send(menu.getBusPos()))
                .bounds(leftPos + 152, topPos + 4, 20, 18)
                .tooltip(net.minecraft.client.gui.components.Tooltip.create(
                        Component.translatable("gui.groupinputconfig.title")))
                .build());
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {

        // One blit of the tier texture. It is 256x256 and the panel is its top-left 176x166, the standard
        // Minecraft GUI convention, so the frame and the slot wells come from the mod art itself and line
        // up with the slots the menu placed from the same tier.
        graphics.blit(getTexture(), leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }
}