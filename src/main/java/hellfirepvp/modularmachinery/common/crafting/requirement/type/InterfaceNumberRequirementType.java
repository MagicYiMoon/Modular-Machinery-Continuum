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
