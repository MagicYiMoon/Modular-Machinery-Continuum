/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.appmek;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.integration.botania.*;
import hellfirepvp.modularmachinery.integration.mekanism.*;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import java.util.*;

/** Prevents the first extension channel from masking another channel's payment or output check. */
public final class MixedResourceRegressionChecks {
    private MixedResourceRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) {
        Block[] blocks = {RegistryMEBlocks.all().get("blockmegasinputbus").get(), RegistryMEBlocks.all().get("blockmegasoutputbus").get(),
                RegistryModularMagicBlocks.blockManaProviderInput.get(), RegistryModularMagicBlocks.blockManaProviderOutput.get(), RegistryBlocks.energyInputHatch.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        try {
            for (int i = 0; i < blocks.length; i++) { before[i] = level.getBlockState(origin.offset(i, 0, 0)); level.setBlock(origin.offset(i, 0, 0), blocks[i].defaultBlockState(), Block.UPDATE_ALL); }
            var gasIn = (MEGasBus) level.getBlockEntity(origin); var gasOut = (MEGasBus) level.getBlockEntity(origin.east());
            var manaIn = (TileManaProvider) level.getBlockEntity(origin.offset(2, 0, 0)); var manaOut = (TileManaProvider) level.getBlockEntity(origin.offset(3, 0, 0));
            var power = ((TileEnergyInputHatch) level.getBlockEntity(origin.offset(4, 0, 0))).getEnergyStorage();
            var machine = new DynamicMachine("mixed_resource_test", "Mixed", new MachinePattern());
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
            context.overrideComponentPositions(List.of(origin, origin.east(), origin.offset(2, 0, 0), origin.offset(3, 0, 0), origin.offset(4, 0, 0)));
            var gas = new RequirementGas(IOType.INPUT, "mekanism:hydrogen", 100, 1F); var mana = new RequirementMana(IOType.INPUT, 100);
            for (boolean reversed : List.of(false, true)) {
                gasIn.gasTanks().get(0).setGasTag(gas.asStack(1000).write(new net.minecraft.nbt.CompoundTag())); manaIn.take(Long.MAX_VALUE, false); manaIn.add(1000, false);
                List<Requirement> needs = reversed ? List.of(mana, gas) : List.of(gas, mana);
                require(MachineTicker.consumeInputs(needs, context, 2) && gasIn.stored(0).getAmount() == 800 && manaIn.getCurrentMana() == 800, "both extension channels paid regardless of order");
                manaIn.take(Long.MAX_VALUE, false);
                require(!MachineTicker.consumeInputs(needs, context, 2) && gasIn.stored(0).getAmount() == 800, "second-channel shortage prevents first-channel consumption");
                var gasOutput = new RequirementGas(IOType.OUTPUT, "mekanism:oxygen", 100, 1F); var manaOutput = new RequirementMana(IOType.OUTPUT, 100);
                manaOut.add(Long.MAX_VALUE, false);
                var outputs = reversed ? List.<Requirement>of(manaOutput, gasOutput) : List.<Requirement>of(gasOutput, manaOutput);
                require(MachineTicker.outputAllocationFailure(context, outputs, 1) != null && gasOut.stored(0).isEmpty(), "full mana output cannot hide behind gas output check");
                manaOut.take(Long.MAX_VALUE, false);
            }
            var energy = new RequirementEnergy(IOType.INPUT, 10);
            var gasRate = new RequirementGasPerTick(IOType.INPUT, "mekanism:hydrogen", 100); var manaRate = new RequirementMana.Rate(IOType.INPUT, 100);
            List<Requirement> rates = List.of(energy, gasRate, manaRate); var recipe = new MachineRecipe("mixed_resource_test", "rate", 2, rates, null, false, -1, "");
            power.receiveEnergy(1000, false); int initialPower = power.getEnergyStored(); long initialGas = gasIn.stored(0).getAmount();
            require(MachineTicker.processPerTick(recipe, rates, context, 2, false) != null && power.getEnergyStored() == initialPower
                    && gasIn.stored(0).getAmount() == initialGas, "mana rate shortage preserves gas and FE");
            manaIn.add(1000, false);
            require(MachineTicker.processPerTick(recipe, rates, context, 2, false) == null && power.getEnergyStored() == initialPower - 20
                    && gasIn.stored(0).getAmount() == initialGas - 200 && manaIn.getCurrentMana() == 800, "mixed per-tick FE/gas/mana pays exactly once");
            Modularmachinery.LOG.info("[MMCE][mixed-resource-regression] PASS: gas+mana payment/output checks in both orders; mixed per-tick FE/gas/mana preflight and exact costs");
        } finally { for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(origin.offset(i, 0, 0), before[i], Block.UPDATE_ALL); }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
