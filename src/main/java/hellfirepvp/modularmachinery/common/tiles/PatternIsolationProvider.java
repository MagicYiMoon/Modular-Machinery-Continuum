/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles;

import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraft.core.BlockPos;

import java.util.List;

/**
 * AE2-free bridge for a machine component that exposes several independent input stores.
 *
 * <p>The source mod's pattern provider returns one component per pattern slot in isolation mode.  The
 * machine context cannot depend on AE2 classes, so the provider publishes the equivalent capability lists
 * through this small interface.</p>
 */
public interface PatternIsolationProvider {

    /** @return independent item handlers, or an empty list when isolation mode is inactive */
    List<? extends IItemHandler> getIsolatedItemHandlers();

    /** @return independent fluid handlers, or an empty list when isolation mode is inactive */
    List<? extends IFluidHandler> getIsolatedFluidHandlers();

    /**
     * Position used to identify the shared source-style isolation groups.  A mirror
     * component returns the bound provider position so that a mirror does not create
     * a second set of 36 groups for the same provider.
     */
    default BlockPos getIsolationGroupPosition() {
        return null;
    }
}
