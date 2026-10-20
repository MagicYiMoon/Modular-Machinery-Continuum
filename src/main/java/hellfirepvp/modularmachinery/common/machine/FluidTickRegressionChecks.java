/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.machine;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.util.*;

/** Invokes production tick paths against isolated real hatches, including ordinary/factory lifecycle. */
public final class FluidTickRegressionChecks {
    private FluidTickRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) throws Exception {
        Requirements.registerBuiltIns();
        var type = RequirementTypeRegistry.get(new net.minecraft.resources.ResourceLocation("modularmachinery:fluid_pertick"));
        require(type != null, "fluid_pertick was not registered");
        var input = (RequirementFluidPerTick) type.createRequirement(IOType.INPUT,
                JsonParser.parseString("{\"fluid\":\"minecraft:water\",\"amount\":100,\"nbt\":{\"quality\":\">=3\"}}").getAsJsonObject());
        var output = (RequirementFluidPerTick) type.createRequirement(IOType.OUTPUT,
                JsonParser.parseString("{\"fluid\":\"minecraft:lava\",\"amount\":50,\"nbt\":{\"quality\":\"4\"}}").getAsJsonObject());
        var modifier = RecipeModifier.deserialize(JsonParser.parseString(
                "{\"target\":\"modularmachinery:fluid\",\"io\":\"input\",\"operation\":1,\"multiplier\":2}").getAsJsonObject());
        var modified = (RequirementFluidPerTick) modifier.apply(input);
        require(modified.fluid().getAmount() == 200, "upstream fluid modifier does not target per-tick fluid");
        boolean rejected = false;
        try { type.createRequirement(IOType.INPUT, JsonParser.parseString(
                "{\"fluid\":\"minecraft:water\",\"amount\":100,\"chance\":0.5}").getAsJsonObject()); }
        catch (RuntimeException expected) { rejected = true; }
        require(rejected, "unsupported probabilistic tick rate silently accepted");

        Block[] blocks = {RegistryBlocks.blockController.get(), RegistryBlocks.fluidInputHatch.get(),
                RegistryBlocks.fluidOutputHatch.get(), RegistryBlocks.energyInputHatch.get(),
                RegistryBlocks.itemOutputBus.get(), RegistryBlocks.blockFactoryController.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        BlockPos[] positions = new BlockPos[blocks.length];
        MachinePattern pattern = new MachinePattern();
        for (int i = 0; i < blocks.length; i++) {
            positions[i] = StructureMatcher.toWorldOffset(origin, Direction.NORTH, i, 0, 0);
            before[i] = level.getBlockState(positions[i]);
            if (i > 0 && i < 5) pattern.addPart(new MachinePattern.Part(i, 0, 0, "minecraft:air"));
        }
        try {
            for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], blocks[i].defaultBlockState(), Block.UPDATE_ALL);
            var machine = new DynamicMachine("fluid_tick_test", "Fluid tick test", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            var controller = (TileController) level.getBlockEntity(origin);
            var in = (TileFluidTank) level.getBlockEntity(positions[1]);
            var out = (TileFluidTank) level.getBlockEntity(positions[2]);
            var energy = (TileEnergyHatch) level.getBlockEntity(positions[3]);
            var bus = (IItemHandlerModifiable) ((TileItemBus) level.getBlockEntity(positions[4])).getInventory();
            var factory = (TileFactoryController) level.getBlockEntity(positions[5]);
            var requirements = List.<Requirement>of(input, output, new RequirementEnergy(IOType.INPUT, 10),
                    new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1));
            var recipe = recipe(requirements, false);
            var decoded = RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe, Map.of()));
            require(decoded.requirements().get(0) instanceof RequirementFluidPerTick rate
                    && rate.fluid().getNbt().pattern() != null, "tick rate/NBT lost in network snapshot");
            seed(in, 150);
            require(FluidTickIO.plan(context, List.of(input, input), 1).failure() != null,
                    "two tick requirements double-counted same input tank");
            require(in.getTank().getFluidAmount() == 150, "planning consumed input");
            seed(in, 600);
            require(input.getMaxParallelism(context, 10, requirements) == 6, "tick parallel grant ignored input limit");
            int seededEnergy = energy.getEnergyStorage().receiveEnergy(1000, false);
            require(MachineTicker.processPerTick(recipe, requirements, context, 2, false) == null,
                    "parallel tick did not execute");
            require(in.getTank().getFluidAmount() == 400 && out.getTank().getFluidAmount() == 100
                    && out.getTank().getFluid().getTag().getInt("quality") == 4, "parallel fluid IO or output NBT wrong");
            require(energy.getEnergyStorage().getEnergyStored() == seededEnergy - 20, "parallel energy payment wrong");
            seed(in, 50);
            require(MachineTicker.processPerTick(recipe, requirements, context, 2, false) != null
                    && in.getTank().getFluidAmount() == 50 && out.getTank().getFluidAmount() == 100
                    && energy.getEnergyStorage().getEnergyStored() == seededEnergy - 20, "fluid shortfall partially paid a tick");
            seed(in, 500);
            energy.getEnergyStorage().extractEnergy(Integer.MAX_VALUE, false);
            require(MachineTicker.processPerTick(recipe, requirements, context, 1, false) != null
                    && in.getTank().getFluidAmount() == 500, "energy shortfall consumed fluid");
            int refillEnergy = energy.getEnergyStorage().receiveEnergy(1000, false);
            out.getTank().setFluid(new FluidStack(output.fluid().asStack(), out.getTank().getCapacity()));
            require(MachineTicker.processPerTick(recipe, requirements, context, 1, false) != null
                    && in.getTank().getFluidAmount() == 500
                    && energy.getEnergyStorage().getEnergyStored() == refillEnergy, "full output partially paid a tick");
            out.getTank().setFluid(FluidStack.EMPTY);

            // Call the actual advancement methods, bypassing recipe search so fixtures need no config mutation.
            var advance = MachineTicker.class.getDeclaredMethod("advance", TileController.class,
                    MachineContext.class, List.class, RecipeCraftingContext.class);
            advance.setAccessible(true);
            controller.beginRecipe(recipe.registryName(), 2, 1);
            seed(in, 0);
            advance.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
            require(controller.hasActiveRecipe() && controller.getRecipeTicks() == 0
                    && "craftcheck.failure.fluid.input".equals(controller.getStatusMessage()), "ordinary controller did not pause");
            seed(in, 500);
            advance.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
            require(controller.getRecipeTicks() == 1 && in.getTank().getFluidAmount() == 400, "ordinary controller did not resume");
            for (int i = 0; i < bus.getSlots(); i++) bus.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            advance.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
            require(controller.getRecipeTicks() == 2 && in.getTank().getFluidAmount() == 300, "duration tick payment wrong");
            for (int i = 0; i < 3; i++) advance.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
            require(in.getTank().getFluidAmount() == 300 && out.getTank().getFluidAmount() == 100,
                    "completed ordinary recipe paid continuous rate while waiting");
            bus.setStackInSlot(0, ItemStack.EMPTY);
            advance.invoke(null, controller, context, List.of(recipe), new RecipeCraftingContext(recipe));
            require(!controller.hasActiveRecipe() && bus.getStackInSlot(0).is(Items.DIAMOND)
                    && in.getTank().getFluidAmount() == 300, "ordinary completion duplicated tick IO");
            var cancel = recipe(requirements, true);
            controller.beginRecipe(cancel.registryName(), 2, 1);
            seed(in, 0);
            advance.invoke(null, controller, context, List.of(cancel), new RecipeCraftingContext(cancel));
            require(!controller.hasActiveRecipe(), "cancelOnPerTickFailure ignored on ordinary controller");

            var tickThread = FactoryTicker.class.getDeclaredMethod("tickThread", FactoryRecipeThread.class,
                    TileFactoryController.class, DynamicMachine.class, MachineContext.class, List.class);
            tickThread.setAccessible(true);
            var thread = new FactoryRecipeThread(factory);
            thread.beginRecipe(recipe.registryName(), 2, 1);
            tickThread.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(thread.getRecipeTicks() == 0 && thread.getBlockedReason() != null, "factory thread did not pause");
            seed(in, 500);
            out.getTank().setFluid(FluidStack.EMPTY);
            tickThread.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(thread.getRecipeTicks() == 1 && in.getTank().getFluidAmount() == 400, "factory thread did not resume");
            var saved = thread.serialize();
            thread = FactoryRecipeThread.deserialize(saved, factory);
            require(thread != null && thread.getRecipeTicks() == 1, "factory progress restore failed");
            for (int i = 0; i < bus.getSlots(); i++) bus.setStackInSlot(i, new ItemStack(Items.COBBLESTONE, 64));
            tickThread.invoke(null, thread, factory, machine, context, List.of(recipe));
            tickThread.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(thread.getRecipeTicks() == 2 && in.getTank().getFluidAmount() == 300
                    && out.getTank().getFluidAmount() == 100, "factory completion wait repeated fluid IO");
            bus.setStackInSlot(0, ItemStack.EMPTY);
            tickThread.invoke(null, thread, factory, machine, context, List.of(recipe));
            require(!thread.hasActiveRecipe() && bus.getStackInSlot(0).is(Items.DIAMOND), "factory did not finish");
            thread.beginRecipe(cancel.registryName(), 2, 1);
            seed(in, 0);
            tickThread.invoke(null, thread, factory, machine, context, List.of(cancel));
            require(!thread.hasActiveRecipe(), "factory ignored cancelOnPerTickFailure");
        } finally {
            for (int i = 0; i < blocks.length; i++) level.setBlock(positions[i], before[i], Block.UPDATE_ALL);
        }
    }
    private static MachineRecipe recipe(List<Requirement> requirements, boolean cancel) {
        return new MachineRecipe("fluid_tick_test", "fluid_tick_test", 2, requirements, null, cancel, -1, "");
    }
    private static void seed(TileFluidTank tank, int amount) {
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putInt("quality", 3);
        tank.getTank().setFluid(new FluidStack(Fluids.WATER, amount, tag));
    }
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }
}
