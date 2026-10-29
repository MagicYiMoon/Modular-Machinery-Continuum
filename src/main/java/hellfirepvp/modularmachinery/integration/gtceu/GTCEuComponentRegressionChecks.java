/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.gtceu;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.multiblock.part.TieredIOPartMachine;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.FluidHatchPartMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.ItemBusPartMachine;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.machine.component.MachineComponentProxyRegistry;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

/** Runs only with the real pinned GTCEu mod loaded; no mock GT inventories. */
public final class GTCEuComponentRegressionChecks {
    private GTCEuComponentRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) {
        var registry = MachineComponentProxyRegistry.INSTANCE;
        require(registry.getRegistry().containsKey(GTCEuComponentIntegration.KEY), "GT proxy not registered");
        var machines = MachineReloadListener.getLoader().getMachines(); var savedMachines = new LinkedHashMap<>(machines);
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine(); var savedRecipes = new LinkedHashMap<>(recipes);
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        try {
            // Normal 1x, 4x and 9x hatches are the same concrete GT class with different real storage layouts.
            MachineDefinition[][] fluidDefinitions = {GTMachines.FLUID_IMPORT_HATCH, GTMachines.FLUID_IMPORT_HATCH_4X,
                    GTMachines.FLUID_IMPORT_HATCH_9X, GTMachines.FLUID_EXPORT_HATCH,
                    GTMachines.FLUID_EXPORT_HATCH_4X, GTMachines.FLUID_EXPORT_HATCH_9X};
            for (int i = 0; i < fluidDefinitions.length; i++) {
                var definition = Arrays.stream(fluidDefinitions[i]).filter(Objects::nonNull).findFirst().orElseThrow();
                var at = origin.offset(0, 0, 20 + i * 2);
                place(level, at, definition.getBlock().defaultBlockState(), saved);
                var be = level.getBlockEntity(at);
                var component = registry.proxy(be);
                require(component != null && component.getIOType() == (i < 3 ? IOType.INPUT : IOType.OUTPUT),
                        "real GT fluid direction");
                var view = component.getCapability(ForgeCapabilities.FLUID_HANDLER);
                require(view.getTanks() == new int[]{1, 4, 9}[i % 3], "real GT fluid tank count");
                require(view == registry.proxy(be).getCapability(ForgeCapabilities.FLUID_HANDLER), "fluid handler identity changed");
                var gt = (FluidHatchPartMachine) ((IMachineBlockEntity) be).getMetaMachine();
                gt.setWorkingEnabled(false);
                gt.tank.setLocked(true, new FluidStack(Fluids.WATER, 1));
                require(view.fill(new FluidStack(Fluids.LAVA, 100), IFluidHandler.FluidAction.EXECUTE) == 0,
                        "GT fluid lock bypassed");
                require(view.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.SIMULATE) == 100
                        && view.getFluidInTank(0).isEmpty(), "fluid simulation mutated live tank");
                require(view.fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE) == 100
                        && !gt.tank.isEmpty(), "GT fluid change notification lost");
                require(view.drain(new FluidStack(Fluids.WATER, 50), IFluidHandler.FluidAction.SIMULATE).getAmount() == 50
                        && view.getFluidInTank(0).getAmount() == 100, "fluid drain simulation mutated live tank");
                var nbt = be.saveWithoutMetadata();
                level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(at, definition.getBlock().defaultBlockState(), Block.UPDATE_ALL);
                level.getBlockEntity(at).load(nbt);
                var restored = registry.proxy(level.getBlockEntity(at)).getCapability(ForgeCapabilities.FLUID_HANDLER);
                require(restored != view && restored.getFluidInTank(0).getAmount() == 100, "GT NBT/replacement fluid view");
            }
            for (boolean factory : List.of(false, true)) {
                var pos = origin.offset(0, 0, factory ? 10 : 0);
                place(level, pos, (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get())
                        .defaultBlockState().setValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING, Direction.NORTH), saved);
                MachineDefinition[] definitions = {GTMachines.ITEM_IMPORT_BUS[GTValues.LV], GTMachines.ITEM_EXPORT_BUS[GTValues.LV],
                        GTMachines.FLUID_IMPORT_HATCH[GTValues.LV], GTMachines.FLUID_EXPORT_HATCH[GTValues.LV]};
                var pattern = new MachinePattern();
                for (int i = 0; i < definitions.length; i++) {
                    var at = pos.offset(i + 1, 0, 0); var block = definitions[i].getBlock();
                    place(level, at, block.defaultBlockState(), saved);
                    ((TieredIOPartMachine) ((IMachineBlockEntity) level.getBlockEntity(at)).getMetaMachine()).setWorkingEnabled(false);
                    pattern.addPart(new MachinePattern.Part(i + 1, 0, 0, ForgeRegistries.BLOCKS.getKey(block).toString(),
                            i % 2 == 0 ? "in" : "out"));
                }
                String name = "mmc_gtceu_fixture";
                var machine = new DynamicMachine(name, "GT proxy fixture", pattern);
                machine.setMaxThreads(1); machine.setParallelizable(true); machine.setInternalParallelism(2); machine.setMaxParallelism(2);
                machines.put(new ResourceLocation("modularmachinery", name), machine);
                var controller = (TileController) level.getBlockEntity(pos); controller.setMachineName(name);
                var context = new MachineContext(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader().getVariables(), pattern);
                require(context.getComponentPositions().size() == 4, "GT discovery");
                var input = context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.INPUT).get(0);
                var output = context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.OUTPUT).get(0);
                var inputFluid = context.internalFluidInputs().get(0); var outputFluid = context.internalFluidOutputs().get(0);
                var gtInput = (ItemBusPartMachine) ((IMachineBlockEntity) level.getBlockEntity(pos.offset(1, 0, 0))).getMetaMachine();
                gtInput.getInventory().setFilter(stack -> stack.is(Items.COAL));
                require(!input.insertItem(0, new ItemStack(Items.DIAMOND), false).isEmpty(), "GT item filter bypassed");
                require(gtInput.getInventory().isEmpty(), "initial GT item empty cache");
                require(input.insertItem(0, new ItemStack(Items.COAL, 2), false).isEmpty()
                        && !gtInput.getInventory().isEmpty(), "GT item notification");
                require(gtInput.getInventory().extractItem(0, 1, false).isEmpty()
                        && input.extractItem(0, 1, true).getCount() == 1
                        && input.getStackInSlot(0).getCount() == 2, "recipe-side input access/simulation");
                inputFluid.fill(new FluidStack(Fluids.WATER, 200), IFluidHandler.FluidAction.EXECUTE);
                require(context.forSelector("out").internalFluidInputs().isEmpty()
                        && context.forSelector("in").capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.OUTPUT).isEmpty(),
                        "GT selectors/directions");
                var formation = MachineFormation.tryForm(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader());
                require(formation.formed() && controller.getBoundComponentCount() == 4, "GT formation binding");
                controller.setFormed(true);
                var recipe = new MachineRecipe(name, "gt_paid_batch", 3, List.of(
                        new RequirementItem(IOType.INPUT, "minecraft:coal", 1), new RequirementFluid(IOType.INPUT, "minecraft:water", 100),
                        new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1), new RequirementFluid(IOType.OUTPUT, "minecraft:lava", 50)),
                        null, false, -1, "");
                recipes.put(name, List.of(recipe));
                for (int i = 0; i < 12 && output.getStackInSlot(0).isEmpty(); i++) {
                    if (controller instanceof TileFactoryController f) FactoryTicker.tick(level, pos, f);
                    else MachineTicker.tick(level, pos, controller);
                }
                require(output.getStackInSlot(0).is(Items.DIAMOND) && output.getStackInSlot(0).getCount() == 2
                        && input.getStackInSlot(0).isEmpty() && inputFluid.getFluidInTank(0).isEmpty()
                        && outputFluid.getFluidInTank(0).getFluid() == Fluids.LAVA
                        && outputFluid.getFluidInTank(0).getAmount() == 100 && gtInput.getInventory().isEmpty(),
                        "GT ordinary/factory real 2x item/fluid recipe IO");
                context.overrideComponentPositions(List.of(pos.offset(3, 0, 0), pos.offset(3, 0, 0)));
                require(context.internalFluidInputs().size() == 1, "GT aliased fluid handler counted twice");
                level.setBlock(pos.offset(3, 0, 0), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                require(context.internalFluidInputs().isEmpty(), "removed GT hatch still exposed");
            }
            var excluded = origin.offset(0, 0, 40);
            place(level, excluded, Arrays.stream(GTMachines.LASER_INPUT_HATCH_256).filter(Objects::nonNull).findFirst().orElseThrow().getBlock().defaultBlockState(), saved);
            require(registry.proxy(level.getBlockEntity(excluded)) == null, "laser hatch enabled without an audited adapter");
            place(level, excluded.offset(2, 0, 0), Blocks.BARREL.defaultBlockState(), saved);
            require(registry.proxy(level.getBlockEntity(excluded.offset(2, 0, 0))) == null, "unrelated inventory adopted");
            Modularmachinery.LOG.info("[MMC][gtceu-components] PASS: actual GT 7.5.3 ordinary/factory 2x item/fluid recipes, 1x/4x/9x hatches, filters/locks/notifications, simulation, selectors/directions, identity/dedup, NBT/replacement/removal and unsupported laser/vanilla exclusion");
        } finally {
            saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            machines.clear(); machines.putAll(savedMachines); recipes.clear(); recipes.putAll(savedRecipes);
        }
    }
    private static void place(ServerLevel level, BlockPos at, BlockState state, Map<BlockPos, BlockState> saved) {
        saved.putIfAbsent(at, level.getBlockState(at)); level.setBlock(at, state, Block.UPDATE_ALL);
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
