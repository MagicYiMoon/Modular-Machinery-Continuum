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

/**
 * {@code modularmachinery:energy} -- a per-tick power cost or gain.
 *
 * <p>This is the body of one branch of {@code RecipeLoader}'s former {@code switch}, moved here by PLAN section 380 with
 * its reasoning intact:
 *
 * <pre>
 * case TYPE_ENERGY -&gt; {
 *     if (!requirement.has("energyPerTick") || !requirement.get("energyPerTick").isJsonPrimitive()) {
 *         problems.add(path + ": energy requirement has no energyPerTick");
 *         yield null;
 *     }
 *     yield new RequirementEnergy(ioType, requirement.get("energyPerTick").getAsInt());
 * }
 * </pre>
 *
 * <h2>Why {@code energyPerTick} is REQUIRED, and it is not about strictness</h2>
 *
 * <p>Upstream's {@code RequirementEnergy} takes the value as a MANDATORY constructor argument. The port read it with a 0
 * default -- and 0 for energy is not "unset", it is a requirement that costs nothing: <b>a machine that runs on free power,
 * with nothing in the log to say so.</b> That is why the branch refuses rather than defaults, and it is the same class of
 * mistake §311 found in {@code io-type} and §313 recorded here.
 */
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
