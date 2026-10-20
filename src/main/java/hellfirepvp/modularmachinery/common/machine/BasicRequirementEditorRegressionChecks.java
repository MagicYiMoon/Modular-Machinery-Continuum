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
import hellfirepvp.modularmachinery.common.editor.BasicRequirementType;
import hellfirepvp.modularmachinery.common.editor.MachineEditorSnapshot;
import hellfirepvp.modularmachinery.common.editor.RequirementResourceSelection;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktMachineEditorAction;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktMachineEditorEdit;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorRequirementAdd;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/** Exercises the real editor save handlers and file loader, only in the opt-in server suite. */
public final class BasicRequirementEditorRegressionChecks {
    private BasicRequirementEditorRegressionChecks() {}

    public static void run(ServerLevel level) throws Exception {
        var machines = MachineReloadListener.getLoader();
        String machine = machines.all().iterator().next().getRegistryName();
        String id = "requirements_regression_" + UUID.randomUUID().toString().replace("-", "");
        Path root = ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        Path file = root.resolve(id + ".json");
        Path collision = root.resolve(id + "_collision.json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "RequirementsTest")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var originalMenu = player.containerMenu;
        player.containerMenu = new MenuMachineEditor(1, player.getInventory(), MenuMachineEditor.EMPTY_SNAPSHOT);
        Method add = handler(PktMachineEditorRequirementAdd.class);
        Method edit = handler(PktMachineEditorEdit.class);
        Method action = handler(PktMachineEditorAction.class);
        try {
            // No empty recipe is ever written; the first confirmed requirement creates a valid recipe.
            var first = new PktMachineEditorRequirementAdd(id, "item", "minecraft:iron_ingot", false,
                    4, 1F, machine, 73, "&6测试配方");
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                PktMachineEditorRequirementAdd.encode(first, buffer);
                require(PktMachineEditorRequirementAdd.decode(buffer).equals(first), "request roundtrip");
            } finally { buffer.release(); }
            add.invoke(null, player, first);
            require(Files.isRegularFile(file), "first confirmed requirement creates file");
            JsonObject saved = read(file);
            require(saved.get("registryName").getAsString().equals(id)
                    && saved.get("machine").getAsString().equals(machine)
                    && saved.get("recipeTime").getAsInt() == 73
                    && saved.get("displayName").getAsString().equals("\u00a76测试配方"), "draft name, duration and identity persist");
            JsonArray expected = new JsonArray();
            expected.add(BasicRequirementType.ITEM.create("minecraft:iron_ingot", false, 4, 1));
            for (boolean output : new boolean[]{false, true}) {
                for (var type : BasicRequirementType.values()) {
                    if (!output && type == BasicRequirementType.ITEM) continue;
                    String resource = type == BasicRequirementType.ITEM ? "minecraft:gold_ingot"
                            : type == BasicRequirementType.FLUID ? "minecraft:water" : "";
                    int amount = type == BasicRequirementType.ITEM ? 3 : type == BasicRequirementType.FLUID ? 1250 : 96;
                    float chance = type == BasicRequirementType.ENERGY ? 1 : 0.25F;
                    add.invoke(null, player, new PktMachineEditorRequirementAdd(id, type.key, resource, output,
                            amount, chance, "", 20, ""));
                    expected.add(type.create(resource, output, amount, chance));
                }
            }
            saved = read(file);
            require(saved.getAsJsonArray("requirements").equals(expected), "all three types in both directions saved exactly");
            require(saved.get("recipeTime").getAsInt() == 73
                    && saved.get("displayName").getAsString().equals("\u00a76测试配方"), "append retains recipe properties");
            var reloaded = new RecipeLoader();
            reloaded.load(machines);
            var recipe = reloaded.getRecipe(id);
            require(recipe != null && recipe.requirements().size() == 6 && reloaded.isSourceEditable(id),
                    "new recipe loads with all six requirements and remains editable");
            var decoded = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, java.util.Map.of()));
            require(decoded.requirements().size() == 6 && decoded.recipeTime() == 73, "client recipe snapshot roundtrip");
            JsonObject editor = JsonParser.parseString(MachineEditorSnapshot.create(machines, reloaded)).getAsJsonObject();
            boolean found = false;
            for (var m : editor.getAsJsonArray("machines")) for (var r : m.getAsJsonObject().getAsJsonArray("recipes")) {
                var entry = r.getAsJsonObject();
                if (id.equals(entry.get("name").getAsString())) {
                    found = entry.get("editable").getAsBoolean() && entry.getAsJsonArray("requirements").size() == 6;
                    for (int i = 0; i < expected.size(); i++) {
                        var actual = entry.getAsJsonArray("requirements").get(i).getAsJsonObject();
                        for (var property : expected.get(i).getAsJsonObject().entrySet())
                            require(property.getValue().equals(actual.get(property.getKey())), "editor retains " + property.getKey());
                    }
                }
            }
            require(found, "reopening editor retains requirements");

            // The old property editor and delete button must also work for newly added types.
            edit.invoke(null, player, new PktMachineEditorEdit(id, 1, 2500, 0.5F, ""));
            edit.invoke(null, player, new PktMachineEditorEdit(id, 2, 512, 1F, ""));
            saved = read(file);
            require(saved.getAsJsonArray("requirements").get(1).getAsJsonObject().get("amount").getAsInt() == 2500
                    && saved.getAsJsonArray("requirements").get(1).getAsJsonObject().get("chance").getAsFloat() == 0.5F
                    && saved.getAsJsonArray("requirements").get(2).getAsJsonObject().get("energyPerTick").getAsInt() == 512,
                    "existing edit handler updates mB, probability and FE/t");
            checkReselection(file, id, player, edit, machines);
            String before = Files.readString(file, StandardCharsets.UTF_8);
            String[][] invalidResources = {{"item", "minecraft:air"}, {"item", "missing:unknown"},
                    {"fluid", "minecraft:empty"}, {"fluid", "missing:unknown"}, {"fluid", "INVALID ID"}, {"unknown", ""}};
            for (var invalid : invalidResources) add.invoke(null, player,
                    new PktMachineEditorRequirementAdd(id, invalid[0], invalid[1], false, 1, 1, "", 20, ""));
            for (int invalid : new int[]{0, -1, Integer.MAX_VALUE}) add.invoke(null, player,
                    new PktMachineEditorRequirementAdd(id, "energy", "", false, invalid, 1, "", 20, ""));
            for (float invalid : new float[]{-1, 1.1F, Float.NaN, Float.POSITIVE_INFINITY}) add.invoke(null, player,
                    new PktMachineEditorRequirementAdd(id, "fluid", "minecraft:water", false, 1000, invalid, "", 20, ""));
            add.invoke(null, player, new PktMachineEditorRequirementAdd(id, "item", "minecraft:stone", false, 65, 1, "", 20, ""));
            edit.invoke(null, player, new PktMachineEditorEdit(id, 0, 65, 1, ""));
            add.invoke(null, player, first); // Existing ID must never be replaced.
            player.containerMenu = originalMenu;
            add.invoke(null, player, new PktMachineEditorRequirementAdd(id, "energy", "", false, 100, 1, "", 20, ""));
            player.containerMenu = new MenuMachineEditor(1, player.getInventory(), MenuMachineEditor.EMPTY_SNAPSHOT);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ReqDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(2, denied.getInventory(), MenuMachineEditor.EMPTY_SNAPSHOT);
            add.invoke(null, denied, new PktMachineEditorRequirementAdd(id, "energy", "", false, 100, 1, "", 20, ""));
            require(before.equals(Files.readString(file, StandardCharsets.UTF_8)), "invalid values, collisions and denied requests must not write");

            String marker = "author file must not be replaced";
            Files.writeString(collision, marker, StandardCharsets.UTF_8);
            add.invoke(null, player, new PktMachineEditorRequirementAdd(id + "_collision", "energy", "", false,
                    1, 1, machine, 20, ""));
            require(Files.readString(collision, StandardCharsets.UTF_8).equals(marker), "unloaded existing file must not be overwritten");
            Files.delete(collision);
            add.invoke(null, player, new PktMachineEditorRequirementAdd(id + "_collision", "energy", "", false,
                    1, 1, "missing_machine", 20, ""));
            add.invoke(null, player, new PktMachineEditorRequirementAdd(id + "_collision", "energy", "", false,
                    1, 1, machine, 0, ""));
            add.invoke(null, player, new PktMachineEditorRequirementAdd(id + "_collision", "energy", "", false,
                    1, 1, machine, 20, "x".repeat(201)));
            require(!Files.exists(collision), "invalid new recipe properties must not create a file");
            var readonly = reloaded.getByMachine().values().stream().flatMap(java.util.Collection::stream)
                    .filter(r -> !reloaded.isSourceEditable(r.registryName())).findFirst();
            require(readonly.isPresent(), "suite includes generated readonly recipes");
            add.invoke(null, player, new PktMachineEditorRequirementAdd(readonly.orElseThrow().registryName(),
                    "energy", "", false, 1, 1, "", 20, ""));
            require(before.equals(Files.readString(file, StandardCharsets.UTF_8)), "readonly request leaves author file intact");
            for (int i = 5; i > 0; i--) action.invoke(null, player,
                    new PktMachineEditorAction(PktMachineEditorAction.DELETE_REQUIREMENT, "", id, i, "", false));
            before = Files.readString(file, StandardCharsets.UTF_8);
            action.invoke(null, player, new PktMachineEditorAction(PktMachineEditorAction.DELETE_REQUIREMENT, "", id, 0, "", false));
            require(before.equals(Files.readString(file, StandardCharsets.UTF_8))
                    && read(file).getAsJsonArray("requirements").size() == 1, "last requirement cannot leave an unloadable empty file");
            reloaded.load(machines);
            require(reloaded.getRecipe(id) != null && reloaded.getRecipe(id).requirements().size() == 1,
                    "edited and deleted requirements survive disk reload");
            Modularmachinery.LOG.info("[MMCE][basic-requirement-editor-regression] PASS: three types, both directions, "
                    + "first-save creation, wire/disk/editor snapshots, edits/deletes, resource reselection/draft cancellation, "
                    + "metadata retention, bounds, readonly, collisions and permissions");
        } finally {
            player.containerMenu = originalMenu;
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".mmce.tmp"));
            Files.deleteIfExists(collision);
            MachineReloadListener.getRecipeLoader().load(machines);
        }
    }

    private static void checkReselection(Path file, String id, ServerPlayer player, Method edit,
                                         MachineLoader machines) throws Exception {
        JsonObject source = read(file);
        source.addProperty("priority", 17);
        for (var entry : source.getAsJsonArray("requirements")) {
            entry.getAsJsonObject().addProperty("parallelizeUnaffected", true);
            entry.getAsJsonObject().addProperty("authorNote", "keep this field");
        }
        Files.writeString(file, source.toString(), StandardCharsets.UTF_8);
        int[] indices = {0, 1, 3, 4};
        String[] replacements = {"minecraft:copper_ingot", "minecraft:lava", "minecraft:diamond", "minecraft:lava"};
        for (int i = 0; i < indices.length; i++) {
            int index = indices[i];
            JsonObject before = read(file);
            JsonObject original = before.getAsJsonArray("requirements").get(index).getAsJsonObject();
            JsonObject untouched = original.deepCopy();
            String key = RequirementResourceSelection.key(original);
            var draft = RequirementResourceSelection.replace(original, replacements[i]);
            require(original.equals(untouched) && !original.get(key).equals(draft.get(key)),
                    "picker edits a detached draft; cancellation leaves original unchanged");
            require(read(file).equals(before), "choosing a resource alone must not write a file");
            int amount = original.get("amount").getAsInt();
            float chance = original.get("chance").getAsFloat();
            var request = new PktMachineEditorEdit(id, index, amount, chance, replacements[i]);
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            PktMachineEditorEdit decoded;
            try {
                PktMachineEditorEdit.encode(request, buffer);
                decoded = PktMachineEditorEdit.decode(buffer);
            } finally { buffer.release(); }
            edit.invoke(null, player, decoded);
            var expected = before.deepCopy();
            expected.getAsJsonArray("requirements").get(index).getAsJsonObject().addProperty(key, replacements[i]);
            require(read(file).equals(expected), "replacement changes only the resource, preserving IO, quantity, chance and metadata");
        }
        var reloaded = new RecipeLoader();
        reloaded.load(machines);
        var snapshot = JsonParser.parseString(MachineEditorSnapshot.create(machines, reloaded)).getAsJsonObject();
        boolean found = false;
        for (var machine : snapshot.getAsJsonArray("machines")) for (var entry : machine.getAsJsonObject().getAsJsonArray("recipes")) {
            var recipe = entry.getAsJsonObject();
            if (!id.equals(recipe.get("name").getAsString())) continue;
            found = true;
            for (int i = 0; i < indices.length; i++) {
                var requirement = recipe.getAsJsonArray("requirements").get(indices[i]).getAsJsonObject();
                require(requirement.get(RequirementResourceSelection.key(requirement)).getAsString().equals(replacements[i]),
                        "reopening editor after disk reload retains each replacement");
            }
        }
        require(found, "reselected recipe remains loadable");
        String beforeRejected = Files.readString(file, StandardCharsets.UTF_8);
        for (String invalid : new String[]{"minecraft:air", "missing:item", "minecraft:water", "INVALID ID"})
            edit.invoke(null, player, new PktMachineEditorEdit(id, 0, 4, 1, invalid));
        for (String invalid : new String[]{"minecraft:empty", "missing:fluid", "minecraft:iron_ingot", "INVALID ID"})
            edit.invoke(null, player, new PktMachineEditorEdit(id, 1, 2500, 0.5F, invalid));
        edit.invoke(null, player, new PktMachineEditorEdit(id, 2, 512, 1, "minecraft:water"));
        require(Files.readString(file, StandardCharsets.UTF_8).equals(beforeRejected),
                "invalid/cross-type replacements and energy resource requests must not write");
        for (String field : new String[]{"legacy", "nbt", "nbt-display"}) {
            JsonObject advanced = JsonParser.parseString(beforeRejected).getAsJsonObject();
            var requirement = advanced.getAsJsonArray("requirements").get(0).getAsJsonObject();
            if (field.equals("legacy")) requirement.addProperty("item", "ingotIron");
            else requirement.add(field, JsonParser.parseString("{\"quality\":\"3\"}"));
            require(!RequirementResourceSelection.canReplace(requirement), "advanced selector is not replaceable: " + field);
            Files.writeString(file, advanced.toString(), StandardCharsets.UTF_8);
            edit.invoke(null, player, new PktMachineEditorEdit(id, 0, 4, 1, "minecraft:gold_ingot"));
            require(read(file).equals(advanced), "server must preserve advanced selector: " + field);
        }
        Files.writeString(file, beforeRejected, StandardCharsets.UTF_8);
        MachineReloadListener.getRecipeLoader().load(machines);
        Modularmachinery.LOG.info("[MMCE][requirement-reselection-regression] PASS: item/fluid input/output, draft cancellation, "
                + "request codec, metadata preservation, disk/editor reload and invalid/advanced rejection");
    }

    private static Method handler(Class<?> packet) throws Exception {
        Method method = packet.getDeclaredMethod("apply", ServerPlayer.class, packet);
        method.setAccessible(true);
        return method;
    }

    private static JsonObject read(Path file) throws Exception {
        return JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Basic requirement editor: " + message);
    }
}
