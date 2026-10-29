/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.embers;

import com.rekindled.embers.api.capabilities.EmbersCapabilities;
import com.rekindled.embers.api.power.IEmberCapability;
import com.rekindled.embers.power.DefaultEmberCapability;
import hellfirepvp.modularmachinery.common.block.BlockEmberHatch;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/** Native double-precision storage behind validation; only real input hatches pay recipes. */
public final class TileEmberHatch extends TileMachineComponent {
    private final DefaultEmberCapability storage = new DefaultEmberCapability();
    private boolean active = true;
    private final IEmberCapability bridge = new IEmberCapability() {
        @Override public double getEmber() { return amount(); }
        @Override public double getEmberCapacity() { return capacity(); }
        @Override public void setEmber(double value) { if (live() && Double.isFinite(value)) restoreAmount(value); }
        @Override public void setEmberCapacity(double value) { /* The blockstate owns capacity. */ }
        @Override public double addAmount(double value, boolean doAdd) { return add(value, !doAdd); }
        @Override public double removeAmount(double value, boolean doRemove) { return take(value, !doRemove); }
        @Override public boolean acceptsVolatile() { return capacity() >= 3200; }
        @Override public void writeToNBT(CompoundTag tag) { tag.putDouble(EMBER, amount()); tag.putDouble(EMBER_CAPACITY, capacity()); }
        @Override public CompoundTag serializeNBT() { var tag = new CompoundTag(); writeToNBT(tag); return tag; }
        @Override public void deserializeNBT(CompoundTag tag) { if (live()) restoreAmount(tag.getDouble(EMBER)); }
        @Override public void onContentsChanged() { if (live()) markForUpdate(); }
        @Override public void invalidate() { active = false; capability.invalidate(); }
        @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) { return TileEmberHatch.this.getCapability(cap, side); }
    };
    private LazyOptional<IEmberCapability> capability = LazyOptional.of(() -> bridge);
    public TileEmberHatch(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(() -> type, pos, state); }
    @Override public IOType getIOType() { return IOType.INPUT; }
    public int capacity() { return getBlockState().getValue(BlockEmberHatch.SIZE).capacity(); }
    public double amount() { return Math.min(capacity(), storage.getEmber()); }
    public boolean live() {
        return active && !isRemoved() && level instanceof ServerLevel server && server.getServer().isSameThread()
                && server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
    }
    public void restoreAmount(double value) {
        if (!live()) return;
        storage.setEmberCapacity(capacity()); storage.setEmber(Double.isFinite(value) ? Math.max(0, Math.min(capacity(), value)) : 0);
        if (level != null) markForUpdate();
    }
    public double add(double value, boolean simulate) {
        if (!live() || !Double.isFinite(value) || value <= 0) return 0;
        double grant = Math.min(value, capacity() - amount());
        if (!simulate && grant > 0) { storage.setEmberCapacity(capacity()); storage.setEmber(amount()); storage.addAmount(grant, true); markForUpdate(); }
        return grant;
    }
    public double take(double value, boolean simulate) {
        if (!live() || !Double.isFinite(value) || value <= 0) return 0;
        double grant = Math.min(value, amount());
        if (!simulate && grant > 0) { storage.setEmber(amount()); storage.removeAmount(grant, true); markForUpdate(); }
        return grant;
    }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return cap == EmbersCapabilities.EMBER_CAPABILITY ? capability.cast() : super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() { super.invalidateCaps(); active = false; capability.invalidate(); }
    @Override public void reviveCaps() { super.reviveCaps(); active = true; capability = LazyOptional.of(() -> bridge); }
    @Override protected void saveAdditional(CompoundTag tag) { super.saveAdditional(tag); tag.putDouble("ember", amount()); }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        double value = tag.getDouble("ember");
        storage.setEmberCapacity(capacity()); storage.setEmber(Double.isFinite(value) ? Math.max(0, Math.min(capacity(), value)) : 0);
    }
}
