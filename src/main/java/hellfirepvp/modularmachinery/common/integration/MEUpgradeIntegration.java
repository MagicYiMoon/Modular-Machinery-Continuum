/*******************************************************************************
 * Modular Machinery: Community Edition — Applied Energistics 2 integration.
 * SPDX-License-Identifier: GPL-3.0-only. See the repository LICENSE.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.integration;

import appeng.api.upgrades.Upgrades;
import appeng.core.definitions.AEItems;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;

/** Registers the AE2 upgrade associations used by the fluid buses. Loaded only when AE2 is present. */
public final class MEUpgradeIntegration {
    private static boolean registered;

    private MEUpgradeIntegration() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        var input = RegistryMEBlocks.itemForms().get("blockmefluidinputbus");
        var output = RegistryMEBlocks.itemForms().get("blockmefluidoutputbus");
        if (input == null || output == null || !input.isPresent() || !output.isPresent()) {
            Modularmachinery.LOG.warn("[MMCE] AE2 upgrade registration skipped: fluid bus item registry objects are not ready");
            return;
        }
        // This is the 1.20.1 equivalent of upstream's Upgrades.CAPACITY.registerItem(..., 5).
        // AE2 15.x takes (upgrade card, machine item, maximum count).
        Upgrades.add(AEItems.CAPACITY_CARD, input.get(), 5);
        Upgrades.add(AEItems.CAPACITY_CARD, output.get(), 5);
        if (hellfirepvp.modularmachinery.common.block.BlockMEGasBus.available()) {
            Upgrades.add(AEItems.CAPACITY_CARD, RegistryMEBlocks.itemForms().get("blockmegasinputbus").get(), 5);
            Upgrades.add(AEItems.CAPACITY_CARD, RegistryMEBlocks.itemForms().get("blockmegasoutputbus").get(), 5);
        }
        registered = true;
        Modularmachinery.LOG.debug("[MMCE] registered AE2 capacity cards for ME fluid buses");
    }
}
