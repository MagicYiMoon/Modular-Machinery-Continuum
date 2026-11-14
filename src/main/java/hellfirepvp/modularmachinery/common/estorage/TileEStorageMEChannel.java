/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/** Registered even without AE. Opaque AE node data survives dependency removal. */
public class TileEStorageMEChannel extends TileEntityBase {
    protected TileEStorageController owner;
    protected CompoundTag nodeData = new CompoundTag();
    public TileEStorageMEChannel(BlockPos pos, BlockState state) { super(EStorageRegistration.CHANNEL_TILE, pos, state); }
    public boolean canBind(TileEStorageController controller) { return owner == null || owner == controller || owner.isRemoved(); }
    public boolean bind(TileEStorageController controller) {
        if (level == null || level.isClientSide || controller.getLevel() != level || !canBind(controller)) return false;
        if (owner != controller) { unbind(); owner = controller; requestStorageRefresh(); }
        return true;
    }
    public void unbind(TileEStorageController controller) { if (owner == controller) unbind(); }
    public void unbind() {
        TileEStorageController previous = owner; owner = null;
        disconnect();
        if (previous != null && !previous.isRemoved()) previous.requestStructureCheck();
    }
    protected void disconnect() { }
    public void requestStorageRefresh() { }
    public void requestPowerUpdate() { }
    public void setPlacingPlayer(Player player) { }
    public boolean isNetworkActive() { return false; }
    public double idleDrain() { return owner == null ? 0 : owner.storageIdleDrain(); }
    public TileEStorageController controller() { return owner; }
    @Override public void load(CompoundTag tag) {
        unbind(); super.load(tag); nodeData = tag.getCompound("MENode").copy();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); if (!nodeData.isEmpty()) tag.put("MENode", nodeData.copy());
    }
    @Override public CompoundTag getUpdateTag() { return new CompoundTag(); }
    @Override public void setRemoved() { unbind(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { unbind(); super.onChunkUnloaded(); }
}
