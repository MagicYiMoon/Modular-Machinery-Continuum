/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting;

import hellfirepvp.modularmachinery.common.machine.*;
import java.util.List;

/** Non-consuming source conditions still need a network snapshot and a one-shot IO contract. */
public abstract class ConditionRequirement extends SelectorRequirement implements BatchIORequirement {
    @Override public IOType getIOType() { return IOType.INPUT; }
    @Override public boolean canFinish(MachineContext context) { return true; }
    @Override public boolean canEmit(MachineContext context, int copies) { return true; }
    @Override public boolean consume(MachineContext context, int copies) { return true; }
    @Override public int produce(MachineContext context, int copies) { return 0; }
    @Override public String outputFailure(MachineContext context, List<Requirement> all, int copies) { return null; }
    @Override public Plan planInputs(MachineContext context, List<Requirement> all, int copies) {
        return new Plan() { public boolean valid() { return true; } public boolean execute() { return true; } };
    }
}
