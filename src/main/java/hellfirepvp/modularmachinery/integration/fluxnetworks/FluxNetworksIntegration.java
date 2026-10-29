/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.fluxnetworks;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import sonar.fluxnetworks.api.FluxCapabilities;
import sonar.fluxnetworks.api.energy.IFNEnergyStorage;

/** Loaded only when Flux Networks is installed. Its capability shares the ordinary FE buffer. */
public final class FluxNetworksIntegration {
    private FluxNetworksIntegration() { }

    public static void register() {
        MinecraftForge.EVENT_BUS.addGenericListener(BlockEntity.class, FluxNetworksIntegration::attach);
        Modularmachinery.LOG.info("[MMC] Flux Networks energy hatch integration enabled (shared FE storage)");
    }

    private static void attach(AttachCapabilitiesEvent<BlockEntity> event) {
        if (event.getObject() instanceof TileEnergyHatch hatch) {
            // The separate directional port must not gain an all-face long-energy bypass.
            if (hatch instanceof hellfirepvp.modularmachinery.common.tiles.TileEnergyOutputPort) return;
            Storage provider = new Storage(hatch);
            event.addCapability(new ResourceLocation(Modularmachinery.MODID, "flux_energy"), provider);
            event.addListener(provider::invalidate);
        }
    }

    /** -1 means no FN capability: the caller may use Forge Energy instead. Never use both for one offer. */
    public static int tryReceive(BlockEntity target, Direction side, int amount) {
        var storage = target.getCapability(FluxCapabilities.FN_ENERGY_STORAGE, side).orElse(null);
        if (storage == null) return -1;
        if (amount <= 0 || !storage.canReceive()) return 0;
        return (int) Math.max(0L, Math.min((long) amount, storage.receiveEnergyL(amount, false)));
    }

    private static final class Storage implements ICapabilityProvider, IFNEnergyStorage {
        private final TileEnergyHatch hatch;
        private final LazyOptional<IFNEnergyStorage> capability = LazyOptional.of(() -> this);
        private boolean valid = true;

        private Storage(TileEnergyHatch hatch) { this.hatch = hatch; }

        private boolean available() {
            return valid && !hatch.isRemoved() && Config.enableFluxNetworksIntegration();
        }

        private boolean server() {
            return available() && hatch.getLevel() != null && !hatch.getLevel().isClientSide;
        }

        private static int bounded(long amount) {
            return (int) Math.max(0L, Math.min(amount, Integer.MAX_VALUE));
        }

        @Override
        public long receiveEnergyL(long amount, boolean simulate) {
            if (!server() || !canReceive() || amount <= 0) return 0;
            return hatch.getEnergyStorage().receiveEnergy(bounded(amount), simulate);
        }

        @Override
        public long extractEnergyL(long amount, boolean simulate) {
            if (!server() || !canExtract() || amount <= 0) return 0;
            return hatch.getEnergyStorage().extractEnergy(bounded(amount), simulate);
        }

        @Override public long getEnergyStoredL() { return available() ? hatch.getStoredEnergy() : 0; }
        @Override public long getMaxEnergyStoredL() { return available() ? hatch.getCapacity() : 0; }
        @Override public boolean canExtract() { return available() && hatch.getIOType() == IOType.OUTPUT; }
        @Override public boolean canReceive() { return available() && hatch.getIOType() == IOType.INPUT; }

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return cap == FluxCapabilities.FN_ENERGY_STORAGE && available() ? capability.cast() : LazyOptional.empty();
        }

        private void invalidate() {
            valid = false;
            capability.invalidate();
        }
    }
}
