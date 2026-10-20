/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** Real factory scheduling with independent core/dynamic pools and live preset changes. */
public final class FactoryCoreThreadRegressionChecks {
    private FactoryCoreThreadRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var blocks = new Block[] {RegistryBlocks.blockFactoryController.get(), RegistryBlocks.itemInputBus.get(),
                RegistryBlocks.itemOutputBus.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        var positions = new BlockPos[blocks.length];
        for (int i = 0; i < blocks.length; i++) {
            positions[i] = origin.offset(i, 0, 0); before[i] = level.getBlockState(positions[i]);
        }
        try {
            for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            var factory = (TileFactoryController) level.getBlockEntity(origin);
            require(factory.isRecipeSearchDue() && factory.currentRecipeSearchDelay() == 20,
                    "factory did not begin with an immediate recipe search");
            factory.recordRecipeSearchResult(false);
            require(!factory.isRecipeSearchDue() && factory.getRecipeSearchRetryCounter() == 1
                    && factory.currentRecipeSearchDelay() == 25, "factory search failure did not enter source backoff");
            for (int i = 0; i < 24; i++) factory.countFactoryTick();
            require(!factory.isRecipeSearchDue(), "factory search backoff fired too early");
            factory.countFactoryTick();
            require(factory.isRecipeSearchDue(), "factory search backoff did not fire at 25 ticks");
            factory.recordRecipeSearchResult(true);
            require(factory.getRecipeSearchRetryCounter() == 0 && !factory.isRecipeSearchDue(),
                    "successful factory search did not reset retry state");
            factory.requestImmediateRecipeSearch();
            require(factory.isRecipeSearchDue(), "factory immediate search request was ignored");
            factory.setFormed(true);
            factory.recordRecipeSearchResult(false);
            require(!factory.isRecipeSearchDue() && factory.getRecipeSearchRetryCounter() == 1,
                    "factory lifecycle reset setup did not enter backoff");
            factory.setFormed(false);
            require(factory.isRecipeSearchDue() && factory.getRecipeSearchRetryCounter() == 0,
                    "unforming a factory kept stale search backoff");
            factory.setFormed(true);
            factory.recordRecipeSearchResult(false);
            factory.setMachineName("core_test");
            require(factory.isRecipeSearchDue() && factory.getRecipeSearchRetryCounter() == 0,
                    "machine rebinding kept stale search backoff");
            var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[1])).getInventory();
            var pattern = new MachinePattern();
            var machine = new DynamicMachine("core_test", "Core test", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            context.overrideComponentPositions(List.of(positions[1], positions[2]));
            var a = recipe("core_a", "ore");
            var b = recipe("core_b", "ore");
            var wrong = recipe("core_wrong", "other");
            var fallback = recipe("core_dynamic", "");
            machine.addCoreThreadPreset("ore", List.of("core_a"));
            factory.setFactoryCeiling(0, 4);
            factory.syncCoreThreadPresets(machine.getCoreThreadPresets());
            require(factory.getThreadCount() == 1 && factory.hasIdleThreadFor(a), "zero dynamic ceiling suppressed core thread");
            require(!factory.hasIdleThreadFor(b) && !factory.hasIdleThreadFor(wrong)
                    && !factory.hasIdleThreadFor(fallback), "core set/name exclusion was bypassed");
            input.setStackInSlot(0, new ItemStack(Items.COAL, 32));
            var start = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class,
                    DynamicMachine.class, MachineContext.class, List.class);
            start.setAccessible(true);
            start.invoke(null, factory, machine, context, List.of(b, wrong, fallback, a));
            var core = factory.getFactoryRecipeThreadList().get(0);
            require(core.getRecipeName().equals("core_a") && core.getParallelism() == 4
                    && input.getStackInSlot(0).getCount() == 28, "core scheduler used excluded recipe or lost batch count");
            start.invoke(null, factory, machine, context, List.of(a));
            require(factory.getActiveThreadCount() == 1 && input.getStackInSlot(0).getCount() == 28,
                    "same core recipe started a duplicate");
            core.advanceRecipe();
            machine.addCoreThreadPreset("ore", List.of("core_b"));
            factory.syncCoreThreadPresets(machine.getCoreThreadPresets());
            require(core.getRecipeSet().equals(Set.of("core_b")) && core.getRecipeTicks() == 1
                    && core.getParallelism() == 4, "live set edit reset paid run or retained stale candidates");
            var permanent = new RecipeModifier("modularmachinery:item", IOType.INPUT, 2.0F,
                    RecipeModifier.OPERATION_MULTIPLY, false);
            machine.addCoreThreadPreset("ore", List.of("core_b"), Map.of("double_input", permanent), Map.of());
            factory.syncCoreThreadPresets(machine.getCoreThreadPresets(), machine.getCoreThreadPermanentModifiers(),
                    machine.getCoreThreadSemiPermanentModifiers());
            require(core.getPermanentModifiers().get("double_input") == permanent && core.activeModifiers().contains(permanent),
                    "core permanent modifier was not attached or exposed to the run");
            var copy = new TileFactoryController(origin, factory.getBlockState());
            copy.load(factory.getUpdateTag());
            var restored = copy.getFactoryRecipeThreadList().get(0);
            require(restored.isPresetThread() && restored.getThreadName().equals("ore")
                    && restored.getRecipeSet().equals(Set.of("core_b")) && restored.getRecipeTicks() == 1
                    && restored.getParallelism() == 4 && restored.getPermanentModifiers().containsKey("double_input"),
                    "NBT/client update lost core identity, set, modifier or progress");
            factory.setFactoryCeiling(1, 4);
            start.invoke(null, factory, machine, context, List.of(fallback));
            require(factory.getActiveThreadCount() == 2 && factory.getThreadCount() == 2,
                    "active core consumed dynamic thread capacity");
            require(!factory.hasIdleThreadFor(fallback), "dynamic pool exceeded ceiling");
            core.finishRecipe();
            for (int i = 0; i < 220; i++) { core.countIdleTick(); factory.countFactoryTick(); factory.cleanIdleThreads(); }
            require(factory.getFactoryRecipeThreadList().contains(core), "idle core was cleaned up");
            machine.addCoreThreadPreset("ore", List.of());
            factory.syncCoreThreadPresets(machine.getCoreThreadPresets());
            require(core.acceptsRecipe(a) && core.acceptsRecipe(b) && !core.acceptsRecipe(wrong),
                    "empty core set did not use all name-compatible recipes");
            start.invoke(null, factory, machine, context, List.of(a));
            factory.syncCoreThreadPresets(Map.of());
            require(factory.getThreadCount() == 1 && factory.getFactoryRecipeThreadList().stream()
                    .noneMatch(FactoryRecipeThread::isPresetThread), "removed running core survived preset deletion");
            require(factory.getFactoryRecipeThreadList().get(0).getRecipeName().equals("core_dynamic"),
                    "preset removal damaged unrelated dynamic job");
            machine.addCoreThreadPreset("ore", List.of("core_b"));
            factory.syncCoreThreadPresets(machine.getCoreThreadPresets());
            require(factory.getFactoryRecipeThreadList().get(0).isPresetThread(), "core was not listed before dynamic jobs");
            Modularmachinery.LOG.info("[MMCE][regression] factory core pools/set/name/batch/NBT/update/idle/remove PASS");
        } finally {
            for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], before[i], Block.UPDATE_ALL);
        }
    }
    private static MachineRecipe recipe(String name, String thread) {
        return new MachineRecipe("core_test", name, 20, List.of(new RequirementItem(IOType.INPUT, "minecraft:coal", 1),
                new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1)), null, false, -1, thread);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
