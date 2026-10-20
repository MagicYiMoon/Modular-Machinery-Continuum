/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.jei;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Serialization-based presentation keeps the base JEI category independent of Blood Magic classes. */
public final class TooltipBloodMagic {
    private TooltipBloodMagic() {}
    public static List<Component> lines(MachineRecipe recipe) {
        var result = new ArrayList<Component>();
        long[] lp = new long[4]; boolean[] present = new boolean[4];
        for (var requirement : recipe.requirements()) {
            if (!(requirement instanceof BatchIORequirement batch)) continue;
            if (!Set.of("modularmachinery:lifeessence", "modularmachinery:will").contains(requirement.typeId())) continue;
            var json = new JsonObject(); batch.writeJson(json);
            boolean input = requirement.getIOType() == IOType.INPUT;
            if (requirement.typeId().endsWith(":lifeessence")) {
                int index = (input ? 0 : 1) + (json.get("perTick").getAsBoolean() ? 2 : 0);
                lp[index] += json.get("amount").getAsLong(); present[index] = true;
            } else {
                result.add(Component.translatable("jei.modularmachinery.will." + (input ? "input" : "output"), json.get("amount").getAsDouble(),
                        Component.translatable("will.modularmachinery." + json.get("will-type").getAsString()), json.get(input ? "min" : "max").getAsDouble()));
            }
        }
        String[] keys = {"input", "output", "input_rate", "output_rate"};
        for (int i = 3; i >= 0; i--) if (present[i]) result.add(0, Component.translatable("jei.modularmachinery.lifeessence." + keys[i], lp[i]));
        return result;
    }
}
