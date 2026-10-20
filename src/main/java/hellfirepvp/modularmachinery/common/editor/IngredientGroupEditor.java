/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashSet;
import java.util.Set;

/** Ordinary input alternatives and weighted item outputs. Advanced source expressions are preserved. */
public final class IngredientGroupEditor {
    public static final String TYPE = "modularmachinery:ingredient_array_input";
    public static final String ORIGIN = "editor-origin-index";
    public static final int MAX_CANDIDATES = 64;
    private IngredientGroupEditor() {}

    public static boolean isGroup(JsonObject json) {
        return json != null && json.has("type") && (TYPE.equals(json.get("type").getAsString()) || CatalystEditor.isCatalyst(json));
    }

    public static JsonObject empty() {
        return empty(false);
    }

    public static boolean isOutput(JsonObject json) {
        return isGroup(json) && json.has("io-type") && "output".equals(json.get("io-type").getAsString());
    }

    public static JsonObject empty(boolean output) {
        JsonObject group = new JsonObject();
        group.addProperty("type", TYPE);
        group.addProperty("io-type", output ? "output" : "input");
        group.addProperty("chance", 1F);
        group.add("items", new JsonArray());
        return group;
    }

    public static boolean canEdit(JsonObject json) {
        try { validate(json); return true; }
        catch (RuntimeException invalid) { return false; }
    }

    /** Canonical request data. Unknown source metadata is recovered from disk, never accepted from the client. */
    public static JsonObject validate(JsonObject json) {
        if (CatalystEditor.isCatalyst(json)) return CatalystEditor.validate(json);
        if (!isGroup(json) || json.has("io-type") && !Set.of("input", "output").contains(json.get("io-type").getAsString())
                || json.has("nbt") || json.has("nbt-display") || !json.has("items") || !json.get("items").isJsonArray())
            throw new IllegalArgumentException("仅支持无 NBT 的候选输入组和随机物品输出组");
        boolean output = isOutput(json);
        var items = json.getAsJsonArray("items");
        if (items.isEmpty() || items.size() > MAX_CANDIDATES)
            throw new IllegalArgumentException("候选物品组需要 1～" + MAX_CANDIDATES + " 个候选项");
        float chance = json.has("chance") ? json.get("chance").getAsFloat() : 1F;
        if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("概率必须为 0～1");
        JsonObject result = empty(output);
        result.addProperty("chance", chance);
        boolean positiveWeight = false;
        for (var element : items) {
            if (!element.isJsonObject()) throw new IllegalArgumentException("候选物品数据无效");
            var candidate = element.getAsJsonObject();
            if (candidate.has("nbt") || candidate.has("nbt-display") || candidate.has("consumeDurability")
                    || !candidate.has("item") || !candidate.get("item").isJsonPrimitive()
                    || !candidate.getAsJsonPrimitive("item").isString())
                throw new IllegalArgumentException("候选项仅支持具体物品或物品标签，NBT 和耐久需求请修改源文件");
            int amount = integer(candidate, "amount", integer(json, "amount", 1));
            int min = integer(candidate, "minAmount", amount), max = integer(candidate, "maxAmount", amount);
            float weight = candidate.has("chance") ? candidate.get("chance").getAsFloat() : 1F;
            if (!output && (min != amount || max != amount || weight != 1F))
                throw new IllegalArgumentException("输入候选项的随机数量或特殊权重请在源文件中编辑");
            if (output && (min < 1 || max > 64 || min > max || !Float.isFinite(weight) || weight < 0))
                throw new IllegalArgumentException("数量范围必须为 1～64 且最少不大于最多；权重必须为非负有限数");
            var validated = BasicRequirementType.ITEM.create(candidate.get("item").getAsString(), output, amount, 1F);
            JsonObject clean = new JsonObject();
            clean.add("item", validated.get("item"));
            clean.addProperty("amount", amount);
            if (output) {
                clean.addProperty("minAmount", min);
                clean.addProperty("maxAmount", max);
                clean.addProperty("chance", weight);
                positiveWeight |= weight > 0;
            }
            if (candidate.has(ORIGIN)) clean.addProperty(ORIGIN, integer(candidate, ORIGIN, -1));
            result.getAsJsonArray("items").add(clean);
        }
        if (output && chance > 0 && !positiveWeight) throw new IllegalArgumentException("产出概率大于 0 时，至少一个产物的权重须大于 0");
        return result;
    }

    public static JsonObject draft(JsonObject source) {
        JsonObject draft = validate(source);
        for (int i = 0; i < draft.getAsJsonArray("items").size(); i++)
            draft.getAsJsonArray("items").get(i).getAsJsonObject().addProperty(ORIGIN, i);
        if (CatalystEditor.isCatalyst(draft)) CatalystEditor.assignOrigins(draft);
        return draft;
    }

    public static JsonObject candidate(String resource) {
        return candidate(resource, false);
    }

    public static JsonObject candidate(String resource, boolean output) {
        JsonObject item = BasicRequirementType.ITEM.create(resource, output, 1, 1F);
        JsonObject candidate = new JsonObject();
        candidate.add("item", item.get("item"));
        candidate.addProperty("amount", 1);
        if (output) {
            candidate.addProperty("minAmount", 1);
            candidate.addProperty("maxAmount", 1);
            candidate.addProperty("chance", 1F);
        }
        candidate.addProperty(ORIGIN, -1);
        return candidate;
    }

    /** Restore each retained candidate's metadata by its original index, even after reordering/reselection. */
    public static JsonObject merge(JsonObject source, JsonObject edited) {
        if (CatalystEditor.isCatalyst(source) || CatalystEditor.isCatalyst(edited)) return CatalystEditor.merge(source, edited);
        JsonObject clean = validate(edited);
        if (source != null && !canEdit(source)) throw new IllegalArgumentException("该候选组包含高级条件，请修改源文件");
        if (source != null && isOutput(source) != isOutput(clean)) throw new IllegalArgumentException("重新编辑时不能改变候选组的输入输出方向");
        JsonObject result = source == null ? empty(isOutput(clean)) : source.deepCopy();
        JsonArray items = new JsonArray();
        Set<Integer> origins = new HashSet<>();
        for (var entry : clean.getAsJsonArray("items")) {
            var item = entry.getAsJsonObject();
            int origin = integer(item, ORIGIN, -1);
            if (origin < -1 || origin >= (source == null ? 0 : source.getAsJsonArray("items").size())
                    || origin >= 0 && !origins.add(origin)) throw new IllegalArgumentException("候选项来源已失效，请重新打开编辑器");
            JsonObject saved = origin < 0 ? new JsonObject() : source.getAsJsonArray("items").get(origin).getAsJsonObject().deepCopy();
            saved.add("item", item.get("item"));
            saved.add("amount", item.get("amount"));
            if (isOutput(clean)) {
                saved.add("minAmount", item.get("minAmount"));
                saved.add("maxAmount", item.get("maxAmount"));
                saved.add("chance", item.get("chance"));
            } else {
                if (saved.has("minAmount")) saved.add("minAmount", item.get("amount"));
                if (saved.has("maxAmount")) saved.add("maxAmount", item.get("amount"));
            }
            saved.remove(ORIGIN);
            items.add(saved);
        }
        result.add("items", items);
        result.add("chance", clean.get("chance"));
        return result;
    }

    /** Conditional selection share; group chance is a separate gate. Double avoids overflowing float sums. */
    public static double selectionShare(JsonObject group, int index, float currentWeight) {
        if (!Float.isFinite(currentWeight) || currentWeight < 0) return 0;
        var items = group.getAsJsonArray("items");
        if (index < 0 || index >= items.size()) return 0;
        double total = 0;
        for (int i = 0; i < items.size(); i++) {
            var candidate = items.get(i).getAsJsonObject();
            float weight = i == index ? currentWeight : candidate.has("chance") ? candidate.get("chance").getAsFloat() : 1F;
            if (!Float.isFinite(weight) || weight < 0) return 0;
            total += weight;
        }
        return total > 0 ? currentWeight / total : 0;
    }

    private static int integer(JsonObject json, String key, int fallback) {
        if (!json.has(key)) return fallback;
        try {
            if (!json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException();
            return json.get(key).getAsBigDecimal().intValueExact();
        }
        catch (RuntimeException invalid) { throw new IllegalArgumentException("数量或候选项索引必须为整数"); }
    }
}
