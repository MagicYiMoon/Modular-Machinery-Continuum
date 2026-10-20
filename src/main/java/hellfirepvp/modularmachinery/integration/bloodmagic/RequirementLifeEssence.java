/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.machine.*;
import java.util.*;

/** Source type modularmachinery:lifeessence, including its perTick flag and batch exemption. */
public class RequirementLifeEssence extends SelectorRequirement implements Requirement.Parallelizable, BatchIORequirement {
    private final IOType io;
    private final int amount;
    private int parallelism = 1;
    private boolean unaffected;
    public RequirementLifeEssence(IOType io, int amount) { this.io = io; this.amount = Math.max(0, amount); }
    public int amount() { return amount; }
    public boolean rate() { return false; }
    @Override public IOType getIOType() { return io; }
    @Override public String typeId() { return "modularmachinery:lifeessence"; }
    @Override public String describe() { return io.name().toLowerCase(Locale.ROOT) + " " + amount + " LP" + (rate() ? "/tick" : ""); }
    @Override public String describeFailure(MachineContext context) { return io == IOType.INPUT ? "error.modularmachinery.requirement.lifeessence.lp.less" : "error.modularmachinery.requirement.lifeessence.lp.more"; }
    @Override public boolean canStart(MachineContext context) { return io == IOType.OUTPUT && isIgnoreOutputCheck() || plan(context, List.of(this), 1, io, rate()).valid(); }
    @Override public boolean canFinish(MachineContext context) { return rate() || canStart(context); }
    @Override public boolean canEmit(MachineContext context, int copies) { return rate() || io != IOType.OUTPUT || isIgnoreOutputCheck() || plan(context, List.of(this), copies, io, false).valid(); }
    @Override public boolean consume(MachineContext context, int copies) { return rate() || plan(context, List.of(this), copies, IOType.INPUT, false).execute(); }
    @Override public int produce(MachineContext context, int copies) {
        if (rate() || io != IOType.OUTPUT) return 0;
        var output = plan(context, List.of(this), copies, io, false);
        return output.execute() ? (int) Math.min(Integer.MAX_VALUE,
                output.reserved().values().stream().mapToLong(Long::longValue).sum()) : 0;
    }
    @Override public Plan planInputs(MachineContext context, List<Requirement> all, int copies) { return plan(context, all, copies, IOType.INPUT, false); }
    @Override public String outputFailure(MachineContext context, List<Requirement> all, int copies) { return plan(context, all, copies, IOType.OUTPUT, false).failure(); }
    private long demand(int copies) { return (long) amount * (unaffected ? 1 : Math.max(1, copies)); }
    @Override public int getParallelism() { return parallelism; }
    @Override public void setParallelism(int n) { if (!unaffected) parallelism = Math.max(1, n); }
    @Override public boolean isParallelizeUnaffected() { return unaffected; }
    @Override public void setParallelizeUnaffected(boolean flag) { unaffected = flag; if (flag) parallelism = 1; }
    @Override public int getMaxParallelism(MachineContext context, int max, List<Requirement> siblings) {
        var all = new ArrayList<>(siblings == null ? List.<Requirement>of() : siblings); if (!all.contains(this)) all.add(this);
        int low = 0, high = Math.max(0, max);
        while (low < high) {
            int mid = low + (int) (((long) high - low + 1) / 2);
            if (plan(context, all, mid, io, rate()).valid()) low = mid; else high = mid - 1;
        }
        return low;
    }
    @Override public void writeJson(JsonObject json) { json.addProperty("amount", amount); json.addProperty("perTick", rate()); json.addProperty("parallelizeUnaffected", unaffected); }
    @Override public Requirement scaled(float value, int operation, IOType direction, boolean chance) {
        if (chance || direction != null && direction != io) return this;
        int changed = Math.max(0, Math.round(Requirement.apply(amount, value, operation)));
        RequirementLifeEssence copy = rate() ? new Rate(io, changed) : new RequirementLifeEssence(io, changed);
        copy.setParallelizeUnaffected(unaffected); copy.setParallelism(parallelism); return copySelectorTo(copy);
    }
    public static EssencePlan plan(MachineContext context, List<Requirement> all, int copies, IOType direction, boolean rate) {
        var reservations = new LinkedHashMap<TileLifeEssenceProvider, Long>();
        // Mandatory outputs reserve first; a discardable output must not steal their space.
        List<Requirement> ordered = direction == IOType.OUTPUT
                ? all.stream().sorted(Comparator.comparing(Requirement::isIgnoreOutputCheck)).toList() : all;
        for (Requirement raw : ordered) {
            if (!(raw instanceof RequirementLifeEssence r) || r.io != direction || r.rate() != rate) continue;
            long remaining = r.demand(copies);
            for (var pos : context.forSelector(r.getSelectorTag()).getComponentPositions()) {
                if (!(context.getLevel().getBlockEntity(pos) instanceof TileLifeEssenceProvider tile) || tile.getIOType() != direction) continue;
                long prior = reservations.getOrDefault(tile, 0L);
                long available = direction == IOType.INPUT ? tile.take(Long.MAX_VALUE, true) : tile.add(Long.MAX_VALUE, true);
                long take = Math.min(remaining, Math.max(0L, available - prior));
                if (take > 0) reservations.put(tile, prior + take);
                remaining -= take; if (remaining == 0) break;
            }
            if (remaining > 0 && !(direction == IOType.OUTPUT && r.isIgnoreOutputCheck()))
                return new EssencePlan(r.describeFailure(context), direction, reservations);
        }
        return new EssencePlan(null, direction, reservations);
    }
    public record EssencePlan(String failure, IOType direction, Map<TileLifeEssenceProvider, Long> reserved) implements TickBatchRequirement.TickPlan {
        @Override public boolean valid() { return failure == null; }
        @Override public boolean execute() {
            if (!valid()) return false;
            for (var e : reserved.entrySet()) if ((direction == IOType.INPUT ? e.getKey().take(e.getValue(), true) : e.getKey().add(e.getValue(), true)) < e.getValue()) return false;
            for (var e : reserved.entrySet()) { if (direction == IOType.INPUT) e.getKey().take(e.getValue(), false); else e.getKey().add(e.getValue(), false); }
            return true;
        }
    }
    public static final class Rate extends RequirementLifeEssence implements TickBatchRequirement {
        public Rate(IOType io, int amount) { super(io, amount); }
        @Override public boolean rate() { return true; }
        @Override public boolean isBatchPerTick() { return true; }
        @Override public TickPlan planTick(MachineContext context, List<Requirement> all, int copies) {
            var in = plan(context, all, copies, IOType.INPUT, true); var out = plan(context, all, copies, IOType.OUTPUT, true);
            return new TickPlan() {
                public String failure() { return in.failure() != null ? in.failure() : out.failure(); }
                public boolean valid() { return in.valid() && out.valid(); }
                public boolean execute() { return valid() && in.execute() && out.execute(); }
            };
        }
        @Override public String doIOTick(MachineContext context, int copies) { var p = planTick(context, List.of(this), copies); return p.execute() ? null : p.failure(); }
    }
}
