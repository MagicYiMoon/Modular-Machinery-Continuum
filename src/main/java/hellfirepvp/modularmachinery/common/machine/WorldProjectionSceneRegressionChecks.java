/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import java.util.*;

/** Exercises the actual world ghost scene and its detached entity lifecycle, without loading client renderers. */
public final class WorldProjectionSceneRegressionChecks {
    private WorldProjectionSceneRegressionChecks() { }

    public static void run(ServerLevel source, BlockPos probe) {
        BlockState before = source.getBlockState(probe);
        BlockEntity beforeEntity = source.getBlockEntity(probe);
        var chestData = StructureNbt.read(JsonParser.parseString("""
                {"nbt":{"Items":[{"Slot":0,"id":"minecraft:dirt","Count":1}]},
                 "preview-nbt":{"x":12345,"y":678,"z":12345,
                   "Items":[{"Slot":0,"id":"minecraft:diamond","Count":7}]}}
                """).getAsJsonObject());
        var bannerData = StructureNbt.read(JsonParser.parseString("""
                {"preview-nbt":{"Patterns":[{"Pattern":"bs","Color":14}]}}
                """).getAsJsonObject());
        var left = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.EAST).setValue(ChestBlock.TYPE, ChestType.LEFT);
        var right = left.setValue(ChestBlock.TYPE, ChestType.RIGHT);
        BlockPos a = new BlockPos(1, -2, 0), b = a.relative(ChestBlock.getConnectedDirection(left)), flag = new BlockPos(1, 1, 2);
        var banner = Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.WEST);
        Map<BlockPos, WorldProjectionDefinition.Cell> cells = new LinkedHashMap<>();
        cells.put(a, cell(a, List.of(left), chestData));
        cells.put(b, cell(b, List.of(right), null));
        cells.put(flag, cell(flag, List.of(banner, Blocks.STONE.defaultBlockState()), bannerData));
        try (var scene = new WorldProjectionScene(source, probe, cells)) {
            int loaded = source.getChunkSource().getLoadedChunksCount();
            cells.clear(); // The scene owns a snapshot of its definition, not the caller's map.
            BlockPos anchor = new BlockPos(-40001, -100, 25001);
            var mutable = anchor.mutable();
            scene.update(mutable, Direction.NORTH, 0, 100);
            mutable.set(1, 1, 1);
            BlockPos first = world(anchor, Direction.NORTH, a), second = world(anchor, Direction.NORTH, b);
            var preview = scene.level();
            var chest = (ChestBlockEntity) preview.getBlockEntity(first);
            require(chest != null && chest.getLevel() == preview && chest.getBlockPos().equals(first)
                            && chest.getType().isValid(chest.getBlockState()) && chest.getBlockState().equals(left),
                    "world ghost entity lost absolute position/facing/type or used preview-NBT coordinates");
            require(chest.getItem(0).is(Items.DIAMOND) && chest.getItem(0).getCount() == 7,
                    "world ghost did not load preview-NBT or loaded matching NBT as display data");
            require(scene.part(first).selectorTag().equals("ghost"), "translated ghost part metadata missing");
            scene.part(first).previewNbt().getList("Items", 10).getCompound(0).putByte("Count", (byte) 1);
            require(chest.getItem(0).getCount() == 7, "preview-NBT view mutated detached inventory");
            boolean combined = ((ChestBlock) left.getBlock()).combine(left, preview, first, true)
                    .apply(new DoubleBlockCombiner.Combiner<ChestBlockEntity, Boolean>() {
                        @Override public Boolean acceptDouble(ChestBlockEntity x, ChestBlockEntity y) {
                            return x != y && x.getLevel() == preview && y.getLevel() == preview;
                        }
                        @Override public Boolean acceptSingle(ChestBlockEntity entity) { return false; }
                        @Override public Boolean acceptNone() { return false; }
                    });
            require(combined && preview.getBlockEntity(second) != null, "world ghost double-chest neighbor context incomplete");
            BlockPos bannerPos = world(anchor, Direction.NORTH, flag);
            var bannerEntity = (BannerBlockEntity) preview.getBlockEntity(bannerPos);
            require(bannerEntity.getPatterns().size() == 2 && bannerEntity.getPatterns().get(1).getSecond() == DyeColor.RED
                            && bannerEntity.getBlockState().getValue(WallBannerBlock.FACING) == Direction.WEST,
                    "world ghost banner pattern/color/facing lost");
            var cachedChunk = preview.getChunkAt(first);
            scene.update(anchor, Direction.NORTH, 0, 200);
            require(preview.getBlockEntity(first) == chest && preview.getBlockEntity(bannerPos) == bannerEntity
                            && preview.getGameTime() == 200, "frozen ghost rebuilt each frame or froze its animation clock");
            scene.update(anchor, Direction.NORTH, 40, 240);
            require(preview.getBlockEntity(first) == chest && preview.getBlockEntity(bannerPos) == null
                            && preview.getBlockState(bannerPos).is(Blocks.STONE), "candidate update retained stale entity or reset unchanged chest");
            scene.update(anchor, Direction.NORTH, 80, 280);
            require(preview.getBlockEntity(bannerPos) instanceof BannerBlockEntity next && next != bannerEntity
                            && next.getPatterns().size() == 2, "restored candidate lost fresh preview data");
            BlockPos moved = anchor.offset(31, -1, -47);
            scene.update(moved, Direction.SOUTH, 80, 300);
            BlockPos movedFirst = world(moved, Direction.SOUTH, a);
            require(cachedChunk.getBlockState(first).isAir() && cachedChunk.getBlockEntity(first) == null
                            && preview.getBlockEntity(movedFirst) instanceof ChestBlockEntity movedChest
                            && movedChest != chest && movedChest.getBlockPos().equals(movedFirst)
                            && movedChest.getBlockState().getValue(ChestBlock.FACING) == Direction.WEST
                            && movedChest.getItem(0).getCount() == 7, "moving/rotating ghost retained old positions/entities or lost NBT");
            require(!preview.setBlock(movedFirst, Blocks.AIR.defaultBlockState(), 3)
                            && preview.getBlockEntity(movedFirst) != null && preview.getServer() == null,
                    "ghost renderer can write gameplay blocks or reach a server");
            require(source.getChunkSource().getLoadedChunksCount() == loaded
                            && source.getBlockState(probe).equals(before) && source.getBlockEntity(probe) == beforeEntity,
                    "world ghost scene generated real chunks or changed live block entities");
            scene.close();
            require(preview.getBlockState(movedFirst).isAir() && preview.getBlockEntity(movedFirst) == null
                            && scene.part(movedFirst) == null && preview.getChunkSource().getLoadedChunksCount() == 0,
                    "cleared world projection retained entity/state/chunk references");
        }
        checkDefinitionPath(source, probe, chestData);
        Modularmachinery.LOG.info("[MMCE][regression] PASS: world ghost scene: detached absolute-position chest/banner preview NBT, "
                + "double-neighbor context, frozen-candidate/live-animation clock, entity reuse/replacement, moving/rotating/reset cleanup, "
                + "definition/controller path and real-world/chunk isolation");
    }

    private static void checkDefinitionPath(ServerLevel source, BlockPos anchor, StructureNbt data) {
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:chest@0", null, data));
        var machine = new DynamicMachine("world_ghost_definition", "World ghost", pattern);
        var definition = new WorldProjectionDefinition(machine, pattern, new MachineVariables(), new BlockStateResolver());
        try (var scene = new WorldProjectionScene(source, anchor, definition.cells())) {
            scene.update(anchor, Direction.EAST, 0, 1);
            var pos = world(anchor, Direction.EAST, new BlockPos(1, 0, 0));
            require(scene.level().getBlockEntity(pos) instanceof ChestBlockEntity chest
                            && chest.getItem(0).is(Items.DIAMOND)
                            && chest.getBlockState().getValue(ChestBlock.FACING) == Direction.EAST
                            && scene.level().getBlockEntity(anchor) instanceof hellfirepvp.modularmachinery.common.tiles.TileController
                            && scene.level().getBlockState(anchor).getValue(BlockController.FACING) == Direction.EAST,
                    "real projection definition did not produce detached preview-NBT/controller entities");
        }
    }
    private static WorldProjectionDefinition.Cell cell(BlockPos pos, List<BlockState> states, StructureNbt data) {
        return new WorldProjectionDefinition.Cell(new MachinePattern.Part(pos.getX(), pos.getY(), pos.getZ(),
                "minecraft:chest", "ghost", data), states, false);
    }
    private static BlockPos world(BlockPos anchor, Direction facing, BlockPos pos) {
        return StructureMatcher.toWorldOffset(anchor, facing, pos.getX(), pos.getY(), pos.getZ());
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
