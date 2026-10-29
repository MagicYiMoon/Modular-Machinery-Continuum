/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Real world writes, reloads and tickers; runs only in the isolated opt-in server suite. */
public final class StructureLifecycleRegressionChecks {
    private StructureLifecycleRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) {
        var loader = MachineReloadListener.getLoader();
        var definitions = new LinkedHashMap<>(loader.getMachines());
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        long time = level.getGameTime();
        try {
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(1);
            checkBindings(level, origin, loader, before);
            checkChanges(level, origin.offset(0, 0, 8), false, loader, before);
            checkChanges(level, origin.offset(0, 0, 16), true, loader, before);
            checkFactorySummary(level, origin.offset(0, 0, 24), before);
            checkDynamicChanges(level, origin.offset(0, 0, 32), loader, before);
            Modularmachinery.LOG.info("[MMC][structure-lifecycle] PASS: sparse bindings, shrink/reload/legacy cleanup, unloaded pending cleanup, foreign owners, block-write invalidation, ordinary/factory tickers, watcher lifecycle, factory totals/NBT");
        } finally {
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            loader.getMachines().clear(); loader.getMachines().putAll(definitions);
            ((net.minecraft.world.level.storage.ServerLevelData) level.getLevelData()).setGameTime(time);
        }
    }

    private static void checkBindings(ServerLevel level, BlockPos origin, MachineLoader loader,
                                      Map<BlockPos, BlockState> before) {
        place(level, before, origin, RegistryBlocks.blockController.get().defaultBlockState());
        for (int x : new int[]{1, 2, 128, 130})
            place(level, before, origin.offset(x, 0, 0), RegistryBlocks.itemInputBus.get().defaultBlockState());
        var controller = (TileController) level.getBlockEntity(origin);
        var near = (MachineComponentTile) level.getBlockEntity(origin.offset(1, 0, 0));
        var far = (MachineComponentTile) level.getBlockEntity(origin.offset(128, 0, 0));
        var foreign = (MachineComponentTile) level.getBlockEntity(origin.offset(130, 0, 0));
        var untracked = (MachineComponentTile) level.getBlockEntity(origin.offset(2, 0, 0));
        var large = pattern(1, 128);
        var small = pattern(1);
        var machine = register(loader, "lifecycle_bindings", large);
        controller.setMachineName(machine.getRegistryName());
        require(MachineFormation.tryForm(level, origin, Direction.NORTH, machine, loader).formed(), "sparse machine failed to form");
        controller.setFormed(true);
        require(controller.getBoundPositions().size() == 2 && origin.equals(far.getControllerPos()), "distant sparse component not bound");
        CompoundTag saved = controller.getUpdateTag();

        // Shrink after a reload: the old address must survive load long enough to be detached.
        machine = register(loader, "lifecycle_bindings", small);
        controller.load(saved);
        require(controller.getBoundPositions().contains(origin.offset(128, 0, 0)), "reload lost old cleanup candidates");
        MachineTicker.tick(level, origin, controller);
        require(controller.isFormed() && far.getControllerPos() == null && origin.equals(near.getControllerPos())
                && controller.getBoundPositions().equals(List.of(origin.offset(1, 0, 0))), "reloaded shrink kept stale binding or lost current input");

        far.setControllerPos(origin);
        CompoundTag legacy = saved.copy(); legacy.remove("boundPositions");
        controller.load(legacy);
        MachineTicker.tick(level, origin, controller);
        require(far.getControllerPos() == null, "legacy hidden-cell fallback did not detach stale component");

        BlockPos otherOwner = origin.offset(0, 0, 3);
        foreign.setControllerPos(otherOwner);
        untracked.setControllerPos(origin);
        controller.setBoundPositions(List.of(origin.offset(1, 0, 0), origin.offset(130, 0, 0)));
        controller.reconcileComponentBindings(List.of(origin.offset(1, 0, 0)));
        require(otherOwner.equals(foreign.getControllerPos()) && origin.equals(untracked.getControllerPos()),
                "cleanup erased another owner's binding or searched unrecorded surrounding cells");
        var chunk = level.getChunkAt(origin);
        chunk.setUnsaved(false);
        controller.reconcileComponentBindings(controller.getBoundPositions());
        require(!chunk.isUnsaved(), "unchanged component reconciliation dirtied an idle chunk");

        // The very distant chunk is deliberately absent until the test explicitly places its hatch.
        BlockPos unloaded = new BlockPos(1_500_000, origin.getY(), origin.getZ());
        require(!level.hasChunkAt(unloaded), "unloaded cleanup fixture was already loaded");
        controller.setBoundPositions(List.of(origin.offset(1, 0, 0), unloaded));
        controller.reconcileComponentBindings(List.of(origin.offset(1, 0, 0)));
        CompoundTag pending = controller.getUpdateTag();
        require(!level.hasChunkAt(unloaded) && Arrays.stream(pending.getLongArray("pendingUnbindPositions")).anyMatch(p -> p == unloaded.asLong()),
                "cleanup loaded a chunk or discarded its deferred position");
        var restored = new TileController(origin, controller.getBlockState());
        restored.setLevel(level); restored.load(pending);
        restored.flushPendingComponentUnbinds();
        require(!level.hasChunkAt(unloaded) && restored.getUpdateTag().getLongArray("pendingUnbindPositions").length == 1,
                "reload lost unloaded pending cleanup");
        place(level, before, unloaded, RegistryBlocks.itemInputBus.get().defaultBlockState());
        var late = (MachineComponentTile) level.getBlockEntity(unloaded);
        late.setControllerPos(origin);
        restored.flushPendingComponentUnbinds();
        require(late.getControllerPos() == null && restored.getUpdateTag().getLongArray("pendingUnbindPositions").length == 0,
                "loaded deferred component did not detach");
        restored.setRemoved();

        // A temporarily unavailable structure must keep its rebind/check requests and formed state.
        CompoundTag waiting = controller.getUpdateTag();
        waiting.putLongArray("hiddenPositions", new long[]{new BlockPos(1_600_000, origin.getY(), origin.getZ()).asLong()});
        controller.load(waiting);
        require(!controller.consumeRebindRequest(), "unloaded structure consumed its one-shot rebind request");
        MachineTicker.tick(level, origin, controller);
        require(controller.isFormed() && controller.isStructureCheckRequested(), "unloaded structure was unformed or lost pending check");
        waiting.putLongArray("hiddenPositions", new long[]{origin.offset(1, 0, 0).asLong()});
        controller.load(waiting);
        MachineTicker.tick(level, origin, controller);
        require(controller.isFormed() && !controller.isStructureCheckRequested(), "loaded structure failed to resume validation");
    }

    private static void checkChanges(ServerLevel level, BlockPos origin, boolean factory, MachineLoader loader,
                                     Map<BlockPos, BlockState> before) {
        place(level, before, origin, (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get()).defaultBlockState());
        place(level, before, origin.offset(1, 0, 0), Blocks.STONE.defaultBlockState());
        place(level, before, origin.offset(3, 0, 0), Blocks.STONE.defaultBlockState());
        var controller = (TileController) level.getBlockEntity(origin);
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:stone"));
        pattern.addPart(new MachinePattern.Part(3, 0, 0, "minecraft:stone"));
        var machine = register(loader, factory ? "lifecycle_factory" : "lifecycle_normal", pattern);
        controller.setMachineName(machine.getRegistryName());
        require(MachineFormation.tryForm(level, origin, Direction.NORTH, machine, loader).formed(), "change fixture did not form");
        controller.setFormed(true);
        controller.acknowledgeStructureCheck();
        level.setBlock(origin.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), 0);
        require(!controller.isStructureCheckRequested(), "identical block write invalidated structure");
        place(level, before, origin.offset(2, 0, 0), Blocks.DIRT.defaultBlockState());
        require(!controller.isStructureCheckRequested(), "undeclared cell invalidated structure");
        level.setBlock(origin.offset(1, 0, 0), Blocks.DIRT.defaultBlockState(), 0);
        require(controller.isStructureCheckRequested() && controller.isFormed(), "actual flag-zero block write did not defer validation to ticker");
        level.setBlock(origin.offset(3, 0, 0), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        if (factory) FactoryTicker.tick(level, origin, (TileFactoryController) controller);
        else MachineTicker.tick(level, origin, controller);
        require(!controller.isFormed() && !controller.isStructureCheckRequested(), "broken structure survived next ticker between periodic checks");
        level.setBlock(origin.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), 0);
        level.setBlock(origin.offset(3, 0, 0), Blocks.STONE.defaultBlockState(), 0);
        require(MachineFormation.tryForm(level, origin, Direction.NORTH, machine, loader).formed(), "repaired structure rejected");
        controller.setFormed(true);
        controller.acknowledgeStructureCheck();
        controller.onChunkUnloaded();
        level.setBlock(origin.offset(1, 0, 0), Blocks.DIRT.defaultBlockState(), 0);
        require(!controller.isStructureCheckRequested(), "unloaded controller retained watcher");
        controller.onLoad(); controller.acknowledgeStructureCheck();
        level.setBlock(origin.offset(1, 0, 0), Blocks.STONE.defaultBlockState(), 0);
        require(controller.isStructureCheckRequested(), "reload did not restore watcher");
        controller.acknowledgeStructureCheck(); controller.setRemoved();
        level.setBlock(origin.offset(1, 0, 0), Blocks.DIRT.defaultBlockState(), 0);
        require(!controller.isStructureCheckRequested(), "removed controller retained watcher");
        controller.clearRemoved(); controller.onLoad();
        controller.setFormed(false);
    }

    private static void checkFactorySummary(ServerLevel level, BlockPos origin, Map<BlockPos, BlockState> before) {
        place(level, before, origin, RegistryBlocks.blockFactoryController.get().defaultBlockState());
        var factory = (TileFactoryController) level.getBlockEntity(origin);
        factory.setFactoryCeiling(12, 16);
        factory.syncCoreThreadPresets(Map.of("named_core", List.of()));
        var core = factory.getFactoryRecipeThreadList().get(0);
        core.beginRecipe("lifecycle_core", 100, 1);
        var batch = factory.claimThread(); batch.beginRecipe("lifecycle_batch", 100, 7);
        batch.setBlockedReason("craftcheck.failure.output_space");
        require(factory.getParallelism() == 1 && factory.getFactoryParallelism() == 7
                && factory.getDynamicThreadCount() == 1 && factory.getThreadCount() == 2,
                "factory summary used first thread, counted cores against ceiling, or omitted blocked batch");
        require(factory.factoryParallelismReadoutLines().size() == 2, "later batch thread was hidden by first thread multiplier");
        var copy = new TileFactoryController(origin, factory.getBlockState());
        copy.load(factory.getUpdateTag());
        require(copy.getMaxThreads() == 12 && copy.getFactoryMaxParallelism() == 16
                && copy.getFactoryParallelism() == 7 && copy.getDynamicThreadCount() == 1,
                "client/NBT copy lost factory ceiling or summary");
        core.finishRecipe(); batch.finishRecipe();
        core.beginRecipe("lifecycle_core", 100, Integer.MAX_VALUE);
        batch.beginRecipe("lifecycle_batch", 100, Integer.MAX_VALUE);
        require(factory.getFactoryParallelism() == Integer.MAX_VALUE, "factory total overflowed");
        factory.abandonAllRecipes();
        require(factory.factoryParallelismReadoutLines().isEmpty(), "idle factory showed active batch lines");
    }

    private static MachinePattern pattern(int... offsets) {
        var pattern = new MachinePattern();
        for (int x : offsets) pattern.addPart(new MachinePattern.Part(x, 0, 0, "modularmachinery:blockinputbus"));
        return pattern;
    }

    private static void checkDynamicChanges(ServerLevel level, BlockPos origin, MachineLoader loader,
                                            Map<BlockPos, BlockState> before) {
        place(level, before, origin, RegistryBlocks.blockController.get().defaultBlockState());
        var controller = (TileController) level.getBlockEntity(origin);
        var section = pattern(0);
        section.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:stone"));
        var dynamic = new DynamicPattern("tail");
        dynamic.setMinSize(1); dynamic.setMaxSize(3);
        dynamic.setStructureSizeOffsetStart(new BlockPos(1, 0, 0));
        dynamic.setStructureSizeOffset(new BlockPos(3, 0, 0));
        dynamic.setPattern(section); dynamic.addFace(Direction.EAST);
        for (int x : new int[]{1, 4, 7}) {
            place(level, before, origin.offset(x, 0, 0), RegistryBlocks.itemInputBus.get().defaultBlockState());
            place(level, before, origin.offset(x + 1, 0, 0), Blocks.STONE.defaultBlockState());
        }
        place(level, before, origin.offset(10, 0, 0), Blocks.AIR.defaultBlockState());
        var machine = register(loader, "lifecycle_dynamic", pattern(1));
        machine.addDynamicPattern(dynamic); controller.setMachineName(machine.getRegistryName());
        require(MachineFormation.tryForm(level, origin, Direction.NORTH, machine, loader).formed(), "dynamic fixture did not form");
        controller.setFormed(true); controller.acknowledgeStructureCheck();
        var tail = (MachineComponentTile) level.getBlockEntity(origin.offset(7, 0, 0));
        level.setBlock(origin.offset(8, 0, 0), Blocks.AIR.defaultBlockState(), 0);
        require(controller.isStructureCheckRequested(), "dynamic tail change did not invalidate effective pattern");
        MachineTicker.tick(level, origin, controller);
        require(controller.isFormed() && controller.getBoundPositionCount() == 2 && tail.getControllerPos() == null
                && !controller.getHiddenPositions().contains(origin.offset(8, 0, 0)),
                "dynamic shrink did not detach old tail/update actual formed cells");
        controller.acknowledgeStructureCheck();
        level.setBlock(origin.offset(7, 0, 0), Blocks.AIR.defaultBlockState(), 0);
        require(!controller.isStructureCheckRequested(), "shrunk-away cell retained stale watcher");
        level.setBlock(origin.offset(7, 0, 0), RegistryBlocks.itemInputBus.get().defaultBlockState(), 0);
        level.setBlock(origin.offset(8, 0, 0), Blocks.STONE.defaultBlockState(), 0);
        require(MachineFormation.tryForm(level, origin, Direction.NORTH, machine, loader).formed()
                && controller.getHiddenPositions().contains(origin.offset(8, 0, 0)), "dynamic growth did not refresh formed positions");
        var saved = controller.getUpdateTag();
        controller.onChunkUnloaded(); controller.setFormedPattern(null); controller.load(saved);
        MachineTicker.tick(level, origin, controller);
        controller.acknowledgeStructureCheck();
        level.setBlock(origin.offset(8, 0, 0), Blocks.AIR.defaultBlockState(), 0);
        require(controller.isStructureCheckRequested() && controller.getFormedPattern(machine).size() == 6,
                "reload failed to restore dynamic effective-pattern watcher");
    }
    private static DynamicMachine register(MachineLoader loader, String name, MachinePattern pattern) {
        var machine = new DynamicMachine(name, "Structure lifecycle test", pattern);
        loader.getMachines().put(new ResourceLocation("modularmachinery", name), machine);
        return machine;
    }
    private static void place(ServerLevel level, Map<BlockPos, BlockState> before, BlockPos pos, BlockState state) {
        before.computeIfAbsent(pos, level::getBlockState);
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
