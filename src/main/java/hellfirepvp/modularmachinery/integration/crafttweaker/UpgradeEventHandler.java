/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.upgrade.MachineUpgrade;
import org.openzen.zencode.java.ZenCodeType;

/** A CraftTweaker callback receiving both the event and the installed upgrade that declared it. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.UpgradeEventHandler")
@FunctionalInterface
public interface UpgradeEventHandler {
    @ZenCodeType.Method
    void handle(MachineEvent event, MachineUpgrade upgrade);
}
