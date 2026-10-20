/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementCatalyst;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import java.util.ArrayList;

/** Decoder for the source mod's optional catalyst requirement. */
public final class CatalystRequirementType implements RequirementType {
    public static final String NAME = "catalyst";

    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        if (io != IOType.INPUT) throw new JsonParseException("catalyst is input-only");
        RequirementIngredientArray parsed = IngredientArrayRequirementType.decode(io, json, false);
        var catalyst = new RequirementCatalyst(new ArrayList<>(parsed.getCandidates()), parsed.getChance());
        if (json.has("tooltips") && json.get("tooltips").isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray("tooltips")) if (e.isJsonPrimitive()) catalyst.addTooltip(e.getAsString());
        }
        if (json.has("modifiers") && json.get("modifiers").isJsonArray()) {
            for (JsonElement e : json.getAsJsonArray("modifiers")) {
                if (!e.isJsonObject()) continue;
                RecipeModifier modifier = RecipeModifier.deserialize(e.getAsJsonObject());
                if (modifier != null) catalyst.addModifier(modifier);
            }
        }
        return catalyst;
    }
}
