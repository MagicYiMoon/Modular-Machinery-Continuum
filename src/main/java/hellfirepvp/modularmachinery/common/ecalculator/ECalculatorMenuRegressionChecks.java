/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.ecalculator;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.MenuECalculatorController;
import hellfirepvp.modularmachinery.common.network.ECalculatorMonitorPackets;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;
import static hellfirepvp.modularmachinery.common.ecalculator.ECalculatorMonitor.*;

/** Common-menu isolation, wire bounds and actual physical core pagination, with and without AE. */
public final class ECalculatorMenuRegressionChecks {
    private ECalculatorMenuRegressionChecks() { }
    private static void checkStatistics() {
        var recorder = new ECalculatorStatistics();
        require(!recorder.measurement().ready(), "unsampled CPU reported measured zero");
        recorder.record(1500, 1); recorder.record(500, 0);
        require(recorder.measurement().samples() == 2 && recorder.measurement().nanosPerTick() == 1000
                && recorder.measurement().operationsPerTick() == .5, "fractional operations or nanosecond average lost");
        for (int i = 0; i < 100; i++) recorder.record(0, 0);
        require(recorder.measurement().samples() == 100 && recorder.measurement().nanosPerTick() == 0
                && recorder.measurement().operationsPerTick() == 0, "100-update window retained stale operations");
        for (int i = 0; i < 101; i++) recorder.record(Long.MAX_VALUE, Long.MAX_VALUE);
        require(recorder.measurement().nanosPerTick() == Long.MAX_VALUE / 100 && recorder.measurement().operationsPerTick() > 0, "measurement overflow");
        try { new ECalculatorStatistics.Measurement(1, 0, Double.NaN); throw new AssertionError("NaN statistics accepted"); } catch (IllegalArgumentException expected) { }
        try { new ECalculatorStatistics.Measurement(0, 1, 0); throw new AssertionError("unknown with nonzero values accepted"); } catch (IllegalArgumentException expected) { }
        try { new ECalculatorStatistics.Page(0, true, 0, 0, Collections.nCopies(5, new ECalculatorStatistics.Task(UUID.randomUUID(), ECalculatorStatistics.Measurement.UNKNOWN))); throw new AssertionError("unbounded statistics accepted"); } catch (IllegalArgumentException expected) { }
    }
    public static void run(ServerLevel world, BlockPos pos) {
        checkStatistics();
        var definitions = MachineReloadListener.getLoader().getMachines();
        var id = new ResourceLocation("modularmachinery", ECalculatorRegistration.machineName(9)); var old = definitions.get(id);
        var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "EcMonitorCheck"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        try {
            var pattern = new MachinePattern();
            for (int i = 1; i <= 13; i++) pattern.addPart(new MachinePattern.Part(i, 0, 0, "modularmachinery:ecalculator_thread_core_hyper_l9"));
            pattern.addPart(new MachinePattern.Part(14, 0, 0, "modularmachinery:ecalculator_cell_drive"));
            definitions.put(id, new DynamicMachine(id.getPath(), "EC monitor fixture", pattern));
            world.setBlockAndUpdate(pos, ECalculatorRegistration.CONTROLLERS.get(9).get().defaultBlockState());
            for (int i = 1; i <= 13; i++) world.setBlockAndUpdate(pos.east(i), ECalculatorRegistration.PARTS.get("ecalculator_thread_core_hyper_l9").get().defaultBlockState());
            world.setBlockAndUpdate(pos.east(14), ECalculatorRegistration.DRIVE.get().defaultBlockState());
            var owner = (TileECalculatorController)world.getBlockEntity(pos); owner.refreshStructure();
            ((TileECalculatorDrive)world.getBlockEntity(pos.east(14))).insertMemory(new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.C).get()));
            var session = UUID.randomUUID(); var menu = MenuECalculatorController.forController(2, player.getInventory(), owner, session);
            player.containerMenu = menu; menu.broadcastChanges();
            require(menu.slots.isEmpty() && menu.quickMoveStack(player, 0).isEmpty(), "task icons exposed as physical inventory");
            require(menu.snapshot().capacity() == 16_777_216_000L && menu.snapshot().maxNormal() == 13 && menu.snapshot().maxHyper() == 104, "physical capacity/core quotas");
            require(menu.snapshot().corePages() == 2 && menu.snapshot().cores().size() == 12, "bounded first core page");
            boolean ae = net.minecraftforge.fml.ModList.get().isLoaded("ae2");
            if (net.minecraftforge.fml.ModList.get().isLoaded("theoneprobe"))
                hellfirepvp.modularmachinery.integration.top.ECalculatorProbeRegressionChecks.run(world, pos, player);
            require(((menu.snapshot().flags() & 16) == 0) == ae && menu.snapshot().cores().get(0).readable() == ae, "unavailable backend reported as zero known usage");
            require(menu.statistics().ready() == ae && menu.statistics().operationsPerTick() == 0, "empty readable/absent backend statistics");
            require(menu.clickMenuButton(player, 1) && menu.snapshot().corePage() == 1 && menu.snapshot().cores().size() == 1, "last core page");
            menu.clickMenuButton(player, 1); require(menu.snapshot().corePage() == 1, "page upper clamp");
            if (ae) {
                var core = (TileECalculatorPart)world.getBlockEntity(pos.east(13));
                var opaque = new net.minecraft.nbt.CompoundTag(); opaque.putString("AE2Crafting", "unknown-format"); core.restoreBackend(opaque);
                menu.clickMenuButton(player, 1);
                require((menu.snapshot().flags() & 16) != 0 && !menu.snapshot().cores().get(0).readable()
                        && core.backendSnapshot().equals(opaque), "invalid saved tasks reported as empty or rewritten");
                require(!menu.statistics().ready(), "unreadable core reported measured zero");
                core.restoreBackend(new net.minecraft.nbt.CompoundTag()); menu.clickMenuButton(player, 1);
            }
            require(!menu.clickMenuButton(player, -1) && !menu.clickMenuButton(player, 4), "invalid menu action accepted");
            require(!menu.cancel(player, UUID.randomUUID(), UUID.randomUUID()), "wrong session accepted");
            var client = new MenuECalculatorController(2, player.getInventory(), pos, session);
            var sampleTask = new Task(UUID.randomUUID(), Component.translatable("item.minecraft.diamond"), new ItemStack(Items.DIAMOND),
                    Long.MAX_VALUE, 7_777_777_777L, true, true, false);
            var data = new Snapshot(15, 9, Long.MAX_VALUE, Long.MAX_VALUE - 1, 7_777_777_777L, 18_688,
                    4, 8, 4, 8, 0, 1, 1, 2, 5, List.of(7_777_777_777L), List.of(), List.of(sampleTask));
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ECalculatorMonitorPackets.Update.encode(new ECalculatorMonitorPackets.Update(2, session, data), buffer);
                var decoded = ECalculatorMonitorPackets.Update.decode(buffer);
                require(buffer.readableBytes() == 0 && decoded.data().tasks().get(0).icon().is(Items.DIAMOND) && decoded.data().capacity() == Long.MAX_VALUE
                        && decoded.data().tasks().get(0).amount() == Long.MAX_VALUE
                        && decoded.data().tasks().get(0).bytes() == 7_777_777_777L, "64-bit packet round trip");
                require(decoded.data().tasks().get(0).name().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents,
                        "dedicated server replaced the client-localized item name with its own language");
                client.accept(UUID.randomUUID(), decoded.data()); require(client.snapshot().used() == 0, "old window snapshot accepted");
                client.accept(session, decoded.data()); require(client.snapshot().used() == Long.MAX_VALUE - 1, "current window snapshot missing");
            } finally { buffer.release(); }
            var statsData = new ECalculatorStatistics.Page(1, true, 7_777_777_777L, .125,
                    List.of(new ECalculatorStatistics.Task(sampleTask.id(), new ECalculatorStatistics.Measurement(8, 7_777_777_777L, .125))));
            var statsBuffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                ECalculatorMonitorPackets.Statistics.encode(new ECalculatorMonitorPackets.Statistics(2, session, statsData), statsBuffer);
                var decoded = ECalculatorMonitorPackets.Statistics.decode(statsBuffer);
                require(statsBuffer.readableBytes() == 0 && decoded.data().equals(statsData), "statistics long/fractional packet round trip");
                client.acceptStatistics(UUID.randomUUID(), decoded.data()); require(!client.statistics().ready(), "foreign statistics session accepted");
                client.acceptStatistics(session, new ECalculatorStatistics.Page(0, true, 1, 1, statsData.tasks())); require(!client.statistics().ready(), "foreign statistics page accepted");
                client.acceptStatistics(session, new ECalculatorStatistics.Page(1, true, 1, 1, List.of(new ECalculatorStatistics.Task(UUID.randomUUID(), new ECalculatorStatistics.Measurement(1, 1, 1))))); require(!client.statistics().ready(), "foreign statistics task accepted");
                client.acceptStatistics(session, decoded.data()); require(client.statistics().equals(statsData), "current statistics page rejected");
                client.accept(session, data); require(!client.statistics().ready(), "new base page kept stale statistics");
            } finally { statsBuffer.release(); }
            Modularmachinery.LOG.info("[MMC][ec-statistics] PASS: 100-update window, fractional operations, overflow bounds, unknown backend, bounded codec and window/page/task isolation");
            try { new Snapshot(0, 9, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 5, List.of(), List.of(), Collections.nCopies(5, sampleTask));
                throw new AssertionError("unbounded task page accepted"); } catch (IllegalArgumentException expected) { }
            player.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
            require(!menu.stillValid(player) && !menu.clickMenuButton(player, 0), "distant action accepted");
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
            world.setBlockAndUpdate(pos.east(13), Blocks.AIR.defaultBlockState()); owner.refreshStructure(); menu.clickMenuButton(player, 0);
            require(menu.snapshot().cores().isEmpty() && menu.snapshot().corePages() == 1 && menu.snapshot().corePage() == 0, "broken structure retained stale core pages");
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()); require(!menu.stillValid(player), "removed controller menu valid");
            Modularmachinery.LOG.info("[MMC][ec-menu] PASS: no inventory slots, 13 real cores/two bounded pages, long capacities/tasks over the wire, window UUID isolation, invalid/distant actions and structure loss; AE2 present={}", net.minecraftforge.fml.ModList.get().isLoaded("ae2"));
        } finally {
            player.containerMenu = player.inventoryMenu;
            for (int i = 0; i <= 14; i++) world.setBlockAndUpdate(pos.east(i), Blocks.AIR.defaultBlockState());
            if (old == null) definitions.remove(id); else definitions.put(id, old);
        }
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
