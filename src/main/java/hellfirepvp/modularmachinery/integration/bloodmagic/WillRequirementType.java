/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.IOType;
import wayoftime.bloodmagic.api.compat.EnumDemonWillType;

public final class WillRequirementType implements RequirementType {
    @Override public String requiresModid() { return "bloodmagic"; }
    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        double amount = number(json, "amount", Double.NaN), min = number(json, "min", 0), max = number(json, "max", 100);
        // Source uses min only for input, max only for output; their values are independent.
        if (!json.has("will-type") || !json.get("will-type").isJsonPrimitive() || !json.getAsJsonPrimitive("will-type").isString())
            throw new JsonParseException("will requires will-type");
        String name = json.get("will-type").getAsString();
        var type = "raw".equals(name) ? EnumDemonWillType.DEFAULT : switch (name) {
            case "corrosive", "destructive", "vengeful", "steadfast" -> EnumDemonWillType.getType(name);
            default -> throw new JsonParseException("Invalid demon will type: " + name);
        };
        if (json.has("perTick") && (!json.get("perTick").isJsonPrimitive()
                || !json.getAsJsonPrimitive("perTick").isBoolean() || json.get("perTick").getAsBoolean()))
            throw new JsonParseException("Source will requirements are one-shot; perTick is unsupported");
        return new RequirementWill(io, amount, type, min, max);
    }
    private static double number(JsonObject json, String name, double fallback) {
        if (!json.has(name)) {
            if (!Double.isFinite(fallback)) throw new JsonParseException("will requires " + name);
            return fallback;
        }
        if (!json.get(name).isJsonPrimitive() || !json.getAsJsonPrimitive(name).isNumber()) throw new JsonParseException("will " + name + " must be a number");
        double result = json.get(name).getAsDouble();
        if (!Double.isFinite(result) || result < 0) throw new JsonParseException("will " + name + " must be finite and non-negative");
        return result;
    }
}
