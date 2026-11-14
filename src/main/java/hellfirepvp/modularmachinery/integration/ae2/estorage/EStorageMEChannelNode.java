/* SPDX-License-Identifier: GPL-3.0-only
 * NECO EStorageMEChannel port, KasumiNova and contributors, b37fbb53.
 * Uses the public AE2 15 node/storage provider APIs instead of 1.12 cell-array events.
 */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.config.Actionable;
import appeng.api.config.AccessRestriction;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.events.GridPowerStorageStateChanged;
import appeng.api.networking.*;
import appeng.api.networking.security.*;
import appeng.api.stacks.*;
import appeng.api.storage.*;
import appeng.api.util.AECableType;
import hellfirepvp.modularmachinery.common.estorage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** One channel per formed ES. Every mounted view has a node and matrix generation lease. */
public final class EStorageMEChannelNode extends TileEStorageMEChannel implements IInWorldGridNodeHost, IActionHost, IStorageProvider, IAEPowerStorage {
    private IManagedGridNode node;
    private boolean storageDirty = true;
    private boolean wasActive;
    private boolean disconnecting;
    private double configuredDrain = -1;
    private Integer placingPlayer;
    private long mountEpoch;
    private long powerRevision = -1;
    private boolean powerDirty;
    private double registeredCapacity;
    @Override public void requestPowerUpdate() { powerDirty = true; }
    @Override public double getAEMaxPower() { return node != null ? registeredCapacity : owner == null ? 0 : owner.energyCapacity(); }
    @Override public double getAECurrentPower() { return owner == null ? 0 : owner.storedEnergy(); }
    @Override public boolean isAEPublicPowerStorage() { return true; }
    @Override public AccessRestriction getPowerFlow() { return AccessRestriction.READ_WRITE; }
    @Override public double injectAEPower(double amount, Actionable mode) {
        return owner == null ? amount : owner.injectPower(amount, mode == Actionable.SIMULATE);
    }
    @Override public double extractAEPower(double amount, Actionable mode, PowerMultiplier multiplier) {
        if (owner == null || !Double.isFinite(amount) || amount <= 0) return 0;
        return multiplier.divide(owner.extractPower(multiplier.multiply(amount), mode == Actionable.SIMULATE));
    }
    private final IGridNodeListener<EStorageMEChannelNode> listener = new IGridNodeListener<>() {
        @Override public void onSaveChanges(EStorageMEChannelNode host, IGridNode gridNode) { host.setChanged(); }
        @Override public void onStateChanged(EStorageMEChannelNode host, IGridNode gridNode, State reason) { host.storageDirty = true; }
        @Override public void onGridChanged(EStorageMEChannelNode host, IGridNode gridNode) { host.storageDirty = true; }
    };
    public EStorageMEChannelNode(BlockPos pos, BlockState state) { super(pos, state); }
    @Override public void setPlacingPlayer(Player player) {
        placingPlayer = appeng.api.features.IPlayerRegistry.getMapping(player.level()).getPlayerId(player.getUUID());
        nodeData.putInt("PlacingPlayer", placingPlayer);
        if (node != null) node.setOwningPlayer(player);
        setChanged();
    }
    @Override public void requestStorageRefresh() { storageDirty = true; }
    @Override public boolean isNetworkActive() {
        return !disconnecting && owner != null && owner.isOperational() && node != null && node.isActive();
    }
    @Override public void tick() {
        if (level == null || level.isClientSide) return;
        if (owner != null && owner.isStructureCheckRequested()) owner.refreshStructure();
        if (owner == null || !owner.isOperational()) { disconnect(); return; }
        // AE caches public power capacity when a node joins. Replace the node when
        // the battery roster changes so grid totals cannot retain a removed battery.
        if (node != null && powerRevision != owner.powerRevision()) disconnect();
        if (node == null) {
            powerRevision = owner.powerRevision();
            registeredCapacity = owner.energyCapacity();
            node = GridHelper.createManagedNode(this, listener).setTagName("channel")
                    .setInWorldNode(true).setFlags(GridFlags.REQUIRE_CHANNEL, GridFlags.DENSE_CAPACITY)
                    .setVisualRepresentation(owner.getBlockState().getBlock())
                    .setIdlePowerUsage(idleDrain()).addService(IStorageProvider.class, this)
                    .addService(IAEPowerStorage.class, this);
            node.loadFromNBT(nodeData);
            if (placingPlayer != null) node.setOwningPlayerId(placingPlayer);
            configuredDrain = idleDrain();
            node.create(level, worldPosition);
            storageDirty = true;
        }
        double drain = idleDrain();
        if (configuredDrain != drain) { node.setIdlePowerUsage(drain); configuredDrain = drain; }
        boolean active = isNetworkActive();
        if (active != wasActive || storageDirty) {
            mountEpoch++;
            wasActive = active; storageDirty = false;
            if (!active && owner != null) owner.revokeStorageAccess();
            IStorageProvider.requestUpdate(node);
        }
        if (powerDirty) {
            powerDirty = false;
            // Dispatch outside AE's inject/extract iterators. Both sets are idempotent;
            // this also restores availability after a temporary structure-check gate.
            node.ifPresent(grid -> {
                grid.postEvent(new GridPowerStorageStateChanged(this, GridPowerStorageStateChanged.PowerEventType.RECEIVE_POWER));
                grid.postEvent(new GridPowerStorageStateChanged(this, GridPowerStorageStateChanged.PowerEventType.PROVIDE_POWER));
            });
        }
    }
    @Override protected void disconnect() {
        if (node == null || disconnecting) return;
        disconnecting = true;
        mountEpoch++;
        try {
            node.saveToNBT(nodeData);
            node.destroy(); node = null; wasActive = false; configuredDrain = -1; storageDirty = true;
        } finally { disconnecting = false; }
    }
    @Override public IGridNode getGridNode(Direction side) { return node == null ? null : node.getNode(); }
    @Override public IGridNode getActionableNode() { return getGridNode(null); }
    @Override public AECableType getCableConnectionType(Direction side) { return AECableType.DENSE_SMART; }
    public IManagedGridNode managedNode() { return node; }
    @Override public void mountInventories(IStorageMounts mounts) {
        if (!isNetworkActive()) return;
        for (TileEStorageDrive drive : owner.drives()) {
            if (drive.isTierSupported() && drive.storageAccess() != null)
                mounts.mount(new MountedMatrix(drive, node, drive.networkRevision(), mountEpoch));
        }
    }
    @Override public void load(CompoundTag tag) { super.load(tag); placingPlayer = nodeData.contains("PlacingPlayer") ? nodeData.getInt("PlacingPlayer") : null; }
    @Override protected void saveAdditional(CompoundTag tag) {
        if (node != null) node.saveToNBT(nodeData);
        super.saveAdditional(tag);
    }
    private final class MountedMatrix implements MEStorage {
        private final TileEStorageDrive drive;
        private final IManagedGridNode leaseNode;
        private final long revision;
        private final long epoch;
        MountedMatrix(TileEStorageDrive drive, IManagedGridNode leaseNode, long revision, long epoch) {
            this.drive = drive; this.leaseNode = leaseNode; this.revision = revision; this.epoch = epoch;
        }
        private EStorageCellInventory access() {
            return node == leaseNode && !storageDirty && mountEpoch == epoch && isNetworkActive() && drive.networkRevision() == revision
                    && owner.owns(drive) ? (EStorageCellInventory) drive.storageAccess() : null;
        }
        @Override public long insert(AEKey key, long amount, Actionable mode, IActionSource source) {
            var data = access(); return data == null ? 0 : data.insert(key, amount, mode, source);
        }
        @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
            var data = access(); return data == null ? 0 : data.extract(key, amount, mode, source);
        }
        @Override public void getAvailableStacks(KeyCounter out) { var data = access(); if (data != null) data.getAvailableStacks(out); }
        @Override public boolean isPreferredStorageFor(AEKey key, IActionSource source) {
            var data = access(); return data != null && data.isPreferredStorageFor(key, source);
        }
        @Override public Component getDescription() { return Component.translatable("storage.modularmachinery.estorage_matrix"); }
    }
}
