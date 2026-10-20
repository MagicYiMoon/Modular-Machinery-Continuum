/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.naturesaura;

import com.google.gson.*;
import de.ellpeck.naturesaura.api.NaturesAuraAPI;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.resources.ResourceLocation;

public final class AuraRequirementType implements RequirementType {
    @Override public String requiresModid() { return "naturesaura"; }
    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        int amount = integer(json, "amount", null);
        if (amount < 0) throw new JsonParseException("aura amount must be non-negative");
        if (!json.has("aura-type") || !json.get("aura-type").isJsonPrimitive() || !json.getAsJsonPrimitive("aura-type").isString())
            throw new JsonParseException("aura requires an aura-type identifier");
        var id = ResourceLocation.tryParse(json.get("aura-type").getAsString());
        var type = id == null ? null : NaturesAuraAPI.AURA_TYPES.get(id);
        if (type == null) throw new JsonParseException("Unknown aura-type: " + json.get("aura-type"));
        if (json.has("perTick") && (!json.get("perTick").isJsonPrimitive() || !json.getAsJsonPrimitive("perTick").isBoolean() || json.get("perTick").getAsBoolean()))
            throw new JsonParseException("Source aura requirements are one-shot, not perTick");
        var result = new RequirementAura(io, amount, type, integer(json, "min-aura", Integer.MIN_VALUE), integer(json, "max-aura", Integer.MAX_VALUE));
        if (json.has("parallelizeUnaffected")) {
            if (!json.get("parallelizeUnaffected").isJsonPrimitive() || !json.getAsJsonPrimitive("parallelizeUnaffected").isBoolean())
                throw new JsonParseException("aura parallelizeUnaffected must be boolean");
            result.setParallelizeUnaffected(json.get("parallelizeUnaffected").getAsBoolean());
        }
        return result;
    }
    private static int integer(JsonObject json, String key, Integer fallback) {
        if (!json.has(key) && fallback != null) return fallback;
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) throw new JsonParseException("aura " + key + " must be an integer");
        try { return json.get(key).getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException ex) { throw new JsonParseException("aura " + key + " is outside the integer range", ex); }
    }
}
