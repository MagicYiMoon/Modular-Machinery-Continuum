/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
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

/** Catalyst editor's actual envelope, source merge, reload and in-world optional effect/consumption. */
public final class CatalystEditorRegressionChecks {
    private CatalystEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "catalyst_editor_" + UUID.randomUUID().toString().replace("-", "");
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var invalidFile = file.resolveSibling(id + "_invalid.json");
        String machine = MachineReloadListener.getLoader().all().iterator().next().getRegistryName();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "CatalystEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var originalMenu = player.containerMenu;
        var menu = new MenuMachineEditor(20, player.getInventory(), PktMachineEditorRequest.snapshot());
        player.containerMenu = menu;
        try {
            var group = CatalystEditor.empty();
            group.getAsJsonArray("items").add(IngredientGroupEditor.candidate("minecraft:iron_ingot"));
            group.getAsJsonArray("items").add(IngredientGroupEditor.candidate("tag:minecraft:planks"));
            var mods = group.getAsJsonArray("modifiers");
            mods.add(modifier("duration", false, 1, 0.5F, false));
            mods.add(modifier("energy", false, 1, 0.5F, false));
            mods.add(modifier("item", true, 1, 2, false));
            mods.add(modifier("fluid", true, 0, 0.1F, true));
            save(player, new PktMachineEditorIngredientGroup(id, -1, group.toString(), machine, 100, "催化剂测试"), true);
            var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            require(loaded.requirements().get(0) instanceof RequirementCatalyst catalyst && catalyst.getModifiers().size() == 4,
                    "first requirement creates real catalyst with all modifiers");
            require(IngredientGroupEditor.canEdit(snapshotGroup(menu.snapshot(), id)), "authoritative catalyst snapshot remains editable");

            var source = read(file);
            source.addProperty("authorNote", "recipe note");
            var root = source.getAsJsonArray("requirements").get(0).getAsJsonObject();
            root.addProperty("authorNote", "catalyst note");
            var tips = new com.google.gson.JsonArray(); tips.add("效果说明"); root.add("tooltips", tips);
            root.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("authorNote", "iron note");
            root.getAsJsonArray("items").get(1).getAsJsonObject().addProperty("authorNote", "tag note");
            for (int i = 0; i < 4; i++) root.getAsJsonArray("modifiers").get(i).getAsJsonObject().addProperty("authorNote", "mod " + i);
            source.getAsJsonArray("requirements").add(BasicRequirementType.ITEM.create("minecraft:copper_ingot", true, 2, 1));
            source.getAsJsonArray("requirements").add(BasicRequirementType.ENERGY.create("", false, 200, 1));
            source.getAsJsonArray("requirements").add(BasicRequirementType.FLUID.create("minecraft:water", true, 100, 0.2F));
            Files.writeString(file, source.toString()); reload(menu);
            loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            checkRuntime(level, origin, loaded, true);
            var decoded = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of()));
            var synced = (RequirementCatalyst) decoded.requirements().get(0);
            require(synced.getModifiers().size() == 4 && synced.getTooltips().equals(List.of("效果说明"))
                    && synced.isParallelizeUnaffected(), "snapshots keep effects, tooltips and source batch exemption");
            var chanceScaled = (RequirementCatalyst) synced.scaled(0.25F, 1, IOType.INPUT, true);
            require(chanceScaled.getChance() == 0.25F && chanceScaled.getIngredients().get(0).getAmount() == 1
                    && chanceScaled.getCandidates().get(0).minAmount() == 1 && chanceScaled.getCandidates().get(0).maxAmount() == 1
                    && chanceScaled.getCandidates().get(0).weight() == 0.25F,
                    "source-style chance modifier changes catalyst chance/weight without changing amounts");

            String before = Files.readString(file);
            var draft = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            draft.addProperty("chance", 0);
            var candidate = draft.getAsJsonArray("items").remove(0); draft.getAsJsonArray("items").add(candidate);
            draft.getAsJsonArray("items").get(1).getAsJsonObject().addProperty("item", "minecraft:gold_ingot");
            var moved = draft.getAsJsonArray("modifiers").remove(0); draft.getAsJsonArray("modifiers").add(moved);
            require(Files.readString(file).equals(before), "detached candidate and modifier changes are cancellable");
            save(player, edit(id, draft), true);
            var saved = read(file);
            var changed = saved.getAsJsonArray("requirements").get(0).getAsJsonObject();
            require(saved.get("authorNote").getAsString().equals("recipe note")
                    && changed.get("authorNote").getAsString().equals("catalyst note")
                    && changed.getAsJsonArray("tooltips").equals(tips)
                    && changed.getAsJsonArray("items").get(0).getAsJsonObject().get("authorNote").getAsString().equals("tag note")
                    && changed.getAsJsonArray("items").get(1).getAsJsonObject().get("authorNote").getAsString().equals("iron note")
                    && changed.getAsJsonArray("modifiers").get(3).getAsJsonObject().get("authorNote").getAsString().equals("mod 0")
                    && !Files.readString(file).contains(IngredientGroupEditor.ORIGIN), "metadata follows candidate/modifier origins, tooltips retained, editor indices omitted");
            checkRuntime(level, origin, MachineReloadListener.getRecipeLoader().getRecipe(id), false);
            draft = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            draft.getAsJsonArray("modifiers").remove(0);
            save(player, edit(id, draft), true);
            require(((RequirementCatalyst) MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0)).getModifiers().size() == 3,
                    "delete modifier persists after reload");
            before = Files.readString(file);
            var valid = IngredientGroupEditor.draft(snapshotGroup(menu.snapshot(), id));
            for (String payload : List.of("{}", "[]", "{")) save(player, new PktMachineEditorIngredientGroup(id, 0, payload, "", 20, ""), false);
            for (String key : List.of("operation", "multiplier", "io", "target", "affectChance")) {
                for (String value : switch (key) {
                    case "operation" -> List.of("-1", "2", "1.5", "\"1\"");
                    case "multiplier" -> List.of("\"2\"", "\"NaN\"", "1e100");
                    case "io" -> List.of("\"both\"", "null");
                    case "target" -> List.of("\"missing:custom\"", "null");
                    default -> List.of("\"true\"", "1");
                }) {
                    var bad = valid.deepCopy();
                    bad.getAsJsonArray("modifiers").get(0).getAsJsonObject().add(key, JsonParser.parseString(value));
                    save(player, edit(id, bad), false);
                }
            }
            for (String target : List.of("duration", "energy")) {
                var bad = valid.deepCopy(); bad.getAsJsonArray("modifiers").add(modifier(target, false, 1, 1, true));
                save(player, edit(id, bad), false);
            }
            var bad = valid.deepCopy(); bad.addProperty("io-type", "output"); save(player, edit(id, bad), false);
            bad = valid.deepCopy(); bad.addProperty("type", IngredientGroupEditor.TYPE); save(player, edit(id, bad), false);
            bad = valid.deepCopy(); bad.getAsJsonArray("modifiers").get(0).getAsJsonObject().addProperty(IngredientGroupEditor.ORIGIN, 999);
            save(player, edit(id, bad), false);
            bad = valid.deepCopy(); bad.getAsJsonArray("modifiers").get(1).getAsJsonObject().add(IngredientGroupEditor.ORIGIN,
                    bad.getAsJsonArray("modifiers").get(0).getAsJsonObject().get(IngredientGroupEditor.ORIGIN)); save(player, edit(id, bad), false);
            bad = valid.deepCopy(); bad.getAsJsonArray("items").get(0).getAsJsonObject().addProperty("amount", 0); save(player, edit(id, bad), false);
            bad = valid.deepCopy(); bad.addProperty("chance", 1.1F); save(player, edit(id, bad), false);
            bad = valid.deepCopy(); for (int i = 0; i <= CatalystEditor.MAX_MODIFIERS; i++) bad.getAsJsonArray("modifiers").add(CatalystEditor.modifier());
            save(player, edit(id, bad), false);
            require(Files.readString(file).equals(before), "invalid data cannot alter source");
            save(player, new PktMachineEditorIngredientGroup(id + "_invalid", -1, "{}", machine, 20, ""), false);
            require(!Files.exists(invalidFile), "invalid first save cannot create source");

            for (String field : List.of("target", "operation", "affectChance")) {
                var advanced = JsonParser.parseString(before).getAsJsonObject();
                var advancedGroup = advanced.getAsJsonArray("requirements").get(0).getAsJsonObject();
                if (field.equals("nbt")) advancedGroup.add("nbt", JsonParser.parseString("{\"quality\":\"3\"}"));
                else {
                    var mod = advancedGroup.getAsJsonArray("modifiers").get(0).getAsJsonObject();
                    mod.add(field, JsonParser.parseString(field.equals("target") ? "\"modularmachinery:gas\"" : field.equals("operation") ? "1.5" : "\"true\""));
                }
                require(!IngredientGroupEditor.canEdit(advancedGroup), "advanced or malformed source is not flattened: " + field);
                Files.writeString(file, advanced.toString()); reload(menu);
                save(player, edit(id, valid), false);
                require(read(file).equals(advanced), "source protection is verified on server: " + field);
            }
            Files.writeString(file, before); reload(menu);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "CatalystDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(20, denied.getInventory(), menu.snapshot());
            save(denied, edit(id, valid), false);
            require(Files.readString(file).equals(before), "permission denied without source mutation");
            Modularmachinery.LOG.info("[MMCE][catalyst-editor-regression] PASS: authenticated wire/first save/disk/reload/snapshots, "
                    + "optional presence effects, zero-chance effect without consumption, one catalyst per batch, modifier targets/order/delete, metadata/tooltips/cancel, invalid/advanced/permission rejection");
        } finally {
            player.containerMenu = originalMenu;
            Files.deleteIfExists(file); Files.deleteIfExists(invalidFile); MachineReloadListener.reloadNow();
        }
    }

    private static void checkRuntime(ServerLevel level, BlockPos origin, MachineRecipe recipe, boolean consume) {
        BlockPos pos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        var before = level.getBlockState(pos);
        try {
            level.setBlock(pos, RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var inventory = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos)).getInventory();
            for (int i = 0; i < inventory.getSlots(); i++) inventory.setStackInSlot(i, ItemStack.EMPTY);
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("catalyst_fixture", "Catalyst", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var run = RecipeCraftingContext.withModifiers(recipe, List.of(), context);
            require(run.activeCatalystIndices().isEmpty() && run.effectiveDuration() == 100
                    && run.getRequirements().get(0).canStart(context)
                    && ((RequirementItem) run.getRequirements().get(1)).getAmount() == 2,
                    "absent catalyst is optional and applies no effect: active=" + run.activeCatalystIndices()
                            + ", duration=" + run.effectiveDuration() + ", base=" + recipe.recipeTime()
                            + ", canStart=" + run.getRequirements().get(0).canStart(context)
                            + ", output=" + ((RequirementItem) run.getRequirements().get(1)).getAmount());
            inventory.setStackInSlot(0, new ItemStack(consume ? Items.IRON_INGOT : Items.GOLD_INGOT));
            run = RecipeCraftingContext.withModifiers(recipe, List.of(), context);
            require(run.activeCatalystIndices().contains(0) && run.effectiveDuration() == 50
                    && ((RequirementItem) run.getRequirements().get(1)).getAmount() == 4
                    && ((RequirementEnergy) run.getRequirements().get(2)).getEnergyPerTick() == 100
                    && Math.abs(((RequirementFluid) run.getRequirements().get(3)).getChance() - 0.3F) < 1e-6,
                    "saved modifiers affect duration, energy, output amount and fluid probability when present");
            require(MachineTicker.consumeInputs(List.of(run.getRequirements().get(0)), context, 4)
                    && (consume ? inventory.getStackInSlot(0).isEmpty() : inventory.getStackInSlot(0).getCount() == 1),
                    "chance 1 consumes one catalyst per batch; chance 0 keeps catalyst while effect remains active");
            for (var item : List.of(Items.OAK_PLANKS, Items.BIRCH_PLANKS)) {
                inventory.setStackInSlot(0, new ItemStack(item));
                run = RecipeCraftingContext.withModifiers(recipe, List.of(), context);
                require(run.activeCatalystIndices().contains(0) && run.effectiveDuration() == 50
                        && MachineTicker.consumeInputs(List.of(run.getRequirements().get(0)), context, 4)
                        && (consume ? inventory.getStackInSlot(0).isEmpty() : inventory.getStackInSlot(0).getCount() == 1),
                        "tag catalyst accepts actual members and respects consume chance");
            }
            inventory.setStackInSlot(0, new ItemStack(Items.STONE));
            require(RecipeCraftingContext.withModifiers(recipe, List.of(), context).activeCatalystIndices().isEmpty(),
                    "unrelated item cannot activate a tag catalyst");
            var tagInput = new RequirementIngredientArray(List.of(new RequirementItem(IOType.INPUT, "tag:minecraft:planks", 2)), 1F);
            inventory.setStackInSlot(0, new ItemStack(Items.OAK_PLANKS));
            require(!tagInput.canStart(context) && !MachineTicker.consumeInputs(List.of(tagInput), context, 1)
                    && inventory.getStackInSlot(0).getCount() == 1, "tag input requires its actual declared quantity");
            inventory.setStackInSlot(0, new ItemStack(Items.OAK_PLANKS, 2));
            require(tagInput.canStart(context) && MachineTicker.consumeInputs(List.of(tagInput), context, 1)
                    && inventory.getStackInSlot(0).isEmpty(), "ordinary tag alternative consumes the declared quantity");
        } finally { level.setBlock(pos, before, Block.UPDATE_ALL); }
    }
    private static JsonObject modifier(String target, boolean output, int operation, float value, boolean chance) {
        var mod = CatalystEditor.modifier(); mod.addProperty("target", "modularmachinery:" + target);
        mod.addProperty("io", output ? "output" : "input"); mod.addProperty("operation", operation);
        mod.addProperty("multiplier", value); mod.addProperty("affectChance", chance); return mod;
    }
    private static PktMachineEditorIngredientGroup edit(String id, JsonObject group) { return new PktMachineEditorIngredientGroup(id, 0, group.toString(), "", 20, ""); }
    private static JsonObject read(java.nio.file.Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshotGroup(String snapshot, String id) {
        for (var machine : JsonParser.parseString(snapshot).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject().getAsJsonArray("requirements").get(0).getAsJsonObject();
        throw new AssertionError("Catalyst missing from snapshot");
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "reload and authoritative sync precede acknowledgement");
        } finally { buffer.release(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError("Catalyst editor: " + message); }
}
