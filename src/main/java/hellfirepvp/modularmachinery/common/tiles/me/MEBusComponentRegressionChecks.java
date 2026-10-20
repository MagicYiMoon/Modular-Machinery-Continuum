/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.me;

import appeng.core.definitions.AEItems;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.helper.FluidInputPlan;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;
import hellfirepvp.modularmachinery.common.tiles.MachineTicker;
import hellfirepvp.modularmachinery.common.tiles.base.MachineComponentTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.fluids.FluidStack;

import java.util.List;
import java.util.UUID;

/** Exercises recipe IO through real ME components and capacity-card block drops. */
public final class MEBusComponentRegressionChecks {
    private MEBusComponentRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos origin) {
        String[] ids = {"blockmeiteminputbus", "blockmeitemoutputbus", "blockmefluidinputbus", "blockmefluidoutputbus"};
        BlockPos[] positions = new BlockPos[ids.length];
        BlockState[] previous = new BlockState[ids.length];
        for (int i = 0; i < ids.length; i++) {
            positions[i] = origin.offset(i * 2, 0, 0);
            previous[i] = level.getBlockState(positions[i]);
        }
        try {
            for (int i = 0; i < ids.length; i++) {
                level.removeBlock(positions[i], false);
                level.setBlock(positions[i], RegistryMEBlocks.all().get(ids[i]).get().defaultBlockState(), Block.UPDATE_ALL);
                require(level.getBlockEntity(positions[i]) instanceof MachineComponentTile, ids[i] + " is not a component");
                var component = (MachineComponentTile) level.getBlockEntity(positions[i]);
                require(component.getIOType() == (i % 2 == 0 ? IOType.INPUT : IOType.OUTPUT), ids[i] + " direction wrong");
                component.setControllerPos(origin.offset(0, 0, 4));
                var tile = level.getBlockEntity(positions[i]);
                var saved = tile.saveWithoutMetadata();
                component.setControllerPos(null);
                tile.load(saved);
                require(origin.offset(0, 0, 4).equals(component.getControllerPos()), ids[i] + " binding NBT lost");
            }
            var pattern = new MachinePattern();
            var machine = new DynamicMachine("me_io_regression", "ME IO regression", pattern);
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), pattern);
            context.overrideComponentPositions(List.of(positions));
            var itemIn = (MEItemInputBus) level.getBlockEntity(positions[0]);
            var itemOut = (MEItemOutputBus) level.getBlockEntity(positions[1]);
            var fluidIn = (MEFluidInputBus) level.getBlockEntity(positions[2]);
            var fluidOut = (MEFluidOutputBus) level.getBlockEntity(positions[3]);
            require(context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.INPUT).size() == 1
                    && context.capabilities(ForgeCapabilities.ITEM_HANDLER, MachineContext.ComponentKind.OUTPUT).size() == 1,
                    "item IO includes the wrong direction or the fluid bus upgrade inventory");
            require(context.capabilities(ForgeCapabilities.FLUID_HANDLER, MachineContext.ComponentKind.INPUT).size() == 1
                    && context.capabilities(ForgeCapabilities.FLUID_HANDLER, MachineContext.ComponentKind.OUTPUT).size() == 1,
                    "fluid IO directions wrong");
            require(!fluidIn.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()
                    && !fluidOut.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(), "upgrades exposed to item recipes");

            itemIn.getInternalInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 4));
            itemOut.getInternalInventory().setStackInSlot(0, new ItemStack(Items.DIAMOND, 10));
            fluidIn.tankAt(0).setFluid(new FluidStack(Fluids.WATER, 300));
            fluidIn.tankAt(1).setFluid(new FluidStack(Fluids.LAVA, 700));
            fluidIn.tankAt(2).setFluid(new FluidStack(Fluids.WATER, 400));
            fluidOut.tankAt(0).setFluid(new FluidStack(Fluids.WATER, 900));
            require(MachineTicker.consumeInputs(List.of(new RequirementItem(IOType.INPUT, "minecraft:diamond", 2, 1F)), context, 1),
                    "machine cannot consume from ME item input");
            var fluidPlan = FluidInputPlan.plan(context, List.of(new RequirementFluid(IOType.INPUT, "minecraft:water", 600)), 1);
            require(fluidPlan.valid() && fluidPlan.execute(), "machine cannot consume water across ME input tanks");
            require(itemIn.getInternalInventory().getStackInSlot(0).getCount() == 2
                    && itemOut.getInternalInventory().getStackInSlot(0).getCount() == 10, "item recipe consumed output buffer");
            require(fluidIn.tankAt(0).isEmpty() && fluidIn.tankAt(2).getFluidAmount() == 100
                    && fluidIn.tankAt(1).getFluidAmount() == 700 && fluidOut.tankAt(0).getFluidAmount() == 900,
                    "fluid recipe consumed another identity or output buffer");
            var outputItem = new RequirementItem(IOType.OUTPUT, "minecraft:diamond", 3, 1F);
            var outputFluid = new RequirementFluid(IOType.OUTPUT, "minecraft:water", 200);
            require(MachineTicker.outputAllocationFailure(context, List.of(outputItem, outputFluid), 1) == null,
                    "machine output allocator cannot see ME buffers");
            MachineTicker.emitOutputs(new MachineRecipe("me_io_regression", "me_io_regression", 1,
                    List.of(outputItem, outputFluid), null, false, -1, ""),
                    List.of(outputItem, outputFluid), context, 1);
            require(itemOut.getInternalInventory().getStackInSlot(0).getCount() == 13
                    && fluidOut.tankAt(0).getFluidAmount() == 1100,
                    "machine did not write into ME output buffers (items="
                            + itemOut.getInternalInventory().getStackInSlot(0).getCount()
                            + ", fluid=" + fluidOut.tankAt(0).getFluidAmount()
                            + ", resolvable=" + outputFluid.isResolvable() + ")");

            checkCardDrops(level, fluidIn);
            checkCardDrops(level, fluidOut);
            Modularmachinery.LOG.info("[MMCE][regression] ME component IO PASS: four directions, binding NBT, recipe consumption/production, card isolation and five-card drops");
        } finally {
            for (int i = 0; i < ids.length; i++) {
                level.removeBlock(positions[i], false);
                level.setBlock(positions[i], previous[i], Block.UPDATE_ALL);
            }
        }
    }

    private static void checkCardDrops(ServerLevel level, MEFluidBus bus) {
        for (int slot = 0; slot < 5; slot++) {
            require(bus.getUpgrades().toItemHandler().insertItem(slot, AEItems.CAPACITY_CARD.stack(), false).isEmpty(),
                    "capacity card installation failed before drop check");
        }
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("9d83a7e4-e1ce-4b90-966b-13c0cf708b34"), "MMCEBusDrop"));
        var box = new AABB(bus.getBlockPos()).inflate(1);
        var before = level.getEntitiesOfClass(ItemEntity.class, box).stream().map(ItemEntity::getUUID).toList();
        bus.getBlockState().getBlock().playerDestroy(level, player, bus.getBlockPos(), bus.getBlockState(), bus, ItemStack.EMPTY);
        var drops = level.getEntitiesOfClass(ItemEntity.class, box, entity -> !before.contains(entity.getUUID()));
        try {
            int count = drops.stream().filter(e -> e.getItem().is(AEItems.CAPACITY_CARD.asItem()))
                    .mapToInt(e -> e.getItem().getCount()).sum();
            require(count == 5, "breaking fluid bus dropped " + count + " cards instead of five");
        } finally {
            drops.forEach(ItemEntity::discard);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
