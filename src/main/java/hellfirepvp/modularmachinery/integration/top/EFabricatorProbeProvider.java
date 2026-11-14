/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NECO EFabricatorInfoProvider, KasumiNova and contributors, b37fbb53.
 * TOP 1.20.1; bounded, NBT-free work-entry illustrations and explicit unknown data.
 */
package hellfirepvp.modularmachinery.integration.top;

import hellfirepvp.modularmachinery.common.efabricator.*;
import mcjty.theoneprobe.api.*;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Locale;

/** Source provider only probes workers. TOP transports its own elements; no AE backend is created. */
public final class EFabricatorProbeProvider implements IProbeInfoProvider {
    @Override public ResourceLocation getID() { return new ResourceLocation("modularmachinery", "efabricator_info_provider"); }
    @Override public void addProbeInfo(ProbeMode mode, IProbeInfo info, Player player, Level level, BlockState state, IProbeHitData hit) {
        if (!(level instanceof ServerLevel server)) return;
        var pos = hit.getPos(); var chunk = server.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk != null && chunk.getBlockEntity(pos) instanceof TileEFabricatorWorker worker) worker(info, worker, mode);
    }
    private static net.minecraft.network.chat.MutableComponent tr(String key) { return Component.translatable("top.modularmachinery.ef." + key); }
    private static IProbeInfo box(IProbeInfo info) { return info.vertical(info.defaultLayoutStyle().borderColor(0x801E90FF).spacing(0)); }
    private static IProbeInfo row(IProbeInfo info) { return info.horizontal(info.defaultLayoutStyle().alignment(ElementAlignment.ALIGN_CENTER).spacing(3)); }
    private static String energy(double value, ProbeMode mode) {
        if (mode != ProbeMode.EXTENDED && value >= 1000) return String.format(Locale.ROOT, "%.1fk", value / 1000);
        return String.format(Locale.ROOT, value == Math.rint(value) ? "%,.0f" : "%,.2f", value);
    }
    static int queueWidth(int capacity) {
        // Source's diminishing-width queue bar, with a readable minimum for the 32-entry tier.
        float multiplier = 1; int length = 0, remaining = Math.max(0, Math.min(EFabricatorSpecs.MAX_QUEUE, capacity));
        while (remaining > 0) {
            int append = (int)(Math.min(remaining, 32) * multiplier);
            if (append < 1) break;
            length += append; multiplier *= .8F; remaining -= append;
        }
        return Math.max(80, Math.min(160, length));
    }
    private static int shade(int color, double factor) {
        int result = color & 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) result |= Math.min(255, (int)((color >> shift & 255) * factor)) << shift;
        return result;
    }
    private static void worker(IProbeInfo info, TileEFabricatorWorker worker, ProbeMode mode) {
        // Source online means a formed owner, independently of ME power/channel availability.
        boolean bound = worker.isBoundPart();
        row(box(info)).mcText(tr("status").withStyle(ChatFormatting.BLUE))
                .mcText(tr(bound ? "online" : "offline").withStyle(bound ? ChatFormatting.GREEN : ChatFormatting.RED));
        if (!net.minecraftforge.fml.ModList.get().isLoaded("ae2")) box(info).mcText(tr("no_ae").withStyle(ChatFormatting.YELLOW));
        if (!worker.readable()) { box(info).mcText(tr("unreadable").withStyle(ChatFormatting.RED)); return; }
        var bars = box(info);
        int energyWidth = mode == ProbeMode.EXTENDED ? 180 : 140;
        row(bars).progress((int)Math.round(worker.energyStored() / EFabricatorSpecs.ENERGY_CACHE * 80), 80,
                bars.defaultProgressStyle().prefix(Component.literal(energy(worker.energyStored(), mode) + " AE / "
                        + energy(EFabricatorSpecs.ENERGY_CACHE, mode) + " AE"))
                        .numberFormat(NumberFormat.NONE).width(energyWidth).filledColor(0xCC42B8FF)
                        .alternateFilledColor(0xCC42BDFF).borderColor(0xCC97FFFF).backgroundColor(0xFF000000));
        int used = worker.queuedCrafts(), capacity = worker.queueDepth();
        int color = ECalculatorProbeProvider.color(used, capacity);
        row(bars).mcText(tr("queue").withStyle(ChatFormatting.BLUE)).progress(Math.min(used, capacity), capacity,
                bars.defaultProgressStyle().prefix(Component.literal(used + " / " + capacity))
                        .numberFormat(NumberFormat.NONE).width(queueWidth(capacity)).filledColor(color)
                        .alternateFilledColor(shade(color, .8)).borderColor(shade(color, 1.25)).backgroundColor(0xFF000000));
        var icons = worker.productIcons();
        if (icons.isEmpty()) return;
        row(box(info)).mcText(tr("current").withStyle(ChatFormatting.BLUE)).item(icons.get(0));
        if (icons.size() == 1) return;
        var queued = box(info); queued.mcText(tr("enqueued").withStyle(ChatFormatting.BLUE));
        IProbeInfo line = null;
        for (int i = 1; i < icons.size(); i++) {
            if ((i - 1) % 8 == 0) line = row(queued);
            line.item(icons.get(i));
        }
    }
}
