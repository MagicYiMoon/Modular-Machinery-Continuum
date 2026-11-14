/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.ecalculator;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Bounded, display-only data. No AE classes, crafting inventories or saved task NBT cross this boundary. */
public final class ECalculatorMonitor {
    public static final int CORE_PAGE_SIZE = 12, TASK_PAGE_SIZE = 4, MAX_SEGMENTS = 16;
    private static final int MAX_NAME_JSON = 2048;
    private ECalculatorMonitor() { }
    public record Task(UUID id, Component name, ItemStack icon, long amount, long bytes, boolean hyper,
                       boolean running, boolean online) {
        public Task {
            Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(icon);
            // Keep translation keys for dedicated servers: resolving to a literal here would force the server's language.
            if (Component.Serializer.toJson(name).length() > MAX_NAME_JSON) {
                String label = name.getString(); name = Component.literal(label.substring(0, Math.min(256, label.length())));
            } else name = name.copy();
            icon = icon.isEmpty() ? ItemStack.EMPTY : new ItemStack(icon.getItem());
            // The icon is an illustration, never an inventory slot or a way of sending private item data.
            if (amount < 0 || bytes < 0) throw new IllegalArgumentException("negative EC task data");
        }
    }
    public record Core(BlockPos pos, int tier, int normal, int hyper, int maxNormal, int maxHyper, boolean readable) {
        public Core {
            Objects.requireNonNull(pos); pos = pos.immutable();
            if ((tier != 4 && tier != 6 && tier != 9) || normal < 0 || hyper < 0 || maxNormal < normal || maxHyper < hyper
                    || maxNormal > 4 || maxHyper > 8) throw new IllegalArgumentException("invalid EC core summary");
        }
    }
    public record Snapshot(int flags, int tier, long capacity, long used, long hyperMemory, long parallelism,
                           long normal, long hyper, long maxNormal, long maxHyper,
                           int corePage, int corePages, int taskPage, int taskPages, int taskCount,
                           List<Long> segments, List<Core> cores, List<Task> tasks) {
        public Snapshot {
            segments = List.copyOf(segments); cores = List.copyOf(cores); tasks = List.copyOf(tasks);
            if (capacity < 0 || used < 0 || hyperMemory < 0 || parallelism < 0 || normal < 0 || hyper < 0
                    || maxNormal < 0 || maxHyper < 0 || corePages < 1 || taskPages < 1
                    || corePage < 0 || corePage >= corePages || taskPage < 0 || taskPage >= taskPages || taskCount < 0
                    || segments.size() > MAX_SEGMENTS || cores.size() > CORE_PAGE_SIZE || tasks.size() > TASK_PAGE_SIZE
                    || segments.stream().anyMatch(v -> v < 0)) throw new IllegalArgumentException("invalid EC monitor page");
        }
        public static Snapshot empty() { return new Snapshot(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 1, 0, List.of(), List.of(), List.of()); }
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(flags); b.writeVarInt(tier);
            for (long v : new long[]{capacity, used, hyperMemory, parallelism, normal, hyper, maxNormal, maxHyper}) b.writeLong(v);
            for (int v : new int[]{corePage, corePages, taskPage, taskPages, taskCount}) b.writeVarInt(v);
            b.writeVarInt(segments.size()); for (long v : segments) b.writeLong(v);
            b.writeVarInt(cores.size()); for (var c : cores) {
                b.writeBlockPos(c.pos); b.writeVarInt(c.tier); b.writeVarInt(c.normal); b.writeVarInt(c.hyper);
                b.writeVarInt(c.maxNormal); b.writeVarInt(c.maxHyper); b.writeBoolean(c.readable);
            }
            b.writeVarInt(tasks.size()); for (var t : tasks) {
                b.writeUUID(t.id); b.writeUtf(Component.Serializer.toJson(t.name), MAX_NAME_JSON);
                b.writeVarInt(net.minecraft.world.item.Item.getId(t.icon.getItem())); b.writeLong(t.amount);
                b.writeLong(t.bytes); b.writeBoolean(t.hyper); b.writeBoolean(t.running); b.writeBoolean(t.online);
            }
        }
        public static Snapshot read(FriendlyByteBuf b) {
            int flags = b.readVarInt(), tier = b.readVarInt(); long[] v = new long[8];
            for (int i = 0; i < v.length; i++) v[i] = b.readLong();
            int cp = b.readVarInt(), cps = b.readVarInt(), tp = b.readVarInt(), tps = b.readVarInt(), count = b.readVarInt();
            var segments = new ArrayList<Long>(); int size = size(b, MAX_SEGMENTS);
            for (int i = 0; i < size; i++) segments.add(b.readLong());
            var cores = new ArrayList<Core>(); size = size(b, CORE_PAGE_SIZE);
            for (int i = 0; i < size; i++) cores.add(new Core(b.readBlockPos(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readBoolean()));
            var tasks = new ArrayList<Task>(); size = size(b, TASK_PAGE_SIZE);
            for (int i = 0; i < size; i++) tasks.add(new Task(b.readUUID(), Component.Serializer.fromJson(b.readUtf(MAX_NAME_JSON)),
                    new ItemStack(net.minecraft.world.item.Item.byId(b.readVarInt())), b.readLong(), b.readLong(), b.readBoolean(), b.readBoolean(), b.readBoolean()));
            return new Snapshot(flags, tier, v[0], v[1], v[2], v[3], v[4], v[5], v[6], v[7], cp, cps, tp, tps, count, segments, cores, tasks);
        }
        private static int size(FriendlyByteBuf b, int max) {
            int size = b.readVarInt(); if (size < 0 || size > max) throw new IllegalArgumentException("oversized EC page"); return size;
        }
    }
    public static long add(long left, long right) { return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right; }
}
