/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.lang.reflect.Method;
import java.util.*;

/** Opt-in checks against real ticker IO and event dispatch, with no player world or scripting dependency. */
public final class RuntimeRecipeQueryRegressionChecks {
    public static final String TARGET = "mmc_runtime_query_regression";
    private RuntimeRecipeQueryRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        var machines = MachineReloadListener.getLoader().getMachines();
        var savedMachines = new LinkedHashMap<>(machines);
        try {
            checkOrdinary(level, origin, before);
            checkFactory(level, origin.offset(0, 0, 8), before);
            Modularmachinery.LOG.info("[MMC][runtime-query] PASS: actual ordinary/factory starts, IO, named and dynamic threads, immutable observations, modified contexts, finish/END events, reload/load/rebind/removal lifetimes");
        } finally {
            before.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            // Isolated callbacks must not retain the test world's controllers for the rest of the suite.
            var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS");
            field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).remove(TARGET);
            machines.clear(); machines.putAll(savedMachines);
            MachineReloadListener.refreshRecipes();
        }
    }

    private static void checkOrdinary(ServerLevel level, BlockPos origin, Map<BlockPos, BlockState> before) throws Exception {
        TileController tile = place(level, origin, false, before);
        DynamicMachine machine = new DynamicMachine(TARGET, TARGET, new MachinePattern());
        MachineReloadListener.getLoader().getMachines().put(new net.minecraft.resources.ResourceLocation("modularmachinery", TARGET), machine);
        MachineContext context = context(level, origin, machine);
        var input = inventory(level, origin.offset(1, 0, 0));
        var output = inventory(level, origin.offset(2, 0, 0));
        input.setStackInSlot(0, new ItemStack(Items.COAL, 16));
        var recipe = recipe("runtime_ordinary", "");
        RecipeThread thread = tile.getRecipeThreadList()[0];
        require(thread.isValid() && thread.getActiveRecipe() == null && thread.getContext() == null
                && tile.getActiveRecipeList().length == 0, "idle ordinary queries fabricated a craft");
        List<RecipeEvent> events = new ArrayList<>();
        ScriptMachineEventRegistry.register(TARGET, RecipeStartEvent.class, events::add);
        ScriptMachineEventRegistry.register(TARGET, RecipeTickEvent.class, events::add);
        ScriptMachineEventRegistry.register(TARGET, RecipeFinishEvent.class, events::add);
        Method start = method(MachineTicker.class, "tryStart", TileController.class, MachineContext.class,
                List.class, RecipeCraftingContext.class);
        RecipeCraftingContext run = new RecipeCraftingContext(recipe);
        start.invoke(null, tile, context, List.of(recipe), run);
        require(input.getStackInSlot(0).getCount() == 15 && tile.getActiveRecipeList().length == 1,
                "actual ordinary start/payment was not observable");
        ActiveMachineRecipe retained = thread.getActiveRecipe();
        require(retained.getTick() == 0 && retained.getTotalTick() == 2 && retained.getParallelism() == 1
                && thread.getContext().getRecipeName().equals(recipe.registryName()), "ordinary start fields differ");
        ActiveMachineRecipe[] list = tile.getActiveRecipeList(); list[0] = null;
        RecipeThread[] threads = tile.getRecipeThreadList(); threads[0] = null;
        require(tile.getActiveRecipe() != null && tile.getRecipeThreadList()[0] != null, "query arrays leaked mutable storage");
        if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker"))
            hellfirepvp.modularmachinery.integration.crafttweaker.RuntimeRecipeQueryScriptRegressionChecks.run(tile, null, recipe);
        Method advance = method(MachineTicker.class, "advance", TileController.class, MachineContext.class,
                List.class, RecipeCraftingContext.class);
        for (int i = 0; i < 2; i++) advance.invoke(null, tile, context, List.of(recipe), new RecipeCraftingContext(recipe));
        require(!tile.hasActiveRecipe() && output.getStackInSlot(0).is(Items.DIAMOND)
                && output.getStackInSlot(0).getCount() == 1 && thread.getContext() == null,
                "ordinary completion IO or context cleanup failed");
        checkClosingEvents(events, "", recipe.registryName());
        tile.beginRecipe("runtime_replacement", 20, 3);
        require(retained.getRegistryName().equals("runtime_ordinary") && retained.getTick() == 0
                && retained.getTotalTick() == 2 && retained.getParallelism() == 1,
                "retained active recipe observation became the next craft");
        tile.abandonRecipe();
        tile.beginRecipe(recipe.registryName(), 2, 1);
        tile.observeRuntimeRecipeContext(new RecipeCraftingContext(recipe));
        tile.load(tile.getUpdateTag());
        require(thread.getContext() == null, "ordinary load exposed pre-load context");
        tile.observeRuntimeRecipeContext(new RecipeCraftingContext(recipe));
        MachineReloadListener.refreshRecipes();
        require(thread.getContext() == null, "ordinary reload exposed old modifier results");
        tile.setMachineName(TARGET + "_other");
        require(!thread.isValid() && thread.getActiveRecipe() == null, "ordinary rebind left old thread handle valid");
        RecipeThread removed = tile.getRecipeThreadList()[0];
        level.removeBlock(origin, false);
        require(!removed.isValid() && removed.getContext() == null, "removed controller query remained usable");
    }

    private static void checkFactory(ServerLevel level, BlockPos origin, Map<BlockPos, BlockState> before) throws Exception {
        var tile = (TileFactoryController) place(level, origin, true, before);
        DynamicMachine machine = new DynamicMachine(TARGET, TARGET, new MachinePattern());
        MachineReloadListener.getLoader().getMachines().put(new net.minecraft.resources.ResourceLocation("modularmachinery", TARGET), machine);
        var duration = new RecipeModifier("modularmachinery:duration", IOType.INPUT, 2F, RecipeModifier.OPERATION_MULTIPLY, false);
        var items = new RecipeModifier("modularmachinery:item", IOType.INPUT, 2F, RecipeModifier.OPERATION_MULTIPLY, false);
        machine.addCoreThreadPreset("ore", List.of("runtime_factory"), Map.of("time", duration, "input", items), Map.of());
        tile.setFactoryCeiling(1, 1);
        tile.syncCoreThreadPresets(machine.getCoreThreadPresets(), machine.getCoreThreadPermanentModifiers(), machine.getCoreThreadSemiPermanentModifiers());
        var recipe = recipe("runtime_factory", "ore");
        var dynamic = recipe("runtime_dynamic", "");
        MachineContext context = context(level, origin, machine);
        inventory(level, origin.offset(1, 0, 0)).setStackInSlot(0, new ItemStack(Items.COAL, 16));
        List<RecipeEvent> events = new ArrayList<>();
        ScriptMachineEventRegistry.register(TARGET, FactoryRecipeStartEvent.class, events::add);
        ScriptMachineEventRegistry.register(TARGET, FactoryRecipeTickEvent.class, events::add);
        ScriptMachineEventRegistry.register(TARGET, FactoryRecipeFinishEvent.class, events::add);
        Method start = method(FactoryTicker.class, "startOneRecipe", TileFactoryController.class, DynamicMachine.class,
                MachineContext.class, List.class);
        inventory(level, origin.offset(1, 0, 0)).setStackInSlot(0, new ItemStack(Items.COAL, 1));
        start.invoke(null, tile, machine, context, List.of(recipe));
        require(tile.getActiveRecipeList().length == 0
                && inventory(level, origin.offset(1, 0, 0)).getStackInSlot(0).getCount() == 1,
                "modified core input shortfall started a craft or consumed materials");
        inventory(level, origin.offset(1, 0, 0)).setStackInSlot(0, new ItemStack(Items.COAL, 16));
        var output = inventory(level, origin.offset(2, 0, 0));
        for (int i = 0; i < output.getSlots(); i++) output.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
        start.invoke(null, tile, machine, context, List.of(recipe));
        require(tile.getActiveRecipeList().length == 0
                && inventory(level, origin.offset(1, 0, 0)).getStackInSlot(0).getCount() == 16,
                "full output started a modified core or consumed materials");
        for (int i = 0; i < output.getSlots(); i++) output.setStackInSlot(i, ItemStack.EMPTY);
        start.invoke(null, tile, machine, context, List.of(recipe));
        start.invoke(null, tile, machine, context, List.of(recipe));
        start.invoke(null, tile, machine, context, List.of(dynamic));
        require(tile.getActiveRecipeList().length == 2, "factory query lost second thread or admitted duplicate recipe");
        require(inventory(level, origin.offset(1, 0, 0)).getStackInSlot(0).getCount() == 13,
                "factory start did not pay the core's modified input plus the dynamic thread's own input");
        RecipeThread thread = tile.getRecipeThreadList()[0];
        var core = tile.getFactoryRecipeThreadList().get(0);
        RecipeCraftingContext retainedContext = thread.getContext();
        require(thread.getThreadName().equals("ore") && thread.isValid() && retainedContext.getEffectiveDuration() == 4
                && ((RequirementItem) retainedContext.getRequirements().get(0)).getAmount() == 2
                && ((RequirementItem) recipe.requirements().get(0)).getAmount() == 1,
                "factory context did not expose actual modifiers or changed the recipe definition");
        String[] descriptions = retainedContext.getInputDescriptions(); descriptions[0] = "corrupted";
        require(!retainedContext.getInputDescriptions()[0].equals("corrupted"), "descriptions leaked mutable array");
        tile.observeRuntimeRecipeContext(null); // Factory events must use their own core, never the controller's first run.
        if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker"))
            hellfirepvp.modularmachinery.integration.crafttweaker.RuntimeRecipeQueryScriptRegressionChecks.run(tile, core, recipe);
        Method tick = method(FactoryTicker.class, "tickThread", core.getClass(), TileFactoryController.class,
                DynamicMachine.class, MachineContext.class, List.class);
        for (int i = 0; i < 4; i++) tick.invoke(null, core, tile, machine, context, List.of(recipe, dynamic));
        require(!core.hasActiveRecipe() && tile.getActiveRecipeList().length == 1 && thread.getContext() == null
                && inventory(level, origin.offset(2, 0, 0)).getStackInSlot(0).getCount() == 1,
                "core finish damaged other craft or retained live context");
        checkClosingEvents(events.stream().filter(e -> e.getContext().getRecipeName().equals(recipe.registryName())).toList(), "ore", recipe.registryName());
        require(retainedContext.getEffectiveDuration() == 4 && retainedContext.getRecipeName().equals(recipe.registryName()),
                "retained context observation was recycled");
        start.invoke(null, tile, machine, context, List.of(recipe));
        MachineReloadListener.refreshRecipes();
        require(thread.getContext() == null, "factory reload exposed stale context");
        core.observeRuntimeRecipeContext(new RecipeCraftingContext(recipe));
        tile.setFormed(false);
        require(thread.getContext() == null && !thread.isWorking(), "unformed factory exposed working context");
        tile.setFormed(true);
        require(thread.getContext() == null, "reforming factory resurrected a pre-unformation context");
        tile.load(tile.getUpdateTag());
        require(!thread.isValid() && tile.getRecipeThreadList()[0].getContext() == null, "factory load reused old thread/context");
        RecipeThread deletedPreset = tile.getRecipeThreadList()[0];
        tile.syncCoreThreadPresets(Map.of());
        require(!deletedPreset.isValid() && deletedPreset.getActiveRecipe() == null, "removed preset handle remained valid");
    }

    private static void checkClosingEvents(List<RecipeEvent> events, String name, String recipe) {
        require(!events.isEmpty(), "ticker did not dispatch query events");
        RecipeEvent finish = events.stream().filter(e -> e instanceof RecipeFinishEvent || e instanceof FactoryRecipeFinishEvent)
                .findFirst().orElseThrow(() -> new AssertionError("missing finish event"));
        RecipeEvent end = events.get(events.size() - 1);
        require((end instanceof RecipeTickEvent ordinary && ordinary.phase == Phase.END
                || end instanceof FactoryRecipeTickEvent factory && factory.phase == Phase.END)
                && finish.getActiveRecipe().isCompleted() && end.getActiveRecipe().isCompleted()
                && end.getActiveRecipe().getRegistryName().equals(recipe)
                && end.getRecipeThread().getThreadName().equals(name), "finish/END lost completed craft identity");
        require(events.get(0).getActiveRecipe().getTick() == 0, "retained START event changed with later ticks");
    }

    private static TileController place(ServerLevel level, BlockPos origin, boolean factory, Map<BlockPos, BlockState> before) {
        Block[] blocks = {factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get(),
                RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get()};
        for (int i = 0; i < blocks.length; i++) {
            BlockPos pos = origin.offset(i, 0, 0); before.putIfAbsent(pos, level.getBlockState(pos));
            level.setBlock(pos, blocks[i].defaultBlockState(), Block.UPDATE_ALL);
        }
        var tile = (TileController) level.getBlockEntity(origin);
        tile.setMachineName(TARGET); tile.setFormed(true); return tile;
    }
    private static MachineContext context(ServerLevel level, BlockPos origin, DynamicMachine machine) {
        var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
        context.overrideComponentPositions(List.of(origin.offset(1, 0, 0), origin.offset(2, 0, 0))); return context;
    }
    private static IItemHandlerModifiable inventory(ServerLevel level, BlockPos pos) {
        return (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(pos)).getInventory();
    }
    private static MachineRecipe recipe(String name, String thread) {
        return new MachineRecipe(TARGET, name, 2, List.of(new RequirementItem(IOType.INPUT, "minecraft:coal", 1),
                new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1)), null, false, -1, thread);
    }
    private static Method method(Class<?> type, String name, Class<?>... parameters) throws Exception {
        Method method = type.getDeclaredMethod(name, parameters); method.setAccessible(true); return method;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
