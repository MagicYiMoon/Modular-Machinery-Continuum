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
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementInterfaceNumInput;
import hellfirepvp.modularmachinery.common.machine.IOType;

/**
 * {@code modularmachinery:interface_number_input} — the registry entry for
 * {@link RequirementInterfaceNumInput}.
 *
 * <p>Upstream's class is {@code RequirementTypeInterfaceNumInput} (12 lines) and its whole body is:</p>
 *
 * <pre>
 * public RequirementInterfaceNumInput createRequirement(IOType type, JsonObject jsonObject) {
 *     return null;
 * }
 * </pre>
 *
 * <p><b>RULE A0 — THE BODY IS THE ONE THING THAT COULD NOT BE PORTED, AND RETURNING NULL HERE WOULD BE
 * PORTING IT AND BREAKING THE FEATURE.</b> In this port a null requirement ABORTS the whole recipe (PLAN 313,
 * and {@code RequirementType}'s own javadoc states the contract), so an upstream-faithful null body would make
 * every file that uses the type unloadable — i.e. the requirement would have a shape and no possible producer
 * from JSON. Upstream got away with it because its ONLY producer was CraftTweaker:
 * {@code RecipePrimer.addSmartInterfaceDataInput} (U266-U281) constructs the requirement directly and there is
 * no JSON route at all (the type is registered at {@code RegistryRequirementTypes} U72 purely so a script could
 * name it). THIS PORT HAS NO SCRIPT RECIPE BUILDER — {@code CraftTweakerIntegration}'s own javadoc records the
 * ZenCode builder as not implemented, because this port has no script-defined recipe list for it to write into —
 * so the JSON route IS the port's producer route, exactly as §592-§598 concluded for the parallelism keys and
 * §662 for factory capacity. The method below is therefore this port's port of upstream's
 * {@code RecipePrimer.addSmartInterfaceDataInput} argument list, translated into the JSON vocabulary the other
 * three types already use.</p>
 *
 * <h2>The keys, and where each comes from</h2>
 *
 * <pre>
 * interface   required  the DECLARED type's name    upstream: addSmartInterfaceDataInput's `typeStr` (U266)
 * min         required  the lower bound             upstream: `minValue`                          (U266)
 * max         optional  the upper bound; absent means max == min, which is upstream's second overload
 *                                                     addSmartInterfaceDataInput(typeStr, value)   (U284-U286)
 * io-type     required  must be "input": upstream's constructors hardcode IOType.INPUT
 * </pre>
 *
 * <p><b>WHY "interface" AND NOT "type":</b> every requirement object already carries its own {@code "type"}
 * key — the REQUIREMENT type, {@code modularmachinery:interface_number_input} — so a second {@code "type"} key
 * inside the same object would be a different thing wearing the same name. The name of the DECLARED type is
 * what this key carries, and upstream's own field for it is {@code SmartInterfaceType#getType()}.</p>
 *
 * <p><b>A MALFORMED ENTRY REJECTS THE RECIPE</b>, which is this loader's rule for every requirement
 * ({@code RequirementType#createRequirement}'s javadoc: null or a throw means "reject the whole recipe" rather
 * than "skip this requirement" — skipping would run the machine without the condition the pack asked for).</p>
 */
public final class InterfaceNumberRequirementType implements RequirementType {

    /**
     * Registered under upstream's own key: {@code RequirementTypesMM.KEY_INTERFACE_NUMBER_INPUT} is
     * {@code new ResourceLocation(ModularMachinery.MODID, "interface_number_input")}, so a pack that already
     * writes that id in a script needs no new spelling here.
     */
    public static final String NAME = "interface_number_input";

    @Override
    public Requirement createRequirement(IOType type, JsonObject json) {
        // Upstream has no output form of this requirement: all three of its constructors pass IOType.INPUT
        // (U29/U36/U43). The loader makes io-type mandatory for every requirement (PLAN 311), so a file that
        // says "output" is refused with a message rather than silently given a direction the engine would then
        // treat as a product.
        if (type != IOType.INPUT) {
            throw new RequirementParseException("interface_number_input requirement must be io-type 'input'"
                    + " (upstream's constructors hardcode IOType.INPUT, and reading a published value"
                    + " produces nothing)");
        }

        String interfaceType = json.has("interface") && json.get("interface").isJsonPrimitive()
                ? json.get("interface").getAsString()
                : "";
        if (interfaceType.isEmpty()) {
            throw new RequirementParseException("interface_number_input requirement has no 'interface'"
                    + " (the name of a smart interface type the machine declares)");
        }

        if (!json.has("min") || !json.get("min").isJsonPrimitive()) {
            throw new RequirementParseException("interface_number_input requirement has no 'min'");
        }
        float min = json.get("min").getAsFloat();
        // No 'max' means the range collapses to min, which is upstream's single-value constructor
        // (RequirementInterfaceNumInput U42-U47) rather than a default this port invented.
        float max = json.has("max") && json.get("max").isJsonPrimitive()
                ? json.get("max").getAsFloat()
                : min;

        return new RequirementInterfaceNumInput(interfaceType, min, max);
    }
}
