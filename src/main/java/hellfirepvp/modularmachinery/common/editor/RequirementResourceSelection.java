/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;

/** Resource replacement shared by the client draft and the server's source-file editor. */
public final class RequirementResourceSelection {
    private RequirementResourceSelection() {}

    public static String key(JsonObject requirement) {
        if (FuelEditor.isFuel(requirement)) return "";
        if (requirement == null || !requirement.has("type")) return "";
        return switch (requirement.get("type").getAsString()) {
            case "modularmachinery:item", "modularmachinery:item_durability" -> "item";
            case "modularmachinery:fluid", "modularmachinery:fluid_pertick" -> "fluid";
            default -> "";
        };
    }

    public static boolean canReplace(JsonObject requirement) {
        if (DurabilityEditor.isDurability(requirement)) return DurabilityEditor.canEdit(requirement);
        if (FluidTickEditor.isRate(requirement)) return FluidTickEditor.canEdit(requirement);
        String key = key(requirement);
        if (key.isEmpty() || !requirement.has(key) || !requirement.get(key).isJsonPrimitive()
                || requirement.has("nbt") || requirement.has("nbt-display")) return false;
        try {
            // Excludes unavailable resources, ore-dictionary/metadata expressions and tag outputs.
            BasicRequirementType.fromKey(key).create(requirement.get(key).getAsString(),
                    requirement.has("io-type") && "output".equals(requirement.get("io-type").getAsString()), 1, 1);
            return true;
        } catch (IllegalArgumentException invalid) { return false; }
    }

    public static JsonObject replace(JsonObject requirement, String resourceId) {
        if (!canReplace(requirement))
            throw new IllegalArgumentException("仅支持普通物品、物品标签输入和流体；NBT 等需求请修改源文件");
        String key = key(requirement);
        if ("item".equals(key) && ItemTagSelection.isTag(requirement) != resourceId.startsWith("tag:"))
            throw new IllegalArgumentException("重新选择时请保持物品或标签类型；更改类型请另建需求");
        JsonObject validated = BasicRequirementType.fromKey(key).create(resourceId,
                requirement.has("io-type") && "output".equals(requirement.get("io-type").getAsString()), 1, 1);
        JsonObject draft = requirement.deepCopy();
        if (DurabilityEditor.isDurability(requirement)) DurabilityEditor.create(resourceId, 1, 1, 1);
        draft.add(key, validated.get(key));
        return draft;
    }
}
