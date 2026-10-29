/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnvironment;
/** Uses the runtime parser so visual and JSON conditions have identical bounds and semantics. */
public final class EnvironmentEditor {
    private EnvironmentEditor() {}
    public static boolean isEnvironment(JsonObject j) {
        return j != null && j.has("type") && new JsonPrimitive(RequirementEnvironment.TYPE_ID).equals(j.get("type"));
    }
    public static JsonObject validate(JsonObject j) {
        if (!isEnvironment(j) || !new JsonPrimitive("input").equals(j.get("io-type")))
            throw new IllegalArgumentException("环境需求仅支持输入条件");
        if (j.has("chance")) throw new IllegalArgumentException("环境条件不支持概率，请先检查源文件中的 chance");
        var runtime = (RequirementEnvironment) new RequirementEnvironment.Type().createRequirement(IOType.INPUT, j);
        var clean = new JsonObject(); clean.addProperty("type", RequirementEnvironment.TYPE_ID); clean.addProperty("io-type", "input");
        runtime.writeJson(clean); return clean;
    }
    public static JsonObject merge(JsonObject source, JsonObject edited) {
        var clean = validate(edited);
        if (source == null) return clean;
        validate(source);
        var result = source.deepCopy();
        for (String key : java.util.List.of("condition", "min", "max", "values", "weather")) result.remove(key);
        clean.entrySet().forEach(e -> result.add(e.getKey(), e.getValue()));
        return result;
    }
    public static String label(JsonObject j) {
        return switch (j.get("condition").getAsString()) {
            case "altitude" -> "高度"; case "time" -> "时间"; case "dimensions" -> "维度";
            case "biomes" -> "群系"; case "moonphase" -> "月相"; case "weather" -> "天气"; default -> "环境";
        };
    }
    public static String summary(JsonObject source) {
        var j = validate(source); String c = j.get("condition").getAsString();
        return label(j) + "：" + switch (c) {
            case "altitude", "time" -> j.get("min").getAsString() + "～" + j.get("max").getAsString() + (c.equals("time") ? " tick" : " Y");
            case "weather" -> switch(j.get("weather").getAsString()) {case "sunny" -> "晴天"; case "raining" -> "下雨"; case "snowing" -> "下雪"; default -> "雷暴";};
            default -> j.get("values").toString();
        };
    }
}
