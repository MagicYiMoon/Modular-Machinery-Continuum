/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;

/** Optional resource authoring without loading optional mods' classes. */
public final class ResourceAmountEditor {
    private ResourceAmountEditor() {}
    private static String type(JsonObject j) {
        return j != null && j.has("type") && j.get("type").isJsonPrimitive() && j.getAsJsonPrimitive("type").isString()
                ? j.get("type").getAsString() : "";
    }
    public static boolean isMana(JsonObject j) { return type(j).equals("modularmachinery:mana"); }
    public static boolean isEmber(JsonObject j) { return type(j).equals("modularmachinery:ember") || type(j).equals("gugu-utils:ember"); }
    public static boolean isLife(JsonObject j) { return type(j).equals("modularmachinery:lifeessence"); }
    public static boolean isWill(JsonObject j) { return type(j).equals("modularmachinery:will"); }
    public static boolean isAura(JsonObject j) { return type(j).equals("modularmachinery:aura"); }
    public static boolean isResource(JsonObject j) { return isMana(j) || isEmber(j) || isLife(j) || isWill(j) || isAura(j); }
    public static String label(JsonObject j) {
        return isMana(j) ? "Botania 魔力" : isEmber(j) ? "Embers 余烬" : isLife(j) ? "生命源质 LP" : isWill(j) ? "恶魔意志" : "自然灵气";
    }
    public static String unit(JsonObject j) {
        return isMana(j) ? "Mana" : isEmber(j) ? "Ember" : isLife(j) ? "LP" : isWill(j) ? "Will" : "Aura";
    }
    public static String amountText(JsonObject j) {
        var clean = validate(j);
        return clean.get(isEmber(clean) ? "ember" : "amount").getAsString() + " " + unit(clean)
                + (clean.has("perTick") && clean.get("perTick").getAsBoolean() ? "/t" : "");
    }
    public static final java.util.List<String> WILL_TYPES = java.util.List.of("raw", "corrosive", "destructive", "vengeful", "steadfast");
    public static String modId(JsonObject j) {
        if (!isResource(j)) throw new IllegalArgumentException("资源类型无效");
        return isMana(j) ? "botania" : isEmber(j) ? "embers" : isAura(j) ? "naturesaura" : "bloodmagic";
    }
    private static boolean bool(JsonObject j, String key) {
        if (!j.has(key)) return false;
        if (!j.get(key).isJsonPrimitive() || !j.getAsJsonPrimitive(key).isBoolean()) throw new IllegalArgumentException(key + " 须为布尔值");
        return j.get(key).getAsBoolean();
    }
    public static JsonObject mana(boolean output, int amount, boolean rate) {
        if (amount < 0) throw new IllegalArgumentException("魔力须为 0～2147483647 的整数");
        var j = new JsonObject(); j.addProperty("type", "modularmachinery:mana");
        j.addProperty("io-type", output ? "output" : "input"); j.addProperty("amount", amount); j.addProperty("perTick", rate); return j;
    }
    public static JsonObject ember(double amount) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("余烬须为有限非负数，可填小数");
        var j = new JsonObject(); j.addProperty("type", "modularmachinery:ember");
        j.addProperty("io-type", "input"); j.addProperty("ember", amount); return j;
    }
    public static JsonObject life(boolean output, int amount, boolean rate) {
        var j = mana(output, amount, rate); j.addProperty("type", "modularmachinery:lifeessence"); return j;
    }
    public static JsonObject will(boolean output, double amount, String kind, double min, double max) {
        if (!Double.isFinite(amount) || amount < 0 || !Double.isFinite(min) || min < 0 || !Double.isFinite(max) || max < 0)
            throw new IllegalArgumentException("意志用量和阈值须为有限非负数");
        if (!WILL_TYPES.contains(kind)) throw new IllegalArgumentException("意志类型无效");
        var j = new JsonObject(); j.addProperty("type", "modularmachinery:will"); j.addProperty("io-type", output ? "output" : "input");
        j.addProperty("amount", amount); j.addProperty("will-type", kind); j.addProperty("min", min); j.addProperty("max", max); return j;
    }
    public static JsonObject aura(boolean output, int amount, String kind, int min, int max) {
        if (amount < 0) throw new IllegalArgumentException("灵气用量须为 0～2147483647 的整数");
        if (net.minecraft.resources.ResourceLocation.tryParse(kind) == null) throw new IllegalArgumentException("灵气类型 ID 无效");
        var j = new JsonObject(); j.addProperty("type", "modularmachinery:aura"); j.addProperty("io-type", output ? "output" : "input");
        j.addProperty("amount", amount); j.addProperty("aura-type", kind); j.addProperty("min-aura", min); j.addProperty("max-aura", max); return j;
    }
    private static int integer(JsonObject j, String key, Integer fallback) {
        if (!j.has(key) && fallback != null) return fallback;
        try { numeric(j,key); return j.get(key).getAsBigDecimal().intValueExact(); }
        catch (RuntimeException invalid) { throw new IllegalArgumentException(key + " 须为 int 范围内的整数"); }
    }
    private static double number(JsonObject j, String key, Double fallback) {
        if (!j.has(key) && fallback != null) return fallback;
        numeric(j,key); double n=j.get(key).getAsDouble();
        if (!Double.isFinite(n) || n < 0) throw new IllegalArgumentException(key + " 须为有限非负数"); return n;
    }
    private static void numeric(JsonObject j, String key) {
        if (!j.has(key) || !j.get(key).isJsonPrimitive() || !j.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException(key + " 须为数字");
    }
    private static String string(JsonObject j, String key) {
        if (!j.has(key) || !j.get(key).isJsonPrimitive() || !j.getAsJsonPrimitive(key).isString()) throw new IllegalArgumentException(key + " 须为文本");
        return j.get(key).getAsString();
    }
    public static JsonObject validate(JsonObject j) {
        if (!isResource(j) || !j.has("io-type") || !j.get("io-type").isJsonPrimitive() || !j.getAsJsonPrimitive("io-type").isString())
            throw new IllegalArgumentException("资源类型或方向无效");
        String io = j.get("io-type").getAsString();
        if (!io.equals("input") && !io.equals("output")) throw new IllegalArgumentException("输入输出方向无效");
        if (j.has("chance")) throw new IllegalArgumentException("此资源不支持概率，请先检查源文件中的 chance");
        bool(j, "parallelizeUnaffected");
        boolean rate = bool(j, "perTick"); boolean out = io.equals("output");
        if (isMana(j)) return mana(out, integer(j,"amount",null),rate);
        if (isLife(j)) return life(out, integer(j,"amount",null),rate);
        if (rate) throw new IllegalArgumentException("余烬、意志和灵气仅支持单次处理");
        if (isWill(j)) {
            if (j.has("parallelizeUnaffected")) throw new IllegalArgumentException("恶魔意志整批只算一份，不提供批量策略");
            return will(out,number(j,"amount",null),string(j,"will-type"),number(j,"min",0d),number(j,"max",100d));
        }
        if (isAura(j)) return aura(out,integer(j,"amount",null),string(j,"aura-type"),integer(j,"min-aura",Integer.MIN_VALUE),integer(j,"max-aura",Integer.MAX_VALUE));
        if (out) throw new IllegalArgumentException("余烬仅支持单次输入");
        return ember(number(j,"ember",null));
    }
    public static JsonObject merge(JsonObject source, JsonObject edited) {
        var clean = validate(edited);
        if (source == null) return clean;
        validate(source);
        if (!validate(source).get("type").equals(clean.get("type")) || !source.get("io-type").equals(clean.get("io-type")))
            throw new IllegalArgumentException("不能改变资源类型或输入输出方向，请另建需求");
        var result = source.deepCopy();
        if (isMana(clean) || isLife(clean)) { result.add("amount", clean.get("amount")); result.add("perTick", clean.get("perTick")); }
        else if (isEmber(clean)) result.add("ember", clean.get("ember"));
        else {
            result.add("amount", clean.get("amount"));
            boolean output = "output".equals(clean.get("io-type").getAsString());
            String kind = isWill(clean) ? "will-type" : "aura-type";
            String bound = isWill(clean) ? output ? "max" : "min" : output ? "max-aura" : "min-aura";
            result.add(kind, clean.get(kind)); result.add(bound, clean.get(bound));
        }
        return result;
    }
}
