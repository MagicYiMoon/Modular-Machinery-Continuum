/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
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
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Real option saves followed by search ordering and ordinary/factory per-tick failure lifecycle checks. */
public final class RecipeOptionsEditorRegressionChecks {
    private RecipeOptionsEditorRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        String id = "recipe_options_" + UUID.randomUUID().toString().replace("-", ""), machine = id + "_machine", other = id + "_other";
        var dir = ModDataHolder.INSTANCE.getRecipeDirectory().toPath();
        var file = dir.resolve(id + ".json"); var competitor = dir.resolve(id + "_competitor.json"); var shared = dir.resolve(id + "_shared.json");
        var machines = ModDataHolder.INSTANCE.getMachineryDirectory().toPath();
        var machineFile = machines.resolve(machine + ".json"); var otherFile = machines.resolve(other + ".json");
        var player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "RecipeOptions")) {
            @Override public boolean hasPermissions(int permissionLevel) { return true; }
        };
        var oldMenu = player.containerMenu;
        try {
            Files.writeString(machineFile, "{\"parts\":[{\"x\":1,\"elements\":\"minecraft:air\"}]}");
            Files.writeString(otherFile, Files.readString(machineFile));
            var source = new JsonObject(); source.addProperty("machine", machine); source.addProperty("registryName", id); source.addProperty("recipeTime", 20);
            source.addProperty("authorNote", "recipe metadata"); source.addProperty("parallelized", false);
            source.addProperty("maxThreads", 1); source.addProperty("threadName", "worker"); source.addProperty("loadJEI", false);
            var tooltips = new JsonArray(); tooltips.add("preserve tooltips"); source.add("tooltips", tooltips);
            var requirements = new JsonArray(); var input = BasicRequirementType.ITEM.create("minecraft:coal", false, 1, 1);
            input.add("nbt", JsonParser.parseString("{\"quality\":\">=3\"}")); input.addProperty("authorNote", "input metadata");
            requirements.add(input); requirements.add(FluidTickEditor.create("minecraft:water", false, 10)); source.add("requirements", requirements);
            Files.writeString(file, source.toString()); var rival = source.deepCopy(); rival.addProperty("registryName", id + "_competitor");
            Files.writeString(competitor, rival.toString()); MachineReloadListener.reloadNow();
            var menu = new MenuMachineEditor(26, player.getInventory(), PktMachineEditorRequest.snapshot()); player.containerMenu = menu;
            var defaultOptions = RecipeOptionsEditor.read(snapshot(menu, id));
            require(defaultOptions.priority() == 0 && !defaultOptions.cancel(), "omitted keys retain source defaults in editor");
            var draft = RecipeOptionsEditor.merge(source, "draft", 80, new RecipeOptionsEditor.Options(-5, true, "worker", 1, false, false, List.of("preserve tooltips")));
            require(!source.has("priority") && !read(file).has("priority") && draft.get("priority").getAsInt() == -5, "local apply/cancel does not mutate source");
            var detached = MachineReloadListener.getRecipeLoader().getRecipeSource(id); detached.addProperty("authorNote", "detached");
            require(MachineReloadListener.getRecipeLoader().getRecipeSource(id).equals(source), "loaded source accessor returns defensive copy");
            save(player, edit(id, "&6高级配方", 80, -5, true), true);
            require(read(file).equals(RecipeOptionsEditor.merge(source, "&6高级配方", 80, new RecipeOptionsEditor.Options(-5, true, "worker", 1, false, false, List.of("preserve tooltips")))),
                    "single save changes only presentation, duration, priority and cancellation policy");
            var loaded = MachineReloadListener.getRecipeLoader().getRecipe(id);
            require(loaded.priority() == -5 && loaded.cancelOnPerTickFailure() && loaded.recipeTime() == 80
                    && loaded.displayName().equals("§6高级配方") && loaded.maxThreads() == 1 && loaded.threadName().equals("worker")
                    && !loaded.parallelized() && !loaded.loadJEI(), "options reload without changing other recipe policies");
            var synced = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(loaded, Map.of()));
            require(synced.priority() == -5 && synced.cancelOnPerTickFailure() && synced.recipeTime() == 80, "client codec carries saved options");
            checkOrder(machine, id, id + "_competitor", true); checkLifecycle(level, origin, id, true);
            save(player, edit(id, "", 40, 5, false), true);
            require(!read(file).has("displayName") && !MachineReloadListener.getRecipeLoader().getRecipe(id).cancelOnPerTickFailure(), "clear label and disable cancellation");
            checkOrder(machine, id, id + "_competitor", false); checkLifecycle(level, origin, id, false);
            for (int priority : List.of(Integer.MIN_VALUE, Integer.MAX_VALUE, 0)) {
                save(player, edit(id, "", 40, priority, false), true);
                require(MachineReloadListener.getRecipeLoader().getRecipe(id).priority() == priority, "full signed integer priority survives wire/reload");
                if (priority != 0) checkOrder(machine, id, id + "_competitor", priority < 0);
            }
            String before = Files.readString(file);
            for (int ticks : List.of(0, -1, 2_000_000_001)) save(player, edit(id, "bad", ticks, 0, false), false);
            var oversized = edit(id, "x".repeat(201), 20, 0, false);
            require(!PktMachineEditorRequest.process(player, new PktMachineEditorRequest(menu.containerId, UUID.randomUUID(),
                    EditorSaveState.revision(menu.snapshot()), oversized)).result().success(), "oversized label rejected server-side");
            save(player, edit(id + "_missing", "bad", 20, 0, false), false);
            require(Files.readString(file).equals(before), "invalid presentation/duration/id never writes source");
            var external = read(file); external.addProperty("authorNote", "external edit"); Files.writeString(file, external.toString());
            save(player, edit(id, "bad", 20, -100, true), false);
            require(read(file).equals(external), "full source comparison rejects unreloaded changes outside edited fields");
            reload(menu); save(player, edit(id, "reloaded", 30, -100, false), true);
            require(read(file).get("authorNote").getAsString().equals("external edit"), "external metadata retained after reload");
            var sharedSource = source.deepCopy(); var owners = new JsonArray(); owners.add(machine); owners.add(other);
            sharedSource.add("machine", owners); sharedSource.addProperty("registryName", id + "_shared"); Files.writeString(shared, sharedSource.toString()); reload(menu);
            save(player, edit(id + "_shared_sub_0", "shared", 60, -20, true), true);
            var otherRecipe = MachineReloadListener.getRecipeLoader().getRecipe(id + "_shared_sub_1");
            require(otherRecipe.priority() == -20 && otherRecipe.cancelOnPerTickFailure() && otherRecipe.recipeTime() == 60
                    && read(shared).get("machine").equals(owners), "shared source saves all machine variants without rewriting owner list");
            save(player, new PktMachineEditorRecipeOptions(id + "_shared_sub_0", "shared", 60, -20, true, "shared core", 0, false, false, List.of("preserve tooltips")), true);
            otherRecipe = MachineReloadListener.getRecipeLoader().getRecipe(id + "_shared_sub_1");
            require(otherRecipe.threadName().equals("shared core") && otherRecipe.maxThreads() == 0 && otherRecipe.priority() == -20
                    && otherRecipe.cancelOnPerTickFailure() && read(shared).get("machine").equals(owners), "thread edits affect all shared variants without losing prior options");
            before = Files.readString(file);
            var denied = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "OptionsDenied")) {
                @Override public boolean hasPermissions(int permissionLevel) { return false; }
            };
            denied.containerMenu = new MenuMachineEditor(26, denied.getInventory(), menu.snapshot()); save(denied, edit(id, "bad", 20, 0, true), false);
            for (boolean wrongWindow : List.of(false, true)) require(!PktMachineEditorRequest.process(player,
                    new PktMachineEditorRequest(wrongWindow ? 27 : 26, UUID.randomUUID(), wrongWindow
                            ? EditorSaveState.revision(menu.snapshot()) : "stale", edit(id, "bad", 20, 0, true))).result().success(), "stale window/revision rejected");
            String readonly = MachineReloadListener.getRecipeLoader().getRecipeNames().stream()
                    .filter(name -> !MachineReloadListener.getRecipeLoader().isSourceEditable(name)).findFirst().orElseThrow();
            require(MachineReloadListener.getRecipeLoader().getRecipeSource(readonly) == null, "generated sources do not expose editable document");
            save(player, edit(readonly, "bad", 20, 0, true), false);
            require(Files.readString(file).equals(before), "denied/stale/generated writes leave file intact");
            save(player, edit(id + "_competitor", "", 20, 0, false), true);
            require(!read(competitor).has("priority") && !read(competitor).has("cancelIfPerTickFails"), "unchanged omitted defaults stay omitted");
            Modularmachinery.LOG.info("[MMCE][recipe-options-editor-regression] PASS: real wire/source/reload/snapshots, ascending priority/extreme integers, "
                    + "ordinary+factory cancel/pause/resume/no input refund, atomic name/time/options save, metadata/NBT/thread policies/shared owners/default omission/cancel, "
                    + "external-source/invalid/permission/window/revision/read-only rejection");
        } finally {
            player.containerMenu = oldMenu;
            for (var path : List.of(file, competitor, shared, machineFile, otherFile)) Files.deleteIfExists(path);
            MachineReloadListener.reloadNow();
        }
    }
    private static void checkOrder(String machine, String id, String competitor, boolean first) {
        var ids = MachineReloadListener.getRecipeLoader().getByMachine().get(machine).stream().map(MachineRecipe::registryName).toList();
        require((ids.indexOf(id) < ids.indexOf(competitor)) == first, "source-style ascending search priority");
    }
    private static void checkLifecycle(ServerLevel level, BlockPos origin, String id, boolean cancel) throws Exception {
        var recipe = MachineReloadListener.getRecipeLoader().getRecipe(id);
        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.itemInputBus.get(), RegistryBlocks.fluidInputHatch.get(), RegistryBlocks.blockFactoryController.get()};
        var saved = new net.minecraft.world.level.block.state.BlockState[4]; var positions = new BlockPos[4];
        try {
            for (int i = 0; i < 4; i++) {
                positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i, 0, 0); saved[i] = level.getBlockState(positions[i]);
                level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            }
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air")); pattern.addPart(new MachinePattern.Part(2, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine(recipe.machine(), "Options fixture", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var inventory = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[1])).getInventory();
            var tank = (TileFluidTank) level.getBlockEntity(positions[2]);
            var controller = (TileController) level.getBlockEntity(origin); var factory = (TileFactoryController) level.getBlockEntity(positions[3]);
            var advance = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class); advance.setAccessible(true);
            var tick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class); tick.setAccessible(true);
            for (boolean useFactory : List.of(false, true)) {
                var coal = new ItemStack(Items.COAL); coal.getOrCreateTag().putInt("quality", 3); inventory.setStackInSlot(0, coal);
                tank.getTank().setFluid(FluidStack.EMPTY);
                require(MachineTicker.consumeInputs(recipe.requirements(), context, 1) && inventory.getStackInSlot(0).isEmpty(), "start actually consumes NBT-matched input");
                var thread = new FactoryRecipeThread(factory);
                if (useFactory) {
                    thread.beginRecipe(id, recipe.recipeTime(), 1); tick.invoke(null, thread, factory, machine, context, List.of(recipe));
                    require(thread.hasActiveRecipe() != cancel && thread.getRecipeTicks() == 0, "factory observes saved failure policy");
                } else {
                    controller.beginRecipe(id, recipe.recipeTime(), 1); advance.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                    require(controller.hasActiveRecipe() != cancel && controller.getRecipeTicks() == 0, "ordinary controller observes saved failure policy");
                }
                require(inventory.getStackInSlot(0).isEmpty(), "per-tick failure never refunds spent start input");
                if (!cancel) {
                    tank.getTank().setFluid(new FluidStack(Fluids.WATER, 20));
                    if (useFactory) { tick.invoke(null, thread, factory, machine, context, List.of(recipe)); require(thread.getRecipeTicks() == 1, "factory resumes paused recipe"); }
                    else { advance.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe)); require(controller.getRecipeTicks() == 1, "ordinary controller resumes paused recipe"); }
                    require(tank.getTank().getFluidAmount() == 10, "resume pays one advancing fluid tick");
                }
            }
        } finally { for (int i = 0; i < 4; i++) if (saved[i] != null) level.setBlock(positions[i], saved[i], Block.UPDATE_ALL); }
    }
    private static JsonObject read(Path file) throws Exception { return JsonParser.parseString(Files.readString(file)).getAsJsonObject(); }
    private static PktMachineEditorRecipeOptions edit(String id, String name, int ticks, int priority, boolean cancel) {
        return new PktMachineEditorRecipeOptions(id, name, ticks, priority, cancel, "worker", 1, false, false, List.of("preserve tooltips"));
    }
    private static void reload(MenuMachineEditor menu) { MachineReloadListener.reloadNow(); menu.setSnapshot(PktMachineEditorRequest.snapshot()); }
    private static JsonObject snapshot(MenuMachineEditor menu, String id) {
        for (var machine : JsonParser.parseString(menu.snapshot()).getAsJsonObject().getAsJsonArray("machines"))
            for (var recipe : machine.getAsJsonObject().getAsJsonArray("recipes"))
                if (id.equals(recipe.getAsJsonObject().get("name").getAsString())) return recipe.getAsJsonObject();
        throw new AssertionError("Missing options editor snapshot");
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
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError("Recipe options: " + message); }
}
