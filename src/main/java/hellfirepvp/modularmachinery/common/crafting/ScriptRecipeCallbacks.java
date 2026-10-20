/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting;

import java.util.LinkedHashMap;
import java.util.Map;

/** Runtime-only callbacks declared by a CraftTweaker recipe builder. */
public final class ScriptRecipeCallbacks {
    private final Map<Integer, ItemRequirementChecker> checkers = new LinkedHashMap<>();
    private final Map<Integer, java.util.List<ItemRequirementModifier>> modifiers = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, ItemRequirementChecker>> arrayCheckers = new LinkedHashMap<>();
    private final Map<Integer, Map<Integer, java.util.List<ItemRequirementModifier>>> arrayModifiers = new LinkedHashMap<>();

    public void setChecker(int requirement, ItemRequirementChecker checker) {
        if (checker != null) checkers.put(requirement, checker);
    }
    public void addModifier(int requirement, ItemRequirementModifier modifier) {
        if (modifier != null) modifiers.computeIfAbsent(requirement, ignored -> new java.util.ArrayList<>()).add(modifier);
    }
    public void setArrayChecker(int requirement, int candidate, ItemRequirementChecker checker) {
        if (checker != null) arrayCheckers.computeIfAbsent(requirement, ignored -> new LinkedHashMap<>()).put(candidate, checker);
    }
    public void addArrayModifier(int requirement, int candidate, ItemRequirementModifier modifier) {
        if (modifier != null) arrayModifiers.computeIfAbsent(requirement, ignored -> new LinkedHashMap<>())
                .computeIfAbsent(candidate, ignored -> new java.util.ArrayList<>()).add(modifier);
    }
    public void apply(java.util.List<Requirement> requirements) {
        checkers.forEach((index, checker) -> {
            if (index >= 0 && index < requirements.size() && requirements.get(index) instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem item)
                item.setItemChecker(checker);
        });
        modifiers.forEach((index, values) -> {
            if (index >= 0 && index < requirements.size() && requirements.get(index) instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem item)
                values.forEach(item::addItemModifier);
        });
        arrayCheckers.forEach((index, values) -> {
            if (index >= 0 && index < requirements.size() && requirements.get(index) instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray array)
                values.forEach(array::setCandidateChecker);
        });
        arrayModifiers.forEach((index, values) -> {
            if (index >= 0 && index < requirements.size() && requirements.get(index) instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray array)
                values.forEach((candidate, list) -> list.forEach(modifier -> array.addCandidateModifier(candidate, modifier)));
        });
    }
}
