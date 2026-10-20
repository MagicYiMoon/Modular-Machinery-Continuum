/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.mekanism;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import java.util.*;

/** Groups all gas_pertick requirements into one reservation and commit per machine tick. */
public final class GasTickIO {
    public record Plan(String failure, GasIOPlan input, GasIOPlan output) {
        public boolean valid() { return failure == null; }
        public boolean execute() { return valid() && input.execute() && output.execute(); }
    }
    private GasTickIO() { }

    public static Plan plan(MachineContext context, List<Requirement> requirements, int parallelism) {
        List<Requirement> converted = new ArrayList<>();
        for (Requirement requirement : requirements) {
            if (!(requirement instanceof RequirementGasPerTick rate)) continue;
            var gas = new RequirementGas(rate.getIOType(), rate.getRawId(), rate.getAmount(), 1F);
            gas.setSelectorTag(rate.getSelectorTag());
            gas.setParallelizeUnaffected(rate.isParallelizeUnaffected());
            converted.add(gas);
        }
        if (converted.isEmpty()) return new Plan(null, GasIOPlan.inputs(context, List.of(), parallelism),
                GasIOPlan.outputs(context, List.of(), parallelism));
        // Inputs and outputs are planned separately so one direction cannot accidentally consume the
        // other direction's hatches. GasIOPlan still groups all same-direction rates atomically.
        List<Requirement> inputs = converted.stream().filter(r -> r.getIOType() == IOType.INPUT).toList();
        List<Requirement> outputs = converted.stream().filter(r -> r.getIOType() == IOType.OUTPUT).toList();
        GasIOPlan inputPlan = GasIOPlan.inputs(context, inputs, parallelism);
        if (!inputPlan.valid()) return new Plan(inputPlan.failure(), inputPlan, GasIOPlan.outputs(context, List.of(), parallelism));
        GasIOPlan outputPlan = GasIOPlan.outputs(context, outputs, parallelism);
        if (!outputPlan.valid()) return new Plan(outputPlan.failure(), inputPlan, outputPlan);
        return new Plan(null, inputPlan, outputPlan);
    }
}
