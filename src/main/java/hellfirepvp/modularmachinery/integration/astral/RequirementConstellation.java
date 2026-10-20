/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.astral;

import com.google.gson.*;
import hellfirepvp.astralsorcery.common.constellation.ConstellationRegistry;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.resources.ResourceLocation;

/** Source constellation requirement: a non-consuming start condition. */
public final class RequirementConstellation extends ConditionRequirement {
    private final ResourceLocation constellation;
    public RequirementConstellation(ResourceLocation constellation) { this.constellation = constellation; }
    public ResourceLocation constellation() { return constellation; }
    @Override public String typeId() { return "modularmachinery:constellation"; }
    @Override public String describe() { return "Active constellation " + constellation; }
    @Override public String describeFailure(MachineContext context) { return "error.modularmachinery.requirement.constellation.less"; }
    @Override public boolean canStart(MachineContext context) {
        for (var pos : context.forSelector(getSelectorTag()).getComponentPositions())
            if (context.getLevel().hasChunkAt(pos) && context.getLevel().getBlockEntity(pos) instanceof TileConstellationProvider tile && tile.isActive(constellation)) return true;
        return false;
    }
    @Override public void writeJson(JsonObject json) { json.addProperty("constellation", constellation.toString()); }
    public static final class Type implements RequirementType {
        @Override public String requiresModid() { return "astralsorcery"; }
        @Override public Requirement createRequirement(IOType io, JsonObject json) {
            if (io != IOType.INPUT) throw new JsonParseException("constellation supports input conditions only");
            if (!json.has("constellation") || !json.get("constellation").isJsonPrimitive() || !json.getAsJsonPrimitive("constellation").isString()) throw new JsonParseException("constellation requires a registry name");
            String name = json.get("constellation").getAsString();
            // Old packs use names such as astralsorcery.constellation.discidia.
            if (name.startsWith("astralsorcery.constellation.")) name = name.substring("astralsorcery.constellation.".length());
            ResourceLocation id = ResourceLocation.tryParse(name.contains(":") ? name : "astralsorcery:" + name);
            if (id == null || ConstellationRegistry.getConstellation(id) == null) throw new JsonParseException("Unknown constellation: " + name);
            return new RequirementConstellation(id);
        }
    }
}
