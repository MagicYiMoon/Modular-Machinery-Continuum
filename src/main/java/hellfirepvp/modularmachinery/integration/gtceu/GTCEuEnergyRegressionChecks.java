/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.gtceu;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.common.data.GTMachines;
import com.gregtechceu.gtceu.common.machine.multiblock.part.EnergyHatchPartMachine;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Real GT energy hatches, fractional conservation and actual ordinary/factory processing. */
public final class GTCEuEnergyRegressionChecks {
    private GTCEuEnergyRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) {
        var machines = MachineReloadListener.getLoader().getMachines(); var oldMachines = new LinkedHashMap<>(machines);
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine(); var oldRecipes = new LinkedHashMap<>(recipes);
        var saved = new LinkedHashMap<BlockPos, BlockState>();
        try {
            MachineDefinition[][] families = {GTMachines.ENERGY_INPUT_HATCH, GTMachines.ENERGY_OUTPUT_HATCH,
                    GTMachines.ENERGY_INPUT_HATCH_4A, GTMachines.ENERGY_OUTPUT_HATCH_4A,
                    GTMachines.ENERGY_INPUT_HATCH_16A, GTMachines.ENERGY_OUTPUT_HATCH_16A,
                    GTMachines.SUBSTATION_ENERGY_INPUT_HATCH, GTMachines.SUBSTATION_ENERGY_OUTPUT_HATCH};
            for (int family = 0; family < families.length; family++) {
                var def = Arrays.stream(families[family]).filter(Objects::nonNull).findFirst().orElseThrow();
                BlockPos at = origin.offset(0, 0, family * 2);
                place(level, at, def.getBlock().defaultBlockState(), saved);
                checkFractional(level, at, family % 2 == 0);
            }
            checkLargeBuffer(level, origin.offset(0, 0, 20), saved);
            for (boolean factory : List.of(false, true)) {
                var pos = origin.offset(0, 0, factory ? 40 : 30);
                place(level, pos, (factory ? RegistryBlocks.blockFactoryController.get() : RegistryBlocks.blockController.get())
                        .defaultBlockState().setValue(hellfirepvp.modularmachinery.common.block.BlockController.FACING, Direction.NORTH), saved);
                Block[] blocks = {RegistryBlocks.itemInputBus.get(), RegistryBlocks.itemOutputBus.get(),
                        GTMachines.ENERGY_INPUT_HATCH[GTValues.LV].getBlock(), GTMachines.ENERGY_OUTPUT_HATCH[GTValues.LV].getBlock()};
                var pattern = new MachinePattern();
                for (int i = 0; i < blocks.length; i++) {
                    place(level, pos.offset(i + 1, 0, 0), blocks[i].defaultBlockState(), saved);
                    pattern.addPart(new MachinePattern.Part(i + 1, 0, 0, ForgeRegistries.BLOCKS.getKey(blocks[i]).toString(), i == 3 ? "out" : "in"));
                }
                var inBE = level.getBlockEntity(pos.offset(3, 0, 0)); var outBE = level.getBlockEntity(pos.offset(4, 0, 0));
                var inGT = hatch(inBE); var outGT = hatch(outBE);
                inGT.setWorkingEnabled(false); outGT.setWorkingEnabled(false);
                inGT.energyContainer.changeEnergy(5); // 20 FE, of which this batch will pay 18.
                var input = (IItemHandlerModifiable) ((hellfirepvp.modularmachinery.common.tiles.base.TileItemBus)
                        level.getBlockEntity(pos.offset(1, 0, 0))).getInventory();
                var output = (IItemHandlerModifiable) ((hellfirepvp.modularmachinery.common.tiles.base.TileItemBus)
                        level.getBlockEntity(pos.offset(2, 0, 0))).getInventory();
                input.setStackInSlot(0, new ItemStack(Items.COAL, 2));
                String name = "mmc_gt_eu_fixture";
                var machine = new DynamicMachine(name, "GT EU", pattern);
                machine.setMaxThreads(1); machine.setParallelizable(true); machine.setInternalParallelism(2); machine.setMaxParallelism(2);
                machines.put(new ResourceLocation("modularmachinery", name), machine);
                var controller = (TileController) level.getBlockEntity(pos); controller.setMachineName(name);
                var context = new MachineContext(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader().getVariables(), pattern);
                var energyA = new RequirementEnergy(IOType.INPUT, 1); var energyB = new RequirementEnergy(IOType.INPUT, 2);
                var product = new RequirementEnergy(IOType.OUTPUT, 1);
                var recipe = new MachineRecipe(name, "gt_eu_fraction_batch", 3, List.of(
                        new RequirementItem(IOType.INPUT, "minecraft:coal", 1), energyA, energyB,
                        new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 1), product), null, false, -1, "");
                recipes.put(name, List.of(recipe));
                require(MachineFormation.tryForm(level, pos, Direction.NORTH, machine, MachineReloadListener.getLoader()).formed(), "EU fixture formation");
                controller.setFormed(true);
                for (int tick = 0; tick < 12 && output.getStackInSlot(0).isEmpty(); tick++) {
                    if (controller instanceof TileFactoryController f) FactoryTicker.tick(level, pos, f);
                    else MachineTicker.tick(level, pos, controller);
                }
                require(output.getStackInSlot(0).is(Items.DIAMOND) && output.getStackInSlot(0).getCount() == 2
                        && input.getStackInSlot(0).isEmpty() && inGT.energyContainer.getEnergyStored() == 0
                        && view(inBE).getEnergyStored() == 2 && outGT.energyContainer.getEnergyStored() == 1
                        && view(outBE).getEnergyStored() == 6, "actual 2x ordinary/factory fractional EU conservation");
                require(context.forSelector("out").internalEnergyStorages(MachineContext.ComponentKind.INPUT).isEmpty(), "EU selector leaked direction");
                require(MachineTicker.processPerTick(recipe, List.of(energyA, energyB), context, 1, false) != null
                        && view(inBE).getEnergyStored() == 2, "insufficient combined EU tick consumed fraction");
                inGT.energyContainer.changeEnergy(10);
                outGT.energyContainer.setEnergyStored(outGT.energyContainer.getEnergyCapacity());
                view(outBE).receiveEnergy(1, false); // Complete the sidecar's last FE; both EU and fraction are now full.
                int before = view(inBE).getEnergyStored();
                require(MachineTicker.processPerTick(recipe, List.of(energyA, product), context, 1, false) != null
                        && view(inBE).getEnergyStored() == before, "full EU output consumed input before rejecting tick");
                context.overrideComponentPositions(List.of(inBE.getBlockPos(), inBE.getBlockPos()));
                require(context.internalEnergyStorages(MachineContext.ComponentKind.INPUT).size() == 1, "same EU storage counted twice");
            }
            Modularmachinery.LOG.info("[MMC][gtceu-energy] PASS: real 2A/4A/16A/64A input/output, exact 4FE/EU with fractional NBT, simulate/direction/stale/async guards, long/int boundaries, actual ordinary+factory 2x jobs, combined reservations and full-output preflight");
        } finally {
            saved.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
            machines.clear(); machines.putAll(oldMachines); recipes.clear(); recipes.putAll(oldRecipes);
        }
    }

    private static void checkFractional(ServerLevel level, BlockPos at, boolean input) {
        var be = level.getBlockEntity(at); var gt = hatch(be); gt.setWorkingEnabled(false);
        var handler = view(be);
        require(handler == view(be) && handler.canExtract() == input && handler.canReceive() != input, "EU identity/direction");
        if (input) gt.energyContainer.changeEnergy(20);
        for (int amount = 1; amount <= 7; amount++) {
            long eu = gt.energyContainer.getEnergyStored(); int before = handler.getEnergyStored();
            int remainder = be.getPersistentData().getInt(GTCEuEnergyView.REMAINDER_KEY);
            require((input ? handler.extractEnergy(amount, true) : handler.receiveEnergy(amount, true)) == amount
                            && gt.energyContainer.getEnergyStored() == eu && handler.getEnergyStored() == before
                            && be.getPersistentData().getInt(GTCEuEnergyView.REMAINDER_KEY) == remainder,
                    "EU simulation changed storage/remainder");
            require((input ? handler.extractEnergy(amount, false) : handler.receiveEnergy(amount, false)) == amount
                            && handler.getEnergyStored() == before + (input ? -amount : amount), "fraction lost or created FE");
        }
        // Ensure a nonzero fraction is saved with the real GT entity and survives wrapper recreation.
        if (input) handler.extractEnergy(1, false); else handler.receiveEnergy(1, false);
        int savedFe = handler.getEnergyStored(); var state = level.getBlockState(at); var nbt = be.saveWithoutMetadata();
        require(be.getPersistentData().getInt(GTCEuEnergyView.REMAINDER_KEY) > 0, "fractional NBT fixture empty");
        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        require(handler.getEnergyStored() == 0 && handler.receiveEnergy(1, false) == 0 && handler.extractEnergy(1, false) == 0, "removed EU view remained live");
        level.setBlock(at, state, Block.UPDATE_ALL); var restored = level.getBlockEntity(at); restored.load(nbt);
        var fresh = view(restored);
        require(fresh != handler && fresh.getEnergyStored() == savedFe, "fraction failed real GT NBT roundtrip");
        require(CompletableFuture.supplyAsync(() -> fresh.receiveEnergy(1, false) == 0 && fresh.extractEnergy(1, false) == 0
                && fresh.getEnergyStored() == 0).join(), "off-thread EU access allowed");
        require(fresh.getEnergyStored() == savedFe, "off-thread request changed FE");
        require((input ? fresh.receiveEnergy(1, false) : fresh.extractEnergy(1, false)) == 0, "wrong direction moved energy");
        require(fresh.receiveEnergy(-1, false) == 0 && fresh.extractEnergy(-1, false) == 0, "negative EU request");
        restored.getPersistentData().putInt(GTCEuEnergyView.REMAINDER_KEY, Integer.MAX_VALUE);
        require(fresh.getEnergyStored() == Math.min(Integer.MAX_VALUE, hatch(restored).energyContainer.getEnergyStored() * 4), "malformed fraction minted FE");
        level.setBlock(at, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL); level.setBlock(at, state, Block.UPDATE_ALL);
        require(view(level.getBlockEntity(at)).getEnergyStored() == 0, "new hatch inherited old position's fraction");
    }

    private static void checkLargeBuffer(ServerLevel level, BlockPos at, Map<BlockPos, BlockState> saved) {
        var definition = Arrays.stream(GTMachines.ENERGY_OUTPUT_HATCH).filter(Objects::nonNull).reduce((a, b) -> b).orElseThrow();
        place(level, at, definition.getBlock().defaultBlockState(), saved);
        var be = level.getBlockEntity(at); var gt = hatch(be); gt.setWorkingEnabled(false);
        var handler = view(be);
        // Exercise the GT long container at its actual type's extreme boundary without multiplying long max.
        gt.energyContainer.resetBasicInfo(Long.MAX_VALUE, 0, 0, 32, 2);
        gt.energyContainer.setEnergyStored(Long.MAX_VALUE - 1);
        require(handler.getEnergyStored() == Integer.MAX_VALUE && handler.getMaxEnergyStored() == Integer.MAX_VALUE
                        && handler.receiveEnergy(100, true) == 7, "long boundary overflow or remainder room");
        var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, ForgeRegistries.BLOCKS.getKey(definition.getBlock()).toString()));
        var context = new MachineContext(level, at.offset(-1, 0, 0), Direction.NORTH,
                new DynamicMachine("gt_large", "Large", pattern), MachineReloadListener.getLoader().getVariables(), pattern);
        var requirement = new RequirementEnergy(IOType.OUTPUT, 1);
        require(requirement.canStart(context) && requirement.getMaxParallelism(context, 8, List.of(requirement)) == 7,
                "saturated int display hid available output room");
        require(handler.receiveEnergy(7, false) == 7 && gt.energyContainer.getEnergyStored() == Long.MAX_VALUE
                && handler.receiveEnergy(1, false) == 0 && !requirement.canStart(context), "full long output/fraction boundary");
        gt.energyContainer.resetBasicInfo(Long.MAX_VALUE, 32, 2, 0, 0);
        long previous = gt.energyContainer.getEnergyStored();
        require(handler.extractEnergy(Integer.MAX_VALUE, true) == Integer.MAX_VALUE
                        && handler.extractEnergy(Integer.MAX_VALUE, false) == Integer.MAX_VALUE
                        && (previous - gt.energyContainer.getEnergyStored()) * 4 + 3
                                - be.getPersistentData().getInt(GTCEuEnergyView.REMAINDER_KEY) == Integer.MAX_VALUE,
                "int-max request from long EU lost conversion energy");
    }
    private static EnergyHatchPartMachine hatch(BlockEntity be) { return (EnergyHatchPartMachine) ((IMachineBlockEntity) be).getMetaMachine(); }
    private static IEnergyStorage view(BlockEntity be) { return MachineComponentProxyRegistry.INSTANCE.proxy(be).getCapability(ForgeCapabilities.ENERGY); }
    private static void place(ServerLevel level, BlockPos pos, BlockState state, Map<BlockPos, BlockState> saved) {
        saved.putIfAbsent(pos, level.getBlockState(pos)); level.setBlock(pos, state, Block.UPDATE_ALL);
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
