/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.upgrade;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.tiles.TileController;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Dispatches the live callbacks attached to installed upgrades.
 *
 * <p>The source mod calls this path from {@code MachineEvent.postEvent()} before ordinary CraftTweaker handlers.
 * The port keeps that order and resolves the installed upgrades from the controller at dispatch time, so a bus
 * change affects the next event and no callback is ever retained in a saved NBT tag.</p>
 */
public final class ScriptUpgradeEventRegistry {
    private ScriptUpgradeEventRegistry() { }

    public static void dispatch(MachineEvent event) {
        if (event == null) return;
        TileController controller = event.getController();
        if (controller == null || controller.getLevel() == null) return;

        List<MachineUpgrade> upgrades;
        try {
            upgrades = controller.getInstalledUpgradesForEvents();
        } catch (Throwable failure) {
            Modularmachinery.LOG.warn("[MMCE] Could not resolve upgrades for {}", event.getClass().getSimpleName(), failure);
            return;
        }
        for (MachineUpgrade upgrade : upgrades) {
            if (upgrade instanceof DynamicMachineUpgrade dynamic && dynamic.isInvalid()) continue;
            for (BiConsumer<MachineEvent, MachineUpgrade> callback : upgrade.eventHandlers(event.getClass())) {
                try {
                    callback.accept(event, upgrade);
                } catch (Throwable failure) {
                    Modularmachinery.LOG.warn("[MMCE] Upgrade event handler failed for {} ({})",
                            upgrade.getType().getName(), event.getClass().getSimpleName(), failure);
                }
                if (event.isCanceled()) return;
            }
        }
    }
}
