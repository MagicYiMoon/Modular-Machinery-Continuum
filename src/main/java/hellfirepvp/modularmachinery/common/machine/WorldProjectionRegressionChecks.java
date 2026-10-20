/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10.
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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Source projection scope and world completion, kept separate from gameplay formation. */
public final class WorldProjectionRegressionChecks {
    private WorldProjectionRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) {
        var loader = new MachineLoader();
        var base = new MachinePattern();
        var nbt = StructureNbt.read(JsonParser.parseString("""
                {"nbt":{"inventory":{}},"preview-nbt":{"CustomName":"preview"}}
                """).getAsJsonObject());
        var lowerPart = new MachinePattern.Part(1, -1, 0, "minecraft:stone");
        var upperPart = new MachinePattern.Part(1, 1, 0, "modularmachinery:blockinputbus", "red", nbt);
        base.addPart(lowerPart);
        base.addPart(upperPart);
        var machine = new DynamicMachine("world_projection_scope", "Projection scope", base);
        BlockPos external = new BlockPos(-2, -3, 1), lower = new BlockPos(1, -1, 0), upper = new BlockPos(1, 1, 0);
        machine.setModifierReplacements(List.of(replacement("minecraft:gold_block", external),
                replacement("minecraft:diamond_block", external), replacement("minecraft:emerald_block", lower)));
        var multiOnly = new BlockPos(2, -4, -1);
        machine.addMultiBlockModifierReplacement(new MultiBlockModifierReplacement("not-in-source-world-preview",
                List.of(new MultiBlockModifierReplacement.Cell(multiOnly, "minecraft:glass")),
                machine.getModifierReplacements().get(0).getModifiers(), List.of()));
        var projection = new WorldProjectionDefinition(machine, base, loader.getVariables(), loader.getResolver());
        require(projection.cells().size() == 4 && projection.cells().containsKey(BlockPos.ZERO)
                        && !projection.cells().containsKey(multiOnly), "controller/exterior single-upgrade scope differs from source");
        require(projection.cells().get(external).states().equals(List.of(Blocks.GOLD_BLOCK.defaultBlockState(),
                        Blocks.DIAMOND_BLOCK.defaultBlockState()))
                        && projection.cells().get(lower).states().equals(List.of(Blocks.STONE.defaultBlockState(),
                        Blocks.EMERALD_BLOCK.defaultBlockState())), "exterior or overlapping alternatives were overwritten");
        require(projection.cells().get(upper).part().equals(upperPart)
                        && projection.cells().get(upper).part().previewNbt().getString("CustomName").equals("preview"),
                "projection lost selector/NBT/preview-NBT metadata");
        require(base.size() == 2 && !base.getParts().stream().anyMatch(p -> p.y() == -3),
                "projection added exterior upgrades to the gameplay pattern");

        var placeholder = RegistryBlocks.blockCasing.get().defaultBlockState();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Map<BlockPos, BlockState> before = new LinkedHashMap<>();
            projection.cells().keySet().forEach(pos -> {
                BlockPos world = world(origin, facing, pos);
                before.put(world, level.getBlockState(world));
            });
            try {
                before.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
                var controllerCell = projection.cells().get(BlockPos.ZERO);
                for (long tick : new long[]{0, 40}) {
                    var ghost = PreviewCellDecision.decide(level, origin, controllerCell, facing, placeholder, tick);
                    require(ghost.cell() == PreviewCellDecision.PreviewCell.GHOST
                                    && ghost.state().getValue(BlockController.FACING) == facing
                                    && ghost.state().is(tick == 0 ? RegistryBlocks.blockController.get()
                                    : RegistryBlocks.blockFactoryController.get()), "controller ghost type/facing wrong");
                }
                level.setBlock(world(origin, facing, lower), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(world(origin, facing, upper), RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
                require(new StructureMatcher().match(level, origin, facing, base, loader.getVariables(), loader.getResolver(),
                                machine.getModifierReplacements()).matched(), "optional exterior upgrade became required for formation");
                require(Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, projection), -3),
                        "exterior upgrade below base not included in projection progression");
                var outside = PreviewCellDecision.decide(level, world(origin, facing, external), projection.cells().get(external),
                        facing, placeholder, 40);
                require(outside.cell() == PreviewCellDecision.PreviewCell.GHOST && outside.state().is(Blocks.DIAMOND_BLOCK),
                        "exterior upgrade lost captured candidate");
                level.setBlock(world(origin, facing, external), Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                require(Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, projection), 0),
                        "missing controller did not keep its layer incomplete");
                for (var block : List.of(RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get())) {
                    for (boolean formed : new boolean[]{false, true}) {
                        level.setBlock(origin, block.defaultBlockState().setValue(BlockController.FACING, facing)
                                .setValue(BlockController.FORMED, formed), Block.UPDATE_ALL);
                        require(PreviewLayerSelection.lowestIncomplete(level, origin, facing, projection) == null
                                        && PreviewCellDecision.decide(level, origin, controllerCell, facing, placeholder, 40).cell()
                                        == PreviewCellDecision.PreviewCell.MATCHED,
                                "controller type/formed property kept a completed projection alive: " + facing);
                    }
                }
                level.setBlock(origin, RegistryBlocks.blockController.get().defaultBlockState()
                        .setValue(BlockController.FACING, facing.getOpposite()), Block.UPDATE_ALL);
                require(Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, projection), 0),
                        "controller facing mismatch incorrectly accepted");
                level.setBlock(origin, RegistryBlocks.blockController.get().defaultBlockState()
                        .setValue(BlockController.FACING, facing), Block.UPDATE_ALL);
                level.removeBlock(world(origin, facing, lower), false);
                require(Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, projection), -1),
                        "broken lower base layer did not return");
                level.setBlock(world(origin, facing, lower), Blocks.EMERALD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                require(PreviewLayerSelection.lowestIncomplete(level, origin, facing, projection) == null,
                        "overlapping base upgrade not accepted");
                level.setBlock(world(origin, facing, external), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                var wrong = PreviewCellDecision.decide(level, world(origin, facing, external), projection.cells().get(external),
                        facing, placeholder, 40);
                require(wrong.highlight() && wrong.state().is(Blocks.DIAMOND_BLOCK)
                                && Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, projection), -3),
                        "wrong exterior block lost its highlight/expected state/lower layer");
                level.setBlock(world(origin, facing, external), Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                require(PreviewLayerSelection.lowestIncomplete(level, origin, facing, projection) == null,
                        "second exterior alternative did not finish the layer");
                var badNbt = StructureNbt.read(JsonParser.parseString("{\"nbt\":{\"inventory\":{\"missing\":1}}}").getAsJsonObject());
                var badCell = new WorldProjectionDefinition.Cell(new MachinePattern.Part(1, 1, 0,
                        upperPart.elements(), "red", badNbt), projection.cells().get(upper).states(), false);
                require(!badCell.matches(level, world(origin, facing, upper), facing)
                                && PreviewCellDecision.decide(level, world(origin, facing, upper), badCell, facing, placeholder, 0).highlight(),
                        "projection matching ignored base-part NBT");
            } finally {
                before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            }
        }

        BlockPos unloaded = origin.offset(1_000_000, 0, 1_000_000);
        require(!level.isLoaded(unloaded), "unloaded projection fixture unexpectedly loaded");
        var outside = PreviewCellDecision.decide(level, unloaded, projection.cells().get(external), Direction.NORTH, placeholder, 40);
        require(outside.cell() == PreviewCellDecision.PreviewCell.GHOST && !outside.highlight()
                        && outside.state().is(Blocks.DIAMOND_BLOCK) && !level.isLoaded(unloaded),
                "unloaded exterior cell loaded a chunk or used a casing placeholder");
        checkDynamicDefinition(machine, loader);
        Modularmachinery.LOG.info("[MMCE][regression] PASS: source world projection scope: controller/exterior single upgrades, "
                + "merged/frozen alternatives, four facings/controller types/formed states, NBT, layer completion/rollback, "
                + "unloaded cells, dynamic expansion, gameplay isolation and multi-upgrade exclusion");
    }

    private static void checkDynamicDefinition(DynamicMachine machine, MachineLoader loader) {
        var dynamic = new DynamicPattern("projection_size");
        dynamic.setMinSize(1);
        dynamic.setMaxSize(2);
        dynamic.setStructureSizeOffsetStart(new BlockPos(3, 0, 0));
        dynamic.setStructureSizeOffset(new BlockPos(0, 1, 0));
        dynamic.getPattern().addPart(new MachinePattern.Part(0, 0, 0, "minecraft:glass"));
        machine.addDynamicPattern(dynamic);
        var small = new WorldProjectionDefinition(machine, BlueprintPreviewState.buildPattern(machine, 1),
                loader.getVariables(), loader.getResolver());
        var large = new WorldProjectionDefinition(machine, BlueprintPreviewState.buildPattern(machine, 2),
                loader.getVariables(), loader.getResolver());
        require(small.cells().size() == 5 && large.cells().size() == 6 && !small.cells().containsKey(new BlockPos(3, 1, 0))
                        && large.cells().get(new BlockPos(3, 1, 0)).states().get(0).is(Blocks.GLASS)
                        && machine.getPattern().size() == 2 && dynamic.getPattern().size() == 1,
                "projection dynamic size changed source templates or an earlier projection");
        machine.setModifierReplacements(List.of());
        require(small.cells().containsKey(new BlockPos(-2, -3, 1)), "live modifier changes mutated a captured projection");
    }

    private static ModifierReplacement replacement(String block, BlockPos pos) {
        var json = JsonParser.parseString("""
                {"elements":"minecraft:stone","x":0,"y":0,"z":0,
                 "modifier":{"target":"modularmachinery:duration","io":"input","operation":1,"multiplier":0.5}}
                """).getAsJsonObject();
        json.addProperty("elements", block);
        json.addProperty("x", pos.getX()); json.addProperty("y", pos.getY()); json.addProperty("z", pos.getZ());
        return ModifierReplacement.deserialize(json);
    }
    private static BlockPos world(BlockPos origin, Direction facing, BlockPos pos) {
        return StructureMatcher.toWorldOffset(origin, facing, pos.getX(), pos.getY(), pos.getZ());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
