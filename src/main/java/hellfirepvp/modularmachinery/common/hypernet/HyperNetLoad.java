/* SPDX-License-Identifier: GPL-3.0-only
 * Ordered CPU/RAM load accounting adapted from NECO old.DataProcessor/module types, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import java.math.*;
import java.util.*;

/** Pure load curve. Counts come from the upgrade bus, attributes from the server machine definition. */
final class HyperNetLoad {
    record Installed(HyperNetModule module, int count) {
        Installed { if (module == null || count <= 0) throw new IllegalArgumentException("Invalid installed module"); }
    }
    private final List<Installed> modules;
    private final BigDecimal capacity;
    HyperNetLoad(List<Installed> modules) {
        this.modules = List.copyOf(modules);
        BigDecimal cpu = BigDecimal.ZERO, ram = BigDecimal.ZERO;
        for (var installed : modules) {
            var amount = BigDecimal.valueOf(installed.module.capacity()).multiply(BigDecimal.valueOf(installed.count));
            if (installed.module.kind() == HyperNetModule.Kind.RAM) ram = ram.add(amount); else cpu = cpu.add(amount);
        }
        capacity = cpu.min(ram).min(BigDecimal.valueOf(0xFFFFFF));
    }
    double capacity() { return capacity.doubleValue(); }

    /** Round each physical module's cumulative cost up, rather than rounding each request up or down. */
    BigInteger cost(BigDecimal work) {
        if (work.signum() < 0 || work.compareTo(capacity) > 0) throw new IllegalArgumentException("Work outside hardware capacity");
        return cost(work, false).add(cost(work, true));
    }
    private BigInteger cost(BigDecimal work, boolean ram) {
        BigInteger total = BigInteger.ZERO;
        for (var installed : modules) {
            var module = installed.module;
            if ((module.kind() == HyperNetModule.Kind.RAM) != ram) continue;
            var unit = BigDecimal.valueOf(module.capacity());
            var take = work.min(unit.multiply(BigDecimal.valueOf(installed.count)));
            var divided = take.divideAndRemainder(unit);
            var full = divided[0].toBigIntegerExact().multiply(BigInteger.valueOf(module.energy()));
            var partial = divided[1].multiply(BigDecimal.valueOf(module.energy())).divide(unit, 0, RoundingMode.CEILING).toBigIntegerExact();
            total = total.add(full).add(partial);
            work = work.subtract(take);
            if (work.signum() == 0) break;
        }
        return total;
    }
    BigInteger increment(BigDecimal used, double amount) {
        return increment(used, BigDecimal.valueOf(amount));
    }
    BigInteger increment(BigDecimal used, BigDecimal amount) {
        return cost(used.add(amount)).subtract(cost(used));
    }
    double affordable(BigDecimal used, long energy) {
        return affordableWork(used, energy).doubleValue();
    }
    BigDecimal affordableWork(BigDecimal used, long energy) {
        var remaining = capacity.subtract(used);
        if (remaining.signum() <= 0) return BigDecimal.ZERO;
        var available = BigInteger.valueOf(energy);
        if (increment(used, remaining).compareTo(available) <= 0) return remaining;
        double high = remaining.doubleValue();
        // The double representation must never round a request above the decimal capacity.
        if (BigDecimal.valueOf(high).compareTo(remaining) > 0) high = Math.nextDown(high);
        if (increment(used, high).compareTo(available) <= 0) return BigDecimal.valueOf(high);
        // Positive IEEE-754 bit patterns are ordered. Searching bits covers subnormal amounts too;
        // 64 arithmetic halvings could otherwise incorrectly report zero for a tiny costly first module.
        long lowBits = 0, highBits = Double.doubleToRawLongBits(high);
        while (lowBits < highBits) {
            long midBits = lowBits + (highBits - lowBits + 1) / 2;
            double mid = Double.longBitsToDouble(midBits);
            if (increment(used, mid).compareTo(available) <= 0) lowBits = midBits; else highBits = midBits - 1;
        }
        return BigDecimal.valueOf(Double.longBitsToDouble(lowBits));
    }
}
