/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementHyperNetMaintenance;
import hellfirepvp.modularmachinery.common.machine.IOType;

public record HyperNetMaintenanceRequirementType(RequirementHyperNetMaintenance.Kind kind) implements RequirementType {
    @Override public Requirement createRequirement(IOType io,JsonObject json){
        if(io!=IOType.INPUT)throw new JsonParseException("HyperNet maintenance must be an input requirement");
        var allowed=java.util.Set.of("type","io-type","amount","parallelizeUnaffected","selector-tag","triggerTime","triggerRepeatable","ignoreOutputCheck");
        for(var key:json.keySet())if(!allowed.contains(key))throw new JsonParseException("Unknown maintenance field: "+key);
        if(!json.has("amount")||!json.get("amount").isJsonPrimitive()||!json.getAsJsonPrimitive("amount").isNumber())
            throw new JsonParseException("Maintenance amount must be an exact nonnegative integer");
        try{return new RequirementHyperNetMaintenance(kind,json.get("amount").getAsBigDecimal().intValueExact());}
        catch(IllegalArgumentException|ArithmeticException e){throw new JsonParseException("Invalid maintenance amount",e);}
    }
}
