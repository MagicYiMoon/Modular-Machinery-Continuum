/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.appmek;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.*;
import appeng.core.definitions.AEItems;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.IntContainerData;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;
import hellfirepvp.modularmachinery.integration.mekanism.*;
import me.ramidzkh.mekae2.ae2.MekanismKey;
import mekanism.api.Action;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Real Applied Mekanistics cell plus production bus transfers and machine recipe IO. */
public final class MEGasBusRegressionChecks {
    private MEGasBusRegressionChecks() {}
    public static void run(ServerLevel level, BlockPos origin) {
        var beforeIn = level.getBlockState(origin); var beforeOut = level.getBlockState(origin.east());
        try {
            var inBlock = RegistryMEBlocks.all().get("blockmegasinputbus").get();
            var outBlock = RegistryMEBlocks.all().get("blockmegasoutputbus").get();
            level.setBlock(origin, inBlock.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(origin.east(), outBlock.defaultBlockState(), Block.UPDATE_ALL);
            var input = (MEGasBus) level.getBlockEntity(origin); var output = (MEGasBus) level.getBlockEntity(origin.east());
            require(input != null && output != null && input.isInput() && !output.isInput(), "real gas block entities register and place");
            var hydrogen = new RequirementGas(IOType.INPUT, "mekanism:hydrogen", 100, 1F);
            var oxygen = new RequirementGas(IOType.INPUT, "mekanism:oxygen", 100, 1F);
            var cellItem = ForgeRegistries.ITEMS.getValue(new ResourceLocation("appmek", "chemical_storage_cell_1k"));
            require(cellItem != null && cellItem != net.minecraft.world.item.Items.AIR, "Applied Mekanistics cell item exists");
            var cellStack = new ItemStack(cellItem); var storage = StorageCells.getCellInventory(cellStack, null);
            require(storage != null, "real chemical cell inventory opens");
            var source = IActionSource.ofMachine(input);
            require(storage.insert(MekanismKey.of(hydrogen.asStack()), 20000, Actionable.MODULATE, source) == 20000
                    && storage.insert(MekanismKey.of(oxygen.asStack()), 20000, Actionable.MODULATE, source) == 20000, "cell stores distinct gas keys");
            class LimitedStorage implements MEStorage {
                long insertLimit = Long.MAX_VALUE;
                public Component getDescription() { return Component.literal("real cell behind controlled network capacity"); }
                public long insert(AEKey key, long amount, Actionable mode, IActionSource action) { return storage.insert(key, Math.min(amount, insertLimit), mode, action); }
                public long extract(AEKey key, long amount, Actionable mode, IActionSource action) { return storage.extract(key, amount, mode, action); }
            }
            var network = new LimitedStorage();
            var bus = new MEGasBus(GasBusRegistration.INPUT, origin, inBlock.defaultBlockState(), true) {
                @Override protected MEStorage networkStorage() { return network; }
            };
            bus.configure(0, hydrogen.asStack()); require(bus.transfer() && bus.stored(0).getAmount() == 8000, "input fills to source capacity");
            network.insertLimit = 300;
            bus.configure(0, oxygen.asStack()); bus.transfer();
            require(bus.stored(0).isTypeEqual(hydrogen.asStack()) && bus.stored(0).getAmount() == 7700, "partial return preserves old gas and refuses overwrite");
            network.insertLimit = Long.MAX_VALUE; bus.transfer();
            require(bus.stored(0).isTypeEqual(oxygen.asStack()) && bus.stored(0).getAmount() == 8000, "new gas loads only after full return");
            bus.configure(8, hydrogen.asStack()); bus.transfer();
            require(bus.stored(8).getAmount() == 8000, "all nine cells participate");
            bus.configure(0, GasStack.EMPTY); bus.transfer(); require(bus.stored(0).isEmpty(), "clearing configuration returns contents");
            input.load(bus.saveWithoutMetadata());
            var settings = input.downloadSettings(); require(settings.equals(bus.downloadSettings()) && input.stored(8).getAmount() == 8000, "contents and ghost config persist");
            require(!input.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent() && !input.getCapability(ForgeCapabilities.FLUID_HANDLER).isPresent(), "gas bus upgrades are not item/fluid recipe inputs");
            var handler = input.getCapability(Capabilities.GAS_HANDLER).orElseThrow(IllegalStateException::new);
            require(handler.getTanks() == 9 && handler.extractChemical(8, 1, Action.EXECUTE).isEmpty(), "external extraction cannot steal input gas");
            input.getUpgrades().setItemDirect(0, AEItems.CAPACITY_CARD.stack());
            require(input.capacity() == 32000, "capacity card multiplies capacity by four");
            var tank = input.gasTanks().get(0); tank.setGasTag(hydrogen.asStack(25000).write(new net.minecraft.nbt.CompoundTag()));
            input.getUpgrades().setItemDirect(0, ItemStack.EMPTY);
            require(input.capacity() == 8000 && input.stored(0).getAmount() == 25000, "capacity downgrade retains paid gas");
            var data = MenuMEGasBus.dataFor(input); var clientData = new SimpleContainerData(data.getCount());
            var wire = new IntContainerData(data); var receive = new IntContainerData(clientData);
            tank.setGasTag(hydrogen.asStack(4_000_000_001L).write(new net.minecraft.nbt.CompoundTag()));
            for (int index = 0; index < wire.getCount(); index++) receive.set(index, (short) wire.get(index));
            long amount = Integer.toUnsignedLong(clientData.get(2)) | ((long) clientData.get(3) << 32);
            require(amount == 4_000_000_001L, "gas amount survives vanilla short-word menu sync");
            tank.clearGas();
            var machine = new DynamicMachine("me_gas_regression", "Gas", new MachinePattern());
            var context = new MachineContext(level, origin, Direction.NORTH, machine, new MachineVariables(), machine.getPattern());
            context.overrideComponentPositions(List.of(origin, origin.east(), origin));
            // Duplicate component positions must not count the same buffer twice.
            var large = new RequirementGas(IOType.INPUT, "mekanism:hydrogen", 5000, 1F);
            require(!GasIOPlan.inputs(context, List.of(large, large), 1).valid() && input.stored(8).getAmount() == 8000, "shared gas reservation prevents overbooking");
            require(hydrogen.consume(context, 2) && input.stored(8).getAmount() == 7800, "machine consumes a real ME input cache internally");
            var emitted = new RequirementGas(IOType.OUTPUT, "mekanism:oxygen", 100, 1F);
            require(emitted.produce(context, 3) == 300 && output.stored(0).getAmount() == 300, "machine fills ME output cache");
            var outBus = new MEGasBus(GasBusRegistration.OUTPUT, origin.east(), outBlock.defaultBlockState(), false) {
                @Override protected MEStorage networkStorage() { return network; }
            };
            outBus.load(output.saveWithoutMetadata()); network.insertLimit = 50; outBus.transfer();
            require(outBus.stored(0).getAmount() == 250, "output keeps network-rejected remainder");
            network.insertLimit = Long.MAX_VALUE; outBus.transfer(); require(outBus.stored(0).isEmpty(), "output drains to real cell");
            require(!input.transfer(), "offline grid preserves contents");
            var rate = new RequirementGasPerTick(IOType.INPUT, "mekanism:hydrogen", 200);
            var tick = GasTickIO.plan(context, List.of(rate), 2);
            require(tick.valid() && tick.execute() && input.stored(8).getAmount() == 7400, "per-tick gas planner sees all ME buffers");
            bus.getMainNode().destroy(); outBus.getMainNode().destroy();
            Modularmachinery.LOG.info("[MMCE][me-gas-bus-regression] PASS: real chemical cell keys/transfers, nine buffers, partial returns/no overwrite, capacity/NBT/settings/64-bit menu sync, external direction, shared reservations, recipe input/output and per-tick IO");
        } finally {
            level.setBlock(origin, beforeIn, Block.UPDATE_ALL); level.setBlock(origin.east(), beforeOut, Block.UPDATE_ALL);
        }
    }
    private static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
}
