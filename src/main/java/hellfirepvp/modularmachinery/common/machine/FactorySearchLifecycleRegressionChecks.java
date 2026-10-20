/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.event.machine.MachineComponentTileNotifiable;
import hellfirepvp.modularmachinery.common.event.machine.MachineEvent;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Map;

/** Exercises actual ticker dispatch and IO, rather than constructing a finish event by hand. */
public final class FactorySearchLifecycleRegressionChecks {
    private FactorySearchLifecycleRegressionChecks() {}

    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Block[] blocks = {RegistryBlocks.blockFactoryController.get(), RegistryBlocks.itemInputBus.get(),
                RegistryBlocks.itemOutputBus.get()};
        BlockState[] before = new BlockState[blocks.length];
        for (int i = 0; i < blocks.length; i++) before[i] = level.getBlockState(origin.offset(i, 0, 0));
        try {
            for (int i = 0; i < blocks.length; i++)
                level.setBlock(origin.offset(i, 0, 0), blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            var factory = (TileFactoryController) level.getBlockEntity(origin);
            var probe = new EventInputBus(origin.offset(1, 0, 0), level.getBlockState(origin.offset(1, 0, 0)));
            level.setBlockEntity(probe);
            var output = (TileItemBus) level.getBlockEntity(origin.offset(2, 0, 0));
            probe.output = output;
            var pattern = new MachinePattern();
            var machine = new DynamicMachine("factory_lifecycle_test", "Factory lifecycle", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            context.overrideComponentPositions(List.of(probe.getBlockPos(), output.getBlockPos()));
            factory.setBoundPositions(List.of(probe.getBlockPos(), output.getBlockPos()));
            factory.setFactoryCeiling(1, 4);
            probe.getInventory().insertItem(0, new ItemStack(Items.COAL, 32), false);
            var recipe = new MachineRecipe(machine.getRegistryName(), "factory_lifecycle_recipe", 2,
                    List.of(new RequirementItem(IOType.INPUT, "minecraft:coal", 1),
                            new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1)), null, false, -1, "");
            var start = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class,
                    DynamicMachine.class, MachineContext.class, List.class);
            var tick = FactoryTicker.class.getDeclaredMethod("tickThread",
                    hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread.class,
                    TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            start.setAccessible(true);
            tick.setAccessible(true);
            start.invoke(null, factory, machine, context, List.of(recipe));
            var thread = factory.getFactoryRecipeThreadList().get(0);
            require(thread.getParallelism() == 4, "lifecycle test lost machine batch multiplier");
            thread.addSemiPermanentModifier("finish_observable", new RecipeModifier(RecipeModifier.DURATION_TARGET, IOType.INPUT,
                    1F, RecipeModifier.OPERATION_MULTIPLY, false));
            factory.recordRecipeSearchResult(false);
            tick.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(probe.finishes == 0 && !factory.isRecipeSearchDue(), "unfinished run woke search or fired finish");
            tick.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(probe.finishes == 1 && probe.sawCompletedRun && probe.sawOutputs && probe.sawModifier,
                    "finish listener did not observe completed recipe, batch outputs and uncleared modifiers");
            require(probe.finishContext != null && probe.finishContext.getRecipe() == recipe,
                    "completed recipe context was absent from real finish dispatch");
            require(thread.isIdle() && thread.getSemiPermanentModifiers().isEmpty() && factory.isRecipeSearchDue(),
                    "completion did not release thread, clear semi-permanent modifiers and wake search");
            require(output.getInventory().getStackInSlot(0).getCount() == 4, "batch outputs differ from listener observation");

            start.invoke(null, factory, machine, context, List.of(recipe));
            factory.recordRecipeSearchResult(false);
            tick.invoke(null, thread, factory, machine, context, List.of());
            require(thread.isIdle() && factory.isRecipeSearchDue() && probe.finishes == 1,
                    "recipe reload removal did not wake search, or falsely reported completion");

            start.invoke(null, factory, machine, context, List.of(recipe));
            factory.recordRecipeSearchResult(false);
            probe.destroyAtStart = true;
            tick.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(thread.isIdle() && factory.isRecipeSearchDue() && probe.failures == 0,
                    "tick-event destruction failed to wake search or emitted failure for an already empty thread");
            probe.destroyAtStart = false;
            start.invoke(null, factory, machine, context, List.of(recipe));
            factory.recordRecipeSearchResult(false);
            probe.failAtStart = true;
            tick.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(thread.isIdle() && factory.isRecipeSearchDue() && probe.failures == 1 && probe.sawFailureContext,
                    "failure-event destruction lost active context or did not wake the released thread");

            factory.refreshRecipeSearchRevision(1);
            factory.recordRecipeSearchResult(false);
            factory.refreshRecipeSearchRevision(1);
            require(!factory.isRecipeSearchDue(), "unchanged recipe revision bypassed backoff");
            factory.refreshRecipeSearchRevision(2);
            require(factory.isRecipeSearchDue() && factory.getRecipeSearchRetryCounter() == 0,
                    "new definitions did not reset stale retry state");
            factory.recordRecipeSearchResult(false);
            var restored = new TileFactoryController(origin, factory.getBlockState());
            restored.load(factory.getUpdateTag());
            require(restored.isRecipeSearchDue() && restored.getRecipeSearchRetryCounter() == 0,
                    "NBT restored a stale retry schedule");

            var delayProbe = new TileFactoryController(origin, factory.getBlockState());
            delayProbe.syncCoreThreadPresets(Map.of("one", List.of()));
            delayProbe.recordRecipeSearchResult(false);
            require(delayProbe.currentRecipeSearchDelay() == 30, "single core did not use source retry step 10");
            delayProbe.syncCoreThreadPresets(Map.of("one", List.of(), "two", List.of()));
            require(delayProbe.currentRecipeSearchDelay() == 25, "two cores did not use source retry step 5");
            for (int i = 0; i < 200; i++) delayProbe.recordRecipeSearchResult(false);
            require(delayProbe.currentRecipeSearchDelay() == 150, "core retry delay was not capped at 150");
            delayProbe.syncCoreThreadPresets(Map.of());
            require(delayProbe.currentRecipeSearchDelay() == 100, "dynamic retry delay was not capped at 100");
            var manyCores = new java.util.LinkedHashMap<String, List<String>>();
            for (int i = 0; i < 12; i++) manyCores.put("core" + i, List.of());
            delayProbe.syncCoreThreadPresets(manyCores);
            delayProbe.resetRecipeSearchState();
            delayProbe.recordRecipeSearchResult(false);
            require(delayProbe.currentRecipeSearchDelay() == 21, "many cores did not floor source retry step at one");
            Modularmachinery.LOG.info("[MMCE][regression] factory real finish context/IO/modifiers, destruction/reload wakeup, revision/NBT/core backoff PASS");
        } finally {
            for (int i = 0; i < blocks.length; i++) level.setBlock(origin.offset(i, 0, 0), before[i], Block.UPDATE_ALL);
        }
    }

    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }

    private static final class EventInputBus extends TileItemInputBus implements MachineComponentTileNotifiable {
        TileItemBus output;
        int finishes, failures;
        boolean sawCompletedRun, sawOutputs, sawModifier, sawFailureContext, destroyAtStart, failAtStart;
        RecipeCraftingContext finishContext;
        EventInputBus(BlockPos pos, BlockState state) { super(pos, state); }

        @Override public void onMachineEvent(MachineEvent event) {
            if (event instanceof FactoryRecipeFinishEvent finished) {
                finishes++;
                var thread = finished.getFactoryRecipeThread();
                finishContext = finished.getContext();
                sawCompletedRun = thread.hasActiveRecipe() && thread.getRecipeTicks() == thread.getRecipeDuration()
                        && thread.getRecipeName().equals("factory_lifecycle_recipe");
                sawOutputs = output.getInventory().getStackInSlot(0).is(Items.DIAMOND)
                        && output.getInventory().getStackInSlot(0).getCount() == thread.getParallelism();
                sawModifier = thread.getSemiPermanentModifiers().containsKey("finish_observable");
            }
            if (event instanceof FactoryRecipeTickEvent tick && tick.phase == Phase.START) {
                if (destroyAtStart) tick.setFailed(true, "lifecycle_destroy");
                if (failAtStart) tick.setFailed(false, "lifecycle_fail");
            }
            if (event instanceof FactoryRecipeFailureEvent failure) {
                failures++;
                sawFailureContext = failure.getContext() != null && failure.getFactoryRecipeThread().hasActiveRecipe();
                failure.setDestructRecipe(true);
            }
        }
    }
}
