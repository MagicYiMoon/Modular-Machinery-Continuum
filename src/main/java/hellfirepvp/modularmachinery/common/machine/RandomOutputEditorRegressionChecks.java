/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Weighted output editing through real request, source, reload, snapshots, and machine emission paths. */
public final class RandomOutputEditorRegressionChecks {
    private RandomOutputEditorRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "random_editor_" + UUID.randomUUID().toString().replace("-", "");
        var directory = ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        var file = directory.resolve(id + ".json");
        var invalidFile = directory.resolve(id + "_invalid.json");
        String machine = MachineReloadListener.getLoader().all().iterator().next().getRegistryName();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "RandomEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var originalMenu = player.containerMenu;
        var menu = new MenuMachineEditor(19, player.getInventory(), PktMachineEditorRequest.snapshot());
        player.containerMenu = menu;
        try {
            var group = IngredientGroupEditor.empty(true);
            add(group, "minecraft:iron_ingot", 2, 4, 2);
            add(group, "minecraft:gold_ingot", 1, 1, 1);
            add(group, "minecraft:copper_ingot", 1, 3, 0);
            save(player, new PktMachineEditorIngredientGroup(id, -1, group.toString(), machine, 37, "随机产出"), true);
            var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var runtime = (RequirementIngredientArray) loaded.requirements().get(0);
            checkWeights(runtime);
            checkEmission(level, origin, loaded, 6, Items.IRON_INGOT);
            var synced = (RequirementIngredientArray) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(0);
            checkWeights(synced);
            require(IngredientGroupEditor.canEdit(snapshotGroup(menu.snapshot(), id))
                    && IngredientGroupEditor.isOutput(snapshotGroup(menu.snapshot(), id)), "output snapshot is recognized and editable");
            require(Math.abs(IngredientGroupEditor.selectionShare(group, 0, 2) - 2.0 / 3) < 1e-12
                    && IngredientGroupEditor.selectionShare(group, 2, 0) == 0
                    && Math.abs(IngredientGroupEditor.selectionShare(group, 0, 1) - 0.5) < 1e-12,
                    "preview uses candidate weight independently of group chance and current unsaved weight");
            var huge = group.deepCopy();
            huge.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("chance", Float.MAX_VALUE);
            huge.getAsJsonArray("items").get(1).getAsJsonObject().addProperty("chance", Float.MAX_VALUE);
            require(Math.abs(IngredientGroupEditor.selectionShare(huge, 0, Float.MAX_VALUE) - 0.5) < 1e-12,
                    "large valid weights do not overflow preview totals");

            var source = read(file);
            source.addProperty("authorNote", "recipe note");
            var old = source.getAsJsonArray("requirements").get(0).getAsJsonObject();
            old.addProperty("authorNote", "group note");
            old.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("authorNote", "iron note");
            old.getAsJsonArray("items").get(1).getAsJsonObject().addProperty("authorNote", "gold note");
            Files.writeString(file, source.toString());
            MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot());
            String before = Files.readString(file);
            var draft = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            draft.getAsJsonArray("items").remove(2);
            var first = draft.getAsJsonArray("items").remove(0); draft.getAsJsonArray("items").add(first);
            var selected = draft.getAsJsonArray("items").get(0).getAsJsonObject();
            selected.addProperty("item", "minecraft:diamond");
            selected.addProperty("amount", 3); selected.addProperty("minAmount", 3); selected.addProperty("maxAmount", 7);
            selected.addProperty("chance", 0.5F); draft.addProperty("chance", 0.25F);
            require(Files.readString(file).equals(before), "range/weight/reorder/delete edits remain a cancellable detached draft");
            save(player, edit(id, draft), true);
            var saved = read(file);
            var changed = saved.getAsJsonArray("requirements").get(0).getAsJsonObject();
            require(saved.get("authorNote").getAsString().equals("recipe note") && changed.get("authorNote").getAsString().equals("group note")
                    && changed.get("chance").getAsFloat() == 0.25F && changed.getAsJsonArray("items").size() == 2,
                    "group chance, deletion and root metadata persist");
            var diamond = changed.getAsJsonArray("items").get(0).getAsJsonObject();
            require(diamond.get("item").getAsString().equals("minecraft:diamond") && diamond.get("minAmount").getAsInt() == 3
                    && diamond.get("maxAmount").getAsInt() == 7 && diamond.get("chance").getAsFloat() == 0.5F
                    && diamond.get("authorNote").getAsString().equals("gold note")
                    && changed.getAsJsonArray("items").get(1).getAsJsonObject().get("authorNote").getAsString().equals("iron note")
                    && !Files.readString(file).contains(IngredientGroupEditor.ORIGIN), "ranges/weights and candidate metadata follow identity after replacement/reorder");
            before = Files.readString(file);
            var valid = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            for (String key : List.of("minAmount", "maxAmount")) for (String value : List.of("0", "65", "1.5", "\"2\"", "2147483648")) {
                var bad = valid.deepCopy(); bad.getAsJsonArray("items").get(0).getAsJsonObject().add(key, JsonParser.parseString(value));
                save(player, edit(id, bad), false);
            }
            var bad = valid.deepCopy(); bad.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("minAmount", 8);
            save(player, edit(id, bad), false);
            for (float weight : new float[]{-1, Float.NaN, Float.POSITIVE_INFINITY}) {
                bad = valid.deepCopy(); bad.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("chance", weight);
                save(player, edit(id, bad), false);
            }
            for (float chance : new float[]{-1, 1.1F, Float.NaN, Float.POSITIVE_INFINITY}) {
                bad = valid.deepCopy(); bad.addProperty("chance", chance); save(player, edit(id, bad), false);
            }
            bad = valid.deepCopy(); bad.getAsJsonArray("items").forEach(item -> item.getAsJsonObject().addProperty("chance", 0));
            save(player, edit(id, bad), false);
            for (String item : List.of("tag:minecraft:planks", "missing:item", "minecraft:air")) {
                bad = valid.deepCopy(); bad.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("item", item);
                save(player, edit(id, bad), false);
            }
            bad = valid.deepCopy(); bad.addProperty("io-type", "input");
            // Use fixed quantities/weights so validation succeeds, then source-direction validation must reject.
            for (var item : bad.getAsJsonArray("items")) {
                var candidate = item.getAsJsonObject(); candidate.addProperty("minAmount", 3); candidate.addProperty("maxAmount", 3);
                candidate.addProperty("amount", 3); candidate.addProperty("chance", 1);
            }
            save(player, edit(id, bad), false);
            require(Files.readString(file).equals(before), "invalid ranges/weights/chances, tag outputs and direction changes never write");
            save(player, new PktMachineEditorIngredientGroup(id + "_invalid", -1, bad.toString().replace("\"input\"", "\"invalid\""), machine, 20, ""), false);
            require(!Files.exists(invalidFile), "invalid first output cannot create recipe file");

            for (String field : List.of("nbt", "nbt-display", "tag-output")) {
                var advanced = JsonParser.parseString(before).getAsJsonObject();
                var entry = advanced.getAsJsonArray("requirements").get(0).getAsJsonObject().getAsJsonArray("items").get(0).getAsJsonObject();
                if (field.equals("tag-output")) entry.addProperty("item", "tag:minecraft:planks");
                else entry.add(field, JsonParser.parseString("{\"quality\":\"3\"}"));
                require(!IngredientGroupEditor.canEdit(advanced.getAsJsonArray("requirements").get(0).getAsJsonObject()), "advanced output not flattened");
                Files.writeString(file, advanced.toString()); MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot());
                save(player, edit(id, valid), false);
                require(read(file).equals(advanced), "advanced source is protected on server: " + field);
            }
            Files.writeString(file, before); MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot());
            var disabled = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            disabled.addProperty("chance", 0); disabled.getAsJsonArray("items").forEach(item -> item.getAsJsonObject().addProperty("chance", 0));
            save(player, edit(id, disabled), true);
            checkEmission(level, origin, MachineReloadListener.getRecipeLoader().getRecipe(id), 0, Items.DIAMOND);
            var tiny = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            tiny.addProperty("chance", 1e-8F); tiny.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("chance", 1e-8F);
            save(player, edit(id, tiny), true);
            require(((RequirementIngredientArray) MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0)).getChance() == 1e-8F,
                    "small scientific-notation probabilities and weights survive save/reload");
            Modularmachinery.LOG.info("[MMCE][random-output-editor-regression] PASS: real wire/disk/reload/snapshots, weighted boundaries, range endpoints, "
                    + "actual batch emission/zero chance, live share math, metadata/reorder/delete/cancel, validation, direction and advanced-source protection");
        } finally {
            player.containerMenu = originalMenu;
            Files.deleteIfExists(file); Files.deleteIfExists(invalidFile);
            MachineReloadListener.reloadNow();
        }
    }

    private static void checkWeights(RequirementIngredientArray array) {
        require(array.getIOType() == IOType.OUTPUT && array.select(0).item().getRawId().equals("minecraft:iron_ingot")
                && array.select(0.66).item().getRawId().equals("minecraft:iron_ingot")
                && array.select(0.67).item().getRawId().equals("minecraft:gold_ingot")
                && array.select(Math.nextDown(1.0)).item().getRawId().equals("minecraft:gold_ingot"), "weights 2:1:0 have correct intervals and skip zero weight");
        var first = array.getCandidates().get(0);
        require(first.minAmount() == 2 && first.maxAmount() == 4 && first.amountAt(0) == 2
                && first.amountAt(1) == 3 && first.amountAt(2) == 4, "inclusive integer output range");
    }
    private static void checkEmission(ServerLevel level, BlockPos origin, MachineRecipe recipe, int expected, Item item) {
        BlockPos pos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        var before = level.getBlockState(pos);
        try {
            level.setBlock(pos, RegistryBlocks.itemOutputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var inventory = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos)).getInventory();
            for (int i = 0; i < inventory.getSlots(); i++) inventory.setStackInSlot(i, ItemStack.EMPTY);
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("random_fixture", "Random", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            MachineTicker.emitOutputs(recipe, recipe.requirements(), context, 3);
            int count = 0, other = 0;
            for (int i = 0; i < inventory.getSlots(); i++) {
                var stack = inventory.getStackInSlot(i);
                if (stack.is(item)) count += stack.getCount(); else other += stack.getCount();
            }
            require(count == expected && other == 0, "real emission chooses one candidate, rolls amount, applies batch multiplier and respects zero chance");
        } finally { level.setBlock(pos, before, Block.UPDATE_ALL); }
    }
    private static void add(JsonObject group, String item, int min, int max, float weight) {
        var candidate = IngredientGroupEditor.candidate(item, true);
        candidate.addProperty("amount", min); candidate.addProperty("minAmount", min); candidate.addProperty("maxAmount", max);
        candidate.addProperty("chance", weight); group.getAsJsonArray("items").add(candidate);
    }
    private static PktMachineEditorIngredientGroup edit(String id, JsonObject group) { return new PktMachineEditorIngredientGroup(id, 0, group.toString(), "", 20, ""); }
    private static JsonObject snapshotGroup(String snapshot, String id) {
        for (var machine : JsonParser.parseString(snapshot).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject();
        throw new AssertionError("Random output missing from snapshot");
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "authoritative reload before save acknowledgement");
        } finally { buffer.release(); }
    }
    private static JsonObject read(java.nio.file.Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError("Random output editor: " + message); }
}
