/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.modifier.MultiBlockModifierReplacement;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

/** In-world regression for the source mod's all-cells-and-rotation multiblock modifier rule. */
public final class MultiBlockModifierRegressionChecks {
    private MultiBlockModifierRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) {
        RecipeModifier modifier = RecipeModifier.deserialize(
                com.google.gson.JsonParser.parseString("""
                        {"target":"modularmachinery:duration","io":"output","operation":1,"multiplier":2}
                        """).getAsJsonObject());
        MultiBlockModifierReplacement replacement = new MultiBlockModifierReplacement(
                "regression-multiblock",
                List.of(new MultiBlockModifierReplacement.Cell(new BlockPos(1, 0, 0), "minecraft:stone"),
                        new MultiBlockModifierReplacement.Cell(new BlockPos(0, 0, 1), "minecraft:stone")),
                List.of(modifier), List.of("regression"));
        BlockStateResolver resolver = new BlockStateResolver();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos first = StructureMatcher.toWorldOffset(origin, facing, 1, 0, 0);
            BlockPos second = StructureMatcher.toWorldOffset(origin, facing, 0, 0, 1);
            var beforeFirst = level.getBlockState(first);
            var beforeSecond = level.getBlockState(second);
            try {
                level.setBlock(first, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(second, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                require(replacement.matches(level, origin, facing, resolver),
                        "all multiblock cells did not match for " + facing);
                level.removeBlock(second, false);
                require(!replacement.matches(level, origin, facing, resolver),
                        "missing multiblock cell still activated for " + facing);
            } finally {
                level.setBlock(first, beforeFirst, Block.UPDATE_ALL);
                level.setBlock(second, beforeSecond, Block.UPDATE_ALL);
            }
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
