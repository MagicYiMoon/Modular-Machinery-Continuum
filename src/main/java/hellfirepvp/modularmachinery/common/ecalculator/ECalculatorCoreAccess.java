/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.ecalculator;

import net.minecraft.nbt.CompoundTag;

/** AE types stay behind a delayed factory; unknown task data survives missing dependencies. */
public interface ECalculatorCoreAccess {
    CompoundTag save();
    void close();
    default boolean monitorReadable() { return true; }
    default boolean hasTasks() { return false; }
    default java.util.List<ECalculatorMonitor.Task> monitorTasks() { return java.util.List.of(); }
    default java.util.Map<java.util.UUID, ECalculatorStatistics.Measurement> monitorStatistics() { return java.util.Map.of(); }
    default boolean cancelTask(java.util.UUID task) { return false; }
}
