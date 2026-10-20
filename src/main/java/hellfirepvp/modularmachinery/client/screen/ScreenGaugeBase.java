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

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * Shared frame for the two hatch screens: a panel, a title, a vertical gauge and a readout line.
 *
 * <h2>Why this base class exists now</h2>
 *
 * <p>Round 26 declined to extract a base from a single screen, and round 27 filled in the second — but
 * the item bus is a <b>slot</b> screen while the controller is a <b>state</b> screen, so the two share no
 * rendering logic beyond a rectangle. The energy and fluid hatches are the first genuinely identical
 * pair: same frame, same gauge geometry, same readout, differing only in the value, its unit and its
 * colour. Extracting from these two is warranted for the reason the earlier refusal was right — the
 * duplication is real rather than anticipated.
 *
 * <p>The subclasses supply four things and nothing else: the title, the fill fraction, the readout text
 * and the gauge colour. Everything about layout lives here, so the two gauges cannot drift apart.
 */
public abstract class ScreenGaugeBase<T extends AbstractContainerMenu>
        extends AbstractContainerScreen<T> {


    /**
     * @return the panel size, which MUST be the MENU's own {@code PANEL_WIDTH}/{@code PANEL_HEIGHT}.
     *
     * <p><b>§193: this was a pair of constants here, and they were 140x88 while both menus said 176x166.</b>
     * The screen blitted only the top-left 140x88 of the texture and the menus placed the player's slots
     * outside it -- reported from the client as the two hatches being visibly wrong.
     *
     * <p><b>And the self-test's panel-size check could not see it</b>: that check reads
     * {@code MenuEnergyHatch.PANEL_WIDTH} against the jar's value, so it passed throughout. The screen kept a
     * <i>second</i> copy of the same fact and nothing compared the two copies -- §186's lesson, that a fact
     * declared in one place cannot drift, applied to the one place it had not been.
     *
     * <p>Making it abstract means the screen cannot state a size at all; it can only ask its menu, and the
     * check already knows the menu agrees with the jar.
     */
    protected abstract int[] panelSize();

    protected static final int COLOR_TEXT = 0xFF404040;

    /** Gauge geometry, shared so both screens put their bar in the same place. */

    protected ScreenGaugeBase(T menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        int[] size = panelSize();
        this.imageWidth = size[0];
        this.imageHeight = size[1];
        // These screens show no player inventory, so its label would float over empty panel.
        // Vanilla's offset from the bottom edge, which is the value that puts the label directly above the
        // player's rows -- those start at y=84, and 166 - 94 = 72, twelve pixels above them. The -1000 that
        // used to be here was not an offset at all: it was a way of hiding a label for a panel that was
        // wrongly thought to have no inventory.
        this.inventoryLabelY = this.imageHeight - 94;
        this.titleLabelY = 6;
    }

    /** @return fill fraction in {@code [0,1]} to draw in the gauge */
    protected abstract float fillFraction();

    /** @return the colour of the filled part of the gauge */
    protected abstract int gaugeColor();

    /** @return the line of text shown under the gauge */
    /**
     * @return the readout line, as a translatable {@link Component}.
     *
     * <p><b>Returning a Component rather than a String is the point.</b> It previously returned a String and
     * both subclasses built one by concatenation, which is how `" (in)"`, `" (out)"` and `" (empty)"` ended up
     * as hard-coded English in a mod shipping a Chinese translation (found by the §109/§110 sweep). A
     * {@code Component} return type makes that mistake unrepresentable here rather than merely discouraged --
     * prose cannot be concatenated into it by accident.
     */
    protected abstract net.minecraft.network.chat.Component readout();

    /**
     * Draws the panel.
     *
     * <p><b>Abstract, and it used to be a body (§196).</b> The base held a complete hand-drawn panel — flat
     * {@code fill()} rectangles in five invented colours ({@code 0xFFC6C6C6}, {@code 0xFF3A3A3A} for the gauge
     * track, and so on) — and both subclasses overrode it without calling {@code super}. So it was dead code
     * that nothing executed, and it was <b>hand-drawn</b>, which this port's brief forbids outright: <i>"GUI
     * must be built against upstream's textures and container layout, not drawn by hand."</i>
     *
     * <p><b>Dead code is not the main problem.</b> The hand-drawn body was a trap: a third gauge screen would
     * have inherited a plausible-looking panel that agreed with no texture and no container, and the two
     * existing screens' correct behaviour would have been the argument that the base was fine.
     *
     * <p>Making it abstract means a subclass cannot inherit a panel at all — it can only draw one, and the
     * texture it draws is the jar's ({@code guibar.png} for both hatches). <b>This is §186's shape again: a
     * rule the code cannot break rather than one it is asked to respect.</b>
     */
    @Override
    protected abstract void renderBg(net.minecraft.client.gui.GuiGraphics graphics, float partialTick,
                                     int mouseX, int mouseY);

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // Right of the gauge well, not over it (§199).
        //
        // The well is baked into guibar.png at (15,10) and is 20 wide, so it spans x=15..35 from y=10 to
        // y=71. Vanilla puts the title at (8,6) -- nine pixels tall, so y=6..15 -- and the two overlap in
        // x=15..35, y=10..15, which the client reported as the title being covered.
        //
        // The well cannot move: it is the jar's own art and the whole point of the texture is that it is not
        // redrawn. So the title moves, to x=40 -- clear of the well and in line with the readout strip that
        // starts at 44.
        graphics.drawString(font, title, 40, titleLabelY, COLOR_TEXT, false);
        // Centred in the readout strip rather than left-aligned, because the strings differ in width
        // between energy ("12,000 / 160,000 FE") and fluid ("1,000 / 16,000 mB water") and a fixed
        // left edge would leave one of them looking misplaced.
        net.minecraft.network.chat.Component text = readout();
        int textX = 44 + (panelSize()[0] - 10 - 44 - font.width(text)) / 2;
        graphics.drawString(font, text, textX, 44, COLOR_TEXT, false);
    }
}
