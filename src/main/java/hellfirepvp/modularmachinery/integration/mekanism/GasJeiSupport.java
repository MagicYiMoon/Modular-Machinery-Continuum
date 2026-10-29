/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.mekanism;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.machine.IOType;
import mekanism.client.jei.MekanismJEI;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.network.chat.Component;

/** Client-only bridge kept outside the base JEI category so Mekanism remains optional. */
public final class GasJeiSupport {
    private GasJeiSupport() { }

    public static int count(MachineRecipe recipe, IOType direction) {
        int count = 0;
        for (var requirement : recipe.requirements()) {
            if (requirement instanceof RequirementGas gas && gas.getIOType() == direction && gas.isResolvable()) count++;
            if (requirement instanceof RequirementGasPerTick gas && gas.getIOType() == direction && gas.isResolvable()) count++;
        }
        return count;
    }

    public static void addSlots(IRecipeLayoutBuilder builder, MachineRecipe recipe,
                                int inputStart, int outputStart,
                                java.util.function.IntFunction<mezz.jei.api.gui.builder.IRecipeSlotBuilder> inputSlot,
                                java.util.function.IntFunction<mezz.jei.api.gui.builder.IRecipeSlotBuilder> outputSlot) {
        int input = inputStart;
        int output = outputStart;
        for (var requirement : recipe.requirements()) {
            if (!(requirement instanceof RequirementGas || requirement instanceof RequirementGasPerTick)) continue;
            boolean isInput = requirement.getIOType() == IOType.INPUT;
            mekanism.api.chemical.gas.GasStack stack;
            int amount;
            boolean guaranteed;
            float chance;
            String selector = requirement.getSelectorTag();
            if (requirement instanceof RequirementGas gas) {
                if (!gas.isResolvable()) continue;
                stack = gas.asStack(); amount = gas.getAmount(); guaranteed = gas.isGuaranteed(); chance = gas.getChance();
            } else {
                RequirementGasPerTick gas = (RequirementGasPerTick) requirement;
                if (!gas.isResolvable()) continue;
                stack = gas.asStack(); amount = gas.getAmount(); guaranteed = true; chance = 1F;
            }
            int index = isInput ? input++ : output++;
            (isInput ? inputSlot : outputSlot).apply(index)
                    .addIngredient(MekanismJEI.TYPE_GAS, stack)
                    .addRichTooltipCallback((view, tooltip) -> {
                        tooltip.add(Component.literal(amount + " mB" +
                                (requirement instanceof RequirementGasPerTick ? "/tick" : "")));
                        if (selector != null) tooltip.add(Component.translatable(
                                "jei.modularmachinery.selector", selector));
                        if (!guaranteed) tooltip.add(Component.literal(
                                Math.round(chance * 100F) + "% chance"));
                    });
        }
    }
}
