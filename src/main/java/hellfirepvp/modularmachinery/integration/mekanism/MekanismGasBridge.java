/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.mekanism;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.tiles.base.GasTankAccess;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;

/** Mekanism-only implementation. No core class has a Mekanism type in its signature. */
public final class MekanismGasBridge {
    private MekanismGasBridge() { }

    public static Capability<IGasHandler> capability() { return Capabilities.GAS_HANDLER; }

    public static boolean has( GasTankAccess tank) {
        if (tank == null) return false;
        CompoundTag tag = tank.getGasTag();
        if (tag.isEmpty()) return false;
        return !GasStack.readFromNBT(tag).isEmpty();
    }

    public static long amount(GasTankAccess tank) {
        GasStack gas = read(tank);
        return gas.isEmpty() ? 0 : gas.getAmount();
    }

    public static String description(GasTankAccess tank) {
        GasStack gas = read(tank);
        return gas.isEmpty() ? "" : gas.getTextComponent().getString();
    }

    public static GasStack read(GasTankAccess tank) {
        return tank == null ? GasStack.EMPTY : GasStack.readFromNBT(tank.getGasTag());
    }

    public static IGasHandler handler(GasTankAccess tank) {
        return handler(tank, false);
    }

    /** Handler used by the owning recipe; it bypasses the external direction gate just like fluid hatches do. */
    public static IGasHandler handler(GasTankAccess tank, boolean internal) {
        return new IGasHandler() {
            @Override public int getTanks() { return 1; }
            @Override public GasStack getChemicalInTank(int index) { return index == 0 ? read(tank).copy() : GasStack.EMPTY; }
            @Override public void setChemicalInTank(int index, GasStack stack) {
                if (index != 0) return;
                if (stack == null || stack.isEmpty()) tank.clearGas();
                else { tank.setGasTag(stack.copy().write(new CompoundTag())); }
            }
            @Override public long getTankCapacity(int index) { return index == 0 ? tank.getGasCapacity() : 0; }
            @Override public boolean isValid(int index, GasStack stack) {
                return index == 0 && stack != null && !stack.isEmpty() &&
                        (read(tank).isEmpty() || read(tank).isTypeEqual(stack));
            }
            @Override public GasStack insertChemical(int index, GasStack stack, Action action) {
                if (index != 0 || stack == null || stack.isEmpty() || (!internal && !tank.canAcceptGas())) return stack == null ? GasStack.EMPTY : stack.copy();
                if (tank instanceof hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank hatch
                        && !hatch.getTank().isEmpty()) return stack.copy();
                GasStack current = read(tank);
                if (!current.isEmpty() && !current.isTypeEqual(stack)) return stack.copy();
                long room = Math.max(0, tank.getGasCapacity() - current.getAmount());
                long accepted = Math.min(room, stack.getAmount());
                if (action.execute() && accepted > 0) {
                    GasStack result = new GasStack(stack, current.getAmount() + accepted);
                    tank.setGasTag(result.write(new CompoundTag()));
                }
                return new GasStack(stack, stack.getAmount() - accepted);
            }
            @Override public GasStack extractChemical(int index, long amount, Action action) {
                if (index != 0 || amount <= 0 || (!internal && !tank.canExtractGas())) return GasStack.EMPTY;
                GasStack current = read(tank);
                if (current.isEmpty()) return GasStack.EMPTY;
                long extracted = Math.min(amount, current.getAmount());
                GasStack result = new GasStack(current, extracted);
                if (action.execute()) {
                    long left = current.getAmount() - extracted;
                    if (left <= 0) tank.clearGas(); else tank.setGasTag(new GasStack(current, left).write(new CompoundTag()));
                }
                return result;
            }
        };
    }
}
