/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.geo.ControllerModelAnimationEvent;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Real registered controllers, all cardinal facings, source coordinate constants and detached render snapshots. */
public final class ControllerSpatialRegressionChecks {
    public static final String TARGET = "mmc_controller_spatial_regression";
    private ControllerSpatialRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var blocks = new ArrayList<Block>();
        blocks.add(RegistryBlocks.blockController.get()); blocks.add(RegistryBlocks.blockFactoryController.get());
        for (boolean factory : List.of(false, true)) {
            var entry = DedicatedControllers.entries().stream().filter(e -> e.factory() == factory).findFirst().orElseThrow();
            blocks.add(entry.block().get());
        }
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        var machines = MachineReloadListener.getLoader().getMachines();
        var savedMachines = new LinkedHashMap<>(machines);
        boolean scripts = net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker");
        try {
            machines.put(new net.minecraft.resources.ResourceLocation("modularmachinery", TARGET),
                    new DynamicMachine(TARGET, TARGET, new MachinePattern()));
            if (scripts) hellfirepvp.modularmachinery.integration.crafttweaker.ControllerSpatialScriptRegressionChecks.prepare();
            for (int i = 0; i < blocks.size(); i++) {
                BlockPos pos = new BlockPos(-Math.abs(origin.getX()) - 48 - i * 4, origin.getY(), -Math.abs(origin.getZ()) - 32);
                saved.put(pos, level.getBlockState(pos));
                for (Direction facing : List.of(Direction.NORTH, Direction.WEST, Direction.SOUTH, Direction.EAST)) {
                    var state = blocks.get(i).defaultBlockState().setValue(BlockController.FACING, facing);
                    level.setBlock(pos, state, Block.UPDATE_ALL);
                    var tile = (TileController) level.getBlockEntity(pos);
                    require(tile != null, "controller did not create a tile");
                    tile.setMachineName(TARGET);
                    var event = new ControllerModelAnimationEvent(tile, null, null);
                    check(tile, event, pos, state, facing);
                    tile.setFormed(true);
                    check(tile, event, pos, tile.getBlockState(), facing);
                    // This is the same saved/update-data path used by client-side render tiles.
                    TileController observation = tile instanceof TileFactoryController
                            ? new TileFactoryController(pos, tile.getBlockState()) : new TileController(pos, tile.getBlockState());
                    observation.load(tile.getUpdateTag());
                    check(observation, new ControllerModelAnimationEvent(observation, null, null), pos, tile.getBlockState(), facing);
                    // Dedicated identities cannot be rebound to the synthetic recipe/model machine.
                    // Their inherited spatial API is checked above; compile callbacks for both generic tile classes.
                    if (scripts && i < 2)
                        hellfirepvp.modularmachinery.integration.crafttweaker.ControllerSpatialScriptRegressionChecks.run(tile, observation);
                    tile.setFormed(false);
                }
            }
            // Replacing a block with one without FACING must not throw or retain an old orientation.
            var fallback = new TileController(origin, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            require(fallback.getFacing() == Direction.NORTH && fallback.rotateWithControllerFacing(new BlockPos(2, -3, 5))
                    .equals(new BlockPos(2, -3, 5)), "missing-facing NORTH fallback differs");
            Modularmachinery.LOG.info("[MMC][controller-spatial] PASS: generic/dedicated ordinary/factory, four facings, source offsets, formation, immutable values and render snapshot queries");
        } finally {
            saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            if (scripts) hellfirepvp.modularmachinery.integration.crafttweaker.ControllerSpatialScriptRegressionChecks.cleanup();
            machines.clear(); machines.putAll(savedMachines); MachineReloadListener.refreshRecipes();
        }
    }
    private static void check(TileController tile, MachineEvent event, BlockPos pos, BlockState state, Direction facing) {
        var controller = event.getIMachineController();
        require(controller == tile && controller.getIPos().equals(pos) && controller.getFacing() == facing
                && controller.getIBlockState() == state, "event spatial query differs from controller");
        // Explicit source outputs; do not derive expectations using the production transform.
        var offsets = List.of(new BlockPos(2, -3, 5), new BlockPos(-7, 4, -11), BlockPos.ZERO);
        for (var offset : offsets) {
            int x = offset.getX(), y = offset.getY(), z = offset.getZ();
            BlockPos expected = switch (facing) {
                case WEST -> new BlockPos(z, y, -x);
                case SOUTH -> new BlockPos(-x, y, -z);
                case EAST -> new BlockPos(-z, y, x);
                default -> new BlockPos(x, y, z);
            };
            BlockPos result = controller.rotateWithControllerFacing(offset);
            require(result.equals(expected) && pos.offset(result).equals(StructureMatcher.toWorldOffset(pos, facing, x, y, z)),
                    "script rotation differs from source or structure formation");
            var mutable = offset.mutable(); var copy = controller.rotateWithControllerFacing(mutable); mutable.set(99, 99, 99);
            require(copy.equals(expected) && !(copy instanceof BlockPos.MutableBlockPos), "rotated coordinate retained mutable input");
        }
        var returned = controller.getIPos(); returned.mutable().set(99, 99, 99);
        require(!(returned instanceof BlockPos.MutableBlockPos) && controller.getIPos().equals(pos), "position query leaks tile coordinates");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
