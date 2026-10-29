/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Server-thread index of formed structure cells. Block writes only invalidate; matching stays in the ticker. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class MachineStructureChanges {
    private static final Map<ServerLevel, Index> LEVELS = new IdentityHashMap<>();
    private record Watch(Set<Long> positions, Set<Long> chunks) { }
    private static final class Index {
        final Map<TileController, Watch> controllers = new IdentityHashMap<>();
        final Map<Long, Set<TileController>> chunks = new HashMap<>();
    }
    private MachineStructureChanges() { }

    public static void watch(TileController controller, MachinePattern pattern) {
        if (!(controller.getLevel() instanceof ServerLevel level) || !controller.isFormed()) return;
        Set<Long> positions = new HashSet<>(), chunks = new HashSet<>();
        BlockPos origin = controller.getBlockPos();
        Direction facing = controller.getBlockState().hasProperty(BlockController.FACING)
                ? controller.getBlockState().getValue(BlockController.FACING) : Direction.NORTH;
        positions.add(origin.asLong());
        chunks.add(ChunkPos.asLong(origin.getX() >> 4, origin.getZ() >> 4));
        for (var part : pattern.getParts()) {
            BlockPos pos = StructureMatcher.toWorldOffset(origin, facing, part.x(), part.y(), part.z());
            positions.add(pos.asLong());
            chunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
        Index index = LEVELS.computeIfAbsent(level, ignored -> new Index());
        Watch previous = index.controllers.get(controller);
        if (previous != null && previous.positions.equals(positions)) return;
        remove(index, controller);
        index.controllers.put(controller, new Watch(Set.copyOf(positions), Set.copyOf(chunks)));
        for (long chunk : chunks) index.chunks.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(controller);
    }

    public static void unwatch(TileController controller) {
        if (!(controller.getLevel() instanceof ServerLevel level)) return;
        Index index = LEVELS.get(level);
        if (index == null) return;
        remove(index, controller);
        if (index.controllers.isEmpty()) LEVELS.remove(level);
    }

    private static void remove(Index index, TileController controller) {
        Watch old = index.controllers.remove(controller);
        if (old == null) return;
        for (long chunk : old.chunks) {
            Set<TileController> controllers = index.chunks.get(chunk);
            controllers.remove(controller);
            if (controllers.isEmpty()) index.chunks.remove(chunk);
        }
    }

    public static void blockChanged(ServerLevel level, BlockPos pos) {
        Index index = LEVELS.get(level);
        if (index == null) return;
        var controllers = index.chunks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        if (controllers == null) return;
        for (TileController controller : controllers) {
            Watch watch = index.controllers.get(controller);
            if (!controller.isRemoved() && watch != null && watch.positions.contains(pos.asLong()))
                controller.requestStructureCheck();
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) LEVELS.remove(level);
    }
}
