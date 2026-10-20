/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.upgrade.SimpleDynamicMachineUpgrade;
import org.openzen.zencode.java.ZenCodeType;

/** CraftTweaker callback used by dynamic item and upgrade-bus descriptions. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.DynamicUpgradeDescriptionHandler")
@FunctionalInterface
public interface DynamicUpgradeDescriptionHandler {
    @ZenCodeType.Method
    String[] apply(SimpleDynamicMachineUpgrade upgrade);
}
