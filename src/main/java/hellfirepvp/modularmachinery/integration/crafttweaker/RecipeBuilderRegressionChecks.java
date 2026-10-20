/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import java.util.*;

/** Actual builder values and production controller paths, loaded only with CraftTweaker installed. */
public final class RecipeBuilderRegressionChecks {
    private RecipeBuilderRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var taggedCoal = new ItemStack(Items.COAL, 3);
        var nbt = new net.minecraft.nbt.CompoundTag(); nbt.putInt("quality", 3); taggedCoal.setTag(nbt);
        var builder = RecipeBuilder.newBuilder("modularmachinery:ct_builder_regression", "alloy_furnace", 2, -20, true)
                .addItemInput(IItemStack.of(taggedCoal)).setParallelizeUnaffected(true)
                .addItemOutput(IItemStack.of(new ItemStack(Items.DIAMOND, 128))).setChance(0.25F)
                .setIgnoreOutputCheck(true)
                .addFluidPerTickInput(IFluidStack.of(Fluids.WATER, 100))
                .setMaxThreads(1).setThreadName("core")
                .addRecipeTooltip("jei.modularmachinery.regression")
                .setParallelized(false).setLoadJEI(false);
        var frozen = builder.snapshot();
        builder.setMaxThreads(5).setTag("last_rate");
        var decoded = RecipeSnapshotCodec.decode(frozen);
        require(decoded.registryName().equals("ct_builder_regression") && decoded.maxThreads() == 1
                && decoded.priority() == -20 && decoded.cancelOnPerTickFailure()
                && !decoded.parallelized() && !decoded.loadJEI()
                && decoded.tooltips().equals(List.of("jei.modularmachinery.regression")),
                "builder flags/name/metadata snapshot wrong");
        require(decoded.requirements().get(0) instanceof RequirementItem item && item.isParallelizeUnaffected()
                && item.matches(taggedCoal), "builder lost item NBT or parallel exemption");
        require(decoded.requirements().get(1) instanceof RequirementItem item && item.getAmount() == 128
                && item.getChance() == 0.25F && !item.isParallelizeUnaffected() && item.isIgnoreOutputCheck(),
                "builder modifier/ignore-output flag affected wrong requirement");
        require(decoded.requirements().get(2).getSelectorTag() == null, "built declaration changed after builder mutation");
        boolean rejected = false;
        try { builder.setChance(0.5F); } catch (IllegalArgumentException expected) { rejected = true; }
        require(rejected, "continuous rate accepted chance silently");

        // An opt-in fixture proves ZenCode itself compiled and its action reached the server loader.
        // Normal integrated runs still exercise the Java builder when no fixture is installed.
        var scripted = MachineReloadListener.getRecipeLoader().getRecipe("mmce_ct_fixture");
        if (java.nio.file.Files.exists(com.blamejared.crafttweaker.api.CraftTweakerAPI.getScriptsDirectory()
                .resolve("mmce_fixture.zs"))) require(scripted != null, "fixture compiled but recipe was not installed");
        if (scripted != null) {
            require(scripted.recipeTime() == 2 && scripted.priority() == -20
                    && scripted.requirements().size() == 3, "compiled script registered unexpected recipe data");
            require(((RequirementItem) scripted.requirements().get(0)).getAmount() == 3
                    && ((RequirementItem) scripted.requirements().get(1)).getAmount() == 2,
                    "ZenCode item amount conversion lost counts");
            var blocks = new Block[] {RegistryBlocks.blockController.get(), RegistryBlocks.itemInputBus.get(),
                    RegistryBlocks.itemOutputBus.get(), RegistryBlocks.energyInputHatch.get()};
            var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
            var positions = new BlockPos[blocks.length];
            for (int i = 0; i < blocks.length; i++) {
                positions[i] = origin.offset(i, 0, 0); before[i] = level.getBlockState(positions[i]);
            }
            try {
                for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
                var pattern = new MachinePattern();
                var machine = new DynamicMachine("alloy_furnace", "CT test", pattern);
                var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
                context.overrideComponentPositions(List.of(positions[1], positions[2], positions[3]));
                var input = (net.minecraftforge.items.IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[1])).getInventory();
                var output = ((TileItemBus) level.getBlockEntity(positions[2])).getInventory();
                input.setStackInSlot(0, new ItemStack(Items.APPLE, 12));
                var energy = (TileEnergyHatch) level.getBlockEntity(positions[3]);
                energy.getEnergyStorage().receiveEnergy(1000, false);
                var controller = (TileController) level.getBlockEntity(origin);
                require(MachineTicker.consumeInputs(scripted.requirements(), context, 2), "scripted input did not execute");
                require(input.getStackInSlot(0).getCount() == 6, "scripted batch input count wrong");
                controller.beginRecipe(scripted.registryName(), 2, 2);
                var advance = MachineTicker.class.getDeclaredMethod("advance", TileController.class,
                        MachineContext.class, List.class, RecipeCraftingContext.class);
                advance.setAccessible(true);
                for (int i = 0; i < 2; i++) advance.invoke(null, controller, context, List.of(scripted), new RecipeCraftingContext(scripted));
                require(!controller.hasActiveRecipe() && output.getStackInSlot(0).is(Items.DIAMOND)
                        && output.getStackInSlot(0).getCount() == 4, "compiled script did not produce parallel results");
            } finally {
                for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], before[i], Block.UPDATE_ALL);
            }
            Modularmachinery.LOG.info("[MMCE][regression] compiled ZenCode recipe / ordinary machine batch lifecycle PASS");
            checkScriptReload(level);
        }
        Modularmachinery.LOG.info("[MMCE][regression] CraftTweaker RecipeBuilder values/NBT/modifier scope PASS");
        checkFactoryScripts(level);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Re-enter CT's real pre-run method and runner, including subsequent runtime actions and empty scripts. */
    private static void checkScriptReload(ServerLevel level) throws Exception {
        var fixture = com.blamejared.crafttweaker.api.CraftTweakerAPI.getScriptsDirectory().resolve("mmce_fixture.zs");
        byte[] original = java.nio.file.Files.readAllBytes(fixture);
        int count = ScriptRecipeRegistry.snapshots().size();
        try {
            replayServerScripts(level);
            replayServerScripts(level);
            require(ScriptRecipeRegistry.snapshots().size() == count
                    && MachineReloadListener.getRecipeLoader().getRecipe("mmce_ct_fixture") != null,
                    "repeated CT execution lost recipes or accumulated duplicates");
            String changed = new String(original, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\"mmce_ct_fixture\", \"alloy_furnace\", 2,", "\"mmce_ct_fixture\", \"alloy_furnace\", 4,");
            java.nio.file.Files.writeString(fixture, changed);
            replayServerScripts(level);
            require(MachineReloadListener.getRecipeLoader().getRecipe("mmce_ct_fixture").recipeTime() == 4,
                    "edited script recipe did not replace its prior revision");
            java.nio.file.Files.writeString(fixture, "");
            replayServerScripts(level);
            require(MachineReloadListener.getRecipeLoader().getRecipe("mmce_ct_fixture") == null,
                    "removed script recipe survived an empty script run");
        } finally {
            java.nio.file.Files.write(fixture, original);
            replayServerScripts(level);
        }
        var beforeClientReplay = ScriptRecipeRegistry.snapshots();
        var config = new com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration(
                com.blamejared.crafttweaker.api.CraftTweakerConstants.DEFAULT_LOADER_NAME,
                com.blamejared.crafttweaker.api.CraftTweakerConstants.CLIENT_RECIPES_UPDATED_SOURCE_ID,
                com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration.RunKind.EXECUTE);
        execute(com.blamejared.crafttweaker.api.CraftTweakerAPI.getScriptRunManager().createScriptRun(
                java.util.List.of(new org.openzen.zencode.shared.LiteralSourceFile("client_mmce_regression.zs",
                        "import mods.modularmachinery.RecipeBuilder;\n"
                        + "RecipeBuilder.newBuilder(\"client_only_recipe\", \"alloy_furnace\", 2).build();")), config));
        require(beforeClientReplay.equals(ScriptRecipeRegistry.snapshots()),
                "client script replay changed the server's script recipe declarations");
        Modularmachinery.LOG.info("[MMCE][regression] CT real runner repeat/edit/empty/client replay isolation PASS");
    }

    private static void replayServerScripts(ServerLevel level) throws Exception {
        var listener = new com.blamejared.crafttweaker.impl.script.ScriptReloadListener(
                level.getServer().getServerResources().managers(), message -> { });
        var prepare = listener.getClass().getDeclaredMethod("prepareRun");
        prepare.setAccessible(true);
        Object preparation = prepare.invoke(listener);
        var run = preparation.getClass().getDeclaredMethod("run");
        run.setAccessible(true);
        execute((com.blamejared.crafttweaker.api.zencode.scriptrun.IScriptRun) run.invoke(preparation));
    }

    private static void execute(com.blamejared.crafttweaker.api.zencode.scriptrun.IScriptRun run) throws Exception {
        try { run.execute(); }
        catch (Throwable failure) { throw new Exception("CraftTweaker test run failed", failure); }
    }

    private static void checkFactoryScripts(ServerLevel level) throws Exception {
        var fixture = com.blamejared.crafttweaker.api.CraftTweakerAPI.getScriptsDirectory().resolve("mmce_core_threads.zs");
        if (!java.nio.file.Files.exists(fixture)) return;
        byte[] original = java.nio.file.Files.readAllBytes(fixture);
        try {
            checkFactoryDefinition("mmce_ct_core_a", true);
            replayServerScripts(level);
            replayServerScripts(level);
            checkFactoryDefinition("mmce_ct_core_a", true);
            java.nio.file.Files.writeString(fixture, new String(original, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("addRecipe(\"mmce_ct_core_a\")", "addRecipe(\"mmce_ct_core_b\")"));
            replayServerScripts(level);
            checkFactoryDefinition("mmce_ct_core_b", false);
            java.nio.file.Files.writeString(fixture, "");
            replayServerScripts(level);
            var machine = MachineReloadListener.getLoader().findByName("mmce_core_fixture");
            require(machine.getCoreThreadPresets().isEmpty() && machine.getMaxThreads() == DynamicMachine.DEFAULT_FACTORY_MAX_THREADS,
                    "empty CT core script retained presets or zero dynamic ceiling");
        } finally {
            java.nio.file.Files.write(fixture, original);
            replayServerScripts(level);
        }
        var machine = MachineReloadListener.getLoader().findByName("mmce_core_fixture");
        var before = new java.util.LinkedHashMap<>(machine.getCoreThreadPresets());
        var config = new com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration(
                com.blamejared.crafttweaker.api.CraftTweakerConstants.DEFAULT_LOADER_NAME,
                com.blamejared.crafttweaker.api.CraftTweakerConstants.CLIENT_RECIPES_UPDATED_SOURCE_ID,
                com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration.RunKind.EXECUTE);
        execute(com.blamejared.crafttweaker.api.CraftTweakerAPI.getScriptRunManager().createScriptRun(
                List.of(new org.openzen.zencode.shared.LiteralSourceFile("client_factory_regression.zs", """
                    import mods.modularmachinery.FactoryRecipeThread;
                    import mods.modularmachinery.MachineModifier;
                    MachineModifier.setMaxThreads("mmce_core_fixture", 9);
                    FactoryRecipeThread.createCoreThread("client_only").addToMachine("mmce_core_fixture");
                    """)), config));
        require(machine.getMaxThreads() == 0 && machine.getCoreThreadPresets().equals(before),
                "client script replay mutated server factory settings");
        var copy = MachineLoader.fromSnapshot(MachineReloadListener.getLoader().snapshot()).findByName("mmce_core_fixture");
        require(copy.getMaxThreads() == 0 && copy.getCoreThreadPresets().equals(before), "definition snapshot lost scripted core settings");
        Modularmachinery.LOG.info("[MMCE][regression] CT core script compile/repeat/edit/empty/client isolation/snapshot PASS");
    }

    private static void checkFactoryDefinition(String expectedRecipe, boolean initial) {
        var machine = MachineReloadListener.getLoader().findByName("mmce_core_fixture");
        var expected = initial ? List.of("mmce_ct_core_a", "mmce_ct_core_b") : List.of("mmce_ct_core_b");
        require(machine.getMaxThreads() == 0 && machine.getCoreThreadPresets().size() == 2
                && machine.getCoreThreadPresets().get("ore").equals(expected)
                && machine.getCoreThreadPresets().get("empty").isEmpty()
                && machine.getCoreThreadPermanentModifiers().getOrDefault("ore", Map.of()).containsKey("double_input"),
                "compiled core script did not register frozen presets: max="
                        + machine.getMaxThreads() + ", presets=" + machine.getCoreThreadPresets());
        var recipe = MachineReloadListener.getRecipeLoader().getRecipe(expectedRecipe);
        require(recipe != null && recipe.threadName().equals("ore"), "compiled thread-bound recipe missing");
    }
}
