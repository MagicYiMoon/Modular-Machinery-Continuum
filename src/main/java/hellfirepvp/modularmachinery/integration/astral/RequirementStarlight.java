/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.astral;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.*;
import java.util.*;

/** Upstream starlight is intensity, not a consumable. It is not multiplied by batch count. */
public final class RequirementStarlight extends SelectorRequirement implements BatchIORequirement, TickBatchRequirement {
    private final IOType io;
    private final float amount;
    public RequirementStarlight(IOType io, float amount) {
        if (io == null || !Float.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Invalid starlight amount/direction");
        this.io = io; this.amount = amount;
    }
    public float amount() { return amount; }
    @Override public IOType getIOType() { return io; }
    @Override public String typeId() { return "modularmachinery:starlight"; }
    @Override public String describe() { return io.name().toLowerCase(Locale.ROOT) + " " + amount + " starlight intensity"; }
    @Override public String describeFailure(MachineContext context) { return "error.modularmachinery.requirement.starlight." + (io == IOType.INPUT ? "less" : "missingoutput"); }
    private TileStarlightProvider find(MachineContext context) {
        for (var pos : context.forSelector(getSelectorTag()).getComponentPositions()) {
            if (!context.getLevel().hasChunkAt(pos)) continue;
            if (context.getLevel().getBlockEntity(pos) instanceof TileStarlightProvider tile && tile.getIOType() == io
                    && (io == IOType.OUTPUT || tile instanceof TileStarlightProvider.Input input && input.stored() >= amount)) return tile;
        }
        return null;
    }
    @Override public boolean canStart(MachineContext context) { return find(context) != null; }
    @Override public boolean canFinish(MachineContext context) { return true; }
    @Override public boolean canEmit(MachineContext context, int copies) { return true; }
    @Override public boolean consume(MachineContext context, int copies) { return true; }
    @Override public int produce(MachineContext context, int copies) { return 0; }
    @Override public Plan planInputs(MachineContext context, List<Requirement> all, int copies) {
        return new Plan() { public boolean valid() { return true; } public boolean execute() { return true; } };
    }
    @Override public String outputFailure(MachineContext context, List<Requirement> all, int copies) { return null; }
    @Override public void writeJson(JsonObject json) { json.addProperty("amount", amount); }
    @Override public boolean isBatchPerTick() { return true; }
    @Override public TickPlan planTick(MachineContext context, List<Requirement> all, int copies) {
        Map<TileStarlightProvider.Output, Double> outputs = new LinkedHashMap<>();
        String failure = null;
        for (Requirement raw : all) {
            if (!(raw instanceof RequirementStarlight r)) continue;
            var tile = r.find(context);
            if (tile == null) { failure = r.describeFailure(context); break; }
            if (tile instanceof TileStarlightProvider.Output output) outputs.merge(output, r.amount / 4000D, Double::sum);
        }
        final String result = failure;
        return new TickPlan() {
            public String failure() { return result; }
            public boolean valid() { return result == null; }
            public boolean execute() {
                if (!valid() || all.stream().filter(r -> r instanceof RequirementStarlight).anyMatch(r -> !r.canStart(context))) return false;
                outputs.forEach(TileStarlightProvider.Output::offer); return true;
            }
        };
    }
    @Override public String doIOTick(MachineContext context, int copies) { var p = planTick(context, List.of(this), copies); return p.execute() ? null : describeFailure(context); }
    @Override public Requirement scaled(float value, int operation, IOType direction, boolean chance) {
        if (chance || direction != null && direction != io) return this;
        float next = Requirement.apply(amount, value, operation);
        if (!Float.isFinite(next)) next = next > 0 ? Float.MAX_VALUE : 0;
        return copySelectorTo(new RequirementStarlight(io, Math.max(0, next)));
    }
    public static final class Type implements RequirementType {
        @Override public String requiresModid() { return "astralsorcery"; }
        @Override public Requirement createRequirement(IOType io, JsonObject json) {
            if (!json.has("amount") || !json.get("amount").isJsonPrimitive() || !json.getAsJsonPrimitive("amount").isNumber()) throw new JsonParseException("starlight requires a finite non-negative numeric amount");
            float amount = json.get("amount").getAsFloat();
            if (!Float.isFinite(amount) || amount < 0) throw new JsonParseException("Invalid starlight amount");
            return new RequirementStarlight(io, amount);
        }
    }
}
