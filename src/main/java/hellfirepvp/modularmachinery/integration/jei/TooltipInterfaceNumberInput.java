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

/**
 * The JEI lines for {@link RequirementInterfaceNumInput} — upstream's
 * {@code common/crafting/tooltip/TooltipInterfaceNumberInput.java} (69 lines), kept whole.
 *
 * <h2>The requirement has no JEI COMPONENT, which is why the text is the whole of its JEI presence</h2>
 *
 * <p>Upstream's {@code RequirementInterfaceNumInput.provideJEIComponent()} returns {@code null} (U108-U111),
 * so nothing is laid out in a slot for it. What a player sees for an interface requirement is exactly these
 * lines, which is why they are ported rather than dropped along with the slot they never had.</p>
 *
 * <h2>RULE A0 — THE TWO HALVES OF UPSTREAM'S CLASS ARE KEPT, ITS BASE CLASS IS NOT</h2>
 *
 * <p>Upstream extends {@code RequirementTip} and implements {@code filterRequirements} (U20-U26) and
 * {@code buildTooltip} (U28-U68); a registry ({@code RegistryRequirementTips} U44) collects the tips and the
 * JEI plugin iterates them. This port has neither the base class nor that registry — and it does not need
 * them: THERE IS EXACTLY ONE TIP. A one-entry registry plus an abstract base for one subclass would be a
 * second way to say "call this one method", which rule B forbids. What is kept is both upstream methods, with
 * their bodies intact: {@link #filterRequirements} selects the requirements, {@link #buildTooltip} turns them
 * into lines, and {@link #lines(MachineRecipe)} is the entry point the JEI category calls.</p>
 *
 * <p><b>AND THE READER IS THE CATEGORY, NOT A TOOLTIP CALLBACK:</b>
 * {@code MachineRecipeCategory.draw} already renders this recipe's energy rates and duration as panel text
 * (PLAN 298), and that is the same shape a value range needs. A rich-tooltip callback is attached to a SLOT,
 * and this requirement has no slot by upstream's own design — so panel text is the port's mechanism for
 * "a number that belongs to the recipe and not to an ingredient".</p>
 *
 * <h2>WHAT ONLY THE BODY REVEALED, AND IS KEPT</h2>
 *
 * <ul>
 *   <li><b>The custom-format path is defensive in three different ways</b> (U44-U58): a
 *       {@code jeiTooltipArgsCount} that is neither 1 nor 2 falls through to the built-in line with two
 *       warnings, and a format string whose placeholders do not match its arguments is caught as a
 *       {@code MissingFormatArgumentException} and ALSO falls through. Both are kept: a pack that writes a
 *       broken format gets its recipe listed with the default text rather than a crash inside JEI rendering.</li>
 *   <li><b>The equality case changes the TEXT, not just the numbers</b> (U60-U65): a single value produces
 *       one line ("value") while a range produces two ("min"/"max"). A port that always printed
 *       {@code min ~ max} would say "0 ~ 0" where upstream says "0".</li>
 *   <li><b>The three lang keys are the mod's own</b> ({@code tooltip.machinery.smartinterface.*}), which this
 *       port already ships in both {@code en_us.json} and {@code zh_cn.json} — read out of the lang files
 *       rather than retyped here.</li>
 * </ul>
 */
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
