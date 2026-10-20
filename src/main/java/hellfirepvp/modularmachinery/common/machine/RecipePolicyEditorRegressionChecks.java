/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import io.netty.buffer.Unpooled;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Saved recipe flags govern real ordinary/factory batches, including after a mid-run edit. */
public final class RecipePolicyEditorRegressionChecks {
    private RecipePolicyEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "policy_editor_" + UUID.randomUUID().toString().replace("-", ""), other = id + "_other";
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var machineDir = ModDataHolder.INSTANCE.getMachineryDirectory().toPath();
        var machineFile = machineDir.resolve(id + ".json"); var otherFile = machineDir.resolve(other + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "PolicyEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        Block[] blocks = {RegistryBlocks.blockFactoryController.get(), RegistryBlocks.blockController.get(),
                RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(), RegistryBlocks.energyInputHatch.get(), RegistryBlocks.parallelController.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length]; var positions = new BlockPos[blocks.length];
        try {
            Files.writeString(machineFile, "{\"parallelism\":2,\"max-parallelism\":128,\"parts\":[{\"x\":1,\"elements\":\"minecraft:air\"}]}");
            Files.writeString(otherFile, Files.readString(machineFile));
            var source = new JsonObject(); source.addProperty("machine", id); source.addProperty("registryName", id); source.addProperty("recipeTime", 2);
            source.addProperty("authorNote", "retain policy metadata"); source.addProperty("priority", -7);
            source.addProperty("maxThreads", 2); source.addProperty("threadName", ""); source.addProperty("cancelIfPerTickFails", true);
            var requirements = new JsonArray(); var inputJson = BasicRequirementType.ITEM.create("minecraft:coal", false, 1, 1);
            inputJson.addProperty("authorNote", "retain input metadata"); requirements.add(inputJson);
            requirements.add(BasicRequirementType.ITEM.create("minecraft:diamond", true, 1, 1));
            requirements.add(BasicRequirementType.ENERGY.create("", false, 10, 1)); source.add("requirements", requirements);
            Files.writeString(file, source.toString()); MachineReloadListener.reloadNow();
            var menu = new MenuMachineEditor(30, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
            var initial = RecipeOptionsEditor.read(snapshot(menu, id)); require(initial.parallelized() && initial.loadJEI(), "omitted flags default to true in editor");
            save(player, edit(id, true, true), true);
            require(!read(file).has("parallelized") && !read(file).has("loadJEI"), "unchanged true defaults remain omitted");
            var local = RecipeOptionsEditor.merge(source, "", 2, new RecipeOptionsEditor.Options(-7, true, "", 2, false, false, List.of()));
            require(!read(file).has("parallelized") && !source.has("parallelized") && !local.get("loadJEI").getAsBoolean(), "local policy draft does not save or mutate source");

            for (int i = 0; i < blocks.length; i++) {
                positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i, 0, 0); before[i] = level.getBlockState(positions[i]);
                level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            }
            var factory = (TileFactoryController) level.getBlockEntity(positions[0]); factory.setMachineName(id);
            var controller = (TileController) level.getBlockEntity(positions[1]); controller.setMachineName(id);
            var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[2])).getInventory();
            var output = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[3])).getInventory();
            var energy = ((TileEnergyHatch) level.getBlockEntity(positions[4])).getEnergyStorage(); energy.receiveEnergy(10000, false);
            ((TileParallelController) level.getBlockEntity(positions[5])).setParallelism(2);
            var start = MachineTicker.class.getDeclaredMethod("tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var advance = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var factoryStart = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            var factoryTick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            for (var method : List.of(start, advance, factoryStart, factoryTick)) method.setAccessible(true);
            for (boolean useFactory : List.of(false, true)) for (boolean batch : List.of(false, true)) for (boolean shown : List.of(false, true)) {
                save(player, edit(id, batch, shown), true);
                var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
                var synced = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()));
                require(recipe.parallelized() == batch && recipe.loadJEI() == shown && synced.parallelized() == batch && synced.loadJEI() == shown,
                        "four combinations survive real wire/save/reload/client codec");
                var options = RecipeOptionsEditor.read(snapshot(menu, id)); require(options.parallelized() == batch && options.loadJEI() == shown,
                        "hidden recipe stays editable with its saved flags");
                var saved = read(file); require(saved.get("requirements").equals(requirements) && saved.get("authorNote").equals(source.get("authorNote"))
                        && saved.get("priority").getAsInt() == -7 && saved.get("maxThreads").getAsInt() == 2 && saved.get("cancelIfPerTickFails").getAsBoolean(),
                        "policy saves preserve requirements and other advanced settings");
                var machine = MachineReloadListener.getLoader().findByName(id);
                var context = new MachineContext(level, useFactory ? positions[0] : positions[1], Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
                context.overrideComponentPositions(List.of(positions[2], positions[3], positions[4], positions[5]));
                int ceiling = MachineTicker.machineParallelism(machine, context), expected = batch ? ceiling : 1;
                require(ceiling == 4, "machine and parallel controller retain shared batch capacity");
                input.setStackInSlot(0, new ItemStack(Items.COAL, 64)); for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, ItemStack.EMPTY);
                int power = energy.getEnergyStored(); FactoryRecipeThread thread = null;
                if (useFactory) {
                    factory.setFactoryCeiling(3, ceiling); factoryStart.invoke(null, factory, machine, context, List.of(recipe));
                    thread = factory.getFactoryRecipeThreadList().stream().filter(FactoryRecipeThread::hasActiveRecipe).findFirst().orElseThrow();
                    factoryStart.invoke(null, factory, machine, context, List.of(recipe));
                    require(thread.getParallelism() == expected && factory.getActiveThreadCount() == 1, "factory saved batch flag and unique identity");
                } else {
                    start.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                    require(controller.hasActiveRecipe() && controller.getParallelism() == expected, "ordinary controller saved batch flag");
                }
                require(input.getStackInSlot(0).getCount() == 64 - expected, "hidden/visible recipe pays correct batch input");
                save(player, edit(id, !batch, !shown), true);
                recipe = MachineReloadListener.getRecipeLoader().getRecipe(id); machine = MachineReloadListener.getLoader().findByName(id);
                require(useFactory ? thread.getParallelism() == expected : controller.getParallelism() == expected, "mid-run flag edit retains paid batch");
                for (int tick = 0; tick < 3; tick++) {
                    if (useFactory) { if (thread.hasActiveRecipe()) factoryTick.invoke(null, thread, factory, machine, context, List.of(recipe)); }
                    else if (controller.hasActiveRecipe()) advance.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                }
                int diamonds = 0; for (int slot = 0; slot < output.getSlots(); slot++) if (output.getStackInSlot(slot).is(Items.DIAMOND)) diamonds += output.getStackInSlot(slot).getCount();
                require(!(useFactory ? thread.hasActiveRecipe() : controller.hasActiveRecipe()) && diamonds == expected
                        && energy.getEnergyStored() == power - 2 * 10 * expected && input.getStackInSlot(0).getCount() == 64 - expected,
                        "mid-run policy edit preserves actual batch output, duration and per-tick energy");
            }
            var shared = read(file); var owners = new JsonArray(); owners.add(id); owners.add(other); shared.add("machine", owners);
            Files.writeString(file, shared.toString()); reload(menu); save(player, edit(id + "_sub_0", false, false), true);
            var sibling = MachineReloadListener.getRecipeLoader().getRecipe(id + "_sub_1");
            require(!sibling.parallelized() && !sibling.loadJEI() && read(file).get("machine").equals(owners), "shared file flags update all machine variants");
            save(player, edit(id + "_sub_0", true, true), true);
            require(read(file).get("parallelized").getAsBoolean() && read(file).get("loadJEI").getAsBoolean(), "reset preserves explicit boolean keys");
            String beforeSave = Files.readString(file); shared = read(file); shared.addProperty("authorNote", "external policy edit"); Files.writeString(file, shared.toString());
            save(player, edit(id + "_sub_0", false, false), false); require(read(file).equals(shared), "unreloaded external source is protected");
            Files.writeString(file, beforeSave); reload(menu);
            String readonly = MachineReloadListener.getRecipeLoader().getRecipeNames().stream()
                    .filter(name -> !MachineReloadListener.getRecipeLoader().isSourceEditable(name)).findFirst().orElseThrow();
            save(player, edit(readonly, false, false), false);
            require(Files.readString(file).equals(beforeSave), "read-only policy writes cannot alter editable source");
            Modularmachinery.LOG.info("[MMCE][recipe-policy-editor-regression] PASS: four flags combinations over real wire/disk/reload/editor/client snapshots, "
                    + "ordinary+factory batch input/output/energy, hidden recipes still run and stay editable, mid-run paid batches retained, "
                    + "defaults/reset/metadata/shared owners/local cancel/external-source/read-only protection; JEI runtime requires client verification");
        } finally {
            player.containerMenu = oldMenu;
            for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(positions[i], before[i], Block.UPDATE_ALL);
            for (var path : List.of(file, machineFile, otherFile)) Files.deleteIfExists(path); MachineReloadListener.reloadNow();
        }
    }
    private static JsonObject read(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes")) if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject();
        throw new AssertionError("Missing policy editor snapshot");
    }
    private static PktMachineEditorRecipeOptions edit(String id, boolean batch, boolean shown) {
        return new PktMachineEditorRecipeOptions(id, "", 2, -7, true, "", 2, batch, shown, List.of());
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation), buffer);
            var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "confirmation follows completed reload");
        } finally { buffer.release(); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError("Recipe policy editor: " + message); }
}
