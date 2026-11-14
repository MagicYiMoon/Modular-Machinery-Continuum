/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO EStorageEnergyCell, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** AE units, independent of the optional AE implementation. Capacity always comes from the block. */
public final class TileEStorageEnergyCell extends TileEntityBase {
    private double stored;
    private TileEStorageController owner;
    public TileEStorageEnergyCell(BlockPos pos, BlockState state) { super(EStorageRegistration.ENERGY_TILE, pos, state); }
    public double capacity() { return ((BlockEStorageEnergyCell) getBlockState().getBlock()).capacity(); }
    public double stored() { return stored; }
    public boolean canBind(TileEStorageController host) { return owner == null || owner == host || owner.isRemoved(); }
    public boolean bind(TileEStorageController host) {
        if (level == null || level.isClientSide || host.getLevel() != level || !canBind(host)) return false;
        owner = host; return true;
    }
    public void unbind(TileEStorageController host) { if (owner == host) unbind(); }
    private void unbind() {
        var previous = owner; owner = null;
        if (previous != null && !previous.isRemoved()) previous.requestStructureCheck();
    }
    public double inject(double amount, boolean simulate) {
        if (!Double.isFinite(amount) || amount <= 0 || level != null && level.isClientSide) return amount;
        double accepted = Math.min(amount, capacity() - stored);
        if (!simulate && accepted > 0) change(stored + accepted);
        return amount - accepted;
    }
    public double extract(double amount, boolean simulate) {
        if (!Double.isFinite(amount) || amount <= 0 || level != null && level.isClientSide) return 0;
        double extracted = Math.min(amount, stored);
        if (!simulate && extracted > 0) change(stored - extracted);
        return extracted;
    }
    private void change(double value) {
        stored = value; setChanged();
        if (owner != null && owner.channel() != null) owner.channel().requestPowerUpdate();
    }
    public CompoundTag energyTag() {
        var tag = new CompoundTag(); tag.putDouble("energyStored", stored); return tag;
    }
    public void loadEnergy(CompoundTag tag) {
        double value = tag.getDouble("energyStored");
        change(Double.isFinite(value) ? Math.max(0, Math.min(capacity(), value)) : 0);
        updateStatus();
    }
    public ItemStack takeDrop() {
        var result = new ItemStack(getBlockState().getBlock());
        result.setTag(energyTag()); change(0); unbind(); return result;
    }
    private void updateStatus() {
        if (level == null || level.isClientSide || isRemoved()) return;
        var state = getBlockState();
        var status = BlockEStorageEnergyCell.Status.fromFraction(stored / capacity());
        if (state.getValue(BlockEStorageEnergyCell.STATUS) != status)
            level.setBlock(worldPosition, state.setValue(BlockEStorageEnergyCell.STATUS, status), Block.UPDATE_CLIENTS);
    }
    @Override public void tick() { if (level != null && level.getGameTime() % 5 == 0) updateStatus(); }
    @Override public void load(CompoundTag tag) { unbind(); super.load(tag); loadEnergy(tag); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putDouble("energyStored", stored); }
    @Override public CompoundTag getUpdateTag() { return new CompoundTag(); }
    @Override public void setRemoved() { unbind(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { unbind(); super.onChunkUnloaded(); }
}
