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
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import hellfirepvp.modularmachinery.common.machine.IOType;























 
public final class EnergyRequirementType implements RequirementType {

    public static final String NAME = "energy";

    @Override
    public Requirement createRequirement(IOType type, JsonObject json) {
        return create(type, json, "");
    }

    /**
     * The loader's path is needed for the message; the interface does not carry one.
     *
     * @param path the recipe's path, prefixed onto the thrown message
     */
    public Requirement create(IOType type, JsonObject json, String path) {
        int perTick = RequirementKeys.requiredInt(path, json, "energyPerTick", NAME);
        return new RequirementEnergy(type, perTick);
    }
}
