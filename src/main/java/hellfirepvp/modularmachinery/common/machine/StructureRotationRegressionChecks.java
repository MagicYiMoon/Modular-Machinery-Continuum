/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Source BlockArray / IBlockStateDescriptor rotation semantics.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.modifier.ModifierReplacement;
import hellfirepvp.modularmachinery.common.modifier.MultiBlockModifierReplacement;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import java.util.*;

/** Independent direction/coordinate oracles, then the production matching/build/projection paths. */
public final class StructureRotationRegressionChecks {
    private StructureRotationRegressionChecks() { }
    private static final Direction[] FACINGS = {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST};
    private static final Direction[] EAST_ROTATED = {Direction.EAST, Direction.NORTH, Direction.WEST, Direction.SOUTH};
    private static final Direction[] NORTH_ROTATED = {Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST};
    private static final Direction[] WEST_ROTATED = {Direction.WEST, Direction.SOUTH, Direction.EAST, Direction.NORTH};

    public static void run(ServerLevel level, BlockPos anchor) {
        checkBlockApi();
        var loader = new MachineLoader();
        var data = StructureNbt.read(JsonParser.parseString("""
                {"nbt":{"Items":[{"Slot":0,"id":"minecraft:diamond","Count":7}]},
                 "preview-nbt":{"Items":[{"Slot":0,"id":"minecraft:diamond","Count":7}]}}
                """).getAsJsonObject());
        var pattern = new MachinePattern();
        BlockPos stairOffset = new BlockPos(2, 0, 1), chestOffset = new BlockPos(-2, 1, 1);
        BlockPos bareOffset = new BlockPos(0, 2, -2), outsideOffset = new BlockPos(0, -1, 3);
        pattern.addPart(new MachinePattern.Part(2, 0, 1, "minecraft:quartz_stairs@0"));
        pattern.addPart(new MachinePattern.Part(-2, 1, 1, "minecraft:chest@0", "rotation", data));
        pattern.addPart(new MachinePattern.Part(0, 2, -2, "minecraft:furnace"));
        var machine = new DynamicMachine("rotation_regression", "Rotation", pattern);
        var overlap = replacement("minecraft:chest@0", stairOffset);
        var exterior = replacement("minecraft:quartz_stairs@1", outsideOffset);
        machine.setModifierReplacements(List.of(overlap, exterior));
        var multi = new MultiBlockModifierReplacement("rotated-upgrade",
                List.of(new MultiBlockModifierReplacement.Cell(stairOffset, "minecraft:quartz_stairs@0"),
                        new MultiBlockModifierReplacement.Cell(outsideOffset, "minecraft:quartz_stairs@1")),
                exterior.getModifiers(), List.of());
        var definition = new WorldProjectionDefinition(machine, pattern, loader.getVariables(), loader.getResolver());
        var placeholder = RegistryBlocks.blockCasing.get().defaultBlockState();

        for (int turn = 0; turn < FACINGS.length; turn++) {
            Direction facing = FACINGS[turn];
            BlockPos stair = expectedWorld(anchor, turn, stairOffset), chest = expectedWorld(anchor, turn, chestOffset);
            BlockPos bare = expectedWorld(anchor, turn, bareOffset), outside = expectedWorld(anchor, turn, outsideOffset);
            Map<BlockPos, Saved> before = new LinkedHashMap<>();
            for (var pos : List.of(anchor, stair, chest, bare, outside)) before.put(pos, save(level, pos));
            try (var scene = new WorldProjectionScene(level, anchor, definition.cells())) {
                before.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
                require(StructureMatcher.toWorldOffset(anchor, facing, 2, 0, 1).equals(stair), "coordinate convention: " + facing);
                var ghost = PreviewCellDecision.decide(level, stair, definition.cells().get(stairOffset), facing, placeholder, 0);
                require(ghost.cell() == PreviewCellDecision.PreviewCell.GHOST
                                && ghost.state().getValue(StairBlock.FACING) == EAST_ROTATED[turn], "stair ghost facing: " + facing);
                require(definition.cells().get(outsideOffset).sample(0, facing).getValue(StairBlock.FACING) == WEST_ROTATED[turn],
                        "external upgrade ghost facing: " + facing);
                scene.update(anchor, facing, 0, 100);
                require(scene.level().getBlockState(stair).getValue(StairBlock.FACING) == EAST_ROTATED[turn]
                                && scene.level().getBlockEntity(chest) instanceof ChestBlockEntity preview
                                && preview.getBlockState().getValue(ChestBlock.FACING) == NORTH_ROTATED[turn]
                                && preview.getItem(0).is(Items.DIAMOND) && preview.getItem(0).getCount() == 7,
                        "detached entity/state/NBT rotation: " + facing);
                require(loader.fillMissing(level, anchor, facing, machine) == 3, "builder did not fill base: " + facing);
                require(level.getBlockState(stair).getValue(StairBlock.FACING) == EAST_ROTATED[turn]
                                && level.getBlockState(chest).getValue(ChestBlock.FACING) == NORTH_ROTATED[turn]
                                && level.getBlockState(bare).getValue(FurnaceBlock.FACING) == NORTH_ROTATED[turn],
                        "builder placed unrotated pinned/default state: " + facing);
                require(matches(level, anchor, facing, machine, loader), "builder and matcher disagree: " + facing);
                require(loader.fillMissing(level, anchor, facing, machine) == 0, "builder changed valid rotated state/NBT: " + facing);
                require(PreviewLayerSelection.lowestIncomplete(level, anchor, facing, pattern, machine, loader) == null,
                        "base layer completion differs from matching: " + facing);
                require(Objects.equals(PreviewLayerSelection.lowestIncomplete(level, anchor, facing, definition), -1),
                        "external upgrade must affect projection only: " + facing);
                level.setBlock(anchor, RegistryBlocks.blockController.get().defaultBlockState()
                        .setValue(BlockController.FACING, facing).setValue(BlockController.FORMED, true), Block.UPDATE_ALL);
                level.setBlock(outside, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, WEST_ROTATED[turn]), Block.UPDATE_ALL);
                require(exterior.matches(level, anchor, facing) && multi.matches(level, anchor, facing, loader.getResolver()),
                        "rotated single/multiblock upgrade inactive: " + facing);
                require(PreviewLayerSelection.lowestIncomplete(level, anchor, facing, definition) == null,
                        "completed directional projection retained a layer: " + facing);
                require(PreviewCellDecision.decide(level, stair, definition.cells().get(stairOffset), facing, placeholder, 0).cell()
                        == PreviewCellDecision.PreviewCell.MATCHED, "correct rotated stair marked wrong: " + facing);

                // Bare ids still accept all orientations; metadata-pinned ids reject even the same block facing away.
                level.setBlock(bare, level.getBlockState(bare).setValue(FurnaceBlock.FACING, Direction.SOUTH), Block.UPDATE_ALL);
                require(matches(level, anchor, facing, machine, loader) && loader.fillMissing(level, anchor, facing, machine) == 0,
                        "bare block orientation became constrained: " + facing);
                level.setBlock(stair, level.getBlockState(stair).setValue(StairBlock.FACING, EAST_ROTATED[turn].getOpposite()), Block.UPDATE_ALL);
                require(!matches(level, anchor, facing, machine, loader) && !multi.matches(level, anchor, facing, loader.getResolver()),
                        "wrong facing accepted by formation/upgrade: " + facing);
                require(PreviewCellDecision.decide(level, stair, definition.cells().get(stairOffset), facing, placeholder, 0).cell()
                                == PreviewCellDecision.PreviewCell.HIGHLIGHTED
                                && Objects.equals(PreviewLayerSelection.lowestIncomplete(level, anchor, facing, definition), 0)
                                && Objects.equals(PreviewLayerSelection.lowestIncomplete(level, anchor, facing, pattern, machine, loader), 0),
                        "wrong facing skipped by ghost/layer checks: " + facing);
                require(loader.fillMissing(level, anchor, facing, machine) == 1 && matches(level, anchor, facing, machine, loader),
                        "builder did not repair wrong orientation: " + facing);
                level.setBlock(outside, level.getBlockState(outside).setValue(StairBlock.FACING, WEST_ROTATED[turn].getOpposite()), Block.UPDATE_ALL);
                require(!exterior.matches(level, anchor, facing) && !multi.matches(level, anchor, facing, loader.getResolver())
                                && matches(level, anchor, facing, machine, loader), "external upgrade changed formation requirement: " + facing);
                var chestState = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, NORTH_ROTATED[turn]);
                level.setBlock(stair, chestState, Block.UPDATE_ALL);
                var retainedEntity = level.getBlockEntity(stair);
                require(overlap.matches(level, anchor, facing) && matches(level, anchor, facing, machine, loader)
                                && loader.fillMissing(level, anchor, facing, machine) == 0 && level.getBlockEntity(stair) == retainedEntity,
                        "valid rotated replacement rebuilt: " + facing);
                require(PreviewCellDecision.decide(level, stair, definition.cells().get(stairOffset), facing, placeholder, 0).cell()
                        == PreviewCellDecision.PreviewCell.MATCHED, "valid rotated replacement remains highlighted: " + facing);
                require(definition.cells().get(stairOffset).sample(40, facing).getValue(ChestBlock.FACING) == NORTH_ROTATED[turn]
                                && definition.cells().get(stairOffset).sample(39, facing).getValue(StairBlock.FACING) == EAST_ROTATED[turn],
                        "rotation changed candidate order or 40-tick period: " + facing);
                ((ChestBlockEntity) level.getBlockEntity(chest)).setItem(0, new net.minecraft.world.item.ItemStack(Items.DIRT));
                require(!matches(level, anchor, facing, machine, loader)
                                && !definition.cells().get(chestOffset).matches(level, chest, facing), "rotation bypassed matching NBT: " + facing);
            } finally {
                before.forEach((pos, saved) -> restore(level, pos, saved));
            }
        }
        checkDynamicScene(level, anchor, loader);
        require(BlockStateShim.resolve("minecraft:quartz_stairs@0").getValue(StairBlock.FACING) == Direction.EAST
                        && definition.cells().get(chestOffset).states().get(0).getValue(ChestBlock.FACING) == Direction.NORTH,
                "rotation mutated resolver/definition templates");
        Modularmachinery.LOG.info("[MMCE][regression] PASS: source state rotation: four independent direction/coordinate oracles, "
                + "stairs/axes/rails/connections/banners, world ghosts and NBT entities, builder/matcher/layers, "
                + "single/multiblock upgrades, alternatives/bare ids, wrong-state rejection and dynamic snapshot isolation");
    }

    private static void checkBlockApi() {
        var stair = Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, Direction.EAST)
                .setValue(StairBlock.HALF, Half.TOP).setValue(StairBlock.WATERLOGGED, true);
        Direction.Axis[] axes = {Direction.Axis.X, Direction.Axis.Z, Direction.Axis.X, Direction.Axis.Z};
        RailShape[] rails = {RailShape.ASCENDING_EAST, RailShape.ASCENDING_NORTH, RailShape.ASCENDING_WEST, RailShape.ASCENDING_SOUTH};
        int[] bannerAngles = {0, 12, 8, 4};
        for (int i = 0; i < FACINGS.length; i++) {
            var result = StructureMatcher.rotateState(stair, FACINGS[i]);
            require(result.getValue(StairBlock.FACING) == EAST_ROTATED[i] && result.getValue(StairBlock.HALF) == Half.TOP
                            && result.getValue(StairBlock.WATERLOGGED), "stair orientation/other properties: " + FACINGS[i]);
            require(StructureMatcher.rotateState(Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X),
                    FACINGS[i]).getValue(RotatedPillarBlock.AXIS) == axes[i], "pillar axis: " + FACINGS[i]);
            require(StructureMatcher.rotateState(Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.ASCENDING_EAST),
                    FACINGS[i]).getValue(RailBlock.SHAPE) == rails[i], "rail shape: " + FACINGS[i]);
            var bars = StructureMatcher.rotateState(Blocks.IRON_BARS.defaultBlockState().setValue(IronBarsBlock.NORTH, true), FACINGS[i]);
            for (var dir : Direction.Plane.HORIZONTAL) {
                var property = switch (dir) {
                    case NORTH -> BlockStateProperties.NORTH;
                    case WEST -> BlockStateProperties.WEST;
                    case SOUTH -> BlockStateProperties.SOUTH;
                    case EAST -> BlockStateProperties.EAST;
                    default -> throw new AssertionError("non-horizontal connection");
                };
                require(bars.getValue(property) == (dir == NORTH_ROTATED[i]),
                        "connection did not rotate: " + FACINGS[i] + "/" + dir);
            }
            require(StructureMatcher.rotateState(Blocks.WHITE_BANNER.defaultBlockState().setValue(BannerBlock.ROTATION, 0), FACINGS[i])
                    .getValue(BannerBlock.ROTATION) == bannerAngles[i], "standing banner angle: " + FACINGS[i]);
            require(StructureMatcher.rotateState(Blocks.PISTON.defaultBlockState().setValue(BlockStateProperties.FACING, Direction.UP), FACINGS[i])
                    .getValue(BlockStateProperties.FACING) == Direction.UP, "vertical state changed");
            require(StructureMatcher.rotateState(Blocks.STONE.defaultBlockState(), FACINGS[i]).equals(Blocks.STONE.defaultBlockState()),
                    "non-directional state changed");
        }
        require(StructureMatcher.rotateState(null, Direction.NORTH) == null
                        && StructureMatcher.rotateState(stair, Direction.UP).equals(stair), "empty/vertical fallback differs from coordinates");
    }

    private static void checkDynamicScene(ServerLevel level, BlockPos anchor, MachineLoader loader) {
        var machine = new DynamicMachine("dynamic_rotation", "Dynamic", new MachinePattern());
        var dynamic = new DynamicPattern("line");
        dynamic.setMinSize(1); dynamic.setMaxSize(2);
        dynamic.setStructureSizeOffsetStart(new BlockPos(4, 0, 0));
        dynamic.setStructureSizeOffset(new BlockPos(0, 0, 2));
        dynamic.getPattern().addPart(new MachinePattern.Part(0, 0, 0, "minecraft:quartz_stairs@0"));
        machine.addDynamicPattern(dynamic);
        var small = new WorldProjectionDefinition(machine, BlueprintPreviewState.buildPattern(machine, 1), loader.getVariables(), loader.getResolver());
        var expanded = BlueprintPreviewState.buildPattern(machine, 2);
        var large = new WorldProjectionDefinition(machine, expanded, loader.getVariables(), loader.getResolver());
        for (int i = 0; i < FACINGS.length; i++) {
            Direction facing = FACINGS[i];
            BlockPos first = expectedWorld(anchor, i, new BlockPos(4, 0, 0)), second = expectedWorld(anchor, i, new BlockPos(4, 0, 2));
            var beforeFirst = save(level, first); var beforeSecond = save(level, second);
            try (var scene = new WorldProjectionScene(level, anchor, large.cells())) {
                level.setBlock(first, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, EAST_ROTATED[i]), Block.UPDATE_ALL);
                level.setBlock(second, Blocks.QUARTZ_STAIRS.defaultBlockState().setValue(StairBlock.FACING, EAST_ROTATED[i]), Block.UPDATE_ALL);
                require(dynamic.matchSize(level, anchor, facing, loader.getVariables(), loader.getResolver()) == 2
                                && new StructureMatcher().match(level, anchor, facing, expanded, loader.getVariables(), loader.getResolver()).matched(),
                        "expanded directional segment does not match: " + facing);
                scene.update(anchor, facing, 0, 1);
                require(scene.level().getBlockState(first).equals(level.getBlockState(first))
                                && scene.level().getBlockState(second).equals(level.getBlockState(second))
                                && !small.cells().containsKey(new BlockPos(4, 0, 2)), "dynamic size/rotation snapshot mismatch: " + facing);
            } finally { restore(level, first, beforeFirst); restore(level, second, beforeSecond); }
        }
        require(dynamic.getPattern().size() == 1 && dynamic.getPattern().getParts().get(0).x() == 0,
                "dynamic rotation modified template");
    }

    // Written independently from toWorldOffset: source NORTH identity, then (z,y,-x) per quarter-turn.
    private static BlockPos expectedWorld(BlockPos anchor, int turn, BlockPos p) {
        return anchor.offset(new BlockPos[]{p, new BlockPos(p.getZ(), p.getY(), -p.getX()),
                new BlockPos(-p.getX(), p.getY(), -p.getZ()), new BlockPos(-p.getZ(), p.getY(), p.getX())}[turn]);
    }
    private static boolean matches(ServerLevel level, BlockPos pos, Direction facing, DynamicMachine machine, MachineLoader loader) {
        return new StructureMatcher().match(level, pos, facing, machine.getPattern(), loader.getVariables(), loader.getResolver(),
                machine.getModifierReplacements()).matched();
    }
    private static ModifierReplacement replacement(String block, BlockPos offset) {
        var json = JsonParser.parseString("""
                {"modifier":{"target":"modularmachinery:duration","io":"input","operation":1,"multiplier":0.5}}
                """).getAsJsonObject();
        json.addProperty("elements", block); json.addProperty("x", offset.getX());
        json.addProperty("y", offset.getY()); json.addProperty("z", offset.getZ());
        return ModifierReplacement.deserialize(json);
    }
    private record Saved(BlockState state, CompoundTag nbt) { }
    private static Saved save(ServerLevel level, BlockPos pos) {
        var entity = level.getBlockEntity(pos);
        return new Saved(level.getBlockState(pos), entity == null ? null : entity.saveWithoutMetadata());
    }
    private static void restore(ServerLevel level, BlockPos pos, Saved saved) {
        level.setBlock(pos, saved.state(), Block.UPDATE_ALL);
        if (saved.nbt() != null && level.getBlockEntity(pos) != null) level.getBlockEntity(pos).load(saved.nbt());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
