/* SPDX-License-Identifier: GPL-3.0-only
 * Demand semantics adapt NECO old.NetNodeImpl.recipeConsumers, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import java.math.BigDecimal;

/** Two scalar snapshots, with no retained recipe, thread, controller or world references. */
public final class HyperNetDemand {
    public static final Sample EMPTY = new Sample(BigDecimal.ZERO, 0);
    public record Sample(BigDecimal amount, int consumers) {
        public Sample {
            if (amount == null || amount.signum() < 0 || consumers < 0)
                throw new IllegalArgumentException("Invalid computation demand");
        }
        public Sample add(Sample other) {
            return new Sample(amount.add(other.amount), (int)Math.min(Integer.MAX_VALUE, (long)consumers + other.consumers));
        }
        /** Display saturation only; demand never creates or reserves budget. */
        public double displayedAmount() { return Math.min(Double.MAX_VALUE, amount.doubleValue()); }
    }
    private long tick = Long.MIN_VALUE;
    private Sample current = EMPTY, previous = EMPTY;
    public void update(long now, Sample sample) {
        if (now < tick) throw new IllegalArgumentException("Demand time moved backwards");
        if (now != tick) { previous = now == tick + 1 ? current : EMPTY; tick = now; }
        current = java.util.Objects.requireNonNull(sample);
    }
    public Sample completed(long now) {
        return now == tick ? previous : now == tick + 1 ? current : EMPTY;
    }
    public void clear() { tick = Long.MIN_VALUE; current = previous = EMPTY; }
}
