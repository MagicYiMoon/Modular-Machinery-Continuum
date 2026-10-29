/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.upgrade;

import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.server.level.ServerLevel;
import java.util.Objects;

/** Authority behind a runtime upgrade copy. Inventory instances and counts are never exposed to scripts. */
public final class RuntimeUpgradeAccess {
    final TileUpgradeBus bus;
    final MachineUpgrade source;
    private final TileController controller;
    private final String machine;

    private RuntimeUpgradeAccess(TileController controller, TileUpgradeBus bus, MachineUpgrade source) {
        this.controller = controller; this.bus = bus; this.source = source; machine = controller.getMachineName();
    }
    public static MachineUpgrade view(TileController controller, TileUpgradeBus bus, MachineUpgrade source) {
        MachineUpgrade copy = source instanceof DynamicMachineUpgrade dynamic ? dynamic.copy(dynamic.getParentStack()) : source.copy();
        copy.runtimeAccess = new RuntimeUpgradeAccess(controller, bus, source);
        return copy;
    }
    boolean canWrite() {
        return controller.getLevel() instanceof ServerLevel && controller.canChangeRuntimeModifiers()
                && controller.isFormed() && Objects.equals(machine, controller.getMachineName())
                && controller.getBoundPositions().contains(bus.getBlockPos())
                && bus.getLevel() == controller.getLevel() && !bus.isRemoved()
                && bus.getLevel().hasChunkAt(bus.getBlockPos()) && bus.getLevel().getBlockEntity(bus.getBlockPos()) == bus
                && controller.getBlockPos().equals(bus.getControllerPos()) && source.getType().isCompatible(machine)
                && bus.getInstalledUpgrades().stream().anyMatch(installed -> installed == source)
                && (!(source instanceof DynamicMachineUpgrade dynamic) || dynamic.isValid());
    }
    void persistCustomData() {
        bus.setUpgradeCustomData(source.getType().getName(), source.customData);
        bus.markNoUpdateSync();
    }
}
