/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundContainerSetDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.util.FakePlayerFactory;
import java.util.*;

/** Real generic/dedicated menus and vanilla packet transport, without loading client rendering classes. */
public final class ControllerPerformanceRegressionChecks {
    private ControllerPerformanceRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos origin) {
        var saved = new LinkedHashMap<BlockPos, net.minecraft.world.level.block.state.BlockState>();
        var blocks = new ArrayList<Block>(List.of(RegistryBlocks.blockController.get(), RegistryBlocks.blockFactoryController.get()));
        for (boolean factory : List.of(false, true)) blocks.add(DedicatedControllers.entries().stream()
                .filter(e -> e.factory() == factory).findFirst().orElseThrow().block().get());
        var player = FakePlayerFactory.getMinecraft(level);
        try {
            var clients = new ArrayList<AbstractContainerMenu>();
            for (int i = 0; i < blocks.size(); i++) {
                BlockPos at = origin.offset(i * 3, 0, 0);
                saved.put(at, level.getBlockState(at));
                level.setBlock(at, blocks.get(i).defaultBlockState(), Block.UPDATE_ALL);
                var tile = (TileController) level.getBlockEntity(at);
                require(tile != null, "controller tile missing");
                AbstractContainerMenu server = tile instanceof TileFactoryController factory
                        ? MenuFactoryController.forController(1, player.getInventory(), factory)
                        : MenuMachineController.forController(1, player.getInventory(), tile);
                var client = newClient(tile, player.getInventory());
                clients.add(client);
                Set<Integer> indices = connect(server, client);
                require(indices.size() == MenuMachineController.DATA_SIZE + ControllerPerformanceData.SIZE * 2,
                        "initial status/performance slot layout");
                require(values(client).tickText().equals("—") && values(client).searchText().equals("—"),
                        "no sample must not claim a zero measurement");
                require(values(client).mode().equals("gui.factory.work_mode.sync"), "engine work mode");
                server.setData(MenuMachineController.DATA_SIZE, 65535);
                server.setData(MenuMachineController.DATA_SIZE + 1, 65535);
                require(tile.getAveragePerformanceMicros() == 0 && tile.getPerformanceSamples() == 0,
                        "remote data changed server timing source");
                tile.recordPerformanceNanos(0); tile.recordRecipeSearchNanos(0); server.broadcastChanges();
                require(values(client).tickText().equals("0") && values(client).searchText().equals("0.00"),
                        "measured zero is not missing");
                // Above signed short, unsigned short, signed int and unsigned int limits in both longs.
                long tickNanos = 5_000_123_456_000_000L + i * 2_000_000L;
                long searchNanos = 9_000_765_432_000_000L + i * 2_000_000L;
                tile.recordPerformanceNanos(tickNanos); tile.recordRecipeSearchNanos(searchNanos);
                server.broadcastChanges();
                require(values(client).tick() == tickNanos / 2 / 1000
                                && values(client).search() == searchNanos / 2 / 1000,
                        "real menu packet high words truncated");
                require(values(client).tickSamples() == 2 && values(client).searchSamples() == 2, "sample update");
                var reopened = newClient(tile, player.getInventory());
                connect(server, reopened);
                require(values(reopened).tick() == values(client).tick()
                        && values(reopened).search() == values(client).search()
                        && values(reopened).tickSamples() == 2, "initial open with existing 64-bit samples");
                connect(server, client);
                require(values(client).searchText().equals("450038271" + (6 + i) + ".00"),
                        "microseconds converted to two-decimal milliseconds");
                if (i == 1) require(values(clients.get(0)).tick() != values(client).tick(),
                        "different open controllers share a cache");
                // Real recent windows expire old large values and clear every high word on the client.
                for (int n = 0; n < 100; n++) tile.recordPerformanceNanos(123_000);
                for (int n = 0; n < 20; n++) tile.recordRecipeSearchNanos(1_235_000);
                server.broadcastChanges();
                require(values(client).tick() == 123 && values(client).search() == 1235
                                && values(client).searchText().equals("1.24") && values(client).tickSamples() == 100
                                && values(client).searchSamples() == 20,
                        "live recent-window update or stale high word");
                TileController rebuilt = tile instanceof TileFactoryController
                        ? new TileFactoryController(at, tile.getBlockState()) : new TileController(at, tile.getBlockState());
                rebuilt.load(tile.saveWithoutMetadata());
                require(rebuilt.getPerformanceSamples() == 0 && rebuilt.getRecipeSearchSamples() == 0,
                        "diagnostic samples persisted to NBT");
            }
            checkViewport();
            Modularmachinery.LOG.info("[MMC][controller-performance] PASS: generic/dedicated ordinary/factory native menus, signed-short packet initial/live updates, 64-bit timings, per-controller isolation, missing/zero, windows, ms rounding, NBT and bounded status/footer scroll");
        } finally {
            saved.forEach((at, state) -> level.setBlock(at, state, Block.UPDATE_ALL));
        }
    }

    private record Values(long tick, long search, int tickSamples, int searchSamples,
                          String tickText, String searchText, String mode) { }
    private static Values values(AbstractContainerMenu menu) {
        if (menu instanceof MenuMachineController machine) return new Values(machine.getAveragePerformanceMicros(),
                machine.getAverageRecipeSearchMicros(), machine.getPerformanceSamples(), machine.getRecipeSearchSamples(),
                machine.performanceTickText(), machine.performanceSearchText(), machine.workModeTranslationKey());
        var factory = (MenuFactoryController) menu;
        return new Values(factory.getAveragePerformanceMicros(), factory.getAverageRecipeSearchMicros(),
                factory.getPerformanceSamples(), factory.getRecipeSearchSamples(), factory.performanceTickText(),
                factory.performanceSearchText(), factory.workModeTranslationKey());
    }
    private static AbstractContainerMenu newClient(TileController tile, net.minecraft.world.entity.player.Inventory inventory) {
        return tile instanceof TileFactoryController ? new MenuFactoryController(1, inventory, tile.getBlockPos())
                : new MenuMachineController(1, inventory, tile.getBlockPos());
    }

    private static Set<Integer> connect(AbstractContainerMenu server, AbstractContainerMenu client) {
        Set<Integer> indices = new HashSet<>();
        server.setSynchronizer(new ContainerSynchronizer() {
            @Override public void sendInitialData(AbstractContainerMenu menu,
                    net.minecraft.core.NonNullList<ItemStack> stacks, ItemStack carried, int[] data) {
                for (int i = 0; i < data.length; i++) sendDataChange(menu, i, data[i]);
            }
            @Override public void sendSlotChange(AbstractContainerMenu menu, int slot, ItemStack stack) { }
            @Override public void sendCarriedChange(AbstractContainerMenu menu, ItemStack stack) { }
            @Override public void sendDataChange(AbstractContainerMenu menu, int index, int value) {
                indices.add(index);
                FriendlyByteBuf wire = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    new ClientboundContainerSetDataPacket(1, index, value).write(wire);
                    var decoded = new ClientboundContainerSetDataPacket(wire);
                    client.setData(decoded.getId(), decoded.getValue());
                } finally { wire.release(); }
            }
        });
        return indices;
    }

    private static void checkViewport() {
        var ordinary = new MachineStatusViewport();
        require(MachineStatusViewport.SCROLL_X + MachineStatusViewport.SCROLL_WIDTH <= 150
                && MachineStatusViewport.FOOTER_Y + MachineStatusViewport.FOOTER_ROWS * MachineStatusViewport.FOOTER_STRIDE
                < MenuMachineController.PLAYER_INV_Y - 12, "ordinary status/footer overlaps blueprint or inventory");
        ordinary.updateHeight(1000);
        require(ordinary.scroll(10, 20, -1) && ordinary.offset() == 10, "ordinary source row wheel stride");
        require(!ordinary.scroll(152, 15, -1) && !ordinary.scroll(10, 132, -1)
                && !ordinary.scroll(10, MachineStatusViewport.FOOTER_Y, -1), "ordinary viewport steals slot/footer wheel");
        require(ordinary.click(MachineStatusViewport.SCROLL_X, 89, 0) && ordinary.offset() == ordinary.maxOffset()
                && ordinary.drag(-100, 0) && ordinary.offset() == 0 && ordinary.release(0), "ordinary drag bounds");
        ordinary.updateHeight(10);
        require(ordinary.maxOffset() == 0 && !ordinary.click(MachineStatusViewport.SCROLL_X, 20, 0), "ordinary short state clamps");

        var view = new FactoryStatusViewport();
        require(FactoryStatusViewport.Y + FactoryStatusViewport.HEIGHT < FactoryStatusViewport.FOOTER_Y
                && FactoryStatusViewport.FOOTER_Y + FactoryStatusViewport.FOOTER_ROWS * FactoryStatusViewport.FOOTER_STRIDE
                < MenuFactoryController.PLAYER_INV_Y - 12, "footer overlaps status/inventory label");
        require(FactoryStatusViewport.SCROLL_X + FactoryStatusViewport.SCROLL_WIDTH <= 254,
                "scroll track overlaps blueprint slot");
        require(!view.scroll(120, 20, -1), "empty area consumes wheel");
        view.updateHeight(1200);
        require(view.scroll(120, 20, -1) && view.offset() == 12, "status wheel");
        int old = view.offset();
        require(!view.scroll(20, 20, -1) && !view.scroll(120, FactoryStatusViewport.FOOTER_Y, -1)
                && view.offset() == old, "status scroll steals thread/footer wheel");
        require(view.click(249, 89, 0) && view.offset() == view.maxOffset(), "track jump to end");
        require(view.drag(-100, 0) && view.offset() == 0 && view.release(0), "drag/release bounds");
        require(!view.drag(90, 0) && !view.click(249, 20, 1), "released/wrong-button drag");
        view.scroll(120, 20, -1); view.updateHeight(10);
        require(view.offset() == 0 && view.maxOffset() == 0 && !view.click(249, 20, 0), "short content resets scroll");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
