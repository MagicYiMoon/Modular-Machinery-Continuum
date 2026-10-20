/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementInterfaceNumInput;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktMachineEditorAction;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.TileSmartInterface;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;

/** Real editor packets, disk/reload snapshots and the resulting smart-interface boundary behavior. */
public final class SmartInterfaceEditorRegressionChecks {
    private SmartInterfaceEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "smart_editor_" + UUID.randomUUID().toString().replace("-", "");
        String machine = id + "_machine", other = id + "_other";
        var directory = ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        var file = directory.resolve(id + ".json"); var shared = directory.resolve(id + "_shared.json");
        var machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(machine + ".json");
        var otherFile = machineFile.resolveSibling(other + ".json");
        var invalidFile = directory.resolve(id + "_invalid.json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "SmartEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        try {
            var definition = JsonParser.parseString("""
                {"parts":[{"x":1,"elements":"modularmachinery:blocksmartinterface","selector-tag":"red"},
                          {"x":2,"elements":"modularmachinery:blocksmartinterface","selector-tag":"blue"}],
                 "smart-interface-types":[{"type":"mode","default-value":0},{"type":"voltage","default-value":1}]}
                """).getAsJsonObject();
            Files.writeString(machineFile, definition.toString());
            var second = definition.deepCopy(); second.getAsJsonArray("smart-interface-types").remove(1);
            Files.writeString(otherFile, second.toString()); MachineReloadListener.reloadNow();
            var menu = new MenuMachineEditor(24, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
            var condition = SmartInterfaceEditor.create("mode", -2.5F, 3.25F);
            save(player, new PktMachineEditorSmartInterface(id, -1, condition.toString(), machine, 80, "数值条件测试"), true);
            require(read(file).get("recipeTime").getAsInt() == 80 && req(read(file)).equals(condition), "first condition saves new recipe and duration");
            var snapshot = snapshot(menu, id);
            require(snapshot.getAsJsonArray("smartInterfaceTypes").toString().equals("[\"mode\",\"voltage\"]"), "declared types reach authoritative snapshot");
            save(player, new PktMachineEditorSmartInterface(id, -1, SmartInterfaceEditor.create("voltage", 1, 1).toString(), "", 20, ""), true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().size() == 2, "append preserves the existing condition");
            save(player, new PktMachineEditorAction(PktMachineEditorAction.DELETE_REQUIREMENT, "", id, 1, "", false), true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().size() == 1, "numeric condition uses existing delete flow");
            var source = read(file); source.addProperty("authorNote", "recipe");
            req(source).addProperty("authorNote", "condition"); req(source).addProperty("selector-tag", "red");
            req(source).addProperty("triggerTime", 7); req(source).addProperty("triggerRepeatable", true);
            Files.writeString(file, source.toString()); reload(menu);
            String before = Files.readString(file);
            var detached = SmartInterfaceEditor.merge(req(source), SmartInterfaceEditor.create("voltage", -4, 10), List.of("mode", "voltage"));
            require(detached.get("interface").getAsString().equals("voltage") && Files.readString(file).equals(before), "cancel leaves source intact");
            save(player, edit(id, SmartInterfaceEditor.create("voltage", -4, 10)), true);
            source.getAsJsonArray("requirements").set(0, detached);
            require(read(file).equals(source), "only interface/min/max change; selector, triggers and author metadata survive");
            save(player, edit(id, condition), true);
            var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var synced = (RequirementInterfaceNumInput) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of())).requirements().get(0);
            require(synced.getInterfaceType().equals("mode") && synced.getMinValue() == -2.5F && synced.getMaxValue() == 3.25F
                    && synced.getSelectorTag().equals("red") && synced.getTriggerTime() == 7 && synced.isTriggerRepeatable(), "runtime wire retains range, routing and triggers");
            checkRuntime(level, origin, machine, synced);
            // Missing max is an exact match; retain its compact source form when edited to another exact match.
            source = read(file); req(source).remove("max"); Files.writeString(file, source.toString()); reload(menu);
            save(player, edit(id, SmartInterfaceEditor.create("mode", 1.5F, 1.5F)), true);
            require(!req(read(file)).has("max") && ((RequirementInterfaceNumInput) MachineReloadListener.getRecipeLoader()
                    .getRecipe(id).requirements().get(0)).getMaxValue() == 1.5F, "omitted max remains exact match");
            save(player, edit(id, condition), true);
            before = Files.readString(file);
            for (String key : List.of("min", "max")) for (String invalid : List.of("null", "\"1\"", "\"NaN\"", "\"Infinity\"", "1e100", "[]")) {
                var bad = condition.deepCopy(); bad.add(key, JsonParser.parseString(invalid)); save(player, edit(id, bad), false);
            }
            for (String type : List.of("", "Mode", "missing", " mode", "mode\n")) {
                var bad = condition.deepCopy(); bad.addProperty("interface", type); save(player, edit(id, bad), false);
            }
            for (String key : List.of("min", "interface", "io-type", "type")) {
                var bad = condition.deepCopy(); bad.remove(key); save(player, edit(id, bad), false);
            }
            var bad = condition.deepCopy(); bad.addProperty("min", 4); save(player, edit(id, bad), false);
            bad = condition.deepCopy(); bad.addProperty("io-type", "output"); save(player, edit(id, bad), false);
            for (int index : List.of(-2, 1, 512)) save(player, new PktMachineEditorSmartInterface(id, index, condition.toString(), "", 20, ""), false);
            for (String payload : List.of("{}", "[]", "{")) save(player, new PktMachineEditorSmartInterface(id, 0, payload, "", 20, ""), false);
            save(player, new PktMachineEditorFuel(id, 0, FuelEditor.create(1600, 1).toString(), "", 20, ""), false);
            save(player, new ModularmachineryNetwork.PktMachineEditorEdit(id, 0, 1, 1, ""), false);
            require(Files.readString(file).equals(before), "malformed, undeclared, inverted, output, wrong-index and wrong-type writes rejected");
            save(player, new PktMachineEditorSmartInterface(id + "_invalid", -1, condition.toString(), other + "_missing", 20, ""), false);
            require(!Files.exists(invalidFile), "invalid first save leaves no file");
            save(player, new PktMachineEditorSmartInterface(id + "_invalid", -1,
                    SmartInterfaceEditor.create("voltage", 0, 1).toString(), other, 20, ""), false);
            require(!Files.exists(invalidFile), "undeclared type creates no new recipe");
            // Disk changed without reload, so snapshot revision alone cannot protect the indexed operation.
            source = read(file); req(source).addProperty("authorNote", "external edit"); Files.writeString(file, source.toString());
            save(player, edit(id, condition), false);
            save(player, new PktMachineEditorSmartInterface(id, -1, condition.toString(), "", 20, ""), false);
            require(read(file).equals(source), "external source changes protected for replacement and append"); reload(menu);
            var changedOwner = source.deepCopy(); changedOwner.addProperty("machine", other); Files.writeString(file, changedOwner.toString());
            save(player, edit(id, SmartInterfaceEditor.create("voltage", 0, 1)), false);
            require(read(file).equals(changedOwner), "unreloaded owner change cannot bypass common-type validation");
            Files.writeString(file, source.toString()); reload(menu);
            // A shared JSON affects every listed machine; the only common declaration is mode.
            var sharedSource = read(file); var machines = new JsonArray(); machines.add(machine); machines.add(other);
            sharedSource.add("machine", machines); sharedSource.addProperty("registryName", id + "_shared"); Files.writeString(shared, sharedSource.toString()); reload(menu);
            String sharedId = id + "_shared_sub_0";
            require(snapshot(menu, sharedId).getAsJsonArray("smartInterfaceTypes").toString().equals("[\"mode\"]"), "shared types use intersection");
            save(player, edit(sharedId, SmartInterfaceEditor.create("voltage", 0, 1)), false);
            save(player, edit(sharedId, SmartInterfaceEditor.create("mode", -10, 10)), true);
            require(((RequirementInterfaceNumInput) MachineReloadListener.getRecipeLoader().getRecipe(id + "_shared_sub_1")
                    .requirements().get(0)).getMinValue() == -10, "all shared variants reload edited range");
            before = Files.readString(file);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "SmartDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(24, denied.getInventory(), menu.snapshot()); save(denied, edit(id, condition), false);
            for (boolean wrongWindow : List.of(false, true)) require(!PktMachineEditorRequest.process(player,
                    new PktMachineEditorRequest(wrongWindow ? 25 : 24, UUID.randomUUID(), wrongWindow
                            ? EditorSaveState.revision(menu.snapshot()) : "stale", edit(id, condition))).result().success(), "stale document/window rejected");
            String readonly = MachineReloadListener.getRecipeLoader().getRecipeNames().stream()
                    .filter(name -> !MachineReloadListener.getRecipeLoader().isSourceEditable(name)).findFirst().orElseThrow();
            save(player, edit(readonly, condition), false);
            require(Files.readString(file).equals(before), "permission/revision/window/generated-source rejection leaves file intact");
            Modularmachinery.LOG.info("[MMCE][smart-interface-editor-regression] PASS: real wire/first save/reload/snapshots, inclusive float boundaries/exact match/selector/batch, "
                    + "metadata/triggers/cancel, shared-type intersection, invalid/output/type/stale-source/permission/window/read-only rejection");
        } finally {
            player.containerMenu = oldMenu;
            for (var path : List.of(file, shared, machineFile, otherFile, invalidFile)) Files.deleteIfExists(path);
            MachineReloadListener.reloadNow();
        }
    }
    private static void checkRuntime(ServerLevel level, BlockPos origin, String machineId, RequirementInterfaceNumInput condition) {
        var machine = MachineReloadListener.getLoader().findByName(machineId);
        BlockPos red = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0), blue = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 2, 0, 0);
        var oldRed = level.getBlockState(red); var oldBlue = level.getBlockState(blue);
        try {
            level.setBlock(red, RegistryBlocks.smartInterface.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(blue, RegistryBlocks.smartInterface.get().defaultBlockState(), Block.UPDATE_ALL);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
            publish(level, blue, "mode", 0);
            require(!condition.canStart(context), "matching number in wrong selector group cannot start");
            publish(level, red, "other", 0); require(!condition.canStart(context), "different published type cannot start");
            for (float value : new float[]{-2.5F, 0, 3.25F}) {
                publish(level, red, "mode", value);
                require(condition.canStart(context) && hellfirepvp.modularmachinery.common.tiles.MachineTicker.consumeInputs(List.of(condition), context, 64)
                        && condition.scaled(64, 1, IOType.INPUT, false).canStart(context), "inclusive bounds survive batch 64 and modifiers: " + value);
            }
            for (float value : new float[]{Math.nextDown(-2.5F), Math.nextUp(3.25F)}) {
                publish(level, red, "mode", value); require(!condition.canStart(context), "adjacent out-of-range float rejected");
            }
        } finally { level.setBlock(red, oldRed, Block.UPDATE_ALL); level.setBlock(blue, oldBlue, Block.UPDATE_ALL); }
    }
    private static void publish(ServerLevel level, BlockPos pos, String type, float value) {
        var tag = new CompoundTag(); var data = new CompoundTag(); var list = new ListTag();
        data.putString("key", type); data.putDouble("number", value); list.add(data); tag.put("boundData", list);
        ((TileSmartInterface) level.getBlockEntity(pos)).load(tag);
    }
    private static JsonObject req(JsonObject json) { return json.getAsJsonArray("requirements").get(0).getAsJsonObject(); }
    private static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static PktMachineEditorSmartInterface edit(String id, JsonObject value) { return new PktMachineEditorSmartInterface(id, 0, value.toString(), "", 20, ""); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject();
        throw new AssertionError("Missing smart recipe snapshot");
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "authoritative reload precedes acknowledgement");
        } finally { buffer.release(); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError("Smart editor: " + message); }
}
