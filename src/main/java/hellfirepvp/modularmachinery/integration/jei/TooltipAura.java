/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.jei;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.network.chat.Component;
import java.util.*;

/** No optional API types in the base JEI class-loading path. */
public final class TooltipAura {
    public static List<Component> lines(MachineRecipe recipe) {
        var result = new ArrayList<Component>();
        for (var r : recipe.requirements()) {
            if (!r.typeId().equals("modularmachinery:aura") || !(r instanceof BatchIORequirement batch)) continue;
            var json = new JsonObject(); batch.writeJson(json); boolean input = r.getIOType() == IOType.INPUT;
            result.add(Component.translatable("jei.modularmachinery.aura." + (input ? "input" : "output"),
                    json.get("amount").getAsInt(), json.get("aura-type").getAsString(), json.get(input ? "min-aura" : "max-aura").getAsInt()));
        }
        return result;
    }
}
