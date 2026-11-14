/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.efabricator;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.*;
import hellfirepvp.modularmachinery.common.network.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;
import static hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView.*;

public final class EFabricatorPatternMenuRegressionChecks {
    private EFabricatorPatternMenuRegressionChecks() { }
    public static void run(ServerLevel world, BlockPos pos) {
        var definitions = MachineReloadListener.getLoader().getMachines(); var id = new ResourceLocation("modularmachinery", EFabricatorRegistration.machineName(EFabricatorSpecs.Tier.L4)); var old = definitions.get(id);
        var player = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "EfPatternMenu")); player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2);
        boolean ae = net.minecraftforge.fml.ModList.get().isLoaded("ae2");
        try {
            var pattern = new MachinePattern(); pattern.addPart(new MachinePattern.Part(1, 0, 0, "modularmachinery:efabricator_pattern_bus"));
            pattern.addPart(new MachinePattern.Part(2, 0, 0, "modularmachinery:efabricator_pattern_bus"));
            definitions.put(id, new DynamicMachine(id.getPath(), "EF pattern menu fixture", pattern));
            world.setBlockAndUpdate(pos, EFabricatorRegistration.CONTROLLERS.get(EFabricatorSpecs.Tier.L4).get().defaultBlockState());
            for (int i = 1; i <= 2; i++) world.setBlockAndUpdate(pos.east(i), EFabricatorRegistration.PATTERN_BUS.get().defaultBlockState());
            var owner = (TileEFabricatorController)world.getBlockEntity(pos); owner.refreshStructure(); require(owner.isAssembled(), "pattern menu structure");
            var a = (TileEFabricatorPatternBus)world.getBlockEntity(pos.east()); var b = (TileEFabricatorPatternBus)world.getBlockEntity(pos.east(2));
            var busMenu = MenuEFabricatorPatternBus.forBus(3, player.getInventory(), a); player.containerMenu = busMenu;
            require(busMenu.slots.size() == 108 && busMenu.slots.get(71).x == 206 && busMenu.slots.get(71).y == 118
                    && busMenu.slots.get(72).x == 35 && busMenu.slots.get(107).y == 208, "source bus slot layout");
            busMenu.setCarried(new ItemStack(Items.STONE, 5)); busMenu.clicked(0, 0, ClickType.PICKUP, player);
            require(a.patternAt(0).isEmpty() && busMenu.getCarried().getCount() == 5, "nonpattern inserted"); busMenu.setCarried(ItemStack.EMPTY);
            var source = ae ? hellfirepvp.modularmachinery.integration.ae2.efabricator.EFabricatorMenuPatterns.create(world) : List.of(new ItemStack(Items.PAPER), new ItemStack(Items.BOOK));
            if (ae) {
                busMenu.setCarried(source.get(0).copy()); busMenu.clicked(71, 0, ClickType.PICKUP, player);
                require(busMenu.getCarried().isEmpty() && ItemStack.matches(a.patternAt(71), source.get(0)), "actual cursor placement last bus slot");
                busMenu.clicked(71, 0, ClickType.PICKUP, player); require(a.patternAt(71).isEmpty() && ItemStack.matches(busMenu.getCarried(), source.get(0)), "actual cursor pickup"); busMenu.setCarried(ItemStack.EMPTY);
                player.getInventory().setItem(9, source.get(0).copy()); busMenu.quickMoveStack(player, 72);
                require(player.getInventory().getItem(9).isEmpty() && !a.patternAt(0).isEmpty(), "shift pattern into bus");
                busMenu.quickMoveStack(player, 0); require(a.patternAt(0).isEmpty() && player.getInventory().countItem(source.get(0).getItem()) == 1, "shift pattern back to player"); player.getInventory().clearContent();
            }
            load(a, source.get(0), 49); load(b, source.get(1), 1);
            var session = UUID.randomUUID(); var menu = open(player.getInventory(), owner, session); var data = menu.snapshot();
            require(data.total() == 50 && data.entries().size() == 48 && data.pages() == 2 && data.buses() == 2 && data.readable(), "bounded physical pattern page");
            require(menu.slots.size() == 36 && menu.slots.stream().allMatch(s -> s.container == player.getInventory()), "virtual pattern entries became item slots");
            if (ae) require(data.entries().get(0).description().valid() && data.entries().get(0).description().icon().is(Items.OAK_PLANKS)
                    && !data.entries().get(0).description().icon().hasTag(), "decoded primary output/private NBT");
            else require(data.entries().get(0).description().icon().isEmpty() && !data.entries().get(0).description().valid(), "AE-less pattern falsely decoded");
            var buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                EFabricatorPatternPackets.Update.encode(new EFabricatorPatternPackets.Update(menu.containerId, session, data), buf);
                var decoded = EFabricatorPatternPackets.Update.decode(buf); require(buf.readableBytes() == 0 && decoded.data().entries().size() == 48, "pattern wire page");
                var client = new MenuEFabricatorPatternSearch(menu.containerId, player.getInventory(), pos, session); client.accept(UUID.randomUUID(), decoded.data());
                require(client.snapshot().total() == 0, "foreign client session"); client.accept(session, decoded.data()); require(client.snapshot().total() == 50, "client snapshot");
                var query = new Query("原木", "planks", Set.of(new ResourceLocation("minecraft:oak_log")), Set.of(new ResourceLocation("minecraft:oak_planks")), 1);
                EFabricatorPatternPackets.Query.encode(new EFabricatorPatternPackets.Query(menu.containerId, session, query), buf);
                var decodedQuery = EFabricatorPatternPackets.Query.decode(buf); require(query.equals(decodedQuery.query()) && buf.readableBytes() == 0, "localized query codec");
            } finally { buf.release(); }
            require(!menu.action(player, UUID.randomUUID(), 0, data.token(), 0) && !menu.action(player, session, 0, data.token() - 1, 0)
                    && !menu.action(player, session, 0, data.token(), 48), "invalid row/session/token");
            var otherPlayer = new FakePlayer(world, new GameProfile(UUID.randomUUID(), "EfOtherViewer")); otherPlayer.setPos(player.getX(), player.getY(), player.getZ());
            var otherSession = UUID.randomUUID(); var otherMenu = open(otherPlayer.getInventory(), owner, otherSession); var otherToken = otherMenu.snapshot().token();
            require(otherMenu.action(otherPlayer, otherSession, 0, otherToken, 0), "second viewer could not take real pattern");
            require(!menu.action(player, session, 0, data.token(), 0) && menu.getCarried().isEmpty(), "two viewers duplicated same pattern");
            otherMenu.setCarried(ItemStack.EMPTY); otherPlayer.containerMenu = otherPlayer.inventoryMenu; load(a, source.get(0), 49);
            menu = open(player.getInventory(), owner, session); data = menu.snapshot();
            var saved = a.patternData(); a.menuInventory().removeItem(0, 1);
            require(!menu.action(player, session, 0, data.token(), 0) && menu.getCarried().isEmpty(), "stale page took replacement/empty item"); a.restorePatternData(saved);
            menu = open(player.getInventory(), owner, session); data = menu.snapshot();
            require(menu.action(player, session, 0, data.token(), 0) && ItemStack.matches(menu.getCarried(), source.get(0)) && a.patternAt(0).isEmpty(), "search pickup conservation");
            require(!menu.action(player, session, 0, data.token(), 0), "replayed pickup");
            if (ae) require(menu.action(player, session, 1, 0, -1) && menu.getCarried().isEmpty() && ItemStack.matches(a.patternAt(0), source.get(0)), "search insertion conservation");
            else { require(!menu.action(player, session, 1, 0, -1), "AE-less insertion accepted"); menu.setCarried(ItemStack.EMPTY); a.restorePatternData(saved); }
            if (ae) {
                var diskA = a.patternData(); var diskB = b.patternData(); load(a, source.get(0), 72); load(b, source.get(1), 72);
                menu = open(player.getInventory(), owner, session); menu.setCarried(source.get(0).copy());
                require(!menu.action(player, session, 1, 0, -1) && ItemStack.matches(menu.getCarried(), source.get(0)), "full buses consumed carried pattern");
                menu.setCarried(ItemStack.EMPTY); a.restorePatternData(diskA); b.restorePatternData(diskB);
            }
            menu = open(player.getInventory(), owner, session); require(menu.query(player, session, new Query("", "", Set.of(), Set.of(), 1)) && menu.snapshot().entries().size() == 2, "search last page");
            if (ae) {
                menu = open(player.getInventory(), owner, session); menu.query(player, session, new Query("oak_log", "", Set.of(), Set.of(), 0)); require(menu.snapshot().total() == 49, "input ID filter");
                menu = open(player.getInventory(), owner, session); menu.query(player, session, new Query("", "本地化名称", Set.of(), Set.of(new ResourceLocation("minecraft:oak_planks")), 0)); require(menu.snapshot().total() == 49, "localized output IDs");
                menu = open(player.getInventory(), owner, session); menu.query(player, session, new Query("oak_log", "gold_ingot", Set.of(), Set.of(), 0)); require(menu.snapshot().total() == 50, "source search union");
            }
            var opaque = new CompoundTag(); opaque.putInt("Version", 999); opaque.putString("Unknown", "retain"); a.restorePatternData(opaque);
            menu = open(player.getInventory(), owner, session); require(!menu.snapshot().readable() && menu.snapshot().total() == 1 && a.patternData().equals(opaque), "opaque inventory erased/shown as readable");
            busMenu.broadcastChanges(); require(!busMenu.readable(), "physical bus menu hid unreadable state");
            var stale = a.menuInventory(); a.invalidateCaps(); a.reviveCaps(); require(stale.getItem(0).isEmpty(), "old menu inventory survived capability generation"); a.restorePatternData(saved);
            menu = open(player.getInventory(), owner, session); var expected = menu.snapshot().token();
            world.setBlockAndUpdate(pos.east(), Blocks.AIR.defaultBlockState()); world.setBlockAndUpdate(pos.east(), EFabricatorRegistration.PATTERN_BUS.get().defaultBlockState()); owner.refreshStructure();
            require(!menu.action(player, session, 0, expected, 0), "replaced bus reference accepted");
            player.setPos(pos.getX() + 20, pos.getY(), pos.getZ()); require(!menu.action(player, session, 1, 0, -1), "remote search action");
            player.setPos(pos.getX() + .5, pos.getY(), pos.getZ() + 2); player.containerMenu = player.inventoryMenu; require(!menu.action(player, session, 0, expected, 0), "closed search action");
            try { new Query("x".repeat(MAX_QUERY + 1), "", Set.of(), Set.of(), 0); throw new AssertionError("unbounded query"); } catch (IllegalArgumentException expectedFailure) { }
            Modularmachinery.LOG.info("[MMC][ef-pattern-menu] PASS: 72 real bus slots/cursor/shift, 50 patterns across two buses and bounded pages, decoded output and input/localized-ID/union filtering when AE is present, codec/private data, stale/replay/session/closed/distance/replaced-bus isolation and unknown-data retention; AE2={}", ae);
        } finally {
            player.containerMenu = player.inventoryMenu;
            for (int i = 0; i <= 2; i++) world.setBlockAndUpdate(pos.east(i), Blocks.AIR.defaultBlockState());
            if (old == null) definitions.remove(id); else definitions.put(id, old);
        }
    }
    private static MenuEFabricatorPatternSearch open(Inventory inv, TileEFabricatorController owner, UUID session) {
        var menu = MenuEFabricatorPatternSearch.forController(4, inv, owner, session); inv.player.containerMenu = menu; menu.broadcastChanges(); return menu;
    }
    public static void load(TileEFabricatorPatternBus bus, ItemStack stack, int count) {
        var tag = new CompoundTag(); tag.putInt("Version", 1); var values = new ListTag();
        for (int i = 0; i < count; i++) { var e = new CompoundTag(); e.putInt("Slot", i); e.put("Item", stack.copyWithCount(1).save(new CompoundTag())); values.add(e); }
        tag.put("Patterns", values); bus.restorePatternData(tag);
    }
    private static void require(boolean value, String why) { if (!value) throw new AssertionError(why); }
}
