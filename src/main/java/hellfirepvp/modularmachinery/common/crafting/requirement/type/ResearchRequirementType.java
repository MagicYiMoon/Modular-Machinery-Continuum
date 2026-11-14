/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementResearch;
import hellfirepvp.modularmachinery.common.machine.IOType;
import java.util.*;

public final class ResearchRequirementType implements RequirementType {
    @Override public Requirement createRequirement(IOType io,JsonObject json) {
        if (io != IOType.INPUT) throw new JsonParseException("Research is an input start gate");
        var allowed = Set.of("type","io-type","research");
        for (var key : json.keySet()) if (!allowed.contains(key)) throw new JsonParseException("Unsupported research field: " + key);
        if (!json.has("research") || !json.get("research").isJsonArray() || json.getAsJsonArray("research").size() > 64) throw new JsonParseException("research must be an array of IDs");
        var ids = new ArrayList<String>();
        for (var value : json.getAsJsonArray("research")) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new JsonParseException("research must contain strings");
            ids.add(value.getAsString());
        }
        return new RequirementResearch(ids);
    }
}
