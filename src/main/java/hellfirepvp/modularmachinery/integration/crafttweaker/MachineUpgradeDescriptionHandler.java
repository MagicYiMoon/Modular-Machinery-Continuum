/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import hellfirepvp.modularmachinery.common.upgrade.SimpleMachineUpgrade;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import org.openzen.zencode.java.ZenCodeType;

/** CraftTweaker callback used by a static upgrade's upgrade-bus description. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.MachineUpgradeDescriptionHandler")
@FunctionalInterface
public interface MachineUpgradeDescriptionHandler {
    @ZenCodeType.Method
    String[] apply(SimpleMachineUpgrade upgrade);
}
