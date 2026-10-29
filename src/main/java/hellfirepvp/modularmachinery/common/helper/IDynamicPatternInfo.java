/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.helper;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import org.openzen.zencode.java.ZenCodeType;

/** Immutable observation of a measured segment, matching source IDynamicPatternInfo. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.IDynamicPatternInfo")
public interface IDynamicPatternInfo {
    @ZenCodeType.Getter("name") String getPatternName();
    @ZenCodeType.Getter("facing") String getFacing();
    @ZenCodeType.Getter("size") int getSize();
    @ZenCodeType.Getter("minSize") int getMinSize();
    @ZenCodeType.Getter("maxSize") int getMaxSize();
}
