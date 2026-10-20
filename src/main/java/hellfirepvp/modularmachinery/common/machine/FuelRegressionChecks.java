/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.ItemInputPlan;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.*;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

public final class FuelRegressionChecks {
    private FuelRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        var fuel = parse("{\"item\":\"any:fuel\",\"time\":1600,\"amount\":64}");
        require(fuel.getTime() == 1600, "fuel treated amount as time");
        for (String value : List.of("-1", "0", "1.5", "2147483648", "\"1600\"", "null")) {
            boolean refused = false;
            try { parse("{\"item\":\"any:fuel\",\"time\":" + value + "}"); }
            catch (RuntimeException expected) { refused = true; }
            require(refused, "invalid fuel time accepted: " + value);
        }
        for (String json : List.of("{\"item\":\"any:fuel\"}", "{\"item\":\"any:fuel\",\"time\":1,\"consumeDurability\":1}")) {
            boolean refused = false;
            try { parse(json); } catch (RuntimeException expected) { refused = true; }
            require(refused, "invalid fuel requirement accepted");
        }
        boolean refused = false;
        try { new ItemRequirementType().createRequirement(IOType.OUTPUT, JsonParser.parseString(
                "{\"item\":\"any:fuel\",\"time\":1600}").getAsJsonObject()); }
        catch (RuntimeException expected) { refused = true; }
        require(refused, "fuel output accepted");
        var tagged = parse("{\"item\":\"any:fuel\",\"time\":1600,\"nbt\":{\"quality\":\">=3\"},\"nbt-display\":{\"quality\":\"3\"}}");
        var scaled = (RequirementFuel) new RecipeModifier("modularmachinery:item", IOType.INPUT, 2, 1, false).apply(tagged);
        require(scaled.getTime() == 3200, "item modifier did not scale fuel time");
        scaled.setParallelizeUnaffected(true);
        var recipe = new MachineRecipe("fuel_test", "fuel_test", 2, List.of(scaled), null, false, -1, "");
        var synced = (RequirementFuel) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of())).requirements().get(0);
        require(synced.getTime() == 3200 && synced.isParallelizeUnaffected() && synced.getNbt().pattern() != null
                && synced.getNbt().display().getInt("quality") == 3, "fuel sync lost values/NBT");
        var zero = (RequirementFuel) fuel.scaled(0, 1, IOType.INPUT, false);
        recipe = new MachineRecipe("fuel_test", "zero_test", 2, List.of(zero), null, false, -1, "");
        require(((RequirementFuel) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()))
                .requirements().get(0)).getTime() == 0, "zero modified fuel failed sync");
        require(RequirementFuel.burnTime(new ItemStack(Items.COAL)) == 1600, "coal furnace time unexpected");

        BlockPos busPos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        var before = level.getBlockState(busPos);
        try {
            level.setBlock(busPos, RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var inventory = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(busPos)).getInventory();
            var pattern = new MachinePattern();
            pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("fuel_test", "Fuel test", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            inventory.setStackInSlot(0, new ItemStack(Items.COAL, 2));
            inventory.setStackInSlot(1, new ItemStack(Items.CHARCOAL, 1));
            require(fuel.canStart(context) && fuel.getMaxParallelism(context, 10, List.of(fuel)) == 3
                    && inventory.getStackInSlot(0).getCount() == 2, "fuel simulation/parallel capacity incorrect");
            require(MachineTicker.consumeInputs(List.of(fuel), context, 3)
                    && inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty(), "mixed fuel consumption failed");
            inventory.setStackInSlot(0, new ItemStack(Items.COAL));
            require(!MachineTicker.consumeInputs(List.of(fuel, fuel), context, 1)
                    && inventory.getStackInSlot(0).getCount() == 1, "fuel shortfall partially consumed or double-spent inventory");
            var small = new RequirementFuel(100, 1, RecipeNbt.EMPTY);
            require(MachineTicker.consumeInputs(List.of(small), context, 1) && inventory.getStackInSlot(0).isEmpty()
                    && !small.canStart(context), "excess burn time incorrectly carried forward");
            inventory.setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET));
            require(!new RequirementFuel(20001, 1, RecipeNbt.EMPTY).canStart(context)
                    && inventory.getStackInSlot(0).is(Items.LAVA_BUCKET), "insufficient container fuel mutated inventory");
            inventory.setStackInSlot(1, new ItemStack(Items.COAL));
            require(MachineTicker.consumeInputs(List.of(new RequirementFuel(21600, 1, RecipeNbt.EMPTY)), context, 1)
                    && inventory.getStackInSlot(0).is(Items.BUCKET) && inventory.getStackInSlot(1).isEmpty(),
                    "lava bucket remainder lost during mixed fuel payment");
            var saved = level.getBlockEntity(busPos).saveWithoutMetadata();
            inventory.setStackInSlot(0, ItemStack.EMPTY);
            level.getBlockEntity(busPos).load(saved);
            require(inventory.getStackInSlot(0).is(Items.BUCKET), "fuel remainder not persisted");
            inventory.setStackInSlot(0, new ItemStack(Items.LAVA_BUCKET, 2));
            require(!fuel.canStart(context), "stacked container fuel unexpectedly accepted");
            inventory.setStackInSlot(0, new ItemStack(Items.COAL));
            require(!tagged.canStart(context), "fuel NBT ignored");
            var qualified = new ItemStack(Items.COAL);
            qualified.getOrCreateTag().putInt("quality", 3);
            inventory.setStackInSlot(0, qualified);
            require(tagged.canStart(context), "fuel NBT match rejected");
            var free = (RequirementFuel) tagged.scaled(0, 1, IOType.INPUT, true);
            require(MachineTicker.consumeInputs(List.of(free), context, 1) && inventory.getStackInSlot(0).getCount() == 1,
                    "zero chance consumed fuel");
            var exempt = new RequirementFuel(1600, 1, RecipeNbt.EMPTY);
            exempt.setParallelizeUnaffected(true);
            require(exempt.getMaxParallelism(context, 100, List.of(exempt)) == 100
                    && MachineTicker.consumeInputs(List.of(exempt), context, 100) && inventory.getStackInSlot(0).isEmpty(),
                    "unaffected fuel was multiplied");
            inventory.setStackInSlot(0, new ItemStack(Items.COAL, 2));
            var material = new RequirementItem(IOType.INPUT, "minecraft:coal", 1);
            require(material.getMaxParallelism(context, 4, List.of(fuel, material)) == 1
                    && MachineTicker.consumeInputs(List.of(fuel, material), context, 1) && inventory.getStackInSlot(0).isEmpty(),
                    "ordinary material and fuel double-spent coal");
            inventory.setStackInSlot(0, new ItemStack(Items.COAL));
            var alternatives = (RequirementIngredientArray) new IngredientArrayRequirementType().createRequirement(IOType.INPUT,
                    JsonParser.parseString("{\"items\":[{\"item\":\"minecraft:coal\"}]}").getAsJsonObject());
            require(!MachineTicker.consumeInputs(List.of(fuel, alternatives), context, 1)
                    && inventory.getStackInSlot(0).getCount() == 1, "array/fuel conflict consumed coal");
            var plan = ItemInputPlan.plan(context, List.of(fuel), 1, false);
            inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND));
            require(!plan.execute() && inventory.getStackInSlot(0).is(Items.DIAMOND), "stale fuel plan changed inventory");
            require(!fuel.canStart(context), "nonfuel item accepted");
            inventory.setStackInSlot(0, new ItemStack(Items.COAL, 64));
            require(new RequirementFuel(Integer.MAX_VALUE, 1, RecipeNbt.EMPTY)
                    .getMaxParallelism(context, Integer.MAX_VALUE, null) == 0, "large fuel arithmetic overflowed");
            lifecycle(level, origin, inventory, fuel);
        } finally { level.setBlock(busPos, before, Block.UPDATE_ALL); }
    }
    private static void lifecycle(ServerLevel level, BlockPos origin, IItemHandlerModifiable inventory,
                                  RequirementFuel fuel) throws Exception {
        BlockPos outputPos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 2, 0, 0);
        BlockPos factoryPos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 3, 0, 0);
        var oldController = level.getBlockState(origin);
        var oldOutput = level.getBlockState(outputPos);
        var oldFactory = level.getBlockState(factoryPos);
        try {
            level.setBlock(origin, RegistryBlocks.blockController.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(outputPos, RegistryBlocks.itemOutputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(factoryPos, RegistryBlocks.blockFactoryController.get().defaultBlockState(), Block.UPDATE_ALL);
            var controller = (hellfirepvp.modularmachinery.common.tiles.TileController) level.getBlockEntity(origin);
            var factory = (hellfirepvp.modularmachinery.common.tiles.TileFactoryController) level.getBlockEntity(factoryPos);
            var output = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(outputPos)).getInventory();
            var pattern = new MachinePattern();
            pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            pattern.addPart(new MachinePattern.Part(2, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("fuel_test", "Fuel test", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var recipe = new MachineRecipe("fuel_test", "fuel_lifecycle", 2,
                    List.of(fuel, new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1)), null, false, -1, "");
            var run = new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(recipe);
            var start = MachineTicker.class.getDeclaredMethod("tryStart", controller.getClass(), MachineContext.class,
                    List.class, run.getClass());
            var advance = MachineTicker.class.getDeclaredMethod("advance", controller.getClass(), MachineContext.class,
                    List.class, run.getClass());
            start.setAccessible(true);
            advance.setAccessible(true);
            for (int i = 0; i < inventory.getSlots(); i++) inventory.setStackInSlot(i, ItemStack.EMPTY);
            inventory.setStackInSlot(0, new ItemStack(Items.CHARCOAL, 1));
            start.invoke(null, controller, context, List.of(recipe), run);
            require(controller.hasActiveRecipe() && inventory.getStackInSlot(0).isEmpty(), "ordinary start did not consume fuel");
            advance.invoke(null, controller, context, List.of(recipe), run);
            advance.invoke(null, controller, context, List.of(recipe), run);
            require(!controller.hasActiveRecipe() && output.getStackInSlot(0).is(Items.DIAMOND)
                    && output.getStackInSlot(0).getCount() == 1, "ordinary fuel recipe failed completion after consuming input");
            output.setStackInSlot(0, ItemStack.EMPTY);
            inventory.setStackInSlot(0, new ItemStack(Items.COAL, 1));
            inventory.setStackInSlot(1, new ItemStack(Items.CHARCOAL, 1));
            factory.setFactoryCeiling(1, 2);
            var factoryStart = hellfirepvp.modularmachinery.common.tiles.FactoryTicker.class.getDeclaredMethod(
                    "startOneRecipe", factory.getClass(), DynamicMachine.class, MachineContext.class, List.class);
            factoryStart.setAccessible(true);
            factoryStart.invoke(null, factory, machine, context, List.of(recipe));
            require(factory.getRunningThreadCount() == 1 && inventory.getStackInSlot(0).isEmpty()
                    && inventory.getStackInSlot(1).isEmpty(), "factory did not consume mixed parallel fuels");
            var thread = factory.getFactoryRecipeThreadList().get(0);
            var tick = hellfirepvp.modularmachinery.common.tiles.FactoryTicker.class.getDeclaredMethod("tickThread",
                    thread.getClass(), factory.getClass(), DynamicMachine.class, MachineContext.class, List.class);
            tick.setAccessible(true);
            tick.invoke(null, thread, factory, machine, context, List.of(recipe));
            tick.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(!thread.hasActiveRecipe() && output.getStackInSlot(0).is(Items.DIAMOND)
                    && output.getStackInSlot(0).getCount() == 2, "factory parallel fuel output incorrect");
        } finally {
            level.setBlock(origin, oldController, Block.UPDATE_ALL);
            level.setBlock(outputPos, oldOutput, Block.UPDATE_ALL);
            level.setBlock(factoryPos, oldFactory, Block.UPDATE_ALL);
        }
    }

    private static RequirementFuel parse(String json) {
        return (RequirementFuel) new ItemRequirementType().createRequirement(IOType.INPUT, JsonParser.parseString(json).getAsJsonObject());
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
