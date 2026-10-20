/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.List;

/** Editor subset of the source optional catalyst; source-only fields are restored from disk. */
public final class CatalystEditor {
    public static final String TYPE = "modularmachinery:catalyst";
    public static final int MAX_MODIFIERS = 32;
    public static final List<String> TARGETS = List.of("modularmachinery:duration", "modularmachinery:energy",
            "modularmachinery:item", "modularmachinery:fluid");
    private CatalystEditor() {}

    public static boolean isCatalyst(JsonObject json) {
        return json != null && json.has("type") && TYPE.equals(json.get("type").getAsString());
    }

    public static JsonObject empty() {
        var result = IngredientGroupEditor.empty(false);
        result.addProperty("type", TYPE);
        result.add("modifiers", new JsonArray());
        return result;
    }

    private static JsonObject asGroup(JsonObject json) {
        var result = json.deepCopy();
        result.addProperty("type", IngredientGroupEditor.TYPE);
        return result;
    }

    public static JsonObject validate(JsonObject json) {
        if (!isCatalyst(json) || json.has("io-type") && !"input".equals(json.get("io-type").getAsString()))
            throw new IllegalArgumentException("催化剂只能用于输入");
        var result = IngredientGroupEditor.validate(asGroup(json));
        result.addProperty("type", TYPE);
        JsonArray mods = new JsonArray();
        if (json.has("modifiers")) {
            if (!json.get("modifiers").isJsonArray() || json.getAsJsonArray("modifiers").size() > MAX_MODIFIERS)
                throw new IllegalArgumentException("催化剂最多支持 " + MAX_MODIFIERS + " 个修改器");
            for (var entry : json.getAsJsonArray("modifiers")) {
                if (!entry.isJsonObject()) throw new IllegalArgumentException("修改器格式无效");
                mods.add(validateModifier(entry.getAsJsonObject()));
            }
        }
        result.add("modifiers", mods);
        // Omission means preserve the source field; an explicit [] means clear it.
        if (json.has("tooltips")) {
            if (!json.get("tooltips").isJsonArray()) throw new IllegalArgumentException("催化剂提示须为文字数组");
            var lines = new java.util.ArrayList<String>();
            for (var line : json.getAsJsonArray("tooltips")) {
                if (!line.isJsonPrimitive() || !line.getAsJsonPrimitive().isString())
                    throw new IllegalArgumentException("催化剂提示每行须为文字");
                lines.add(line.getAsString());
            }
            RecipeTooltipEditor.validate(lines);
            result.add("tooltips", json.getAsJsonArray("tooltips").deepCopy());
        }
        return result;
    }

    public static JsonObject modifier() {
        JsonObject result = new JsonObject();
        result.addProperty("target", TARGETS.get(0));
        result.addProperty("io", "input");
        result.addProperty("operation", 1);
        result.addProperty("multiplier", 0.5F);
        result.addProperty("affectChance", false);
        result.addProperty(IngredientGroupEditor.ORIGIN, -1);
        return result;
    }

    public static JsonObject validateModifier(JsonObject json) {
        try {
            String target = json.get("target").getAsString(), io = json.get("io").getAsString();
            if (!TARGETS.contains(target) || !List.of("input", "output").contains(io)) throw new IllegalArgumentException();
            if (!json.getAsJsonPrimitive("operation").isNumber() || !json.getAsJsonPrimitive("multiplier").isNumber())
                throw new IllegalArgumentException();
            int operation = json.get("operation").getAsBigDecimal().intValueExact();
            float value = json.get("multiplier").getAsFloat();
            if ((operation != 0 && operation != 1) || !Float.isFinite(value)) throw new IllegalArgumentException();
            boolean chance = false;
            if (json.has("affectChance")) {
                if (!json.getAsJsonPrimitive("affectChance").isBoolean()) throw new IllegalArgumentException();
                chance = json.get("affectChance").getAsBoolean();
            }
            if ((target.equals(TARGETS.get(0)) || target.equals(TARGETS.get(1))) && chance) throw new IllegalArgumentException();
            JsonObject result = new JsonObject();
            result.addProperty("target", target);
            result.addProperty("io", io);
            result.addProperty("operation", operation);
            result.addProperty("multiplier", value);
            result.addProperty("affectChance", chance);
            if (json.has(IngredientGroupEditor.ORIGIN)) {
                if (!json.getAsJsonPrimitive(IngredientGroupEditor.ORIGIN).isNumber()) throw new IllegalArgumentException();
                result.addProperty(IngredientGroupEditor.ORIGIN, json.get(IngredientGroupEditor.ORIGIN).getAsBigDecimal().intValueExact());
            }
            return result;
        } catch (RuntimeException invalid) {
            throw new IllegalArgumentException("修改器须选择时间／能量／物品／流体、输入或输出、加法或乘法，并填写有限数值；时间和能量不支持概率修改");
        }
    }

    public static void assignOrigins(JsonObject draft) {
        var mods = draft.getAsJsonArray("modifiers");
        for (int i = 0; i < mods.size(); i++) mods.get(i).getAsJsonObject().addProperty(IngredientGroupEditor.ORIGIN, i);
    }

    public static JsonObject merge(JsonObject source, JsonObject edited) {
        if (!isCatalyst(edited) || source != null && !isCatalyst(source))
            throw new IllegalArgumentException("不能将催化剂与普通候选组互换");
        var clean = validate(edited);
        if (source != null) validate(source);
        var result = IngredientGroupEditor.merge(source == null ? null : asGroup(source), asGroup(clean));
        result.addProperty("type", TYPE);
        var old = source != null && source.has("modifiers") ? source.getAsJsonArray("modifiers") : new JsonArray();
        JsonArray mods = new JsonArray();
        var origins = new HashSet<Integer>();
        for (var entry : clean.getAsJsonArray("modifiers")) {
            var mod = entry.getAsJsonObject();
            int origin = mod.has(IngredientGroupEditor.ORIGIN) ? mod.get(IngredientGroupEditor.ORIGIN).getAsInt() : -1;
            if (origin < -1 || origin >= old.size() || origin >= 0 && !origins.add(origin))
                throw new IllegalArgumentException("修改器来源已失效，请重新打开编辑器");
            var saved = origin < 0 ? new JsonObject() : old.get(origin).getAsJsonObject().deepCopy();
            for (String key : List.of("target", "io", "operation", "multiplier", "affectChance")) saved.add(key, mod.get(key));
            saved.remove(IngredientGroupEditor.ORIGIN);
            mods.add(saved);
        }
        result.add("modifiers", mods);
        if (clean.has("tooltips")) result.add("tooltips", clean.getAsJsonArray("tooltips").deepCopy());
        return result;
    }

    public static String targetLabel(String target) {
        return switch (target) {
            case "modularmachinery:duration" -> "运行时间";
            case "modularmachinery:energy" -> "能量 FE/t";
            case "modularmachinery:item" -> "物品";
            case "modularmachinery:fluid" -> "流体";
            default -> target;
        };
    }
}
