/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MachineStructurePreviewPanel.getMachineExtraInfo.
 */
package hellfirepvp.modularmachinery.common.machine;

import net.minecraft.network.chat.Component;
import java.util.*;

/** Source preview information, shared by the standalone blueprint and the JEI panel. */
public final class BlueprintMachineInfo {
    private BlueprintMachineInfo() { }

    public static List<Component> tooltip(DynamicMachine machine, BlueprintSceneDefinition definition) {
        var bounds = definition.bounds();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatableWithFallback(machine.getTranslationKey(), machine.getLocalizedName()));
        lines.add(Component.translatable("gui.preview.button.machine_info"));
        lines.add(Component.translatable("gui.preview.button.machine_info.xyz.0"));
        lines.add(Component.translatable("gui.preview.button.machine_info.xyz.1",
                (long) bounds.maxX() - bounds.minX() + 1, (long) bounds.maxY() - bounds.minY() + 1,
                (long) bounds.maxZ() - bounds.minZ() + 1));
        // Source reports the original machine pattern's controller height, not optional exterior upgrades.
        lines.add(Component.translatable("gui.preview.button.machine_info.controller_y_pos",
                machine.getPattern() == null ? 0L : Math.abs((long) machine.getPattern().getMinY())));
        if (machine.getInternalParallelism() > 0) lines.add(Component.translatable(
                "gui.preview.button.machine_info.internal_parallelism", machine.getInternalParallelism()));
        if (machine.getMaxParallelism() != DynamicMachine.DEFAULT_MAX_PARALLELISM) lines.add(Component.translatable(
                "gui.preview.button.machine_info.max_parallelism", machine.getMaxParallelism()));
        // Both controller types are supported for every machine in this port. The translation labels factory-only values.
        lines.add(Component.translatable("gui.preview.button.machine_info.max_threads", machine.getMaxThreads()));
        if (!machine.getCoreThreadPresets().isEmpty()) lines.add(Component.translatable(
                "gui.preview.button.machine_info.core_threads", machine.getCoreThreadPresets().size()));
        if (!machine.getDynamicPatterns().isEmpty()) lines.add(Component.translatable("gui.preview.button.machine_info.dynamic_pattern"));
        if (machine.isRequiresBlueprint()) lines.add(Component.translatable("gui.preview.button.machine_info.requires_blueprint"));
        return List.copyOf(lines);
    }
}
