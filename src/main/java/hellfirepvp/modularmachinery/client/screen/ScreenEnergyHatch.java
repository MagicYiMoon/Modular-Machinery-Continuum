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

import hellfirepvp.modularmachinery.common.menu.MenuEnergyHatch;
import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The energy hatch screen: a fill gauge plus stored/capacity in FE.
 *
 * <p>All layout comes from {@link ScreenGaugeBase}; this class supplies only the value, the colour and
 * the readout. See that class for why the shared base is warranted here and was not for the earlier
 * screens.
 */
public class ScreenEnergyHatch extends ScreenGaugeBase<MenuEnergyHatch> {

    private static final int COLOR_FILL = 0xFFD46A00;

    public ScreenEnergyHatch(MenuEnergyHatch menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    /**
     * The upstream texture. Both hatch screens use {@code guibar.png} — the energy gauge is at texture
     * x=196 and the fluid frame at x=176, with the fluid's own still texture drawn inside it.
     */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/guibar.png");

    @Override
    protected void renderBg(net.minecraft.client.gui.GuiGraphics graphics, float partialTick,
                            int mouseX, int mouseY) {
        // Panel, then the gauge in upstream's exact position: a 20-wide, 61-tall well at GUI (15,10),
        // which is what GuiContainerEnergyHatch and GuiContainerFluidHatch both draw.
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int gaugeX = leftPos + 15;
        int gaugeY = topPos + 10;
        float fraction = Math.max(0F, Math.min(1F, fillFraction()));
        int filled = (int) (61 * fraction);
        if (filled > 0) {
            // Bottom-up, as upstream: source y runs from (61 - filled) so a full gauge takes the whole well.
            graphics.blit(TEXTURE, gaugeX, gaugeY + 61 - filled,
                    196, 61 - filled, 20, filled, 256, 256);
        }
    }

    @Override
    protected float fillFraction() {
        return menu.getFillFraction();
    }

    /**
     * @return the menu's own panel size (§193). This screen states no size of its own, so it cannot disagree
     *         with the menu that places its slots.
     */
    @Override
    protected int[] panelSize() {
        return new int[]{hellfirepvp.modularmachinery.common.menu.MenuEnergyHatch.PANEL_WIDTH,
                hellfirepvp.modularmachinery.common.menu.MenuEnergyHatch.PANEL_HEIGHT};
    }

    @Override
    protected int gaugeColor() {
        // A single colour regardless of direction: the buffer is the buffer. Direction is shown in the
        // readout instead, where it cannot be mistaken for a state of charge.
        return COLOR_FILL;
    }

    @Override
    protected net.minecraft.network.chat.Component readout() {
        // THE UNIT COMES FROM THE SWITCH (audit row 41), which is what makes `display.energy.Display_Energy_Type`
        // observable at all. This is upstream's own GUI line -- GuiContainerEnergyHatch U57-U64:
        //
        //     long currentEnergy = EnergyDisplayUtil.type.formatEnergyForDisplay(energyHatch.getCurrentEnergy());
        //     long maxEnergy     = EnergyDisplayUtil.type.formatEnergyForDisplay(energyHatch.getMaxEnergy());
        //     text.add(I18n.format("tooltip.energyhatch.charge",
        //         MiscUtils.formatNumber(currentEnergy),
        //         MiscUtils.formatNumber(maxEnergy),
        //         I18n.format(EnergyDisplayUtil.type.getUnlocalizedFormat())));
        //
        // so the key, the argument order and the conversion are upstream's, and "tooltip.energyhatch.charge" is
        // "%s / %s %s" -- the third placeholder is the unit.
        //
        // ⚠️ WHY THE PORT'S OWN KEYS COULD NOT CARRY THE UNIT (reported, not edited): the port's readout used
        // `gui.modularmachinery.hatch.energy.in` / `.out`, and BOTH ship with "FE" BAKED INTO THE STRING --
        // "%s / %s FE (in)" and "%s / %s FE (out)". A unit selected in the config could never reach a line whose
        // text already says FE, and the language files are not this unit's to edit (only a genuinely ABSENT key
        // would be). Those two keys are therefore left untouched and unused by this screen; the same
        // hard-coded-unit question is reported for them in the round's notes.
        //
        // The direction word that the port's own keys carried ("(in)" / "(out)") is dropped HERE and that is
        // upstream's shape rather than a loss: upstream's charge line names no direction, because the hatch the
        // player right-clicked is the hatch whose window is open.
        //
        // The unit is passed as a Component, not a pre-resolved String, so it resolves in the player's own
        // language -- which is what I18n.format did on 1.12.2.
        hellfirepvp.modularmachinery.client.util.EnergyDisplayUtil.EnergyType type =
                hellfirepvp.modularmachinery.client.util.EnergyDisplayUtil.type();
        return net.minecraft.network.chat.Component.translatable(
                "tooltip.energyhatch.charge",
                type.formatEnergyForDisplay(menu.getStored()),
                type.formatEnergyForDisplay(menu.getCapacity()),
                net.minecraft.network.chat.Component.translatable(type.getUnlocalizedFormat()));
    }
}
