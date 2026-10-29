/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.event.machine.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.*;

/** Real block tickers and paid factory jobs; isolated fixtures only, never a player world. */
public final class ControllerRuntimeLimitsRegressionChecks {
    private ControllerRuntimeLimitsRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var machines = MachineReloadListener.getLoader().getMachines(); var savedMachines = new LinkedHashMap<>(machines);
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine(); var savedRecipes = new LinkedHashMap<>(recipes);
        var savedBlocks = new LinkedHashMap<BlockPos, BlockState>();
        var handlersField = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); handlersField.setAccessible(true);
        @SuppressWarnings("unchecked") var handlers = (Map<String, Object>) handlersField.get(null);
        var savedHandlers = new LinkedHashMap<>(handlers);
        var cleanField = Config.class.getDeclaredField("CLEAN_CUSTOM_DATA_ON_STRUCTURE_FAILURE"); cleanField.setAccessible(true);
        var clean = (ForgeConfigSpec.BooleanValue) cleanField.get(null); boolean oldClean = clean.get();
        var blocks = new ArrayList<Block>(List.of(RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get()));
        for (boolean factory : List.of(false, true)) blocks.add(DedicatedControllers.entries().stream()
                .filter(e -> e.factory() == factory).findFirst().orElseThrow().block().get());
        try {
            for (int i = 0; i < blocks.size(); i++) {
                BlockPos pos = origin.offset(i * 8, 0, 0); Block block = blocks.get(i);
                put(level, pos, block.defaultBlockState().setValue(BlockController.FACING, Direction.NORTH), savedBlocks);
                put(level, pos.offset(1, 0, 0), RegistryBlocks.itemInputBus.get().defaultBlockState(), savedBlocks);
                put(level, pos.offset(2, 0, 0), RegistryBlocks.itemOutputBus.get().defaultBlockState(), savedBlocks);
                put(level, pos.offset(3, 0, 0), Blocks.AIR.defaultBlockState(), savedBlocks);
                put(level, pos.above(), Blocks.AIR.defaultBlockState(), savedBlocks);
                var tile = (TileController) level.getBlockEntity(pos);
                String target = Optional.ofNullable(DedicatedControllers.binding(block)).orElse("mmc_runtime_limits_fixture");
                var pattern = new MachinePattern();
                pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:blockinputbus"));
                pattern.addPart(new MachinePattern.Part(2, 0, 0, "modularmachinery:blockoutputbus"));
                pattern.addPart(new MachinePattern.Part(3, 0, 0, "minecraft:stone"));
                var machine = new DynamicMachine(target, target, pattern); machine.setMaxThreads(1);
                machine.setParallelizable(true); machine.setInternalParallelism(2); machine.setMaxParallelism(8);
                machine.addCoreThreadPreset("core", List.of("limits_core"));
                var candidates = List.of(recipe(target, "limits_a", ""), recipe(target, "limits_b", ""),
                        recipe(target, "limits_c", ""), recipe(target, "limits_core", "core"));
                machines.clear(); machines.put(new ResourceLocation("modularmachinery", target), machine);
                recipes.clear(); recipes.put(target, candidates); handlers.clear();
                tile.setMachineName(target);
                require(tile.getTicksExisted() == 0, "new controller age is not zero");
                tick(tile);
                require(!tile.isFormed() && tile.getTicksExisted() == 1, "unformed tick did not count");
                put(level, pos.offset(3, 0, 0), Blocks.STONE.defaultBlockState(), savedBlocks);
                require(MachineFormation.tryForm(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader()).formed(), "fixture did not form");
                tile.setFormed(true);
                put(level, pos.above(), Blocks.REDSTONE_BLOCK.defaultBlockState(), savedBlocks);
                require(tile.isRedstoneStopped(), "pause fixture has no signal"); tick(tile);
                require(tile.getTicksExisted() == 2 && !tile.hasActiveRecipe(), "redstone pause stopped age or started a job");
                put(level, pos.above(), Blocks.AIR.defaultBlockState(), savedBlocks);
                var observed = new ArrayList<Integer>();
                ScriptMachineEventRegistry.register(target, MachineTickEvent.class, event -> observed.add(event.getController().getTicksExisted()));
                tick(tile);
                require(tile.getTicksExisted() == 3 && observed.equals(List.of(2, 2)), "START/END did not observe pre-increment age");
                if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")) {
                    // CT's execute completion refreshes all JSON/script recipes. Keep their machine definitions valid.
                    machines.putAll(savedMachines); machines.put(new ResourceLocation("modularmachinery", target), machine);
                    hellfirepvp.modularmachinery.integration.crafttweaker.ControllerRuntimeLimitsScriptRegressionChecks.run(tile, target);
                }
                checkClientTicker(level, tile);
                int age = tile.getTicksExisted(); var disk = tile.getUpdateTag();
                var copy = copy(tile); copy.load(disk);
                require(copy.getTicksExisted() == 0, "saved/update tag persisted controller lifetime");
                tile.load(disk);
                require(tile.getTicksExisted() == age, "same instance NBT application reset local lifetime");
                if (tile instanceof TileFactoryController factory) checkFactory(level, factory, machine, clean);
                else {
                    tile.setExtraThreadCount(70000); require(tile.getExtraThreadCount() == 0, "ordinary controller accepted extra threads");
                }
                // A successful recheck and an unforming transition do not reset the lifetime.
                age = tile.getTicksExisted(); tile.setFormed(false);
                require(tile.getTicksExisted() == age && tile.getExtraThreadCount() == 0, "unforming changed age or retained extra threads");
            }
            Modularmachinery.LOG.info("[MMC][controller-runtime-limits] PASS: generic/dedicated ordinary/factory lifetime, unformed/paused/event order, native client-only ticker, NBT, extra capacity, core independence, distinct paid jobs/batches, signed limits, reset and stale/async/unloaded guards");
        } finally {
            clean.set(oldClean); handlers.clear(); handlers.putAll(savedHandlers);
            savedBlocks.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_CLIENTS));
            machines.clear(); machines.putAll(savedMachines); recipes.clear(); recipes.putAll(savedRecipes);
        }
    }

    private static void checkFactory(ServerLevel level, TileFactoryController tile, DynamicMachine machine,
                                     ForgeConfigSpec.BooleanValue clean) throws Exception {
        tile.setExtraThreadCount(1);
        var input = ((TileItemBus) level.getBlockEntity(tile.getBlockPos().offset(1, 0, 0))).getInventory();
        input.insertItem(0, new ItemStack(Items.COAL, 64), false);
        for (int i = 0; i < 3; i++) { tile.requestImmediateRecipeSearch(); tick(tile); }
        require(tile.getMaxThreads() == 2 && tile.getActiveThreadCount() == 3 && tile.getDynamicThreadCount() == 2,
                "extra capacity did not admit two dynamic jobs plus the independent core");
        require(input.getStackInSlot(0).getCount() == 60, "extra threads changed batch accounting or did not pay independent inputs");
        var names = new HashSet<String>();
        for (var thread : tile.getFactoryRecipeThreadList()) {
            require(names.add(thread.getRecipeName()), "one recipe occupied multiple threads");
            require(thread.getParallelism() == (thread.getRecipeName().equals("limits_a") ? 2 : 1), "thread delta changed batch multiplier");
        }
        var progress = tile.getFactoryRecipeThreadList().stream().map(t -> t.getRecipeTicks()).toList();
        tile.setExtraThreadCount(-1); tick(tile);
        require(tile.getMaxThreads() == 0 && tile.getActiveThreadCount() == 3 && input.getStackInSlot(0).getCount() == 60,
                "capacity reduction discarded paid runs or admitted another job");
        for (int i = 0; i < progress.size(); i++) require(tile.getFactoryRecipeThreadList().get(i).getRecipeTicks() == progress.get(i) + 1,
                "capacity reduction stopped an existing dynamic/core job");
        tile.setExtraThreadCount(2); tick(tile);
        require(tile.getMaxThreads() == 3 && tile.getActiveThreadCount() == 4 && input.getStackInSlot(0).getCount() == 59,
                "capacity increase did not immediately wake scheduling");
        require(tile.getFactoryRecipeThreadList().stream().map(t -> t.getRecipeName()).distinct().count() == 4, "increase duplicated a running recipe");
        tile.setExtraThreadCount(Integer.MIN_VALUE); require(tile.getMaxThreads() == 0, "negative total overflowed");
        tile.setExtraThreadCount(Integer.MAX_VALUE); require(tile.getMaxThreads() == Integer.MAX_VALUE, "positive total overflowed");
        BlockPos peerPos = tile.getBlockPos().offset(6, 0, 0); var peerBefore = level.getBlockState(peerPos);
        try {
            level.setBlock(peerPos, RegistryBlocks.blockFactoryController.get().defaultBlockState(), Block.UPDATE_CLIENTS);
            var peer = (TileFactoryController) level.getBlockEntity(peerPos); peer.setMachineName(machine.getRegistryName());
            peer.setFactoryCeiling(2, 1); peer.setFormed(true);
            require(hellfirepvp.modularmachinery.common.machine.factory.FactoryControllerFamily.threads(machine.getRegistryName())[1]
                    == Integer.MAX_VALUE, "family aggregate wrapped a large runtime delta");
            peer.setFormed(false);
        } finally { level.setBlock(peerPos, peerBefore, Block.UPDATE_CLIENTS); }
        tile.setExtraThreadCount(70000); var disk = tile.getUpdateTag();
        var copy = (TileFactoryController) copy(tile); copy.load(disk);
        require(copy.getExtraThreadCount() == 70000 && copy.getMaxThreads() == 70001, "NBT truncated or double-added the extra count");
        copy.setExtraThreadCount(1); require(copy.getExtraThreadCount() == 70000, "detached snapshot allowed a runtime write");
        var legacy = disk.copy(); legacy.putShort("extraThreadCount", (short) 300); copy.load(legacy);
        require(copy.getExtraThreadCount() == 300, "numeric legacy tag was not read");
        legacy.remove("extraThreadCount"); copy.load(legacy); require(copy.getExtraThreadCount() == 0, "old port tag did not default to zero");
        Thread writer = new Thread(() -> tile.setExtraThreadCount(5)); writer.start(); writer.join();
        require(tile.getExtraThreadCount() == 70000, "async setter changed capacity");
        tile.load(disk); require(tile.getExtraThreadCount() == 70000 && tile.getMaxThreads() == 70001, "live reload lost the delta");
        require(MachineFormation.tryForm(level, tile.getBlockPos(), Direction.NORTH, machine, MachineReloadListener.getLoader()).formed()
                && tile.getExtraThreadCount() == 70000, "successful recheck cleared runtime capacity");
        var far = new TileFactoryController(new BlockPos(1_700_000, tile.getBlockPos().getY(), 0), tile.getBlockState());
        far.setLevel(level); far.setExtraThreadCount(1);
        require(far.getExtraThreadCount() == 0 && !level.hasChunkAt(far.getBlockPos()), "setter loaded an absent controller chunk");
        for (boolean clearData : List.of(false, true)) {
            clean.set(clearData); tile.setExtraThreadCount(3);
            level.setBlock(tile.getBlockPos().offset(3, 0, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            require(!MachineTicker.structureStillHolds(level, tile.getBlockPos(), Direction.NORTH, machine,
                    MachineReloadListener.getLoader(), tile) && tile.getExtraThreadCount() == 0, "confirmed structure failure retained extra capacity");
            level.setBlock(tile.getBlockPos().offset(3, 0, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            require(MachineFormation.tryForm(level, tile.getBlockPos(), Direction.NORTH, machine, MachineReloadListener.getLoader()).formed(), "fixture failed to reform");
            tile.setFormed(true);
        }
        tile.setExtraThreadCount(4); tile.setRemoved(); tile.setExtraThreadCount(9);
        require(tile.getExtraThreadCount() == 4, "removed instance accepted a write"); tile.clearRemoved();
    }

    /** Executes the same server/client block tickers without requiring any Minecraft client classes. */
    @SuppressWarnings({"rawtypes", "unchecked"}) public static void tick(TileController tile) {
        BlockController block = (BlockController) tile.getBlockState().getBlock();
        BlockEntityTicker ticker = block.getTicker(tile.getLevel(), tile.getBlockState(), tile.getType());
        require(ticker != null, "controller block supplied no ticker");
        ticker.tick(tile.getLevel(), tile.getBlockPos(), tile.getBlockState(), tile);
    }
    private static void checkClientTicker(ServerLevel source, TileController tile) {
        var states = new LinkedHashMap<BlockPos, BlockState>(); var entities = new HashMap<BlockPos, BlockEntity>();
        try (var preview = new BlueprintPreviewLevel(source, tile.getBlockPos(), states, entities)) {
            var client = copy(tile); states.put(client.getBlockPos(), client.getBlockState()); entities.put(client.getBlockPos(), client);
            client.setLevel(preview); var before = client.getUpdateTag();
            tick(client); tick(client);
            require(client.getTicksExisted() == 2 && before.equals(client.getUpdateTag()), "client ticker changed gameplay NBT or did not count local lifetime");
            MachineTicker.tick(preview, client.getBlockPos(), client);
            if (client instanceof TileFactoryController factory) FactoryTicker.tick(preview, client.getBlockPos(), factory);
            require(client.getTicksExisted() == 2, "client static server engine also counted");
            if (client instanceof TileFactoryController) {
                int beforeAge = tile.getTicksExisted(); MachineTicker.tick(source, tile.getBlockPos(), tile);
                require(tile.getTicksExisted() == beforeAge, "wrong ordinary engine counted factory lifetime");
            }
        }
    }
    private static TileController copy(TileController tile) {
        return tile instanceof TileFactoryController ? new TileFactoryController(tile.getBlockPos(), tile.getBlockState())
                : new TileController(tile.getBlockPos(), tile.getBlockState());
    }
    private static MachineRecipe recipe(String target, String name, String thread) {
        return new MachineRecipe(target, name, 200, List.of(new RequirementItem(IOType.INPUT, "minecraft:coal", 1),
                new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1)), null, false, -1, thread, 0,
                RecipeCommandContainer.empty(), List.of(), true, true);
    }
    private static void put(ServerLevel level, BlockPos pos, BlockState state, Map<BlockPos, BlockState> saved) {
        saved.putIfAbsent(pos, level.getBlockState(pos)); level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
