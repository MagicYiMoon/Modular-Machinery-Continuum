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
        try {
            var literal = net.minecraft.nbt.TagParser.parseTag("{byte:1b,short:2s,long:3L,float:4f,double:5d,bytes:[B;1b,2b],ints:[I;3,4],longs:[L;5L,6L],name:'[literal].*',nested:{x:7},list:[{slot:0b}]}");
            var part = new com.google.gson.JsonObject();
            part.addProperty("nbt", literal.toString()); part.addProperty("preview-nbt", literal.toString());
            var parsed = StructureNbt.read(part);
            require(parsed.matches(literal) && parsed.preview().equals(literal), "literal SNBT types/arrays lost");
            var extra = literal.copy(); extra.putInt("extra", 8);
            require(parsed.matches(extra), "literal compound stopped allowing extra keys");
            extra.putString("name", "some literal value");
            require(!parsed.matches(extra), "literal string was treated as regex");
            extra = literal.copy(); extra.putIntArray("ints", new int[]{3,5});
            require(!parsed.matches(extra), "literal array mismatch ignored");
            extra = literal.copy(); extra.putInt("byte", 1);
            require(!parsed.matches(extra), "literal numeric type changed");
            var copy = parsed.preview(); copy.putInt("byte", 99);
            require(parsed.preview().equals(literal), "preview literal aliases caller NBT");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) { throw new AssertionError(invalid); }
        for (String malformed : new String[]{"[]", "1", "{broken", "{x:1} trailing"}) {
            var part = new com.google.gson.JsonObject(); part.addProperty("nbt", malformed);
            rejected = false;
            try { StructureNbt.read(part); } catch (RuntimeException expected) { rejected = true; }
            require(rejected, "malformed compound SNBT accepted: " + malformed);
        }
        try {
            var part = new com.google.gson.JsonObject();
            part.addProperty("nbt", "{inventory:{Items:[],Size:6}}");
            var patternData = StructureNbt.read(part);
            require(patternData.matches(net.minecraft.nbt.TagParser.parseTag("{inventory:{Items:[{Slot:0b,id:'minecraft:iron_ingot',Count:1b}],Size:6}}")),
                    "exported empty list rejected a bus after inserting items");
            require(!patternData.matches(net.minecraft.nbt.TagParser.parseTag("{inventory:{Items:[],Size:9}}")),
                    "empty-list fix ignored the inventory size condition");
            part.addProperty("nbt", "{entries:[{x:1},{x:1}]}");
            var duplicates = StructureNbt.read(part);
            require(!duplicates.matches(net.minecraft.nbt.TagParser.parseTag("{entries:[{x:1}]}")),
                    "one list entry satisfied two literal requirements");
            require(duplicates.matches(net.minecraft.nbt.TagParser.parseTag("{entries:[{x:1},{x:2},{x:1}]}")),
                    "literal subset list rejected distinct matching entries");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException invalid) { throw new AssertionError(invalid); }
        hellfirepvp.modularmachinery.Modularmachinery.LOG.info("[MMC][structure-nbt] PASS: JSON patterns and literal SNBT, types/arrays/literal text, subset matching, preview copies and malformed data rejection");
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
