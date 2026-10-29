/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.gtceu;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.energy.IEnergyStorage;

/** Recipe-side conversion at the source MMCE rate. Never installed as an external FE capability. */
final class GTCEuEnergyView implements IEnergyStorage {
    static final String REMAINDER_KEY = "modularmachinery:gtceu_fe_remainder";
    static final int FE_PER_EU = 4;
    private final BlockEntity owner;
    private final EnergyHatchPartMachine hatch;

    GTCEuEnergyView(BlockEntity owner, EnergyHatchPartMachine hatch) {
        this.owner = owner;
        this.hatch = hatch;
    }

    private boolean live() {
        return !owner.isRemoved() && owner.getLevel() instanceof ServerLevel level
                && level.getServer().isSameThread() && level.hasChunkAt(owner.getBlockPos())
                && level.getBlockEntity(owner.getBlockPos()) == owner
                && owner instanceof IMachineBlockEntity holder && holder.getMetaMachine() == hatch;
    }

    private int remainder() {
        int saved = owner.getPersistentData().getInt(REMAINDER_KEY);
        return saved >= 0 && saved < FE_PER_EU ? saved : 0;
    }

    private void saveRemainder(int value) {
        if (value == 0) owner.getPersistentData().remove(REMAINDER_KEY);
        else owner.getPersistentData().putInt(REMAINDER_KEY, value);
        owner.setChanged();
    }

    /** Saturate before multiplying: GT stores long EU, whereas Forge Energy exposes int FE. */
    private static int toFe(long eu, int extra) {
        eu = Math.max(0L, eu);
        if (eu > (Integer.MAX_VALUE - (long) extra) / FE_PER_EU) return Integer.MAX_VALUE;
        return (int) (eu * FE_PER_EU + extra);
    }

    @Override public int extractEnergy(int request, boolean simulate) {
        if (request <= 0 || !canExtract()) return 0;
        var energy = hatch.energyContainer;
        int credit = remainder();
        int amount = Math.min(request, toFe(energy.getEnergyStored(), credit));
        if (simulate || amount == 0) return amount;
        long neededEu = Math.max(0L, ((long) amount - credit + FE_PER_EU - 1) / FE_PER_EU);
        long removedEu = neededEu == 0 ? 0 : Math.max(0L, -energy.changeEnergy(-neededEu));
        long available = removedEu * FE_PER_EU + credit; // <= int request + 3
        int delivered = (int) Math.min(amount, available);
        saveRemainder((int) (available - delivered));
        return delivered;
    }

    @Override public int receiveEnergy(int request, boolean simulate) {
        if (request <= 0 || !canReceive()) return 0;
        var energy = hatch.energyContainer;
        int credit = remainder();
        long roomEu = Math.max(0L, energy.getEnergyCapacity() - energy.getEnergyStored());
        // Three fractional FE may wait for the next complete EU, including at a full GT buffer.
        int amount = Math.min(request, toFe(roomEu, FE_PER_EU - 1 - credit));
        if (simulate || amount == 0) return amount;
        long offeredEu = ((long) credit + amount) / FE_PER_EU;
        long addedEu = offeredEu == 0 ? 0 : Math.max(0L, energy.changeEnergy(offeredEu));
        int accepted = (int) Math.min(amount, addedEu * FE_PER_EU + FE_PER_EU - 1 - credit);
        saveRemainder((int) (credit + (long) accepted - addedEu * FE_PER_EU));
        return accepted;
    }

    @Override public int getEnergyStored() {
        return live() ? toFe(hatch.energyContainer.getEnergyStored(), remainder()) : 0;
    }
    @Override public int getMaxEnergyStored() {
        return live() ? toFe(hatch.energyContainer.getEnergyCapacity(), FE_PER_EU - 1) : 0;
    }
    @Override public boolean canExtract() {
        return live() && hatch.energyContainer.getHandlerIO() == IO.IN;
    }
    @Override public boolean canReceive() {
        return live() && hatch.energyContainer.getHandlerIO() == IO.OUT;
    }
}
