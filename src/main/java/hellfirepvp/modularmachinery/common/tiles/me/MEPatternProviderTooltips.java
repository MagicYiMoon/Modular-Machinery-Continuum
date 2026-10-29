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
