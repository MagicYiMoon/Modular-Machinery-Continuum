/* SPDX-License-Identifier: GPL-3.0-only; NECO EFabricatorData, KasumiNova and contributors, b37fbb53. */
package hellfirepvp.modularmachinery.common.efabricator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.*;
import java.util.*;

/** Bounded display summaries only. Neither recipes nor private item/fluid NBT are sent to viewers. */
public final class EFabricatorMonitor {
    public static final int PAGE_SIZE = 12;
    private EFabricatorMonitor() { }
    public record Worker(BlockPos pos, boolean readable, int queued, int capacity, double energy, ItemStack icon) {
        public Worker {
            pos = pos.immutable(); icon = icon.isEmpty() ? ItemStack.EMPTY : new ItemStack(icon.getItem());
            if (queued < 0 || queued > EFabricatorSpecs.MAX_QUEUE || capacity < 1 || capacity > EFabricatorSpecs.MAX_QUEUE
                    || !Double.isFinite(energy) || energy < 0 || energy > EFabricatorSpecs.ENERGY_CACHE) throw new IllegalArgumentException("Invalid EF worker summary");
        }
        @Override public ItemStack icon() { return icon.copy(); }
    }
    private static boolean tier(int n) { return n == 0 || n == 4 || n == 6 || n == 9; }
    public record Snapshot(int flags, int tier, boolean overclock, boolean cooling, int parallelism, int delay,
                           int untilWork, int coolant, long crafted, long queued, long queueCapacity, double energy,
                           long energyCapacity, long pending, long input, long inputCapacity, long output, long outputCapacity,
                           int workerCount, int buses, int processors, int page, int pages, List<Worker> workers) {
        public Snapshot {
            workers = List.copyOf(workers);
            if (!EFabricatorMonitor.tier(tier) || flags < 0 || flags > 31 || parallelism < 0 || delay < 1 || delay > 20 || untilWork < 0 || untilWork > 20
                    || coolant < 0 || coolant >= 200_000 || crafted < 0 || queued < 0 || queueCapacity < 0
                    || !Double.isFinite(energy) || energy < 0 || energyCapacity < 0 || energy > energyCapacity
                    || pending < 0 || input < 0 || inputCapacity < input || output < 0 || outputCapacity < output
                    || workerCount < 0 || buses < 0 || processors < 0 || pages < 1 || page < 0 || page >= pages
                    || workers.size() > PAGE_SIZE) throw new IllegalArgumentException("Invalid EF monitor page");
        }
        public boolean readable() { return (flags & 16) == 0; }
        public static Snapshot empty() { return new Snapshot(16, 0, false, false, 0, 20, 20, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, List.of()); }
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(flags); b.writeVarInt(tier); b.writeBoolean(overclock); b.writeBoolean(cooling);
            for (int n : new int[]{parallelism, delay, untilWork, coolant}) b.writeVarInt(n);
            for (long n : new long[]{crafted, queued, queueCapacity}) b.writeLong(n);
            b.writeDouble(energy);
            for (long n : new long[]{energyCapacity, pending, input, inputCapacity, output, outputCapacity}) b.writeLong(n);
            for (int n : new int[]{workerCount, buses, processors, page, pages, workers.size()}) b.writeVarInt(n);
            for (var w : workers) {
                b.writeBlockPos(w.pos); b.writeBoolean(w.readable); b.writeVarInt(w.queued); b.writeVarInt(w.capacity);
                b.writeDouble(w.energy); b.writeVarInt(Item.getId(w.icon.getItem()));
            }
        }
        public static Snapshot read(FriendlyByteBuf b) {
            int flags = b.readVarInt(), tier = b.readVarInt(); boolean oc = b.readBoolean(), cooling = b.readBoolean();
            int parallel = b.readVarInt(), delay = b.readVarInt(), until = b.readVarInt(), coolant = b.readVarInt();
            long crafted = b.readLong(), queued = b.readLong(), capacity = b.readLong(); double energy = b.readDouble();
            long ec = b.readLong(), pending = b.readLong(), in = b.readLong(), ic = b.readLong(), out = b.readLong(), ocap = b.readLong();
            int wc = b.readVarInt(), buses = b.readVarInt(), processors = b.readVarInt(), page = b.readVarInt(), pages = b.readVarInt(), count = b.readVarInt();
            if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("Oversized EF page");
            var workers = new ArrayList<Worker>();
            for (int i = 0; i < count; i++) workers.add(new Worker(b.readBlockPos(), b.readBoolean(), b.readVarInt(), b.readVarInt(),
                    b.readDouble(), new ItemStack(Item.byId(b.readVarInt()))));
            return new Snapshot(flags, tier, oc, cooling, parallel, delay, until, coolant, crafted, queued, capacity, energy, ec, pending,
                    in, ic, out, ocap, wc, buses, processors, page, pages, workers);
        }
    }
    public static long add(long a, long b) { return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b; }
}
