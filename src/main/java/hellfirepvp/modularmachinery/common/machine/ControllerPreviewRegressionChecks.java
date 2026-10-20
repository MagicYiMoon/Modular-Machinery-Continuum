/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 * Derived from Modular Machinery CE; upstream HellFirePvP / KasumiNova attribution retained.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** Real tick discovery and world-layer decisions, independent of the client's renderer. */
public final class ControllerPreviewRegressionChecks {
    private ControllerPreviewRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) {
        var loader = MachineReloadListener.getLoader();
        var definitions = new LinkedHashMap<>(loader.getMachines());
        var pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, -1, 0, "minecraft:diamond_block"));
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:blockinputbus"));
        pattern.addPart(new MachinePattern.Part(1, 1, 0, "minecraft:gold_block"));
        var normal = new DynamicMachine("preview_auto_normal", "Normal", pattern);
        var required = new DynamicMachine("preview_auto_required", "Required", pattern);
        required.setRequiresBlueprint(true);
        checkProjectionCandidates(level, origin, loader);
        try {
            loader.getMachines().clear();
            loader.getMachines().put(new ResourceLocation("modularmachinery", required.getRegistryName()), required);
            loader.getMachines().put(new ResourceLocation("modularmachinery", normal.getRegistryName()), normal);
            for (boolean factory : List.of(false, true)) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    Map<BlockPos, BlockState> before = new LinkedHashMap<>();
                    before.put(origin, level.getBlockState(origin));
                    for (var part : pattern.getParts()) {
                        BlockPos pos = StructureMatcher.toWorldOffset(origin, facing, part.x(), part.y(), part.z());
                        before.put(pos, level.getBlockState(pos));
                    }
                    try {
                        before.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
                        var block = factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get();
                        level.setBlock(origin, block.defaultBlockState().setValue(
                                hellfirepvp.modularmachinery.common.block.BlockController.FACING, facing), Block.UPDATE_ALL);
                        var tile = (TileController) level.getBlockEntity(origin);
                        loader.fillMissing(level, origin, facing, normal);
                        BlockPos busPos = StructureMatcher.toWorldOffset(origin, facing, 1, 0, 0);
                        var bus = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(busPos)).getInventory();
                        bus.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 7));
                        tick(level, origin, tile);
                        require(tile.isFormed() && normal.getRegistryName().equals(tile.getMachineName()),
                                "first server tick did not detect blueprint-free structure: " + factory + "/" + facing);
                        require(bus.getStackInSlot(0).getCount() == 7, "discovery consumed input");
                        BlockPos lower = StructureMatcher.toWorldOffset(origin, facing, 1, -1, 0);
                        level.removeBlock(lower, false);
                        require(!MachineTicker.structureStillHolds(level, origin, facing, normal, loader, tile)
                                && !tile.isFormed(), "broken structure kept running");
                        level.setBlock(lower, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                        tick(level, origin, tile);
                        require(tile.isFormed(), "repaired structure did not reform automatically");

                        tile.setFormed(false);
                        loader.getMachines().remove(new ResourceLocation("modularmachinery", normal.getRegistryName()));
                        tick(level, origin, tile);
                        require(!tile.isFormed(), "required blueprint bypassed via stale recognised name");
                        var blueprint = new ItemStack(RegistryItems.BLUEPRINT.get());
                        ItemBlueprint.setAssociatedMachine(blueprint, required);
                        ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, blueprint);
                        tick(level, origin, tile);
                        require(tile.isFormed() && required.getRegistryName().equals(tile.getMachineName()),
                                "blueprint-first discovery failed");
                        ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, ItemStack.EMPTY);
                        require(!tile.isFormed(), "required blueprint removal left machine formed");
                        loader.getMachines().put(new ResourceLocation("modularmachinery", normal.getRegistryName()), normal);
                        tick(level, origin, tile);
                        require(tile.isFormed() && normal.getRegistryName().equals(tile.getMachineName()), "fallback detection failed");
                        ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, blueprint);
                        require(normal.getRegistryName().equals(tile.getMachineName()), "optional blueprint changed a formed machine");
                        ((IItemHandlerModifiable) tile.getBlueprintInventory()).setStackInSlot(0, ItemStack.EMPTY);
                        require(tile.isFormed(), "optional blueprint removal reset a formed machine");

                        require(PreviewLayerSelection.lowestIncomplete(level, origin, facing, pattern, normal, loader) == null,
                                "complete structure retains a projected layer");
                        BlockPos upper = StructureMatcher.toWorldOffset(origin, facing, 1, 1, 0);
                        level.removeBlock(lower, false);
                        level.removeBlock(upper, false);
                        require(Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, pattern, normal, loader), -1),
                                "lowest unfinished layer not selected");
                        level.setBlock(lower, Blocks.DIAMOND_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                        require(Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, pattern, normal, loader), 1),
                                "completed layer did not advance");
                        level.removeBlock(lower, false);
                        require(Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, pattern, normal, loader), -1),
                                "broken lower layer not revisited");
                    } finally {
                        before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
                        loader.getMachines().put(new ResourceLocation("modularmachinery", normal.getRegistryName()), normal);
                    }
                }
            }
            Modularmachinery.LOG.info("[MMCE][regression] PASS: automatic ordinary/factory formation across four facings, "
                    + "blueprint priority/removal, repair, no input consumption, world layer advancement/rollback/completion");
        } finally {
            loader.getMachines().clear();
            loader.getMachines().putAll(definitions);
        }
    }

    /** Frozen visual candidates must not narrow the set of blocks accepted by matching or layer progression. */
    private static void checkProjectionCandidates(ServerLevel level, BlockPos origin, MachineLoader loader) {
        var pattern = new MachinePattern();
        var part = new MachinePattern.Part(1, -1, 0, "minecraft:stone,minecraft:glass");
        pattern.addPart(part);
        var machine = new DynamicMachine("projection_snapshot_regression", "Projection snapshot", pattern);
        var controls = new BlueprintPreviewState(machine, loader.getVariables(), loader.getResolver());
        var placeholder = RegistryBlocks.blockCasing.get().defaultBlockState();
        controls.updateClock(39);
        var first = controls.projectionSnapshot(40);
        controls.updateClock(40);
        var second = controls.projectionSnapshot(80);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos cell = StructureMatcher.toWorldOffset(origin, facing, part.x(), part.y(), part.z());
            BlockState previous = level.getBlockState(cell);
            try {
                level.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                var stone = PreviewCellDecision.decide(level, cell, part, loader.getVariables(), loader.getResolver(),
                        machine, placeholder, first.sampleTick());
                var glass = PreviewCellDecision.decide(level, cell, part, loader.getVariables(), loader.getResolver(),
                        machine, placeholder, second.sampleTick());
                require(stone.cell() == PreviewCellDecision.PreviewCell.GHOST && stone.state().is(Blocks.STONE)
                                && glass.cell() == PreviewCellDecision.PreviewCell.GHOST && glass.state().is(Blocks.GLASS),
                        "world projection ignored the GUI candidate phase: " + facing);
                controls.updateClock(120);
                require(PreviewCellDecision.decide(level, cell, part, loader.getVariables(), loader.getResolver(),
                                machine, placeholder, first.sampleTick()).state().equals(stone.state()),
                        "pending/placed projection resumed cycling with its source panel");
                level.setBlock(cell, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                var wrong = PreviewCellDecision.decide(level, cell, part, loader.getVariables(), loader.getResolver(),
                        machine, placeholder, second.sampleTick());
                require(wrong.highlight() && wrong.state().is(Blocks.GLASS)
                                && Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, pattern, machine, loader), -1),
                        "wrong cell lost the frozen candidate or its unfinished layer");
                for (BlockState accepted : List.of(Blocks.STONE.defaultBlockState(), Blocks.GLASS.defaultBlockState())) {
                    level.setBlock(cell, accepted, Block.UPDATE_ALL);
                    for (var snapshot : List.of(first, second)) {
                        var match = PreviewCellDecision.decide(level, cell, part, loader.getVariables(), loader.getResolver(),
                                machine, placeholder, snapshot.sampleTick());
                        require(match.cell() == PreviewCellDecision.PreviewCell.MATCHED && !match.highlight()
                                        && PreviewLayerSelection.lowestIncomplete(level, origin, facing, pattern, machine, loader) == null,
                                "frozen sample narrowed matching/layer completion to the displayed alternative");
                    }
                }
                level.removeBlock(cell, false);
                require(Objects.equals(PreviewLayerSelection.lowestIncomplete(level, origin, facing, pattern, machine, loader), -1)
                                && PreviewCellDecision.decide(level, cell, part, loader.getVariables(), loader.getResolver(),
                                machine, placeholder, second.sampleTick()).state().is(Blocks.GLASS),
                        "layer rollback changed the captured candidate");
            } finally {
                level.setBlock(cell, previous, Block.UPDATE_ALL);
            }
        }
        Modularmachinery.LOG.info("[MMCE][regression] PASS: frozen world projection candidates across four facings, "
                + "GUI handoff/source clock isolation, wrong/empty cells, all accepted alternatives, layer completion/rollback");
    }

    private static void tick(ServerLevel level, BlockPos pos, TileController tile) {
        if (tile instanceof TileFactoryController factory) FactoryTicker.tick(level, pos, factory);
        else MachineTicker.tick(level, pos, tile);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
