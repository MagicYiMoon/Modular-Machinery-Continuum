/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.editor;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.RecipeLoader;
import hellfirepvp.modularmachinery.common.machine.*;
import java.util.*;

/** Component selector groups are exact structure tags, independent of smart-interface numeric types. */
public final class SelectorBindingEditor {
    public static final int MAX_LENGTH = 256;
    private SelectorBindingEditor() {}
    public static List<String> options(DynamicMachine machine) {
        var tags = new TreeSet<String>();
        collect(tags, machine.getPattern());
        machine.getDynamicPatterns().values().forEach(pattern -> {
            collect(tags, pattern.getPattern()); collect(tags, pattern.getPatternEnd());
        });
        return List.copyOf(tags);
    }
    private static void collect(Set<String> tags, MachinePattern pattern) {
        if (pattern == null) return;
        pattern.getParts().stream().map(MachinePattern.Part::selectorTag).filter(Objects::nonNull)
                .filter(tag -> !tag.isEmpty() && tag.length() <= MAX_LENGTH && tag.chars().noneMatch(Character::isISOControl))
                .forEach(tags::add);
    }
    /** A shared recipe file affects all its machines; offer only groups declared by every affected machine. */
    public static List<String> options(String recipeId, MachineLoader machines, RecipeLoader recipes) {
        var recipe = recipes.getRecipe(recipeId);
        if (recipe == null) return List.of();
        String path = recipes.getSourcePath(recipeId);
        var tags = new TreeSet<String>();
        var machine = machines.findByName(recipe.machine());
        if (machine == null) return List.of();
        tags.addAll(options(machine));
        if (path != null && recipes.isSourceEditable(recipeId)) {
            for (String otherId : recipes.getRecipeNames()) {
                if (!path.equals(recipes.getSourcePath(otherId))) continue;
                var other = machines.findByName(recipes.getRecipe(otherId).machine());
                if (other == null) return List.of();
                tags.retainAll(options(other));
            }
        }
        return List.copyOf(tags);
    }
    public static JsonObject merge(JsonObject source, String tag, List<String> options) {
        if (tag == null || tag.length() > MAX_LENGTH || tag.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("接口分组无效或超过 256 字符");
        String current = SelectorTags.read(source);
        if (!tag.isEmpty() && !tag.equals(current) && !options.contains(tag))
            throw new IllegalArgumentException("该分组未在对应机器结构中声明，请重新打开编辑器");
        var result = source.deepCopy();
        if (tag.isEmpty()) result.remove("selector-tag"); else result.addProperty("selector-tag", tag);
        return result;
    }
}
