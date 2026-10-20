/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.modifier.ModifierReplacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Checks author JSON through sync, construction, rotation and dynamic world matching. */
public final class CoordinateArrayRegressionChecks {
    private CoordinateArrayRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) {
        JsonObject definition = JsonParser.parseString("""
                {"registryname":"coordinate_regression","parts":[
                  {"x":[2,2,3],"y":[-1,1],"z":[-2,0],"elements":"frame"},
                  {"x":2,"y":-1,"z":-2,"elements":"minecraft:diamond_block"}],
                 "modifiers":[{"x":[2,3],"y":1,"z":0,"elements":"minecraft:gold_block",
                   "modifier":{"target":"modularmachinery:item","io":"output","operation":1,"multiplier":2}}],
                 "dynamic-patterns":[{"name":"line","faces":["north","east","south","west"],
                   "minSize":2,"maxSize":2,"structure-size-offset-start":{"x":5},
                   "structure-size-offset":{"z":3},
                   "parts":[{"x":[0,1],"y":[-1,0],"elements":"minecraft:iron_block"}],
                   "parts-end":[{"x":[0,1],"elements":"minecraft:gold_block"}]}]}
                """).getAsJsonObject();
        MachineLoader loader = load(definition);
        DynamicMachine machine = loader.getMachines().values().iterator().next();
        require(loader.getProblems().isEmpty(), "valid arrays reported a load problem");
        require(machine.getPattern().size() == 8, "Cartesian product or duplicate position handling failed");
        require(machine.getPattern().getParts().get(0).equals(
                new MachinePattern.Part(2, -1, -2, "minecraft:diamond_block")), "later declaration did not replace first");
        require(machine.getPattern().getMinY() == -1 && machine.getPattern().getMaxX() == 3,
                "expanded bounds incorrect");
        require(machine.getModifierReplacements().size() == 2, "modifier coordinates not expanded");
        require(PatternCoordinates.expand(JsonParser.parseString("{\"x\":[-2,5]}").getAsJsonObject())
                .equals(List.of(new BlockPos(-2, 0, 0), new BlockPos(5, 0, 0))), "omitted axes not zero");
        require(PatternCoordinates.expand(JsonParser.parseString("{\"x\":[4]}").getAsJsonObject())
                .equals(List.of(new BlockPos(4, 0, 0))), "single value array failed");
        JsonObject originModifier = definition.getAsJsonArray("modifiers").get(0).getAsJsonObject().deepCopy();
        originModifier.add("x", JsonParser.parseString("[0,1]"));
        originModifier.addProperty("y", 0);
        require(ModifierReplacement.deserializeAll(originModifier).size() == 1, "modifier overwrote controller position");

        DynamicPattern dynamic = machine.getDynamicPatterns().get("line");
        require(dynamic.getPattern().size() == 4 && dynamic.getPatternEnd().size() == 2,
                "dynamic template/cap coordinates not expanded");
        MachinePattern effective = MachineFormation.effectivePattern(machine, Map.of("line", 2));
        require(effective.size() == 18 && effective.getMaxZ() == 6, "repeated/cap placement incorrect");
        MachineLoader client = MachineLoader.fromSnapshot(loader.snapshot());
        DynamicMachine copied = client.getMachines().values().iterator().next();
        require(client.snapshot().equals(loader.snapshot())
                        && copied.getPattern().getParts().equals(machine.getPattern().getParts())
                        && MachineFormation.effectivePattern(copied, Map.of("line", 2)).getParts().equals(effective.getParts())
                        && copied.getModifierReplacements().size() == 2, "snapshot lost array semantics");

        for (Direction facing : Direction.Plane.HORIZONTAL) {
            Map<BlockPos, BlockState> before = new LinkedHashMap<>();
            for (MachinePattern.Part part : effective.getParts()) {
                BlockPos pos = StructureMatcher.toWorldOffset(origin, facing, part.x(), part.y(), part.z());
                before.put(pos, level.getBlockState(pos));
            }
            // The dynamic walker probes the first absent section beyond the declared line as well.
            for (MachinePattern.Part part : dynamic.getPattern().getParts()) {
                BlockPos pos = StructureMatcher.toWorldOffset(origin, facing, part.x() + 5, part.y(), 6);
                before.putIfAbsent(pos, level.getBlockState(pos));
            }
            try {
                before.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL));
                require(loader.fillMissing(level, origin, facing, machine) == 8, "builder missed expanded positions: " + facing);
                require(loader.fillMissing(level, origin, facing, machine) == 0, "builder duplicated placements");
                require(matches(level, origin, facing, machine.getPattern(), loader, machine), "built array cannot match");
                for (ModifierReplacement replacement : machine.getModifierReplacements()) {
                    BlockPos pos = StructureMatcher.toWorldOffset(origin, facing,
                            replacement.getX(), replacement.getY(), replacement.getZ());
                    level.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                    require(replacement.matches(level, origin, facing), "expanded modifier not active");
                }
                require(loader.fillMissing(level, origin, facing, machine) == 0
                        && matches(level, origin, facing, machine.getPattern(), loader, machine), "builder replaced valid array modifiers");
                for (MachinePattern.Part part : dynamic.instantiate(2).getParts()) {
                    BlockPos pos = StructureMatcher.toWorldOffset(origin, facing, part.x(), part.y(), part.z());
                    level.setBlock(pos, BlockStateShim.resolve(part.elements()), Block.UPDATE_ALL);
                }
                require(dynamic.matchSize(level, origin, facing, loader.getVariables(), loader.getResolver()) == 2
                        && matches(level, origin, facing, effective, loader, machine), "dynamic array world match failed: " + facing);
                BlockPos cap = StructureMatcher.toWorldOffset(origin, facing, 6, 0, 6);
                level.removeBlock(cap, false);
                require(dynamic.matchSize(level, origin, facing, loader.getVariables(), loader.getResolver()) == 0,
                        "missing expanded cap accepted");
            } finally {
                before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            }
        }

        for (String invalid : List.of("[]", "[1,\"2\"]", "[1,null]", "[[1]]", "null", "true", "\"1\"",
                "1.5", "2147483648", "-2147483649")) {
            JsonObject malformed = definition.deepCopy();
            malformed.getAsJsonArray("parts").get(0).getAsJsonObject().add("x", JsonParser.parseString(invalid));
            requireRejected(malformed, "invalid static coordinate accepted: " + invalid);
        }
        for (String location : List.of("parts", "parts-end")) {
            JsonObject malformed = definition.deepCopy();
            malformed.getAsJsonArray("dynamic-patterns").get(0).getAsJsonObject()
                    .getAsJsonArray(location).get(0).getAsJsonObject().add("y", JsonParser.parseString("[]"));
            requireRejected(malformed, "invalid dynamic " + location + " accepted");
        }
        JsonObject badModifier = definition.deepCopy();
        badModifier.getAsJsonArray("modifiers").get(0).getAsJsonObject().add("x", JsonParser.parseString("[false]"));
        requireRejected(badModifier, "invalid modifier coordinates accepted");
        JsonObject large = new JsonObject();
        com.google.gson.JsonArray axis = new com.google.gson.JsonArray();
        for (int i = 0; i < 257; i++) axis.add(i);
        large.add("x", axis); large.add("y", axis.deepCopy());
        boolean rejected = false;
        try { PatternCoordinates.expand(large); } catch (com.google.gson.JsonParseException expected) { rejected = true; }
        require(rejected, "oversized product not bounded before allocation");
    }

    private static MachineLoader load(JsonObject definition) {
        JsonObject snapshot = new JsonObject(), variables = new JsonObject(), machines = new JsonObject();
        variables.add("frame", JsonParser.parseString("[\"minecraft:stone\",\"minecraft:iron_block\"]"));
        machines.add("coordinate_regression", definition);
        snapshot.add("variables", variables); snapshot.add("machines", machines);
        return MachineLoader.fromSnapshot(snapshot);
    }

    private static boolean matches(ServerLevel level, BlockPos origin, Direction facing, MachinePattern pattern,
                                   MachineLoader loader, DynamicMachine machine) {
        return new StructureMatcher().match(level, origin, facing, pattern, loader.getVariables(),
                loader.getResolver(), machine.getModifierReplacements()).matched();
    }

    private static void requireRejected(JsonObject malformed, String message) {
        boolean rejected = false;
        try { load(malformed); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
