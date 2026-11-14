/* SPDX-License-Identifier: GPL-3.0-only
 * Research concepts adapted from NECO old.research.ResearchCognitionData, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.*;
import java.math.BigDecimal;
import java.util.*;

/** Stable IDs are stored in physical databases; definitions never grant player/world unlocks. */
public record HyperNetResearch(String id, String displayName, double techLevel, BigDecimal requiredPoints,
                               BigDecimal minimumPerTick, List<String> dependencies, List<String> descriptions,
                               List<String> unlockDescriptions, boolean hidden,HyperNetResearchIcon previewIcon) {
    public HyperNetResearch(String id,String name,double tech,BigDecimal points,BigDecimal minimum,List<String> dependencies,List<String> descriptions,List<String> unlocks,boolean hidden){
        this(id,name,tech,points,minimum,dependencies,descriptions,unlocks,hidden,"minecraft:paper");
    }
    public HyperNetResearch(String id,String name,double tech,BigDecimal points,BigDecimal minimum,List<String> dependencies,List<String> descriptions,List<String> unlocks,boolean hidden,String item){
        this(id,name,tech,points,minimum,dependencies,descriptions,unlocks,hidden,HyperNetResearchIcon.ofItem(item));
    }
    public String previewItem(){return previewIcon.item();}
    public static String checkedId(String id) {
        if (id == null || id.isBlank() || id.length() > 256 || id.codePoints().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("Invalid research id: " + id);
        return id;
    }
    public HyperNetResearch {
        checkedId(id);
        if(previewIcon==null)
            throw new IllegalArgumentException("Invalid research preview item");
        if (displayName == null || displayName.length() > 256 || !Double.isFinite(techLevel) || techLevel < 0
                || requiredPoints == null || minimumPerTick == null || requiredPoints.signum() <= 0 || minimumPerTick.signum() <= 0
                || !supported(requiredPoints) || !supported(minimumPerTick)) throw new IllegalArgumentException("Invalid research parameters: " + id);
        dependencies = List.copyOf(dependencies); descriptions = List.copyOf(descriptions); unlockDescriptions = List.copyOf(unlockDescriptions);
        if (dependencies.size() > 64 || new HashSet<>(dependencies).size() != dependencies.size()) throw new IllegalArgumentException("Duplicate/too many dependencies: " + id);
        dependencies.forEach(HyperNetResearch::checkedId);
        for (var lines : List.of(descriptions, unlockDescriptions)) {
            if (lines.size() > 64 || lines.stream().anyMatch(s -> s.length() > 1024)) throw new IllegalArgumentException("Research description too long");
        }
    }
    static boolean supported(BigDecimal n) {
        return n.precision() <= 128 && Math.abs((long)n.scale()) <= 1024
                && Double.isFinite(n.doubleValue()) && n.doubleValue() > 0;
    }
    static boolean supportedProgress(BigDecimal n) {
        return n != null && n.signum() >= 0 && n.precision() <= 1024 && Math.abs((long)n.scale()) <= 1024
                && (n.signum() == 0 || Double.isFinite(n.doubleValue()) && n.doubleValue() > 0);
    }
    public static HyperNetResearch parse(JsonObject j) {
        var allowed = Set.of("id", "display-name", "tech-level", "required-points", "minimum-per-tick", "dependencies", "descriptions", "unlock-descriptions", "hidden","preview-item","preview-stack");
        for (var key : j.keySet()) if (!allowed.contains(key)) throw new IllegalArgumentException("Unknown research field: " + key);
        String id = text(j, "id");
        double tech = j.has("tech-level") ? number(j,"tech-level").doubleValue() : 0;
        boolean hidden = false;
        if (j.has("hidden")) {
            if (!j.get("hidden").isJsonPrimitive() || !j.getAsJsonPrimitive("hidden").isBoolean()) throw new IllegalArgumentException("hidden must be boolean");
            hidden = j.get("hidden").getAsBoolean();
        }
        return new HyperNetResearch(id, j.has("display-name") ? text(j,"display-name") : id, tech,
                number(j,"required-points"), number(j,"minimum-per-tick"), strings(j,"dependencies"),
                strings(j,"descriptions"), strings(j,"unlock-descriptions"), hidden,HyperNetResearchIcon.parse(j));
    }
    /** Canonical editable definition; stable IDs must never be silently renamed. */
    public JsonObject json() {
        var j=new JsonObject();j.addProperty("id",id);j.addProperty("display-name",displayName);
        j.addProperty("tech-level",techLevel);j.addProperty("required-points",requiredPoints);j.addProperty("minimum-per-tick",minimumPerTick);
        for(var key:List.of("dependencies","descriptions","unlock-descriptions")) {
            var a=new JsonArray();(key.equals("dependencies")?dependencies:key.equals("descriptions")?descriptions:unlockDescriptions).forEach(a::add);j.add(key,a);
        }
        j.addProperty("hidden",hidden);previewIcon.put(j);return j;
    }
    private static String text(JsonObject j,String key) {
        if (!j.has(key) || !j.get(key).isJsonPrimitive() || !j.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException(key + " must be a string");
        return j.get(key).getAsString();
    }
    private static BigDecimal number(JsonObject j,String key) {
        if (!j.has(key) || !j.get(key).isJsonPrimitive() || !j.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException(key + " must be a number");
        return j.get(key).getAsBigDecimal();
    }
    private static List<String> strings(JsonObject j,String key) {
        if (!j.has(key)) return List.of();
        if (!j.get(key).isJsonArray() || j.getAsJsonArray(key).size() > 64) throw new IllegalArgumentException(key + " must be an array of at most 64 strings");
        var result = new ArrayList<String>();
        for (var value : j.getAsJsonArray(key)) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new IllegalArgumentException(key + " must contain strings");
            result.add(value.getAsString());
        }
        return result;
    }
}
