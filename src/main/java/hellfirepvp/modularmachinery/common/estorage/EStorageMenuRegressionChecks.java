/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.MenuEStorageController;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;
import java.util.*;

public final class EStorageMenuRegressionChecks {
    private EStorageMenuRegressionChecks() { }
    public static void run(ServerLevel world, BlockPos pos) {
        var definitions = MachineReloadListener.getLoader().getMachines();
        var id = new ResourceLocation("modularmachinery", EStorageRegistration.machineName(9)); var saved = definitions.get(id);
        var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "EsMenuCheck"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        try {
            var pattern = new MachinePattern();
            for (int i = 1; i <= 9; i++) pattern.addPart(new MachinePattern.Part(i, 0, 0, "modularmachinery:estorage_cell_drive"));
            pattern.addPart(new MachinePattern.Part(10, 0, 0, "modularmachinery:estorage_energy_cell_l9"));
            definitions.put(id, new DynamicMachine(id.getPath(), "ES screen test", pattern));
            for (int i = 0; i <= 10; i++) require(world.getBlockState(pos.east(i)).isAir(), "menu fixture not empty");
            world.setBlockAndUpdate(pos, EStorageRegistration.CONTROLLERS.get(9).get().defaultBlockState().setValue(BlockEStorageController.FACING, Direction.NORTH));
            for (int i = 1; i <= 9; i++) world.setBlockAndUpdate(pos.east(i), EStorageRegistration.DRIVE.get().defaultBlockState());
            world.setBlockAndUpdate(pos.east(10), EStorageRegistration.ENERGY_CELLS.get(9).get().defaultBlockState());
            var battery = (TileEStorageEnergyCell) world.getBlockEntity(pos.east(10)); battery.inject(987654321.125, false);
            var owner = (TileEStorageController) world.getBlockEntity(pos); owner.refreshStructure();
            var spec = new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.C);
            for (int i = 1; i <= 9; i++) {
                var drive = (TileEStorageDrive) world.getBlockEntity(pos.east(i));
                drive.insertMatrix(new ItemStack(EStorageRegistration.CELLS.get(spec).get()));
            }
            owner.refreshStructure();
            boolean ae = net.minecraftforge.fml.ModList.get().isLoaded("ae2");
            if (ae) {
                for (int i = 1; i <= 9; i++) hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks
                        .seed(((TileEStorageDrive) world.getBlockEntity(pos.east(i))).storageAccess(), spec);
            }
            var server = MenuEStorageController.forController(90, player.getInventory(), owner);
            var client = new MenuEStorageController(90, player.getInventory(), pos);
            final int[] fields = {0};
            server.setSynchronizer(new ContainerSynchronizer() {
                public void sendInitialData(AbstractContainerMenu menu, NonNullList<ItemStack> stacks, ItemStack carried, int[] data) {
                    fields[0] = data.length; for (int i = 0; i < data.length; i++) sendDataChange(menu, i, data[i]);
                }
                public void sendSlotChange(AbstractContainerMenu menu, int index, ItemStack stack) { }
                public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) { }
                public void sendDataChange(AbstractContainerMenu menu, int index, int value) {
                    var wire = new FriendlyByteBuf(Unpooled.buffer());
                    try { new ClientboundContainerSetDataPacket(90, index, value).write(wire); var packet = new ClientboundContainerSetDataPacket(wire); client.setData(packet.getId(), packet.getValue()); }
                    finally { wire.release(); }
                }
            });
            require(fields[0] == (MenuEStorageController.ROW_BASE + 6 * MenuEStorageController.ROW_STRIDE) * 4, "unbounded sync field count");
            require(client.energyValue(MenuEStorageController.ENERGY) == 987654321.125
                    && client.energyValue(MenuEStorageController.ENERGY_MAX) == 1_000_000_000, "double energy truncated on signed-short wire");
            require(client.value(MenuEStorageController.MATRICES) == (ae ? 9 : 0)
                    && client.value(MenuEStorageController.CAPACITY) == (ae ? 2_359_296_000L : 0), "matrix statistics capacity");
            if (ae) {
                require(client.row(0) != null && client.row(6) == null && client.row(-1) == null, "row bounds");
                require(server.clickMenuButton(player, 1), "valid page action failed"); server.broadcastChanges();
                require(client.value(MenuEStorageController.PAGE) == 1 && client.value(MenuEStorageController.ROWS) == 3 && client.row(3) == null, "last page not bounded");
                for (int i = 0; i < 5; i++) server.clickMenuButton(player, 1);
                require(server.value(MenuEStorageController.PAGE) == 1, "page overflow");
                var drive = (TileEStorageDrive) world.getBlockEntity(pos.east());
                var before = drive.summary();
                var data = drive.storageAccess();
                hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageHostNativeChecks.seed(data, spec);
                require(drive.summary().amount() == before.amount() * 2, "deferred matrix content summary stale");
                var cached = drive.summary(); require(cached.usedBytes() > before.usedBytes(), "summary bytes did not change");
                drive.revokeStorageAccess();
                require(drive.summary().amount() == cached.amount(), "offline cached summary lost pending persisted contents");
                for (int i = 3; i <= 9; i++) ((TileEStorageDrive) world.getBlockEntity(pos.east(i))).takeMatrix();
                owner.refreshStructure();
                server.clickMenuButton(player, 1); server.broadcastChanges();
                require(client.value(MenuEStorageController.PAGE) == 0 && client.value(MenuEStorageController.ROWS) == 2
                        && client.row(2) == null, "removed matrix rows/page not cleared");
            }
            require(server.slots.size() == 36 && server.slots.get(0).x == 8 && server.slots.get(0).y == 125
                    && server.slots.get(27).y == 183 && server.slots.get(35).x + 16 < 176, "source inventory layout");
            player.getInventory().setItem(9, new ItemStack(Items.STONE, 7));
            require(server.quickMoveStack(player, 0).getCount() == 7 && player.getInventory().getItem(9).isEmpty(), "player-only shift click");
            require(server.quickMoveStack(player, -1).isEmpty() && !server.clickMenuButton(player, 999), "invalid menu action accepted");
            player.setPos(pos.getX() + 50, pos.getY(), pos.getZ()); require(!server.stillValid(player) && !server.clickMenuButton(player, 0), "distant player accessed menu");
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            require(!server.stillValid(player), "removed controller menu stayed valid");
            Modularmachinery.LOG.info("[MMC][es-menu] PASS: source panel/inventory, bounded six-row pages, signed-short round trip with fractional AE, summary cache/deferred writes, native slot moves, page shrink/clear, invalid/distant actions and controller removal; AE2 present={}", ae);
        } finally {
            for (int i = 0; i <= 10; i++) world.setBlockAndUpdate(pos.east(i), Blocks.AIR.defaultBlockState());
            if (saved == null) definitions.remove(id); else definitions.put(id, saved);
        }
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
