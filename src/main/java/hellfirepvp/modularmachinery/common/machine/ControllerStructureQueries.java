/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.helper.*;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Reads the last measured sparse structure; queries never run the matcher or load chunks. */
public final class ControllerStructureQueries {
    private static final String TAG = "structureQueries";
    private final TileController controller;
    private DynamicMachine definition;
    private MachinePattern pattern;
    private String machineName;
    private Direction facing;
    private Map<String, DynamicPatternInfo> dynamic = Map.of();

    public ControllerStructureQueries(TileController controller) { this.controller = controller; }

    public void record(DynamicMachine machine, MachinePattern pattern, Map<String, Integer> sizes, Direction facing) {
        var next = new LinkedHashMap<String, DynamicPatternInfo>();
        for (var entry : machine.getDynamicPatterns().entrySet()) {
            Integer size = sizes.get(entry.getKey());
            if (size != null) next.put(entry.getKey(), DynamicPatternInfo.measured(entry.getValue(), facing, size));
        }
        boolean changed = !Objects.equals(machineName, machine.getRegistryName()) || this.facing != facing || !dynamic.equals(next);
        definition = machine; this.pattern = pattern; machineName = machine.getRegistryName(); this.facing = facing;
        dynamic = Collections.unmodifiableMap(next);
        if (changed && controller.isFormed()) controller.markNoUpdateSync();
    }

    public void clear() { definition = null; pattern = null; machineName = null; facing = null; dynamic = Map.of(); }
    public void retainForMachine(String name) { if (!Objects.equals(machineName, name)) clear(); }

    private boolean matchesController() {
        return controller.isFormed() && !controller.isRemoved() && Objects.equals(machineName, controller.getMachineName())
                && facing == controller.getFacing();
    }

    private boolean serverAvailable() {
        return controller.getLevel() instanceof ServerLevel && controller.canChangeRuntimeModifiers()
                && matchesController() && definition != null && pattern != null
                && MachineReloadListener.serverDefinitions().machines().findByName(machineName) == definition;
    }

    public IDynamicPatternInfo dynamic(String name) {
        if (name == null) return null;
        if (controller.getLevel() instanceof ServerLevel) {
            if (!serverAvailable()) return null;
        } else if (!matchesController()) return null;
        return dynamic.get(name);
    }

    public BlockPos[] positions(String descriptor) {
        if (!serverAvailable() || descriptor == null || descriptor.isBlank()) return new BlockPos[0];
        var states = new HashSet<>(new BlockStateResolver().resolveAccepted(descriptor));
        return positions(states::contains);
    }

    public BlockPos[] positions(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) return new BlockPos[0];
        return positions(item.getBlock().defaultBlockState());
    }

    public BlockPos[] positions(BlockState state) {
        return state == null ? new BlockPos[0] : positions(candidate -> candidate == state);
    }

    public BlockPos[] positions(IBlockStatePredicate predicate) {
        if (predicate == null || !serverAvailable()) return new BlockPos[0];
        MachinePattern observed = pattern;
        var level = controller.getLevel(); var offsets = new LinkedHashSet<BlockPos>();
        for (var part : observed.getParts()) {
            // Source returns already-rotated relative positions; add controller.pos without rotating again.
            BlockPos offset = StructureMatcher.toWorldOffset(BlockPos.ZERO, facing, part.x(), part.y(), part.z());
            if (!level.hasChunkAt(controller.getBlockPos().offset(offset))) return new BlockPos[0];
            offsets.add(offset);
        }
        var result = new ArrayList<BlockPos>();
        for (var offset : offsets) {
            // A predicate may change the world; do not continue from an invalidated tile/formation.
            if (!serverAvailable() || pattern != observed) return new BlockPos[0];
            BlockPos real = controller.getBlockPos().offset(offset);
            if (!level.hasChunkAt(real)) return new BlockPos[0];
            BlockState state = level.getBlockState(real);
            if (!state.isAir() && predicate.test(state)) result.add(offset);
        }
        return serverAvailable() && pattern == observed ? result.toArray(BlockPos[]::new) : new BlockPos[0];
    }

    public void save(CompoundTag root) {
        if (!controller.isFormed() || machineName == null || facing == null) { root.remove(TAG); return; }
        var tag = new CompoundTag(); tag.putString("machine", machineName); tag.putString("facing", facing.getName());
        var entries = new ListTag();
        for (var info : dynamic.values()) {
            var entry = new CompoundTag(); entry.putString("name", info.name()); entry.putString("facing", info.facing().getName());
            entry.putInt("size", info.size()); entry.putInt("min", info.minSize()); entry.putInt("max", info.maxSize()); entries.add(entry);
        }
        tag.put("patterns", entries); root.put(TAG, tag);
    }

    public void load(CompoundTag root) {
        clear();
        var tag = root.getCompound(TAG); Direction loadedFacing = Direction.byName(tag.getString("facing"));
        if (loadedFacing == null || !loadedFacing.getAxis().isHorizontal() || tag.getString("machine").isBlank()) return;
        var entries = tag.getList("patterns", Tag.TAG_COMPOUND); var loaded = new LinkedHashMap<String, DynamicPatternInfo>();
        for (int i = 0; i < entries.size(); i++) {
            var entry = entries.getCompound(i); Direction direction = Direction.byName(entry.getString("facing"));
            int size = entry.getInt("size"), min = entry.getInt("min"), max = entry.getInt("max"); String name = entry.getString("name");
            if (!name.isBlank() && direction != null && min >= 0 && max >= min && size >= min && size <= max)
                loaded.put(name, new DynamicPatternInfo(name, direction, size, min, max));
        }
        machineName = tag.getString("machine"); facing = loadedFacing; dynamic = Collections.unmodifiableMap(loaded);
        // Servers need real formation/rebind before queries; client/offline metadata observations are read-only.
    }
}
