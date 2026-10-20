/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.jei;

import hellfirepvp.modularmachinery.common.crafting.BatchIORequirement;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Optional mana information without linking Botania classes from the base JEI category. */
public final class TooltipMana {
    private TooltipMana() {}
    public static List<Component> lines(MachineRecipe recipe) {
        long[] amounts = new long[4]; boolean[] present = new boolean[4];
        for (var requirement : recipe.requirements()) {
            if (!"modularmachinery:mana".equals(requirement.typeId()) || !(requirement instanceof BatchIORequirement mana)) continue;
            var json = new com.google.gson.JsonObject(); mana.writeJson(json);
            int index = (requirement.getIOType() == IOType.INPUT ? 0 : 1) + (json.has("perTick") && json.get("perTick").getAsBoolean() ? 2 : 0);
            amounts[index] += json.get("amount").getAsLong(); present[index] = true;
        }
        var result = new ArrayList<Component>();
        String[] keys = {"jei.modularmachinery.mana.input", "jei.modularmachinery.mana.output", "jei.modularmachinery.mana.input_rate", "jei.modularmachinery.mana.output_rate"};
        for (int i = 0; i < 4; i++) if (present[i]) result.add(Component.translatable(keys[i], amounts[i]));
        return result;
    }
}
