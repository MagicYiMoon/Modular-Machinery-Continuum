/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Real authenticated saves, original-format metadata merges, and actual input consumption after reload. */
public final class IngredientGroupEditorRegressionChecks {
    private IngredientGroupEditorRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "group_editor_" + UUID.randomUUID().toString().replace("-", "");
        var directory = ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        var file = directory.resolve(id + ".json");
        var rejectedFile = directory.resolve(id + "_invalid.json");
        String machine = MachineReloadListener.getLoader().all().iterator().next().getRegistryName();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GroupEditorTest")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var originalMenu = player.containerMenu;
        var menu = new MenuMachineEditor(18, player.getInventory(), PktMachineEditorRequest.snapshot());
        player.containerMenu = menu;
        try {
            JsonObject group = IngredientGroupEditor.empty();
            add(group, "minecraft:iron_ingot", 2);
            add(group, "minecraft:gold_ingot", 3);
            save(player, new PktMachineEditorIngredientGroup(id, -1, group.toString(), machine, 47, "&6候选组"), true);
            var source = read(file);
            require(source.get("recipeTime").getAsInt() == 47 && source.get("displayName").getAsString().equals("\u00a76候选组"),
                    "group may be the first requirement of a local recipe draft");
            require(!Files.readString(file).contains(IngredientGroupEditor.ORIGIN), "editor origin markers never enter source files");
            var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var runtime = (RequirementIngredientArray) loaded.requirements().get(0);
            checkConsumption(level, origin, runtime);
            var synced = (RequirementIngredientArray) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(0);
            require(synced.getIngredients().get(0).getAmount() == 2 && synced.getIngredients().get(1).getAmount() == 3,
                    "runtime snapshots retain individual candidate amounts");
            require(IngredientGroupEditor.canEdit(snapshotGroup(menu.snapshot(), id)), "normalized editor snapshots remain editable");

            source.addProperty("priority", 19);
            source.addProperty("authorNote", "recipe metadata");
            var sourceGroup = source.getAsJsonArray("requirements").get(0).getAsJsonObject();
            sourceGroup.addProperty("parallelizeUnaffected", true);
            sourceGroup.addProperty("authorNote", "group metadata");
            sourceGroup.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("authorNote", "iron metadata");
            sourceGroup.getAsJsonArray("items").get(1).getAsJsonObject().addProperty("authorNote", "gold metadata");
            Files.writeString(file, source.toString());
            MachineReloadListener.reloadNow();
            menu.setSnapshot(PktMachineEditorRequest.snapshot());
            String before = Files.readString(file);
            var draft = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            var first = draft.getAsJsonArray("items").remove(0);
            draft.getAsJsonArray("items").add(first);
            draft.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("amount", 5);
            draft.getAsJsonArray("items").get(1).getAsJsonObject().addProperty("item", "minecraft:copper_ingot");
            add(draft, "tag:minecraft:planks", 4);
            draft.addProperty("chance", 0.25F);
            require(Files.readString(file).equals(before), "selection, reordering and cancellation only change a detached draft");
            save(player, edit(id, draft), true);
            source = read(file);
            var changed = source.getAsJsonArray("requirements").get(0).getAsJsonObject();
            require(source.get("priority").getAsInt() == 19 && source.get("authorNote").getAsString().equals("recipe metadata")
                    && changed.get("authorNote").getAsString().equals("group metadata") && changed.get("parallelizeUnaffected").getAsBoolean(),
                    "recipe/group metadata survives editing");
            var candidates = changed.getAsJsonArray("items");
            require(candidates.size() == 3 && candidates.get(0).getAsJsonObject().get("item").getAsString().equals("minecraft:gold_ingot")
                    && candidates.get(0).getAsJsonObject().get("amount").getAsInt() == 5
                    && candidates.get(0).getAsJsonObject().get("authorNote").getAsString().equals("gold metadata")
                    && candidates.get(1).getAsJsonObject().get("item").getAsString().equals("minecraft:copper_ingot")
                    && candidates.get(1).getAsJsonObject().get("authorNote").getAsString().equals("iron metadata"),
                    "candidate metadata follows original identity across reorder, quantity edit and resource replacement");
            require(changed.get("chance").getAsFloat() == 0.25F && !Files.readString(file).contains(IngredientGroupEditor.ORIGIN),
                    "group chance is saved without editor bookkeeping");
            runtime = (RequirementIngredientArray) MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0);
            require(runtime.getIngredients().get(2).matches(new ItemStack(Items.OAK_PLANKS))
                    && runtime.getIngredients().get(2).matches(new ItemStack(Items.BIRCH_PLANKS)), "tag candidates keep all member items");
            draft = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            draft.getAsJsonArray("items").remove(1);
            save(player, edit(id, draft), true);
            require(MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().size() == 1
                    && ((RequirementIngredientArray) MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0)).getIngredients().size() == 2,
                    "deleting a candidate keeps one grouped requirement");
            before = Files.readString(file);
            var valid = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            for (String invalid : List.of("{}", "{\"items\":[]}", "[]", "{", groupWith("missing:item", 1).toString(),
                    groupWith("minecraft:air", 1).toString(), groupWith("minecraft:stone", 65).toString(),
                    groupWith("tag:missing:empty", 1).toString())) save(player, new PktMachineEditorIngredientGroup(id, 0, invalid, "", 20, ""), false);
            for (String value : List.of("0", "1.5", "2147483648", "\"2\"")) {
                var invalid = valid.deepCopy();
                invalid.getAsJsonArray("items").get(0).getAsJsonObject().add("amount", JsonParser.parseString(value));
                save(player, edit(id, invalid), false);
            }
            for (float chance : new float[]{-1, 1.1F, Float.NaN, Float.POSITIVE_INFINITY}) {
                var invalid = valid.deepCopy(); invalid.addProperty("chance", chance);
                save(player, edit(id, invalid), false);
            }
            var invalid = valid.deepCopy(); invalid.addProperty("io-type", "output"); save(player, edit(id, invalid), false);
            invalid = valid.deepCopy(); invalid.getAsJsonArray("items").get(0).getAsJsonObject().addProperty(IngredientGroupEditor.ORIGIN, 999);
            save(player, edit(id, invalid), false);
            invalid = valid.deepCopy(); invalid.getAsJsonArray("items").get(1).getAsJsonObject().addProperty(IngredientGroupEditor.ORIGIN, 0);
            save(player, edit(id, invalid), false);
            invalid = valid.deepCopy(); invalid.add("items", new com.google.gson.JsonArray()); save(player, edit(id, invalid), false);
            invalid = IngredientGroupEditor.empty();
            for (int i = 0; i <= IngredientGroupEditor.MAX_CANDIDATES; i++) add(invalid, "minecraft:stone", 1);
            save(player, edit(id, invalid), false);
            save(player, new PktMachineEditorIngredientGroup(id, 511, valid.toString(), "", 20, ""), false);
            save(player, new PktMachineEditorIngredientGroup(id, -2, valid.toString(), "", 20, ""), false);
            require(Files.readString(file).equals(before), "invalid group requests leave source byte-for-byte intact");
            save(player, new PktMachineEditorIngredientGroup(id + "_invalid", -1, "{}", machine, 20, ""), false);
            require(!Files.exists(rejectedFile), "invalid initial group cannot create a recipe file");

            for (String field : List.of("nbt", "nbt-display", "minAmount", "chance")) {
                var advanced = JsonParser.parseString(before).getAsJsonObject();
                var candidate = advanced.getAsJsonArray("requirements").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject();
                candidate.add(field, JsonParser.parseString(field.startsWith("nbt") ? "{\"quality\":\"3\"}" : field.equals("chance") ? "0.5" : "1"));
                var advancedGroup = advanced.getAsJsonArray("requirements").get(0).getAsJsonObject();
                require(!IngredientGroupEditor.canEdit(advancedGroup), "advanced candidate not flattened: " + field);
                Files.writeString(file, advanced.toString());
                MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot());
                save(player, edit(id, valid), false);
                require(read(file).equals(advanced), "server protects advanced source even from ordinary forged replacement: " + field);
            }
            Files.writeString(file, before); MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot());
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GroupDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(18, denied.getInventory(), menu.snapshot());
            save(denied, edit(id, valid), false);
            require(Files.readString(file).equals(before), "permission denial cannot change source");
            Modularmachinery.LOG.info("[MMCE][ingredient-group-editor-regression] PASS: authenticated wire/disk/reload, new recipe first save, "
                    + "whole-alternative consumption/parallel batches, candidate tags/amounts/reorder/delete, metadata retention, cancel and advanced/invalid rejection");
        } finally {
            player.containerMenu = originalMenu;
            Files.deleteIfExists(file); Files.deleteIfExists(rejectedFile);
            MachineReloadListener.reloadNow();
        }
    }

    private static void checkConsumption(ServerLevel level, BlockPos origin, RequirementIngredientArray array) {
        BlockPos pos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        var before = level.getBlockState(pos);
        try {
            level.setBlock(pos, RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var inventory = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos)).getInventory();
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("group_fixture", "Group", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3)); inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 6));
            require(array.getMaxParallelism(context, 10, List.of(array)) == 3
                    && MachineTicker.consumeInputs(List.of(array), context, 3)
                    && inventory.getStackInSlot(0).getCount() == 1 && inventory.getStackInSlot(1).isEmpty(),
                    "saved group pays each batch with a whole candidate in priority order");
            inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 2));
            require(!array.canStart(context) && !MachineTicker.consumeInputs(List.of(array), context, 1)
                    && inventory.getStackInSlot(0).getCount() == 1 && inventory.getStackInSlot(1).getCount() == 2,
                    "incomplete alternatives cannot be pooled or partly consumed");
        } finally { level.setBlock(pos, before, Block.UPDATE_ALL); }
    }
    private static void add(JsonObject group, String item, int amount) {
        var candidate = IngredientGroupEditor.candidate(item); candidate.addProperty("amount", amount); group.getAsJsonArray("items").add(candidate);
    }
    private static JsonObject groupWith(String item, int amount) {
        var group = IngredientGroupEditor.empty(); var candidate = new JsonObject();
        candidate.addProperty("item", item); candidate.addProperty("amount", amount); group.getAsJsonArray("items").add(candidate); return group;
    }
    private static PktMachineEditorIngredientGroup edit(String id, JsonObject group) {
        return new PktMachineEditorIngredientGroup(id, 0, group.toString(), "", 20, "");
    }
    private static JsonObject snapshotGroup(String snapshot, String id) {
        for (var machine : JsonParser.parseString(snapshot).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (id.equals(recipe.getAsJsonObject().get("name").getAsString()))
                    return recipe.getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject();
        throw new AssertionError("Group missing from editor snapshot");
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "authoritative reload before success");
        } finally { buffer.release(); }
    }
    private static JsonObject read(java.nio.file.Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError("Ingredient group editor: " + message); }
}
