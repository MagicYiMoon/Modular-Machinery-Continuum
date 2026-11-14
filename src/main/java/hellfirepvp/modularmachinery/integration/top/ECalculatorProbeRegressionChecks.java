/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.top;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import io.netty.buffer.Unpooled;
import mcjty.theoneprobe.TheOneProbe;
import mcjty.theoneprobe.api.*;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import mcjty.theoneprobe.apiimpl.elements.*;
import net.minecraft.ChatFormatting;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Opt-in self-test call only: real registered TOP providers, world ownership and native wire codec. */
public final class ECalculatorProbeRegressionChecks {
    private ECalculatorProbeRegressionChecks() { }
    public static void run(ServerLevel level, BlockPos pos, Player player) {
        var providers = TheOneProbe.theOneProbeImp.getProviders().stream()
                .filter(p -> p.getID().equals(new net.minecraft.resources.ResourceLocation("modularmachinery", "ecalculator_info_provider"))).toList();
        require(providers.size() == 1, "EC provider absent or registered twice");
        var provider = providers.get(0);
        var owner = (TileECalculatorController)level.getBlockEntity(pos);
        var core = (TileECalculatorPart)level.getBlockEntity(pos.east());
        boolean ae = net.minecraftforge.fml.ModList.get().isLoaded("ae2");
        var host = probe(provider, level, pos, player, ProbeMode.EXTENDED);
        require(hasKey(host, !ae ? "no_ae" : owner.isOperational() ? "offline" : "stopped") && hasKey(host, "parallel"), "host status or parallel missing");
        var bars = flatten(host).stream().filter(e -> e instanceof ElementProgress).map(e -> (ElementProgress)e).toList();
        require(ae ? bars.size() == 2 && bars.get(0).getStyle().getPrefix().contains("16,777,216,000")
                : bars.isEmpty() && hasKey(host, "memory_unknown"), "long physical capacity or unknown backend");
        var ordinary = probe(new MachineProbeProvider(), level, pos, player, ProbeMode.NORMAL);
        require(ordinary.getElements().isEmpty(), "generic provider duplicated EC status");
        var bound = probe(provider, level, pos.east(), player, ProbeMode.NORMAL);
        require(hasKey(bound, "bound") && (ae || hasKey(bound, "unreadable")), "bound/unreadable core status");
        roundTrip(host); roundTrip(bound);
        core.unbind(owner);
        require(hasKey(probe(provider, level, pos.east(), player, ProbeMode.NORMAL), "unbound"), "unbound core exposed tasks");
        core.bind(owner);
        if (ae) {
            var original = core.backendSnapshot();
            var opaque = new net.minecraft.nbt.CompoundTag(); opaque.putString("AE2Crafting", "unknown-format");
            try {
                core.restoreBackend(opaque);
                require(hasKey(probe(provider, level, pos.east(), player, ProbeMode.NORMAL), "unreadable")
                        && hasKey(probe(provider, level, pos, player, ProbeMode.NORMAL), "memory_unknown")
                        && core.backendSnapshot().equals(opaque), "opaque tasks shown as empty or modified");
            } finally { core.restoreBackend(original); }
        }
        // Edge datasets exercise presentation only, never create fake AE CPUs or statistics in the world.
        var tasks = new ArrayList<ECalculatorMonitor.Task>();
        var stats = new HashMap<UUID, ECalculatorStatistics.Measurement>();
        for (int i = 0; i < 6; i++) {
            var stack = new ItemStack(Items.DIAMOND); stack.getOrCreateTag().putString("PrivateInventory", "must-not-leak");
            var id = new UUID(0, i);
            tasks.add(new ECalculatorMonitor.Task(id, Component.translatable("item.minecraft.diamond"), stack,
                    Long.MAX_VALUE, 7_777_777_777L, i == 3, i != 2, i != 1));
            if (i != 0) stats.put(id, new ECalculatorStatistics.Measurement(8, 7_777_777_777L, .125));
        }
        Collections.reverse(tasks);
        var rows = new ProbeInfo(); ECalculatorProbeProvider.taskRows(rows, tasks, stats, ProbeMode.EXTENDED);
        require(rows.getElements().size() == 5 && hasKey(rows, "more") && hasKey(rows, "normal")
                && hasKey(rows, "hyper") && hasKey(rows, "paused") && hasKey(rows, "returning"), "bounded/stable task rows or states");
        var elements = flatten(rows);
        require(elements.stream().filter(e -> e instanceof ElementItemStack).count() == 4, "unbounded task icons");
        require(elements.stream().filter(e -> e instanceof ElementText).anyMatch(e -> text(e).getString().contains("9,223,372,036,854,775,807")), "64-bit request quantity truncated");
        require(elements.stream().filter(e -> e instanceof ElementText).anyMatch(e -> text(e).getString().contains("?")), "unsampled statistic reported measured");
        var wire = roundTrip(rows);
        require(wire.stream().noneMatch(b -> b.contains("PrivateInventory") || b.contains("must-not-leak")), "private item NBT leaked");
        require(ECalculatorProbeProvider.fill(1, 0, 150) == 0 && ECalculatorProbeProvider.fill(Long.MAX_VALUE, 1, 150) == 150
                && ECalculatorProbeProvider.fill(-1, 100, 150) == 0, "zero capacity or overflow fill");
        require(ECalculatorProbeProvider.color(0, 100) == 0xCC54FF9F && ECalculatorProbeProvider.color(100, 100) == 0xCCFF4500
                && ECalculatorProbeProvider.color(50, 100) == 0xCCFFFF00, "source gradient");
        require(ECalculatorProbeProvider.timeColor(399_999, false) == ChatFormatting.GREEN
                && ECalculatorProbeProvider.timeColor(400_000, false) == ChatFormatting.YELLOW
                && ECalculatorProbeProvider.timeColor(800_000, false) == ChatFormatting.RED
                && ECalculatorProbeProvider.timeColor(1_200_000, false) == ChatFormatting.DARK_RED
                && ECalculatorProbeProvider.timeColor(2_000_000, true) == ChatFormatting.YELLOW, "source CPU thresholds");
        owner.setFormed(false);
        require(probe(provider, level, pos, player, ProbeMode.NORMAL).getElements().isEmpty()
                && hasKey(probe(provider, level, pos.east(), player, ProbeMode.NORMAL), "unbound"), "broken structure retained TOP usage");
        owner.refreshStructure();
        Modularmachinery.LOG.info("[MMC][ec-top] PASS: actual IMC provider, formed host/core ownership, physical long capacity, unknown backend preservation, no generic duplicate, four-task bound/states, native TOP wire round trip, private NBT exclusion and source colors; AE2 present={}", ae);
    }
    private static ProbeInfo probe(IProbeInfoProvider provider, ServerLevel level, BlockPos pos, Player player, ProbeMode mode) {
        var info = new ProbeInfo();
        provider.addProbeInfo(mode, info, player, level, level.getBlockState(pos), new IProbeHitData() {
            public BlockPos getPos() { return pos; }
            public Vec3 getHitVec() { return Vec3.atCenterOf(pos); }
            public Direction getSideHit() { return Direction.NORTH; }
            public ItemStack getPickBlock() { return ItemStack.EMPTY; }
        });
        return info;
    }
    private static List<IElement> flatten(IProbeInfo info) {
        var out = new ArrayList<IElement>();
        for (var element : info.getElements()) { out.add(element); if (element instanceof IProbeInfo children) out.addAll(flatten(children)); }
        return out;
    }
    private static Object field(Object object, String name) {
        try { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static Component text(IElement element) { return (Component)field(element, "text"); }
    private static boolean hasKey(IProbeInfo info, String suffix) {
        return flatten(info).stream().filter(e -> e instanceof ElementText).map(ECalculatorProbeRegressionChecks::text)
                .anyMatch(c -> c.getContents() instanceof TranslatableContents t && t.getKey().equals("top.modularmachinery.ec." + suffix));
    }
    private static List<String> roundTrip(ProbeInfo info) {
        var buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ProbeInfo.writeElements(info.getElements(), buffer);
            var bytes = new byte[buffer.readableBytes()]; buffer.getBytes(buffer.readerIndex(), bytes);
            var decoded = new ProbeInfo(); decoded.fromBytes(buffer);
            require(buffer.readableBytes() == 0 && flatten(decoded).size() == flatten(info).size(), "TOP codec lost elements");
            var expected = flatten(info).stream().filter(e -> e instanceof ElementText).map(ECalculatorProbeRegressionChecks::text).toList();
            var actual = flatten(decoded).stream().filter(e -> e instanceof ElementText).toList();
            for (int i = 0; i < actual.size(); i++) {
                require(!((Boolean)field(actual.get(i), "legacy")), "legacy TOP renderer discards EC component colors");
                require(Component.Serializer.toJson(text(actual.get(i))).equals(Component.Serializer.toJson(expected.get(i))),
                        "TOP codec lost translated arguments or nested statistic colors");
            }
            return List.of(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        } finally { buffer.release(); }
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
