/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.upgrade;

import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.server.level.ServerLevel;
import java.util.*;

/** Queries only the declared conditions and recorded loaded components; never scans a structure volume. */
public final class ControllerUpgradeQueries {
    private ControllerUpgradeQueries() { }
    private static boolean available(TileController controller, boolean requireFormed) {
        return controller.getLevel() instanceof ServerLevel && controller.canChangeRuntimeModifiers()
                && (!requireFormed || controller.isFormed()) && controller.getMachineName() != null;
    }
    public static String[] foundModifiers(TileController controller) {
        if (!available(controller, true)) return new String[0];
        var machine = MachineReloadListener.getLoader().findByName(controller.getMachineName());
        if (machine == null) return new String[0];
        var found = new LinkedHashSet<String>(); var level = (ServerLevel) controller.getLevel();
        for (var replacement : machine.getModifierReplacements())
            if (replacement.matches(level, controller.getBlockPos(), controller.getFacing())) found.add(replacement.getName());
        for (var replacement : machine.getMultiBlockModifierReplacements())
            if (replacement.matches(level, controller.getBlockPos(), controller.getFacing(), MachineReloadListener.getLoader().getResolver()))
                found.add(replacement.getName());
        return found.toArray(String[]::new);
    }
    public static List<MachineUpgrade> installed(TileController controller, boolean requireFormed) {
        if (!available(controller, requireFormed)) return List.of();
        var level = controller.getLevel(); var found = new ArrayList<MachineUpgrade>();
        var merged = new LinkedHashMap<UpgradeType, MachineUpgrade>();
        for (var pos : new LinkedHashSet<>(controller.getBoundPositions())) {
            if (!level.hasChunkAt(pos) || !(level.getBlockEntity(pos) instanceof TileUpgradeBus bus)
                    || bus.isRemoved() || !controller.getBlockPos().equals(bus.getControllerPos())) continue;
            for (var source : bus.getUpgradesFor(controller.getMachineName())) {
                if (source instanceof DynamicMachineUpgrade dynamic) {
                    if (dynamic.isValid()) found.add(RuntimeUpgradeAccess.view(controller, bus, source));
                    continue;
                }
                var previous = merged.get(source.getType());
                if (previous == null) {
                    var view = RuntimeUpgradeAccess.view(controller, bus, source);
                    int cap = Math.max(0, source.getType().getMaxStackSize());
                    if (view.getStackSize() > cap) view.decrementStackSize(view.getStackSize() - cap);
                    if (view.getStackSize() == 0) continue;
                    merged.put(view.getType(), view); found.add(view);
                } else {
                    int room = previous.getType().getMaxStackSize() - previous.getStackSize();
                    if (room > 0) previous.incrementStackSize(Math.min(room, source.getStackSize()));
                }
            }
        }
        return List.copyOf(found);
    }
}
