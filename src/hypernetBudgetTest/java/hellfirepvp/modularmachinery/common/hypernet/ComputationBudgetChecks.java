/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone Java 17 accounting checks; no Minecraft fixtures or optional mod dependencies. */
public final class ComputationBudgetChecks {
    public static void main(String[] args) throws Exception {
        try (var budget = new ComputationBudget(); var other = new ComputationBudget()) {
            var id = new UUID(0, 1); var first = budget.attach(id); var second = budget.attach(new UUID(0, 2));
            require(!budget.publish(first, 10), "supply before first tick");
            budget.beginTick(0, true, 100);
            require(budget.publish(first, 60) && budget.publish(second, 80), "fresh supply");
            require(!budget.publish(other.attach(id), 500), "foreign lease accepted");
            var simulated = budget.request(90, true);
            require(simulated.granted() == 90 && budget.available() == 100 && budget.consumed() == 0, "simulation spent credit");
            var used = budget.request(90, false);
            require(used.equals(simulated) && used.shares().get(id) == 60 && budget.available() == 10, "multi-provider accounting");
            require(budget.request(11, false).granted() == 0 && budget.available() == 10, "partial failed debit");
            require(!budget.beginTick(0, true, 1000) && !budget.publish(first, 1000) && budget.available() == 10, "duplicate tick refill");
            budget.beginTick(1, true, 100);
            require(budget.available() == 0, "unpublished previous-tick credit survived");
            budget.publish(first, 60); budget.publish(second, 80);
            var replacement = budget.attach(id);
            require(!budget.publish(first, 60) && !budget.detach(first), "replaced lease revived");
            require(budget.available() == 80 && !budget.publish(replacement, 20), "replacement duplicated same-tick supply");
            budget.detach(second); require(budget.available() == 0, "detached supply retained");
            budget.stop(); require(budget.request(1, false).granted() == 0 && !budget.publish(replacement, 20), "stopped centre supplied");
            budget.beginTick(2, true, 100); require(budget.available() == 0, "resume retained credit");
            budget.publish(replacement, 0.3); require(budget.request(0.1, false).granted() == 0.1, "fractional supply");
            require(budget.request(0.3, false).granted() == 0 && budget.available() > 0, "fractional failure consumed rest");
            for (double invalid : new double[]{-1, Double.NaN, Double.POSITIVE_INFINITY}) {
                rejects(() -> budget.request(invalid, false)); rejects(() -> budget.publish(replacement, invalid));
                rejects(() -> budget.beginTick(3, true, invalid));
            }
            rejects(() -> budget.beginTick(1, true, 100));
            var failure = new AtomicReference<Throwable>();
            var thread = new Thread(() -> { try { budget.request(1, false); } catch (Throwable t) { failure.set(t); } });
            thread.start(); thread.join(); require(failure.get() instanceof IllegalStateException, "cross-thread debit accepted");
        }
        var retired = new ComputationBudget(); var lease = retired.attach(UUID.randomUUID()); retired.close();
        rejects(() -> retired.publish(lease, 1)); rejects(() -> retired.beginTick(0, true, 1));
        try (var budget = new ComputationBudget()) {
            var p = budget.attach(UUID.randomUUID()); budget.beginTick(0, true, 100); budget.publish(p, 80);
            budget.request(30, false); budget.restrict(p, 40);
            require(budget.available() == 40 && budget.consumed() == 30, "power clamp changed consumed budget");
            budget.restrict(p, 100); require(budget.available() == 40, "power restoration refilled same tick");
            rejects(() -> budget.restrict(p, -1));
            budget.detach(p); budget.restrict(p, 40); require(budget.available() == 0, "revoked clamp restored credit");
        }
        try (var budget = new ComputationBudget()) {
            var idA = UUID.randomUUID(); var idB = UUID.randomUUID();
            var a = budget.attach(idA); var b = budget.attach(idB);
            budget.beginTick(0, true, 10); budget.publish(a, 1); budget.publish(b, 1);
            budget.request(1e-20, false); var split = budget.request(1.5, false);
            require(split.exactShares().get(idA).compareTo(new java.math.BigDecimal(".99999999999999999999")) == 0
                    && split.exactShares().get(idB).compareTo(new java.math.BigDecimal(".50000000000000000001")) == 0,
                    "receipt rounded away tiny prior work");
        }
        try (var budget = new ComputationBudget()) {
            var a = budget.attach(new UUID(0, 1)); var b = budget.attach(new UUID(0, 2)); var random = new Random(3555);
            for (int tick = 0; tick < 2000; tick++) {
                double limit = 1 + random.nextInt(2000), supplyA = random.nextInt(1000), supplyB = random.nextInt(1000);
                budget.beginTick(tick, true, limit); budget.publish(a, supplyA); budget.publish(b, supplyB);
                double balance = Math.min(limit, supplyA + supplyB), total = 0;
                for (int request = 0; request < 100; request++) {
                    double amount = random.nextInt(1000) / 8.0;
                    var before = budget.available(); var preview = budget.request(amount, true);
                    require(budget.available() == before, "random simulation mutated ledger");
                    var actual = budget.request(amount, false); require(preview.equals(actual), "simulation/commit disagreement");
                    if (amount > balance) require(actual.granted() == 0, "random overdraft");
                    balance -= actual.granted(); total += actual.granted();
                    require(budget.available() == balance && budget.consumed() == total, "random conservation");
                }
            }
            budget.beginTick(2000, true, Double.MAX_VALUE); budget.publish(a, Double.MAX_VALUE); budget.publish(b, Double.MAX_VALUE);
            require(budget.available() == Double.MAX_VALUE && budget.request(Double.MAX_VALUE, true).granted() == Double.MAX_VALUE, "finite saturation overflow");
            require(budget.request(1, false).granted() == 1 && budget.consumed() == 1,
                    "small debit disappeared beside huge centre balance");
            require(budget.request(Double.MAX_VALUE, false).granted() == 0,
                    "huge request reused already-spent credit");
        }
        try (var budget = new ComputationBudget()) {
            var provider = budget.attach(new UUID(0, 1));budget.beginTick(0,true,1);budget.publish(provider,.3);
            var amount = new java.math.BigDecimal(".1").multiply(java.math.BigDecimal.valueOf(3));
            require(budget.request(amount,true).granted()==.3&&budget.available()==.3,"decimal aggregate simulation");
            require(budget.request(amount,false).granted()==.3&&budget.available()==0,"decimal aggregate debit");
            rejects(()->budget.request(new java.math.BigDecimal("1e-999"),false));
            require(budget.consumed()==.3,"underflow request changed budget");
        }
        System.out.println("[hypernet-budget] PASS: tick reset, atomic multi-provider debit, 200000 shared requests, simulation, stale/foreign leases, detach/stop/retire, invalid/fractional values, decimal batch/underflow, finite overflow and thread isolation");
    }
    private static void rejects(Runnable action) { try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { return; } throw new AssertionError("invalid operation accepted"); }
    private static void require(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
}
