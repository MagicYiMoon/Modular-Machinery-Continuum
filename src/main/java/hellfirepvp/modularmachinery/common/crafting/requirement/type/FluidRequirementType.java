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
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid;
import hellfirepvp.modularmachinery.common.machine.IOType;
































 
public final class FluidRequirementType implements RequirementType {

    public static final String NAME = "fluid";

    @Override
    public Requirement createRequirement(IOType type, JsonObject json) {
        return create(type, json, "");
    }

    /**
     * The loader's path is needed for the messages, and the interface does not carry one.
     *
     * <p>Rather than widen {@link RequirementType}'s contract for a string that only the messages use, the loader calls
     * this overload when it has a path in hand. The interface method above stays as the general contract, which is what an
     * addon implements.
     *
     * @param path the recipe's path, prefixed onto every message this method produces
     */
    public Requirement create(IOType type, JsonObject json, String path) {
        String raw = readName(json);
        if (raw == null) {
            throw new RequirementParseException(path + ": fluid requirement has no 'fluid' key");
        }
        return new RequirementFluid(type, raw, RequirementKeys.requiredFluidAmount(path, json),
                RequirementKeys.chanceOf(json))
                .withNbt(hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.read(json));
    }

    /** @return the fluid id, or null when the key is absent, empty, or not a primitive */
    private static String readName(JsonObject json) {
        if (!json.has("fluid") || !json.get("fluid").isJsonPrimitive()) {
            return null;
        }
        String raw = json.get("fluid").getAsString();
        return (raw == null || raw.isEmpty()) ? null : raw;
    }
}
