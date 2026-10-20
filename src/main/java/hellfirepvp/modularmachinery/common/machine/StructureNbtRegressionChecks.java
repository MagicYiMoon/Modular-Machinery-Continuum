/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;

/** Regression checks for structure-part nbt and preview-nbt parsing and matching. */
public final class StructureNbtRegressionChecks {
    private StructureNbtRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) {
        var data = StructureNbt.read(JsonParser.parseString("""
                {"x":1,"y":0,"z":0,"elements":"modularmachinery:blockinputbus",
                 "selector-tag":"red","nbt":{"inventory":{}},
                 "preview-nbt":{"Items":[],"CustomName":"preview"}}
                """).getAsJsonObject());
        require(data != null && data.hasMatching() && data.preview() != null,
                "structure nbt fields were not parsed");
        require(data.preview().contains("CustomName"), "preview-nbt concrete tag was lost");

        MachinePattern pattern = new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:blockinputbus", "red", data));
        var loader = new MachineLoader();
        BlockPos busPos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        var before = level.getBlockState(busPos);
        try {
            level.setBlock(busPos, hellfirepvp.modularmachinery.common.registry.RegistryBlocks.itemInputBus
                    .get().defaultBlockState(), Block.UPDATE_ALL);
            require(level.getBlockEntity(busPos) != null, "input bus block entity was not created");
            require(new StructureMatcher().match(level, origin, Direction.NORTH, pattern,
                    loader.getVariables(), loader.getResolver()).matched(),
                    "matching structure NBT rejected a matching block entity");
            var wrong = StructureNbt.read(JsonParser.parseString(
                    "{\"nbt\":{\"inventory\":{\"missing\":1}}}").getAsJsonObject());
            MachinePattern wrongPattern = new MachinePattern();
            wrongPattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:blockinputbus", null, wrong));
            require(!new StructureMatcher().match(level, origin, Direction.NORTH, wrongPattern,
                    loader.getVariables(), loader.getResolver()).matched(),
                    "mismatching block entity NBT was accepted");
        } finally {
            level.setBlock(busPos, before, Block.UPDATE_ALL);
        }

        boolean rejected = false;
        try { StructureNbt.read(JsonParser.parseString("{\"nbt\":[]}").getAsJsonObject()); }
        catch (RuntimeException expected) { rejected = true; }
        require(rejected, "non-object structure nbt was accepted");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
