/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementCatalyst;
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

/** Actual editor requests, source persistence and both codecs for requirement-level catalyst text. */
public final class CatalystTooltipEditorRegressionChecks {
    private CatalystTooltipEditorRegressionChecks() {}
    public static void run(ServerLevel level) throws Exception {
        String id = "catalyst_tooltip_" + UUID.randomUUID().toString().replace("-", "");
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var invalidFile = file.resolveSibling(id + "_invalid.json");
        String machine = MachineReloadListener.getLoader().all().iterator().next().getRegistryName();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "CatalystTooltip")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        var menu = new MenuMachineEditor(33, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
        try {
            var group = CatalystEditor.empty(); group.getAsJsonArray("items").add(IngredientGroupEditor.candidate("minecraft:diamond"));
            group.getAsJsonArray("modifiers").add(CatalystEditor.modifier());
            var originals = List.of("literal &6 text", "§b旧颜色", "item.minecraft.diamond", "", "重复", "重复", "   ");
            setTips(group, originals);
            save(player, new PktMachineEditorIngredientGroup(id, -1, group.toString(), machine, 100, "催化剂文字"), true);
            checkSnapshots(menu, id, originals);
            require(group.getAsJsonArray("tooltips").equals(catalyst(read(file)).get("tooltips")), "first requirement saves catalyst text");

            var source = read(file); var recipeTips = array(List.of("recipe-level note")); source.add("tooltips", recipeTips);
            source.addProperty("authorNote", "recipe metadata"); catalyst(source).addProperty("authorNote", "catalyst metadata");
            catalyst(source).getAsJsonArray("items").get(0).getAsJsonObject().addProperty("authorNote", "candidate metadata");
            catalyst(source).getAsJsonArray("modifiers").get(0).getAsJsonObject().addProperty("authorNote", "modifier metadata");
            source.getAsJsonArray("requirements").add(BasicRequirementType.ITEM.create("minecraft:gold_ingot", true, 2, 1));
            Files.writeString(file, source.toString()); reload(menu);
            var unrelated = source.getAsJsonArray("requirements").get(1).deepCopy();

            // Requests with no text field must continue to preserve old source text.
            var draft = draft(menu, id); draft.remove("tooltips"); save(player, edit(id, draft), true);
            checkSnapshots(menu, id, originals);
            draft = draft(menu, id);
            var unchanged = new ArrayList<String>();
            for (var raw : originals) unchanged.add(RecipeTooltipEditor.editedLine(raw, MachineNameFormatting.editable(raw)));
            setTips(draft, unchanged); save(player, edit(id, draft), true);
            require(catalyst(read(file)).get("tooltips").equals(array(originals)), "untouched ampersands/section codes/blanks/duplicates preserve exact strings");

            String before = Files.readString(file);
            var local = new ArrayList<>(originals); Collections.swap(local, 0, 2); local.remove(4);
            local.set(1, RecipeTooltipEditor.editedLine(local.get(1), "&6新的效果&r & 普通文字")); local.add("末行");
            draft = draft(menu, id); setTips(draft, local);
            var detached = CatalystEditor.merge(catalyst(read(file)), draft);
            draft.getAsJsonArray("tooltips").set(0, new JsonPrimitive("changed after merge"));
            require(RecipeTooltipEditor.read(detached).equals(local) && Files.readString(file).equals(before), "merge owns detached text; cancellation does not write");
            draft = draft(menu, id); setTips(draft, local); draft.addProperty("chance", 0);
            draft.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("amount", 2);
            draft.getAsJsonArray("modifiers").get(0).getAsJsonObject().addProperty("multiplier", 0.25F);
            save(player, edit(id, draft), true); checkSnapshots(menu, id, local);
            var saved = read(file); var changed = catalyst(saved);
            require(saved.get("tooltips").equals(recipeTips) && saved.get("authorNote").equals(source.get("authorNote"))
                    && saved.getAsJsonArray("requirements").get(1).equals(unrelated)
                    && saved.get("recipeTime").getAsInt() == 100 && saved.get("displayName").getAsString().equals("催化剂文字"),
                    "root tips/name/duration/other requirements remain independent");
            require(changed.get("chance").getAsFloat() == 0 && changed.get("authorNote").getAsString().equals("catalyst metadata")
                    && changed.getAsJsonArray("items").get(0).getAsJsonObject().get("amount").getAsInt() == 2
                    && changed.getAsJsonArray("items").get(0).getAsJsonObject().get("authorNote").getAsString().equals("candidate metadata")
                    && changed.getAsJsonArray("modifiers").get(0).getAsJsonObject().get("multiplier").getAsFloat() == 0.25F
                    && changed.getAsJsonArray("modifiers").get(0).getAsJsonObject().get("authorNote").getAsString().equals("modifier metadata")
                    && !Files.readString(file).contains(IngredientGroupEditor.ORIGIN), "text/candidate/chance/effect saved together with origin metadata");

            for (var boundary : List.of(Collections.nCopies(64, ""), Collections.nCopies(16, "界".repeat(512)))) {
                draft = draft(menu, id); setTips(draft, boundary); save(player, edit(id, draft), true); checkSnapshots(menu, id, boundary);
            }
            before = Files.readString(file); var valid = draft(menu, id);
            for (var invalid : List.of(JsonNull.INSTANCE, new JsonPrimitive("text"), JsonParser.parseString("[1]"), JsonParser.parseString("[true]"),
                    JsonParser.parseString("[null]"), JsonParser.parseString("[{}]"), JsonParser.parseString("[[]]"), array(Collections.nCopies(65, "")),
                    array(List.of("x".repeat(513))), array(Collections.nCopies(17, "x".repeat(512))), array(List.of("bad\nline")),
                    array(List.of("bad\tline")), array(List.of("bad\rline")), array(List.of("bad\u0000line")))) {
                var bad = valid.deepCopy(); bad.add("tooltips", invalid); save(player, edit(id, bad), false);
            }
            var bad = valid.deepCopy(); bad.addProperty("io-type", "output"); save(player, edit(id, bad), false);
            bad = valid.deepCopy(); bad.getAsJsonArray("items").get(0).getAsJsonObject().addProperty(IngredientGroupEditor.ORIGIN, 999);
            save(player, edit(id, bad), false);
            require(Files.readString(file).equals(before), "invalid string types/bounds/direction/origin never write");
            bad = group.deepCopy(); bad.add("tooltips", JsonParser.parseString("[false]"));
            save(player, new PktMachineEditorIngredientGroup(id + "_invalid", -1, bad.toString(), machine, 20, ""), false);
            require(!Files.exists(invalidFile), "invalid first save creates no file");

            draft = draft(menu, id); setTips(draft, List.of()); save(player, edit(id, draft), true); checkSnapshots(menu, id, List.of());
            require(catalyst(read(file)).getAsJsonArray("tooltips").isEmpty(), "explicit empty clears source");
            var imported = read(file); catalyst(imported).remove("tooltips"); Files.writeString(file, imported.toString()); reload(menu);
            save(player, edit(id, draft(menu, id)), true); require(!catalyst(read(file)).has("tooltips"), "absent remains absent");

            var stale = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), edit(id, draft(menu, id)));
            draft = draft(menu, id); setTips(draft, List.of("updated")); save(player, edit(id, draft), true);
            require(!PktMachineEditorRequest.process(player, stale).result().success(), "stale revision rejected");
            var external = read(file); external.addProperty("authorNote", "external modification"); Files.writeString(file, external.toString());
            save(player, edit(id, draft(menu, id)), false); require(read(file).equals(external), "external full-source changes protected before reload");
            reload(menu);
            String readonly = MachineReloadListener.getRecipeLoader().getRecipeNames().stream()
                    .filter(name -> !MachineReloadListener.getRecipeLoader().isSourceEditable(name)).findFirst().orElseThrow();
            save(player, edit(readonly, group), false);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "CatalystTipDeny")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(33, denied.getInventory(), menu.snapshot()); save(denied, edit(id, draft(menu, id)), false);
            player.containerMenu = oldMenu; saveClosed(player, edit(id, group)); player.containerMenu = menu;
            require(read(file).equals(external), "permission/read-only/closed-window rejection without source changes");
            Modularmachinery.LOG.info("[MMCE][catalyst-tooltip-editor-regression] PASS: real wire/first save/disk/reload/editor+runtime snapshots, "
                    + "ordered literal/legacy/new formatting/blanks/duplicates, detached cancel, omitted vs clear, separate recipe text, "
                    + "candidate/modifier metadata and combined edits, Unicode boundaries/type/size/control/origin/direction rejection, stale/external/read-only/permission/window guards; UI requires client verification");
        } finally {
            player.containerMenu = oldMenu; Files.deleteIfExists(file); Files.deleteIfExists(invalidFile); MachineReloadListener.reloadNow();
        }
    }
    private static void checkSnapshots(MenuMachineEditor menu, String id, List<String> expected) {
        var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
        require(((RequirementCatalyst) loaded.requirements().get(0)).getTooltips().equals(expected), "runtime exact strings");
        require(((RequirementCatalyst) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(0)).getTooltips().equals(expected), "runtime codec exact strings");
        require(RecipeTooltipEditor.read(draft(menu, id)).equals(expected), "editor snapshot exact strings");
    }
    private static JsonObject draft(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return IngredientGroupEditor.draft(catalyst(recipe.getAsJsonObject()));
        throw new AssertionError("Missing catalyst tooltip snapshot");
    }
    private static JsonObject catalyst(JsonObject source) { return source.getAsJsonArray("requirements").get(0).getAsJsonObject(); }
    private static JsonArray array(List<String> lines) { var array = new JsonArray(); lines.forEach(array::add); return array; }
    private static void setTips(JsonObject source, List<String> lines) { source.add("tooltips", array(lines)); }
    private static JsonObject read(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static PktMachineEditorIngredientGroup edit(String id, JsonObject group) { return new PktMachineEditorIngredientGroup(id, 0, group.toString(), "", 20, ""); }
    private static void saveClosed(FakePlayer player, Object mutation) { save(player, mutation, false, 33, ""); }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu; save(player, mutation, expected, menu.containerId, EditorSaveState.revision(menu.snapshot()));
    }
    private static void save(FakePlayer player, Object mutation, boolean expected, int window, String revision) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(new PktMachineEditorRequest(window, UUID.randomUUID(), revision, mutation), buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && ((MenuMachineEditor) player.containerMenu).snapshot().equals(PktMachineEditorRequest.snapshot()), "reload precedes acknowledgement");
        } finally { buffer.release(); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError("Catalyst tooltip editor: " + message); }
}
