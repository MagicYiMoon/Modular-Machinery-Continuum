/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles;

import hellfirepvp.modularmachinery.common.block.BlockEnergyOutputPort;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.registry.RegistryBlockEntities;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import java.lang.ref.WeakReference;

/** Bounded persistent recipe output, sent along the front ray without loading chunks. */
public final class TileEnergyOutputPort extends TileEnergyHatch {
    public static final int CAPACITY = Integer.MAX_VALUE;
    public static final int TRANSFER = CAPACITY / 10;
    private boolean active = true;
    private int generation;
    private LazyOptional<IEnergyStorage> external = createExternal(null);
    private final java.util.Map<Direction, LazyOptional<IEnergyStorage>> sided = new java.util.EnumMap<>(Direction.class);
    private BlockPos target;
    private WeakReference<BlockEntity> targetTile = new WeakReference<>(null);
    private LazyOptional<IEnergyStorage> targetEnergy = LazyOptional.empty();
    private Direction scanFacing;
    private int scanRange;
    private long nextScan = Long.MIN_VALUE;
    private long transferTick = Long.MIN_VALUE;
    private long lastOutput = Long.MIN_VALUE;
    private int transferred;
    private boolean delivering;
    private int visualDistance, visualRate;
    private long sampleEnergy, sampleTime = Long.MIN_VALUE;

    public TileEnergyOutputPort(BlockPos pos, BlockState state) {
        super(RegistryBlockEntities.ENERGY_OUTPUT_PORT, pos, state, IOType.OUTPUT, CAPACITY, Integer.MAX_VALUE);
    }
    public boolean live() {
        return active && !isRemoved() && level instanceof ServerLevel server && server.getServer().isSameThread()
                && server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
    }
    @Override public int produceInternal(int amount, boolean simulate) {
        return live() && !delivering && amount > 0 ? super.produceInternal(amount, simulate) : 0;
    }
    @Override public int consumeInternal(int amount, boolean simulate) { return 0; }
    private int budget() {
        if (transferTick != level.getGameTime()) { transferTick = level.getGameTime(); transferred = 0; }
        return Math.max(0, TRANSFER - transferred);
    }
    private int extract(int requested, boolean simulate) {
        if (!live() || delivering || requested <= 0) return 0;
        int actual = getEnergyStorage().extractEnergy(Math.min(requested, budget()), simulate);
        if (!simulate && actual > 0) { if (sampleTime == Long.MIN_VALUE) sampleTime = level.getGameTime() - 1; transferred += actual; sampleEnergy += actual; lastOutput = level.getGameTime(); }
        return actual;
    }
    private LazyOptional<IEnergyStorage> createExternal(Direction side) {
        int token = generation;
        return LazyOptional.of(() -> new IEnergyStorage() {
            public int receiveEnergy(int amount, boolean simulate) { return 0; }
            public int extractEnergy(int amount, boolean simulate) { return canExtract() ? extract(amount, simulate) : 0; }
            public int getEnergyStored() { return TileEnergyOutputPort.this.getStoredEnergy(); }
            public int getMaxEnergyStored() { return CAPACITY; }
            public boolean canExtract() { return token == generation && active && (side == null || side == getBlockState().getValue(BlockEnergyOutputPort.FACING)); }
            public boolean canReceive() { return false; }
        });
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == ForgeCapabilities.ENERGY)
            return active && (side == null || side == getBlockState().getValue(BlockEnergyOutputPort.FACING))
                    ? (side == null ? external : sided.computeIfAbsent(side, this::createExternal)).cast() : LazyOptional.empty();
        return super.getCapability(cap, side);
    }
    public void invalidateTarget() { target = null; targetTile.clear(); targetEnergy = LazyOptional.empty(); nextScan = Long.MIN_VALUE; }
    private void refreshTarget() {
        invalidateTarget();
        scanFacing = getBlockState().getValue(BlockEnergyOutputPort.FACING);
        scanRange = Config.energyOutputPortRange();
        nextScan = level.getGameTime() + 10;
        for (int distance = 1; distance <= scanRange; distance++) {
            BlockPos candidate = worldPosition.relative(scanFacing, distance);
            if (!level.hasChunkAt(candidate)) return;
            BlockEntity tile = level.getBlockEntity(candidate);
            // Like the extension, the FIRST block entity stops the ray, including non-energy tiles.
            if (tile != null && !tile.isRemoved()) {
                target = candidate; targetTile = new WeakReference<>(tile);
                targetEnergy = tile.getCapability(ForgeCapabilities.ENERGY, scanFacing.getOpposite());
                if (!targetEnergy.isPresent()) targetEnergy = tile.getCapability(ForgeCapabilities.ENERGY, null);
                return;
            }
            if (level.getBlockState(candidate).canOcclude()) return;
        }
    }
    private IEnergyStorage receiver() {
        if (!live()) return null;
        Direction facing = getBlockState().getValue(BlockEnergyOutputPort.FACING);
        if (scanFacing != facing || scanRange != Config.energyOutputPortRange() || level.getGameTime() >= nextScan) refreshTarget();
        if (target == null) return null;
        // The cached ray may cross chunks. Unloading any of them must not leave a usable remote link.
        int distance = Math.abs(target.getX()-worldPosition.getX()) + Math.abs(target.getY()-worldPosition.getY()) + Math.abs(target.getZ()-worldPosition.getZ());
        if (!level.hasChunkAt(target)) { invalidateTarget(); return null; }
        for (int i=1; i<distance; i+=16) if (!level.hasChunkAt(worldPosition.relative(facing,i))) { invalidateTarget(); return null; }
        if (level.getBlockEntity(target) != targetTile.get() || targetTile.get() == null || targetTile.get().isRemoved()) { invalidateTarget(); return null; }
        IEnergyStorage receiver = targetEnergy.orElse(null);
        return receiver != null && receiver.canReceive() ? receiver : null;
    }
    public BlockPos targetPosition() { return receiver() == null ? null : target; }
    private void deliver(IEnergyStorage receiver, int offer) {
        delivering = true;
        try {
            int possible = Math.max(0, Math.min(offer, receiver.receiveEnergy(offer, true)));
            if (possible == 0) return; // Full targets must not dirty the chunk by taking/refunding energy.
            // Reserve before entering external commit code, and reject callbacks into this port.
            int reserved = getEnergyStorage().extractEnergy(possible, false);
            int accepted = 0;
            try { accepted = Math.max(0, Math.min(reserved, receiver.receiveEnergy(reserved, false))); }
            finally {
                getEnergyStorage().receiveEnergy(reserved - accepted, false);
                transferred += accepted;
                sampleEnergy += accepted;
                if (accepted > 0) lastOutput = level.getGameTime();
            }
        } finally { delivering = false; }
    }
    @Override public void tick() {
        if (!live()) return;
        if (sampleTime == Long.MIN_VALUE || level.getGameTime() < sampleTime) { sampleTime = level.getGameTime() - 1; sampleEnergy = 0; }
        IEnergyStorage receiver = receiver();
        if (receiver != null && getStoredEnergy() > 0) {
            int offer = extract(Integer.MAX_VALUE, true);
            if (offer > 0) deliver(receiver, offer);
        }
        int status = lastOutput != Long.MIN_VALUE && level.getGameTime() - lastOutput >= 0 && level.getGameTime() - lastOutput < 20 ? 2 : receiver != null ? 1 : 0;
        if (getBlockState().getValue(BlockEnergyOutputPort.STATUS) != status)
            level.setBlock(worldPosition, getBlockState().setValue(BlockEnergyOutputPort.STATUS, status), 2);
        updateVisual(receiver != null && target != null ? Math.abs(target.getX()-worldPosition.getX())
                + Math.abs(target.getY()-worldPosition.getY()) + Math.abs(target.getZ()-worldPosition.getZ()) : 0);
    }
    private void updateVisual(int distance) {
        long now = level.getGameTime();
        if (sampleTime == Long.MIN_VALUE || now < sampleTime) { sampleTime = now - 1; sampleEnergy = 0; }
        int rate = visualRate;
        if (distance == 0) { rate = 0; sampleEnergy = 0; sampleTime = now; }
        else if (now - sampleTime >= 10) {
            rate = (int) Math.min(Integer.MAX_VALUE, sampleEnergy / Math.max(1, now - sampleTime));
            sampleEnergy = 0; sampleTime = now;
        }
        if (distance != visualDistance || rate != visualRate) {
            visualDistance = distance; visualRate = rate; markNoUpdateSync();
        }
    }
    public int visualDistance() { return visualDistance; }
    public int visualRate() { return visualRate; }
    @Override public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag(); tag.putInt("portVisualDistance", visualDistance); tag.putInt("portVisualRate", visualRate); return tag;
    }
    @Override public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        return new net.minecraft.world.phys.AABB(worldPosition).expandTowards(
                net.minecraft.world.phys.Vec3.atLowerCornerOf(getBlockState().getValue(BlockEnergyOutputPort.FACING).getNormal()).scale(visualDistance)).inflate(0, .5, 0);
    }
    @Override public void invalidateCaps() {
        super.invalidateCaps(); active = false; generation++; external.invalidate();
        sided.values().forEach(LazyOptional::invalidate); sided.clear(); invalidateTarget();
    }
    @Override public void reviveCaps() { super.reviveCaps(); active = true; external = createExternal(null); invalidateTarget(); }
    @Override public void load(CompoundTag tag) {
        // Only this bounded port changes NBT validation; existing hatch save behavior is untouched.
        CompoundTag clean = tag.copy();
        if (clean.contains("energy")) clean.putInt("energy", Math.max(0, clean.getInt("energy")));
        super.load(clean); invalidateTarget();
        visualDistance = Math.max(0, Math.min(256, tag.getInt("portVisualDistance")));
        visualRate = Math.max(0, tag.getInt("portVisualRate"));
    }
}
