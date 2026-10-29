/* SPDX-License-Identifier: GPL-3.0-only
 * Port of upstream RequirementGas, modified for Forge 1.20.1, 2026-10.
 */
package hellfirepvp.modularmachinery.integration.mekanism;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.machine.*;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.*;
import net.minecraft.resources.ResourceLocation;
import java.util.*;

/** One-time gas IO: consume at start, produce at completion, through ordinary hybrid hatches. */
public final class RequirementGas extends SelectorRequirement implements Requirement.Parallelizable, BatchIORequirement {
    @Override public int getSortingWeight() { return PRIORITY_WEIGHT_FLUID; }
    private final IOType ioType;
    private final String rawId;
    private final int amount;
    private final float chance;
    private final Gas gas;
    private int parallelism = 1;
    private boolean parallelizeUnaffected;

    public RequirementGas(IOType ioType, String rawId, long amount, float chance) {
        this.ioType = Objects.requireNonNull(ioType);
        String name = rawId == null ? "" : rawId;
        // 1.12 gas names were bare strings; Mekanism 10 uses namespaced registry keys.
        this.rawId = name.indexOf(':') < 0 ? "mekanism:" + name : name;
        this.amount = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, amount));
        this.chance = Float.isFinite(chance) ? Math.max(0F, Math.min(1F, chance)) : 1F;
        ResourceLocation id = ResourceLocation.tryParse(this.rawId);
        this.gas = id == null ? null : MekanismAPI.gasRegistry().getValue(id);
    }
    public GasStack asStack(long count) { return isResolvable() && count > 0 ? new GasStack(gas, count) : GasStack.EMPTY; }
    public GasStack asStack() { return asStack(amount); }
    public String getRawId() { return rawId; }
    public int getAmount() { return amount; }
    public float getChance() { return chance; }
    public boolean isResolvable() { return gas != null && !gas.isEmptyType(); }
    public boolean isGuaranteed() { return chance >= 1F; }
    public boolean matches(GasStack stored) { return isResolvable() && !stored.isEmpty() && stored.isTypeEqual(gas); }
    public long demand(int copies) { return (long) amount * (parallelizeUnaffected ? 1 : Math.max(1, copies)); }
    public boolean roll() {
        return chance > 0F && (chance >= 1F || Boolean.getBoolean("modularmachinery.selfTest")
                || java.util.concurrent.ThreadLocalRandom.current().nextFloat() < chance);
    }
    @Override public IOType getIOType() { return ioType; }
    @Override public String typeId() { return "modularmachinery:gas"; }
    @Override public String describe() { return (ioType == IOType.INPUT ? "input " : "output ") + amount + "mB " + rawId; }
    @Override public String describeFailure(MachineContext context) {
        return ioType == IOType.INPUT ? "craftcheck.failure.gas.input" : "craftcheck.failure.gas.output.space";
    }
    @Override public boolean canStart(MachineContext context) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return true;
        return ioType == IOType.INPUT ? GasIOPlan.inputs(context, List.of(this), 1).valid()
                : GasIOPlan.outputs(context, List.of(this), 1).valid();
    }
    @Override public boolean canFinish(MachineContext context) { return canStart(context); }
    @Override public boolean canEmit(MachineContext context, int copies) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return true;
        return ioType != IOType.OUTPUT || GasIOPlan.outputs(context, List.of(this), copies).valid();
    }
    @Override public int getMaxParallelism(MachineContext context, int max, List<Requirement> siblings) {
        if (ioType == IOType.OUTPUT && isIgnoreOutputCheck()) return max;
        List<Requirement> all = new ArrayList<>(siblings == null ? List.of() : siblings);
        if (!all.contains(this)) all.add(this);
        int low = 0, high = Math.max(0, max);
        while (low < high) {
            int mid = low + (int)(((long) high - low + 1) / 2);
            boolean fits = ioType == IOType.INPUT ? GasIOPlan.inputs(context, all, mid).valid()
                    : GasIOPlan.outputs(context, all, mid).valid();
            if (fits) low = mid; else high = mid - 1;
        }
        return low;
    }
    @Override public int getParallelism() { return parallelism; }
    @Override public void setParallelism(int value) { if (!parallelizeUnaffected) parallelism = Math.max(1, value); }
    @Override public boolean isParallelizeUnaffected() { return parallelizeUnaffected; }
    @Override public void setParallelizeUnaffected(boolean value) { parallelizeUnaffected = value; if (value) parallelism = 1; }
    @Override public boolean consume(MachineContext context, int copies) { return GasIOPlan.inputs(context, List.of(this), copies).execute(); }
    @Override public int produce(MachineContext context, int copies) {
        if (ioType != IOType.OUTPUT || !roll()) return 0;
        var plan = GasIOPlan.outputs(context, List.of(this), copies);
        if (isIgnoreOutputCheck()) {
            plan.execute();
            return (int) Math.min(Integer.MAX_VALUE, demand(copies));
        }
        return plan.execute() ? (int) Math.min(Integer.MAX_VALUE, demand(copies)) : 0;
    }
    @Override public Plan planInputs(MachineContext context, List<Requirement> requirements, int copies) {
        return GasIOPlan.inputs(context, requirements, copies);
    }
    @Override public String outputFailure(MachineContext context, List<Requirement> requirements, int copies) {
        // The group gate must still check other outputs when its first member is exempt.
        return GasIOPlan.outputs(context, requirements.stream().filter(r -> !r.isIgnoreOutputCheck()).toList(), copies).failure();
    }
    @Override public void writeJson(JsonObject json) {
        json.addProperty("gas", rawId);
        json.addProperty("amount", amount);
        json.addProperty("chance", chance);
        json.addProperty("parallelizeUnaffected", parallelizeUnaffected);
    }
    @Override public Requirement scaled(float value, int operation, IOType io, boolean chanceFlag) {
        if (io != null && io != ioType) return this;
        float scaled = Requirement.apply(chanceFlag ? chance : amount, value, operation);
        var copy = copySelectorTo(new RequirementGas(ioType, rawId, chanceFlag ? amount : Math.max(0, Math.round(scaled)),
                chanceFlag ? scaled : chance));
        copy.setParallelizeUnaffected(parallelizeUnaffected);
        copy.setParallelism(parallelism);
        return copy;
    }
}
