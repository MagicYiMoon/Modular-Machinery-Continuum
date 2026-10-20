/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.IOType;

public final class FluidPerTickRequirementType implements RequirementType {
    public static final String NAME = "fluid_pertick";
    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        if (json.has("chance") && json.get("chance").getAsFloat() != 1F) {
            throw new JsonParseException("fluid_pertick is a continuous rate and does not support chance");
        }
        return new RequirementFluidPerTick((RequirementFluid) new FluidRequirementType().createRequirement(io, json));
    }
}
