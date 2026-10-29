/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockEnergyHatch;
import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import hellfirepvp.modularmachinery.common.data.Config;
import hellfirepvp.modularmachinery.common.menu.MenuEnergyHatch;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayerFactory;

/** Actual menu packets must preserve high capacity values and subsequent energy changes. */
public final class EnergyHatchCapacityRegressionChecks {
    public static void run(ServerLevel level, BlockPos origin) {
        checkTransferLimits(level, origin.offset(0, 0, 4));
        var setting = Config.entryForEnergyCapacity(EnergyHatchData.ULTIMATE);
        long previous = setting.get();
        var blocks = new Block[]{RegistryBlocks.energyInputHatch.get(), RegistryBlocks.energyOutputHatch.get()};
        var before = new net.minecraft.world.level.block.state.BlockState[blocks.length];
        try {
            setting.set((long) Integer.MAX_VALUE);
            var player = FakePlayerFactory.getMinecraft(level);
            for (int i = 0; i < blocks.length; i++) {
                var at = origin.offset(i, 0, 0);
                before[i] = level.getBlockState(at);
                level.setBlock(at, blocks[i].defaultBlockState().setValue(BlockEnergyHatch.SIZE, EnergyHatchData.ULTIMATE), Block.UPDATE_ALL);
                var hatch = (TileEnergyHatch) level.getBlockEntity(at);
                require(hatch != null && hatch.getCapacity() == Integer.MAX_VALUE, "input/output ultimate capacity");
                var saved = hatch.saveWithoutMetadata();
                saved.putInt("energy", Integer.MAX_VALUE - 10);
                hatch.load(saved);
                require(hatch.produceInternal(100, false) == 10 && hatch.getStoredEnergy() == Integer.MAX_VALUE,
                        "filling at signed-int boundary caps without overflow");
                require(hatch.produceInternal(1, false) == 0, "full buffer refuses excess energy");
                var persisted = hatch.saveWithoutMetadata();
                hatch.consumeInternal(100, false);
                hatch.load(persisted);
                require(hatch.getStoredEnergy() == Integer.MAX_VALUE, "full energy persists through NBT");

                var server = MenuEnergyHatch.forHatch(1, player.getInventory(), hatch);
                var client = new MenuEnergyHatch(1, player.getInventory(), at);
                server.addSlotListener(new ContainerListener() {
                    @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) { }
                    @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) {
                        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
                        try {
                            new ClientboundContainerSetDataPacket(1, index, value).write(wire);
                            var decoded = new ClientboundContainerSetDataPacket(wire);
                            client.setData(decoded.getId(), decoded.getValue());
                        } finally { wire.release(); }
                    }
                });
                require(client.getCapacity() == Integer.MAX_VALUE && client.getStored() == Integer.MAX_VALUE
                        && client.getFillFraction() == 1F, "real signed-short packets preserve full capacity/energy");
                hatch.consumeInternal(100, false);
                server.broadcastChanges();
                require(client.getStored() == Integer.MAX_VALUE - 100 && client.getCapacity() == Integer.MAX_VALUE,
                        "subsequent energy update preserves high word");
            }
            Modularmachinery.LOG.info("[MMCE][energy-capacity-regression] PASS: input/output int-max saturation, NBT and actual menu packet initial/update roundtrips");
        } finally {
            setting.set(previous);
            for (int i = 0; i < blocks.length; i++) if (before[i] != null) level.setBlock(origin.offset(i, 0, 0), before[i], Block.UPDATE_ALL);
        }
    }
    private static void checkTransferLimits(ServerLevel level, BlockPos at) {
        var before = level.getBlockState(at);
        int[] expected = {100, 1_000, 10_000, 100_000, 1_000_000, 10_000_000, 100_000_000, 214_748_364};
        try {
            for (var tier : EnergyHatchData.values()) {
                var capacity = Config.entryForEnergyCapacity(tier);
                var transfer = Config.entryForEnergyTransfer(tier);
                long oldCapacity = capacity.get(), oldTransfer = transfer.get();
                try {
                    capacity.set((long) tier.getDefaultMaxEnergy()); transfer.set(0L);
                    int rate = expected[tier.ordinal()];
                    require(tier.getMaxTransfer() == rate, "automatic tier transfer");
                    for (var block : new Block[]{RegistryBlocks.energyInputHatch.get(), RegistryBlocks.energyOutputHatch.get()}) {
                        level.setBlock(at, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        level.setBlock(at, block.defaultBlockState().setValue(BlockEnergyHatch.SIZE, tier), Block.UPDATE_ALL);
                        var hatch = (TileEnergyHatch) level.getBlockEntity(at);
                        var external = hatch.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.ENERGY).orElse(null);
                        boolean input = hatch.getIOType() == IOType.INPUT;
                        require(hatch.produceInternal(Integer.MAX_VALUE, true) == rate && hatch.getStoredEnergy() == 0,
                                "automatic transfer simulation mutated buffer or ignored limit");
                        for (int n = 0; n < 10; n++) {
                            int received = input ? external.receiveEnergy(Integer.MAX_VALUE, false)
                                    : hatch.produceInternal(Integer.MAX_VALUE, false);
                            require(received == rate, "ten full-rate receives");
                        }
                        int remainder = hatch.getCapacity() - rate * 10;
                        require(hatch.produceInternal(Integer.MAX_VALUE, false) == remainder
                                && hatch.getStoredEnergy() == hatch.getCapacity(), "rounding remainder or int-max saturation");
                        require(input ? external.extractEnergy(Integer.MAX_VALUE, false) == 0
                                : external.receiveEnergy(Integer.MAX_VALUE, false) == 0, "external direction gate");
                        require((input ? hatch.consumeInternal(Integer.MAX_VALUE, true)
                                : external.extractEnergy(Integer.MAX_VALUE, true)) == rate
                                && hatch.getStoredEnergy() == hatch.getCapacity(), "extract simulation/limit");
                        require((input ? hatch.consumeInternal(Integer.MAX_VALUE, false)
                                : external.extractEnergy(Integer.MAX_VALUE, false)) == rate, "actual extraction limit");
                    }
                    capacity.set(777L);
                    require(tier.getMaxTransfer() == 77, "auto transfer did not follow custom capacity");
                    capacity.set(1L);
                    require(tier.getMaxTransfer() == 1, "sub-ten capacity cannot transfer");
                    transfer.set(321L); capacity.set(777L);
                    require(tier.getMaxTransfer() == 321, "explicit transfer override lost");
                } finally { capacity.set(oldCapacity); transfer.set(oldTransfer); }
            }
            Modularmachinery.LOG.info("[MMC][energy-transfer] PASS: eight real input/output tiers, automatic 10%, rounding, int-max saturation, simulate/direction/internal IO, custom capacity and explicit override");
        } finally { level.setBlock(at, before, Block.UPDATE_ALL); }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

