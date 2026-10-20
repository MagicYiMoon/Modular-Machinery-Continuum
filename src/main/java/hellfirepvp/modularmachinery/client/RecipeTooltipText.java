/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client;

import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/** Same translation-key lookup for the author's preview and the actual JEI recipe page. */
public final class RecipeTooltipText {
    private RecipeTooltipText() {}
    public static Component resolve(String raw) { return Component.literal(I18n.exists(raw) ? I18n.get(raw) : raw); }
}
