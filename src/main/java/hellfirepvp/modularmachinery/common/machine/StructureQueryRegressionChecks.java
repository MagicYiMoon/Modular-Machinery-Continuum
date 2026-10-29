/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.helper.IBlockStatePredicate;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Real formation, source offset constants and live state queries, not a second implementation of the matcher. */
public final class StructureQueryRegressionChecks {
    public static final String TARGET = "mmc_structure_query_regression";
    private StructureQueryRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var loader = MachineReloadListener.getLoader(); var machines = loader.getMachines();
        var originalMachines = new LinkedHashMap<>(machines); var saved = new LinkedHashMap<BlockPos, BlockState>();
        boolean scripts = net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker");
        try {
            int index = 0;
            for (boolean factory : List.of(false, true)) for (Direction facing : List.of(Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST)) {
                BlockPos pos = origin.offset(index++ * 12, 0, 0);
                var base = new MachinePattern();
                base.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:stone,minecraft:gold_block"));
                base.addPart(new MachinePattern.Part(-1, 0, 0, "minecraft:glass"));
                base.addPart(new MachinePattern.Part(-1, 1, 0, "minecraft:lava"));
                base.addPart(new MachinePattern.Part(-1, 0, 1, "minecraft:quartz_stairs"));
                base.addPart(new MachinePattern.Part(-1, 0, -1, "minecraft:iron_block"));
                base.addPart(new MachinePattern.Part(0, 1, 0, "minecraft:air"));
                base.addPart(new MachinePattern.Part(0, -1, 0, "modularmachinery:blockinputbus"));
                var machine = new DynamicMachine(TARGET, TARGET, base);
                var line = new DynamicPattern("line"); line.addFace(Direction.NORTH); line.setMinSize(1); line.setMaxSize(4);
                line.setStructureSizeOffsetStart(new BlockPos(1, 0, 0)); line.setStructureSizeOffset(new BlockPos(1, 0, 0));
                var segment = new MachinePattern(); segment.addPart(new MachinePattern.Part(0, 0, 0, "minecraft:stone")); line.setPattern(segment);
                var cap = new MachinePattern(); cap.addPart(new MachinePattern.Part(0, 0, 0, "minecraft:gold_block")); line.setPatternEnd(cap);
                machine.addDynamicPattern(line);
                var optional = new DynamicPattern("optional"); optional.addFace(Direction.UP); optional.setMinSize(0); optional.setMaxSize(2);
                optional.setStructureSizeOffsetStart(new BlockPos(0, 3, 0)); optional.setStructureSizeOffset(new BlockPos(0, 1, 0));
                var emerald = new MachinePattern(); emerald.addPart(new MachinePattern.Part(0, 0, 0, "minecraft:emerald_block")); optional.setPattern(emerald);
                machine.addDynamicPattern(optional);
                machines.put(new ResourceLocation("modularmachinery", TARGET), machine);
                put(level, pos, (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get())
                        .defaultBlockState().setValue(BlockController.FACING, facing), saved);
                var tile = (TileController) level.getBlockEntity(pos); tile.setMachineName(TARGET);
                putLocal(level, pos, facing, -1, 0, 0, Blocks.GLASS.defaultBlockState(), saved);
                putLocal(level, pos, facing, -1, 1, 0, Blocks.LAVA.defaultBlockState(), saved);
                putLocal(level, pos, facing, -1, 0, 1, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST), saved);
                putLocal(level, pos, facing, -1, 0, -1, Blocks.IRON_BLOCK.defaultBlockState(), saved);
                putLocal(level, pos, facing, 0, 1, 0, Blocks.AIR.defaultBlockState(), saved);
                putLocal(level, pos, facing, 0, -1, 0, RegistryBlocks.itemInputBus.get().defaultBlockState(), saved);
                putLocal(level, pos, facing, 0, 0, 2, Blocks.STONE.defaultBlockState(), saved); // Inside bounds but undeclared.
                for (int x = 1; x <= 5; x++) putLocal(level, pos, facing, x, 0, 0,
                        (x <= 2 ? Blocks.STONE : x == 3 ? Blocks.GOLD_BLOCK : Blocks.AIR).defaultBlockState(), saved);
                for (int y = 3; y <= 5; y++) putLocal(level, pos, facing, 0, y, 0, Blocks.AIR.defaultBlockState(), saved);
                require(tile.getDynamicPattern("line") == null && tile.getBlocksInPattern("minecraft:stone") == 0, "unformed queries leaked structure");
                form(level, tile, machine, loader);
                check(tile, facing, 2);
                require(tile.getBlocksInPattern("minecraft:quartz_stairs") == 1
                        && tile.getBlocksInPattern(Blocks.QUARTZ_STAIRS.defaultBlockState()) == 0
                        && tile.getBlocksInPattern("minecraft:quartz_stairs@0") == 1, "block/state selector semantics differ");
                int[] predicateCalls = {0};
                require(tile.getBlocksInPattern((IBlockStatePredicate) state -> { predicateCalls[0]++; return true; }) == 8
                        && predicateCalls[0] == 8, "air, duplicate/undeclared positions or cap counted incorrectly");
                require(tile.getBlocksInPattern("minecraft:lava") == 1 && tile.getBlocksInPattern("minecraft:air") == 0
                        && tile.getBlocksInPattern("not a valid block") == 0, "fluid/air/invalid selectors differ");
                var retained = tile.getDynamicPattern("line");
                if (scripts) hellfirepvp.modularmachinery.integration.crafttweaker.StructureQueryScriptRegressionChecks.run(tile);
                int[] updateEvents = {0}, updatedSize = {-1};
                ScriptMachineEventRegistry.register(TARGET, hellfirepvp.modularmachinery.common.event.machine.MachineStructureUpdateEvent.class, event -> {
                    updateEvents[0]++;
                    var measured = event.getController().getDynamicPattern("line");
                    updatedSize[0] = measured == null ? -1 : measured.getSize();
                });
                var savedTag = tile.getUpdateTag();
                TileController observation = factory ? new TileFactoryController(pos, tile.getBlockState()) : new TileController(pos, tile.getBlockState());
                observation.load(savedTag);
                require(observation.getDynamicPattern("line").getSize() == 2 && observation.getBlocksInPattern("minecraft:stone") == 0,
                        "detached metadata missing or world scan allowed");
                // Deserialization into the same installed object must invalidate its old pattern before rebind.
                tile.load(savedTag);
                require(tile.getDynamicPattern("line") == null && tile.getBlocksInPattern("minecraft:stone") == 0, "load reused an unvalidated world pattern");
                MachineFormation.rebind(level, pos, facing, machine, loader); check(tile, facing, 2);
                form(level, tile, machine, loader);
                require(updateEvents[0] == 1 && updatedSize[0] == 2, "successful recheck did not publish one measured structure update");
                // Grow to three measured segments without changing the declared minimum or the retained observation.
                putLocal(level, pos, facing, 3, 0, 0, Blocks.STONE.defaultBlockState(), saved);
                putLocal(level, pos, facing, 4, 0, 0, Blocks.GOLD_BLOCK.defaultBlockState(), saved);
                form(level, tile, machine, loader); check(tile, facing, 3);
                require(updateEvents[0] == 2 && updatedSize[0] == 3, "growth callback observed old segment size");
                require(retained.getSize() == 2 && observation.getDynamicPattern("line").getSize() == 2, "old observations changed on growth");
                putLocal(level, pos, facing, 2, 0, 0, Blocks.GOLD_BLOCK.defaultBlockState(), saved);
                form(level, tile, machine, loader); check(tile, facing, 1);
                require(updateEvents[0] == 3 && updatedSize[0] == 1, "shrink callback observed old segment size");
                // Read current world blocks rather than substituting the definition's expected material.
                putLocal(level, pos, facing, 1, 0, 0, Blocks.DIAMOND_BLOCK.defaultBlockState(), saved);
                require(tile.getBlocksInPattern("minecraft:stone") == 0 && tile.getBlocksInPattern("minecraft:diamond_block") == 1, "query used expected states instead of world states");
                var async = java.util.concurrent.CompletableFuture.supplyAsync(() -> tile.getDynamicPattern("line") == null
                        && tile.getBlocksInPattern("minecraft:stone") == 0).get();
                require(async, "async query reached world state");
                // A recorded position in an absent chunk must not be loaded or produce partial counts/callbacks.
                BlockPos far = new BlockPos(1_600_000, pos.getY(), pos.getZ()); require(!level.hasChunkAt(far), "far fixture already loaded");
                var sparse = new MachinePattern(); sparse.addPart(new MachinePattern.Part(-1, 0, 0, "minecraft:glass"));
                BlockPos farOffset = inverseOffset(facing, far.subtract(pos));
                sparse.addPart(new MachinePattern.Part(farOffset.getX(), farOffset.getY(), farOffset.getZ(), "minecraft:stone"));
                tile.recordFormedStructure(machine, sparse, Map.of("line", 1, "optional", 0), facing);
                int[] unloadedCalls = {0};
                require(tile.getBlocksInPattern((IBlockStatePredicate) state -> { unloadedCalls[0]++; return true; }) == 0
                        && unloadedCalls[0] == 0 && !level.hasChunkAt(far), "query loaded a chunk or returned partial data");
                machines.put(new ResourceLocation("modularmachinery", TARGET), new DynamicMachine(TARGET, TARGET, base));
                require(tile.getDynamicPattern("line") == null && tile.getBlocksInPattern("minecraft:glass") == 0,
                        "definition replacement retained a stale formation");
                machines.put(new ResourceLocation("modularmachinery", TARGET), machine);
                tile.recordFormedStructure(machine, base, Map.of("line", 1, "optional", 0), facing);
                require(tile.getBlocksInPattern((IBlockStatePredicate) state -> { tile.setFormed(false); return true; }) == 0,
                        "predicate invalidation returned a partial result");
                require(tile.getDynamicPattern("line") == null && tile.getBlocksInPattern("minecraft:glass") == 0, "unforming retained active metadata");
                cleanupCallbacks();
            }
            Modularmachinery.LOG.info("[MMC][structure-queries] PASS: ordinary/factory four facings, actual/zero segments, rotated offsets, states/air/fluid, growth/shrink, save/rebind, stale/async/unloaded guards");
        } finally {
            cleanupCallbacks();
            saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
            machines.clear(); machines.putAll(originalMachines); MachineReloadListener.refreshRecipes();
        }
    }
    private static void check(TileController tile, Direction facing, int size) {
        var info = tile.getDynamicPattern("line");
        require(info != null && info.getSize() == size && info.getMinSize() == 1 && info.getMaxSize() == 4
                && info.getPatternName().equals("line") && info.getFacing().equals(sourceMatchFacing(facing)), "dynamic snapshot differs from measured source values");
        var zero = tile.getDynamicPattern("optional");
        require(zero != null && zero.getSize() == 0 && zero.getFacing().equals("UP") && tile.getDynamicPattern("absent") == null, "zero and absent patterns conflated");
        var offsets = tile.getBlockPosInPattern("minecraft:stone");
        require(offsets.length == size && tile.getBlocksInPattern("minecraft:stone") == size, "segment cells omitted or overlap counted twice");
        for (int i = 0; i < offsets.length; i++) {
            int x = i + 1; BlockPos expected = switch (facing) {
                case WEST -> new BlockPos(0, 0, -x); case SOUTH -> new BlockPos(-x, 0, 0);
                case EAST -> new BlockPos(0, 0, x); default -> new BlockPos(x, 0, 0);
            };
            require(offsets[i].equals(expected), "returned coordinates are world positions or unrotated offsets");
        }
        offsets[0] = BlockPos.ZERO;
        require(!tile.getBlockPosInPattern("minecraft:stone")[0].equals(BlockPos.ZERO), "returned coordinate array aliases query state");
    }
    private static void form(ServerLevel level, TileController tile, DynamicMachine machine, MachineLoader loader) {
        var attempt = MachineFormation.tryForm(level, tile.getBlockPos(), tile.getFacing(), machine, loader);
        require(attempt.formed(), "real structure failed: " + attempt.report()); tile.setFormed(true);
    }
    private static BlockPos inverseOffset(Direction facing, BlockPos offset) {
        return switch (facing) {
            case WEST -> new BlockPos(-offset.getZ(), offset.getY(), offset.getX());
            case SOUTH -> new BlockPos(-offset.getX(), offset.getY(), -offset.getZ());
            case EAST -> new BlockPos(offset.getZ(), offset.getY(), -offset.getX()); default -> offset;
        };
    }
    public static String sourceMatchFacing(Direction facing) {
        // Explicit upstream getTrueFacing outputs for a declared NORTH face (not the spatial-offset transform).
        return switch (facing) { case WEST -> "EAST"; case EAST -> "WEST"; default -> facing.name(); };
    }
    private static void cleanupCallbacks() throws Exception {
        var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); field.setAccessible(true);
        ((Map<?, ?>) field.get(null)).remove(TARGET);
    }
    private static void putLocal(ServerLevel level, BlockPos pos, Direction face, int x, int y, int z, BlockState state, Map<BlockPos, BlockState> saved) {
        put(level, StructureMatcher.toWorldOffset(pos, face, x, y, z), state, saved);
    }
    private static void put(ServerLevel level, BlockPos pos, BlockState state, Map<BlockPos, BlockState> saved) {
        saved.putIfAbsent(pos, level.getBlockState(pos)); level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
