/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from DynamicMachineRenderContext's projection pattern.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** A detached projection pattern: controller + expanded structure + single-block upgrade alternatives. */
public final class WorldProjectionDefinition {
    public record Cell(MachinePattern.Part part, List<BlockState> states, boolean controller) {
        public Cell { states = List.copyOf(states); }

        public BlockState sample(long tick, Direction facing) {
            return orient(PreviewCellDecision.sampleStateForPreview(states, tick), facing);
        }

        private BlockState orient(BlockState state, Direction facing) {
            // The origin controller follows the anchor; formed is a visual property absent from source metadata.
            return controller && state != null && state.getBlock() instanceof BlockController
                    ? state.setValue(BlockController.FACING, facing) : StructureMatcher.rotateState(state, facing);
        }

        public boolean matches(Level level, BlockPos world, Direction facing) {
            if (level == null || !level.isLoaded(world)) return false;
            BlockState actual = level.getBlockState(world);
            if (controller && actual.getBlock() instanceof BlockController) {
                actual = actual.setValue(BlockController.FORMED, false);
            }
            for (BlockState expected : states) {
                if (actual.equals(orient(expected, facing))) return StructureMatcher.matchesPartNbt(level, world, part);
            }
            return false;
        }
    }

    private final Map<BlockPos, Cell> cells;

    public WorldProjectionDefinition(DynamicMachine machine, MachinePattern expandedPattern,
                                     MachineVariables variables, BlockStateResolver resolver) {
        Map<BlockPos, Cell> built = new LinkedHashMap<>();
        for (var part : expandedPattern.getParts()) {
            built.put(new BlockPos(part.x(), part.y(), part.z()), new Cell(part,
                    List.copyOf(new LinkedHashSet<>(StructureMatcher.acceptedStates(part, variables, resolver, List.of()))), false));
        }
        // Keep the port's existing rule allowing both controller types for every machine.
        built.put(BlockPos.ZERO, new Cell(new MachinePattern.Part(0, 0, 0, "modularmachinery:blockcontroller"),
                List.of(RegistryBlocks.blockController.get().defaultBlockState(),
                        RegistryBlocks.blockFactoryController.get().defaultBlockState()), true));
        // Source getModifiersAsMatchingReplacements includes single-block conditions only, including exterior cells.
        for (var replacement : machine.getModifierReplacements()) {
            BlockPos pos = new BlockPos(replacement.getX(), replacement.getY(), replacement.getZ());
            Cell old = built.get(pos);
            Set<BlockState> merged = new LinkedHashSet<>(old == null ? List.of() : old.states());
            merged.addAll(resolver.resolveAccepted(replacement.getBlockId()));
            built.put(pos, new Cell(old == null ? new MachinePattern.Part(pos.getX(), pos.getY(), pos.getZ(),
                    replacement.getBlockId()) : old.part(), List.copyOf(merged), pos.equals(BlockPos.ZERO)));
        }
        cells = Collections.unmodifiableMap(built);
    }

    public Map<BlockPos, Cell> cells() { return cells; }
}
