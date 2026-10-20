/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.tiles.me;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuMEItemOutputBus;
import hellfirepvp.modularmachinery.common.registry.RegistryMEBlocks;
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

import java.util.UUID;

/** Verifies the output bus's logical 32-bit cap survives vanilla menu packets. */
public final class MEItemOutputMenuRegressionChecks {
    private MEItemOutputMenuRegressionChecks() { }

    public static void run(ServerLevel level, BlockPos pos) {
        var previous = level.getBlockState(pos);
        var player = FakePlayerFactory.get(level, new GameProfile(
                UUID.fromString("2e2deea2-5b0d-4ab3-b2a0-bd0f98b1bd8a"), "MMCEItemOutputMenu"));
        try {
            level.removeBlock(pos, false);
            level.setBlock(pos, RegistryMEBlocks.all().get("blockmeitemoutputbus").get().defaultBlockState(), Block.UPDATE_ALL);
            require(level.getBlockEntity(pos) instanceof MEItemOutputBus, "item output bus tile missing");
            var bus = (MEItemOutputBus) level.getBlockEntity(pos);
            var server = MenuMEItemOutputBus.forBus(11, player.getInventory(), bus);
            MenuMEItemOutputBus client = new MenuMEItemOutputBus(11, player.getInventory(), pos);
            server.addSlotListener(new ContainerListener() {
                @Override public void slotChanged(AbstractContainerMenu menu, int slot, ItemStack stack) { }
                @Override public void dataChanged(AbstractContainerMenu menu, int index, int value) {
                    sendWord(client, index, value);
                }
            });
            require(client.getConfiguredStackSize() == MEItemOutputBus.DEFAULT_STACK_SIZE,
                    "initial output cap was not synchronized");
            int[] values = {1, 32768, 65536, 250000, Integer.MAX_VALUE, 7};
            for (int value : values) {
                bus.setConfiguredStackSize(value);
                server.broadcastChanges();
                require(client.getConfiguredStackSize() == value,
                        "output cap packet changed " + value + " to " + client.getConfiguredStackSize());
            }
            Modularmachinery.LOG.info("[MMCE][regression] ME item output menu PASS: 32-bit cap packet roundtrip");
        } finally {
            player.closeContainer();
            level.removeBlock(pos, false);
            level.setBlock(pos, previous, Block.UPDATE_ALL);
        }
    }

    private static void sendWord(MenuMEItemOutputBus client, int index, int word) {
        FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ClientboundContainerSetDataPacket(11, index, word).write(wire);
            var decoded = new ClientboundContainerSetDataPacket(wire);
            client.setData(decoded.getId(), decoded.getValue());
        } finally {
            wire.release();
        }
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
