/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import java.nio.file.*;
import java.util.*;

/** Actual request/atomic save/reload/snapshots for ordered recipe-level explanation lines. */
public final class RecipeTooltipEditorRegressionChecks {
    private RecipeTooltipEditorRegressionChecks() {}
    public static void run(ServerLevel level) throws Exception {
        String id = "tooltip_editor_" + UUID.randomUUID().toString().replace("-", ""), other = id + "_other";
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var machineDir = ModDataHolder.INSTANCE.getMachineryDirectory().toPath();
        var machineFile = machineDir.resolve(id + ".json"); var otherFile = machineDir.resolve(other + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "TooltipEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        try {
            Files.writeString(machineFile, "{\"parts\":[{\"x\":1,\"elements\":\"minecraft:air\"}]}"); Files.writeString(otherFile, Files.readString(machineFile));
            var source = new JsonObject(); source.addProperty("machine", id); source.addProperty("registryName", id); source.addProperty("recipeTime", 20);
            source.addProperty("authorNote", "preserve recipe metadata"); source.addProperty("priority", -3); source.addProperty("cancelIfPerTickFails", true);
            source.addProperty("maxThreads", 2); source.addProperty("threadName", "worker"); source.addProperty("parallelized", false); source.addProperty("loadJEI", false);
            var requirements = new JsonArray(); var input = BasicRequirementType.ITEM.create("minecraft:coal", false, 1, 1);
            input.add("nbt", JsonParser.parseString("{\"quality\":\">=3\"}")); input.addProperty("authorNote", "input metadata"); requirements.add(input);
            var catalyst = CatalystEditor.empty(); catalyst.getAsJsonArray("items").add(IngredientGroupEditor.candidate("minecraft:diamond"));
            var catalystTips = new JsonArray(); catalystTips.add("independent catalyst notes"); catalyst.add("tooltips", catalystTips); requirements.add(catalyst);
            source.add("requirements", requirements); Files.writeString(file, source.toString()); MachineReloadListener.reloadNow();
            var menu = new MenuMachineEditor(32, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
            require(RecipeOptionsEditor.read(snapshot(menu, id)).tooltips().isEmpty(), "omitted tooltip list defaults empty");
            save(player, edit(id, List.of()), true); require(!read(file).has("tooltips"), "empty omitted field stays omitted");
            var originals = List.of("literal &6 remains", "§b旧格式", "item.minecraft.diamond", "未知的翻译键.example", "", "重复", "重复", "   ");
            var array = new JsonArray(); originals.forEach(array::add); var imported = source.deepCopy(); imported.add("tooltips", array);
            Files.writeString(file, imported.toString()); reload(menu);
            var options = RecipeOptionsEditor.read(snapshot(menu, id)); require(options.tooltips().equals(originals), "snapshot retains translation keys/blanks/duplicates/order");
            var unchanged = new ArrayList<String>();
            for (var raw : originals) unchanged.add(RecipeTooltipEditor.editedLine(raw, MachineNameFormatting.editable(raw)));
            save(player, edit(id, unchanged), true); require(read(file).get("tooltips").equals(array), "untouched legacy format and ampersands save byte-for-byte as strings");
            String colored = RecipeTooltipEditor.editedLine("", "&6金色&r & 普通文字"); require(colored.equals("§6金色§r & 普通文字"), "newly edited color codes convert without ordinary ampersands");
            var draft = new ArrayList<>(originals); draft.set(3, colored); Collections.swap(draft, 0, 2); draft.remove(5); draft.add("最后一行");
            var local = RecipeOptionsEditor.merge(imported, "&a说明配方", 80, new RecipeOptionsEditor.Options(-3, true, "worker", 2, false, false, draft));
            draft.set(0, "changed after snapshot");
            require(read(file).get("tooltips").equals(array) && !imported.has("displayName"), "sub-draft changes never write source before save");
            var detached = new ArrayList<>(originals); var immutable = new RecipeOptionsEditor.Options(-3, true, "worker", 2, false, false, detached);
            detached.clear(); require(immutable.tooltips().equals(originals), "options own defensive tooltip copy");
            var desired = RecipeTooltipEditor.read(local); save(player, edit(id, desired), true);
            var saved = read(file); var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            require(loaded.tooltips().equals(desired) && loaded.displayName().equals("§a说明配方") && loaded.recipeTime() == 80
                    && loaded.priority() == -3 && loaded.cancelOnPerTickFailure() && loaded.threadName().equals("worker") && loaded.maxThreads() == 2
                    && !loaded.parallelized() && !loaded.loadJEI(), "one save updates name/time/tooltips and preserves all advanced policies");
            require(saved.get("requirements").equals(requirements) && saved.get("authorNote").equals(source.get("authorNote")), "NBT, catalyst notes and metadata untouched");
            require(RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).tooltips().equals(desired)
                    && RecipeOptionsEditor.read(snapshot(menu, id)).tooltips().equals(desired), "ordered text survives runtime and editor codecs");
            save(player, edit(id, Collections.nCopies(64, "")), true); require(MachineReloadListener.getRecipeLoader().getRecipe(id).tooltips().size() == 64, "64 empty entries retain source spacing");
            var boundary = Collections.nCopies(16, "界".repeat(512)); save(player, edit(id, boundary), true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id).tooltips().equals(boundary), "per-line 512 and total 8192 Unicode boundary survives wire");
            String before = Files.readString(file);
            var invalid = new ArrayList<List<String>>(List.of(Collections.nCopies(65, ""), List.of("x".repeat(513)), Collections.nCopies(17, "x".repeat(512)),
                    List.of("bad\nline"), List.of("bad\rline"), List.of("bad\tline"), List.of("bad\u0000line")));
            invalid.add(Arrays.asList((String) null));
            for (var lines : invalid) require(!PktMachineEditorRequest.process(player, new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(),
                    EditorSaveState.revision(menu.snapshot()), edit(id, lines))).result().success(), "invalid tooltip payload rejected on server");
            require(Files.readString(file).equals(before), "invalid payloads never write source");
            for (int count : List.of(-1, 65)) {
                var buffer = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    buffer.writeUtf(id, 256); buffer.writeUtf("", 200); buffer.writeVarInt(20); buffer.writeInt(0); buffer.writeBoolean(false);
                    buffer.writeUtf("", 256); buffer.writeInt(-1); buffer.writeBoolean(true); buffer.writeBoolean(true); buffer.writeVarInt(count);
                    boolean rejected = false; try { PktMachineEditorRecipeOptions.decode(buffer); } catch (IllegalArgumentException expected) { rejected = true; }
                    require(rejected, "invalid wire count rejected before list allocation");
                } finally { buffer.release(); }
            }
            save(player, edit(id, List.of()), true); require(read(file).getAsJsonArray("tooltips").isEmpty(), "clear all leaves explicit empty list");
            var shared = read(file); var owners = new JsonArray(); owners.add(id); owners.add(other); shared.add("machine", owners);
            Files.writeString(file, shared.toString()); reload(menu); save(player, edit(id + "_sub_0", desired), true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id + "_sub_1").tooltips().equals(desired) && read(file).get("machine").equals(owners), "shared variants receive same text");
            shared = read(file); shared.addProperty("authorNote", "external edit"); Files.writeString(file, shared.toString());
            save(player, edit(id + "_sub_0", List.of("must not overwrite")), false); require(read(file).equals(shared), "full-source external changes protected");
            reload(menu); String readonly = MachineReloadListener.getRecipeLoader().getRecipeNames().stream()
                    .filter(name -> !MachineReloadListener.getRecipeLoader().isSourceEditable(name)).findFirst().orElseThrow();
            save(player, edit(readonly, List.of("denied")), false);
            Modularmachinery.LOG.info("[MMCE][recipe-tooltip-editor-regression] PASS: real wire/atomic source save/reload/editor/runtime codecs, ordered add/edit/delete/clear text, "
                    + "translation keys/Unicode/legacy/new formatting/blanks/duplicates, immutable drafts, name+time+advanced settings, NBT/catalyst notes/shared owners, "
                    + "length/count/total/control/null/wire bounds and external/read-only rejection; client preview+JEI need client verification");
        } finally {
            player.containerMenu = oldMenu;
            for (var path : List.of(file, machineFile, otherFile)) Files.deleteIfExists(path); MachineReloadListener.reloadNow();
        }
    }
    private static JsonObject read(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes")) if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject();
        throw new AssertionError("Missing tooltip editor snapshot");
    }
    private static PktMachineEditorRecipeOptions edit(String id, List<String> lines) {
        return new PktMachineEditorRecipeOptions(id, "&a说明配方", 80, -3, true, "worker", 2, false, false, lines);
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu; var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation), buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "reload precedes confirmation");
        } finally { buffer.release(); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError("Recipe tooltip editor: " + message); }
}
