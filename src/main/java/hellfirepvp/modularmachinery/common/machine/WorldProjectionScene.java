/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Detached world projection states and preview block entities.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Full projection neighbor context, independent of visible layers and never registered with the real level. */
public final class WorldProjectionScene implements AutoCloseable {
    private final Map<BlockPos, WorldProjectionDefinition.Cell> cells;
    private final Map<BlockPos, BlockState> states = new LinkedHashMap<>();
    private final Map<BlockPos, BlockEntity> entities = new HashMap<>();
    private final Map<BlockPos, MachinePattern.Part> parts = new HashMap<>();
    private final Set<Block> loggedFailures = new HashSet<>();
    private final BlueprintPreviewLevel level;
    private BlockPos anchor;
    private Direction facing;
    private long sampleTick;
    private boolean closed;

    public WorldProjectionScene(Level template, BlockPos biomeOrigin, Map<BlockPos, WorldProjectionDefinition.Cell> cells) {
        this.cells = Map.copyOf(cells);
        level = new BlueprintPreviewLevel(template, biomeOrigin, states, entities);
    }

    public BlueprintPreviewLevel level() { return level; }
    public MachinePattern.Part part(BlockPos world) { return parts.get(world); }

    public void update(BlockPos requestedAnchor, Direction requestedFacing, long requestedSampleTick, long worldTick) {
        if (closed) throw new IllegalStateException("World projection scene is closed");
        level.setPreviewTime(worldTick);
        if (requestedAnchor.equals(anchor) && requestedFacing == facing && requestedSampleTick == sampleTick) return;
        boolean samePlacement = requestedAnchor.equals(anchor) && requestedFacing == facing;
        Map<BlockPos, BlockEntity> previous = samePlacement ? new HashMap<>(entities) : Map.of();
        anchor = requestedAnchor.immutable();
        facing = requestedFacing;
        sampleTick = requestedSampleTick;
        states.clear(); entities.clear(); parts.clear();
        cells.forEach((relative, cell) -> {
            BlockPos world = StructureMatcher.toWorldOffset(anchor, facing, relative.getX(), relative.getY(), relative.getZ());
            BlockState state = cell.sample(sampleTick, facing);
            if (state != null && !state.isAir()) {
                states.put(world, state);
                parts.put(world, cell.part());
            }
        });
        // Install all sampled states first so model/entity constructors can read projected neighbors too.
        states.forEach((pos, state) -> {
            BlockEntity entity = previous.get(pos);
            if (entity == null || !entity.getBlockState().equals(state)) {
                try {
                    entity = level.createBlockEntity(pos, state, parts.get(pos).previewNbt());
                } catch (RuntimeException | LinkageError failure) {
                    entity = null;
                    if (loggedFailures.add(state.getBlock())) Modularmachinery.LOG.warn(
                            "[MMCE] cannot create detached world projection block entity for {}", state, failure);
                }
            }
            if (entity != null) entities.put(pos, entity);
        });
    }

    @Override public void close() {
        closed = true;
        entities.clear(); states.clear(); parts.clear(); loggedFailures.clear();
        level.close();
    }
}
