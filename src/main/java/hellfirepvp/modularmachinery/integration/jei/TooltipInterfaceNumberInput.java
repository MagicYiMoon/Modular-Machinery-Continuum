/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.integration.jei;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementInterfaceNumInput;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.util.SmartInterfaceType;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.MissingFormatArgumentException;










































 
public final class TooltipInterfaceNumberInput {

    private TooltipInterfaceNumberInput() {
    }

    /**
     * The lines this recipe's interface requirements produce, in declaration order.
     *
     * <p>The entry point the JEI category calls. Returns an empty list for a recipe with no interface
     * requirement, which is every shipped recipe today — so this costs them one empty pass.</p>
     */
    public static List<String> lines(MachineRecipe recipe) {
        return buildTooltip(recipe, filterRequirements(recipe));
    }

    /**
     * Upstream U20-U26: keep only the interface requirements.
     *
     * <p>Upstream's signature takes the recipe AND the requirement collection, because its
     * {@code RequirementTip} base is handed both by the plugin. With one tip there is nothing to hand in
     * here, so the collection comes from the recipe — the same list, read at the same moment.</p>
     */
    static List<RequirementInterfaceNumInput> filterRequirements(MachineRecipe recipe) {
        List<RequirementInterfaceNumInput> filtered = new ArrayList<>();
        for (var requirement : recipe.requirements()) {
            if (requirement instanceof RequirementInterfaceNumInput interfaceRequirement) {
                filtered.add(interfaceRequirement);
            }
        }
        return filtered;
    }

    /**
     * Upstream U28-U68, line for line: a custom format when the declared type has one and it fits, otherwise
     * the built-in single-value or min/max lines.
     */
    static List<String> buildTooltip(MachineRecipe recipe, List<RequirementInterfaceNumInput> filtered) {
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        // The descriptor lives on the MACHINE, so the recipe's own machine is resolved here — exactly where
        // upstream had it resolved at load time (its requirement held the SmartInterfaceType from the
        // MachineRegistry). A name the machine does not declare yields null and falls through to the
        // built-in line, which is also what the requirement itself reports at runtime.
        DynamicMachine machine = MachineReloadListener.getLoader().findByName(recipe.machine());

        ArrayList<String> tooltip = new ArrayList<>();
        for (RequirementInterfaceNumInput requirement : filtered) {
            float minValue = requirement.getMinValue();
            float maxValue = requirement.getMaxValue();

            SmartInterfaceType type = requirement.resolveType(machine);
            String jeiTooltip = type == null ? "" : type.getJeiTooltip();
            if (!jeiTooltip.isEmpty()) {
                try {
                    if (type.getJeiTooltipArgsCount() == 1) {
                        tooltip.add(String.format(jeiTooltip, minValue));
                        continue;
                    } else if (type.getJeiTooltipArgsCount() == 2) {
                        tooltip.add(String.format(jeiTooltip, minValue, maxValue));
                        continue;
                    }
                    Modularmachinery.LOG.warn("Invalid JEITooltipArgsCount, Using default format...");
                    Modularmachinery.LOG.warn("Type: {}, TooltipMessage: {}", type.getType(), jeiTooltip);
                } catch (MissingFormatArgumentException ex) {
                    Modularmachinery.LOG.warn("Caught MissingFormatArgumentException! Using default format...");
                    Modularmachinery.LOG.warn("Type: {}, TooltipMessage: {}", type.getType(), jeiTooltip);
                }
            }

            if (minValue == maxValue) {
                tooltip.add(Component.translatable("tooltip.machinery.smartinterface.value", minValue).getString());
            } else {
                tooltip.add(Component.translatable("tooltip.machinery.smartinterface.minvalue", minValue)
                        .getString());
                tooltip.add(Component.translatable("tooltip.machinery.smartinterface.maxvalue", maxValue)
                        .getString());
            }
        }
        return tooltip;
    }
}
