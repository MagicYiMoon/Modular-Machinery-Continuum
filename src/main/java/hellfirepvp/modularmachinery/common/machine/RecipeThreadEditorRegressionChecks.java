/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
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

/** Thread properties travel through real saves and then the production factory scheduler. */
public final class RecipeThreadEditorRegressionChecks {
    private RecipeThreadEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "thread_editor_" + UUID.randomUUID().toString().replace("-", "");
        var file = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        var machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ThreadEditor")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        Block[] blocks = {RegistryBlocks.blockFactoryController.get(), RegistryBlocks.itemInputBus.get(),
                RegistryBlocks.itemOutputBus.get(), RegistryBlocks.parallelController.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        var positions = new BlockPos[blocks.length];
        try {
            Files.writeString(machineFile, "{\"parts\":[{\"x\":1,\"elements\":\"minecraft:air\"}]}");
            var source = new JsonObject(); source.addProperty("machine", id); source.addProperty("registryName", id);
            source.addProperty("recipeTime", 100); source.addProperty("authorNote", "preserve thread metadata");
            var requirements = new JsonArray(); requirements.add(BasicRequirementType.ITEM.create("minecraft:coal", false, 1, 1));
            requirements.add(BasicRequirementType.ITEM.create("minecraft:diamond", true, 1, 1)); source.add("requirements", requirements);
            Files.writeString(file, source.toString()); MachineReloadListener.reloadNow();
            var menu = new MenuMachineEditor(28, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
            var defaults = RecipeOptionsEditor.read(new JsonObject());
            require(defaults.threadName().isEmpty() && defaults.maxThreads() == -1, "source defaults in panel");
            var local = RecipeOptionsEditor.merge(source, "", 100, new RecipeOptionsEditor.Options(0, false, "draft", 0, true, true, List.of()));
            require(!source.has("threadName") && !read(file).has("threadName") && local.get("maxThreads").getAsInt() == 0,
                    "local thread draft never mutates source");
            save(player, edit(id, "", -1), true);
            require(!read(file).has("threadName") && !read(file).has("maxThreads"), "unchanged omitted defaults stay omitted");
            for (String name : List.of("Worker", "炉心 一线", " worker ", "x".repeat(256))) {
                save(player, edit(id, name, Integer.MAX_VALUE), true);
                var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
                require(recipe.threadName().equals(name) && recipe.maxThreads() == Integer.MAX_VALUE, "Unicode/space/length/int boundaries survive wire/reload");
                var expected = RecipeOptionsEditor.merge(source, "", 100, new RecipeOptionsEditor.Options(0, false, name, Integer.MAX_VALUE, true, true, List.of()));
                require(read(file).equals(expected), "only selected recipe policies changed");
                require(menu.snapshot().contains("\"threadName\":\"" + name + "\""), "authoritative snapshot carries thread name");
            }
            String saved = Files.readString(file);
            for (var bad : List.of(edit(id, "worker", -2), edit(id, "worker", Integer.MIN_VALUE),
                    edit(id, "bad\nname", 1), edit(id, "bad\tname", 1), edit(id, "bad\u007fname", 1))) save(player, bad, false);
            var oversized = edit(id, "x".repeat(257), 1);
            require(!PktMachineEditorRequest.process(player, new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(),
                    EditorSaveState.revision(menu.snapshot()), oversized)).result().success(), "server rejects oversized name before writing");
            require(Files.readString(file).equals(saved), "invalid thread settings do not write file");

            for (int i = 0; i < blocks.length; i++) {
                positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i, 0, 0); before[i] = level.getBlockState(positions[i]);
                level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            }
            var factory = (TileFactoryController) level.getBlockEntity(origin);
            var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[1])).getInventory();
            var parallel = (TileParallelController) level.getBlockEntity(positions[3]); parallel.setParallelism(2);
            var pattern = new MachinePattern(); for (int i = 1; i < blocks.length; i++) pattern.addPart(new MachinePattern.Part(i, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine(id, "Thread editor fixture", pattern); machine.setInternalParallelism(2); machine.setMaxParallelism(128);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            int batch = MachineTicker.machineParallelism(machine, context); require(batch == 4, "machine and controller batch ceiling");
            factory.setFactoryCeiling(3, batch); input.setStackInSlot(0, new ItemStack(Items.COAL, 64));
            var start = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            start.setAccessible(true);
            save(player, edit(id, "Worker", -1), true);
            start.invoke(null, factory, machine, context, List.of(MachineReloadListener.getRecipeLoader().getRecipe(id)));
            require(factory.getActiveThreadCount() == 0 && input.getStackInSlot(0).getCount() == 64, "named recipe cannot spill to dynamic pool");
            machine.addCoreThreadPreset("worker", List.of()); factory.syncCoreThreadPresets(machine.getCoreThreadPresets());
            start.invoke(null, factory, machine, context, List.of(MachineReloadListener.getRecipeLoader().getRecipe(id)));
            require(factory.getActiveThreadCount() == 0, "thread name comparison is case sensitive");
            machine.removeCoreThreadPreset("worker"); machine.addCoreThreadPreset("Worker", List.of("other_recipe"));
            factory.syncCoreThreadPresets(machine.getCoreThreadPresets());
            start.invoke(null, factory, machine, context, List.of(MachineReloadListener.getRecipeLoader().getRecipe(id)));
            require(factory.getActiveThreadCount() == 0, "matching name still respects core recipe set");
            machine.addCoreThreadPreset("Worker", List.of()); factory.syncCoreThreadPresets(machine.getCoreThreadPresets());
            save(player, edit(id, "Worker", 0), true);
            start.invoke(null, factory, machine, context, List.of(MachineReloadListener.getRecipeLoader().getRecipe(id)));
            require(factory.getActiveThreadCount() == 0 && input.getStackInSlot(0).getCount() == 64, "zero recipe limit disables core starts too");
            for (String name : List.of("Worker", "")) for (int limit : List.of(-1, 1, 8)) {
                factory.setFactoryCeiling(3, batch); save(player, edit(id, name, limit), true);
                var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id); int remaining = input.getStackInSlot(0).getCount();
                start.invoke(null, factory, machine, context, List.of(recipe));
                require(factory.getActiveThreadCount() == 1 && input.getStackInSlot(0).getCount() == remaining - batch,
                        "saved thread policy preserves full batch input");
                var active = factory.getFactoryRecipeThreadList().stream().filter(t -> t.hasActiveRecipe()).findFirst().orElseThrow();
                require(active.getParallelism() == batch && (name.isEmpty() || active.getThreadName().equals(name)), "selected thread retains batch and name");
                factory.setFactoryCeiling(3, batch * 2);
                require(factory.getAvailableParallelism() >= batch, "duplicate test retains spare batch budget");
                start.invoke(null, factory, machine, context, List.of(recipe));
                require(factory.getActiveThreadCount() == 1 && input.getStackInSlot(0).getCount() == remaining - batch,
                        "even positive source limits above one cannot duplicate an active recipe");
                save(player, edit(id, name, 0), true);
                require(active.hasActiveRecipe() && active.getParallelism() == batch, "saving zero limit does not discard paid run");
                active.abandonRecipe();
            }
            save(player, edit(id, "", 0), true);
            start.invoke(null, factory, machine, context, List.of(MachineReloadListener.getRecipeLoader().getRecipe(id)));
            require(factory.getActiveThreadCount() == 0, "zero disables unpinned factory recipe");
            save(player, edit(id, "", -1), true);
            require(read(file).get("threadName").getAsString().isEmpty() && read(file).get("maxThreads").getAsInt() == -1,
                    "clear name and reset limit retain explicit source fields");
            Modularmachinery.LOG.info("[MMCE][recipe-thread-editor-regression] PASS: real wire/save/reload/snapshot, Unicode/space/default/boundary/invalid settings, "
                    + "dynamic/core/name/case/recipe-set filtering, zero blocks both pools, distinct identities with spare budget, machine+controller batches, paid-run retention");
        } finally {
            player.containerMenu = oldMenu;
            for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(positions[i], before[i], Block.UPDATE_ALL);
            Files.deleteIfExists(file); Files.deleteIfExists(machineFile); MachineReloadListener.reloadNow();
        }
    }
    private static JsonObject read(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static PktMachineEditorRecipeOptions edit(String id, String name, int limit) {
        return new PktMachineEditorRecipeOptions(id, "", 100, 0, false, name, limit, true, true, List.of());
    }
    private static void save(FakePlayer player, Object mutation, boolean expected) {
        var menu = (MenuMachineEditor) player.containerMenu;
        var request = new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(), EditorSaveState.revision(menu.snapshot()), mutation);
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            PktMachineEditorRequest.encode(request, buffer); var reply = PktMachineEditorRequest.process(player, PktMachineEditorRequest.decode(buffer));
            require(reply.result().success() == expected, "save result: " + reply.result().message());
            if (expected) require(reply.snapshot() != null && menu.snapshot().equals(PktMachineEditorRequest.snapshot()), "reload precedes confirmation");
        } finally { buffer.release(); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError("Recipe thread editor: " + message); }
}
