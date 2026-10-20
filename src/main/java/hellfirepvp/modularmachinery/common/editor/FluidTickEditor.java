/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;

/** A continuous fluid rate, never interchangeable with a one-time fluid quantity. */
public final class FluidTickEditor {
    private FluidTickEditor() {}
    public static boolean isRate(JsonObject json) {
        return json != null && json.has("type") && json.get("type").isJsonPrimitive()
                && "modularmachinery:fluid_pertick".equals(json.get("type").getAsString());
    }
    public static JsonObject create(String fluid, boolean output, int rate) {
        var clean = BasicRequirementType.FLUID.create(fluid, output, rate, 1);
        clean.addProperty("type", "modularmachinery:fluid_pertick");
        clean.remove("chance");
        return clean;
    }
    public static JsonObject validate(JsonObject json) {
        if (!isRate(json) || !json.has("io-type") || !json.get("io-type").isJsonPrimitive()
                || !json.has("fluid") || !json.get("fluid").isJsonPrimitive() || !json.getAsJsonPrimitive("fluid").isString()
                || json.has("nbt") || json.has("nbt-display"))
            throw new IllegalArgumentException("仅支持具体流体的每 tick 需求；NBT 等高级条件请修改源文件");
        String io = json.get("io-type").getAsString();
        if (!io.equals("input") && !io.equals("output")) throw new IllegalArgumentException("输入输出方向无效");
        int rate;
        try {
            if (!json.has("amount") || !json.get("amount").isJsonPrimitive() || !json.getAsJsonPrimitive("amount").isNumber())
                throw new IllegalArgumentException();
            rate = json.get("amount").getAsBigDecimal().intValueExact();
        } catch (RuntimeException invalid) { throw new IllegalArgumentException("速率必须为 1～2000000000 mB/t 的整数"); }
        if (json.has("chance") && (!json.get("chance").isJsonPrimitive() || !json.getAsJsonPrimitive("chance").isNumber()
                || json.get("chance").getAsDouble() != 1D))
            throw new IllegalArgumentException("持续流体速率不支持概率；已有 chance 只能为 1");
        return create(json.get("fluid").getAsString(), io.equals("output"), rate);
    }
    public static boolean canEdit(JsonObject json) {
        try { validate(json); return true; } catch (RuntimeException invalid) { return false; }
    }
    public static JsonObject merge(JsonObject source, JsonObject edited) {
        var clean = validate(edited);
        if (source == null) return clean;
        validate(source);
        if (!source.get("io-type").equals(clean.get("io-type")))
            throw new IllegalArgumentException("不能改变持续流体的输入输出方向，请另建需求");
        var result = source.deepCopy();
        result.add("fluid", clean.get("fluid")); result.add("amount", clean.get("amount"));
        return result;
    }
}
