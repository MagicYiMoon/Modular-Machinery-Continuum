/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import java.util.*;

/** Persistence/lifecycle checks run with and without CT. Optional script tests use these actual world tiles. */
public final class ControllerCustomDataRegressionChecks {
    public static final String TARGET = "mmc_controller_data_regression";
    private ControllerCustomDataRegressionChecks() { }
    public interface Recording { int syncs(); int updates(); void resetCounts(); }
    private static class Ordinary extends TileController implements Recording {
        private int syncs, updates;
        Ordinary(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void markNoUpdateSync() { syncs++; super.markNoUpdateSync(); }
        @Override public void markForUpdate() { updates++; super.markForUpdate(); }
        public int syncs() { return syncs; } public int updates() { return updates; }
        public void resetCounts() { syncs = updates = 0; }
    }
    private static class Factory extends TileFactoryController implements Recording {
        private int syncs, updates;
        Factory(BlockPos pos, BlockState state) { super(pos, state); }
        @Override public void markNoUpdateSync() { syncs++; super.markNoUpdateSync(); }
        @Override public void markForUpdate() { updates++; super.markForUpdate(); }
        public int syncs() { return syncs; } public int updates() { return updates; }
        public void resetCounts() { syncs = updates = 0; }
    }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var before = new LinkedHashMap<BlockPos, BlockState>();
        var machines = MachineReloadListener.getLoader().getMachines(); var saved = new LinkedHashMap<>(machines);
        var configField = ForgeConfigSpec.class.getDeclaredField("childConfig"); configField.setAccessible(true);
        var originalConfig = (com.electronwill.nightconfig.core.CommentedConfig) configField.get(Config.SPEC);
        var testConfig = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        Config.SPEC.correct(testConfig); Config.SPEC.setConfig(testConfig);
        try {
            machines.put(new ResourceLocation("modularmachinery", TARGET), new DynamicMachine(TARGET, TARGET, new MachinePattern()));
            for (boolean factory : List.of(false, true)) {
                BlockPos pos = origin.offset(factory ? 8 : 0, 0, 0);
                var tile = place(level, pos, factory, before);
                var recording = (Recording) tile;
                var input = TagParser.parseTag("{counter:3,nested:{value:7},list:[{value:9}],ints:[I;1,2],bytes:[B;3b,4b],longs:[L;5L,6L]}");
                tile.setCustomDataTag(input); input.getCompound("nested").putInt("value", 99);
                require(tile.getCustomDataTag().getCompound("nested").getInt("value") == 7, "native setter retained caller NBT alias");
                recording.resetCounts(); tile.setCustomDataTag(tile.getCustomDataTag());
                require(recording.syncs() == 0 && recording.updates() == 0, "equal native write synchronized or updated block");
                var other = place(level, pos.offset(3, 0, 0), factory, before);
                other.setCustomDataTag(tile.getCustomDataTag()); other.getCustomDataTag().getCompound("nested").putInt("value", 88);
                require(tile.getCustomDataTag().getCompound("nested").getInt("value") == 7, "different controller data aliased");
                var disk = tile.saveWithoutMetadata(); var expected = tile.getCustomDataTag().copy();
                var restored = factory ? new TileFactoryController(pos, tile.getBlockState()) : new TileController(pos, tile.getBlockState());
                restored.load(disk); disk.getCompound("customData").getCompound("nested").putInt("value", 44);
                require(restored.getCustomDataTag().equals(expected), "disk load omitted data or retained incoming NBT alias");
                var update = tile.getUpdateTag(); restored.load(update); update.getCompound("customData").putInt("counter", 999);
                require(restored.getCustomDataTag().equals(expected), "client/update load retained packet NBT alias");
                Thread writer = new Thread(() -> tile.setCustomDataTag(new CompoundTag())); writer.start(); writer.join();
                require(tile.getCustomDataTag().equals(expected), "off-thread native setter changed world data");
                MachineReloadListener.refreshRecipes();
                require(tile.getCustomDataTag().equals(expected), "definition refresh erased live controller data");
                if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker"))
                    hellfirepvp.modularmachinery.integration.crafttweaker.ControllerCustomDataScriptRegressionChecks.run(level, tile, other);
                checkStructurePolicy(level, pos, tile, before);
                restored.load(new CompoundTag());
                require(restored.getCustomDataTag().isEmpty(), "legacy/empty load left old custom data");
            }
            Modularmachinery.LOG.info("[MMC][controller-data] PASS: ordinary/factory NBT save+sync copies, instance isolation, definition refresh, invalid writes and structure cleanup policy");
        } finally {
            Config.SPEC.setConfig(originalConfig);
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            machines.clear(); machines.putAll(saved); MachineReloadListener.refreshRecipes();
        }
    }
    private static void checkStructurePolicy(ServerLevel level, BlockPos pos, TileController tile, Map<BlockPos, BlockState> before) throws Exception {
        var loader = MachineReloadListener.getLoader(); var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:stone"));
        var machine = new DynamicMachine(TARGET, TARGET, pattern);
        loader.getMachines().put(new ResourceLocation("modularmachinery", TARGET), machine);
        var field = Config.class.getDeclaredField("CLEAN_CUSTOM_DATA_ON_STRUCTURE_FAILURE"); field.setAccessible(true);
        var option = (ForgeConfigSpec.BooleanValue) field.get(null);
        var at = pos.offset(1, 0, 0); before.putIfAbsent(at, level.getBlockState(at));
        for (boolean clear : List.of(false, true)) {
            option.set(clear); level.setBlock(at, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
            tile.setMachineName(TARGET);
            require(MachineFormation.tryForm(level, pos, Direction.NORTH, machine, loader).formed(), "data fixture failed to form");
            tile.setFormed(true); var data = new CompoundTag(); data.putInt("kept", 12); tile.setCustomDataTag(data);
            level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            require(!MachineTicker.structureStillHolds(level, pos, Direction.NORTH, machine, loader, tile)
                    && tile.getCustomDataTag().isEmpty() == clear, "structure cleanup option ignored");
        }
    }
    private static TileController place(ServerLevel level, BlockPos pos, boolean factory, Map<BlockPos, BlockState> before) {
        before.putIfAbsent(pos, level.getBlockState(pos));
        var state = (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get()).defaultBlockState();
        level.setBlock(pos, state, Block.UPDATE_ALL); level.removeBlockEntity(pos);
        TileController tile = factory ? new Factory(pos, state) : new Ordinary(pos, state);
        level.setBlockEntity(tile); tile.setMachineName(TARGET); return tile;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
