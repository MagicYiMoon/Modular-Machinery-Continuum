/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.embers;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** The old extension's one-shot input channel, with shared reservation and double precision. */
public final class RequirementEmber extends SelectorRequirement implements Requirement.Parallelizable, BatchIORequirement {
    private final double ember;
    private int parallelism = 1;
    private boolean unaffected;
    public RequirementEmber(double ember) {
        if (!Double.isFinite(ember) || ember < 0) throw new IllegalArgumentException("ember must be finite and non-negative");
        this.ember = ember;
    }
    public double amount() { return ember; }
    public Component tooltip() { return Component.translatable("jei.modularmachinery.ember", ember); }
    @Override public IOType getIOType() { return IOType.INPUT; }
    @Override public String typeId() { return "modularmachinery:ember"; }
    @Override public String describe() { return "input " + ember + " Ember"; }
    @Override public String describeFailure(MachineContext context) { return "error.modularmachinery.requirement.ember"; }
    @Override public boolean canStart(MachineContext context) { return plan(context, List.of(this), 1).valid(); }
    // This API checks completeness before payment; completion itself only rechecks outputs.
    @Override public boolean canFinish(MachineContext context) { return canStart(context); }
    @Override public boolean consume(MachineContext context, int copies) { return plan(context, List.of(this), copies).execute(); }
    @Override public int produce(MachineContext context, int copies) { return 0; }
    @Override public Plan planInputs(MachineContext context, List<Requirement> all, int copies) { return plan(context, all, copies); }
    @Override public String outputFailure(MachineContext context, List<Requirement> all, int copies) { return null; }
    @Override public int getParallelism() { return parallelism; }
    @Override public void setParallelism(int value) { if (!unaffected) parallelism = Math.max(1, value); }
    @Override public boolean isParallelizeUnaffected() { return unaffected; }
    @Override public void setParallelizeUnaffected(boolean value) { unaffected = value; if (value) parallelism = 1; }
    @Override public int getMaxParallelism(MachineContext context, int max, List<Requirement> siblings) {
        var all = new ArrayList<>(siblings == null ? List.<Requirement>of() : siblings); if (!all.contains(this)) all.add(this);
        int low = 0, high = Math.max(0, max);
        while (low < high) {
            int mid = low + (int) (((long) high - low + 1) / 2);
            if (plan(context, all, mid).valid()) low = mid; else high = mid - 1;
        }
        return low;
    }
    @Override public Requirement scaled(float value, int operation, IOType io, boolean chance) {
        if (chance || io != null && io != IOType.INPUT) return this;
        double changed = operation == Requirement.OPERATION_MULTIPLY ? ember * value : ember + value;
        if (!Double.isFinite(changed)) throw new IllegalArgumentException("Ember modifier overflow");
        return copySelectorTo(new RequirementEmber(Math.max(0, changed)));
    }
    @Override public void writeJson(JsonObject json) { json.addProperty("ember", ember); json.addProperty("parallelizeUnaffected", unaffected); }
    public static EmberPlan plan(MachineContext context, List<Requirement> all, int copies) {
        var reserved = new LinkedHashMap<TileEmberHatch, Double>(); var original = new LinkedHashMap<TileEmberHatch, Double>();
        var server = context.getLevel();
        if (server == null || !server.getServer().isSameThread()) return new EmberPlan(false, reserved, original);
        for (Requirement raw : all) {
            if (!(raw instanceof RequirementEmber r)) continue;
            double remaining = r.ember * (r.unaffected ? 1 : Math.max(1, copies));
            if (!Double.isFinite(remaining)) return new EmberPlan(false, reserved, original);
            if (remaining == 0) continue;
            for (var pos : new LinkedHashSet<>(context.forSelector(r.getSelectorTag()).getComponentPositions())) {
                if (!server.hasChunkAt(pos) || !(server.getBlockEntity(pos) instanceof TileEmberHatch tile) || !tile.live()) continue;
                double prior = reserved.getOrDefault(tile, 0D);
                double take = Math.min(remaining, Math.max(0, tile.amount() - prior));
                if (take > 0) { original.putIfAbsent(tile, tile.amount()); reserved.put(tile, prior + take); remaining -= take; }
                if (remaining <= 0) break;
            }
            if (remaining > 0) return new EmberPlan(false, reserved, original);
        }
        return new EmberPlan(true, reserved, original);
    }
    public record EmberPlan(boolean valid, Map<TileEmberHatch, Double> reserved, Map<TileEmberHatch, Double> original) implements Plan {
        @Override public boolean execute() {
            if (!valid) return false;
            for (var e : reserved.entrySet())
                if (!e.getKey().live() || e.getKey().amount() != original.get(e.getKey()) || e.getValue() > e.getKey().amount()) return false;
            reserved.forEach((tile, amount) -> tile.take(amount, false)); return true;
        }
    }
    public static final class Type implements RequirementType {
        @Override public String requiresModid() { return "embers"; }
        @Override public Requirement createRequirement(IOType io, JsonObject json) {
            if (io != IOType.INPUT) throw new JsonParseException("ember supports input only; no Ember output hatch exists");
            if (!json.has("ember") || !json.get("ember").isJsonPrimitive() || !json.getAsJsonPrimitive("ember").isNumber())
                throw new JsonParseException("ember requires a numeric ember amount");
            if (json.has("perTick") && (!json.get("perTick").isJsonPrimitive() || !json.getAsJsonPrimitive("perTick").isBoolean() || json.get("perTick").getAsBoolean()))
                throw new JsonParseException("ember is a one-shot input, not perTick");
            double amount = json.get("ember").getAsDouble();
            if (!Double.isFinite(amount) || amount < 0) throw new JsonParseException("ember must be finite and non-negative");
            var result = new RequirementEmber(amount);
            if (json.has("parallelizeUnaffected")) {
                if (!json.get("parallelizeUnaffected").isJsonPrimitive() || !json.getAsJsonPrimitive("parallelizeUnaffected").isBoolean())
                    throw new JsonParseException("parallelizeUnaffected must be boolean");
                result.setParallelizeUnaffected(json.get("parallelizeUnaffected").getAsBoolean());
            }
            return result;
        }
    }
}
