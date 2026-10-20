/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementCatalyst;
import java.util.Set;

/** Only the batch-exemption field is edited; advanced matching and lifecycle fields remain untouched. */
public final class RequirementBatchEditor {
    public static final String KEY = "parallelizeUnaffected";
    public static final int DEFAULT = 0, FOLLOW = 1, EXEMPT = 2;
    private static final Set<String> TYPES = Set.of("modularmachinery:item", "modularmachinery:item_durability",
            "modularmachinery:fluid", "modularmachinery:fluid_pertick", "modularmachinery:energy",
            "modularmachinery:ingredient_array_input", "modularmachinery:gas", "modularmachinery:gas_pertick");
    private RequirementBatchEditor() {}
    public static boolean supports(Requirement requirement) {
        return requirement instanceof Requirement.Parallelizable && !(requirement instanceof RequirementCatalyst)
                && TYPES.contains(requirement.typeId());
    }
    public static int policy(JsonObject source) {
        if (source == null || !source.has(KEY)) return DEFAULT;
        if (!source.get(KEY).isJsonPrimitive() || !source.getAsJsonPrimitive(KEY).isBoolean())
            throw new IllegalArgumentException("parallelizeUnaffected 须为布尔值");
        return source.get(KEY).getAsBoolean() ? EXEMPT : FOLLOW;
    }
    public static JsonObject merge(JsonObject source, int policy) {
        if (source == null || policy < DEFAULT || policy > EXEMPT) throw new IllegalArgumentException("需求批量设置无效");
        var result = source.deepCopy();
        if (policy == DEFAULT) result.remove(KEY); else result.addProperty(KEY, policy == EXEMPT);
        return result;
    }
    /** JSON extension of the source's CraftTweaker setParallelizeUnaffected method. */
    public static void applySource(Requirement requirement, JsonObject source) {
        if (!source.has(KEY)) return; // Keep constructor defaults, notably optional catalysts.
        int policy = policy(source);
        if (!(requirement instanceof Requirement.Parallelizable parallel))
            throw new IllegalArgumentException("该需求不支持 parallelizeUnaffected");
        parallel.setParallelizeUnaffected(policy == EXEMPT);
    }
}
