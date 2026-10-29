/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.modifier;

import hellfirepvp.modularmachinery.common.machine.BlockStateResolver;
import hellfirepvp.modularmachinery.common.machine.StructureMatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A structure condition made of several cells.  This is the 1.20.1 equivalent of the source
 * mod's {@code MultiBlockModifierReplacement}: every declared cell must match before the recipe
 * modifiers become active.  Conditions are intentionally independent of the machine's normal
 * pattern, so a pack can use a separate multiblock arrangement as an optional upgrade.
 */
public final class MultiBlockModifierReplacement {
    public record Cell(BlockPos offset, String blockId) {
        public Cell {
            if (offset == null || blockId == null || blockId.isBlank()) {
                throw new IllegalArgumentException("modifier cell needs an offset and block id");
            }
            offset = offset.immutable();
        }
    }

    private final String name;
    private final List<Cell> cells;
    private final List<RecipeModifier> modifiers;
    private final List<String> descriptions;

    public MultiBlockModifierReplacement(String name, List<Cell> cells,
                                         List<RecipeModifier> modifiers,
                                         List<String> descriptions) {
        if (cells == null || cells.isEmpty()) {
            throw new IllegalArgumentException("multiblock modifier needs at least one cell");
        }
        this.name = name == null ? ModifierReplacement.nextDefaultName() : name;
        this.cells = List.copyOf(cells);
        this.modifiers = modifiers == null ? List.of() : List.copyOf(modifiers);
        this.descriptions = descriptions == null ? List.of() : List.copyOf(descriptions);
    }

    public String getName() { return name; }
    public List<Cell> getCells() { return cells; }
    public List<RecipeModifier> getModifiers() { return modifiers; }
    public List<String> getDescriptions() { return descriptions; }

    /**
     * Tests all cells with the same rotation and metadata semantics as the normal structure matcher.
     * A bare block id accepts any state of that block; an explicit {@code @metadata} mapping is exact.
     */
    public boolean matches(Level level, BlockPos controllerPos, Direction facing) {
        return matches(level, controllerPos, facing, new BlockStateResolver());
    }

    /**
     * Same test using the loader's shared resolver.  Runtime checks should use this overload so
     * unresolved legacy ids are reported in the normal machine-load worklist instead of being
     * hidden in a short-lived resolver.
     */
    public boolean matches(Level level, BlockPos controllerPos, Direction facing,
                           BlockStateResolver resolver) {
        if (level == null || controllerPos == null || facing == null) return false;
        if (resolver == null) return false;
        for (Cell cell : cells) {
            BlockPos world = StructureMatcher.toWorldOffset(controllerPos, facing,
                    cell.offset().getX(), cell.offset().getY(), cell.offset().getZ());
            if (!level.isLoaded(world)) return false;
            List<BlockState> accepted = resolver.resolveAccepted(cell.blockId());
            if (accepted.isEmpty() || !StructureMatcher.matchesAny(level.getBlockState(world), accepted, facing)) {
                return false;
            }
        }
        return true;
    }
}
