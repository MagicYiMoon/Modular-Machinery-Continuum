/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine.component;

import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;
import java.util.Objects;

/** A live machine-facing view. Providers must preserve handler identity and honor simulate/execute. */
public interface ProxiedMachineComponent {
    IOType getIOType();
    <T> @Nullable T getCapability(Capability<T> capability);

    /** A small adapter for the standard item/fluid/FE contracts; no automatic discovery or side guessing. */
    static ProxiedMachineComponent of(IOType direction, @Nullable IItemHandler items,
                                      @Nullable IFluidHandler fluids, @Nullable IEnergyStorage energy) {
        Objects.requireNonNull(direction, "Proxy direction");
        if (items == null && fluids == null && energy == null) throw new IllegalArgumentException("No proxy handlers");
        return new ProxiedMachineComponent() {
            @Override public IOType getIOType() { return direction; }
            @Override @SuppressWarnings("unchecked") public <T> @Nullable T getCapability(Capability<T> capability) {
                if (capability == ForgeCapabilities.ITEM_HANDLER) return (T) items;
                if (capability == ForgeCapabilities.FLUID_HANDLER) return (T) fluids;
                if (capability == ForgeCapabilities.ENERGY) return (T) energy;
                return null;
            }
        };
    }
}
