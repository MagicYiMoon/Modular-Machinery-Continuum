/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;

/** Mod-neutral validation: loading the editor does not link any optional PNC classes. */
public final class PneumaticEditor {
    private PneumaticEditor() {}
    private static String type(JsonObject j) {
        return j != null && j.has("type") && j.get("type").isJsonPrimitive()
                && j.getAsJsonPrimitive("type").isString() ? j.get("type").getAsString() : "";
    }
    public static boolean isAir(JsonObject j) {
        return java.util.Set.of("modularmachinery:compressed_air", "modularmachinery:compressed_air_pertick",
                "gugu-utils:compressed_air", "gugu-utils:compressed_air_pertick").contains(type(j));
    }
    public static boolean isTemperature(JsonObject j) { return type(j).equals("modularmachinery:temperature"); }
    public static boolean isPneumatic(JsonObject j) { return isAir(j) || isTemperature(j); }
    public static boolean perTick(JsonObject j) { boolean explicit = bool(j, "perTick", false); return type(j).endsWith("_pertick") || explicit; }
    public static JsonObject air(boolean output, int amount, float pressure, boolean rate) {
        if (amount < 0 || !Float.isFinite(pressure) || pressure < -1 || pressure > 25)
            throw new IllegalArgumentException("空气须为 0～2147483647 的整数；压力须在 -1～25 bar 内");
        var j = new JsonObject(); j.addProperty("type", "modularmachinery:compressed_air");
        j.addProperty("io-type", output ? "output" : "input"); j.addProperty("air", amount);
        j.addProperty("pressure", pressure); j.addProperty("perTick", rate); return j;
    }
    public static JsonObject temperature(int min, int max) {
        if (min < 0 || max < min) throw new IllegalArgumentException("温度须为 0～2147483647 K 的整数，且最小值 ≤ 最大值");
        var j = new JsonObject(); j.addProperty("type", "modularmachinery:temperature");
        j.addProperty("io-type", "input"); j.addProperty("min", min); j.addProperty("max", max); return j;
    }
    private static boolean bool(JsonObject j, String key, boolean fallback) {
        if (!j.has(key)) return fallback;
        if (!j.get(key).isJsonPrimitive() || !j.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException(key + " 须为布尔值");
        return j.get(key).getAsBoolean();
    }
    private static int integer(JsonObject j, String key, int fallback) {
        if (!j.has(key)) return fallback;
        try {
            if (!j.get(key).isJsonPrimitive() || !j.getAsJsonPrimitive(key).isNumber()) throw new ArithmeticException();
            return j.get(key).getAsBigDecimal().intValueExact();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException(key + " 须为有效整数"); }
    }
    public static JsonObject validate(JsonObject j) {
        if (!isPneumatic(j) || !j.has("io-type") || !j.get("io-type").isJsonPrimitive()
                || !j.getAsJsonPrimitive("io-type").isString()) throw new IllegalArgumentException("气动需求类型或方向无效");
        String io = j.get("io-type").getAsString();
        if (!io.equals("input") && !io.equals("output")) throw new IllegalArgumentException("输入输出方向无效");
        // These families never roll a chance; leave ambiguous legacy definitions read-only.
        if (j.has("chance")) throw new IllegalArgumentException("气动条件不支持概率，请先检查源文件中的 chance");
        if (isTemperature(j)) {
            if (!io.equals("input") || (!j.has("min") && !j.has("max"))) throw new IllegalArgumentException("温度仅支持输入条件，且至少需一个边界");
            return temperature(integer(j, "min", 0), integer(j, "max", Integer.MAX_VALUE));
        }
        if (io.equals("input") && !j.has("pressure") || io.equals("output") && !j.has("air"))
            throw new IllegalArgumentException("输入需要压力，输出需要空气用量");
        float pressure = 0;
        if (j.has("pressure")) {
            if (!j.get("pressure").isJsonPrimitive() || !j.getAsJsonPrimitive("pressure").isNumber()) throw new IllegalArgumentException("压力须为数字");
            pressure = j.get("pressure").getAsFloat();
        }
        bool(j, "parallelizeUnaffected", false);
        return air(io.equals("output"), integer(j, "air", 0), pressure, perTick(j));
    }
    public static JsonObject merge(JsonObject source, JsonObject edited) {
        var clean = validate(edited);
        if (source == null) return clean;
        validate(source);
        if (isAir(source) != isAir(clean) || !source.get("io-type").equals(clean.get("io-type")))
            throw new IllegalArgumentException("不能改变气动需求的类型或输入输出方向，请另建需求");
        var result = source.deepCopy();
        if (isAir(clean)) {
            // Canonical base type is required: the legacy _pertick alias forces rate=true.
            for (String key : java.util.List.of("type", "air", "pressure", "perTick")) result.add(key, clean.get(key));
        } else {
            result.add("min", clean.get("min")); result.add("max", clean.get("max"));
        }
        return result;
    }
}
