/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting;

import hellfirepvp.modularmachinery.common.machine.MachineContext;
import java.util.List;

/** Optional resources which must reserve all same-type rates before the machine pays any tick costs. */
public interface TickBatchRequirement extends Requirement.PerTick {
    TickPlan planTick(MachineContext context, List<Requirement> siblings, int parallelism);
    interface TickPlan extends BatchIORequirement.Plan { String failure(); }
}
