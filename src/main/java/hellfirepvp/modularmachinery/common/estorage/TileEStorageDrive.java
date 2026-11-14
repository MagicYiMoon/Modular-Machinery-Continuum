/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO EStorageCellDrive, KasumiNova and contributors, revision b37fbb53.
 */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** One matrix, one runtime owner, and one revocable storage access. No owner is trusted from NBT. */
public final class TileEStorageDrive extends TileEntityBase {
    private long capabilityGeneration;
    private boolean capabilitiesActive = true;
    private net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler> matrixSlot = createMatrixSlot();
    private net.minecraftforge.common.util.LazyOptional<net.minecraftforge.items.IItemHandler> createMatrixSlot() {
        long generation = capabilityGeneration;
        return net.minecraftforge.common.util.LazyOptional.of(() -> new MatrixSlot(generation));
    }
    @Override public <T> net.minecraftforge.common.util.LazyOptional<T> getCapability(
            net.minecraftforge.common.capabilities.Capability<T> capability, net.minecraft.core.Direction side) {
        if (capability == net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER)
            return capabilitiesActive && !isRemoved() ? matrixSlot.cast() : net.minecraftforge.common.util.LazyOptional.empty();
        return super.getCapability(capability, side);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); capabilitiesActive = false; capabilityGeneration++; matrixSlot.invalidate();
    }
    @Override public void reviveCaps() {
        super.reviveCaps(); capabilitiesActive = true; matrixSlot = createMatrixSlot();
    }
    /** Upstream exposes the one physical matrix slot on every side, not the matrix contents. */
    private final class MatrixSlot implements net.minecraftforge.items.IItemHandler {
        private final long generation;
        private MatrixSlot(long generation) { this.generation = generation; }
        private boolean valid() {
            return capabilitiesActive && generation == capabilityGeneration && !isRemoved()
                    && level != null && !level.isClientSide && level.hasChunkAt(worldPosition)
                    && level.getBlockEntity(worldPosition) == TileEStorageDrive.this;
        }
        public int getSlots() { return 1; }
        public int getSlotLimit(int slot) { return slot == 0 ? 1 : 0; }
        public boolean isItemValid(int slot, ItemStack stack) { return slot == 0 && stack.getItem() instanceof ItemEStorageCell; }
        public ItemStack getStackInSlot(int slot) { return slot == 0 && valid() ? matrixSnapshot() : ItemStack.EMPTY; }
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!valid() || !isItemValid(slot, stack) || stack.isEmpty() || hasMatrix()) return stack;
            if (!simulate && !insertMatrix(stack)) return stack;
            return stack.copyWithCount(stack.getCount() - 1);
        }
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 0 || amount <= 0 || !valid() || !hasMatrix()) return ItemStack.EMPTY;
            return simulate ? matrixSnapshot() : takeMatrix();
        }
    }
    private ItemStack matrix = ItemStack.EMPTY;
    private TileEStorageController owner;
    private EStorageMatrixAccess access;
    private boolean invalidData;
    private long networkRevision;
    private EStorageMatrixSummary summary;
    private boolean summaryChecked;
    private long lastWriteTick = Long.MIN_VALUE, nextVisualUpdate;
    private EStorageDriveAppearance sentAppearance = EStorageDriveAppearance.EMPTY;
    private volatile EStorageDriveAppearance clientAppearance = EStorageDriveAppearance.EMPTY;
    private void inventoryChanged() {
        summary = null; summaryChecked = false; if (level != null) lastWriteTick = level.getGameTime(); setChanged();
    }
    public EStorageDriveAppearance appearance() {
        if (level != null && level.isClientSide) return clientAppearance;
        if (!(matrix.getItem() instanceof ItemEStorageCell cell)) return EStorageDriveAppearance.EMPTY;
        var data = summary();
        if (data == null && cell.specification().backendAvailable()) return EStorageDriveAppearance.EMPTY;
        return EStorageDriveAppearance.of(cell.specification(), data,
                level != null && lastWriteTick != Long.MIN_VALUE && level.getGameTime() - lastWriteTick < 40);
    }
    @Override public void tick() {
        if (level == null || level.isClientSide || isRemoved() || level.getGameTime() < nextVisualUpdate) return;
        nextVisualUpdate = level.getGameTime() + 5;
        var current = appearance();
        if (!sentAppearance.equals(current)) { sentAppearance = current; markNoUpdateSync(); }
    }
    @Override public net.minecraftforge.client.model.data.ModelData getModelData() {
        return net.minecraftforge.client.model.data.ModelData.builder().with(EStorageDriveAppearance.DATA, appearance()).build();
    }
    private void receiveAppearance(CompoundTag tag) {
        var current = EStorageDriveAppearance.decode(tag.getInt("Appearance"));
        if (clientAppearance.equals(current)) return;
        clientAppearance = current;
        requestModelDataUpdate();
        if (level != null && level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override public void handleUpdateTag(CompoundTag tag) { receiveAppearance(tag); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) receiveAppearance(packet.getTag());
    }
    /** Read-only statistics also work while the ME network is unpowered. Cache until contents change. */
    public EStorageMatrixSummary summary() {
        if (!(matrix.getItem() instanceof ItemEStorageCell)) return null;
        if (access != null && access.isOpen()) return new EStorageMatrixSummary(
                ((ItemEStorageCell) matrix.getItem()).specification(), access.storedTypes(), access.usedBytes(), access.storedAmount());
        if (!summaryChecked) {
            try { summary = EStorageMatrixAccess.inspect(matrix); }
            catch (IllegalArgumentException invalid) { summary = null; }
            summaryChecked = true;
        }
        return summary;
    }
    public long networkRevision() { return networkRevision; }
    public double matrixIdleDrain() {
        return isTierSupported() && !invalidData && matrix.getItem() instanceof ItemEStorageCell cell
                && cell.specification().backendAvailable() ? cell.specification().idleDrain() : 0;
    }
    private void changedMatrix() { summary = null; summaryChecked = false; networkRevision++; if (owner != null) owner.storageChanged(); }
    public void revokeStorageAccess() { closeAccess(); }
    public TileEStorageDrive(BlockPos pos, BlockState state) { super(EStorageRegistration.DRIVE_TILE, pos, state); }
    public boolean hasMatrix() { return !matrix.isEmpty(); }
    public ItemStack matrixSnapshot() { flush(); return matrix.copy(); }
    public boolean insertMatrix(ItemStack input) {
        if (level != null && level.isClientSide || !matrix.isEmpty() || input.isEmpty()
                || !(input.getItem() instanceof ItemEStorageCell)) return false;
        matrix = input.copyWithCount(1); invalidData = false;
        lastWriteTick = Long.MIN_VALUE;
        changedMatrix();
        updateOccupied(); setChanged(); return true;
    }
    public ItemStack takeMatrix() {
        if (level != null && level.isClientSide) return ItemStack.EMPTY;
        closeAccess();
        ItemStack result = matrix; matrix = ItemStack.EMPTY; invalidData = false;
        lastWriteTick = Long.MIN_VALUE;
        changedMatrix();
        updateOccupied(); setChanged(); return result;
    }
    public boolean canBind(TileEStorageController controller) {
        return owner == null || owner == controller || owner.isRemoved();
    }
    public boolean bind(TileEStorageController controller) {
        if (level == null || level.isClientSide || controller.getLevel() != level || !canBind(controller)) return false;
        if (owner != controller) { closeAccess(); owner = controller; changedMatrix(); }
        return true;
    }
    public void unbind(TileEStorageController controller) { if (owner == controller) unbind(); }
    public void unbind() {
        closeAccess();
        changedMatrix();
        TileEStorageController previous = owner; owner = null;
        if (previous != null && !previous.isRemoved()) previous.requestStructureCheck();
    }
    public boolean isTierSupported() {
        return owner != null && matrix.getItem() instanceof ItemEStorageCell cell
                && cell.specification().tier().supportedBy(owner.tier());
    }
    public EStorageMatrixAccess storageAccess() {
        if (!mayAccess()) { closeAccess(); return null; }
        if (access != null && !access.isOpen()) access = null;
        if (access == null && !invalidData) {
            try { access = EStorageMatrixAccess.open(matrix, this::inventoryChanged, this::mayAccess); }
            catch (IllegalArgumentException ex) {
                invalidData = true;
                Modularmachinery.LOG.error("[MMC][ES] Matrix at {} cannot be opened; preserving its NBT", worldPosition, ex);
            }
        }
        return access;
    }
    private boolean mayAccess() {
        return level != null && !level.isClientSide && !isRemoved() && owner != null
                && owner.isStorageAvailable() && isTierSupported();
    }
    public boolean hasInvalidData() { return invalidData; }
    private void flush() { if (access != null) access.persist(); }
    private void closeAccess() { if (access != null) { access.close(); access = null; } }
    private void updateOccupied() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || isRemoved()) return;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk == null || chunk.getBlockEntity(worldPosition) != this) return;
        var state = chunk.getBlockState(worldPosition);
        // Taking the matrix during onRemove must not restore the cached drive over its replacement.
        if (state.getBlock() == getBlockState().getBlock() && state.hasProperty(BlockEStorageDrive.OCCUPIED)
                && state.getValue(BlockEStorageDrive.OCCUPIED) != hasMatrix())
            level.setBlock(worldPosition, state.setValue(BlockEStorageDrive.OCCUPIED, hasMatrix()), Block.UPDATE_CLIENTS);
    }
    @Override public void onLoad() { super.onLoad(); if (!capabilitiesActive) reviveCaps(); updateOccupied(); }
    @Override public void load(CompoundTag tag) {
        closeAccess(); owner = null; invalidData = false; summary = null; summaryChecked = false;
        lastWriteTick = Long.MIN_VALUE; nextVisualUpdate = 0; sentAppearance = EStorageDriveAppearance.EMPTY;
        networkRevision++;
        super.load(tag);
        matrix = tag.contains("Matrix", Tag.TAG_COMPOUND) ? ItemStack.of(tag.getCompound("Matrix")) : ItemStack.EMPTY;
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        flush(); super.saveAdditional(tag);
        if (!matrix.isEmpty()) tag.put("Matrix", matrix.save(new CompoundTag()));
    }
    // The chunk packet must not broadcast a possibly large private matrix inventory to all players.
    @Override public CompoundTag getUpdateTag() { var tag = new CompoundTag(); tag.putInt("Appearance", appearance().encode()); return tag; }
    @Override public void setRemoved() { unbind(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { unbind(); invalidateCaps(); super.onChunkUnloaded(); }
}
