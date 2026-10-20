/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
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
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.Files;
import java.util.*;

/** Save through the real editor wire and test the resulting reloadable fuel budget against inventories. */
public final class FuelEditorRegressionChecks {
    private FuelEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "fuel_editor_" + UUID.randomUUID().toString().replace("-", "");
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var invalidFile = file.resolveSibling(id + "_invalid.json");
        String machine = MachineReloadListener.getLoader().all().iterator().next().getRegistryName();
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "FuelEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        var menu = new MenuMachineEditor(22, player.getInventory(), PktMachineEditorRequest.snapshot());
        player.containerMenu = menu;
        try {
            var fuel = FuelEditor.create(1600, 1);
            save(player, new PktMachineEditorFuel(id, -1, fuel.toString(), machine, 100, "燃料编辑测试"), true);
            var source = read(file);
            require(source.get("recipeTime").getAsInt() == 100 && !requirement(source).has("amount")
                    && requirement(source).get("item").getAsString().equals("any:fuel"),
                    "first save uses source item any:fuel and separates burn budget from recipe duration and quantity");
            require(FuelEditor.canEdit(snapshot(menu, id)) && RequirementResourceSelection.key(snapshot(menu, id)).isEmpty()
                    && !RequirementResourceSelection.canReplace(snapshot(menu, id)), "fuel snapshot remains editable without resource replacement");
            checkRuntime(level, origin, id, 1600, false);
            source.addProperty("authorNote", "recipe note");
            var root = requirement(source);
            root.addProperty("authorNote", "fuel note"); root.addProperty("amount", 64);
            root.addProperty("item", "ANY:FUEL"); root.addProperty("triggerTime", 12); root.addProperty("triggerRepeatable", true);
            Files.writeString(file, source.toString()); reload(menu);
            String before = Files.readString(file);
            var draft = snapshot(menu, id).deepCopy(); draft.addProperty("time", 3200); draft.addProperty("chance", 0.25F);
            require(Files.readString(file).equals(before), "local time and chance changes are cancellable");
            save(player, edit(id, draft), true);
            source = read(file); root = requirement(source);
            require(source.get("authorNote").getAsString().equals("recipe note") && root.get("authorNote").getAsString().equals("fuel note")
                    && root.get("amount").getAsInt() == 64 && root.get("item").getAsString().equals("ANY:FUEL")
                    && root.get("time").getAsInt() == 3200 && root.get("chance").getAsFloat() == 0.25F
                    && root.get("triggerTime").getAsInt() == 12 && root.get("triggerRepeatable").getAsBoolean()
                    && source.get("registryName").getAsString().equals(id), "source metadata, ignored amount, selector spelling, triggers and recipe ID preserved");
            var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            var synced = (RequirementFuel) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of())).requirements().get(0);
            require(synced.getTime() == 3200 && synced.getChance() == 0.25F && synced.getTriggerTime() == 12
                    && synced.isTriggerRepeatable(), "fuel snapshots retain edited values and source trigger settings");
            root.remove("triggerTime"); root.remove("triggerRepeatable"); Files.writeString(file, source.toString()); reload(menu);
            for (int time : List.of(100, 21600, 1600)) {
                save(player, edit(id, FuelEditor.create(time, time == 1600 ? 0 : 1)), true);
                checkRuntime(level, origin, id, time, time == 1600);
            }
            before = Files.readString(file);
            var valid = snapshot(menu, id).deepCopy();
            for (String key : List.of("time", "chance")) {
                var values = key.equals("time") ? List.of("0", "-1", "1.5", "2000000001", "2147483648", "\"1600\"", "null")
                        : List.of("-1", "1.1", "1e100", "\"NaN\"", "\"Infinity\"", "\"0.5\"", "null");
                for (String value : values) {
                    var bad = valid.deepCopy(); bad.add(key, JsonParser.parseString(value)); save(player, edit(id, bad), false);
                }
            }
            var bad = valid.deepCopy(); bad.remove("time"); save(player, edit(id, bad), false);
            bad = valid.deepCopy(); bad.addProperty("io-type", "output"); save(player, edit(id, bad), false);
            for (String item : List.of("minecraft:coal", "tag:minecraft:planks", "any:missing", "missing:item")) {
                bad = valid.deepCopy(); bad.addProperty("item", item); save(player, edit(id, bad), false);
            }
            for (String key : List.of("nbt", "nbt-display", "consumeDurability")) {
                bad = valid.deepCopy(); bad.add(key, key.equals("consumeDurability") ? new JsonPrimitive(0) : new JsonObject());
                save(player, edit(id, bad), false);
            }
            for (String payload : List.of("{}", "[]", "{")) save(player, new PktMachineEditorFuel(id, 0, payload, "", 20, ""), false);
            for (int index : List.of(-2, 511, 512)) save(player, new PktMachineEditorFuel(id, index, valid.toString(), "", 20, ""), false);
            save(player, new ModularmachineryNetwork.PktMachineEditorEdit(id, 0, 1, 1, "minecraft:coal"), false);
            save(player, new ModularmachineryNetwork.PktMachineEditorEdit(id, 0, 1, 1, ""), false);
            save(player, new PktMachineEditorDurability(id, 0, DurabilityEditor.create("minecraft:iron_pickaxe", 1, 10, 1).toString(), "", 20, ""), false);
            var group = IngredientGroupEditor.empty(); group.getAsJsonArray("items").add(IngredientGroupEditor.candidate("minecraft:coal"));
            save(player, new PktMachineEditorIngredientGroup(id, 0, group.toString(), "", 20, ""), false);
            require(Files.readString(file).equals(before), "invalid data and alternative editor routes never change fuel source");
            save(player, new PktMachineEditorFuel(id + "_invalid", -1, "{}", machine, 20, ""), false);
            require(!Files.exists(invalidFile), "invalid first request creates no file");
            for (String mode : List.of("nbt", "nbt-display", "consumeDurability", "ordinary", "durability", "group")) {
                var advanced = JsonParser.parseString(before).getAsJsonObject();
                var reqs = advanced.getAsJsonArray("requirements"); var req = requirement(advanced);
                switch (mode) {
                    case "ordinary" -> reqs.set(0, BasicRequirementType.ITEM.create("minecraft:coal", false, 1, 1));
                    case "durability" -> reqs.set(0, DurabilityEditor.create("minecraft:iron_pickaxe", 1, 10, 1));
                    case "group" -> reqs.set(0, group);
                    case "consumeDurability" -> req.addProperty(mode, 0);
                    default -> req.add(mode, new JsonObject());
                }
                require(!FuelEditor.canEdit(requirement(advanced)), "advanced/nonfuel source cannot be flattened: " + mode);
                Files.writeString(file, advanced.toString()); reload(menu); save(player, edit(id, valid), false);
                require(read(file).equals(advanced), "server checks actual source, not just the client snapshot: " + mode);
            }
            Files.writeString(file, before); reload(menu);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "FuelDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(22, denied.getInventory(), menu.snapshot());
            save(denied, edit(id, valid), false);
            require(Files.readString(file).equals(before), "permissions checked before file mutation");
            var stale = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), "stale", edit(id, valid));
            require(!PktMachineEditorRequest.process(player, stale).result().success(), "stale fuel document rejected");
            stale = new PktMachineEditorRequest(menu.containerId + 1, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), edit(id, valid));
            require(!PktMachineEditorRequest.process(player, stale).result().success() && Files.readString(file).equals(before), "closed/wrong fuel window rejected");
            Modularmachinery.LOG.info("[MMCE][fuel-editor-regression] PASS: authenticated wire/first save/source/reload/snapshots, "
                    + "mixed fuels/batch/overburn/container/zero chance/shortfall/shared inventory, metadata/triggers/cancel, "
                    + "malformed/advanced/type/permission/stale revision/window rejection");
        } finally {
            player.containerMenu = oldMenu; Files.deleteIfExists(file); Files.deleteIfExists(invalidFile); MachineReloadListener.reloadNow();
        }
    }

    private static void checkRuntime(ServerLevel level, BlockPos origin, String id, int time, boolean noCharge) {
        var fuel = (RequirementFuel) MachineReloadListener.getRecipeLoader().getRecipe(id).requirements().get(0);
        require(fuel.getTime() == time && fuel.getChance() == (noCharge ? 0 : 1), "saved budget and probability actually decoded");
        BlockPos pos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        var old = level.getBlockState(pos);
        try {
            level.setBlock(pos, RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var inventory = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos)).getInventory();
            for (int i = 0; i < inventory.getSlots(); i++) inventory.setStackInSlot(i, ItemStack.EMPTY);
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("fuel_editor_fixture", "Fuel editor", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            require(!fuel.canStart(context), "even zero-chance fuel requires an available burn budget at start");
            inventory.setStackInSlot(0, new ItemStack(Items.STONE)); require(!fuel.canStart(context), "nonfuel cannot start recipe");
            if (time == 21600) {
                inventory.setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));
                require(!fuel.canStart(context) && !MachineTicker.consumeInputs(List.of(fuel), context, 1)
                        && inventory.getStackInSlot(0).is(Items.LAVA_BUCKET), "shortfall retains container fuel intact");
                inventory.setStackInSlot(1, new ItemStack(Items.COAL));
                require(fuel.canStart(context) && MachineTicker.consumeInputs(List.of(fuel), context, 1)
                        && inventory.getStackInSlot(0).is(Items.BUCKET) && inventory.getStackInSlot(1).isEmpty(), "mixed container fuel leaves empty bucket");
            } else if (time == 100) {
                inventory.setStackInSlot(0, new ItemStack(Items.COAL));
                require(MachineTicker.consumeInputs(List.of(fuel), context, 1) && inventory.getStackInSlot(0).isEmpty()
                        && !fuel.canStart(context), "overburn discards unused burn time rather than storing credit");
            } else {
                inventory.setStackInSlot(0, new ItemStack(Items.COAL)); inventory.setStackInSlot(1, new ItemStack(Items.CHARCOAL));
                require(fuel.canStart(context) && fuel.getMaxParallelism(context, 8, List.of(fuel)) == 2
                        && inventory.getStackInSlot(0).getCount() == 1 && inventory.getStackInSlot(1).getCount() == 1, "simulation sums mixed fuel budgets without mutation");
                require(MachineTicker.consumeInputs(List.of(fuel), context, 2)
                        && (noCharge ? inventory.getStackInSlot(0).getCount() == 1 && inventory.getStackInSlot(1).getCount() == 1
                        : inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty()), "saved chance and batch affect actual fuel payment");
                inventory.setStackInSlot(0, new ItemStack(Items.COAL)); inventory.setStackInSlot(1, ItemStack.EMPTY);
                require(fuel.getMaxParallelism(context, 8, List.of(fuel)) == 1, "zero chance never increases start capacity");
                if (!noCharge) {
                    require(!MachineTicker.consumeInputs(List.of(fuel), context, 2) && inventory.getStackInSlot(0).getCount() == 1,
                            "batch shortfall does not partially consume fuel");
                    var material = new RequirementItem(IOType.INPUT, "minecraft:coal", 1);
                    require(fuel.getMaxParallelism(context, 8, List.of(fuel, material)) == 0
                            && !MachineTicker.consumeInputs(List.of(fuel, material), context, 1) && inventory.getStackInSlot(0).getCount() == 1,
                            "fuel and an explicit input cannot spend the same coal twice");
                }
            }
        } finally { level.setBlock(pos, old, Block.UPDATE_ALL); }
    }
    private static JsonObject requirement(JsonObject source) { return source.getAsJsonArray("requirements").get(0).getAsJsonObject(); }
    private static JsonObject read(java.nio.file.Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static PktMachineEditorFuel edit(String id, JsonObject fuel) { return new PktMachineEditorFuel(id, 0, fuel.toString(), "", 20, ""); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return requirement(recipe.getAsJsonObject());
        throw new AssertionError("Fuel not found in editor snapshot");
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
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError("Fuel editor: " + message); }
}
