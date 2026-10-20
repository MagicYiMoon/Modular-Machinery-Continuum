/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting;

import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.world.item.ItemStack;

@FunctionalInterface
public interface ItemRequirementChecker {
    boolean test(TileController controller, ItemStack stack);
}
