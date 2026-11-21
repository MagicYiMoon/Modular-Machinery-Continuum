/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.base;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.block.BlockEnergyHatch;
import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import net.minecraft.nbt.Tag;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base for an energy hatch (input or output), backed by Forge Energy.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream implemented its own {@code IEnergyHandler} with long-typed
 * storage and, separately, bridged IC2's {@code IEnergySink}/{@code IEnergySource} on the energy
 * hatch classes. IC2 is gone from this port's scope, and the long-typed handler is replaced by
 * Forge Energy, whose {@link IEnergyStorage} is {@code int}-based — see
 * {@link hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData#getMaxEnergy()} for why the
 * hatch model follows that rather than the other way round.
 * Optional Flux Networks support attaches its long-amount capability to this same int-sized store;
 * it does not introduce a second buffer or change the configured capacity.
 *
 * <p>Input/output asymmetry is enforced in the storage predicates, expressed through the same
 * {@link IOType} the machine components use. Notice that the two directions are genuinely different
 * capabilities of the same block: an input hatch must not let a neighbouring machine drain it, and an
 * output hatch must not let one fill it. Encoding that in {@code canExtract}/{@code canReceive}
 * (rather than in a per-side wrapper) keeps one storage instance per block entity.
 */
public abstract class TileEnergyHatch extends TileMachineComponent {

    private final IOType ioType;
    private final HatchStorage energy;

    protected TileEnergyHatch(java.util.function.Supplier<? extends BlockEntityType<?>> type,
                              BlockPos pos, BlockState state, IOType ioType,
                              int capacity, int maxTransfer) {
        super(type, pos, state);
        this.ioType = ioType;
        this.energy = new HatchStorage(capacity, maxTransfer);
    }

    @Override
    public void setBlockState(BlockState state) {
        BlockState previous = getBlockState();
        super.setBlockState(state);
        if (energy == null || !(state.getBlock() instanceof BlockEnergyHatch)
                || !state.hasProperty(BlockEnergyHatch.SIZE)) return;
        EnergyHatchData size = state.getValue(BlockEnergyHatch.SIZE);
        if (!previous.hasProperty(BlockEnergyHatch.SIZE) || previous.getValue(BlockEnergyHatch.SIZE) != size) {
            // Same-block state changes reuse this tile. Keep existing capability wrappers and update
            // their backing storage in place; neither callers nor saved FE can exceed the new tier.
            energy.resize(size.getMaxEnergy(), size.getMaxTransfer());
            setChanged();
        }
    }

    private final class HatchStorage extends EnergyStorage {
        private HatchStorage(int capacity, int transfer) {
            super(capacity, transfer, transfer);
        }

        private int transferLimit() { return maxReceive; }

        private void resize(int capacity, int transfer) {
            this.capacity = capacity;
            maxReceive = maxExtract = transfer;
            energy = Math.max(0, Math.min(energy, capacity));
        }

        @Override
        public void deserializeNBT(Tag tag) {
            super.deserializeNBT(tag);
            energy = Math.max(0, Math.min(energy, capacity));
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            if (amount <= 0) return 0;
            int accepted = super.receiveEnergy(amount, simulate);
            if (accepted > 0 && !simulate) setChanged();
            return accepted;
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            if (amount <= 0) return 0;
            int extracted = super.extractEnergy(amount, simulate);
            if (extracted > 0 && !simulate) setChanged();
            return extracted;
        }
    }

    /**
     * The {@code IEnergyStorage} this hatch presents to <b>other</b> machines.
     *
     * <p>This is where the direction rule lives: an input hatch refuses extraction (so a neighbour
     * cannot drain the buffer its owner is filling) and an output hatch refuses insertion. The
     * underlying store stays bidirectional so the owning machine can work with it — see
     * {@link HatchStorage}.
     *
     * <p>Only the four guard methods are overridden; storage/capacity are read straight through, so the
     * externally visible energy figure always matches what the machine actually holds.
     */
    private IEnergyStorage externalView() {
        final boolean externalReceive = ioType == IOType.INPUT;
        final boolean externalExtract = ioType == IOType.OUTPUT;
        return new IEnergyStorage() {
            @Override
            public int receiveEnergy(int maxReceive, boolean simulate) {
                return externalReceive ? energy.receiveEnergy(maxReceive, simulate) : 0;
            }

            @Override
            public int extractEnergy(int maxExtract, boolean simulate) {
                return externalExtract ? energy.extractEnergy(maxExtract, simulate) : 0;
            }

            @Override
            public int getEnergyStored() {
                return energy.getEnergyStored();
            }

            @Override
            public int getMaxEnergyStored() {
                return energy.getMaxEnergyStored();
            }

            @Override
            public boolean canExtract() {
                return externalExtract;
            }

            @Override
            public boolean canReceive() {
                return externalReceive;
            }
        };
    }

    public IOType getIOType() {
        return ioType;
    }

    /**
     * @return the energy currently held, for display
     *
     * <p>Needed because the store is a separate {@link HatchStorage}, so the tile
     * itself has no {@code getEnergyStored} to call. Reading through the capability instead would go via
     * {@link #externalView}, which reports the same figures — but it would also construct a wrapper per
     * call, and a GUI reads this every frame.
     */
    public int getStoredEnergy() {
        return energy.getEnergyStored();
    }

    /** @return this hatch's capacity in FE, for display */
    public int getCapacity() {
        return energy.getMaxEnergyStored();
    }

    /** Actual limit of this loaded backing store, including configuration at construction time. */
    public final int getMachineTransferLimit() { return energy.transferLimit(); }

    /**
     * Draws energy for the machine's own recipe consumption.
     *
     * <p>The owning machine and the outside world have <b>different rights</b> over this buffer, so they
     * reach it by different routes:
     *
     * <ul>
     *   <li>{@link #getCapability} returns {@link #externalView()}, which refuses extraction on an input
     *       hatch — a neighbouring machine must not drain the buffer its owner is filling;</li>
     *   <li>this method draws through the backing store, which is bidirectional, so the owning machine
     *       can always spend its own energy.</li>
     * </ul>
     *
     * <p>An earlier design put the access rule on the store instead, via {@code maxExtract = 0}. Because
     * {@code EnergyStorage#extractEnergy} interprets that as "you get nothing", the rule then applied to
     * <b>every</b> caller and the machine could never power itself — the bug that stalled all recipes at
     * 0 progress against a full buffer. A rate limit cannot express a caller-dependent rule.
     *
     * @return FE actually drawn, which may be less than requested if the buffer is low
     */
    public int consumeInternal(int amount, boolean simulate) {
        int extracted = energy.extractEnergy(amount, simulate);
        // Probe retained until the fix is confirmed by the self-test: it is what distinguished "the
        // internal channel was never reached" (an anonymous-subclass cast failure) from "it was reached
        // and the store still refused". Both looked identical at the caller.
        if (Boolean.getBoolean("modularmachinery.selfTest") && internalProbeLogged < 3) {
            internalProbeLogged++;
            hellfirepvp.modularmachinery.Modularmachinery.LOG.info(
                    "[MMCE][tick] consumeInternal #{}: requested={} extracted={} storedAfter={}",
                    internalProbeLogged, amount, extracted, energy.getEnergyStored());
        }
        return extracted;
    }

    /** Counts probe emissions so the trace shows a few real draws, not just the first. */
    private static int internalProbeLogged = 0;

    /**
     * Deposits energy the machine produced into an output hatch.
     *
     * <p>The mirror of {@link #consumeInternal}: an output hatch has {@code maxReceive = 0} so that
     * external machines cannot push into it, yet the owner must be able to write its product in.
     */
    public int produceInternal(int amount, boolean simulate) {
        return energy.receiveEnergy(amount, simulate);
    }

    public EnergyStorage getEnergyStorage() {
        return energy;
    }

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ENERGY) {
            // The EXTERNAL view, not the raw store: this is where the direction rule is enforced. The
            // store itself stays bidirectional so the owning machine can spend its own buffer.
            return LazyOptional.of(this::externalView).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("energy")) {
            // EnergyStorage is INBTSerializable<Tag>; using its own format (rather than a bare int)
            // keeps the on-disk shape in sync with Forge if that format ever changes.
            energy.deserializeNBT(tag.get("energy"));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("energy", energy.serializeNBT());
    }
}
