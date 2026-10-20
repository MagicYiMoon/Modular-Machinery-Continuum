/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.jei;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Render optional requirements without linking Astral's classes into the base JEI plugin. */
public final class TooltipAstralRainbow {
    public static List<Component> lines(MachineRecipe recipe) {
        var result = new ArrayList<Component>();
        for (var requirement : recipe.requirements()) {
            if (!(requirement instanceof BatchIORequirement batch)) continue;
            var json = new JsonObject();
            switch (requirement.typeId()) {
                case "modularmachinery:starlight" -> {
                    batch.writeJson(json);
                    result.add(Component.translatable("jei.modularmachinery.starlight." + (requirement.getIOType() == IOType.INPUT ? "input" : "output"), json.get("amount").getAsFloat()));
                }
                case "modularmachinery:constellation" -> {
                    batch.writeJson(json);
                    result.add(Component.translatable("jei.modularmachinery.constellation", json.get("constellation").getAsString()));
                }
                case "modularmachinery:rainbow" -> result.add(Component.translatable("jei.modularmachinery.rainbow"));
            }
        }
        return result;
    }
}
