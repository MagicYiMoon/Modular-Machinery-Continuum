/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.RecipeLoader;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineLoader;

/** Builds the small, server-authoritative document shown by the pack-author GUI. */
public final class MachineEditorSnapshot {
    private MachineEditorSnapshot() {}

    /** Merge unsaved new recipes without replacing any authoritative server entry. */
    public static JsonObject withLocalDrafts(String snapshot, JsonObject local) {
        JsonObject fresh = com.google.gson.JsonParser.parseString(snapshot).getAsJsonObject();
        if (!local.has("machines")) return fresh;
        for (var oldMachine : local.getAsJsonArray("machines")) {
            var old = oldMachine.getAsJsonObject();
            for (var newMachine : fresh.getAsJsonArray("machines")) {
                var target = newMachine.getAsJsonObject();
                if (!old.get("name").equals(target.get("name"))) continue;
                for (var oldRecipe : old.getAsJsonArray("recipes")) {
                    var draft = oldRecipe.getAsJsonObject();
                    if (!draft.has("newRecipe") || !draft.get("newRecipe").getAsBoolean()) continue;
                    boolean exists = false;
                    for (var entry : target.getAsJsonArray("recipes"))
                        if (draft.get("name").equals(entry.getAsJsonObject().get("name"))) { exists = true; break; }
                    if (!exists) target.getAsJsonArray("recipes").add(draft.deepCopy());
                }
            }
        }
        return fresh;
    }

    public static String create(MachineLoader machines, RecipeLoader recipes) {
        JsonObject root = new JsonObject();
        root.addProperty("defaultRecipeParallelized", hellfirepvp.modularmachinery.common.data.Config.recipeParallelizeEnabledByDefault());
        JsonArray machineArray = new JsonArray();
        JsonArray recipeArray = recipes.snapshot();
        for (DynamicMachine machine : machines.all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("name", machine.getRegistryName());
            entry.addProperty("localizedName", machine.getLocalizedName());
            entry.addProperty("editableName", true);
            var smartTypes = new JsonArray();
            SmartInterfaceEditor.options(machine).forEach(smartTypes::add);
            entry.add("smartInterfaceTypes", smartTypes);
            JsonArray owned = new JsonArray();
            for (var recipe : recipeArray) {
                if (recipe.isJsonObject()
                        && machine.getRegistryName().equals(recipe.getAsJsonObject().get("machine").getAsString())) {
                    JsonObject copy = recipe.getAsJsonObject().deepCopy();
                    if (copy.has("name")) {
                        String recipeId = copy.get("name").getAsString();
                        boolean editable = recipes.isSourceEditable(recipeId);
                        copy.addProperty("editable", editable);
                        var selectors = new JsonArray();
                        SelectorBindingEditor.options(recipeId, machines, recipes).forEach(selectors::add);
                        copy.add("selectorTags", selectors);
                        var sharedTypes = new JsonArray();
                        SmartInterfaceEditor.options(recipeId, machines, recipes).forEach(sharedTypes::add);
                        copy.add("smartInterfaceTypes", sharedTypes);
                        var loaded = recipes.getRecipe(recipeId);
                        for (int index = 0; index < loaded.requirements().size(); index++) {
                            copy.getAsJsonArray("requirements").get(index).getAsJsonObject().addProperty("editableSelector",
                                    editable && loaded.requirements().get(index) instanceof hellfirepvp.modularmachinery.common.crafting.SelectorRequirement);
                            var requirement = loaded.requirements().get(index);
                            var entryJson = copy.getAsJsonArray("requirements").get(index).getAsJsonObject();
                            entryJson.addProperty("editableBatch", editable && RequirementBatchEditor.supports(requirement));
                            entryJson.addProperty("editableTrigger", editable && RequirementTriggerEditor.supports(requirement));
                            var original = recipes.getRequirementSource(requirement);
                            if (original != null) {
                                if (original.has("triggerTime")) entryJson.addProperty("triggerTime", requirement.getTriggerTime());
                                else entryJson.remove("triggerTime");
                                if (original.has("triggerRepeatable")) entryJson.addProperty("triggerRepeatable", requirement.isTriggerRepeatable());
                                else entryJson.remove("triggerRepeatable");
                            }
                            entryJson.addProperty("batchPolicy", RequirementBatchEditor.policy(recipes.getRequirementSource(requirement)));
                        }
                        if (!editable) {
                            String source = recipes.getSourcePath(recipeId);
                            copy.addProperty("readOnlyReason", source != null && source.startsWith("CraftTweaker:")
                                    ? "由 CraftTweaker 脚本生成，请在源脚本中修改"
                                    : "由适配器自动生成，请修改适配器模板或源配方");
                        }
                    }
                    owned.add(copy);
                }
            }
            entry.add("recipes", owned);
            machineArray.add(entry);
        }
        root.add("machines", machineArray);
        return root.toString();
    }
}
