/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementCatalyst;
import hellfirepvp.modularmachinery.common.machine.IOType;
import java.util.Set;

/** Isolated editing of source-style one-shot input trigger metadata. */
public final class RequirementTriggerEditor {
    private static final Set<String> TYPES = Set.of("modularmachinery:item", "modularmachinery:item_durability",
            "modularmachinery:ingredient_array_input", "modularmachinery:fluid", "modularmachinery:gas");
    private RequirementTriggerEditor() {}
    public static boolean supports(Requirement requirement) {
        return requirement instanceof SelectorRequirement && requirement.getIOType() == IOType.INPUT
                && !(requirement instanceof Requirement.PerTick) && !(requirement instanceof RequirementCatalyst)
                && TYPES.contains(requirement.typeId());
    }
    /** -1 removes the time field; repeat policy 0 removes, 1 disables, 2 enables. */
    public static JsonObject merge(JsonObject source, int tick, int repeat, int duration) {
        if (source == null || tick < -1 || repeat < 0 || repeat > 2)
            throw new IllegalArgumentException("触发设置无效");
        if (tick > 0 && tick >= duration)
            throw new IllegalArgumentException("触发 tick 须小于配方运行时间");
        var result = source.deepCopy();
        if (tick == -1) result.remove("triggerTime"); else result.addProperty("triggerTime", tick);
        if (repeat == 0) result.remove("triggerRepeatable"); else result.addProperty("triggerRepeatable", repeat == 2);
        return result;
    }
}
