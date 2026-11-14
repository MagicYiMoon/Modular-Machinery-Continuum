/* SPDX-License-Identifier: GPL-3.0-only
 * NECO's 100-update TimeRecorder window, adapted to AE2 15 execution counters.
 */
package hellfirepvp.modularmachinery.common.ecalculator;

import net.minecraft.network.FriendlyByteBuf;
import java.util.*;

/** Runtime-only measurements; never part of saved tasks or the existing monitor wire format. */
public final class ECalculatorStatistics {
    public static final int WINDOW = 100;
    private static final long MAX_SAMPLE = Long.MAX_VALUE / WINDOW;
    private final long[] times = new long[WINDOW], operations = new long[WINDOW];
    private long timeSum, operationSum;
    private int cursor, size;
    public void record(long nanos, long pushedPatterns) {
        if (nanos < 0 || pushedPatterns < 0) throw new IllegalArgumentException("negative EC sample");
        nanos = Math.min(nanos, MAX_SAMPLE); pushedPatterns = Math.min(pushedPatterns, MAX_SAMPLE);
        timeSum -= times[cursor]; operationSum -= operations[cursor];
        times[cursor] = nanos; operations[cursor] = pushedPatterns;
        timeSum += nanos; operationSum += pushedPatterns;
        cursor = (cursor + 1) % WINDOW; size = Math.min(WINDOW, size + 1);
    }
    public Measurement measurement() { return size == 0 ? Measurement.UNKNOWN : new Measurement(size, timeSum / size, (double)operationSum / size); }
    public record Measurement(int samples, long nanosPerTick, double operationsPerTick) {
        public static final Measurement UNKNOWN = new Measurement(0, 0, 0);
        public Measurement {
            if (samples < 0 || samples > WINDOW || nanosPerTick < 0 || !Double.isFinite(operationsPerTick) || operationsPerTick < 0
                    || samples == 0 && (nanosPerTick != 0 || operationsPerTick != 0))
                throw new IllegalArgumentException("invalid EC measurement");
        }
        public boolean ready() { return samples > 0; }
    }
    public record Task(UUID id, Measurement measurement) { public Task { Objects.requireNonNull(id); Objects.requireNonNull(measurement); } }
    public record Page(int taskPage, boolean ready, long nanosPerTick, double operationsPerTick, List<Task> tasks) {
        public static final Page UNKNOWN = new Page(0, false, 0, 0, List.of());
        public Page {
            tasks = List.copyOf(tasks);
            if (taskPage < 0 || nanosPerTick < 0 || !Double.isFinite(operationsPerTick) || operationsPerTick < 0
                    || tasks.size() > ECalculatorMonitor.TASK_PAGE_SIZE || tasks.stream().map(Task::id).distinct().count() != tasks.size())
                throw new IllegalArgumentException("invalid EC statistics page");
        }
        public Measurement task(UUID id) { return tasks.stream().filter(t -> t.id.equals(id)).map(Task::measurement).findFirst().orElse(Measurement.UNKNOWN); }
        public void write(FriendlyByteBuf b) {
            b.writeVarInt(taskPage); b.writeBoolean(ready); b.writeLong(nanosPerTick); b.writeDouble(operationsPerTick);
            b.writeVarInt(tasks.size());
            for (var t : tasks) { b.writeUUID(t.id); b.writeVarInt(t.measurement.samples); b.writeLong(t.measurement.nanosPerTick); b.writeDouble(t.measurement.operationsPerTick); }
        }
        public static Page read(FriendlyByteBuf b) {
            int page = b.readVarInt(); boolean ready = b.readBoolean(); long nanos = b.readLong(); double ops = b.readDouble();
            int size = b.readVarInt(); if (size < 0 || size > ECalculatorMonitor.TASK_PAGE_SIZE) throw new IllegalArgumentException("oversized EC statistics page");
            var tasks = new ArrayList<Task>(size);
            for (int i = 0; i < size; i++) tasks.add(new Task(b.readUUID(), new Measurement(b.readVarInt(), b.readLong(), b.readDouble())));
            return new Page(page, ready, nanos, ops, tasks);
        }
    }
}
