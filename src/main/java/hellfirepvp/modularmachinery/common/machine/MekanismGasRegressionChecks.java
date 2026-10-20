/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementTypeRegistry;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.Requirements;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.machine.MachinePattern;
import hellfirepvp.modularmachinery.integration.mekanism.MekanismGasBridge;
import hellfirepvp.modularmachinery.integration.mekanism.GasIOPlan;
import hellfirepvp.modularmachinery.integration.mekanism.RequirementGas;
import mekanism.api.Action;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.GasStack;
import mekanism.api.chemical.gas.IGasHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fml.ModList;

/** Server checks for the optional hybrid fluid/gas hatch. Only invoked with Mekanism installed. */
public final class MekanismGasRegressionChecks {
    private MekanismGasRegressionChecks() { }

    public static void run(net.minecraft.server.level.ServerLevel level, BlockPos pos) {
        if (!ModList.get().isLoaded("mekanism")) return;
        BlockPos secondPos = pos.east();
        BlockPos itemPos = pos.north();
        var old = level.getBlockState(pos);
        var oldSecond = level.getBlockState(secondPos);
        var oldItem = level.getBlockState(itemPos);
        try {
            level.setBlock(pos, RegistryBlocks.fluidInputHatch.get().defaultBlockState(), Block.UPDATE_ALL);
            require(level.getBlockEntity(pos) instanceof TileFluidTank, "gas fixture tile missing");
            TileFluidTank hatch = (TileFluidTank) level.getBlockEntity(pos);
            IGasHandler gas = hatch.getCapability(MekanismGasBridge.capability())
                    .orElseThrow(() -> new IllegalStateException("gas capability missing"));
            var hydrogen = MekanismAPI.gasRegistry().getValue(new ResourceLocation("mekanism:hydrogen"));
            require(hydrogen != null, "Mekanism hydrogen missing");
            GasStack source = new GasStack(hydrogen, 500);
            require(gas.insertChemical(0, source, Action.SIMULATE).getAmount() == 0, "gas simulate did not fit");
            require(!hatch.hasGas(), "gas simulate mutated hatch");
            require(gas.insertChemical(0, source, Action.EXECUTE).isEmpty() && hatch.getGasAmount() == 500,
                    "gas execute failed");
            require(hatch.getTank().fill(new net.minecraftforge.fluids.FluidStack(Fluids.WATER, 100),
                    net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE) == 0,
                    "fluid entered gas-filled hatch");
            require(gas.insertChemical(0, new GasStack(hydrogen, 100), Action.SIMULATE).getAmount() == 0
                            && hatch.getGasAmount() == 500,
                    "same gas did not stack");
            var other = MekanismAPI.gasRegistry().getValue(new ResourceLocation("mekanism:oxygen"));
            if (other != null) require(gas.insertChemical(0, new GasStack(other, 100), Action.EXECUTE).getAmount() == 100,
                    "different gas replaced existing gas");
            var saved = hatch.getUpdateTag();
            hatch.clearGas();
            hatch.load(saved);
            require(hatch.hasGas() && hatch.getGasAmount() == 500, "gas NBT did not round-trip");
            Requirements.registerBuiltIns();
            require(RequirementTypeRegistry.get(new ResourceLocation("modularmachinery:gas")) != null,
                    "gas requirement type was not registered");
            require(RequirementTypeRegistry.get(new ResourceLocation("modularmachinery:gas_pertick")) != null,
                    "gas_pertick requirement type was not registered");
            var requirement = new RequirementGas(IOType.INPUT, "mekanism:hydrogen", 100, 1F);
            MachinePattern pattern = new MachinePattern();
            var machine = new DynamicMachine("gas_test", "gas_test", pattern);
            var context = new MachineContext(level, pos, net.minecraft.core.Direction.NORTH, machine,
                    new MachineVariables(), pattern);
            context.overrideComponentPositions(java.util.List.of(pos));
            require(requirement.canStart(context), "gas requirement did not see hatch contents");
            require(requirement.consume(context, 1) && hatch.getGasAmount() == 400,
                    "gas requirement did not consume input");
            var rate = (hellfirepvp.modularmachinery.integration.mekanism.RequirementGasPerTick)
                    RequirementTypeRegistry.get(new ResourceLocation("modularmachinery:gas_pertick"))
                            .createRequirement(IOType.INPUT, com.google.gson.JsonParser.parseString(
                                    "{\"gas\":\"mekanism:hydrogen\",\"amount\":100}").getAsJsonObject());
            require(hellfirepvp.modularmachinery.integration.mekanism.GasTickIO
                            .plan(context, java.util.List.of(rate), 2).execute()
                            && hatch.getGasAmount() == 200,
                    "gas_pertick did not consume one parallelized tick atomically");
            level.setBlock(pos, RegistryBlocks.fluidOutputHatch.get().defaultBlockState(), Block.UPDATE_ALL);
            TileFluidTank output = (TileFluidTank) level.getBlockEntity(pos);
            var outputContext = new MachineContext(level, pos, net.minecraft.core.Direction.NORTH, machine,
                    new MachineVariables(), pattern);
            outputContext.overrideComponentPositions(java.util.List.of(pos));
            var outputRequirement = new RequirementGas(IOType.OUTPUT, "mekanism:hydrogen", 200, 1F);
            require(outputRequirement.canStart(outputContext), "gas output room was not detected");
            require(outputRequirement.produce(outputContext, 1) == 200 && output.getGasAmount() == 200,
                    "gas requirement did not produce output");

            // Shared reservations: two gas requirements may not both claim the same stored amount.
            level.setBlock(pos, RegistryBlocks.fluidInputHatch.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(secondPos, RegistryBlocks.fluidInputHatch.get().defaultBlockState(), Block.UPDATE_ALL);
            TileFluidTank firstIn = (TileFluidTank) level.getBlockEntity(pos);
            TileFluidTank secondIn = (TileFluidTank) level.getBlockEntity(secondPos);
            MekanismGasBridge.handler(firstIn, true).insertChemical(0, new GasStack(hydrogen, 500), Action.EXECUTE);
            MekanismGasBridge.handler(secondIn, true).insertChemical(0, new GasStack(hydrogen, 300), Action.EXECUTE);
            var twoInputs = new MachineContext(level, pos, net.minecraft.core.Direction.NORTH, machine,
                    new MachineVariables(), pattern);
            twoInputs.overrideComponentPositions(java.util.List.of(pos, secondPos));
            var gas600 = new RequirementGas(IOType.INPUT, "mekanism:hydrogen", 600, 1F);
            var gas300 = new RequirementGas(IOType.INPUT, "mekanism:hydrogen", 300, 1F);
            require(!GasIOPlan.inputs(twoInputs, java.util.List.of(gas600, gas300), 1).valid()
                            && firstIn.getGasAmount() == 500 && secondIn.getGasAmount() == 300,
                    "shared gas planning double-counted or mutated inputs");
            require(gas600.getMaxParallelism(twoInputs, 10, java.util.List.of(gas600)) == 1,
                    "gas parallel limit ignored aggregate hatch contents");
            require(GasIOPlan.inputs(twoInputs, java.util.List.of(gas600), 1).execute()
                            && firstIn.getGasAmount() + secondIn.getGasAmount() == 200,
                    "gas input did not aggregate across hatches");

            // All one-shot inputs are preflighted before item mutation.
            level.setBlock(itemPos, RegistryBlocks.itemInputBus.get().defaultBlockState(), Block.UPDATE_ALL);
            var itemBus = (hellfirepvp.modularmachinery.common.tiles.base.TileItemBus) level.getBlockEntity(itemPos);
            ((net.minecraftforge.items.IItemHandlerModifiable) itemBus.getInventory()).setStackInSlot(0, new net.minecraft.world.item.ItemStack(
                    net.minecraft.world.item.Items.COBBLESTONE, 4));
            twoInputs.overrideComponentPositions(java.util.List.of(pos, secondPos, itemPos));
            var impossibleGas = new RequirementGas(IOType.INPUT, "mekanism:hydrogen", 300, 1F);
            var oneStone = new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem(
                    IOType.INPUT, "minecraft:cobblestone", 1);
            require(!hellfirepvp.modularmachinery.common.tiles.MachineTicker.consumeInputs(
                            java.util.List.of(oneStone, impossibleGas), twoInputs, 1)
                            && itemBus.getInventory().getStackInSlot(0).getCount() == 4,
                    "gas shortfall partially consumed an item input");

            // Fluid and gas outputs share ordinary hatch storage. The planner and writer both give fluid
            // the first hatch, then place gas in the second.
            level.setBlock(pos, RegistryBlocks.fluidOutputHatch.get().defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(secondPos, RegistryBlocks.fluidOutputHatch.get().defaultBlockState(), Block.UPDATE_ALL);
            TileFluidTank firstOut = (TileFluidTank) level.getBlockEntity(pos);
            TileFluidTank secondOut = (TileFluidTank) level.getBlockEntity(secondPos);
            var twoOutputs = new MachineContext(level, pos, net.minecraft.core.Direction.NORTH, machine,
                    new MachineVariables(), pattern);
            var waterOut = new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid(
                    IOType.OUTPUT, "minecraft:water", 100, 1F);
            var gasOut = new RequirementGas(IOType.OUTPUT, "mekanism:hydrogen", 200, 1F);
            twoOutputs.overrideComponentPositions(java.util.List.of(pos));
            require(!GasIOPlan.outputs(twoOutputs, java.util.List.of(gasOut, waterOut), 1).valid(),
                    "fluid and gas outputs claimed the same hybrid hatch");
            twoOutputs.overrideComponentPositions(java.util.List.of(pos, secondPos));
            require(GasIOPlan.outputs(twoOutputs, java.util.List.of(gasOut, waterOut), 1).valid(),
                    "separate hybrid hatches did not satisfy mixed outputs");
            var mixedRecipe = new hellfirepvp.modularmachinery.common.crafting.MachineRecipe(
                    "gas_test", "gas_mixed_output", 1, java.util.List.of(gasOut, waterOut), null,
                    false, -1, "");
            hellfirepvp.modularmachinery.common.tiles.MachineTicker.emitOutputs(
                    mixedRecipe, mixedRecipe.requirements(), twoOutputs, 1);
            require(firstOut.getTank().getFluidAmount() == 100 && !firstOut.hasGas()
                            && secondOut.getGasAmount() == 200 && secondOut.getTank().isEmpty(),
                    "mixed output emission order disagreed with allocation");

            // Optional outputs never hold completion when no hatch can accept them.
            secondOut.clearGas();
            firstOut.getTank().setFluid(new net.minecraftforge.fluids.FluidStack(Fluids.WATER,
                    firstOut.getTank().getCapacity()));
            secondOut.getTank().setFluid(new net.minecraftforge.fluids.FluidStack(Fluids.WATER,
                    secondOut.getTank().getCapacity()));
            var optional = new RequirementGas(IOType.OUTPUT, "mekanism:hydrogen", 100, 0.5F);
            require(GasIOPlan.outputs(twoOutputs, java.util.List.of(optional), 1).valid(),
                    "optional gas output incorrectly held a completed craft");

            // Server recipe snapshots carry the optional requirement without loading client-only code.
            gasOut.setSelectorTag("gas_line");
            gasOut.setParallelizeUnaffected(true);
            var snapshotRecipe = new hellfirepvp.modularmachinery.common.crafting.MachineRecipe(
                    "gas_test", "gas_snapshot", 20, java.util.List.of(gasOut), null, false, -1, "");
            var decoded = hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec.decode(
                    hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec.encode(snapshotRecipe, java.util.Map.of()));
            require(decoded.requirements().get(0) instanceof RequirementGas decodedGas
                            && "gas_line".equals(decodedGas.getSelectorTag())
                            && decodedGas.isParallelizeUnaffected() && decodedGas.getAmount() == 200,
                    "gas recipe snapshot lost requirement metadata");
            var rateSnapshot = new hellfirepvp.modularmachinery.common.crafting.MachineRecipe(
                    "gas_test", "gas_rate_snapshot", 20, java.util.List.of(rate), null, false, -1, "");
            rate.setSelectorTag("rate_line");
            var decodedRate = hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec.decode(
                    hellfirepvp.modularmachinery.common.crafting.RecipeSnapshotCodec.encode(rateSnapshot, java.util.Map.of()));
            require(decodedRate.requirements().get(0) instanceof hellfirepvp.modularmachinery.integration.mekanism.RequirementGasPerTick
                            && "rate_line".equals(decodedRate.requirements().get(0).getSelectorTag()),
                    "gas_pertick snapshot lost selector metadata");
            Modularmachinery.LOG.info("[MMCE][regression] Mekanism gas recipes and hybrid hatches PASS");
        } finally {
            level.setBlock(pos, old, Block.UPDATE_ALL);
            level.setBlock(secondPos, oldSecond, Block.UPDATE_ALL);
            level.setBlock(itemPos, oldItem, Block.UPDATE_ALL);
        }
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalStateException(message);
    }
}
