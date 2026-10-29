/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.recipes;

import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.adapter.ForeignRecipeAdapter;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.tconstruct.library.recipe.melting.MeltingRecipe;
import slimeknights.tconstruct.library.recipe.alloying.AlloyRecipe;
import java.util.*;

/** Static melting and alloying only; dynamic tool damage/ore-rate results require their own context. */
public final class TinkersRecipeAdapter extends ForeignRecipeAdapter {
    private final boolean melting;
    public TinkersRecipeAdapter(boolean melting) { super(new ResourceLocation("tconstruct", melting ? "smeltery_melting" : "smeltery_alloy")); this.melting = melting; }
    @Override protected boolean accepts(Recipe<?> recipe) {
        return melting ? recipe instanceof slimeknights.tconstruct.library.recipe.melting.IMeltingRecipe : recipe instanceof AlloyRecipe;
    }
    @Override protected Converted convert(Recipe<?> source, List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> modifiers) {
        var requirements = new ArrayList<Requirement>(); var inputs = new ArrayList<List<FluidStack>>(); int duration;
        if (melting) {
            if (source.getClass() != MeltingRecipe.class) throw new IllegalArgumentException("dynamic melting result (damage, material or ore rate)");
            var recipe = (MeltingRecipe) source;
            requirements.add(itemInput(recipe.getInput(), 1));
            for (var output : recipe.getOutputWithByproducts()) {
                if (output.size() != 1) throw new IllegalArgumentException("unresolved output alternatives");
                requirements.add(fluid(IOType.OUTPUT, output.get(0)));
            }
            duration = Math.max(1, recipe.getTemperature() / 10);
        } else {
            if (source.getClass() != AlloyRecipe.class) throw new IllegalArgumentException("custom alloy semantics");
            var recipe = (AlloyRecipe) source;
            for (var input : recipe.getInputs()) {
                if (input.catalyst()) throw new IllegalArgumentException("non-consuming fluid catalyst");
                inputs.add(fluids(input.fluid().getFluids()));
            }
            requirements.add(fluid(IOType.OUTPUT, recipe.getOutput())); duration = 4;
        }
        // Source adapters use temperature/10 or 4 ticks, with no implicit furnace fuel/FE requirement.
        return new Converted(duration, 0, requirements, inputs);
    }
}
