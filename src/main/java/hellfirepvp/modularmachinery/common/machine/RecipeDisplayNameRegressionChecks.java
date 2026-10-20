/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeLoader;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.MachineEditorSnapshot;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorRecipeEdit;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;

/** Real save-handler, disk reload and client snapshot checks, only in the opt-in server suite. */
public final class RecipeDisplayNameRegressionChecks {
    private RecipeDisplayNameRegressionChecks() {}

    public static void run(ServerLevel level) throws Exception {
        var machines = MachineReloadListener.getLoader();
        String machine = machines.all().iterator().next().getRegistryName();
        var file = Files.createTempFile(ModDataHolder.INSTANCE.getRecipeDirectory().toPath(),
                "name-regression-", ".json");
        String id = "name_regression_" + UUID.randomUUID().toString().replace("-", "");
        var json = new JsonObject();
        json.addProperty("machine", machine);
        json.addProperty("registryName", id);
        json.addProperty("recipeTime", 20);
        json.addProperty("priority", 17);
        var requirements = new JsonArray();
        var energy = new JsonObject();
        energy.addProperty("type", "modularmachinery:energy");
        energy.addProperty("io-type", "input");
        energy.addProperty("energyPerTick", 1);
        requirements.add(energy);
        json.add("requirements", requirements);
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "RecipeNameTest")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var originalMenu = player.containerMenu;
        var apply = PktMachineEditorRecipeEdit.class.getDeclaredMethod("apply", ServerPlayer.class,
                PktMachineEditorRecipeEdit.class);
        apply.setAccessible(true);
        try {
            Files.writeString(file, json.toString(), StandardCharsets.UTF_8);
            MachineReloadListener.getRecipeLoader().load(machines);
            var original = MachineReloadListener.getRecipeLoader().getRecipe(id);
            require(original != null && original.displayNameOrId().equals(id), "old JSON must fall back to its ID");
            player.containerMenu = new MenuMachineEditor(1, player.getInventory(), MenuMachineEditor.EMPTY_SNAPSHOT);
            var request = new PktMachineEditorRecipeEdit(id, "&6&l合金冶炼&r & 零件", 80);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                PktMachineEditorRecipeEdit.encode(request, buffer);
                require(PktMachineEditorRecipeEdit.decode(buffer).equals(request), "request roundtrip");
            } finally { buffer.release(); }
            apply.invoke(null, player, request);
            JsonObject saved = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            require(saved.has("displayName") && saved.get("displayName").getAsString().equals("\u00a76\u00a7l合金冶炼\u00a7r & 零件"),
                    "save must apply formatting but preserve ordinary ampersands");
            require(saved.get("registryName").getAsString().equals(id) && saved.get("priority").getAsInt() == 17
                    && saved.get("requirements").equals(requirements)
                    && saved.get("recipeTime").getAsInt() == 80, "save must retain identity and unrelated fields");
            var reloaded = new RecipeLoader();
            reloaded.load(machines);
            var named = reloaded.getRecipe(id);
            require(named != null && named.displayName().equals(saved.get("displayName").getAsString())
                    && named.recipeTime() == 80, "disk reload must preserve the name and duration");
            var snapshot = RecipeSnapshotCodec.encode(named, Map.of());
            var client = RecipeSnapshotCodec.decode(snapshot);
            require(client.displayName().equals(named.displayName()) && client.registryName().equals(id),
                    "client snapshot must preserve display name and stable identity");
            require(named.withPriority(1).withCommands(null).withMetadata(java.util.List.of(), false, true)
                    .displayName().equals(named.displayName()), "metadata copies must keep display names");
            var editor = JsonParser.parseString(MachineEditorSnapshot.create(machines, reloaded)).getAsJsonObject();
            boolean editorFound = false;
            for (var m : editor.getAsJsonArray("machines")) for (var recipe : m.getAsJsonObject().getAsJsonArray("recipes")) {
                var entry = recipe.getAsJsonObject();
                if (id.equals(entry.get("name").getAsString())) editorFound = named.displayName().equals(entry.get("displayName").getAsString())
                        && entry.get("editable").getAsBoolean();
            }
            require(editorFound, "editor snapshot must contain the editable label");
            snapshot.remove("displayName");
            require(RecipeSnapshotCodec.decode(snapshot).displayNameOrId().equals(id), "old snapshots must still load");
            String beforeRejected = Files.readString(file, StandardCharsets.UTF_8);
            apply.invoke(null, player, new PktMachineEditorRecipeEdit(id, "invalid", 0));
            apply.invoke(null, player, new PktMachineEditorRecipeEdit(id, "x".repeat(201), 20));
            player.containerMenu = originalMenu;
            apply.invoke(null, player, request);
            require(Files.readString(file, StandardCharsets.UTF_8).equals(beforeRejected),
                    "invalid values and requests outside the editor must not write");
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "NameDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(2, denied.getInventory(), MenuMachineEditor.EMPTY_SNAPSHOT);
            apply.invoke(null, denied, request);
            require(Files.readString(file, StandardCharsets.UTF_8).equals(beforeRejected), "non-operators must not write");
            player.containerMenu = new MenuMachineEditor(1, player.getInventory(), MenuMachineEditor.EMPTY_SNAPSHOT);
            apply.invoke(null, player, new PktMachineEditorRecipeEdit(id, "", 80));
            require(!JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject().has("displayName"),
                    "clearing the name must remove the optional field");
            reloaded.load(machines);
            require(reloaded.getRecipe(id).displayNameOrId().equals(id), "cleared name must fall back after reload");
            Modularmachinery.LOG.info("[MMCE][recipe-name-regression] PASS: real save, stable ID, disk reload, "
                    + "formatting, snapshots, copies, clear/fallback, bounds and permissions");
        } finally {
            player.containerMenu = originalMenu;
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".mmce.tmp"));
            MachineReloadListener.getRecipeLoader().load(machines);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Recipe display name: " + message);
    }
}
