/* SPDX-License-Identifier: GPL-3.0-only
 * Rolling load from NECO old.DataProcessor, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Arrays;

/** Runtime-only, at most 100 completed working ticks. Inspection never samples. */
final class HyperNetLoadAverage {
    private final BigDecimal[] samples = new BigDecimal[100];
    private BigDecimal total = BigDecimal.ZERO;
    private int next, count;
    private long tick = Long.MIN_VALUE;

    void sample(long now, boolean working, BigDecimal amount) {
        if (amount == null || amount.signum() < 0) throw new IllegalArgumentException("Invalid computation load");
        if (now < tick) throw new IllegalArgumentException("Load time moved backwards");
        if (!working) { clear(); tick = now; return; }
        if (now == tick) return;
        if (tick != Long.MIN_VALUE && now != tick + 1) clear();
        tick = now;
        if (count == samples.length) total = total.subtract(samples[next]); else count++;
        samples[next] = amount;
        total = total.add(amount);
        next = (next + 1) % samples.length;
    }

    double average() {
        if (count == 0) return 0;
        return Math.min(Double.MAX_VALUE, total.divide(BigDecimal.valueOf(count), MathContext.DECIMAL128).doubleValue());
    }

    void clear() {
        Arrays.fill(samples, null); total = BigDecimal.ZERO; next = count = 0; tick = Long.MIN_VALUE;
    }
}
