/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.mekanism;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.IOType;

/** JSON type: modularmachinery:gas, with gas registry ids in the modern Mekanism format. */
public final class GasRequirementType implements RequirementType {
    public static final String NAME = "gas";
    @Override public String requiresModid() { return "mekanism"; }
    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        if (!json.has("gas") || !json.get("gas").isJsonPrimitive()
                || !json.getAsJsonPrimitive("gas").isString()) throw new JsonParseException("gas requirement needs a gas name");
        if (!json.has("amount") || !json.get("amount").isJsonPrimitive()
                || !json.getAsJsonPrimitive("amount").isNumber()) throw new JsonParseException("gas requirement needs a numeric amount");
        float chance = hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementKeys.chanceOf(json);
        var result = new RequirementGas(io, json.get("gas").getAsString(), json.get("amount").getAsInt(), chance);
        if (!result.isResolvable()) throw new JsonParseException("Unknown Mekanism gas: " + result.getRawId());
        if (json.has("parallelizeUnaffected")) result.setParallelizeUnaffected(json.get("parallelizeUnaffected").getAsBoolean());
        return result;
    }
}
