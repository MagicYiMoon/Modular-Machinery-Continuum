/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.pneumatic;

import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.adapter.ForeignRecipeAdapter;
import hellfirepvp.modularmachinery.common.machine.IOType;
import me.desht.pneumaticcraft.api.crafting.recipe.*;
import me.desht.pneumaticcraft.common.recipes.machine.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

/** Fixed modern conversions; native machine-specific throughput/upgrades remain template policy. */
public final class PneumaticRecipeAdapter extends ForeignRecipeAdapter {
    private final String family;
    public PneumaticRecipeAdapter(String family) { super(new ResourceLocation("pneumaticcraft", family)); this.family = family; }
    @Override protected boolean accepts(Recipe<?> recipe) {
        return switch (family) {
            case "pressure_chamber" -> recipe instanceof PressureChamberRecipe;
            case "assembly" -> recipe instanceof AssemblyRecipe;
            case "refinery" -> recipe instanceof RefineryRecipe;
            default -> false;
        };
    }
    private static Requirement stackedInput(Ingredient ingredient, int fallback) {
        var json = ingredient.toJson(); int count = fallback;
        if (ingredient instanceof me.desht.pneumaticcraft.api.crafting.ingredient.StackedIngredient && json.isJsonObject()) {
            var obj = json.getAsJsonObject().deepCopy(); count = obj.has("count") ? obj.get("count").getAsBigDecimal().intValueExact() : fallback;
            obj.remove("count"); obj.remove("type"); json = obj;
        }
        return itemInput(Ingredient.fromJson(json), count);
    }
    @Override protected Converted convert(Recipe<?> source, List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> modifiers) {
        var requirements = new ArrayList<Requirement>(); var fluids = new ArrayList<List<FluidStack>>();
        if (source instanceof PressureChamberRecipe recipe) {
            if (source.getClass() != PressureChamberRecipeImpl.class) throw new IllegalArgumentException("dynamic pressure chamber crafting");
            for (var input : recipe.getInputsForDisplay()) {
                requirements.add(stackedInput(input, 1));
            }
            for (var alternatives : recipe.getResultsForDisplay()) {
                if (alternatives.size() != 1) throw new IllegalArgumentException("dynamic pressure outputs");
                requirements.add(item(IOType.OUTPUT, alternatives.get(0)));
            }
            requirements.add(new RequirementCompressedAir(IOType.INPUT, recipe.getCraftingPressureForDisplay(), 0));
        } else if (source instanceof AssemblyRecipe recipe) {
            if (source.getClass() != AssemblyRecipeImpl.class) throw new IllegalArgumentException("dynamic assembly result");
            requirements.add(stackedInput(recipe.getInput(), recipe.getInputAmount())); requirements.add(item(IOType.OUTPUT, recipe.getOutput()));
        } else if (source instanceof RefineryRecipe recipe) {
            if (source.getClass() != RefineryRecipeImpl.class) throw new IllegalArgumentException("dynamic refinery result");
            fluids.add(fluids(recipe.getInput().getFluidStacks()));
            for (var output : recipe.getOutputs()) requirements.add(fluid(IOType.OUTPUT, output));
            var range = recipe.getOperatingTemp(); requirements.add(new RequirementTemperature(range.getMin(), range.getMax()));
        }
        return new Converted(100, 0, requirements, fluids);
    }
}
