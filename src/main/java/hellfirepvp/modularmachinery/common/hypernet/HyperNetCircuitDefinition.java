/* SPDX-License-Identifier: GPL-3.0-only
 * Circuit wear settings adapt NECO old.ComputationCenterType, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.*;

/** Optional pack balance; max wear is exclusive, equal bounds mean a fixed amount. */
public record HyperNetCircuitDefinition(int capacity, int minWear, int maxWear, double wearChance) {
    public HyperNetCircuitDefinition {
        if (capacity <= 0 || minWear < 0 || maxWear < minWear || !Double.isFinite(wearChance)
                || wearChance < 0 || wearChance > 1) throw new IllegalArgumentException("Invalid circuit configuration");
    }
    public static HyperNetCircuitDefinition parse(JsonElement value) {
        if (!value.isJsonObject()) throw new IllegalArgumentException("hypernet.circuit must be an object");
        var json = value.getAsJsonObject();
        for (var key : json.keySet()) if (!java.util.Set.of("capacity", "min-wear", "max-wear", "wear-chance").contains(key))
            throw new IllegalArgumentException("Unknown circuit field: " + key);
        if (!json.has("wear-chance") || !json.get("wear-chance").isJsonPrimitive() || !json.getAsJsonPrimitive("wear-chance").isNumber())
            throw new IllegalArgumentException("circuit.wear-chance must be numeric");
        var chance=json.get("wear-chance").getAsBigDecimal();
        if(chance.signum()<0||chance.compareTo(java.math.BigDecimal.ONE)>0)throw new IllegalArgumentException("circuit.wear-chance must be in [0,1]");
        return new HyperNetCircuitDefinition(integer(json,"capacity"), integer(json,"min-wear"), integer(json,"max-wear"),json.get("wear-chance").getAsDouble());
    }
    private static int integer(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("circuit." + key + " must be an exact integer");
        try { return json.get(key).getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException e) { throw new IllegalArgumentException("circuit." + key + " must be an exact integer",e); }
    }
}
