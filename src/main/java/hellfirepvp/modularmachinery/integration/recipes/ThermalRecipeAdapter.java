/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.recipes;

import cofh.thermal.core.util.recipes.machine.InsolatorRecipe;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.adapter.ForeignRecipeAdapter;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.fluids.FluidStack;
import java.util.*;

public final class ThermalRecipeAdapter extends ForeignRecipeAdapter {
    public ThermalRecipeAdapter() { super(new ResourceLocation("thermal", "insolator")); }
    // Split expected outputs only AFTER chance modifiers. Scaling each split separately changes yield.
    @Override protected Requirement nativeWithModifiers(Requirement requirement, List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> modifiers) {
        if (requirement instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem && requirement.getIOType() == IOType.OUTPUT) return requirement;
        return super.nativeWithModifiers(requirement, modifiers);
    }
    public static List<Requirement> scaleOutput(net.minecraft.world.item.ItemStack stack, float nativeChance,
            List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> modifiers) {
        float chance = nativeChance == -1 ? 1 : nativeChance;
        float amount = stack.getCount();
        for (var modifier : modifiers) {
            if (!"modularmachinery:item".equals(modifier.getTarget()) || modifier.getIOTarget() != IOType.OUTPUT) continue;
            if (modifier.isChance()) chance = Requirement.apply(chance, modifier.getModifier(), modifier.getOperation());
            else amount = Requirement.apply(amount, modifier.getModifier(), modifier.getOperation());
        }
        if (!Float.isFinite(chance) || !Float.isFinite(amount) || chance > Integer.MAX_VALUE / (double) Math.max(1, Math.round(amount)))
            throw new IllegalArgumentException("invalid output chance/amount");
        var result = new ArrayList<Requirement>(); int count = Math.max(0, Math.round(amount));
        if (chance <= 0 || count == 0) return result;
        int guaranteed = (int) Math.floor(chance);
        if (guaranteed > 0) result.add(item(IOType.OUTPUT, stack.copyWithCount(count * guaranteed)));
        if (chance > guaranteed) result.add(item(IOType.OUTPUT, stack.copyWithCount(count)).scaled(chance - guaranteed, 1, IOType.OUTPUT, true));
        return result;
    }
    @Override protected boolean accepts(Recipe<?> recipe) { return recipe instanceof InsolatorRecipe; }
    @Override protected Converted convert(Recipe<?> source, List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> modifiers) {
        if (source.getClass() != InsolatorRecipe.class) throw new IllegalArgumentException("custom insolator semantics");
        var recipe = (InsolatorRecipe) source;
        var requirements = new ArrayList<Requirement>(); var inputs = new ArrayList<List<FluidStack>>();
        for (var input : recipe.getInputItems()) requirements.add(itemInput(input, 1));
        for (var input : recipe.getInputFluids()) inputs.add(fluids(Arrays.asList(input.getFluids())));
        for (int i = 0; i < recipe.getOutputItems().size(); i++) {
            var stack = recipe.getOutputItems().get(i);
            var output = scaleOutput(stack, recipe.getOutputItemChances().get(i), modifiers);
            requirements.addAll(output);
        }
        for (var output : recipe.getOutputFluids()) requirements.add(fluid(IOType.OUTPUT, output));
        return new Converted(Math.max(1, (int) Math.ceil(recipe.getEnergy() / 20.0)), 20, requirements, inputs);
    }
}
