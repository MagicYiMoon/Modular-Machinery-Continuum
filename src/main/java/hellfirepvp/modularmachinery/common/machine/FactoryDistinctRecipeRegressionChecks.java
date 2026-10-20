/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** Exercises production factory scheduling and ticks with independent jobs and legacy saved batches. */
public final class FactoryDistinctRecipeRegressionChecks {
    private FactoryDistinctRecipeRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Block[] blocks = {RegistryBlocks.blockFactoryController.get(), RegistryBlocks.itemInputBus.get(),
                RegistryBlocks.itemOutputBus.get(), RegistryBlocks.energyInputHatch.get(), RegistryBlocks.parallelController.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        var positions = new BlockPos[blocks.length];
        for (int i = 0; i < positions.length; i++) {
            positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i, 0, 0);
            before[i] = level.getBlockState(positions[i]);
        }
        try {
            for (int i = 0; i < positions.length; i++) level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            var factory = (TileFactoryController) level.getBlockEntity(origin);
            var input = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[1])).getInventory();
            var output = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[2])).getInventory();
            var energy = ((TileEnergyHatch) level.getBlockEntity(positions[3])).getEnergyStorage();
            var pattern = new MachinePattern();
            for (int i = 1; i < positions.length; i++) pattern.addPart(new MachinePattern.Part(i, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("factory_single_test", "Factory single test", pattern);
            machine.setInternalParallelism(2);
            machine.setMaxParallelism(128);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var parallelController = (TileParallelController) level.getBlockEntity(positions[4]);
            parallelController.setParallelism(2);
            int batchCeiling = MachineTicker.machineParallelism(machine, context);
            require(batchCeiling == 4, "machine/parallel controller did not combine into a batch ceiling");
            var recipes = List.of(recipe("a", "minecraft:coal", "minecraft:diamond", 2, 10),
                    recipe("b", "minecraft:charcoal", "minecraft:emerald", 3, 20),
                    recipe("c", "minecraft:coal", "minecraft:gold_ingot", 4, 30));
            machine.addCoreThreadPreset("ore", List.of("b"));
            var start = FactoryTicker.class.getDeclaredMethod("startOneRecipe", TileFactoryController.class,
                    DynamicMachine.class, MachineContext.class, List.class);
            var tick = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class,
                    TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            start.setAccessible(true);
            tick.setAccessible(true);
            int seeded = energy.receiveEnergy(1000, false);
            factory.setFactoryCeiling(3, batchCeiling);
            factory.syncCoreThreadPresets(machine.getCoreThreadPresets());
            input.setStackInSlot(0, new ItemStack(Items.COAL, 16));
            input.setStackInSlot(1, new ItemStack(Items.CHARCOAL, 16));
            start.invoke(null, factory, machine, context, recipes);
            require(input.getStackInSlot(0).getCount() == 12 && factory.getActiveThreadCount() == 1,
                    "first factory thread did not preserve the machine/parallel controller batch multiplier");
            start.invoke(null, factory, machine, context, recipes);
            start.invoke(null, factory, machine, context, recipes);
            require(factory.getActiveThreadCount() == 3 && factory.getThreadCount() == 3
                    && input.getStackInSlot(0).getCount() == 11 && input.getStackInSlot(1).getCount() == 15,
                    "three independent jobs did not pay their own inputs");
            var names = new HashSet<String>();
            for (var thread : factory.getFactoryRecipeThreadList()) {
                names.add(thread.getRecipeName());
                require(thread.getRecipeName().equals("a") ? thread.getParallelism() == 4 : thread.getParallelism() == 1,
                        "thread batch multiplier or remaining parallel budget incorrect");
            }
            require(names.equals(Set.of("a", "b", "c")), "uncapped recipe identities were not distributed to distinct threads");
            require(factory.getFactoryRecipeThreadList().stream()
                    .anyMatch(t -> "ore".equals(t.getThreadName()) && t.getRecipeSet().contains("b")),
                    "core factory thread preset was not created");
            // Thread list order includes named preset threads and is not part of the runtime contract. Select the
            // active "a" run by identity so this regression checks the invariant it names rather than an incidental
            // insertion order.
            var first = factory.getFactoryRecipeThreadList().stream()
                    .filter(thread -> "a".equals(thread.getRecipeName()))
                    .findFirst().orElseThrow(() -> new AssertionError("active recipe a thread missing"));
            boolean refused = false;
            try { first.beginRecipe("overwrite", 1, 9); } catch (IllegalStateException expected) { refused = true; }
            require(refused && first.getRecipeName().equals("a"), "active thread allowed recipe replacement");
            refused = false;
            try { first.setRecipe(recipes.get(1)); } catch (IllegalStateException expected) { refused = true; }
            require(refused && first.getRecipeName().equals("a"), "active thread rebound to another recipe");
            start.invoke(null, factory, machine, context, recipes);
            require(factory.getActiveThreadCount() == 3 && input.getStackInSlot(0).getCount() == 11,
                    "full factory consumed another input");
            factory.setFactoryCeiling(1, batchCeiling);
            require(!factory.hasIdleThread() && factory.claimThread() == null, "lowered ceiling allowed extra jobs");
            factory.setFactoryCeiling(3, batchCeiling);
            for (var thread : factory.getFactoryRecipeThreadList()) tick.invoke(null, thread, factory, machine, context, recipes);
            require(energy.getEnergyStored() == seeded - 90 && factory.getFactoryRecipeThreadList().stream()
                    .allMatch(t -> t.getRecipeTicks() == 1), "threads did not pay independent per-tick energy");
            var state = factory.saveWithoutMetadata();
            var clientCopy = new TileFactoryController(origin, factory.getBlockState());
            clientCopy.load(factory.getUpdateTag());
            require(clientCopy.getActiveThreadCount() == 3 && clientCopy.getFactoryRecipeThreadList().stream()
                    .allMatch(t -> t.getRecipeTicks() == 1), "client thread snapshot lost independent jobs");
            factory.load(state);
            factory.setFactoryCeiling(3, batchCeiling);
            require(factory.getFactoryRecipeThreadList().stream().allMatch(t -> t.getFactory() == factory), "restored thread has wrong owner");
            for (int step = 0; step < 4; step++)
                for (var thread : factory.getFactoryRecipeThreadList()) tick.invoke(null, thread, factory, machine, context, recipes);
            require(factory.getActiveThreadCount() == 0 && count(output, Items.DIAMOND) == 4
                    && count(output, Items.EMERALD) == 1 && count(output, Items.GOLD_INGOT) == 1,
                    "independent jobs did not emit their own batch counts after reload");
            require(energy.getEnergyStored() == seeded - (4 * 2 * 10 + 3 * 20 + 4 * 30), "completed thread kept paying energy");
            require(input.getStackInSlot(0).getCount() == 11 && input.getStackInSlot(1).getCount() == 15,
                    "advancement consumed start inputs again");

            machine.removeCoreThreadPreset("ore");
            factory.syncCoreThreadPresets(machine.getCoreThreadPresets());

            // Idle slots from a larger former ceiling must not bypass a new ceiling of zero/one.
            factory.setFactoryCeiling(0, batchCeiling);
            start.invoke(null, factory, machine, context, recipes);
            require(factory.getAvailableParallelism() == 0 && factory.getActiveThreadCount() == 0
                    && factory.claimThread() == null && input.getStackInSlot(0).getCount() == 11, "zero ceiling reused an old idle thread");
            factory.setFactoryCeiling(1, batchCeiling);
            start.invoke(null, factory, machine, context, recipes);
            start.invoke(null, factory, machine, context, recipes);
            require(factory.getActiveThreadCount() == 1 && input.getStackInSlot(0).getCount() == 7, "idle pool bypassed reduced ceiling");
            for (int i = 0; i < output.getSlots(); i++) output.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            var blocked = factory.getFactoryRecipeThreadList().stream().filter(FactoryRecipeThread::hasActiveRecipe).findFirst().orElseThrow();
            tick.invoke(null, blocked, factory, machine, context, recipes);
            tick.invoke(null, blocked, factory, machine, context, recipes);
            require(blocked.hasActiveRecipe() && blocked.getBlockedReason() != null && factory.getRunningThreadCount() == 0
                    && !factory.hasIdleThread() && factory.claimThread() == null, "blocked job released its occupied thread");
            factory.setFactoryCeiling(3, batchCeiling);
            start.invoke(null, factory, machine, context, List.of(recipes.get(0)));
            require(factory.getActiveThreadCount() == 1 && input.getStackInSlot(0).getCount() == 7,
                    "blocked recipe started a duplicate on a free thread");
            int heldEnergy = energy.getEnergyStored();
            tick.invoke(null, blocked, factory, machine, context, recipes);
            require(energy.getEnergyStored() == heldEnergy, "output wait charged energy again");
            clear(output);
            tick.invoke(null, blocked, factory, machine, context, recipes);
            require(!blocked.hasActiveRecipe() && count(output, Items.DIAMOND) == 4, "blocked single job did not resume");

            // Preserve old already-paid parallel jobs. Do not clamp their outputs on upgrade.
            clear(output);
            var old = new CompoundTag();
            old.putString("recipeName", "a"); old.putInt("duration", 2); old.putInt("parallelism", 4); old.putInt("maxParallelism", 4);
            var oldList = new ListTag(); oldList.add(old);
            var oldState = factory.saveWithoutMetadata(); oldState.put("threadList", oldList);
            factory.load(oldState); factory.setFactoryCeiling(1, batchCeiling);
            var legacy = factory.getFactoryRecipeThreadList().get(0);
            require(legacy.getParallelism() == 4, "legacy batch was clamped and would lose paid materials");
            int legacyEnergy = energy.getEnergyStored();
            tick.invoke(null, legacy, factory, machine, context, recipes);
            tick.invoke(null, legacy, factory, machine, context, recipes);
            require(count(output, Items.DIAMOND) == 4 && energy.getEnergyStored() == legacyEnergy - 80,
                    "legacy batch output/energy count changed");
            start.invoke(null, factory, machine, context, recipes);
            require(legacy.hasActiveRecipe() && legacy.getParallelism() == 4 && input.getStackInSlot(0).getCount() == 3,
                    "restored thread did not restart with the retained machine batch multiplier");
            var detached = new FactoryRecipeThread(factory);
            detached.beginRecipe("compatibility", 2, 128);
            require(detached.getParallelism() == 128, "compatibility start API lost batch multiplier");
        } finally {
            for (int i = 0; i < positions.length; i++) level.setBlock(positions[i], before[i], Block.UPDATE_ALL);
        }
    }
    private static MachineRecipe recipe(String name, String input, String output, int duration, int energy) {
        return new MachineRecipe("factory_single_test", name, duration, List.of(
                new RequirementItem(IOType.INPUT, input, 1), new RequirementEnergy(IOType.INPUT, energy),
                new RequirementItem(IOType.OUTPUT, output, 1)), null, false, -1, "");
    }
    private static int count(IItemHandlerModifiable handler, Item item) {
        int count = 0;
        for (int i = 0; i < handler.getSlots(); i++) if (handler.getStackInSlot(i).is(item)) count += handler.getStackInSlot(i).getCount();
        return count;
    }
    private static void clear(IItemHandlerModifiable handler) {
        for (int i = 0; i < handler.getSlots(); i++) handler.setStackInSlot(i, ItemStack.EMPTY);
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
