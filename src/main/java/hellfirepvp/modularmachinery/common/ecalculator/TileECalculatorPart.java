/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO ECalculatorPart. */
package hellfirepvp.modularmachinery.common.ecalculator;

import hellfirepvp.modularmachinery.common.tiles.base.TileEntityBase;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.Supplier;

/** Runtime ownership is never restored from disk. Opaque future backend data is never sent to clients. */
public class TileECalculatorPart extends TileEntityBase {
    private TileECalculatorController owner;
    private boolean loaded = true;
    private CompoundTag backendData = new CompoundTag();
    private ECalculatorCoreAccess cpuAccess;
    private volatile ECalculatorAppearance appearance = ECalculatorAppearance.OFF;
    public ECalculatorAppearance appearance() { return appearance; }
    /** Checks loaded ownership without refreshing UNKNOWN chunk tickets or querying AE power. */
    public boolean isBoundPart() {
        if (!loaded || isRemoved() || !(level instanceof net.minecraft.server.level.ServerLevel server)) return false;
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this && owner != null
                && !owner.isRemoved() && owner.isAssembled() && owner.isFormed() && owner.owns(this);
    }
    private TileECalculatorPart loadedPart(BlockPos pos) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server)) return null;
        var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(pos) instanceof TileECalculatorPart part ? part : null;
    }
    boolean visualDriveLink(Direction side) {
        return isBoundPart() && loadedPart(worldPosition.relative(side)) instanceof TileECalculatorDrive drive
                && drive.isBoundPart() && drive.owner() == owner && drive.isTierSupported()
                && drive.getBlockState().getValue(BlockECalculatorPart.FACING) == getBlockState().getValue(BlockECalculatorPart.FACING);
    }
    private ECalculatorAppearance calculateAppearance() {
        if (!isBoundPart()) return ECalculatorAppearance.OFF;
        var status = ECalculatorAppearance.Status.ON;
        var link = ECalculatorAppearance.Link.NONE;
        var tier = ECalculatorAppearance.Tier.NONE;
        var kind = specification().kind();
        if (kind == ECalculatorSpecs.Kind.THREAD || kind == ECalculatorSpecs.Kind.HYPER_THREAD) {
            // Source threads = all retained CPUs, including hyper-threads and pending material returns.
            // Do not instantiate a backend just to render a light.
            if (cpuAccess != null && cpuAccess.hasTasks()) status = ECalculatorAppearance.Status.RUN;
        } else if (kind == ECalculatorSpecs.Kind.TRANSMITTER) {
            boolean up = visualDriveLink(Direction.UP), down = visualDriveLink(Direction.DOWN);
            link = up && down ? ECalculatorAppearance.Link.ALL : up ? ECalculatorAppearance.Link.UP : down ? ECalculatorAppearance.Link.DOWN : ECalculatorAppearance.Link.NONE;
            tier = ECalculatorAppearance.tier(owner.tier());
        } else if (kind == ECalculatorSpecs.Kind.DRIVE && ((TileECalculatorDrive)this).isTierSupported()) {
            link = owner.driveVisualLink(worldPosition);
        }
        return new ECalculatorAppearance(status, link, tier);
    }
    public void refreshAppearance() {
        if (!(level instanceof net.minecraft.server.level.ServerLevel server) || isRemoved()) return;
        var current = calculateAppearance();
        if (current.equals(appearance)) return;
        appearance = current;
        level.getLightEngine().checkBlock(worldPosition);
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        if (chunk != null && chunk.getBlockEntity(worldPosition) == this)
            net.minecraftforge.network.PacketDistributor.TRACKING_CHUNK.with(() -> chunk).send(net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this));
    }
    public TileECalculatorPart(BlockPos pos, BlockState state) { this(ECalculatorRegistration.PART_TILE, pos, state); }
    protected TileECalculatorPart(Supplier<? extends BlockEntityType<?>> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public ECalculatorSpecs.Part specification() { return ((BlockECalculatorPart)getBlockState().getBlock()).specification(); }
    public TileECalculatorController owner() { return owner; }
    public boolean canBind(TileECalculatorController host) { return loaded && !isRemoved() && (owner == null || owner == host || owner.isRemoved()); }
    public boolean bind(TileECalculatorController host) {
        if (level == null || level.isClientSide || host.getLevel() != level || !canBind(host)) return false;
        owner = host; return true;
    }
    public void unbind(TileECalculatorController host) { if (owner == host) unbind(); }
    public void unbind() {
        var previous = owner; owner = null;
        refreshAppearance();
        if (previous != null && !previous.isRemoved()) previous.requestStructureCheck();
    }
    public boolean isActivePart() {
        if (!loaded || isRemoved() || !(level instanceof net.minecraft.server.level.ServerLevel server)) return false;
        // Level.getBlockEntity/getChunk may refresh an UNKNOWN ticket even after hasChunkAt succeeds.
        // CPU liveness checks run every grid tick and must not keep their own core chunk loaded forever.
        var chunk = server.getChunkSource().getChunkNow(worldPosition.getX() >> 4, worldPosition.getZ() >> 4);
        return chunk != null && chunk.getBlockEntity(worldPosition) == this
                && owner != null && owner.isOperational() && owner.owns(this);
    }
    public ECalculatorCoreAccess cpuAccess() {
        if (level == null || level.isClientSide || !net.minecraftforge.fml.ModList.get().isLoaded("ae2")) return null;
        var kind = specification().kind();
        if (kind != ECalculatorSpecs.Kind.THREAD && kind != ECalculatorSpecs.Kind.HYPER_THREAD) return null;
        if (cpuAccess == null) cpuAccess = hellfirepvp.modularmachinery.integration.ae2.ecalculator.ECalculatorFactory.core(this, backendData);
        return cpuAccess;
    }
    public CompoundTag backendSnapshot() { if (cpuAccess != null) backendData = cpuAccess.save(); return backendData.copy(); }
    public void restoreBackend(CompoundTag data) {
        if (cpuAccess != null) { cpuAccess.close(); cpuAccess = null; }
        backendData = data.copy(); setChanged();
        if (owner != null) owner.componentsChanged();
    }
    /** Source transmitter links only same-facing supported drives directly above/below. */
    public boolean linkedDrive(Direction side) {
        if (specification().kind() != ECalculatorSpecs.Kind.TRANSMITTER || !isActivePart()
                || side.getAxis() != Direction.Axis.Y || !level.hasChunkAt(worldPosition.relative(side))) return false;
        return loadedPart(worldPosition.relative(side)) instanceof TileECalculatorDrive drive
                && drive.owner() == owner && drive.isTierSupported()
                && drive.getBlockState().getValue(BlockECalculatorPart.FACING) == getBlockState().getValue(BlockECalculatorPart.FACING);
    }
    @Override public void load(CompoundTag tag) {
        unbind(); super.load(tag);
        restoreBackend(tag.getCompound("BackendData"));
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        var data = backendSnapshot();
        if (!data.isEmpty()) tag.put("BackendData", data);
    }
    @Override public CompoundTag getUpdateTag() { var tag = new CompoundTag(); tag.putInt("Appearance", appearance.encode()); return tag; }
    @Override public net.minecraftforge.client.model.data.ModelData getModelData() {
        return net.minecraftforge.client.model.data.ModelData.builder().with(ECalculatorAppearance.DATA, appearance).build();
    }
    private void receiveAppearance(CompoundTag tag) {
        if (level != null && !level.isClientSide) return;
        var current = ECalculatorAppearance.decode(tag.getInt("Appearance"));
        if (appearance.equals(current)) return;
        appearance = current; requestModelDataUpdate();
        if (level != null) {
            level.getLightEngine().checkBlock(worldPosition);
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }
    @Override public void handleUpdateTag(CompoundTag tag) { receiveAppearance(tag); }
    @Override public void onDataPacket(net.minecraft.network.Connection connection, net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) receiveAppearance(packet.getTag());
    }
    @Override public void onLoad() { super.onLoad(); loaded = true; }
    private void suspendCPUs() { backendData = backendSnapshot(); if (cpuAccess != null) { cpuAccess.close(); cpuAccess = null; } }
    @Override public void setRemoved() { unbind(); suspendCPUs(); super.setRemoved(); }
    @Override public void onChunkUnloaded() { loaded = false; unbind(); suspendCPUs(); super.onChunkUnloaded(); }
}
