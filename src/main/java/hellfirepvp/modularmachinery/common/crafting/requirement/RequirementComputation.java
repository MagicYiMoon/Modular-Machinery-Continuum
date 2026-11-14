/* SPDX-License-Identifier: GPL-3.0-only
 * Adapts NECO RecipePrimerHyperNet/old.NetNodeImpl computation demand, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.crafting.requirement;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.hypernet.ComputationTickIO;
import hellfirepvp.modularmachinery.common.machine.*;
import java.util.List;

/** Continuous network input; execution is grouped by the ticker, never by physical hatch selectors. */
public final class RequirementComputation implements Requirement.PerTick, Requirement.Parallelizable {
    private final double amount;
    private final boolean triggerFailure;
    private int parallelism = 1;
    private boolean unaffected;
    public RequirementComputation(double amount, boolean triggerFailure) {
        if (!Double.isFinite(amount) || amount < 0) throw new IllegalArgumentException("Computation amount must be finite and nonnegative");
        this.amount = amount; this.triggerFailure = triggerFailure;
    }
    public double amount() { return amount; }
    public boolean triggerFailure() { return triggerFailure; }
    @Override public String typeId() { return "modularmachinery:computation"; }
    @Override public IOType getIOType() { return IOType.INPUT; }
    @Override public boolean isBatchPerTick() { return true; }
    @Override public boolean canStart(MachineContext context) { return ComputationTickIO.startPlan(context, List.of(this), 1).valid(); }
    @Override public boolean canFinish(MachineContext context) { return true; }
    @Override public String describeFailure(MachineContext context) { return ComputationTickIO.startPlan(context, List.of(this), 1).failure(); }
    @Override public String describe() { return amount + " computation/t"; }
    @Override public int getMaxParallelism(MachineContext context, int max, List<Requirement> siblings) {
        List<Requirement> all = siblings == null || siblings.isEmpty() ? List.of(this) : siblings;
        int low = 0, high = Math.max(0, max);
        while (low < high) {
            int mid = low + (int)(((long)high - low + 1) / 2);
            if (ComputationTickIO.startPlan(context, all, mid).valid()) low = mid; else high = mid - 1;
        }
        return low;
    }
    @Override public int getParallelism() { return parallelism; }
    @Override public void setParallelism(int value) { if (!unaffected) parallelism = Math.max(1, value); }
    @Override public boolean isParallelizeUnaffected() { return unaffected; }
    @Override public void setParallelizeUnaffected(boolean value) { unaffected = value; if (value) parallelism = 1; }
    @Override public Requirement scaled(float value, int operation, IOType io, boolean chance) {
        double next = chance || io == IOType.OUTPUT ? amount : operation == OPERATION_MULTIPLY ? amount * value : amount + value;
        var copy = new RequirementComputation(Math.max(0, next), triggerFailure);
        copy.setParallelizeUnaffected(unaffected); copy.setParallelism(parallelism); return copy;
    }
}
