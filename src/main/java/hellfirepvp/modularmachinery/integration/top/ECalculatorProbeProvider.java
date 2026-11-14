/* SPDX-License-Identifier: GPL-3.0-only
 * NECO ECalculatorInfoProvider, KasumiNova and contributors, b37fbb53.
 * Adapted to TOP 1.20.1, bounded task rows and the actual AE2 15 runtime measurements.
 */
package hellfirepvp.modularmachinery.integration.top;

import hellfirepvp.modularmachinery.common.ecalculator.*;
import mcjty.theoneprobe.api.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** TOP owns the transport. No AE classes, inventory NBT, fake menus or persistent statistics. */
public final class ECalculatorProbeProvider implements IProbeInfoProvider {
    public static final int MAX_TASKS = 4;
    @Override public ResourceLocation getID() { return new ResourceLocation("modularmachinery", "ecalculator_info_provider"); }
    @Override public void addProbeInfo(ProbeMode mode, IProbeInfo info, Player player, Level level, BlockState state, IProbeHitData hit) {
        if (!(level instanceof ServerLevel server)) return;
        var pos = hit.getPos(); var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return;
        var tile = chunk.getBlockEntity(pos);
        if (tile instanceof TileECalculatorController controller) controller(info, controller, mode);
        else if (tile instanceof TileECalculatorPart core && core.specification().normalSlots() + core.specification().hyperSlots() > 0) core(info, core, mode);
    }
    private static net.minecraft.network.chat.MutableComponent tr(String key, Object... args) { return Component.translatable("top.modularmachinery.ec." + key, args); }
    private static IProbeInfo box(IProbeInfo info) { return info.vertical(info.defaultLayoutStyle().borderColor(0xFF757575).spacing(2)); }
    private static IProbeInfo row(IProbeInfo info) { return info.horizontal(info.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER).spacing(3)); }
    private static String number(long value, ProbeMode mode) {
        if (mode == ProbeMode.EXTENDED || value < 1000) return String.format(Locale.ROOT, "%,d", value);
        double amount = value; String[] suffix = {"", "k", "M", "G", "T", "P", "E"}; int i = 0;
        while (amount >= 1000 && i < suffix.length - 1) { amount /= 1000; i++; }
        return String.format(Locale.ROOT, "%.1f%s", amount, suffix[i]);
    }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value); }
    static ChatFormatting timeColor(long nanos, boolean global) {
        long unit = global ? 2_000_000 : 400_000;
        return nanos < unit ? ChatFormatting.GREEN : nanos < unit * 2 ? ChatFormatting.YELLOW : nanos < unit * 3 ? ChatFormatting.RED : ChatFormatting.DARK_RED;
    }
    static int fill(long used, long capacity, int width) { return capacity <= 0 ? 0 : (int)Math.min(width, Math.max(0, (double)used / capacity * width)); }
    static int color(int value, int maximum) {
        // The source uses low,low,low,mid,mid,full,full with alpha CC.
        int[] colors = {0x54FF9F, 0x54FF9F, 0x54FF9F, 0xFFFF00, 0xFFFF00, 0xFF4500, 0xFF4500};
        double segment = Math.max(0, Math.min(1, maximum <= 0 ? 0 : (double)value / maximum)) * 6;
        int i = Math.min(5, (int)segment); double fraction = segment - i;
        int result = 0xCC000000;
        for (int shift : new int[]{16, 8, 0}) { int a = colors[i] >> shift & 255, b = colors[i + 1] >> shift & 255; result |= (int)(a + (b - a) * fraction) << shift; }
        return result;
    }
    private static int shade(int color, double factor) {
        int result = color & 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) result |= Math.min(255, (int)((color >> shift & 255) * factor)) << shift;
        return result;
    }
    private static void progress(IProbeInfo parent, Component label, Component prefix, long used, long maximum, int width) {
        var info = row(box(parent)); info.mcText(label.copy().withStyle(ChatFormatting.AQUA));
        int amount = fill(used, maximum, width), color = color(amount, width);
        info.progress(amount, width, info.defaultProgressStyle().prefix(prefix).numberFormat(NumberFormat.NONE)
                .width(width).filledColor(color).alternateFilledColor(shade(color, .8)).borderColor(shade(color, 1.2)).backgroundColor(0xFF000000));
    }
    private record Usage(boolean readable, long normal, long hyper, long bytes, boolean measured, long nanos, double operations) { }
    private static Usage usage(boolean readable, List<ECalculatorMonitor.Task> tasks, Map<UUID, ECalculatorStatistics.Measurement> statistics) {
        long normal = 0, hyper = 0, bytes = 0, nanos = 0; double operations = 0; boolean measured = readable;
        for (var task : tasks) {
            if (task.hyper()) hyper++; else normal++;
            bytes = ECalculatorMonitor.add(bytes, task.bytes());
            var m = statistics.getOrDefault(task.id(), ECalculatorStatistics.Measurement.UNKNOWN);
            measured &= m.ready(); nanos = ECalculatorMonitor.add(nanos, m.nanosPerTick()); operations += m.operationsPerTick();
        }
        return new Usage(readable, normal, hyper, bytes, measured, nanos, operations);
    }
    private static Component threads(long used, long normal, long hyper, ProbeMode mode) {
        return Component.literal(number(used, mode) + " / " + number(normal, mode) + " ")
                .append(Component.literal("(+" + number(hyper, mode) + ")").withStyle(ChatFormatting.YELLOW));
    }
    private static void measurements(IProbeInfo info, boolean measured, long nanos, double operations, boolean global) {
        info.mcText(tr("cpu_time", measured ? Component.literal(decimal(nanos / 1000.0)).withStyle(timeColor(nanos, global)) : Component.literal("?")));
        info.mcText(tr("operations", measured ? Component.literal(decimal(operations)).withStyle(ChatFormatting.DARK_PURPLE) : Component.literal("?")));
    }
    private static void controller(IProbeInfo info, TileECalculatorController controller, ProbeMode mode) {
        if (!controller.isAssembled() || !controller.isFormed() || controller.isRemoved()) return;
        long capacity = 0, parallel = 0, normal = 0, hyper = 0; boolean readable = net.minecraftforge.fml.ModList.get().isLoaded("ae2"), online = false;
        var tasks = new ArrayList<ECalculatorMonitor.Task>(); var statistics = new HashMap<UUID, ECalculatorStatistics.Measurement>();
        for (var part : controller.parts()) {
            if (!part.isBoundPart() || part.owner() != controller) continue;
            var spec = part.specification(); normal += spec.normalSlots(); hyper += spec.hyperSlots(); parallel = ECalculatorMonitor.add(parallel, spec.parallelism());
            if (part instanceof TileECalculatorDrive drive && drive.isTierSupported() && drive.memorySnapshot().getItem() instanceof ItemECalculatorMemory cell)
                capacity = ECalculatorMonitor.add(capacity, cell.specification().bytes());
            if (part instanceof TileECalculatorMEChannel channel) online |= channel.isNetworkActive();
            if (spec.normalSlots() + spec.hyperSlots() == 0) continue;
            var access = part.cpuAccess(); readable &= access != null && access.monitorReadable();
            if (access != null) { tasks.addAll(access.monitorTasks()); statistics.putAll(access.monitorStatistics()); }
        }
        var usage = usage(readable, tasks, statistics);
        box(info).mcText(tr(!net.minecraftforge.fml.ModList.get().isLoaded("ae2") ? "no_ae" : !controller.isOperational() ? "stopped" : online ? "online" : "offline"));
        if (usage.readable) {
            progress(info, tr("memory"), Component.literal(number(usage.bytes, mode) + " / " + number(capacity, mode) + " B"), usage.bytes, capacity, 150);
            progress(info, tr("threads"), threads(usage.normal + usage.hyper, normal, hyper, mode), usage.normal + usage.hyper, normal + hyper, 100);
        } else {
            box(info).mcText(tr("memory_unknown", number(capacity, mode))).mcText(tr("threads_unknown", number(normal, mode), number(hyper, mode)));
        }
        var details = box(info); details.mcText(tr("parallel", number(parallel, mode)));
        measurements(details, usage.measured, usage.nanos, usage.operations, true);
    }
    private static void core(IProbeInfo info, TileECalculatorPart core, ProbeMode mode) {
        boolean bound = core.isBoundPart();
        box(info).mcText(tr(bound ? "bound" : "unbound").withStyle(bound ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (!bound) return;
        var access = core.cpuAccess(); boolean readable = access != null && access.monitorReadable();
        var tasks = access == null ? List.<ECalculatorMonitor.Task>of() : access.monitorTasks();
        var statistics = access == null ? Map.<UUID, ECalculatorStatistics.Measurement>of() : access.monitorStatistics();
        var usage = usage(readable, tasks, statistics); var spec = core.specification();
        if (readable) progress(info, tr("threads"), threads(usage.normal + usage.hyper, spec.normalSlots(), spec.hyperSlots(), mode),
                usage.normal + usage.hyper, spec.normalSlots() + spec.hyperSlots(), 75);
        else { box(info).mcText(tr("unreadable")); return; }
        taskRows(info, tasks, statistics, mode);
    }
    static void taskRows(IProbeInfo info, List<ECalculatorMonitor.Task> tasks,
                         Map<UUID, ECalculatorStatistics.Measurement> statistics, ProbeMode mode) {
        var ordered = tasks.stream().sorted(Comparator.comparing(ECalculatorMonitor.Task::id)).toList();
        for (var task : ordered.stream().limit(MAX_TASKS).toList()) {
            var taskBox = box(info); var line = row(taskBox);
            if (!task.icon().isEmpty()) line.item(task.icon(), line.defaultItemStyle().width(16).height(16)).itemLabel(task.icon()); else line.mcText(task.name());
            if (task.running()) line.mcText(Component.literal("× " + number(task.amount(), mode)));
            line.mcText(tr(!task.running() ? "returning" : !task.online() ? "paused" : task.hyper() ? "hyper" : "normal"));
            var m = statistics.getOrDefault(task.id(), ECalculatorStatistics.Measurement.UNKNOWN);
            // One statistics row per task keeps a four-task overlay within a small screen.
            row(taskBox).mcText(tr("cpu_time", m.ready() ? Component.literal(decimal(m.nanosPerTick() / 1000.0)).withStyle(timeColor(m.nanosPerTick(), false)) : Component.literal("?")))
                    .mcText(tr("operations", m.ready() ? Component.literal(decimal(m.operationsPerTick())).withStyle(ChatFormatting.DARK_PURPLE) : Component.literal("?")));
        }
        if (tasks.size() > MAX_TASKS) box(info).mcText(tr("more", tasks.size() - MAX_TASKS));
    }
}
