/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.crafting.adapter;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The CE adapter registered for every loaded machine. It copies the source machine's already-expanded
 * recipes into the machine named by the template. This is the 1.20.1 equivalent of the upstream
 * {@code DynamicMachineRecipeAdapter}; the registry entry is late-bound because machine definitions are
 * config data rather than Forge registry objects.
 */
public final class DynamicMachineRecipeAdapter extends RecipeAdapter {

    private final DynamicMachine originalMachine;

    public DynamicMachineRecipeAdapter(ResourceLocation registryName, DynamicMachine originalMachine) {
        super(registryName);
        this.originalMachine = originalMachine;
    }

    @Override
    public Collection<MachineRecipe> createRecipesFor(String owningMachine,
                                                       List<RecipeModifier> modifiers,
                                                       List<Requirement> additionalRequirements) {
        if (originalMachine == null || owningMachine == null || owningMachine.isBlank()) {
            return List.of();
        }

        // RecipeLoader registers ordinary recipes before it expands adapter templates, matching the
        // upstream RecipeRegistry two-pass order. Reading that same loader also means a /reload sees the
        // current source set rather than a stale copy held by the adapter instance.
        List<MachineRecipe> source = MachineReloadListener.getRecipeLoader().getByMachine()
                .getOrDefault(originalMachine.getRegistryName(), List.of());
        if (source.isEmpty()) {
            RecipeAdapters.recordNotExpanded(getRegistryName(),
                    "source machine '" + originalMachine.getRegistryName() + "' has no loaded recipes");
            return List.of();
        }

        List<MachineRecipe> result = new ArrayList<>(source.size());
        String identifier = owningMachine.replace(':', '.');
        int copyId = incId++;
        for (MachineRecipe recipe : source) {
            if (recipe == null) continue;
            String copiedName = recipe.registryName() + ".copy." + identifier + "_" + copyId;

            List<Requirement> requirements = new ArrayList<>(recipe.requirements().size());
            for (Requirement requirement : recipe.requirements()) {
                Requirement current = requirement;
                if (modifiers != null) {
                    for (RecipeModifier modifier : modifiers) {
                        if (modifier != null) current = modifier.apply(current);
                    }
                }
                requirements.add(current);
            }

            int duration = RecipeModifier.effectiveDuration(modifiers, recipe.recipeTime());
            result.add(new MachineRecipe(owningMachine, copiedName, duration, requirements,
                    null, recipe.cancelOnPerTickFailure(), recipe.maxThreads(), recipe.threadName(),
                    recipe.priority(), recipe.commands(), recipe.formattedTooltips(),
                    recipe.parallelized(), recipe.loadJEI(), recipe.displayName()));
        }
        return result;
    }
}
