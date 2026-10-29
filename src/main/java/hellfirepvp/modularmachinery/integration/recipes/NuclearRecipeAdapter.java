/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.recipes;

import igentuman.nc.recipes.type.NcRecipe;
import igentuman.nc.content.processors.Processors;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.adapter.ForeignRecipeAdapter;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

/** Modern Neoteric counterpart; deliberately distinct from old NuclearCraft Overhauled IDs. */
public final class NuclearRecipeAdapter extends ForeignRecipeAdapter {
    private final String processor;
    public NuclearRecipeAdapter(String processor) { super(new ResourceLocation("nuclearcraft", processor)); this.processor = processor; }
    @Override protected boolean accepts(Recipe<?> recipe) { return recipe instanceof NcRecipe nc && processor.equals(nc.getCodeId()); }
    @Override protected Converted convert(Recipe<?> source, List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> modifiers) {
        var recipe = (NcRecipe) source; var prefab = Processors.all().get(processor);
        if (prefab == null) throw new IllegalArgumentException("processor unavailable");
        double time = prefab.getTime() * recipe.getTimeModifier(), energy = prefab.getPower() * recipe.getEnergy();
        if (!Double.isFinite(time) || !Double.isFinite(energy) || time <= 0 || energy < 0 || time > Integer.MAX_VALUE || energy > Integer.MAX_VALUE)
            throw new IllegalArgumentException("invalid processor cost");
        var requirements = new ArrayList<Requirement>(); var fluids = new ArrayList<List<FluidStack>>();
        for (int i = 0; i < recipe.getInputItems().length; i++) {
            var input = recipe.getInputItems()[i];
            if (input == null) continue;
            if (input.getRepresentations().stream().anyMatch(stack -> stack.hasTag())) throw new IllegalArgumentException("NBT item ingredient");
            requirements.add(itemInput(recipe.getInputIngredient(i), input.getAmount()));
        }
        for (var input : recipe.getInputFluids()) if (input != null && input.getAmount() > 0) fluids.add(fluids(input.getRepresentations()));
        if (recipe.getResultItems().size() != Arrays.stream(recipe.getOutputItems()).filter(Objects::nonNull).count())
            throw new IllegalArgumentException("native preferred item output unresolved");
        for (var output : recipe.getResultItems()) if (!output.isEmpty()) requirements.add(item(IOType.OUTPUT, output));
        for (var output : recipe.getOutputFluids()) if (!output.isEmpty()) requirements.add(fluid(IOType.OUTPUT, output));
        return new Converted(Math.max(1, (int) Math.ceil(time)), (int) Math.ceil(energy), requirements, fluids);
    }
}
