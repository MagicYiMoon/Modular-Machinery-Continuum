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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.Files;
import java.util.*;

/** Actual editor wire, atomic source merge, reload, snapshots and saved durability behavior. */
public final class DurabilityEditorRegressionChecks {
    private DurabilityEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "durability_editor_" + UUID.randomUUID().toString().replace("-", "");
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var invalidFile = file.resolveSibling(id + "_invalid.json");
        String machine = MachineReloadListener.getLoader().all().iterator().next().getRegistryName();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "DurabilityEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var originalMenu = player.containerMenu;
        var menu = new MenuMachineEditor(21, player.getInventory(), PktMachineEditorRequest.snapshot());
        player.containerMenu = menu;
        try {
            var tool = DurabilityEditor.create("minecraft:iron_pickaxe", 2, 10, 1);
            save(player, new PktMachineEditorDurability(id, -1, tool.toString(), machine, 100, "耐久测试"), true);
            var source = read(file);
            require(source.getAsJsonArray("requirements").get(0).getAsJsonObject().get("type").getAsString().equals("modularmachinery:item"),
                    "new source uses upstream item + consumeDurability format");
            require(DurabilityEditor.canEdit(snapshot(menu, id)), "authoritative durability snapshot is editable");
            checkRuntime(level, origin, id, false);
            source.addProperty("authorNote", "recipe metadata");
            var root = source.getAsJsonArray("requirements").get(0).getAsJsonObject();
            root.addProperty("authorNote", "tool metadata");
            root.addProperty("triggerTime", 12);
            root.addProperty("triggerRepeatable", true);
            Files.writeString(file, source.toString()); reload(menu);
            String before = Files.readString(file);
            var draft = RequirementResourceSelection.replace(snapshot(menu, id), "minecraft:diamond_pickaxe");
            require(draft.get("amount").getAsInt() == 2 && draft.get("consumeDurability").getAsInt() == 10
                    && Files.readString(file).equals(before), "resource replacement retains independent cancellable values");
            draft.addProperty("amount", 3); draft.addProperty("consumeDurability", 7); draft.addProperty("chance", 0.25F);
            save(player, edit(id, draft), true);
            source = read(file); root = source.getAsJsonArray("requirements").get(0).getAsJsonObject();
            require(source.get("authorNote").getAsString().equals("recipe metadata")
                    && root.get("authorNote").getAsString().equals("tool metadata") && root.get("triggerTime").getAsInt() == 12
                    && root.get("triggerRepeatable").getAsBoolean()
                    && root.get("amount").getAsInt() == 3 && root.get("consumeDurability").getAsInt() == 7
                    && root.get("chance").getAsFloat() == 0.25F && root.get("item").getAsString().equals("minecraft:diamond_pickaxe"),
                    "source metadata and trigger settings retained while all four editable fields persist");
            var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var synced = (RequirementItem) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(0);
            require(synced.getTriggerTime() == 12 && synced.isTriggerRepeatable() && synced.getConsumeDurability() == 7 && synced.getAmount() == 3
                    && synced.getChance() == 0.25F, "wire snapshots retain durability and trigger settings");
            root.addProperty("type", DurabilityEditor.ALIAS);
            Files.writeString(file, source.toString()); reload(menu);
            draft = DurabilityEditor.create("minecraft:iron_pickaxe", 2, 10, 1);
            save(player, edit(id, draft), true);
            require(read(file).getAsJsonArray("requirements").get(0).getAsJsonObject().get("type").getAsString().equals(DurabilityEditor.ALIAS),
                    "existing compatibility alias retained on disk");
            source = read(file); root = source.getAsJsonArray("requirements").get(0).getAsJsonObject();
            root.remove("triggerTime"); root.remove("triggerRepeatable"); Files.writeString(file, source.toString()); reload(menu);
            draft = DurabilityEditor.create("minecraft:iron_pickaxe", 2, 10, 0);
            save(player, edit(id, draft), true);
            checkRuntime(level, origin, id, true);
            before = Files.readString(file);
            var valid = snapshot(menu, id).deepCopy();
            for (String key : List.of("amount", "consumeDurability", "chance")) {
                var values = key.equals("chance") ? List.of("-1", "1.1", "1e100", "\"NaN\"", "\"Infinity\"", "\"0.5\"", "null")
                        : key.equals("amount") ? List.of("0", "-1", "65", "1.5", "\"2\"", "null")
                        : List.of("0", "-1", "1.5", "2000000001", "2147483648", "\"10\"", "null");
                for (String value : values) {
                    var bad = valid.deepCopy(); bad.add(key, JsonParser.parseString(value)); save(player, edit(id, bad), false);
                }
            }
            for (String item : List.of("minecraft:stone", "minecraft:air", "missing:tool", "ore:toolPickaxe", "tag:minecraft:planks", "tag:missing:tools")) {
                var bad = valid.deepCopy(); bad.addProperty("item", item); save(player, edit(id, bad), false);
            }
            var bad = valid.deepCopy(); bad.addProperty("io-type", "output"); save(player, edit(id, bad), false);
            for (String key : List.of("nbt", "nbt-display")) {
                bad = valid.deepCopy(); bad.addProperty(key, "[invalid"); save(player, edit(id, bad), false);
            }
            save(player, new ModularmachineryNetwork.PktMachineEditorEdit(id, 0, 2, 1, "minecraft:stone"), false);
            var group = IngredientGroupEditor.empty(); group.getAsJsonArray("items").add(IngredientGroupEditor.candidate("minecraft:iron_ingot"));
            save(player, new PktMachineEditorIngredientGroup(id, 0, group.toString(), "", 20, ""), false);
            for (String payload : List.of("{}", "[]", "{")) save(player, new PktMachineEditorDurability(id, 0, payload, "", 20, ""), false);
            save(player, new PktMachineEditorDurability(id, -2, valid.toString(), "", 20, ""), false);
            save(player, new PktMachineEditorDurability(id, 511, valid.toString(), "", 20, ""), false);
            require(Files.readString(file).equals(before), "invalid edits and alternative packet routes cannot alter source");
            save(player, new PktMachineEditorDurability(id + "_invalid", -1, "{}", machine, 20, ""), false);
            require(!Files.exists(invalidFile), "invalid first save creates no file");
            for (String field : List.of("ordinary", "group", "legacy")) {
                var advanced = JsonParser.parseString(before).getAsJsonObject();
                var reqs = advanced.getAsJsonArray("requirements");
                var req = reqs.get(0).getAsJsonObject();
                if (field.equals("ordinary")) reqs.set(0, BasicRequirementType.ITEM.create("minecraft:iron_pickaxe", false, 1, 1));
                else if (field.equals("group")) reqs.set(0, group);
                else if (field.equals("legacy")) req.addProperty("item", "ore:toolPickaxe");
                else req.add(field, new JsonObject());
                require(!DurabilityEditor.canEdit(reqs.get(0).getAsJsonObject()), "advanced source not flattened: " + field);
                Files.writeString(file, advanced.toString()); reload(menu);
                save(player, edit(id, valid), false);
                require(read(file).equals(advanced), "server verifies actual source type and conditions: " + field);
            }
            // A concrete item and a tag are distinct editor modes; both directions require new requirements.
            Files.writeString(file, before); reload(menu);
            var tagged = DurabilityEditor.create("tag:minecraft:pickaxes", 2, 10, 1);
            save(player, edit(id, tagged), false);
            source = read(file); source.getAsJsonArray("requirements").set(0, tagged);
            Files.writeString(file, source.toString()); reload(menu);
            save(player, edit(id, valid), false);
            tagged.addProperty("amount", 3); save(player, edit(id, tagged), true);
            require(snapshot(menu, id).get("item").getAsString().equals("tag:minecraft:pickaxes"), "tag stays a selector in snapshots");
            checkRuntime(level, origin, id, false);
            expectInvalid(() -> RequirementResourceSelection.replace(valid, "minecraft:stone"));
            expectInvalid(() -> RequirementResourceSelection.replace(valid, "tag:minecraft:pickaxes"));
            expectInvalid(() -> RequirementResourceSelection.replace(tagged, "minecraft:iron_pickaxe"));
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "DurabilityDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(21, denied.getInventory(), menu.snapshot());
            before = Files.readString(file); save(denied, edit(id, tagged), false);
            require(Files.readString(file).equals(before), "permission denied before file mutation");
            Modularmachinery.LOG.info("[MMCE][durability-editor-regression] PASS: authenticated wire/first save/disk/reload/snapshots, "
                    + "uses/batch/damage/break/zero-chance/tag runtime, source alias/metadata/cancel, malformed/advanced/source-type/permission rejection");
        } finally {
            player.containerMenu = originalMenu;
            Files.deleteIfExists(file); Files.deleteIfExists(invalidFile); MachineReloadListener.reloadNow();
        }
    }
    private static void checkRuntime(ServerLevel level, BlockPos origin, String id, boolean noCharge) {
        var requirement = (RequirementItem) MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0);
        BlockPos pos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        var before = level.getBlockState(pos);
        try {
            level.setBlock(pos, RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var inventory = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos)).getInventory();
            for (int i = 0; i < inventory.getSlots(); i++) inventory.setStackInSlot(i, ItemStack.EMPTY);
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("durability_fixture", "Durability", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            require(!requirement.canStart(context), "zero-chance and tags still need usable tools");
            var tool = new ItemStack(Items.IRON_PICKAXE); inventory.setStackInSlot(0, tool);
            require(requirement.canStart(context) && MachineTicker.consumeInputs(List.of(requirement), context, 3)
                    && inventory.getStackInSlot(0).getCount() == 1
                    && inventory.getStackInSlot(0).getDamageValue() == (noCharge ? 0 : requirement.getAmount() * 30),
                    "saved use count is multiplied by batch; each use damages one tool rather than consuming an item");
            inventory.setStackInSlot(0, new ItemStack(Items.STONE));
            require(!requirement.canStart(context) && !hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan
                            .plan(context, List.of(requirement), 1, false).valid()
                            && (noCharge || !MachineTicker.consumeInputs(List.of(requirement), context, 1)),
                    "non-tool cannot pay durability input");
            tool = new ItemStack(Items.IRON_PICKAXE); tool.setDamageValue(tool.getMaxDamage() - 1); inventory.setStackInSlot(0, tool);
            require(!requirement.canStart(context) && !hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan
                            .plan(context, List.of(requirement), 1, false).valid()
                            && (noCharge || !MachineTicker.consumeInputs(List.of(requirement), context, 1))
                            && inventory.getStackInSlot(0).getDamageValue() == tool.getMaxDamage() - 1,
                    "insufficient uses never partly damage a tool even at zero probability");
            if (!noCharge) {
                for (int i = 1; i < requirement.getAmount(); i++) {
                    var other = new ItemStack(Items.IRON_PICKAXE); other.setDamageValue(other.getMaxDamage() - 1); inventory.setStackInSlot(i, other);
                }
                require(MachineTicker.consumeInputs(List.of(requirement), context, 1), "last partial uses summed across tools");
                for (int i = 0; i < requirement.getAmount(); i++) require(inventory.getStackInSlot(i).isEmpty(), "used-up tools break");
                if (requirement.getRawId().startsWith("tag:")) {
                    inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND_PICKAXE));
                    require(MachineTicker.consumeInputs(List.of(requirement), context, 1)
                            && inventory.getStackInSlot(0).getDamageValue() == requirement.getAmount() * 10, "tag accepts a second actual tool member");
                }
            }
        } finally { level.setBlock(pos, before, Block.UPDATE_ALL); }
    }
    private static PktMachineEditorDurability edit(String id, JsonObject req) { return new PktMachineEditorDurability(id, 0, req.toString(), "", 20, ""); }
    private static JsonObject read(java.nio.file.Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject();
        throw new AssertionError("Durability missing from snapshot");
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "reload/sync precede acknowledgement");
        } finally { buffer.release(); }
    }
    private static void expectInvalid(Runnable operation) {
        try { operation.run(); } catch (RuntimeException expected) { return; }
        throw new AssertionError("Invalid durability replacement accepted");
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError("Durability editor: " + message); }
}
