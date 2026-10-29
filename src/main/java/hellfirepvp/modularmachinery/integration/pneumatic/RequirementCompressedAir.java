/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.pneumatic;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.network.chat.Component;
import java.util.*;

/** Pressure is a condition; only mL of air is multiplied by batch/resource modifiers. */
public class RequirementCompressedAir extends SelectorRequirement implements Requirement.Parallelizable, BatchIORequirement {
    private final IOType io;
    private final float pressure;
    private final int amount;
    private boolean unaffected;
    private int parallelism = 1;
    public RequirementCompressedAir(IOType io, float pressure, int amount) { this.io = io; this.pressure = pressure; this.amount = amount; }
    public float pressure() { return pressure; }
    public int amount() { return amount; }
    public boolean rate() { return false; }
    @Override public String typeId() { return "modularmachinery:compressed_air"; }
    @Override public IOType getIOType() { return io; }
    @Override public String describe() { return io.name().toLowerCase(Locale.ROOT) + " " + amount + " mL air" + (rate() ? "/tick" : "") + "; " + pressure + " bar"; }
    public Component tooltip() { return Component.translatable("jei.modularmachinery.pressure", io.name().toLowerCase(Locale.ROOT), amount, rate() ? "/t" : "", pressure); }
    @Override public String describeFailure(MachineContext ctx) { return io == IOType.INPUT ? "error.modularmachinery.requirement.pressure.input" : "error.modularmachinery.requirement.pressure.output"; }
    @Override public boolean canStart(MachineContext ctx) { return io == IOType.OUTPUT && isIgnoreOutputCheck() || plan(ctx, List.of(this), 1, io, rate()).valid(); }
    @Override public boolean canFinish(MachineContext ctx) { return rate() || canStart(ctx); }
    @Override public boolean canEmit(MachineContext ctx, int copies) { return rate() || io != IOType.OUTPUT || isIgnoreOutputCheck() || plan(ctx, List.of(this), copies, io, false).valid(); }
    @Override public boolean consume(MachineContext ctx, int copies) { return rate() || plan(ctx, List.of(this), copies, IOType.INPUT, false).execute(); }
    @Override public int produce(MachineContext ctx, int copies) {
        if (rate() || io != IOType.OUTPUT) return 0;
        var p = plan(ctx, List.of(this), copies, io, false);
        return p.execute() ? (int) Math.min(Integer.MAX_VALUE, p.reserved.values().stream().mapToLong(Long::longValue).sum()) : 0;
    }
    @Override public Plan planInputs(MachineContext ctx, List<Requirement> all, int copies) { return plan(ctx, all, copies, IOType.INPUT, false); }
    @Override public String outputFailure(MachineContext ctx, List<Requirement> all, int copies) { return plan(ctx, all, copies, IOType.OUTPUT, false).failure(); }
    @Override public int getParallelism() { return parallelism; }
    @Override public void setParallelism(int value) { if (!unaffected) parallelism = Math.max(1, value); }
    @Override public boolean isParallelizeUnaffected() { return unaffected; }
    @Override public void setParallelizeUnaffected(boolean value) { unaffected = value; if (value) parallelism = 1; }
    @Override public int getMaxParallelism(MachineContext ctx, int max, List<Requirement> siblings) {
        var all = new ArrayList<>(siblings == null ? List.<Requirement>of() : siblings); if (!all.contains(this)) all.add(this);
        int lo = 0, hi = Math.max(0, max);
        while (lo < hi) { int mid = lo + (int) (((long) hi - lo + 1) / 2); if (plan(ctx, all, mid, io, rate()).valid()) lo = mid; else hi = mid - 1; }
        return lo;
    }
    @Override public void writeJson(JsonObject json) { json.addProperty("pressure", pressure); json.addProperty("air", amount); json.addProperty("perTick", rate()); json.addProperty("parallelizeUnaffected", unaffected); }
    @Override public Requirement scaled(float value, int operation, IOType direction, boolean chance) {
        if (chance || direction != null && direction != io) return this;
        int count = Math.max(0, Math.round(Requirement.apply(amount, value, operation)));
        var copy = rate() ? new Rate(io, pressure, count) : new RequirementCompressedAir(io, pressure, count);
        copy.setParallelizeUnaffected(unaffected); copy.setParallelism(parallelism); return copySelectorTo(copy);
    }
    public static AirPlan plan(MachineContext ctx, List<Requirement> all, int copies, IOType io, boolean rate) {
        var reserved = new LinkedHashMap<TilePressureHatch, Long>(); var original = new LinkedHashMap<TilePressureHatch, Integer>();
        var server = ctx.getLevel();
        if (server == null || !server.getServer().isSameThread())
            return new AirPlan("error.modularmachinery.requirement.pressure.input", io, reserved, original);
        var ordered = io == IOType.OUTPUT ? all.stream().sorted(Comparator.comparing(Requirement::isIgnoreOutputCheck)).toList() : all;
        for (var raw : ordered) {
            if (!(raw instanceof RequirementCompressedAir r) || r.io != io || r.rate() != rate) continue;
            long remaining = (long) r.amount * (r.unaffected ? 1 : Math.max(1, copies)); boolean matched = false;
            for (var pos : new LinkedHashSet<>(ctx.forSelector(r.getSelectorTag()).getComponentPositions())) {
                if (!server.hasChunkAt(pos)) continue;
                if (!(ctx.getLevel().getBlockEntity(pos) instanceof TilePressureHatch tile) || tile.getIOType() != io || !tile.live()
                        || io == IOType.INPUT && !tile.meetsPressure(r.pressure)) continue;
                long available = io == IOType.INPUT ? tile.take(Long.MAX_VALUE, r.pressure, true) : tile.add(Long.MAX_VALUE, true);
                long count = Math.min(remaining, Math.max(0, available - reserved.getOrDefault(tile, 0L)));
                matched = true; original.putIfAbsent(tile, tile.air().getAir());
                reserved.merge(tile, count, Long::sum); remaining -= count;
                if (remaining == 0) break;
            }
            if ((remaining > 0 || !matched) && !(io == IOType.OUTPUT && r.isIgnoreOutputCheck()))
                return new AirPlan(r.describeFailure(ctx), io, reserved, original);
        }
        return new AirPlan(null, io, reserved, original);
    }
    public record AirPlan(String failure, IOType direction, Map<TilePressureHatch, Long> reserved,
                          Map<TilePressureHatch, Integer> original) implements TickBatchRequirement.TickPlan {
        @Override public boolean valid() { return failure == null; }
        @Override public boolean execute() {
            if (!valid()) return false;
            int sign = direction == IOType.INPUT ? -1 : 1;
            for (var entry : reserved.entrySet()) if (entry.getKey().air().getAir() != original.get(entry.getKey())
                    || !entry.getKey().canChange(sign * entry.getValue())) return false;
            reserved.forEach((tile, count) -> tile.changeReserved(sign * count)); return true;
        }
    }
    public static final class Rate extends RequirementCompressedAir implements TickBatchRequirement {
        public Rate(IOType io, float pressure, int amount) { super(io, pressure, amount); }
        @Override public boolean rate() { return true; }
        @Override public boolean isBatchPerTick() { return true; }
        @Override public String doIOTick(MachineContext ctx, int copies) { var p = planTick(ctx, List.of(this), copies); return p.execute() ? null : p.failure(); }
        @Override public TickPlan planTick(MachineContext ctx, List<Requirement> all, int copies) {
            var input = plan(ctx, all, copies, IOType.INPUT, true); var output = plan(ctx, all, copies, IOType.OUTPUT, true);
            return new TickPlan() {
                @Override public boolean valid() { return input.valid() && output.valid(); }
                @Override public String failure() { return input.valid() ? output.failure() : input.failure(); }
                @Override public boolean execute() { return valid() && input.execute() && output.execute(); }
            };
        }
    }
    public static final class Type implements RequirementType {
        private final boolean rate;
        public Type(boolean rate) { this.rate = rate; }
        @Override public String requiresModid() { return "pneumaticcraft"; }
        @Override public Requirement createRequirement(IOType io, JsonObject json) {
            int amount = 0;
            if (json.has("air")) {
                try {
                    if (!json.get("air").isJsonPrimitive() || !json.getAsJsonPrimitive("air").isNumber()) throw new ArithmeticException();
                    amount = json.get("air").getAsBigDecimal().intValueExact();
                } catch (ArithmeticException | NumberFormatException e) { throw new JsonParseException("air must be a non-negative integer in mL", e); }
            } else if (io == IOType.OUTPUT) throw new JsonParseException("compressed air output requires air");
            float pressure = 0;
            if (json.has("pressure")) {
                if (!json.get("pressure").isJsonPrimitive() || !json.getAsJsonPrimitive("pressure").isNumber()) throw new JsonParseException("pressure must be a number in bar");
                pressure = json.get("pressure").getAsFloat();
            } else if (io == IOType.INPUT) throw new JsonParseException("compressed air input requires pressure");
            if (amount < 0 || !Float.isFinite(pressure) || pressure < -1 || pressure > 25) throw new JsonParseException("air must be non-negative; pressure must be within -1..25 bar");
            boolean perTick = rate;
            if (json.has("perTick")) {
                if (!json.get("perTick").isJsonPrimitive() || !json.getAsJsonPrimitive("perTick").isBoolean()) throw new JsonParseException("perTick must be boolean");
                perTick |= json.get("perTick").getAsBoolean();
            }
            var result = perTick ? new Rate(io, pressure, amount) : new RequirementCompressedAir(io, pressure, amount);
            if (json.has("parallelizeUnaffected")) {
                if (!json.get("parallelizeUnaffected").isJsonPrimitive() || !json.getAsJsonPrimitive("parallelizeUnaffected").isBoolean()) throw new JsonParseException("parallelizeUnaffected must be boolean");
                result.setParallelizeUnaffected(json.get("parallelizeUnaffected").getAsBoolean());
            }
            return result;
        }
    }
}
