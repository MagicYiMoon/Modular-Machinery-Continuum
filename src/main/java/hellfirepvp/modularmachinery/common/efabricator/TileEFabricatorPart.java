/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO EFabricatorPart. */
package hellfirepvp.modularmachinery.common.efabricator;

import hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Ownership is runtime-only. No inventory or pending-output data is broadcast in chunk packets. */
public class TileEFabricatorPart extends TileEntityBase {
    private TileEFabricatorController owner;
    private boolean loaded = true;
    private volatile EFabricatorAppearance appearance = EFabricatorAppearance.OFF;
    private long lastWorkTick = Long.MIN_VALUE;
    public EFabricatorAppearance appearance() { return appearance; }
    /** Upstream RUN follows retained real work and lingers for twenty ticks, even when no power is available. */
    public void refreshAppearance() {
        if (!(level instanceof ServerLevel server) || isRemoved() || !(this instanceof TileEFabricatorWorker)) return;
        var current = EFabricatorAppearance.OFF;
        if (isBoundPart()) {
            current = EFabricatorAppearance.ON;
            if (this instanceof TileEFabricatorWorker worker) {
                long now = level.getGameTime();
                if (worker.queuedCrafts() > 0) { current = EFabricatorAppearance.RUN; lastWorkTick = now; }
                else if (appearance == EFabricatorAppearance.RUN && lastWorkTick != Long.MIN_VALUE && now - lastWorkTick <= 20)
                    current = EFabricatorAppearance.RUN;
            }
        } else lastWorkTick = Long.MIN_VALUE;
        if (current == appearance) return;
        appearance = current;
        EFabricatorAppearance.publishLight(server, worldPosition, current);
        level.getLightEngine().checkBlock(worldPosition);
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk != null && chunk.getBlockEntity(worldPosition) == this)
            net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> chunk)
                    .send(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this));
    }
    public TileEFabricatorPart(BlockPos pos, BlockState state) { super(EFabricatorRegistration.PART_TILE, pos, state); }
    public EFabricatorSpecs.Part specification() { return ((BlockEFabricatorPart)getBlockState().getBlock()).specification(); }
    public TileEFabricatorController owner() { return owner; }
    public boolean isBoundPart() {
        if (!loaded || isRemoved() || !(level instanceof ServerLevel server)) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this && owner != null && !owner.isRemoved()
                && owner.isAssembled() && owner.isFormed() && owner.owns(this);
    }
    public boolean isActivePart() { return isBoundPart() && owner.isOperational(); }
    public boolean canBind(TileEFabricatorController host) { return loaded && !isRemoved() && (owner == null || owner == host || owner.isRemoved()); }
    public boolean bind(TileEFabricatorController host) {
        if (level == null || level.isClientSide || host.getLevel() != level || !canBind(host)) return false;
        owner = host; return true;
    }
    public void unbind(TileEFabricatorController host) { if (owner == host) unbind(); }
    public void unbind() { var old = owner; owner = null; refreshAppearance(); if (old != null && !old.isRemoved()) old.requestStructureCheck(); }
    @Override public void load(CompoundTag tag) { unbind(); super.load(tag); }
    @Override public CompoundTag getUpdateTag() {
        var tag = new CompoundTag();
        if (this instanceof TileEFabricatorWorker) tag.putByte("Appearance", (byte)appearance.ordinal());
        return tag;
    }
    @Override public net.minecraftforge.client.model.data.ModelData getModelData() {
        return net.minecraftforge.client.model.data.ModelData.builder().with(EFabricatorAppearance.DATA, appearance).build();
    }
    private void receiveAppearance(CompoundTag tag) {
        if (!(this instanceof TileEFabricatorWorker) || level != null && !level.isClientSide) return;
        var current = EFabricatorAppearance.decode(tag.getInt("Appearance"));
        if (appearance == current) return;
        appearance = current; requestModelDataUpdate();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            // Vanilla sends incremental light sections only to chunk-boundary observers.
            // Interior observers must recheck after metadata changes their dynamic emission.
            level.getLightEngine().checkBlock(worldPosition);
            queueClientLightCheck();
        }
    }
    @Override public void handleUpdateTag(CompoundTag tag) { receiveAppearance(tag); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) receiveAppearance(packet.getTag());
    }
    @Override public void onLoad() {
        super.onLoad(); loaded = true;
        if (level != null && level.isClientSide && this instanceof TileEFabricatorWorker) {
            requestModelDataUpdate(); level.getLightEngine().checkBlock(worldPosition);
            queueClientLightCheck();
        }
    }
    private void queueClientLightCheck() {
        if (level != null && level.isClientSide) net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> hellfirepvp.modularmachinery.client.world.EFabricatorVisualUpdates.enqueue(this));
    }
    @Override public void setRemoved() { unbind(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { loaded = false; unbind(); super.onChunkUnloaded(); }
}
