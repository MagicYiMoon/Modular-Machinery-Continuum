/* SPDX-License-Identifier: GPL-3.0-only
 * NECO virtual/running CPU ownership adapted to AE2 15 host entry points.
 */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.config.Actionable;
import appeng.api.stacks.GenericStack;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.crafting.execution.CraftingSubmitResult;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

/** Unique lifetime identity per actual AE cluster. No hidden CraftingBlockEntity host is created. */
public final class ECalculatorCpuHost {
    /** Ephemeral UI identity: never reuse a cancelled/restored CPU's button for a new task. */
    final java.util.UUID monitorId = java.util.UUID.randomUUID();
    public final CraftingCPUCluster cluster;
    ECalculatorTasks tasks;
    long bytes;
    boolean hyper;
    private final ECalculatorMEChannelNode idleChannel;
    private final long idleEpoch;
    private boolean retired, submitting;
    private final hellfirepvp.modularmachinery.common.ecalculator.ECalculatorStatistics statistics = new hellfirepvp.modularmachinery.common.ecalculator.ECalculatorStatistics();
    private boolean measuring;
    private long pushedPatterns;
    public void beginMeasurement() { measuring = true; pushedPatterns = 0; }
    public void recordOperations(int count) { if (measuring && count > 0) pushedPatterns = Math.min(Long.MAX_VALUE / 100, pushedPatterns + (long)count); }
    public void endMeasurement(long nanos) { if (measuring) { measuring = false; statistics.record(Math.max(0, nanos), pushedPatterns); } }
    public hellfirepvp.modularmachinery.common.ecalculator.ECalculatorStatistics.Measurement measurement() { return statistics.measurement(); }
    private ECalculatorCpuHost(ECalculatorMEChannelNode channel, ECalculatorTasks tasks, long bytes, boolean hyper) {
        idleChannel = channel; idleEpoch = channel == null ? -1 : channel.epoch(); this.tasks = tasks; this.bytes = bytes; this.hyper = hyper;
        var pos = tasks != null ? tasks.core.getBlockPos() : channel.owner().getBlockPos();
        cluster = new CraftingCPUCluster(pos, pos);
        ((ECalculatorCpuBridge)(Object)cluster).mmc$ecHost(this);
    }
    static ECalculatorCpuHost idle(ECalculatorMEChannelNode channel) { return new ECalculatorCpuHost(channel, null, 0, false); }
    static ECalculatorCpuHost restored(ECalculatorTasks tasks, long bytes, boolean hyper) { return new ECalculatorCpuHost(null, tasks, bytes, hyper); }
    public ECalculatorMEChannelNode channel() {
        if (retired) return null;
        if (tasks == null) return idleChannel != null && idleChannel.epoch() == idleEpoch ? idleChannel : null;
        var owner = tasks.core.owner();
        if (owner == null || !tasks.core.isActivePart() || !tasks.valid()) return null;
        for (var part : owner.parts()) if (part instanceof ECalculatorMEChannelNode node) return node;
        return null;
    }
    public boolean active() { var channel = channel(); return !retired && !cluster.isDestroyed() && channel != null && channel.isNetworkActive(); }
    /** A deferred AE roster must not use a CPU after its channel has moved to another grid. */
    public boolean belongsTo(IGrid grid) {
        var channel = channel();
        var node = channel == null ? null : channel.getActionableNode();
        return !retired && !cluster.isDestroyed() && node != null && node.getGrid() == grid;
    }
    public boolean belongsTo(appeng.api.networking.crafting.ICraftingService service) {
        var channel = channel();
        var node = channel == null ? null : channel.getActionableNode();
        return !retired && !cluster.isDestroyed() && node != null && node.getGrid().getCraftingService() == service;
    }
    public boolean busy() { return !retired && tasks != null; } // Includes cancelled tasks waiting to return their inventory.
    public IGridNode node() { var channel = channel(); return channel != null && active() ? channel.getActionableNode() : null; }
    public Level level() { return tasks == null ? idleChannel.getLevel() : tasks.core.getLevel(); }
    public IActionSource source() { var channel = channel(); return channel == null ? IActionSource.empty() : new MachineSource(channel); }
    public Component name() {
        var channel = channel();
        return Component.translatable(tasks == null ? "ec.cpu.idle" : hyper ? "ec.cpu.hyper" : "ec.cpu.normal",
                channel == null || channel.owner() == null ? "?" : channel.owner().tier());
    }
    public long storage() { var channel = channel(); return tasks == null ? channel == null ? 0 : channel.availableBytes() : bytes; }
    public int parallelism() { var channel = channel(); return channel == null || channel.owner() == null ? 0 : (int)Math.min(Integer.MAX_VALUE - 1L, channel.owner().parallelism()); }
    public void dirty() {
        if (tasks == null || !tasks.valid() || tasks.core.isRemoved()) return;
        var core = tasks.core;
        if (core.getLevel() instanceof net.minecraft.server.level.ServerLevel server) {
            var pos = core.getBlockPos();
            var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            // A late cancellation of an unloaded/retired link must not reload the core chunk just to dirty it.
            if (chunk != null && chunk.getBlockEntity(pos) == core) core.setChanged();
        }
    }
    /** Reserve before AE extracts materials. Failed submissions release this exact reservation. */
    public ICraftingSubmitResult begin(IGrid grid, ICraftingPlan plan) {
        if (!active() || node().getGrid() != grid) return CraftingSubmitResult.CPU_OFFLINE;
        if (tasks != null || submitting) return CraftingSubmitResult.CPU_BUSY;
        var channel = channel();
        if (!channel.reserve(this, plan.bytes())) return CraftingSubmitResult.CPU_TOO_SMALL;
        submitting = true; return null;
    }
    public boolean submitting() { return submitting; }
    /** AE2's native failed-extraction helper clears leftovers even if network storage refuses the rollback. */
    public GenericStack extractInitialItems(ICraftingPlan plan, IGrid grid, ListCraftingInventory inventory, IActionSource source) {
        var storage = grid.getStorageService().getInventory();
        for (var entry : plan.usedItems()) {
            long wanted = entry.getLongValue();
            long taken = storage.extract(entry.getKey(), wanted, Actionable.MODULATE, source);
            inventory.insert(entry.getKey(), taken, Actionable.MODULATE);
            if (taken < wanted) {
                cluster.craftingLogic.storeItems(); // Removes only amounts actually accepted; retains any remainder on this core.
                return new GenericStack(entry.getKey(), wanted - taken);
            }
        }
        return null;
    }
    public ICraftingSubmitResult submit(IGrid grid, ICraftingPlan plan, IActionSource source, ICraftingRequester requester) {
        var rejected = begin(grid, plan);
        if (rejected != null) return rejected;
        try {
            var result = cluster.craftingLogic.trySubmitJob(grid, plan, source, requester);
            submitted(result); return result;
        } catch (RuntimeException | Error failure) {
            // Keep any materials already extracted by AE attached to the core, even if a third-party hook throws.
            boolean ownsMaterials = cluster.craftingLogic.hasJob() || !cluster.craftingLogic.getInventory().list.isEmpty();
            submitted(ownsMaterials ? CraftingSubmitResult.successful(null) : CraftingSubmitResult.CPU_OFFLINE);
            throw failure;
        }
    }
    public void submitted(ICraftingSubmitResult result) {
        if (!submitting) return;
        submitting = false;
        var channel = idleChannel;
        if (!result.successful() && !cluster.craftingLogic.hasJob() && cluster.craftingLogic.getInventory().list.isEmpty()) {
            tasks.remove(this); tasks = null; bytes = 0; hyper = false;
        }
        else channel.submitted(this);
        dirty(); channel.requestCpuUpdate();
    }
    public void afterTick() {
        if (!retired && tasks != null && !submitting && !cluster.craftingLogic.hasJob() && cluster.craftingLogic.getInventory().list.isEmpty()) {
            var channel = channel(); var previous = tasks; previous.remove(this); retire();
            if (channel != null) channel.requestCpuUpdate();
        }
    }
    public void retire() { if (retired) return; retired = true; cluster.destroy(); }
}
