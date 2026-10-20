/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.base;

import net.minecraftforge.items.IItemHandler;

/** Supplies an inventory that the shared block break hook must drop. */
public interface BreakInventoryProvider {
    IItemHandler getBreakInventory();
}
