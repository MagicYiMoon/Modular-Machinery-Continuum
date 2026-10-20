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
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
