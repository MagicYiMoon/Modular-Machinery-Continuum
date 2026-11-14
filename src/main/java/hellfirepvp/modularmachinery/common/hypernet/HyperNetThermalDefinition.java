/* SPDX-License-Identifier: GPL-3.0-only
 * Thermal thresholds/distribution adapt NECO old.DataProcessorType, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.JsonElement;

/** Opt-in processor heat; no pack-specific balance or materials are registered. */
public record HyperNetThermalDefinition(int overheatThreshold, int heatDistribution, String dynamicPattern) {
    public HyperNetThermalDefinition {
        if(overheatThreshold<=0||heatDistribution<0||dynamicPattern==null||dynamicPattern.length()>256)
            throw new IllegalArgumentException("Invalid processor thermal configuration");
    }
    public static HyperNetThermalDefinition parse(JsonElement value) {
        if(!value.isJsonObject())throw new IllegalArgumentException("hypernet.thermal must be an object");
        var json=value.getAsJsonObject();
        for(var key:json.keySet())if(!java.util.Set.of("overheat-threshold","heat-distribution","dynamic-pattern").contains(key))
            throw new IllegalArgumentException("Unknown thermal field: "+key);
        if(json.has("dynamic-pattern")&&(!json.get("dynamic-pattern").isJsonPrimitive()||!json.getAsJsonPrimitive("dynamic-pattern").isString()))
            throw new IllegalArgumentException("thermal.dynamic-pattern must be a string");
        return new HyperNetThermalDefinition(integer(json,"overheat-threshold"),integer(json,"heat-distribution"),
                json.has("dynamic-pattern")?json.get("dynamic-pattern").getAsString():"");
    }
    private static int integer(com.google.gson.JsonObject json,String key) {
        if(!json.has(key)||!json.get(key).isJsonPrimitive()||!json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("thermal."+key+" must be an exact integer");
        try{return json.get(key).getAsBigDecimal().intValueExact();}
        catch(ArithmeticException invalid){throw new IllegalArgumentException("thermal."+key+" must be an exact integer",invalid);}
    }
}
