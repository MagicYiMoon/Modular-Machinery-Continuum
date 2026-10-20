/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.mekanism;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import java.util.List;

/** Continuous Mekanism gas rate, matching the original gas_pertick requirement. */
public final class RequirementGasPerTick extends hellfirepvp.modularmachinery.common.crafting.SelectorRequirement
        implements Requirement.Parallelizable, Requirement.PerTick {
    private final RequirementGas gas;

    public RequirementGasPerTick(IOType io, String rawId, long amount) {
        this.gas = new RequirementGas(io, rawId, amount, 1F);
    }

    public RequirementGasPerTick(RequirementGas gas) { this.gas = gas; }
    public RequirementGas gas() { return gas; }
    public String getRawId() { return gas.getRawId(); }
    public int getAmount() { return gas.getAmount(); }
    public mekanism.api.chemical.gas.GasStack asStack() { return gas.asStack(); }
    public boolean isResolvable() { return gas.isResolvable(); }

    @Override public IOType getIOType() { return gas.getIOType(); }
    @Override public String typeId() { return "modularmachinery:gas_pertick"; }
    @Override public String describe() { return gas.describe() + "/tick"; }
    @Override public boolean canStart(MachineContext context) {
        return GasTickIO.plan(context, List.of(this), 1).valid();
    }
    @Override public boolean canFinish(MachineContext context) { return true; }
    @Override public boolean canEmit(MachineContext context, int parallelism) { return true; }
    @Override public boolean isBatchPerTick() { return true; }
    @Override public String doIOTick(MachineContext context, int parallelism) {
        var plan = GasTickIO.plan(context, List.of(this), parallelism);
        if (!plan.valid()) return plan.failure();
        return plan.execute() ? null : (getIOType() == IOType.INPUT
                ? "craftcheck.failure.gas.input" : "craftcheck.failure.gas.output.space");
    }
    @Override public int getParallelism() { return gas.getParallelism(); }
    @Override public void setParallelism(int value) { gas.setParallelism(value); }
    @Override public boolean isParallelizeUnaffected() { return gas.isParallelizeUnaffected(); }
    @Override public void setParallelizeUnaffected(boolean value) { gas.setParallelizeUnaffected(value); }
    @Override public int getMaxParallelism(MachineContext context, int max, List<Requirement> siblings) {
        var all = new java.util.ArrayList<Requirement>(siblings == null ? List.of() : siblings);
        if (!all.contains(this)) all.add(this);
        int low = 0, high = Math.max(0, max);
        while (low < high) {
            int mid = low + (int)(((long) high - low + 1) / 2);
            if (GasTickIO.plan(context, all, mid).valid()) low = mid; else high = mid - 1;
        }
        return low;
    }
    @Override public Requirement scaled(float value, int operation, IOType io, boolean chance) {
        if (chance || (io != null && io != getIOType())) return this;
        RequirementGas scaled = (RequirementGas) gas.scaled(value, operation, io, false);
        var result = new RequirementGasPerTick(scaled);
        result.setParallelizeUnaffected(isParallelizeUnaffected());
        result.setParallelism(getParallelism());
        return copySelectorTo(result);
    }
}
