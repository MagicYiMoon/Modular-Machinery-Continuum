/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.Files;
import java.util.*;

/** Real selector editor source writes followed by deliberately wrong-group inventory checks. */
public final class SelectorEditorRegressionChecks {
    private SelectorEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "selector_editor_" + UUID.randomUUID().toString().replace("-", "");
        String machine = id + "_machine", otherMachine = id + "_other";
        var directory = ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        var file = directory.resolve(id + ".json"); var sharedFile = directory.resolve(id + "_shared.json");
        var machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(machine + ".json");
        var otherFile = machineFile.resolveSibling(otherMachine + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "SelectorEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        try {
            var definition = JsonParser.parseString("""
                {"parts":[{"x":1,"elements":"minecraft:air","selector-tag":"red"},
                  {"x":2,"elements":"minecraft:air","selector-tag":"blue"},
                  {"x":3,"elements":"minecraft:air","selector-tag":"red"},
                  {"x":4,"elements":"minecraft:air","selector-tag":"blue"}],
                 "dynamic-patterns":[{"name":"extension","faces":["north"],"minSize":0,"maxSize":2,
                  "parts":[{"x":6,"elements":"minecraft:air","selector-tag":"dynamic"}],
                  "parts-end":[{"x":7,"elements":"minecraft:air","selector-tag":"cap"}]}]}
                """).getAsJsonObject();
            Files.writeString(machineFile, definition.toString());
            var otherDefinition = JsonParser.parseString("{\"parts\":[{\"x\":1,\"elements\":\"minecraft:air\",\"selector-tag\":\"red\"}]}").getAsJsonObject();
            Files.writeString(otherFile, otherDefinition.toString());
            var source = new JsonObject(); source.addProperty("machine", machine); source.addProperty("registryName", id);
            source.addProperty("recipeTime", 20); source.addProperty("authorNote", "recipe metadata");
            var requirements = new JsonArray();
            var input = BasicRequirementType.ITEM.create("minecraft:coal", false, 2, 1);
            input.addProperty("authorNote", "input metadata");
            input.addProperty("triggerTime", 4); input.addProperty("triggerRepeatable", true);
            input.add("nbt", JsonParser.parseString("{\"quality\":\">=3\"}"));
            input.add("nbt-display", JsonParser.parseString("{\"quality\":3}")); requirements.add(input);
            requirements.add(BasicRequirementType.ITEM.create("minecraft:diamond", true, 1, 1)); source.add("requirements", requirements);
            Files.writeString(file, source.toString()); MachineReloadListener.reloadNow();
            require(MachineReloadListener.getLoader().findByName(machine) != null, "fixture machine loaded");
            var menu = new MenuMachineEditor(23, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
            var snapshot = snapshot(menu, id);
            var options = snapshot.getAsJsonArray("selectorTags").asList().stream().map(JsonElement::getAsString).toList();
            require(options.equals(List.of("blue", "cap", "dynamic", "red")), "stable deduplicated groups include dynamic sections and caps");
            require(snapshot.getAsJsonArray("requirements").get(0).getAsJsonObject().get("editableSelector").getAsBoolean(),
                    "advanced NBT source permits isolated selector edits without flattening contents");
            require(SelectorBindingEditor.options(MachineReloadListener.getLoader().findByName(machine)).equals(options), "server and snapshot choices agree");
            String before = Files.readString(file);
            var detached = SelectorBindingEditor.merge(input, "red", options);
            require(detached.get("selector-tag").getAsString().equals("red") && !input.has("selector-tag")
                    && Files.readString(file).equals(before), "cancelled selector draft does not mutate document or source");
            save(player, new PktMachineEditorSelector(id, 0, "red"), true);
            var expected = source.deepCopy(); expected.getAsJsonArray("requirements").get(0).getAsJsonObject().addProperty("selector-tag", "red");
            require(read(file).equals(expected), "selector edit changes only its single field, preserving NBT, triggers, ID and metadata");
            save(player, new PktMachineEditorSelector(id, 1, "blue"), true);
            checkRuntime(level, origin, id, machine);
            var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var synced = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of()));
            require(synced.requirements().get(0).getSelectorTag().equals("red") && synced.requirements().get(1).getSelectorTag().equals("blue")
                    && ((RequirementItem) synced.requirements().get(0)).getNbt().pattern() != null, "network snapshots preserve routing and NBT");
            before = Files.readString(file);
            for (String invalid : List.of("Red", "missing", " red", "red ", "mode", "red\n", "x".repeat(257))) {
                var packet = new PktMachineEditorSelector(id, 0, invalid);
                if (invalid.length() > 256) require(!PktMachineEditorRequest.process(player,
                        new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), packet)).result().success(), "oversized tag rejected server-side");
                else save(player, packet, false);
            }
            for (int invalid : List.of(-1, 2, 512)) save(player, new PktMachineEditorSelector(id, invalid, "red"), false);
            require(Files.readString(file).equals(before), "invalid tags and indices never write source");
            // Unknown legacy selector can stay untouched or be cleared, without offering arbitrary new text.
            var legacy = read(file); legacy.getAsJsonArray("requirements").get(0).getAsJsonObject().addProperty("selector-tag", "legacy");
            Files.writeString(file, legacy.toString()); reload(menu);
            save(player, new PktMachineEditorSelector(id, 0, "legacy"), true);
            save(player, new PktMachineEditorSelector(id, 0, ""), true);
            require(!read(file).getAsJsonArray("requirements").get(0).getAsJsonObject().has("selector-tag")
                    && MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0).getSelectorTag() == null,
                    "unrestricted removes field and restores root component routing");
            var changedOutside = read(file); changedOutside.getAsJsonArray("requirements").get(0).getAsJsonObject().addProperty("amount", 3);
            Files.writeString(file, changedOutside.toString());
            save(player, new PktMachineEditorSelector(id, 0, "red"), false);
            require(read(file).equals(changedOutside), "unreloaded source changes cannot target a stale loaded requirement");
            reload(menu); save(player, new PktMachineEditorSelector(id, 0, "red"), true);
            // A single source file may supply several machines; restrict new selections to their common groups.
            var shared = source.deepCopy(); var machines = new JsonArray(); machines.add(machine); machines.add(otherMachine);
            shared.add("machine", machines); shared.addProperty("registryName", id + "_shared"); Files.writeString(sharedFile, shared.toString()); reload(menu);
            String sharedId = id + "_shared_sub_0";
            require(SelectorBindingEditor.options(sharedId, MachineReloadListener.getLoader(), MachineReloadListener.getRecipeLoader()).equals(List.of("red")),
                    "shared recipe file offers only groups present in all target machines");
            save(player, new PktMachineEditorSelector(sharedId, 0, "blue"), false);
            save(player, new PktMachineEditorSelector(sharedId, 0, "red"), true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id + "_shared_sub_1").requirements().get(0).getSelectorTag().equals("red"),
                    "shared file reload consistently updates all machine variants");
            before = Files.readString(file);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "SelectorDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(23, denied.getInventory(), menu.snapshot());
            save(denied, new PktMachineEditorSelector(id, 0, "blue"), false);
            for (boolean wrongWindow : List.of(false, true)) {
                var request = new PktMachineEditorRequest(wrongWindow ? menu.containerId + 1 : menu.containerId, UUID.randomUUID(),
                        wrongWindow ? EditorSaveState.revision(menu.snapshot()) : "stale", new PktMachineEditorSelector(id, 0, "blue"));
                require(!PktMachineEditorRequest.process(player, request).result().success(), "stale revision/wrong window rejected");
            }
            String readonly = MachineReloadListener.getRecipeLoader().getRecipeNames().stream()
                    .filter(name -> !MachineReloadListener.getRecipeLoader().isSourceEditable(name)).findFirst().orElseThrow();
            save(player, new PktMachineEditorSelector(readonly, 0, ""), false);
            require(Files.readString(file).equals(before), "denied/stale/read-only edits leave source intact");
            Modularmachinery.LOG.info("[MMCE][selector-editor-regression] PASS: authenticated wire/source/reload/snapshot, exact red input/blue output/NBT routing, "
                    + "dynamic/cap options/shared-source intersection, isolated-field preservation/cancel/clear/legacy, stale disk/index/tag/permission/window/read-only rejection");
        } finally {
            player.containerMenu = oldMenu; Files.deleteIfExists(file); Files.deleteIfExists(sharedFile);
            Files.deleteIfExists(machineFile); Files.deleteIfExists(otherFile); MachineReloadListener.reloadNow();
        }
    }
    private static void checkRuntime(ServerLevel level, BlockPos origin, String id, String machineId) {
        var machine = MachineReloadListener.getLoader().findByName(machineId);
        var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
        var saved = new net.minecraft.world.level.block.state.BlockState[4]; var positions = new BlockPos[4];
        try {
            for (int i = 0; i < 4; i++) {
                positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i + 1, 0, 0); saved[i] = level.getBlockState(positions[i]);
                level.setBlock(positions[i], (i < 2 ? RegistryBlocks.itemInputBus : RegistryBlocks.itemOutputBus).get().defaultBlockState(), Block.UPDATE_ALL);
            }
            var red = bus(level, positions[0]); var blue = bus(level, positions[1]);
            var redOut = bus(level, positions[2]); var blueOut = bus(level, positions[3]);
            for (var inv : List.of(red, blue, redOut, blueOut)) for (int i = 0; i < inv.getSlots(); i++) inv.setStackInSlot(i, ItemStack.EMPTY);
            var coal = new ItemStack(Items.COAL, 8); coal.getOrCreateTag().putInt("quality", 3); blue.setStackInSlot(0, coal);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
            var requirement = recipe.requirements().get(0);
            require(!requirement.canStart(context), "wrong group cannot supply a saved tagged input even with matching NBT");
            var wrongNbt = new ItemStack(Items.COAL, 8); wrongNbt.getOrCreateTag().putInt("quality", 2); red.setStackInSlot(0, wrongNbt);
            require(!requirement.canStart(context), "selector edit retains NBT filtering");
            red.setStackInSlot(0, coal.copy());
            require(requirement.canStart(context) && MachineTicker.consumeInputs(recipe.requirements(), context, 2)
                    && red.getStackInSlot(0).getCount() == 4 && blue.getStackInSlot(0).getCount() == 8, "only selected input is spent at batch two");
            MachineTicker.emitOutputs(recipe, recipe.requirements(), context, 2);
            require(blueOut.getStackInSlot(0).is(Items.DIAMOND)
                    && blueOut.getStackInSlot(0).getCount() == 2 && redOut.getStackInSlot(0).isEmpty(), "saved output selector routes to blue only");
        } finally { for (int i = 0; i < 4; i++) if (saved[i] != null) level.setBlock(positions[i], saved[i], Block.UPDATE_ALL); }
    }
    private static IItemHandlerModifiable bus(ServerLevel level, BlockPos pos) { return (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos)).getInventory(); }
    private static JsonObject read(java.nio.file.Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (recipe.getAsJsonObject().get("name").getAsString().equals(id)) return recipe.getAsJsonObject();
        throw new AssertionError("Selector fixture absent in snapshot");
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer); var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "authoritative reload/sync precede confirmation");
        } finally { buffer.release(); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError("Selector editor: " + message); }
}
