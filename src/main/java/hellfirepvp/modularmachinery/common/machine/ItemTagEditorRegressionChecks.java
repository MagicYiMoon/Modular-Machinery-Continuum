/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktMachineEditorEdit;
import io.netty.buffer.Unpooled;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.util.FakePlayer;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Saves tag inputs through the real authenticated editor envelope and reloads the runtime recipe. */
public final class ItemTagEditorRegressionChecks {
    private ItemTagEditorRegressionChecks() {}

    public static void run(ServerLevel level) throws Exception {
        checkSearch();
        String id = "item_tag_editor_" + UUID.randomUUID().toString().replace("-", "");
        var directory = ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        var file = directory.resolve(id + ".json");
        var rejectedFile = directory.resolve(id + "_rejected.json");
        String machine = MachineReloadListener.getLoader().all().iterator().next().getRegistryName();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "TagEditorTest")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var originalMenu = player.containerMenu;
        var menu = new MenuMachineEditor(17, player.getInventory(), PktMachineEditorRequest.snapshot());
        player.containerMenu = menu;
        try {
            save(player, new PktMachineEditorRequirementAdd(id, "item", "tag:minecraft:planks", false,
                    4, 0.5F, machine, 31, "标签配方"), true);
            var saved = read(file);
            var requirement = saved.getAsJsonArray("requirements").get(0).getAsJsonObject();
            require(requirement.get("item").getAsString().equals("tag:minecraft:planks")
                    && requirement.get("amount").getAsInt() == 4 && requirement.get("chance").getAsFloat() == 0.5F,
                    "first save preserves the selector and numeric values");
            var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var runtime = (RequirementItem) loaded.requirements().get(0);
            require(runtime.matches(new ItemStack(Items.OAK_PLANKS)) && runtime.matches(new ItemStack(Items.BIRCH_PLANKS))
                    && !runtime.matches(new ItemStack(Items.IRON_INGOT)), "runtime accepts multiple tag members, rejects unrelated items");
            var decoded = (RequirementItem) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(0);
            require(decoded.getRawId().equals("tag:minecraft:planks") && decoded.matches(new ItemStack(Items.BIRCH_PLANKS)),
                    "recipe synchronization retains a live tag selector");
            require(snapshotRequirement(menu.snapshot(), id).get("item").getAsString().equals("tag:minecraft:planks"),
                    "authoritative editor refresh retains tag identity");

            requirement.addProperty("authorNote", "keep me");
            requirement.addProperty("selector-tag", "wood");
            saved.addProperty("priority", 7);
            Files.writeString(file, saved.toString());
            MachineReloadListener.reloadNow();
            menu.setSnapshot(PktMachineEditorRequest.snapshot());
            String before = Files.readString(file);
            var draft = RequirementResourceSelection.replace(requirement, "tag:minecraft:logs");
            require(!draft.equals(requirement) && Files.readString(file).equals(before)
                    && requirement.get("item").getAsString().equals("tag:minecraft:planks"),
                    "tag replacement uses a detached draft; cancellation leaves source untouched");
            save(player, new PktMachineEditorEdit(id, 0, 6, 0.25F, "tag:minecraft:logs"), true);
            var expected = saved.deepCopy();
            var edited = expected.getAsJsonArray("requirements").get(0).getAsJsonObject();
            edited.addProperty("item", "tag:minecraft:logs");
            edited.addProperty("amount", 6);
            edited.addProperty("chance", 0.25F);
            require(read(file).equals(expected), "replacement preserves IO, metadata and other recipe fields");
            require(snapshotRequirement(menu.snapshot(), id).get("item").getAsString().equals("tag:minecraft:logs"),
                    "reopening uses the replacement tag");
            runtime = (RequirementItem) MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0);
            require(runtime.matches(new ItemStack(Items.OAK_LOG)) && runtime.matches(new ItemStack(Items.BIRCH_LOG))
                    && !runtime.matches(new ItemStack(Items.OAK_PLANKS)), "replacement loads the new membership");
            save(player, new PktMachineEditorEdit(id, 0, 8, 1F, ""), true);
            require(read(file).getAsJsonArray("requirements").get(0).getAsJsonObject().get("item").getAsString().equals("tag:minecraft:logs"),
                    "numeric-only edit retains the tag");
            save(player, new PktMachineEditorRequirementAdd(id, "item", "minecraft:iron_ingot", false, 1, 1F, "", 20, ""), true);
            save(player, new PktMachineEditorRequirementAdd(id, "item", "minecraft:diamond", true, 1, 1F, "", 20, ""), true);
            before = Files.readString(file);
            for (String invalid : List.of("tag:INVALID ID", "tag:missing:nonexistent", "tag:", "tag:minecraft:air")) {
                save(player, new PktMachineEditorRequirementAdd(id, "item", invalid, false, 1, 1F, "", 20, ""), false);
                save(player, new PktMachineEditorEdit(id, 0, 4, 1F, invalid), false);
            }
            // Exercise any declared empty tags as well as missing tags, without rebinding global registries.
            for (var tag : BuiltInRegistries.ITEM.getTagNames().filter(t -> ItemTagSelection.members(t.location()).isEmpty()).toList())
                save(player, new PktMachineEditorRequirementAdd(id, "item", "tag:" + tag.location(), false, 1, 1F, "", 20, ""), false);
            save(player, new PktMachineEditorRequirementAdd(id, "item", "tag:minecraft:planks", true, 1, 1F, "", 20, ""), false);
            save(player, new PktMachineEditorRequirementAdd(id, "item", "tag:minecraft:planks", false, 65, 1F, "", 20, ""), false);
            save(player, new PktMachineEditorEdit(id, 0, 65, 1F, ""), false);
            save(player, new PktMachineEditorEdit(id, 0, 4, 1F, "minecraft:oak_log"), false);
            save(player, new PktMachineEditorEdit(id, 1, 4, 1F, "tag:minecraft:planks"), false);
            save(player, new PktMachineEditorEdit(id, 2, 4, 1F, "tag:minecraft:planks"), false);
            require(Files.readString(file).equals(before), "invalid requests and selector-type conversions cannot change the file");
            save(player, new PktMachineEditorRequirementAdd(id + "_rejected", "item", "tag:minecraft:planks", true,
                    1, 1F, machine, 20, ""), false);
            require(!Files.exists(rejectedFile), "invalid first requirement cannot create a recipe");
            var output = BasicRequirementType.ITEM.create("minecraft:oak_planks", true, 1, 1);
            output.addProperty("item", "tag:minecraft:planks");
            require(!RequirementResourceSelection.canReplace(output), "tag output does not offer reselection");
            var nbt = BasicRequirementType.ITEM.create("tag:minecraft:planks", false, 1, 1);
            nbt.add("nbt", new JsonObject());
            require(!RequirementResourceSelection.canReplace(nbt), "NBT tags retain advanced-selector protection");
            Modularmachinery.LOG.info("[MMCE][item-tag-editor-regression] PASS: tag search, actual envelope/disk/reload/snapshots, "
                    + "multiple member matching, draft cancellation, reselection/metadata, validation and input-only semantics");
        } finally {
            player.containerMenu = originalMenu;
            Files.deleteIfExists(file);
            Files.deleteIfExists(rejectedFile);
            Files.deleteIfExists(file.resolveSibling(file.getFileName() + ".mmce.tmp"));
            MachineReloadListener.reloadNow();
        }
    }

    private static void checkSearch() {
        var id = new ResourceLocation("forge", "ingots/iron");
        for (String query : List.of("", "iron", "铁锭", "@forge #ingots 铁", "IRON", "#forge:ingots/iron"))
            require(ItemTagSelection.matches(query, id, () -> List.of("铁锭", "Iron Ingot")), "matches tag search: " + query);
        for (String query : List.of("@minecraft", "#gold", "不存在", "@", "#", "iron gold"))
            require(!ItemTagSelection.matches(query, id, () -> List.of("铁锭", "Iron Ingot")), "rejects tag search: " + query);
        var icon = ItemTagSelection.representative("tag:minecraft:planks");
        require(!icon.isEmpty() && ItemTagSelection.members(new ResourceLocation("minecraft", "planks")).contains(icon.getItem()),
                "example icon belongs to the selected tag");
    }

    private static JsonObject snapshotRequirement(String snapshot, String id) {
        for (var machine : JsonParser.parseString(snapshot).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes")) {
                var entry = recipe.getAsJsonObject();
                if (id.equals(entry.get("name").getAsString())) return entry.getAsJsonArray("requirements").get(0).getAsJsonObject();
            }
        throw new AssertionError("Tag recipe absent from editor snapshot");
    }

    private static void save(FakePlayer player, Object mutation, boolean expectedSuccess) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expectedSuccess, "save outcome: " + reply.result().message());
            if (expectedSuccess) require(reply.snapshot() != null && reply.snapshot().equals(PktMachineEditorRequest.snapshot()), "fresh server document");
        } finally { buffer.release(); }
    }

    private static JsonObject read(java.nio.file.Path path) throws Exception {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError("Item tag editor: " + message);
    }
}
