/* SPDX-License-Identifier: GPL-3.0-only
 * Ten-update display mean from NECO TotalCraftedLabel, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.efabricator;

import java.util.Arrays;

/** Per-view display samples; never saved, used as credit, or advanced by rendering. */
public final class EFabricatorThroughput {
    private final double[] samples = new double[10];
    private int next, count;
    private long tick = Long.MIN_VALUE, crafted;

    public void sample(long now, long value, boolean readable) {
        if (!readable || value < 0) { clear(); return; }
        if (tick == Long.MIN_VALUE || now < tick || value < crafted) {
            clear(); tick = now; crafted = value; return;
        }
        if (now == tick) return;
        samples[next] = (double)(value - crafted) / (now - tick);
        next = (next + 1) % samples.length;
        count = Math.min(samples.length, count + 1);
        tick = now; crafted = value;
    }

    public double perTick() {
        double mean = 0;
        // Divide before summing so even very large valid counts remain finite.
        for (int i = 0; i < count; i++) mean += samples[i] / count;
        return mean;
    }

    private void clear() { Arrays.fill(samples, 0); next = count = 0; tick = Long.MIN_VALUE; crafted = 0; }
}
