/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 * Derived from Modular Machinery CE; upstream HellFirePvP / KasumiNova attribution retained.
 */
package hellfirepvp.modularmachinery.common.machine;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

/** Source BlockArrayPreviewRenderHelper.updateLayers: first unfinished Y slice, or none. */
public final class PreviewLayerSelection {
    private PreviewLayerSelection() { }

    /** Projection completion uses its augmented pattern; it never changes the machine's formation pattern. */
    public static Integer lowestIncomplete(Level level, BlockPos anchor, Direction facing,
                                          WorldProjectionDefinition projection) {
        Integer lowest = null;
        for (var entry : projection.cells().entrySet()) {
            BlockPos relative = entry.getKey();
            if (lowest != null && relative.getY() >= lowest) continue;
            BlockPos world = StructureMatcher.toWorldOffset(anchor, facing, relative.getX(), relative.getY(), relative.getZ());
            if (!entry.getValue().matches(level, world, facing)) lowest = relative.getY();
        }
        return lowest;
    }

    public static Integer lowestIncomplete(Level level, BlockPos anchor, Direction facing,
                                          MachinePattern pattern, DynamicMachine machine, MachineLoader loader) {
        Integer lowest = null;
        for (var part : pattern.getParts()) {
            if (lowest != null && part.y() >= lowest) continue;
            BlockPos world = StructureMatcher.toWorldOffset(anchor, facing, part.x(), part.y(), part.z());
            if (!level.isLoaded(world) || !StructureMatcher.matchesAny(level.getBlockState(world),
                    StructureMatcher.acceptedStates(part, loader.getVariables(), loader.getResolver(),
                            machine.getModifierReplacements()), facing) || !StructureMatcher.matchesPartNbt(level, world, part)) {
                lowest = part.y();
            }
        }
        return lowest;
    }
}
