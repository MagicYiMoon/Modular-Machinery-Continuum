/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.me;

import appeng.api.upgrades.IUpgradeInventory;
import appeng.core.definitions.AEItems;
import appeng.api.upgrades.Upgrades;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.items.IItemHandler;
import hellfirepvp.modularmachinery.common.menu.MenuMEFluidInputBus;
import hellfirepvp.modularmachinery.common.menu.MenuMEFluidOutputBus;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraftforge.common.util.FakePlayerFactory;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.UUID;

/** Regression checks for the five-slot AE2 capacity upgrade on both fluid buses. */
public final class MEFluidUpgradeRegressionChecks {
    private static final int UPGRADE_SLOTS = 5;
    private static final int EXPECTED_CAPACITY = MEFluidBus.TANK_DEFAULT_CAPACITY * (1 << (UPGRADE_SLOTS * 2));

    private MEFluidUpgradeRegressionChecks() {
    }

    public static void run(net.minecraft.server.level.ServerLevel level, BlockPos origin) {
        checkBus(level, origin, "blockmefluidinputbus", MEFluidInputBus.class);
        checkBus(level, origin.offset(2, 0, 0), "blockmefluidoutputbus", MEFluidOutputBus.class);
        Modularmachinery.LOG.info("[MMCE][regression] ME fluid upgrade PASS: five slots, capacity multiplier, card restrictions, NBT, 32-bit menu packet roundtrip");
    }

    private static <T extends MEFluidBus> void checkBus(net.minecraft.server.level.ServerLevel level, BlockPos pos,
                                                        String id, Class<T> type) {
        var previous = level.getBlockState(pos);
        try {
            level.removeBlock(pos, false);
            var block = RegistryMEBlocks.all().get(id).get();
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            require(type.isInstance(level.getBlockEntity(pos)), id + " tile missing");
            T bus = type.cast(level.getBlockEntity(pos));
            require(bus.getIOType() == (bus instanceof MEFluidInputBus ? IOType.INPUT : IOType.OUTPUT),
                    id + " is not exposed as the correct machine-component direction");
            var fluidHandler = bus.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER)
                    .orElse(null);
            require(fluidHandler != null && fluidHandler.getTanks() == MEFluidBus.TANK_SLOT_AMOUNT,
                    id + " does not expose all nine tanks to machine requirements");
            IUpgradeInventory upgrades = bus.getUpgrades();
            IItemHandler handler = upgrades.toItemHandler();
            require(handler.getSlots() == UPGRADE_SLOTS, id + " does not expose five upgrade slots");
            IItemHandler exposed = bus.getBreakInventory();
            require(exposed != null && exposed.getSlots() == UPGRADE_SLOTS,
                    id + " does not expose its AE2 upgrade inventory to the block drop path");
            ItemStack card = AEItems.CAPACITY_CARD.stack();
            int max = Upgrades.getMaxInstallable(AEItems.CAPACITY_CARD, bus.getVisualItemStack().getItem());
            require(max == UPGRADE_SLOTS, id + " AE2 registration exposes max=" + max);
            require(!handler.insertItem(0, AEItems.SPEED_CARD.stack(), true).isEmpty(),
                    id + " accepts an unregistered speed card");
            for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
                ItemStack remainder = handler.insertItem(slot, card.copy(), false);
                require(remainder.isEmpty(), id + " rejected capacity card in slot " + slot);
            }
            require(bus.getInstalledUpgrades(AEItems.CAPACITY_CARD) == UPGRADE_SLOTS,
                    id + " installed-card count is wrong");
            require(exposed.getStackInSlot(0).is(AEItems.CAPACITY_CARD.asItem()),
                    id + " exposed upgrade inventory is not the installed-card inventory");
            require(bus.tankAt(0).getCapacity() == EXPECTED_CAPACITY,
                    id + " capacity did not become 8000 * 4^5");
            for (int slot = 0; slot < MEFluidBus.TANK_SLOT_AMOUNT; slot++) {
                require(bus.tankAt(slot).getCapacity() == EXPECTED_CAPACITY, id + " tank capacity differs at " + slot);
            }
            bus.tankAt(0).setFluid(new FluidStack(Fluids.WATER, 250000));
            bus.tankAt(8).setFluid(new FluidStack(Fluids.LAVA, EXPECTED_CAPACITY));
            checkAggregateDrainDoesNotLoseMixedFluids(bus);
            if (bus instanceof MEFluidInputBus input) {
                input.setConfiguredFluid(0, new FluidStack(Fluids.WATER, 1));
            }
            checkMenuPackets(level, bus);

            ItemStack rejected = handler.insertItem(0, card.copy(), false);
            require(!rejected.isEmpty(), id + " accepted a sixth card");

            CompoundTag saved = bus.saveWithoutMetadata();
            for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
                handler.extractItem(slot, 1, false);
            }
            require(bus.getInstalledUpgrades(AEItems.CAPACITY_CARD) == 0
                            && bus.tankAt(0).getCapacity() == MEFluidBus.TANK_DEFAULT_CAPACITY,
                    id + " did not clear its upgrades");
            bus.load(saved);
            require(bus.getInstalledUpgrades(AEItems.CAPACITY_CARD) == UPGRADE_SLOTS,
                    id + " did not restore upgrade NBT");
            require(bus.tankAt(0).getCapacity() == EXPECTED_CAPACITY,
                    id + " did not restore upgraded capacity from NBT");
            require(bus.tankAt(0).getFluidAmount() == 250000
                            && bus.tankAt(8).getFluidAmount() == EXPECTED_CAPACITY,
                    id + " lost buffered fluid during upgrade removal/save/load");
        } finally {
            level.removeBlock(pos, false);
            level.setBlock(pos, previous, Block.UPDATE_ALL);
        }
    }

    /**
     * Forge's untyped drain is allowed to span tanks, but it must return one fluid
     * identity.  This catches the old implementation which drained a lava tank and
     * only then discovered that lava could not be merged into a preceding water result.
     */
    private static void checkAggregateDrainDoesNotLoseMixedFluids(MEFluidBus bus) {
        var handler = bus.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER)
                .orElseThrow(() -> new IllegalStateException("fluid bus capability missing"));
        bus.tankAt(1).setFluid(new FluidStack(Fluids.WATER, 1000));
        bus.tankAt(2).setFluid(new FluidStack(Fluids.LAVA, 1000));
        CompoundTag variant = new CompoundTag();
        variant.putInt("quality", 3);
        bus.tankAt(3).setFluid(new FluidStack(Fluids.WATER, 750, variant));
        bus.tankAt(4).setFluid(new FluidStack(Fluids.WATER, 500));
        int lavaBefore = bus.tankAt(2).getFluidAmount();
        var simulated = handler.drain(300000,
                net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.SIMULATE);
        require(simulated.getFluid() == Fluids.WATER && simulated.getAmount() == 251500,
                "aggregate fluid simulation did not stay on the first fluid identity");
        require(bus.tankAt(0).getFluidAmount() == 250000 && bus.tankAt(1).getFluidAmount() == 1000
                        && bus.tankAt(2).getFluidAmount() == lavaBefore && bus.tankAt(3).getFluidAmount() == 750
                        && bus.tankAt(4).getFluidAmount() == 500,
                "aggregate fluid simulation mutated a tank");
        var executed = handler.drain(300000,
                net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        require(executed.getFluid() == Fluids.WATER && executed.getAmount() == 251500,
                "aggregate fluid execution returned the wrong identity or amount");
        require(bus.tankAt(0).isEmpty() && bus.tankAt(1).isEmpty() && bus.tankAt(4).isEmpty(),
                "aggregate fluid execution did not consume matching water tanks");
        require(bus.tankAt(2).getFluidAmount() == lavaBefore,
                "aggregate fluid execution lost a different fluid");
        require(bus.tankAt(3).getFluidAmount() == 750 && variant.equals(bus.tankAt(3).getFluid().getTag()),
                "aggregate fluid execution lost another NBT identity");
        FluidStack typed = handler.drain(new FluidStack(Fluids.WATER, 400, variant),
                net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
        require(typed.getAmount() == 400 && variant.equals(typed.getTag())
                        && bus.tankAt(3).getFluidAmount() == 350 && bus.tankAt(2).getFluidAmount() == lavaBefore,
                "typed drain lost fluid identity or NBT");
        require(handler.drain(0, net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "zero drain returned fluid");
        bus.tankAt(2).setFluid(FluidStack.EMPTY);
        bus.tankAt(3).setFluid(FluidStack.EMPTY);
        bus.tankAt(0).setFluid(new FluidStack(Fluids.WATER, 250000));
    }

    private static void checkMenuPackets(net.minecraft.server.level.ServerLevel level, MEFluidBus bus) {
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("a57b9931-a8ac-499a-ad4a-229af3f194cd"), "MMCEFluidUpgrades"));
        AbstractContainerMenu server;
        AbstractContainerMenu client;
        if (bus instanceof MEFluidInputBus input) {
            server = MenuMEFluidInputBus.forBus(7, player.getInventory(), input);
            client = new MenuMEFluidInputBus(7, player.getInventory(), bus.getBlockPos());
        } else {
            server = MenuMEFluidOutputBus.forBus(7, player.getInventory(), (MEFluidOutputBus) bus);
            client = new MenuMEFluidOutputBus(7, player.getInventory(), bus.getBlockPos());
        }
        server.addSlotListener(new ContainerListener() {
            @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) {
                if (slot >= 45) client.slots.get(slot).set(stack.copy());
            }
            @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) {
                FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    new ClientboundContainerSetDataPacket(menu.containerId, index, value).write(wire);
                    var decoded = new ClientboundContainerSetDataPacket(wire);
                    client.setData(decoded.getId(), decoded.getValue());
                } finally {
                    wire.release();
                }
            }
        });
        server.broadcastChanges();
        checkClient(client, 250000);
        bus.tankAt(0).setFluid(new FluidStack(Fluids.WATER, 65536));
        server.broadcastChanges();
        checkClient(client, 65536);
        bus.tankAt(0).setFluid(new FluidStack(Fluids.WATER, 250000));
    }

    private static void checkClient(AbstractContainerMenu client, int firstAmount) {
        if (client instanceof MenuMEFluidInputBus input) {
            require(input.storedFluidFor(0).getAmount() == firstAmount
                            && input.storedFluidFor(8).getAmount() == EXPECTED_CAPACITY
                            && input.configuredFluidFor(0).getAmount() == EXPECTED_CAPACITY
                            && input.configuredFluidFor(1).isEmpty()
                            && input.capacityForDisplay() == EXPECTED_CAPACITY,
                    "input client menu lost an upgraded fluid amount, empty sentinel or capacity card");
        } else {
            var output = (MenuMEFluidOutputBus) client;
            require(output.fluidFor(0).getAmount() == firstAmount
                            && output.fluidFor(8).getAmount() == EXPECTED_CAPACITY
                            && output.fluidFor(1).isEmpty()
                            && output.capacityForDisplay() == EXPECTED_CAPACITY,
                    "output client menu lost an upgraded fluid amount, empty sentinel or capacity card");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
