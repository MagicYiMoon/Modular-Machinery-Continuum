/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.extrautilities;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.*;
import java.util.List;

/** Source rainbow condition, rechecked before any per-tick resource payment. */
public final class RequirementRainbow extends ConditionRequirement implements TickBatchRequirement {
    @Override public String typeId() { return "modularmachinery:rainbow"; }
    @Override public String describe() { return "Adjacent running rainbow generator (Extra Utilities Rebirth)"; }
    @Override public String describeFailure(MachineContext context) { return "error.modularmachinery.requirement.rainbow.less"; }
    @Override public boolean canStart(MachineContext context) {
        for (var at : context.forSelector(getSelectorTag()).getComponentPositions())
            if (context.getLevel().hasChunkAt(at) && context.getLevel().getBlockEntity(at) instanceof TileRainbowProvider tile && tile.isRunning()) return true;
        return false;
    }
    @Override public void writeJson(JsonObject json) { }
    @Override public boolean isBatchPerTick() { return true; }
    @Override public TickPlan planTick(MachineContext context, List<Requirement> siblings, int copies) {
        String failure = siblings.stream().filter(r -> r instanceof RequirementRainbow)
                .filter(r -> !r.canStart(context)).findFirst().map(r -> r.describeFailure(context)).orElse(null);
        return new TickPlan() {
            public String failure() { return failure; }
            public boolean valid() { return failure == null; }
            public boolean execute() { return valid() && siblings.stream().filter(r -> r instanceof RequirementRainbow).allMatch(r -> r.canStart(context)); }
        };
    }
    @Override public String doIOTick(MachineContext context, int copies) { return canStart(context) ? null : describeFailure(context); }
    public static final class Type implements RequirementType {
        @Override public String requiresModid() { return "extrautilitiesrebirth"; }
        @Override public Requirement createRequirement(IOType io, JsonObject json) {
            if (io != IOType.INPUT) throw new JsonParseException("rainbow is an input condition, not an energy output");
            if (json.has("amount") || json.has("power")) throw new JsonParseException("rainbow is a condition without amount/power");
            return new RequirementRainbow();
        }
    }
}
