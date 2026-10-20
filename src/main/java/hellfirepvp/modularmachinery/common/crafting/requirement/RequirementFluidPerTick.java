/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.requirement;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.helper.FluidTickIO;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import java.util.List;

/** A continuous rate, deliberately separate from the one-time fluid requirement. */
public final class RequirementFluidPerTick extends hellfirepvp.modularmachinery.common.crafting.SelectorRequirement implements Requirement.Parallelizable, Requirement.PerTick {
    private final RequirementFluid fluid;

    public RequirementFluidPerTick(RequirementFluid fluid) { this.fluid = fluid; }
    public RequirementFluid fluid() { return fluid; }
    @Override public IOType getIOType() { return fluid.getIOType(); }
    @Override public String typeId() { return "modularmachinery:fluid_pertick"; }
    @Override public String describe() { return fluid.describe() + "/tick"; }
    @Override public boolean canStart(MachineContext context) {
        return FluidTickIO.plan(context, List.of(this), 1).failure() == null;
    }
    @Override public boolean canFinish(MachineContext context) { return true; }
    @Override public boolean canEmit(MachineContext context, int parallelism) { return true; }
    @Override public boolean isBatchPerTick() { return true; }

    /** Fluid rates are planned together by FluidTickIO so two rates cannot overbook one tank. */
    @Override public String doIOTick(MachineContext context, int parallelism) {
        var plan = hellfirepvp.modularmachinery.common.crafting.helper.FluidTickIO
                .plan(context, List.of(this), parallelism);
        if (plan.failure() != null) return plan.failure();
        return plan.execute() ? null : (getIOType() == IOType.INPUT
                ? "craftcheck.failure.fluid.input" : "craftcheck.failure.fluid.output.space");
    }
    @Override public int getParallelism() { return fluid.getParallelism(); }
    @Override public void setParallelism(int parallelism) { fluid.setParallelism(parallelism); }
    @Override public boolean isParallelizeUnaffected() { return fluid.isParallelizeUnaffected(); }
    @Override public void setParallelizeUnaffected(boolean unaffected) { fluid.setParallelizeUnaffected(unaffected); }

    @Override
    public int getMaxParallelism(MachineContext context, int max, List<Requirement> siblings) {
        var all = new java.util.ArrayList<Requirement>(siblings == null ? List.of() : siblings);
        if (!all.contains(this)) all.add(this);
        int low = 0, high = Math.max(0, max);
        while (low < high) {
            int count = low + (int) (((long) high - low + 1) / 2);
            if (FluidTickIO.plan(context, all, count).failure() == null) low = count;
            else high = count - 1;
        }
        return low;
    }

    @Override
    public Requirement scaled(float value, int operation, IOType io, boolean chance) {
        if (chance || (io != null && io != getIOType())) return this;
        var result = new RequirementFluidPerTick((RequirementFluid) fluid.scaled(value, operation, io, false));
        result.setParallelizeUnaffected(isParallelizeUnaffected());
        result.setParallelism(getParallelism());
        return copySelectorTo(result);
    }
}
