/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting;

import hellfirepvp.modularmachinery.common.machine.MachineContext;

/** Optional one-shot input/output channel used by chemical and magic integrations. */
public interface BatchIORequirement extends Requirement {
    boolean consume(MachineContext context, int parallelism);
    int produce(MachineContext context, int parallelism);
    Plan planInputs(MachineContext context, java.util.List<Requirement> requirements, int parallelism);
    /** Optional shared output commit, for world resources whose providers affect overlapping areas. */
    default Plan planOutputs(MachineContext context, java.util.List<Requirement> requirements, int parallelism) { return null; }
    String outputFailure(MachineContext context, java.util.List<Requirement> requirements, int parallelism);
    void writeJson(com.google.gson.JsonObject json);

    interface Plan {
        boolean valid();
        boolean execute();
    }
}
