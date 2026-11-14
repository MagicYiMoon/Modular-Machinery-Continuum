/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.efabricator.EFabricatorThroughput;
import java.math.BigDecimal;

/** Standalone numerical/lifetime checks; actual node and menu wiring have separate Forge fixtures. */
public final class NecoStatisticsChecks {
    private static void equal(double value, double expected, String reason) {
        if (!Double.isFinite(value) || Math.abs(value - expected) > Math.max(1e-12, Math.abs(expected) * 1e-12))
            throw new AssertionError(reason + ": " + value + " != " + expected);
    }
    public static void main(String[] args) {
        var load = new HyperNetLoadAverage();
        load.sample(0, true, BigDecimal.TEN);
        for (int tick = 1; tick < 100; tick++) load.sample(tick, true, BigDecimal.ZERO);
        equal(load.average(), .1, "100-tick working mean includes idle ticks");
        for (int n = 0; n < 500; n++) { load.sample(99, true, BigDecimal.TEN); equal(load.average(), .1, "same tick and inspection cannot add load"); }
        load.sample(100, true, BigDecimal.ZERO); equal(load.average(), 0, "oldest load expires");
        load.sample(101, true, BigDecimal.TEN);
        load.sample(102, false, BigDecimal.ZERO); equal(load.average(), 0, "stopped core clears load");
        load.sample(103, true, BigDecimal.ONE); equal(load.average(), 1, "resume starts new window");
        load.sample(200, true, BigDecimal.TEN); equal(load.average(), 10, "tick gap discards stale load");
        load.clear();
        for (int tick = 0; tick < 110; tick++) load.sample(tick, true, BigDecimal.valueOf(Double.MAX_VALUE));
        equal(load.average(), Double.MAX_VALUE, "sum cannot overflow at large finite load");
        var statistics = new HyperNetStatistics();
        statistics.commit(30, new BigDecimal("0.1")); statistics.commit(30, new BigDecimal("0.2"));
        if (statistics.completedConsumption(31).compareTo(new BigDecimal("0.3")) != 0) throw new AssertionError("completed decimal load");
        statistics.advance(31);
        if (statistics.completedConsumption(31).compareTo(new BigDecimal("0.3")) != 0) throw new AssertionError("advance changed completed load");
        equal(statistics.completedConsumption(33).doubleValue(), 0, "expired completion");

        var rate = new EFabricatorThroughput();
        rate.sample(0, 0, true); rate.sample(10, 20, true); equal(rate.perTick(), 2, "production starting at zero is counted");
        rate.sample(10, 20, true); equal(rate.perTick(), 2, "same-tick page update");
        rate.sample(15, 30, true); equal(rate.perTick(), 2, "early action uses actual elapsed ticks");
        for (int i = 0; i < 10; i++) rate.sample(25 + i * 10, 30, true);
        equal(rate.perTick(), 0, "idle heartbeats flush all ten samples");
        rate.sample(130, 40, true); rate.sample(140, 0, true); equal(rate.perTick(), 0, "counter reset rebases");
        rate.sample(150, 10, true); equal(rate.perTick(), 1, "counter reset recovery");
        rate.sample(160, 10, false); equal(rate.perTick(), 0, "unreadable data clears history");
        rate.sample(170, Long.MAX_VALUE - 40, true); rate.sample(180, Long.MAX_VALUE - 20, true);
        equal(rate.perTick(), 2, "small delta at long maximum retains precision");
        rate.sample(190, Long.MAX_VALUE, true); equal(rate.perTick(), 2, "long total delta does not narrow to int");
        rate.sample(200, Long.MAX_VALUE, true);
        for (int n = 0; n < 500; n++) equal(rate.perTick(), 4.0 / 3, "rendering cannot sample");
        rate.sample(1, 1, true); equal(rate.perTick(), 0, "clock rollback resets view");
        System.out.println("[neco-statistics] PASS: bounded working load, exact completed ticks, stop/gap/reset, ten display updates, idle decay, irregular timing and large counters");
    }
}
