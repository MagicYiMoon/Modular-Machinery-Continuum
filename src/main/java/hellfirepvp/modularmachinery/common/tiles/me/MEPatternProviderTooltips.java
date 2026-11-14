/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.me;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The ME pattern provider's two buttons, as the LINES their tooltips show (PLAN 745).
 *
 * <h2>What upstream does, read at the body (rule A)</h2>
 *
 * <pre>
 *   GuiMEPatternProvider G88-G101   the RETURN ITEMS button: setTooltipFunction returns a List&lt;String&gt; of
 *                                   I18n.format(gui.mepatternprovider.return_items) +
 *                                   I18n.format(gui.mepatternprovider.return_items.desc)
 *   GuiMEPatternProvider G121-G136  the WORK MODE button: the header (work_mode.desc) then FIVE lines, one per
 *                                   mode, each PREPENDED with I18n.format(gui.mepatternprovider.current) when it is
 *                                   the mode currently selected -- i.e. the tooltip names the current mode by
 *                                   MARKING its own line, and the selected mode is read from
 *                                   workModeSetting.getCurrentSelection() (G122), which updateGUIState sets from
 *                                   owner.getWorkMode() (G245) -- the TILE, not a field of its own
 *   Button L59-L65                  the mechanism: getHoverTooltips -> tooltipFunction.apply(this)
 *   AEBaseGuiContainerDynamic L54-L70 the draw: renderHoveredToolTip asks the controller for the hovered
 *                                   tooltips and calls this.drawHoveringText(itemTooltip, mouseX, mouseY, font)
 *   WidgetController L111-L120       the second caller of the same list, also drawHoveringText
 * </pre>
 *
 * <p>So a 1.12.2 tooltip here is a {@code List<String>} of ALREADY-FORMATTED text, drawn by
 * {@code drawHoveringText}, queried once per frame from {@code renderHoveredToolTip} -- a {@code GuiScreen} hook
 * that vanilla's {@code drawScreen} calls by itself.</p>
 *
 * <h2>RULE A0 -- the translation shape, in the three parts</h2>
 *
 * <p><b>(1) UPSTREAM:</b> the file:line list above. The text is resolved eagerly by {@code I18n.format} at the moment
 * the widget is built or hovered, and the widget returns strings.</p>
 *
 * <p><b>(2) WHY THE SHAPE CANNOT BE COPIED:</b> 1.20.1 has no {@code drawHoveringText} and no {@code I18n} in that
 * form -- a line is a {@code Component} handed to {@code GuiGraphics.renderTooltip}. <b>And the hook is not called by
 * anything by default, which is measured rather than assumed:</b> {@code javap -p -c} over the mapped 47.3.0 jar finds
 * exactly two mentions of {@code renderTooltip} in {@code AbstractContainerScreen} -- its own
 * {@code protected void renderTooltip(GuiGraphics, int, int)} declaration and the
 * {@code GuiGraphics.renderTooltip} call INSIDE it. <b>Nothing in {@code AbstractContainerScreen.render} invokes
 * it</b>; vanilla's own {@code ContainerScreen.render} does it itself, in that order:
 * {@code renderBackground}, {@code super.render}, then {@code invokevirtual renderTooltip}. This is the 1.12.2
 * difference that matters: {@code renderHoveredToolTip} was a hook vanilla CALLED, and
 * {@code AbstractContainerScreen.renderTooltip} is a method each screen must call.</p>
 *
 * <p><b>(3) WHAT THIS PORT DOES:</b> the lines are built HERE, once, and the screen's own {@code render} override
 * calls {@code renderTooltip} -- which is the call that was missing and is why the two buttons showed nothing on
 * hover. Reason (3) is also why this class is not the screen: it names no client type, so the self-test can call the
 * same builder on a dedicated server (§481/§708 -- a client-only class can only be read, not loaded).</p>
 *
 * <h2>WHICH LINES ARE TRUE IN THIS PORT TODAY, AND WHICH DESCRIBE UPSTREAM ONLY</h2>
 *
 * <p><b>TRUE HERE:</b> the header; the marker line's identity (which mode is selected); the whole RETURN ITEMS pair --
 * {@code returnToNetwork()} walks the independent storage and inserts every item and fluid into the ME network
 * (PLAN 737.3②), and it sets {@code machineCompleted = true}, which is the sentence's second half verbatim.</p>
 *
 * <p><b>UPSTREAM'S SEMANTICS, NOT THIS PORT'S BEHAVIOUR:</b> the five mode sentences describe what each mode DOES in
 * 1.12.2 (see {@link WorkModeSetting}'s table). Here the mode is real, cyclable and persisted, but the ROUTING is not
 * implemented (PLAN 737.3③, 737.9:2): selecting {@link WorkModeSetting#BLOCKING_MODE},
 * {@link WorkModeSetting#CRAFTING_LOCK_MODE} or {@link WorkModeSetting#ENHANCED_BLOCKING_MODE} changes two predicates
 * that nothing calls, and {@link WorkModeSetting#ISOLATION_INPUT} cannot isolate (737.4(1)). The sentences were kept
 * verbatim because they are what the mode MEANS -- recorded instead of silently promised, and the divergence is in
 * PLAN.md.</p>
 */
public final class MEPatternProviderTooltips {

    /** Upstream G125-G134: the marker prepended to the ENABLED mode's own line ("[Currently Enabled]" / "[当前启用]"). */
    public static final String CURRENT_KEY = "gui.mepatternprovider.current";

    /** Upstream G124: the work-mode button's header line. */
    public static final String WORK_MODE_KEY = "gui.mepatternprovider.work_mode.desc";

    /** Upstream G96: the return-items button's name. */
    public static final String RETURN_ITEMS_KEY = "gui.mepatternprovider.return_items";

    /** Upstream G97: the return-items button's one sentence. */
    public static final String RETURN_ITEMS_DESC_KEY = "gui.mepatternprovider.return_items.desc";

    /** The nine shipped keys' common prefix, so the per-mode key is DERIVED and never retyped. */
    private static final String DESCRIPTION_PREFIX = "gui.mepatternprovider.";

    private static final String DESCRIPTION_SUFFIX = ".desc";

    private MEPatternProviderTooltips() {
    }

    /**
     * The description key of one mode, <b>derived from the enum constant's own name</b>.
     *
     * <p>Upstream builds the same five keys by hand (G125-G134) and its enum is name-for-name the one this port
     * ships, so {@code BLOCKING_MODE} is {@code blocking_mode.desc} by convention rather than by a table that could
     * drift from {@code values()}. A sixth mode would get its key from this one line, and the self-test requires that
     * key to exist in BOTH language files -- so a new constant fails the suite instead of rendering its key.</p>
     *
     * @param mode the mode whose description key is wanted
     * @return the translation key of that mode's own tooltip line
     */
    public static String descriptionKey(WorkModeSetting mode) {
        return DESCRIPTION_PREFIX + mode.name().toLowerCase(Locale.ROOT) + DESCRIPTION_SUFFIX;
    }

    /**
     * The work-mode button's tooltip: the header, then <b>one line per mode in the enum's own order</b>, with the
     * marker on the line of the mode passed in.
     *
     * <p>Upstream's list, in upstream's order (G124-G134). The marker identifies the mode because it is prepended to
     * THAT mode's own sentence -- there is no separate "you are here" line, and the sentence itself names the mode
     * ("Blocking Mode: ..." / "阻挡模式：...").</p>
     *
     * @param current the mode the screen must mark. The caller reads it from the MENU's data slot, which is the value
     *                the client can see; {@link WorkModeSetting#fromOrdinal(int)} maps an out-of-range number to
     *                {@link WorkModeSetting#DEFAULT}, the same reading the button's own sprite uses
     * @return the lines, in render order
     */
    public static List<Component> workModeLines(WorkModeSetting current) {
        WorkModeSetting[] modes = WorkModeSetting.values();
        List<Component> lines = new ArrayList<>(modes.length + 1);
        lines.add(Component.translatable(WORK_MODE_KEY));
        for (WorkModeSetting mode : modes) {
            Component description = Component.translatable(descriptionKey(mode));
            lines.add(mode == current ? Component.translatable(CURRENT_KEY).append(description) : description);
        }
        return lines;
    }

    /**
     * The return-items button's tooltip: its name and its one sentence (upstream G94-G99).
     *
     * @return the two lines, in render order
     */
    public static List<Component> returnItemsLines() {
        return List.of(Component.translatable(RETURN_ITEMS_KEY), Component.translatable(RETURN_ITEMS_DESC_KEY));
    }
}
