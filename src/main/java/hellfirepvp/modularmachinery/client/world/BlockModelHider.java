/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.client.world;

import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Client-side world model suppression for machines declaring hide-components-when-formed. */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlockModelHider {
    /**
     * A position alone is not a world identity: the same x/y/z can exist in the overworld, nether and every
     * modded dimension.  The first implementation keyed both maps only by {@link BlockPos}, so a formed machine at
     * (0,64,0) in one dimension hid the block at (0,64,0) in every other dimension while the client was rendering it.
     * Keep the dimension in the key and in the lookup so model suppression cannot leak across worlds.
     */
    private record ControllerKey(ResourceKey<Level> dimension, BlockPos position) {
        private ControllerKey {
            position = position.immutable();
        }
    }

    private static final Map<ControllerKey, Set<BlockPos>> BY_CONTROLLER = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> HIDDEN_BY_DIMENSION = new HashMap<>();

    private BlockModelHider() { }

    public static boolean isHidden(Level level, BlockPos position) {
        if (level == null || !level.isClientSide) return false;
        return HIDDEN_BY_DIMENSION.getOrDefault(level.dimension(), Set.of()).contains(position);
    }

    /** Replaces the controller's previous set atomically and marks affected sections for rebuild. */
    public static void update(TileController controller) {
        if (controller == null || controller.getLevel() == null || !controller.getLevel().isClientSide) return;
        Level level = controller.getLevel();
        ControllerKey key = new ControllerKey(level.dimension(), controller.getBlockPos());
        Set<BlockPos> old = BY_CONTROLLER.remove(key);
        if (old != null) {
            removeHidden(level, old);
            markDirty(level, old);
        }
        DynamicMachine machine = MachineReloadListener.getLoader().findByName(controller.getMachineName());
        if (!controller.isFormed() || machine == null || !machine.isHideComponentsWhenFormed()) return;
        Set<BlockPos> next = new HashSet<>();
        next.add(key.position());
        controller.getHiddenPositions().forEach(position -> next.add(position.immutable()));
        BY_CONTROLLER.put(key, next);
        HIDDEN_BY_DIMENSION.computeIfAbsent(level.dimension(), ignored -> new HashSet<>()).addAll(next);
        markDirty(level, next);
    }

    private static void removeController(ControllerKey key) {
        Set<BlockPos> old = BY_CONTROLLER.remove(key);
        if (old != null) {
            Level level = Minecraft.getInstance().level;
            removeHidden(level, old);
            markDirty(level, old);
        }
    }

    private static void removeHidden(Level level, Set<BlockPos> positions) {
        if (level == null) return;
        Set<BlockPos> hidden = HIDDEN_BY_DIMENSION.get(level.dimension());
        if (hidden == null) return;
        hidden.removeAll(positions);
        if (hidden.isEmpty()) HIDDEN_BY_DIMENSION.remove(level.dimension());
    }

    private static void markDirty(Level level, Set<BlockPos> positions) {
        Minecraft minecraft = Minecraft.getInstance();
        if (level == null || minecraft.level == null || minecraft.levelRenderer == null
                || minecraft.level.dimension() != level.dimension()) return;
        positions.forEach(pos -> minecraft.levelRenderer.setBlocksDirty(
                pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ()));
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide() && event.getLevel() instanceof Level level) clearDimension(level);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clear();
            return;
        }
        // A broken controller block removes its block entity without calling our common-side setter.
        // Prune these keys periodically so the hidden model does not remain until logout.
        if (minecraft.level.getGameTime() % 20 == 0) {
            ResourceKey<Level> dimension = minecraft.level.dimension();
            new HashSet<>(BY_CONTROLLER.keySet()).forEach(key -> {
                if (!key.dimension().equals(dimension)) return;
                if (!(minecraft.level.getBlockEntity(key.position()) instanceof TileController)) removeController(key);
            });
        }
    }

    public static void clear() {
        Minecraft minecraft = Minecraft.getInstance();
        Level level = minecraft.level;
        Set<BlockPos> old = level == null ? Set.of()
                : new HashSet<>(HIDDEN_BY_DIMENSION.getOrDefault(level.dimension(), Set.of()));
        BY_CONTROLLER.clear();
        HIDDEN_BY_DIMENSION.clear();
        if (!old.isEmpty()) markDirty(level, old);
    }

    /** Removes only one dimension's entries when Forge unloads a client level. */
    private static void clearDimension(Level level) {
        if (level == null) return;
        ResourceKey<Level> dimension = level.dimension();
        Set<BlockPos> old = new HashSet<>(HIDDEN_BY_DIMENSION.getOrDefault(dimension, Set.of()));
        BY_CONTROLLER.keySet().removeIf(key -> key.dimension().equals(dimension));
        HIDDEN_BY_DIMENSION.remove(dimension);
        if (!old.isEmpty()) markDirty(level, old);
    }
}
