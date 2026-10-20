/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.RecipeLoader;
import hellfirepvp.modularmachinery.common.machine.*;
import java.util.*;

/** Numeric smart-interface conditions read a value; they never consume a quantity or produce output. */
public final class SmartInterfaceEditor {
    private SmartInterfaceEditor() {}
    public static boolean isSmart(JsonObject json) {
        return json != null && json.has("type") && json.get("type").isJsonPrimitive()
                && "modularmachinery:interface_number_input".equals(json.get("type").getAsString());
    }
    public static List<String> options(DynamicMachine machine) {
        if (machine == null) return List.of();
        return machine.getSmartInterfaceTypes().keySet().stream().filter(SmartInterfaceEditor::validName).sorted().toList();
    }
    public static List<String> options(String recipeId, MachineLoader machines, RecipeLoader recipes) {
        var recipe = recipes.getRecipe(recipeId);
        if (recipe == null) return List.of();
        var types = new TreeSet<>(options(machines.findByName(recipe.machine())));
        String path = recipes.getSourcePath(recipeId);
        if (path != null && recipes.isSourceEditable(recipeId)) {
            for (String otherId : recipes.getRecipeNames()) {
                if (path.equals(recipes.getSourcePath(otherId)))
                    types.retainAll(options(machines.findByName(recipes.getRecipe(otherId).machine())));
            }
        }
        return List.copyOf(types);
    }
    private static boolean validName(String name) {
        return name != null && !name.isBlank() && name.length() <= 256 && name.chars().noneMatch(Character::isISOControl);
    }
    public static JsonObject create(String type, float min, float max) {
        if (!validName(type)) throw new IllegalArgumentException("请选择有效的智能接口类型");
        if (!Float.isFinite(min) || !Float.isFinite(max) || min > max)
            throw new IllegalArgumentException("范围须为有限数字，且最小值不能大于最大值");
        var result = new JsonObject();
        result.addProperty("type", "modularmachinery:interface_number_input");
        result.addProperty("io-type", "input"); result.addProperty("interface", type);
        result.addProperty("min", min); result.addProperty("max", max);
        return result;
    }
    public static JsonObject validate(JsonObject json) {
        if (!isSmart(json) || !json.has("io-type") || !"input".equals(json.get("io-type").getAsString())
                || !json.has("interface") || !json.get("interface").isJsonPrimitive()
                || !json.getAsJsonPrimitive("interface").isString())
            throw new IllegalArgumentException("仅支持智能接口数值输入条件");
        float min = number(json, "min");
        return create(json.get("interface").getAsString(), min, json.has("max") ? number(json, "max") : min);
    }
    private static float number(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonPrimitive() || !json.getAsJsonPrimitive(key).isNumber())
            throw new IllegalArgumentException("上下限必须为数字");
        return json.get(key).getAsFloat();
    }
    public static JsonObject merge(JsonObject source, JsonObject edited, List<String> options) {
        var clean = validate(edited);
        if (!options.contains(clean.get("interface").getAsString()))
            throw new IllegalArgumentException("接口类型未在所有对应机器中声明，请重新打开编辑器");
        if (source == null) return clean;
        validate(source);
        var result = source.deepCopy();
        result.add("interface", clean.get("interface")); result.add("min", clean.get("min"));
        if (source.has("max") || clean.get("min").getAsFloat() != clean.get("max").getAsFloat())
            result.add("max", clean.get("max"));
        return result;
    }
}
