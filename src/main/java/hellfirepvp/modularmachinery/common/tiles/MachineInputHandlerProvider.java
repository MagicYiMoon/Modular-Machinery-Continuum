/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;

/**
 * Machine-side input bridge for components whose public Forge capability has a
 * different purpose.  The 1.12 ME pattern provider exposed its encoded-pattern
 * inventory through the item capability, while its {@code provideComponent()}
 * exposed the cached ingredients to Modular Machinery.  Keeping that distinction
 * explicit prevents the machine from accidentally consuming encoded patterns.
 */
public interface MachineInputHandlerProvider {
    IItemHandler machineItemHandler();
    IFluidHandler machineFluidHandler();
}
