/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.top;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.*;
import io.netty.buffer.Unpooled;
import mcjty.theoneprobe.TheOneProbe;
import mcjty.theoneprobe.api.*;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import mcjty.theoneprobe.apiimpl.elements.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Called only from the disposable-world self-test, using the actual registered provider and codec. */
public final class EFabricatorProbeRegressionChecks {
    private EFabricatorProbeRegressionChecks() { }
    /** Read-only assertion for the separate native-AE-order fixture; never seeds worker data. */
    public static void observe(ServerLevel world, TileEFabricatorWorker worker, boolean queued) {
        var provider = TheOneProbe.theOneProbeImp.getProviders().stream()
                .filter(p -> p.getID().equals(new ResourceLocation("modularmachinery", "efabricator_info_provider"))).findFirst().orElseThrow();
        var before = worker.workData();
        var info = probe(provider, world, worker.getBlockPos(), null, ProbeMode.EXTENDED);
        require(worker.readable() && hasKey(info, "online") && hasKey(info, "current") == queued
                && (worker.queuedCrafts() > 0) == queued, "actual AE queue not reflected in TOP");
        var bars = bars(info);
        require(bars.size() == 2 && bars.get(1).getStyle().getPrefix().equals(worker.queuedCrafts() + " / " + worker.queueDepth())
                && bars.get(0).getStyle().getPrefix().startsWith(String.format(Locale.ROOT, "%,.0f AE", worker.energyStored())), "actual AE energy/count mismatch");
        roundTrip(info); require(worker.workData().equals(before), "probing actual AE work changed data");
    }
    public static void run(ServerLevel world, TileEFabricatorWorker worker, Player player) {
        var providers = TheOneProbe.theOneProbeImp.getProviders().stream()
                .filter(p -> p.getID().equals(new ResourceLocation("modularmachinery", "efabricator_info_provider"))).toList();
        require(providers.size() == 1, "EF provider absent or registered twice");
        var provider = providers.get(0); var owner = worker.owner(); var saved = worker.workData();
        boolean oc = owner.isOverclocked(), cooling = owner.isActiveCooling();
        try {
            owner.setModes(true, false);
            var q = new EFabricatorQueue();
            for (int i = 0; i < 40; i++) {
                var stack = new ItemStack(i % 2 == 0 ? Items.DIAMOND : Items.APPLE, 4);
                stack.getOrCreateTag().putString("PrivateInventory", "must-not-leak");
                q.offer(new EFabricatorQueue.Work(i == 0 ? 2 : 1, stack, List.of(new ItemStack(Items.BUCKET))), 512);
            }
            var data = new CompoundTag(); data.putInt("Version", 1); data.putDouble("Energy", 250000.5); data.put("Queue", q.save());
            worker.restoreWorkData(data);
            var before = worker.workData(); var icons = worker.productIcons();
            require(icons.size() == 33 && icons.get(0).is(Items.DIAMOND) && icons.get(1).is(Items.APPLE), "FIFO entry bound or craft expansion");
            require(icons.stream().allMatch(s -> s.getCount() == 1 && !s.hasTag()), "private stack data escaped summary");
            icons.get(0).setCount(0); require(worker.productIcons().get(0).is(Items.DIAMOND), "mutable summary changed work");
            var normal = probe(provider, world, worker.getBlockPos(), player, ProbeMode.NORMAL);
            var extended = probe(provider, world, worker.getBlockPos(), player, ProbeMode.EXTENDED);
            require(hasKey(normal, "online") && hasKey(normal, "queue") && hasKey(normal, "current") && hasKey(normal, "enqueued"), "source worker fields missing");
            require(hasKey(normal, "no_ae") != net.minecraftforge.fml.ModList.get().isLoaded("ae2"), "missing AE status");
            require(flatten(normal).stream().filter(e -> e instanceof ElementItemStack).count() == 33, "unbounded TOP product icons");
            var bars = bars(extended);
            require(bars.size() == 2 && bars.get(0).getStyle().getPrefix().contains("250,000.50 AE")
                    && bars.get(1).getStyle().getPrefix().equals("41 / 512"), "fractional AE, source units or queued crafts lost");
            roundTrip(normal); roundTrip(extended);
            require(worker.workData().equals(before), "probe modified authoritative queue or energy");
            owner.setModes(false, false);
            require(bars(probe(provider, world, worker.getBlockPos(), player, ProbeMode.NORMAL)).get(1).getStyle().getPrefix().equals("41 / 32"), "downclock concealed retained work");
            worker.unbind(owner);
            var offline = probe(provider, world, worker.getBlockPos(), player, ProbeMode.NORMAL);
            require(hasKey(offline, "offline") && hasKey(offline, "current"), "unbound retained work hidden or online");
            worker.bind(owner);
            var future = new CompoundTag(); future.putInt("Version", 999); future.putString("PrivateInventory", "must-not-leak");
            worker.restoreWorkData(future);
            var unknown = probe(provider, world, worker.getBlockPos(), player, ProbeMode.NORMAL);
            require(hasKey(unknown, "unreadable") && bars(unknown).isEmpty() && worker.productIcons().isEmpty()
                    && worker.workData().equals(future), "unknown data reported empty or modified"); roundTrip(unknown);
            worker.restoreWorkData(new CompoundTag());
            var empty = probe(provider, world, worker.getBlockPos(), player, ProbeMode.NORMAL);
            require(bars(empty).size() == 2 && !hasKey(empty, "current") && !hasKey(empty, "enqueued"), "empty queue produced stale icons");
            for (var part : owner.parts()) if (!(part instanceof TileEFabricatorWorker))
                require(probe(provider, world, part.getBlockPos(), player, ProbeMode.NORMAL).getElements().isEmpty(), "nonworker source scope expanded");
            require(probe(provider, world, owner.getBlockPos(), player, ProbeMode.NORMAL).getElements().isEmpty()
                    && probe(new MachineProbeProvider(), world, owner.getBlockPos(), player, ProbeMode.NORMAL).getElements().isEmpty(), "host acquired misleading generic status");
            var absent = new BlockPos(20_000_000, 120, 20_000_000);
            require(world.getChunkSource().getChunkNow(absent.getX() >> 4, absent.getZ() >> 4) == null, "far test chunk unexpectedly loaded");
            require(probe(provider, world, absent, player, ProbeMode.NORMAL).getElements().isEmpty()
                    && world.getChunkSource().getChunkNow(absent.getX() >> 4, absent.getZ() >> 4) == null, "probe loaded absent chunk");
            require(EFabricatorProbeProvider.queueWidth(32) >= 80 && EFabricatorProbeProvider.queueWidth(512) <= 160, "unreadable/unbounded queue bar");
            Modularmachinery.LOG.info("[MMC][ef-top] PASS: actual IMC provider, native TOP codec, source worker-only status/AE/queue/FIFO entries, 33 NBT-free icon bound, fractional cache, downclock retention, offline saved work, unknown data preservation and no chunk loads; AE2 present={}", net.minecraftforge.fml.ModList.get().isLoaded("ae2"));
        } finally { worker.restoreWorkData(saved); owner.setModes(oc, cooling); owner.refreshStructure(); }
    }
    private static ProbeInfo probe(IProbeInfoProvider provider, ServerLevel world, BlockPos pos, Player player, ProbeMode mode) {
        var info = new ProbeInfo();
        // Do not let the test's own state lookup load a chunk before the provider is queried.
        var chunk = world.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        provider.addProbeInfo(mode, info, player, world, chunk == null ? net.minecraft.world.level.block.Blocks.AIR.defaultBlockState() : chunk.getBlockState(pos), new IProbeHitData() {
            public BlockPos getPos() { return pos; }
            public Vec3 getHitVec() { return Vec3.atCenterOf(pos); }
            public Direction getSideHit() { return Direction.NORTH; }
            public ItemStack getPickBlock() { return ItemStack.EMPTY; }
        });
        return info;
    }
    private static List<IElement> flatten(IProbeInfo info) {
        var out = new ArrayList<IElement>();
        for (var element : info.getElements()) { out.add(element); if (element instanceof IProbeInfo nested) out.addAll(flatten(nested)); }
        return out;
    }
    private static List<ElementProgress> bars(IProbeInfo info) { return flatten(info).stream().filter(e -> e instanceof ElementProgress).map(e -> (ElementProgress)e).toList(); }
    private static boolean hasKey(IProbeInfo info, String suffix) {
        for (var element : flatten(info)) if (element instanceof ElementText) try {
            var field = element.getClass().getDeclaredField("text"); field.setAccessible(true);
            if (((Component)field.get(element)).getContents() instanceof TranslatableContents t && t.getKey().equals("top.modularmachinery.ef." + suffix)) return true;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        return false;
    }
    private static void roundTrip(ProbeInfo info) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ProbeInfo.writeElements(info.getElements(), buffer);
            require(buffer.readableBytes() < 10_000, "unbounded TOP reply");
            var bytes = new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(), bytes);
            var wire = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            require(!wire.contains("PrivateInventory") && !wire.contains("must-not-leak") && !wire.contains("Remaining"), "private data leaked into TOP packet");
            var decoded = new ProbeInfo(); decoded.fromBytes(buffer);
            require(buffer.readableBytes() == 0 && flatten(decoded).size() == flatten(info).size(), "TOP codec lost elements");
            for (var element : flatten(decoded)) if (element instanceof ElementText) try {
                var legacy = element.getClass().getDeclaredField("legacy"); legacy.setAccessible(true);
                require(!legacy.getBoolean(element), "legacy TOP renderer discards Component colors");
                var field = element.getClass().getDeclaredField("text"); field.setAccessible(true); var text = (Component)field.get(element);
                if (text.getContents() instanceof TranslatableContents t && t.getKey().equals("top.modularmachinery.ef.online"))
                    require(text.getStyle().getColor() != null && text.getStyle().getColor().getValue() == net.minecraft.ChatFormatting.GREEN.getColor(), "source online color lost in transport");
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        } finally { buffer.release(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
