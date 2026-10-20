/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.botania;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.IOType;

public final class ManaRequirementType implements RequirementType {
    @Override public String requiresModid() { return "botania"; }
    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        if (!json.has("amount") || !json.get("amount").isJsonPrimitive() || !json.getAsJsonPrimitive("amount").isNumber())
            throw new JsonParseException("mana requires an integer amount");
        int amount;
        try { amount = json.get("amount").getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException ex) { throw new JsonParseException("mana amount is outside the integer range", ex); }
        if (amount < 0) throw new JsonParseException("mana amount must be non-negative");
        boolean rate = false;
        if (json.has("perTick")) {
            if (!json.get("perTick").isJsonPrimitive() || !json.getAsJsonPrimitive("perTick").isBoolean()) throw new JsonParseException("mana perTick must be boolean");
            rate = json.get("perTick").getAsBoolean();
        }
        var result = rate ? new RequirementMana.Rate(io, amount) : new RequirementMana(io, amount);
        if (json.has("parallelizeUnaffected")) result.setParallelizeUnaffected(json.get("parallelizeUnaffected").getAsBoolean());
        return result;
    }
}
