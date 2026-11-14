/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO EPartController / EStorageController, KasumiNova and contributors.
 * Structure matching uses MMC's native matcher; no ordinary recipe engine is ticked here.
 */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TileEStorageController extends TileController {
    /** NECO EStorageController.HIDE_POS_LIST: fixed neighbours, excluding the controller. */
    public java.util.List<BlockPos> modelHiddenPositions() {
        var result = new java.util.ArrayList<BlockPos>(11);
        var facing = getBlockState().getValue(BlockEStorageController.FACING);
        for (int x = 0; x <= 1; x++) for (int z = 0; z <= 1; z++) for (int y = -1; y <= 1; y++) {
            if (x == 0 && y == 0 && z == 0) continue;
            result.add(StructureMatcher.toWorldOffset(worldPosition, facing, x, y, z));
        }
        return java.util.List.copyOf(result);
    }
    private void refreshClientHiding(boolean remove) {
        if (level != null && level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> { if (remove) hellfirepvp.modularmachinery.client.world.BlockModelHider.remove(this);
                    else hellfirepvp.modularmachinery.client.world.BlockModelHider.update(this); });
    }
    private final Map<BlockPos, TileEStorageDrive> drives = new LinkedHashMap<>();
    private final Map<BlockPos, TileEStorageEnergyCell> energyCells = new LinkedHashMap<>();
    private long powerRevision;
    public long powerRevision() { return powerRevision; }
    public double storedEnergy() { return isOperational() ? energyCells.values().stream().mapToDouble(TileEStorageEnergyCell::stored).sum() : 0; }
    public double energyCapacity() { return isOperational() ? energyCells.values().stream().mapToDouble(TileEStorageEnergyCell::capacity).sum() : 0; }
    /** Source priority: charge the least stored AE first, discharge the most stored AE first. */
    public double injectPower(double amount, boolean simulate) {
        if (!isOperational() || !Double.isFinite(amount) || amount <= 0) return amount;
        double remaining = amount;
        var ordered = energyCells.values().stream().sorted(java.util.Comparator.comparingDouble(TileEStorageEnergyCell::stored)).toList();
        for (var cell : ordered) {
            remaining = cell.inject(remaining, simulate);
            if (remaining <= 0) break;
        }
        return remaining;
    }
    public double extractPower(double amount, boolean simulate) {
        if (!isOperational() || !Double.isFinite(amount) || amount <= 0) return 0;
        double extracted = 0;
        var ordered = energyCells.values().stream().sorted(java.util.Comparator.comparingDouble(TileEStorageEnergyCell::stored).reversed()).toList();
        for (var cell : ordered) {
            extracted += cell.extract(amount - extracted, simulate);
            if (extracted >= amount) break;
        }
        return extracted;
    }
    private TileEStorageMEChannel channel;
    private boolean assembled;
    private long nextCheck;
    private long definitionRevision = -1;
    public TileEStorageController(BlockPos pos, BlockState state) { super(EStorageRegistration.CONTROLLER_TILE, pos, state); }
    public int tier() { return ((BlockEStorageController) getBlockState().getBlock()).tier(); }
    public boolean isAssembled() { return assembled; }
    public int driveCount() { return drives.size(); }
    public java.util.List<TileEStorageDrive> drives() { return java.util.List.copyOf(drives.values()); }
    public boolean owns(TileEStorageDrive drive) { return drives.get(drive.getBlockPos()) == drive; }
    public TileEStorageMEChannel channel() { return channel; }
    public boolean isStorageAvailable() { return isOperational() && (channel == null || channel.isNetworkActive()); }
    public double storageIdleDrain() {
        double drain = 64;
        for (TileEStorageDrive drive : drives.values()) drain += drive.matrixIdleDrain();
        return drain;
    }
    public void storageChanged() { if (channel != null) channel.requestStorageRefresh(); }
    public void revokeStorageAccess() { for (TileEStorageDrive drive : drives.values()) drive.revokeStorageAccess(); }
    public boolean isOperational() {
        return assembled && isFormed() && !isRemoved() && !isRedstoneStopped()
                && !isStructureCheckRequested() && isStructureChunkLoaded()
                && definitionRevision == MachineReloadListener.getServerDefinitionRevision();
    }
    @Override public void tick() {
        if (!(level instanceof ServerLevel)) return;
        if (assembled && !isStructureChunkLoaded()) { disassemble(); setFormed(false); }
        if (level.getGameTime() >= nextCheck || isStructureCheckRequested()
                || definitionRevision != MachineReloadListener.getServerDefinitionRevision()) refreshStructure();
        // Revoke retained backend references as soon as a known invalidation or redstone stop occurs.
        if (!isOperational()) for (TileEStorageDrive drive : drives.values()) drive.storageAccess();
    }
    public void refreshStructure() {
        if (!(level instanceof ServerLevel server) || isRemoved()) return;
        nextCheck = level.getGameTime() + 40;
        definitionRevision = MachineReloadListener.getServerDefinitionRevision();
        acknowledgeStructureCheck();
        flushPendingComponentUnbinds();
        MachineLoader loader = MachineReloadListener.getLoader();
        DynamicMachine machine = loader.findByName(EStorageRegistration.machineName(tier()));
        if (machine == null || sharedController()) { disassemble(); setFormed(false); return; }
        Direction facing = getBlockState().getValue(BlockEStorageController.FACING);
        MachineFormation.Attempt attempt = MachineFormation.tryForm(server, worldPosition, facing, machine, loader);
        if (!attempt.formed()) { disassemble(); setFormed(false); return; }
        Map<BlockPos, TileEStorageDrive> found = new LinkedHashMap<>();
        Map<BlockPos, TileEStorageEnergyCell> foundEnergy = new LinkedHashMap<>();
        TileEStorageMEChannel foundChannel = null;
        for (var part : getFormedPattern(machine).getParts()) {
            BlockPos pos = StructureMatcher.toWorldOffset(worldPosition, facing, part.x(), part.y(), part.z());
            if (!server.hasChunkAt(pos)) { disassemble(); setFormed(false); return; }
            if (server.getBlockEntity(pos) instanceof TileEStorageDrive drive) {
                if (!drive.canBind(this)) { disassemble(); setFormed(false); return; }
                found.put(pos, drive);
            } else if (server.getBlockEntity(pos) instanceof TileEStorageMEChannel candidate) {
                // One source ES exposes one channel. Multiple channels must not duplicate a matrix
                // across different ME grids or silently pick whichever part was scanned last.
                if (foundChannel != null && foundChannel != candidate || !candidate.canBind(this)) {
                    disassemble(); setFormed(false); return;
                }
                foundChannel = candidate;
            } else if (server.getBlockEntity(pos) instanceof TileEStorageEnergyCell cell) {
                if (!cell.canBind(this)) { disassemble(); setFormed(false); return; }
                foundEnergy.put(pos, cell);
            }
        }
        boolean mountsChanged = channel != foundChannel || !drives.equals(found);
        if (!energyCells.equals(foundEnergy)) {
            for (var entry : energyCells.entrySet()) if (foundEnergy.get(entry.getKey()) != entry.getValue()) entry.getValue().unbind(this);
            energyCells.clear(); energyCells.putAll(foundEnergy); powerRevision++;
        }
        if (channel != foundChannel) {
            if (channel != null) channel.unbind(this);
            channel = foundChannel;
            revokeStorageAccess();
        }
        for (var entry : drives.entrySet()) if (found.get(entry.getKey()) != entry.getValue()) entry.getValue().unbind(this);
        drives.clear(); drives.putAll(found);
        for (TileEStorageDrive drive : drives.values()) if (!drive.bind(this)) {
            disassemble(); setFormed(false); return;
        }
        if (channel != null && !channel.bind(this)) { disassemble(); setFormed(false); return; }
        for (var cell : energyCells.values()) if (!cell.bind(this)) { disassemble(); setFormed(false); return; }
        assembled = true;
        setBoundComponentCount(attempt.boundCount() + drives.size() + energyCells.size() + (channel == null ? 0 : 1));
        setFormed(true);
        acknowledgeStructureCheck();
        if (mountsChanged) storageChanged();
        if (channel != null) channel.requestPowerUpdate();
    }
    private boolean sharedController() {
        for (BlockPos pos : new BlockPos[]{worldPosition.above(2), worldPosition.below(2)})
            if (level.hasChunkAt(pos) && level.getBlockState(pos).getBlock() == getBlockState().getBlock()) return true;
        return false;
    }
    private void disassemble() {
        assembled = false;
        if (channel != null) { channel.unbind(this); channel = null; }
        for (TileEStorageDrive drive : drives.values()) drive.unbind(this);
        drives.clear();
        for (var cell : energyCells.values()) cell.unbind(this);
        if (!energyCells.isEmpty()) powerRevision++;
        energyCells.clear();
    }
    @Override public void setFormed(boolean value) { if (!value) disassemble(); super.setFormed(value); }
    @Override public void load(CompoundTag tag) { disassemble(); super.load(tag); assembled = false; nextCheck = 0; definitionRevision = -1; }
    @Override public void onLoad() { super.onLoad(); refreshClientHiding(false); }
    @Override public void setRemoved() { disassemble(); super.setRemoved(); refreshClientHiding(true); }
    @Override public void onChunkUnloaded() { disassemble(); super.onChunkUnloaded(); refreshClientHiding(true); }
}
