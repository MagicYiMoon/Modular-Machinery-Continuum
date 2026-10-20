/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.mekanism;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementKeys;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.IOType;

/** JSON type for the original modularmachinery:gas_pertick requirement. */
public final class GasPerTickRequirementType implements RequirementType {
    public static final String NAME = "gas_pertick";
    @Override public String requiresModid() { return "mekanism"; }
    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        if (!json.has("gas") || !json.get("gas").isJsonPrimitive() || !json.getAsJsonPrimitive("gas").isString())
            throw new JsonParseException("gas_pertick requirement needs a gas name");
        if (!json.has("amount") || !json.get("amount").isJsonPrimitive() || !json.getAsJsonPrimitive("amount").isNumber())
            throw new JsonParseException("gas_pertick requirement needs a numeric amount");
        if (json.has("chance") && RequirementKeys.chanceOf(json) != 1F)
            throw new JsonParseException("gas_pertick is a continuous rate and does not support chance");
        var result = new RequirementGasPerTick(io, json.get("gas").getAsString(), json.get("amount").getAsLong());
        if (!result.isResolvable()) throw new JsonParseException("Unknown Mekanism gas: " + result.getRawId());
        if (json.has("parallelizeUnaffected")) result.setParallelizeUnaffected(json.get("parallelizeUnaffected").getAsBoolean());
        return result;
    }
}
