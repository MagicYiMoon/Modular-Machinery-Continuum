/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.util;

/** Source TimeRecorder's bounded recent-sample average, with safe nanosecond accumulation. */
public final class RollingTiming {
    private final long[] samples;
    private int size, next;
    private long total;

    public RollingTiming(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("Timing window must be positive");
        samples = new long[capacity];
    }
    public void add(long nanos) {
        long value = Math.min(Math.max(0, nanos), Long.MAX_VALUE / samples.length);
        total -= samples[next];
        samples[next] = value;
        total += value;
        next = (next + 1) % samples.length;
        if (size < samples.length) size++;
    }
    public long averageMicros() { return size == 0 ? 0 : total / size / 1_000; }
    public int size() { return size; }
}
