/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO ECalculatorMEChannel. */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import appeng.api.networking.*;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.security.IActionHost;
import appeng.api.util.AECableType;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** A formed EC publishes real AE crafting clusters; tasks remain owned by their physical cores. */
public final class ECalculatorMEChannelNode extends TileECalculatorMEChannel implements IInWorldGridNodeHost, IActionHost {
    private IManagedGridNode node;
    private Integer placingPlayer;
    private long epoch, revision = -1;
    private boolean dirty = true, wasActive, disconnecting;
    private ECalculatorCpuHost idle;
    private final IGridNodeListener<ECalculatorMEChannelNode> listener = new IGridNodeListener<>() {
        @Override public void onSaveChanges(ECalculatorMEChannelNode host, IGridNode gridNode) { host.setChanged(); }
        @Override public void onStateChanged(ECalculatorMEChannelNode host, IGridNode gridNode, State reason) { host.requestCpuUpdate(); }
        @Override public void onGridChanged(ECalculatorMEChannelNode host, IGridNode gridNode) { host.requestCpuUpdate(); }
    };
    public ECalculatorMEChannelNode(BlockPos pos, BlockState state) { super(pos, state); }
    public long epoch() { return epoch; }
    public void requestCpuUpdate() { dirty = true; }
    @Override public void setPlacingPlayer(Player player) {
        placingPlayer = appeng.api.features.IPlayerRegistry.getMapping(player.level()).getPlayerId(player.getUUID());
        nodeData.putInt("PlacingPlayer", placingPlayer);
        if (node != null) node.setOwningPlayer(player);
        setChanged();
    }
    @Override public boolean isNetworkActive() {
        return !disconnecting && isActivePart() && node != null && node.isActive();
    }
    public List<ECalculatorTasks> cores() {
        if (owner() == null) return List.of();
        var result = new ArrayList<ECalculatorTasks>();
        for (var part : owner().parts()) if (part.isActivePart() && part.cpuAccess() instanceof ECalculatorTasks tasks) result.add(tasks);
        return result;
    }
    public long availableBytes() {
        if (!isNetworkActive()) return 0;
        long used = 0;
        try {
            for (var tasks : cores()) {
                if (!tasks.valid()) return 0; // Unreadable saved tasks must never advertise their memory as free.
                used = Math.addExact(used, tasks.allocated());
            }
        } catch (ArithmeticException overflow) { return 0; }
        return Math.max(0, owner().totalBytes() - used);
    }
    private boolean canOfferIdle() {
        if (!isNetworkActive()) return false;
        long available = availableBytes();
        return available > 0 && available >= owner().totalBytes() * 0.1F
                && cores().stream().anyMatch(c -> c.hasSlot(false) || c.hasSlot(true));
    }
    boolean reserve(ECalculatorCpuHost cpu, long requested) {
        if (cpu != idle || !canOfferIdle() || requested <= 0) return false;
        for (boolean hyper : new boolean[]{false, true}) {
            long reserved;
            try { reserved = Math.addExact(requested, hyper ? (long)(requested * 0.1F) : 0); }
            catch (ArithmeticException overflow) { continue; }
            if (reserved > availableBytes()) continue;
            for (var tasks : cores()) if (tasks.hasSlot(hyper)) {
                cpu.tasks = tasks; cpu.bytes = reserved; cpu.hyper = hyper; tasks.add(cpu); return true;
            }
        }
        return false;
    }
    void submitted(ECalculatorCpuHost cpu) { if (idle == cpu) idle = null; dirty = true; }
    public List<ECalculatorCpuHost> publishedCpus() {
        if (!isNetworkActive()) return List.of();
        return trackedCpus();
    }
    /** Keep occupied CPUs in AE's internal roster while unpowered or waiting for channels.
     * CraftingService filters them from the public list; removing them here would cancel requester links. */
    public List<ECalculatorCpuHost> trackedCpus() {
        var result = new ArrayList<ECalculatorCpuHost>();
        for (var tasks : cores()) if (tasks.valid()) result.addAll(tasks.cpus());
        if (idle != null && isNetworkActive() && canOfferIdle()) result.add(idle);
        return List.copyOf(result);
    }
    @Override public void tick() {
        if (level == null || level.isClientSide) return;
        if (owner() != null && owner().isStructureCheckRequested()) owner().refreshStructure();
        if (!isActivePart()) { disconnect(); return; }
        if (node == null) {
            node = GridHelper.createManagedNode(this, listener).setTagName("channel")
                    .setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
                    .setVisualRepresentation(owner().getBlockState().getBlock()).setIdlePowerUsage(1);
            node.loadFromNBT(nodeData);
            if (placingPlayer != null) node.setOwningPlayerId(placingPlayer);
            node.create(level, worldPosition); dirty = true;
        }
        boolean active = isNetworkActive();
        if (active != wasActive || revision != owner().componentRevision()) {
            if (idle != null) { idle.retire(); idle = null; }
            epoch++; dirty = true; wasActive = active; revision = owner().componentRevision();
        }
        if (idle != null && !canOfferIdle()) { idle.retire(); idle = null; dirty = true; }
        if (idle == null && canOfferIdle()) { idle = ECalculatorCpuHost.idle(this); dirty = true; }
        // Defer changes until the block tick: CraftingService may currently be iterating its CPU set.
        if (dirty) { dirty = false; node.ifPresent(grid -> grid.postEvent(new GridCraftingCpuChange(node.getNode()))); }
    }
    @Override protected void disconnect() {
        if (disconnecting) return;
        disconnecting = true;
        try {
            epoch++; if (idle != null) { idle.retire(); idle = null; }
            if (node != null) { node.saveToNBT(nodeData); node.destroy(); node = null; }
            dirty = true; wasActive = false; revision = -1;
        } finally { disconnecting = false; }
    }
    @Override public IGridNode getGridNode(Direction side) { return node == null ? null : node.getNode(); }
    @Override public IGridNode getActionableNode() { return getGridNode(null); }
    @Override public AECableType getCableConnectionType(Direction side) { return AECableType.DENSE_SMART; }
    @Override public void load(CompoundTag tag) { super.load(tag); placingPlayer = nodeData.contains("PlacingPlayer") ? nodeData.getInt("PlacingPlayer") : null; }
    @Override protected void saveAdditional(CompoundTag tag) { if (node != null) node.saveToNBT(nodeData); super.saveAdditional(tag); }
}
