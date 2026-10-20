/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Exercises defaults through disk loaders, actual startup/finish IO and remote snapshots. */
public final class GlobalDefaultsRegressionChecks {
    private GlobalDefaultsRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        // ConfigValue.set on Forge's autosaving file starts an asynchronous file-watch reload.
        // Use a temporary in-memory backing config, preserving the real file and all its values.
        var backingField = ForgeConfigSpec.class.getDeclaredField("childConfig"); backingField.setAccessible(true);
        var originalConfig = (com.electronwill.nightconfig.core.CommentedConfig) backingField.get(Config.SPEC);
        var testConfig = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        Config.SPEC.correct(testConfig); Config.SPEC.setConfig(testConfig);
        var changed = new LinkedHashMap<ForgeConfigSpec.ConfigValue<?>, Object>();
        String id = "global_defaults_" + UUID.randomUUID().toString().replace("-", "");
        Path machineFile = ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(id + ".json");
        Path recipeFile = ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(id + ".json");
        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get(),
                RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(), RegistryBlocks.parallelController.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        try {
            for (int i = 0; i < blocks.length; i++) {
                var pos = origin.offset(i, 0, 0); before[i] = level.getBlockState(pos);
                level.setBlock(pos, blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            }
            Files.writeString(machineFile, "{\"parts\":[{\"x\":1,\"elements\":\"minecraft:air\"}]}");
            var source = JsonParser.parseString("""
                    {"recipeTime":2,"requirements":[
                      {"type":"modularmachinery:item","io-type":"input","item":"minecraft:coal","amount":1},
                      {"type":"modularmachinery:item","io-type":"output","item":"minecraft:diamond","amount":1}]}
                    """).getAsJsonObject();
            source.addProperty("machine", id); source.addProperty("registryName", id);
            Files.writeString(recipeFile, source.toString());
            var controller = (TileController) level.getBlockEntity(origin); controller.setMachineName(id);
            var factory = (TileFactoryController) level.getBlockEntity(origin.offset(1, 0, 0)); factory.setMachineName(id);
            var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(origin.offset(2, 0, 0))).getInventory();
            var output = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(origin.offset(3, 0, 0))).getInventory();
            var parallel = (TileParallelController) level.getBlockEntity(origin.offset(4, 0, 0)); parallel.setParallelism(4);
            var start = MachineTicker.class.getDeclaredMethod("tryStart", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var tick = MachineTicker.class.getDeclaredMethod("advance", TileController.class, MachineContext.class, List.class, RecipeCraftingContext.class);
            var fstart = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            var ftick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class, TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            for (var method : List.of(start, tick, fstart, ftick)) method.setAccessible(true);
            for (boolean machineBatch : List.of(false, true)) for (boolean recipeBatch : List.of(false, true)) {
                set(changed, "MACHINE_PARALLEL_DEFAULT", machineBatch);
                set(changed, "RECIPE_PARALLEL_DEFAULT", recipeBatch);
                set(changed, "MAX_MACHINE_PARALLELISM", 3);
                set(changed, "FACTORY_MAX_THREADS", 2);
                MachineReloadListener.reloadNow();
                var machines = MachineReloadListener.getLoader(); var recipes = MachineReloadListener.getRecipeLoader();
                var machine = machines.findByName(id); var recipe = recipes.getRecipe(id);
                require(machine != null && recipe != null, "default fixtures load");
                require(machine.isParallelizable() == machineBatch && machine.getMaxParallelism() == 3
                        && machine.getMaxThreads() == 2 && recipe.parallelized() == recipeBatch, "server config defaults apply to JSON: expected " + machineBatch + "/3/2/" + recipeBatch
                                + " but got " + machine.isParallelizable() + "/" + machine.getMaxParallelism() + "/" + machine.getMaxThreads() + "/" + recipe.parallelized());
                require(new MachineRecipe(id, "java_default", 2, List.of(), null, false, -1, "").parallelized() == recipeBatch,
                        "implicit Java/adapter recipes inherit recipe policy");
                var options = RecipeOptionsEditor.read(source);
                require(options.parallelized() == recipeBatch && !RecipeOptionsEditor.merge(source, "", 2, options).has("parallelized"),
                        "unchanged save retains omitted server default");
                var opposite = new RecipeOptionsEditor.Options(0, false, "", -1, !recipeBatch, true, List.of());
                require(RecipeOptionsEditor.merge(source, "", 2, opposite).get("parallelized").getAsBoolean() == !recipeBatch,
                        "explicit recipe policy can override either default");
                var editor = JsonParser.parseString(MachineEditorSnapshot.create(machines, recipes)).getAsJsonObject();
                require(editor.get("defaultRecipeParallelized").getAsBoolean() == recipeBatch, "new draft receives server policy");
                JsonObject machineWire = machines.snapshot(); JsonArray recipeWire = recipes.snapshot();
                set(changed, "MACHINE_PARALLEL_DEFAULT", !machineBatch); set(changed, "RECIPE_PARALLEL_DEFAULT", !recipeBatch);
                set(changed, "MAX_MACHINE_PARALLELISM", 99); set(changed, "FACTORY_MAX_THREADS", 17);
                var remote = MachineLoader.fromSnapshot(machineWire); var remoteRecipe = RecipeLoader.fromSnapshot(recipeWire, remote).getRecipe(id);
                var remoteMachine = remote.findByName(id);
                require(remoteMachine.isParallelizable() == machineBatch && remoteMachine.getMaxParallelism() == 3
                        && remoteMachine.getMaxThreads() == 2 && remoteRecipe.parallelized() == recipeBatch,
                        "remote config cannot override resolved server defaults");
                for (boolean useFactory : List.of(false, true)) {
                    input.setStackInSlot(0, new ItemStack(Items.COAL, 64));
                    for (int slot = 0; slot < output.getSlots(); slot++) output.setStackInSlot(slot, ItemStack.EMPTY);
                    var context = new MachineContext(level, useFactory ? origin.offset(1, 0, 0) : origin, Direction.NORTH,
                            machine, new MachineVariables(), machine.getPattern());
                    context.overrideComponentPositions(List.of(origin.offset(2, 0, 0), origin.offset(3, 0, 0), origin.offset(4, 0, 0)));
                    int expected = machineBatch && recipeBatch ? 3 : 1;
                    FactoryRecipeThread active = null;
                    if (useFactory) {
                        factory.setFactoryCeiling(machine.getMaxThreads(), MachineTicker.machineParallelism(machine, context));
                        fstart.invoke(null, factory, machine, context, List.of(recipe));
                        active = factory.getFactoryRecipeThreadList().stream().filter(FactoryRecipeThread::hasActiveRecipe).findFirst().orElseThrow();
                        fstart.invoke(null, factory, machine, context, List.of(recipe));
                        require(active.getParallelism() == expected && factory.getActiveThreadCount() == 1, "factory keeps one recipe identity and batch");
                    } else {
                        start.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                        require(controller.hasActiveRecipe() && controller.getParallelism() == expected, "ordinary startup uses captured defaults");
                    }
                    require(input.getStackInSlot(0).getCount() == 64 - expected, "startup pays captured batch despite config change");
                    for (int n = 0; n < 3; n++) {
                        if (useFactory) { if (active.hasActiveRecipe()) ftick.invoke(null, active, factory, machine, context, List.of(recipe)); }
                        else if (controller.hasActiveRecipe()) tick.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
                    }
                    int diamonds = 0;
                    for (int slot = 0; slot < output.getSlots(); slot++) if (output.getStackInSlot(slot).is(Items.DIAMOND)) diamonds += output.getStackInSlot(slot).getCount();
                    require(diamonds == expected, "ordinary/factory finish keeps paid output count");
                }
            }
            // Explicit JSON and script values win even when all global defaults differ.
            Files.writeString(machineFile, "{\"parallelizable\":true,\"parallelism\":2,\"max-parallelism\":7,\"parts\":[{\"x\":1,\"elements\":\"minecraft:air\"}]}");
            source.addProperty("parallelized", true); Files.writeString(recipeFile, source.toString());
            set(changed, "MACHINE_PARALLEL_DEFAULT", false); set(changed, "RECIPE_PARALLEL_DEFAULT", false);
            MachineReloadListener.reloadNow(); var machine = MachineReloadListener.getLoader().findByName(id);
            require(machine.isParallelizable() && machine.getMaxParallelism() == 7 && machine.getInternalParallelism() == 2
                    && MachineReloadListener.getRecipeLoader().getRecipe(id).parallelized(), "explicit JSON overrides defaults");
            machine.setParallelizable(true); machine.setMaxThreads(6); machine.setInternalParallelism(123); machine.setMaxParallelism(91);
            var copy = MachineLoader.fromSnapshot(MachineReloadListener.getLoader().snapshot()).findByName(id);
            require(copy.getInternalParallelism() == 123 && copy.getMaxParallelism() == 91 && copy.getMaxThreads() == 6, "script settings survive wire");
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
            context.overrideComponentPositions(List.of());
            require(MachineTicker.machineParallelism(machine, context) == 91, "base alone obeys explicit ceiling");
            machine.setInternalParallelism(Integer.MAX_VALUE - 1); machine.setMaxParallelism(Integer.MAX_VALUE);
            context.overrideComponentPositions(List.of(parallel.getBlockPos()));
            require(MachineTicker.machineParallelism(machine, context) == Integer.MAX_VALUE, "batch addition saturates without int overflow");
            var old = parallel.saveWithoutMetadata(); old.putInt("parallelism", Integer.MAX_VALUE); parallel.load(old);
            require(parallel.getParallelism() == parallel.getMaxParallelism(), "saved parallel setting honors current tier ceiling");
            Modularmachinery.LOG.info("[MMCE][global-defaults-regression] PASS: config/explicit JSON/Java defaults, opposite-client sync, editor preservation, ordinary+factory paid IO, distinct recipe identity, batch ceiling/overflow and saved tier clamp");
        } finally {
            Config.SPEC.setConfig(originalConfig);
            Files.deleteIfExists(machineFile); Files.deleteIfExists(recipeFile); MachineReloadListener.reloadNow();
            for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(origin.offset(i, 0, 0), before[i], Block.UPDATE_ALL);
        }
    }
    private static void set(Map<ForgeConfigSpec.ConfigValue<?>, Object> changed, String field, Object value) throws Exception {
        var declared = Config.class.getDeclaredField(field); declared.setAccessible(true);
        var config = (ForgeConfigSpec.ConfigValue<?>) declared.get(null);
        changed.putIfAbsent(config, config.get()); restore(config, value);
    }
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void restore(ForgeConfigSpec.ConfigValue value, Object old) { value.set(old); }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
