/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.AdvancedItemModifierCT")
@FunctionalInterface
public interface AdvancedItemModifierCT {
    @ZenCodeType.Method IItemStack apply(TileController controller, IItemStack stack);
}
