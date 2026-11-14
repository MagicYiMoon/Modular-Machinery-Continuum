/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementComputation;
import hellfirepvp.modularmachinery.common.machine.IOType;

public final class ComputationRequirementType implements RequirementType {
    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        if (io != IOType.INPUT) throw new JsonParseException("Computation is an input requirement");
        var allowed = java.util.Set.of("type","io-type","amount","triggerFailure","parallelizeUnaffected",
                "triggerTime","triggerRepeatable","ignoreOutputCheck","selector-tag");
        for (var key : json.keySet()) if (!allowed.contains(key)) throw new JsonParseException("Unknown computation field: " + key);
        if (!json.has("amount") || !json.get("amount").isJsonPrimitive() || !json.getAsJsonPrimitive("amount").isNumber())
            throw new JsonParseException("Computation amount must be a number");
        if (json.has("triggerFailure") && (!json.get("triggerFailure").isJsonPrimitive() || !json.getAsJsonPrimitive("triggerFailure").isBoolean()))
            throw new JsonParseException("triggerFailure must be boolean");
        var exact = json.get("amount").getAsBigDecimal();
        double amount = json.get("amount").getAsDouble();
        if (exact.signum() < 0 || exact.signum() > 0 && amount == 0)
            throw new JsonParseException("Computation amount is negative or below the supported number range");
        return new RequirementComputation(amount, json.has("triggerFailure") && json.get("triggerFailure").getAsBoolean());
    }
}
