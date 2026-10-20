/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.item.IItemStack;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.AdvancedItemCheckerCT")
@FunctionalInterface
public interface AdvancedItemCheckerCT {
    @ZenCodeType.Method boolean isMatch(TileController controller, IItemStack stack);
}
