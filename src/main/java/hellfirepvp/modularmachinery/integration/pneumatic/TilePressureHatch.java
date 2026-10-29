/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.pneumatic;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileMachineComponent;
import me.desht.pneumaticcraft.api.PNCCapabilities;
import me.desht.pneumaticcraft.api.PneumaticRegistry;
import me.desht.pneumaticcraft.api.tileentity.IAirHandlerMachine;
import me.desht.pneumaticcraft.api.upgrade.IUpgradeItem;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import java.util.*;

/** Native pressure balance, vacuum floor, leakage and overpressure checks remain owned by PNC. */
public final class TilePressureHatch extends TileMachineComponent {
    public static final int BASE_VOLUME = 16000;
    private final boolean input;
    private boolean active = true;
    private final IAirHandlerMachine handler = PneumaticRegistry.getInstance().getAirHandlerMachineFactory().createTierTwoAirHandler(BASE_VOLUME);
    private LazyOptional<IAirHandlerMachine> capability = LazyOptional.of(() -> handler);
    private boolean refreshConnections = true;
    private int lastAir;
    private boolean readingUpgrades;
    private final me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic heat = PneumaticRegistry.getInstance().getHeatRegistry().makeHeatExchangerLogic();
    private LazyOptional<me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic> heatCapability = LazyOptional.of(() -> heat);
    private boolean refreshHeat = true;
    private int lastTemperature = 300;
    private final ItemStackHandler upgrades = new ItemStackHandler(4) {
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            int limit = upgradeLimit(stack);
            if (limit == 0) return false;
            var type = ((IUpgradeItem) stack.getItem()).getUpgradeType();
            for (int i = 0; i < getSlots(); i++)
                if (i != slot && getStackInSlot(i).getItem() instanceof IUpgradeItem other && other.getUpgradeType() == type) return false;
            return true;
        }
        @Override protected int getStackLimit(int slot, ItemStack stack) { return Math.min(super.getStackLimit(slot, stack), upgradeLimit(stack)); }
        @Override public void setStackInSlot(int slot, ItemStack stack) {
            if (!stack.isEmpty() && (!isItemValid(slot, stack) || stack.getCount() > getStackLimit(slot, stack)))
                throw new IllegalArgumentException("Invalid pressure hatch upgrade");
            super.setStackInSlot(slot, stack);
        }
        @Override protected void onContentsChanged(int slot) {
            if (!readingUpgrades) { refreshUpgrades(); markForUpdate(); }
        }
    };
    public TilePressureHatch(BlockEntityType<?> type, BlockPos pos, BlockState state, boolean input) { super(() -> type, pos, state); this.input = input; }
    @Override public IOType getIOType() { return input ? IOType.INPUT : IOType.OUTPUT; }
    public IAirHandlerMachine air() { return handler; }
    public me.desht.pneumaticcraft.api.heat.IHeatExchangerLogic heat() { return heat; }
    public ItemStackHandler upgrades() { return upgrades; }
    public void upgradesChanged() { refreshUpgrades(); markForUpdate(); }
    public static int upgradeLimit(ItemStack stack) {
        if (!(stack.getItem() instanceof IUpgradeItem item) || item.getUpgradeTier() < 1 || item.getUpgradeType() == null) return 0;
        ResourceLocation id = item.getUpgradeType().getId();
        int limit = id.equals(new ResourceLocation("pneumaticcraft", "volume")) ? 64
                : id.equals(new ResourceLocation("pneumaticcraft", "security")) ? 1 : 0;
        return limit / item.getUpgradeTier();
    }
    public int upgradeCount(String name) {
        int count = 0;
        for (int i = 0; i < upgrades.getSlots(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (stack.getItem() instanceof IUpgradeItem item && item.getUpgradeType().getId().equals(new ResourceLocation("pneumaticcraft", name)))
                count += stack.getCount() * item.getUpgradeTier();
        }
        return count;
    }
    private void refreshUpgrades() {
        handler.setVolumeUpgrades(Math.min(64, upgradeCount("volume")));
        if (upgradeCount("security") > 0) handler.enableSafetyVenting(p -> p > handler.getDangerPressure(), Direction.UP);
        else handler.disableSafetyVenting();
    }
    public boolean live() {
        return active && !isRemoved() && level instanceof ServerLevel server && server.getServer().isSameThread()
                && server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
    }
    public void neighborsChanged() { refreshConnections = true; refreshHeat = true; }
    public void tick() {
        if (level == null || isRemoved()) return;
        if (!level.isClientSide() && !live()) return;
        if (!level.isClientSide()) {
            if (refreshHeat || level.getGameTime() % 20 == 0) {
                heat.setThermalCapacity(100); heat.setThermalResistance(1);
                var faces = Arrays.stream(Direction.values()).filter(dir -> level.hasChunkAt(worldPosition.relative(dir))).toArray(Direction[]::new);
                heat.initializeAsHull(level, worldPosition, (world, pos) -> level.hasChunkAt(pos), faces);
                refreshHeat = false;
            }
            heat.tick();
            int current = heat.getTemperatureAsInt();
            if (current != lastTemperature) { lastTemperature = current; setChanged(); }
        }
        if (refreshConnections) { handler.setConnectedFaces(List.of(Direction.values())); refreshConnections = false; }
        if (!level.isClientSide()) {
            // Source output hatches vent when disconnected; input hatches retain their reserve.
            handler.setSideLeaking(!input && handler.getConnectedAirHandlers(this).isEmpty() ? Direction.UP : null);
        }
        handler.tick(this);
        if (!level.isClientSide() && !isRemoved() && handler.getAir() != lastAir) {
            setChanged();
            if (level.getGameTime() % 10 == 0) { lastAir = handler.getAir(); markForUpdate(); }
        }
    }
    public boolean meetsPressure(float minimum) {
        return live() && (minimum < 0 ? handler.getPressure() <= minimum : handler.getPressure() >= minimum);
    }
    public long take(long amount, float pressure, boolean simulate) {
        if (!meetsPressure(pressure)) return 0;
        long available = pressure < 0 ? (long) handler.getAir() + handler.getVolume() : Math.max(0, handler.getAir());
        int taken = (int) Math.min(Math.max(0, amount), Math.max(0, available));
        if (!simulate && taken > 0) { handler.addAir(-taken); markForUpdate(); }
        return taken;
    }
    public long add(long amount, boolean simulate) {
        if (!live()) return 0;
        // Recipe output preflights against the tier's critical limit; pipes can still overpressurize natively.
        long capacity = (long) (handler.getCriticalPressure() * handler.getVolume());
        int added = (int) Math.min(Math.max(0, amount), Math.max(0L, capacity - handler.getAir()));
        if (!simulate && added > 0) { handler.addAir(added); markForUpdate(); }
        return added;
    }
    boolean canChange(long delta) {
        long next = handler.getAir() + delta;
        return live() && delta >= -Integer.MAX_VALUE && delta <= Integer.MAX_VALUE && next >= -handler.getVolume() && next <= Integer.MAX_VALUE;
    }
    void changeReserved(long delta) { if (delta != 0) { handler.addAir((int) delta); markForUpdate(); } }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        if (cap == PNCCapabilities.HEAT_EXCHANGER_CAPABILITY) return heatCapability.cast();
        return cap == PNCCapabilities.AIR_HANDLER_MACHINE_CAPABILITY ? capability.cast() : super.getCapability(cap, side);
    }
    @Override public void invalidateCaps() {
        // Remove native reciprocal heat links before this tile becomes stale or unloaded.
        if (level != null && !level.isClientSide() && level.getServer().isSameThread()) {
            // A neighbour can initiate the reciprocal link before this hatch initializes its own hull.
            // Clearing only our native hull list misses those inbound connections.
            for (var direction : Direction.values()) {
                var pos = worldPosition.relative(direction);
                if (!level.hasChunkAt(pos)) continue;
                var neighbor = level.getBlockEntity(pos);
                if (neighbor != null) neighbor.getCapability(PNCCapabilities.HEAT_EXCHANGER_CAPABILITY, direction.getOpposite())
                        .ifPresent(logic -> logic.removeConnectedExchanger(heat));
            }
            heat.initializeAsHull(level, worldPosition, (world, pos) -> false, new Direction[0]);
        }
        active = false;
        super.invalidateCaps(); capability.invalidate(); heatCapability.invalidate(); handler.setConnectedFaces(List.of()); refreshConnections = true;
    }
    @Override public void reviveCaps() { super.reviveCaps(); active = true; capability = LazyOptional.of(() -> handler); heatCapability = LazyOptional.of(() -> heat); refreshConnections = true; refreshHeat = true; }
    @Override public void onLoad() { super.onLoad(); refreshConnections = true; refreshHeat = true; }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag); tag.put("pneumaticAir", handler.serializeNBT()); tag.put("pressureUpgrades", upgrades.serializeNBT());
        tag.putDouble("pneumaticTemperature", heat.getTemperature());
    }
    @Override public void load(CompoundTag tag) {
        super.load(tag);
        double temperature = tag.contains("pneumaticTemperature") ? tag.getDouble("pneumaticTemperature") : 300;
        heat.setTemperature(Double.isFinite(temperature) ? Math.max(0, Math.min(2273, temperature)) : 300); refreshHeat = true;
        // Restore a fixed four-slot inventory. Saved Size and duplicate/unsupported upgrades are not trusted.
        readingUpgrades = true;
        try {
            for (int i = 0; i < 4; i++) upgrades.setStackInSlot(i, ItemStack.EMPTY);
            for (var entry : tag.getCompound("pressureUpgrades").getList("Items", 10)) {
                CompoundTag saved = (CompoundTag) entry;
                int slot = saved.getInt("Slot");
                ItemStack stack = ItemStack.of(saved);
                if (slot >= 0 && slot < 4 && upgrades.getStackInSlot(slot).isEmpty() && upgrades.isItemValid(slot, stack)) {
                    stack.setCount(Math.min(stack.getCount(), Math.min(stack.getMaxStackSize(), upgradeLimit(stack))));
                    upgrades.setStackInSlot(slot, stack);
                }
            }
        } finally { readingUpgrades = false; }
        refreshUpgrades();
        var checked = tag.getCompound("pneumaticAir").copy();
        // Native air may legitimately exceed the critical limit: retain it for native explosion checks.
        checked.putInt("Air", Math.max(-handler.getVolume(), checked.getInt("Air")));
        handler.deserializeNBT(checked); refreshConnections = true; lastAir = handler.getAir();
    }
}
