/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.helper;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import net.minecraft.world.level.block.state.BlockState;
import org.openzen.zencode.java.ZenCodeType;

@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.IBlockStatePredicate")
@FunctionalInterface
public interface IBlockStatePredicate {
    @ZenCodeType.Method boolean test(BlockState state);
}
