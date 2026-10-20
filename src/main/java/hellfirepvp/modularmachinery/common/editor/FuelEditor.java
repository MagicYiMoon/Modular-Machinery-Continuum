/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;

/** Source any:fuel uses a burn-time budget, never an item quantity. */
public final class FuelEditor {
    private FuelEditor() {}
    public static boolean isFuel(JsonObject json) {
        return json != null && json.has("type") && json.get("type").isJsonPrimitive()
                && "modularmachinery:item".equals(json.get("type").getAsString())
                && json.has("item") && json.get("item").isJsonPrimitive()
                && "any:fuel".equalsIgnoreCase(json.get("item").getAsString());
    }
    public static JsonObject create(int time, float chance) {
        if (time < 1 || time > 2_000_000_000) throw new IllegalArgumentException("燃烧时间必须为 1～2000000000 tick");
        if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("消耗概率必须为 0～1");
        var result = new JsonObject();
        result.addProperty("type", "modularmachinery:item");
        result.addProperty("io-type", "input");
        result.addProperty("item", "any:fuel");
        result.addProperty("time", time);
        result.addProperty("chance", chance);
        return result;
    }
    public static JsonObject validate(JsonObject json) {
        if (!isFuel(json) || !json.has("io-type") || !"input".equals(json.get("io-type").getAsString())
                || json.has("nbt") || json.has("nbt-display") || json.has("consumeDurability"))
            throw new IllegalArgumentException("仅支持无 NBT 的普通燃料输入；高级条件请修改源文件");
        int time;
        try {
            if (!json.has("time") || !json.get("time").isJsonPrimitive() || !json.getAsJsonPrimitive("time").isNumber())
                throw new IllegalArgumentException();
            time = json.get("time").getAsBigDecimal().intValueExact();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("燃烧时间必须为整数"); }
        if (json.has("chance") && (!json.get("chance").isJsonPrimitive() || !json.getAsJsonPrimitive("chance").isNumber()))
            throw new IllegalArgumentException("消耗概率必须为 0～1 的数字");
        return create(time, json.has("chance") ? json.get("chance").getAsFloat() : 1F);
    }
    public static boolean canEdit(JsonObject json) {
        try { validate(json); return true; } catch (RuntimeException invalid) { return false; }
    }
    public static JsonObject merge(JsonObject source, JsonObject edited) {
        var clean = validate(edited);
        if (source == null) return clean;
        if (!canEdit(source)) throw new IllegalArgumentException("源需求不是普通燃料输入，或包含高级条件，请修改源文件");
        var result = source.deepCopy();
        result.add("time", clean.get("time"));
        result.add("chance", clean.get("chance"));
        return result;
    }
}
