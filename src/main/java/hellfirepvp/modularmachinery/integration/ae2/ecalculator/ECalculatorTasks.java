/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO ECalculatorThreadCore; task execution belongs to AE2 15.
 */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import net.minecraft.nbt.*;
import java.util.*;

/** A core owns its tasks independently of an assembled controller or an AE node. */
public final class ECalculatorTasks implements ECalculatorCoreAccess {
    public final TileECalculatorPart core;
    private final CompoundTag original;
    private final List<ECalculatorCpuHost> cpus = new ArrayList<>();
    private boolean invalid, closed;
    private static final String KEY = "AE2Crafting";
    ECalculatorTasks(TileECalculatorPart core, CompoundTag data) {
        this.core = core; original = data.copy();
        if (!data.contains(KEY)) return;
        try {
            if (!data.contains(KEY, Tag.TAG_COMPOUND)) throw new IllegalArgumentException("invalid EC task container");
            var state = data.getCompound(KEY);
            if (state.getInt("Version") != 1 || !state.contains("CPUs", Tag.TAG_LIST)) throw new IllegalArgumentException("unsupported EC task format");
            var list = ECalculatorSavedTasks.compounds(state, "CPUs");
            var spec = core.specification();
            if (list.size() > spec.normalSlots() + spec.hyperSlots()) throw new IllegalArgumentException("too many saved tasks");
            int normal = 0, hyper = 0; long allocated = 0;
            // Validate the whole core before native readers register any requester links.
            for (var entry : list) {
                var tag = (CompoundTag)entry;
                long bytes = tag.getLong("ReservedBytes");
                if (bytes <= 0 || !tag.contains("Logic", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("invalid task memory/logic");
                allocated = Math.addExact(allocated, bytes);
                if (tag.getBoolean("Hyper")) hyper++; else normal++;
                ECalculatorSavedTasks.validate(tag.getCompound("Logic"), core.getLevel());
            }
            if (normal > spec.normalSlots() || hyper > spec.hyperSlots()) throw new IllegalArgumentException("invalid saved task slots");
            for (var entry : list) {
                var tag = (CompoundTag)entry;
                long bytes = tag.getLong("ReservedBytes");
                if (bytes <= 0 || !tag.contains("Logic", Tag.TAG_COMPOUND)) throw new IllegalArgumentException("invalid task memory/logic");
                var cpu = ECalculatorCpuHost.restored(this, bytes, tag.getBoolean("Hyper"));
                cpus.add(cpu); cpu.cluster.readFromNBT(tag.getCompound("Logic"));
            }
        } catch (RuntimeException failure) {
            invalid = true; cpus.forEach(ECalculatorCpuHost::retire); cpus.clear();
            Modularmachinery.LOG.error("[MMC][EC] Invalid tasks at {}; keeping opaque data and disabling this core", core.getBlockPos(), failure);
        }
    }
    public boolean valid() { return !invalid && !closed; }
    @Override public boolean monitorReadable() { return valid(); }
    @Override public boolean hasTasks() { return valid() && !cpus.isEmpty(); }
    @Override public Map<UUID, ECalculatorStatistics.Measurement> monitorStatistics() {
        if (!valid()) return Map.of();
        var result = new HashMap<UUID, ECalculatorStatistics.Measurement>();
        for (var cpu : cpus) result.put(cpu.monitorId, cpu.measurement());
        return Map.copyOf(result);
    }
    @Override public List<ECalculatorMonitor.Task> monitorTasks() {
        if (!valid()) return List.of();
        var result = new ArrayList<ECalculatorMonitor.Task>();
        for (var cpu : cpus) {
            var output = cpu.cluster.craftingLogic.getFinalJobOutput();
            var icon = output != null && output.what() instanceof appeng.api.stacks.AEItemKey item
                    ? new net.minecraft.world.item.ItemStack(item.getItem()) : net.minecraft.world.item.ItemStack.EMPTY;
            result.add(new ECalculatorMonitor.Task(cpu.monitorId,
                    output == null ? net.minecraft.network.chat.Component.translatable("gui.modularmachinery.ec.returning") : output.what().getDisplayName(),
                    icon, output == null ? 0 : output.amount(), cpu.bytes, cpu.hyper,
                    cpu.cluster.craftingLogic.hasJob(), cpu.active()));
        }
        return List.copyOf(result);
    }
    @Override public boolean cancelTask(UUID task) {
        if (!valid()) return false;
        for (var cpu : cpus) if (cpu.monitorId.equals(task) && !cpu.submitting() && cpu.cluster.craftingLogic.hasJob()) {
            // AE owns cancellation and material returns. Do not drop the CPU/reservation while its inventory is nonempty.
            cpu.cluster.craftingLogic.cancel(); cpu.dirty(); return true;
        }
        return false;
    }
    public List<ECalculatorCpuHost> cpus() { return List.copyOf(cpus); }
    public long allocated() { long bytes = 0; for (var cpu : cpus) bytes = Math.addExact(bytes, cpu.bytes); return bytes; }
    public boolean hasSlot(boolean hyper) {
        if (!valid()) return false;
        var used = cpus.stream().filter(cpu -> cpu.hyper == hyper).count();
        return used < (hyper ? core.specification().hyperSlots() : core.specification().normalSlots());
    }
    public void add(ECalculatorCpuHost cpu) { cpus.add(cpu); core.setChanged(); }
    public void remove(ECalculatorCpuHost cpu) { cpus.remove(cpu); core.setChanged(); }
    @Override public CompoundTag save() {
        var tag = original.copy();
        if (invalid || closed || cpus.isEmpty() && !original.contains(KEY)) return tag;
        var state = new CompoundTag(); state.putInt("Version", 1);
        var list = new ListTag();
        for (var cpu : cpus) {
            var entry = new CompoundTag(); entry.putLong("ReservedBytes", cpu.bytes); entry.putBoolean("Hyper", cpu.hyper);
            var logic = new CompoundTag(); cpu.cluster.writeToNBT(logic); entry.put("Logic", logic); list.add(entry);
        }
        state.put("CPUs", list); tag.put(KEY, state); return tag;
    }
    @Override public void close() { closed = true; cpus.forEach(ECalculatorCpuHost::retire); }
}
