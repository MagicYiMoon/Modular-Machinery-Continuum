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
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

public final class IngredientArrayRegressionChecks {
    private IngredientArrayRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Requirements.registerBuiltIns();
        require(RequirementTypeRegistry.get(new ResourceLocation("modularmachinery:ingredient_array_input")) != null,
                "array not registered");
        var array = parse("""
                {"amount":2,"items":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:gold_ingot","amount":3}]}
                """);
        require(array.getIngredients().get(0).getAmount() == 2 && array.getIngredients().get(1).getAmount() == 3,
                "root amount inheritance/candidate override failed");
        for (String json : List.of("{}", "{\"items\":[]}", "{\"items\":1}", "{\"items\":[1]}",
                "{\"items\":[{\"item\":\"minecraft:missing_item\"}]}",
                "{\"items\":[{\"item\":\"minecraft:iron_ingot\",\"amount\":1.5}]}",
                "{\"items\":[{\"item\":\"minecraft:iron_ingot\",\"amount\":0}]}",
                "{\"items\":[{\"item\":\"minecraft:iron_ingot\",\"amount\":2147483648}]}",
                "{\"items\":[{\"item\":\"minecraft:iron_ingot\",\"consumeDurability\":1}]}")) {
            boolean refused = false;
            try { parse(json); } catch (RuntimeException expected) { refused = true; }
            require(refused, "invalid array accepted: " + json);
        }
        var outputArray = (RequirementIngredientArray) new IngredientArrayRequirementType().createRequirement(IOType.OUTPUT,
                JsonParser.parseString("{\"items\":[{\"item\":\"minecraft:iron_ingot\",\"minAmount\":2,\"maxAmount\":4,\"chance\":2},{\"item\":\"minecraft:gold_ingot\",\"chance\":1}]}").getAsJsonObject());
        require(outputArray.getIOType() == IOType.OUTPUT && outputArray.getCandidates().get(0).maxAmount() == 4
                && outputArray.select(0.80).item().getRawId().equals("minecraft:gold_ingot"), "weighted output/range parsing failed");
        var outputSynced = (RequirementIngredientArray) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(
                new MachineRecipe("array_test", "weighted_output", 1, List.of(outputArray), null, false, -1, ""), Map.of())).requirements().get(0);
        require(outputSynced.getIOType() == IOType.OUTPUT && outputSynced.getCandidates().get(0).minAmount() == 2
                && outputSynced.getCandidates().get(0).weight() == 2F, "weighted output snapshot lost metadata");
        var tagged = parse("""
                {"items":[{"item":"minecraft:iron_ingot","amount":80,"nbt":{"quality":">=3"},"nbt-display":{"quality":"3"}}]}
                """);
        var modified = (RequirementIngredientArray) new RecipeModifier("modularmachinery:item", IOType.INPUT, 2, 1, false).apply(tagged);
        require(modified.getIngredients().get(0).getAmount() == 160, "item modifier did not target candidates");
        modified.setParallelizeUnaffected(true);
        var recipe = new MachineRecipe("array_test", "array_test", 1, List.of(modified), null, false, -1, "");
        var synced = (RequirementIngredientArray) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of())).requirements().get(0);
        require(synced.getIngredients().get(0).getAmount() == 160 && synced.isParallelizeUnaffected()
                && synced.getIngredients().get(0).getNbt().pattern() != null
                && synced.getIngredients().get(0).asDisplayStack().getTag().getInt("quality") == 3,
                "snapshot lost large amounts, NBT, display or unaffected flag");
        var plainRecipe = new MachineRecipe("array_test", "source_test", 1, List.of(array), null, false, -1, "");
        var staleSource = JsonParser.parseString("{\"nbt\":{\"obsolete\":\"9\"},\"nbt-display\":{\"obsolete\":\"9\"}}").getAsJsonObject();
        var plainSynced = (RequirementIngredientArray) RecipeSnapshotCodec.decode(
                RecipeSnapshotCodec.encode(plainRecipe, Map.of(array, staleSource))).requirements().get(0);
        require(plainSynced.getIngredients().get(0).getNbt().pattern() == null,
                "snapshot inherited stale source NBT instead of actual candidate values");
        var zero = (RequirementIngredientArray) array.scaled(0, 1, IOType.INPUT, false);
        recipe = new MachineRecipe("array_test", "zero_test", 1, List.of(zero), null, false, -1, "");
        require(((RequirementIngredientArray) RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()))
                .requirements().get(0)).getIngredients().get(0).getAmount() == 0, "zero modified amount failed sync");

        BlockPos busPos = StructureMatcher.toWorldOffset(origin, Direction.NORTH, 1, 0, 0);
        var before = level.getBlockState(busPos);
        try {
            level.setBlock(busPos, RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var inventory = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(busPos)).getInventory();
            var pattern = new MachinePattern();
            pattern.addPart(new MachinePattern.Part(1, 0, 0, "minecraft:air"));
            var machine = new DynamicMachine("array_test", "Array test", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var callbackItem = new RequirementItem(IOType.INPUT, "minecraft:iron_ingot", 2);
            callbackItem.setItemChecker((controller, stack) -> stack.hasTag() && stack.getTag().getInt("quality") == 7);
            var checked = new ItemStack(Items.IRON_INGOT, 2);
            checked.getOrCreateTag().putInt("quality", 7);
            require(!callbackItem.matches(context, new ItemStack(Items.IRON_INGOT, 2))
                    && callbackItem.matches(context, checked), "item checker did not gate NBT at runtime");
            callbackItem.addItemModifier((controller, stack) -> stack.copyWithCount(3));
            require(callbackItem.inputAmount(context) == 3, "item input modifier count was not applied");
            var callbackArray = parse("{\"items\":[{\"item\":\"minecraft:iron_ingot\",\"amount\":2}]}");
            callbackArray.setCandidateChecker(0, (controller, stack) -> stack.getCount() == 2);
            callbackArray.addCandidateModifier(0, (controller, stack) -> stack.copyWithCount(4));
            require(callbackArray.matchesCandidate(context, 0, new ItemStack(Items.IRON_INGOT, 2))
                    && callbackArray.inputAmount(context, 0, 2) == 4,
                    "ingredient-array checker/modifier did not apply to its candidate");
            var callbackOutput = (RequirementIngredientArray) new IngredientArrayRequirementType().createRequirement(IOType.OUTPUT,
                    JsonParser.parseString("{\"items\":[{\"item\":\"minecraft:iron_ingot\",\"amount\":2}]}" ).getAsJsonObject());
            callbackOutput.addCandidateModifier(0, (controller, stack) -> stack.copyWithCount(2));
            require(callbackOutput.outputAmount(context, 0, 2) == 4,
                    "ingredient-array output modifier did not multiply the selected amount");
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
            inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 6));
            require(array.getMaxParallelism(context, 10, List.of(array)) == 3, "mixed alternatives did not grant three whole copies");
            require(inventory.getStackInSlot(0).getCount() == 3, "array simulation mutated inventory");
            require(MachineTicker.consumeInputs(List.of(array), context, 3)
                    && inventory.getStackInSlot(0).getCount() == 1 && inventory.getStackInSlot(1).isEmpty(),
                    "mixed consumption did not preserve partial candidate remainder");
            inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 2));
            require(!array.canStart(context) && !MachineTicker.consumeInputs(List.of(array), context, 1)
                    && inventory.getStackInSlot(0).getCount() == 1 && inventory.getStackInSlot(1).getCount() == 2,
                    "fractional alternatives were combined or consumed on failure");
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 3));
            var mandatory = new RequirementItem(IOType.INPUT, "minecraft:iron_ingot", 2);
            require(mandatory.getMaxParallelism(context, 10, List.of(array, mandatory)) == 1,
                    "ordinary input ignored array reservation");
            require(MachineTicker.consumeInputs(List.of(array, mandatory), context, 1)
                    && inventory.getStackInSlot(0).isEmpty() && inventory.getStackInSlot(1).isEmpty(),
                    "mandatory ingredient was stolen by an earlier array");
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            require(!MachineTicker.consumeInputs(List.of(array, array), context, 1)
                    && inventory.getStackInSlot(0).getCount() == 2, "two arrays double-spent inventory");
            var tool = new RequirementItem(IOType.INPUT, "minecraft:iron_pickaxe", 1).withConsumeDurability(10);
            inventory.setStackInSlot(2, new ItemStack(Items.IRON_PICKAXE));
            require(!MachineTicker.consumeInputs(List.of(tool, array, array), context, 1)
                    && inventory.getStackInSlot(2).getDamageValue() == 0, "failed array damaged tool");
            require(MachineTicker.consumeInputs(List.of(tool, array), context, 1)
                    && inventory.getStackInSlot(2).getDamageValue() == 10, "array and durability combination failed");
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            var plan = ItemInputPlan.plan(context, List.of(array), 1, false);
            inventory.setStackInSlot(0, new ItemStack(Items.DIAMOND));
            require(!plan.execute() && inventory.getStackInSlot(0).is(Items.DIAMOND), "stale array plan changed inventory");
            var one = parse("{\"items\":[{\"item\":\"minecraft:iron_ingot\",\"nbt\":{\"quality\":\">=3\"}}]}");
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            require(!one.canStart(context), "array ignored NBT mismatch");
            var qualified = new ItemStack(Items.IRON_INGOT, 2);
            qualified.getOrCreateTag().putInt("quality", 3);
            inventory.setStackInSlot(0, qualified);
            require(one.canStart(context), "array rejected matching NBT");
            var free = (RequirementIngredientArray) one.scaled(0, 1, IOType.INPUT, true);
            require(MachineTicker.consumeInputs(List.of(free), context, 1) && inventory.getStackInSlot(0).getCount() == 2,
                    "zero array chance consumed items");
            one.setParallelizeUnaffected(true);
            require(one.getMaxParallelism(context, 100, List.of(one)) == 100
                    && MachineTicker.consumeInputs(List.of(one), context, 100) && inventory.getStackInSlot(0).getCount() == 1,
                    "unaffected array multiplied input");
            var ore = parse("{\"items\":[{\"item\":\"ore:ingotIron\"}]}");
            require(ore.canStart(context), "legacy ore/tag alternative did not match");
            lifecycle(level, origin, inventory, array);
        } finally { level.setBlock(busPos, before, Block.UPDATE_ALL); }
    }
    private static void lifecycle(ServerLevel level, BlockPos origin, IItemHandlerModifiable inventory,
                                  RequirementIngredientArray array) throws Exception {
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
            var machine = new DynamicMachine("array_test", "Array test", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var recipe = new MachineRecipe("array_test", "array_lifecycle", 2,
                    List.of(array, new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1)), null, false, -1, "");
            var run = new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(recipe);
            var start = MachineTicker.class.getDeclaredMethod("tryStart", controller.getClass(), MachineContext.class,
                    List.class, run.getClass());
            var advance = MachineTicker.class.getDeclaredMethod("advance", controller.getClass(), MachineContext.class,
                    List.class, run.getClass());
            start.setAccessible(true);
            advance.setAccessible(true);
            for (int i = 0; i < inventory.getSlots(); i++) inventory.setStackInSlot(i, ItemStack.EMPTY);
            inventory.setStackInSlot(0, new ItemStack(Items.GOLD_INGOT, 3));
            start.invoke(null, controller, context, List.of(recipe), run);
            require(controller.hasActiveRecipe() && inventory.getStackInSlot(0).isEmpty(), "ordinary start did not consume candidate");
            advance.invoke(null, controller, context, List.of(recipe), run);
            advance.invoke(null, controller, context, List.of(recipe), run);
            require(!controller.hasActiveRecipe() && output.getStackInSlot(0).is(Items.DIAMOND)
                    && output.getStackInSlot(0).getCount() == 1, "ordinary array recipe failed completion after consuming input");
            output.setStackInSlot(0, ItemStack.EMPTY);
            inventory.setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 2));
            inventory.setStackInSlot(1, new ItemStack(Items.GOLD_INGOT, 3));
            factory.setFactoryCeiling(1, 2);
            var factoryStart = hellfirepvp.modularmachinery.common.tiles.FactoryTicker.class.getDeclaredMethod(
                    "startOneRecipe", factory.getClass(), DynamicMachine.class, MachineContext.class, List.class);
            factoryStart.setAccessible(true);
            factoryStart.invoke(null, factory, machine, context, List.of(recipe));
            require(factory.getRunningThreadCount() == 1 && inventory.getStackInSlot(0).isEmpty()
                    && inventory.getStackInSlot(1).isEmpty(), "factory did not consume mixed parallel candidates");
            var thread = factory.getFactoryRecipeThreadList().get(0);
            var tick = hellfirepvp.modularmachinery.common.tiles.FactoryTicker.class.getDeclaredMethod("tickThread",
                    thread.getClass(), factory.getClass(), DynamicMachine.class, MachineContext.class, List.class);
            tick.setAccessible(true);
            tick.invoke(null, thread, factory, machine, context, List.of(recipe));
            tick.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(!thread.hasActiveRecipe() && output.getStackInSlot(0).is(Items.DIAMOND)
                    && output.getStackInSlot(0).getCount() == 2, "factory parallel array output incorrect");
        } finally {
            level.setBlock(origin, oldController, Block.UPDATE_ALL);
            level.setBlock(outputPos, oldOutput, Block.UPDATE_ALL);
            level.setBlock(factoryPos, oldFactory, Block.UPDATE_ALL);
        }
    }

    private static RequirementIngredientArray parse(String json) {
        return (RequirementIngredientArray) new IngredientArrayRequirementType().createRequirement(IOType.INPUT,
                JsonParser.parseString(json).getAsJsonObject());
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
