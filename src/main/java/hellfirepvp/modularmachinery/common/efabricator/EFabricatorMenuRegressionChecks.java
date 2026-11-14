/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.efabricator;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.MenuEFabricatorController;
import hellfirepvp.modularmachinery.common.network.EFabricatorMonitorPackets;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;
import static hellfirepvp.modularmachinery.common.efabricator.EFabricatorMonitor.*;

public final class EFabricatorMenuRegressionChecks {
    private EFabricatorMenuRegressionChecks() { }
    public static void run(ServerLevel world, BlockPos pos) {
        var definitions = MachineReloadListener.getLoader().getMachines();
        var id = new ResourceLocation("modularmachinery", EFabricatorRegistration.machineName(EFabricatorSpecs.Tier.L9)); var old = definitions.get(id);
        var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "EfMenuCheck"));
        player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        try {
            var pattern = new MachinePattern();
            for (int i = 1; i <= 13; i++) pattern.addPart(new MachinePattern.Part(i, 0, 0, "modularmachinery:efabricator_worker"));
            pattern.addPart(new MachinePattern.Part(1, 1, 0, "modularmachinery:efabricator_parallel_proc_l6"));
            pattern.addPart(new MachinePattern.Part(1, -1, 0, "modularmachinery:efabricator_parallel_proc_l9"));
            definitions.put(id, new DynamicMachine(id.getPath(), "EF menu fixture", pattern));
            world.setBlockAndUpdate(pos, EFabricatorRegistration.CONTROLLERS.get(EFabricatorSpecs.Tier.L9).get().defaultBlockState());
            for (int i = 1; i <= 13; i++) world.setBlockAndUpdate(pos.east(i), EFabricatorRegistration.WORKER.get().defaultBlockState());
            world.setBlockAndUpdate(pos.east().above(), EFabricatorRegistration.PARTS.get("efabricator_parallel_proc_l6").get().defaultBlockState());
            world.setBlockAndUpdate(pos.east().below(), EFabricatorRegistration.PARTS.get("efabricator_parallel_proc_l9").get().defaultBlockState());
            var owner = (TileEFabricatorController)world.getBlockEntity(pos); owner.refreshStructure(); require(owner.isAssembled(), "real menu structure not formed");
            var w = (TileEFabricatorWorker)world.getBlockEntity(pos.east());
            var icon = new ItemStack(Items.DIAMOND); icon.getOrCreateTag().putString("Private", "not-for-viewers");
            var q = new EFabricatorQueue(); q.offer(new EFabricatorQueue.Work(8, icon, List.of()), 32);
            var work = new CompoundTag(); work.putInt("Version", 1); work.putInt("Energy", 432100); work.put("Queue", q.save()); w.restoreWorkData(work);
            var production = owner.productionData(); production.putLong("TotalCrafted", 7_777_777_777L); owner.restoreProductionData(production);
            var session = UUID.randomUUID(); var menu = MenuEFabricatorController.forController(2, player.getInventory(), owner, session);
            player.containerMenu = menu; menu.broadcastChanges(); var data = menu.snapshot();
            require(menu.slots.size() == 36 && menu.slots.stream().allMatch(s -> s.container == player.getInventory()), "virtual work icon exposed as inventory");
            require(data.pages() == 2 && data.workers().size() == 12 && data.workerCount() == 13, "bounded physical worker pages");
            require(data.queued() == 8 && data.queueCapacity() == 13 * 32 && data.energy() == 432100 && data.energyCapacity() == 6500000L, "real queue/cache totals");
            require(data.tier() == 9 && data.processors() == 2, "host display tier and actual shared parallel modules");
            require(!data.workers().get(0).icon().hasTag() && w.workData().equals(work), "display leaked or changed private work data");
            require(!menu.action(player, UUID.randomUUID(), 0, true) && !owner.isOverclocked(), "foreign session changed mode");
            require(menu.action(player, session, 0, true) && owner.isOverclocked() && menu.snapshot().queueCapacity() == 13 * 512, "overclock request not applied");
            menu.action(player, session, 0, true); require(owner.isOverclocked(), "repeated explicit request toggled mode");
            require(menu.action(player, session, 1, true) && owner.isActiveCooling() && menu.snapshot().delay() == 7, "active cooling or delay");
            require(menu.action(player, session, 3, false) && menu.snapshot().page() == 1 && menu.snapshot().workers().size() == 1, "last page");
            menu.action(player, session, 3, false); require(menu.snapshot().page() == 1, "page clamp");
            require(!menu.action(player, session, -1, true) && !menu.action(player, session, 5, true), "invalid action");
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                EFabricatorMonitorPackets.Update.encode(new EFabricatorMonitorPackets.Update(2, session, data), buffer);
                var decoded = EFabricatorMonitorPackets.Update.decode(buffer);
                require(buffer.readableBytes() == 0 && decoded.data().crafted() == 7_777_777_777L && decoded.data().energy() == 432100
                        && decoded.data().workers().get(0).icon().is(Items.DIAMOND) && !decoded.data().workers().get(0).icon().hasTag(), "wire round trip/long/private data");
                var client = new MenuEFabricatorController(2, player.getInventory(), pos, session);
                client.accept(UUID.randomUUID(), decoded.data()); require(client.snapshot().workerCount() == 0, "foreign display session");
                client.accept(session, decoded.data()); require(client.snapshot().crafted() == 7_777_777_777L, "current display session");
            } finally { buffer.release(); }
            try { new Worker(pos, true, 0, 32, Double.NaN, ItemStack.EMPTY); throw new AssertionError("NaN cache accepted"); } catch (IllegalArgumentException expected) { }
            var opaque = new CompoundTag(); opaque.putInt("Version", 999); w.restoreWorkData(opaque); menu.action(player, session, 2, false);
            require(!menu.snapshot().readable() && !menu.snapshot().workers().get(0).readable() && w.workData().equals(opaque), "unknown work shown as trusted empty data");
            player.getInventory().setItem(9, new ItemStack(Items.APPLE, 3));
            require(menu.quickMoveStack(player, 0).getCount() == 3 && player.getInventory().getItem(9).isEmpty() && player.getInventory().getItem(0).is(Items.APPLE), "player shift-transfer");
            player.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
            require(!menu.stillValid(player) && !menu.action(player, session, 0, false), "remote mode request");
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2); player.containerMenu = player.inventoryMenu;
            require(!menu.action(player, session, 0, false), "closed menu accepted request"); player.containerMenu = menu;
            world.setBlockAndUpdate(pos.east(13), Blocks.AIR.defaultBlockState()); owner.refreshStructure(); menu.action(player, session, 2, false);
            require(menu.snapshot().workers().isEmpty() && menu.snapshot().pages() == 1, "broken structure stale page");
            world.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            world.setBlockAndUpdate(pos, EFabricatorRegistration.CONTROLLERS.get(EFabricatorSpecs.Tier.L9).get().defaultBlockState());
            require(!menu.stillValid(player) && !menu.action(player, session, 0, false), "replaced controller old reference");
            Modularmachinery.LOG.info("[MMC][ef-menu] PASS: 13 real cores, two bounded pages, source host-tier display and actual shared processor count, private-NBT-free icons, long totals/cache codec, explicit idempotent modes, player-only slots/shift transfer, unknown data, session/closed/distance/replacement isolation; AE2={}", net.minecraftforge.fml.ModList.get().isLoaded("ae2"));
        } finally {
            player.containerMenu = player.inventoryMenu;
            for (int i = 0; i <= 13; i++) world.setBlockAndUpdate(pos.east(i), Blocks.AIR.defaultBlockState());
            world.setBlockAndUpdate(pos.east().above(), Blocks.AIR.defaultBlockState()); world.setBlockAndUpdate(pos.east().below(), Blocks.AIR.defaultBlockState());
            if (old == null) definitions.remove(id); else definitions.put(id, old);
        }
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
