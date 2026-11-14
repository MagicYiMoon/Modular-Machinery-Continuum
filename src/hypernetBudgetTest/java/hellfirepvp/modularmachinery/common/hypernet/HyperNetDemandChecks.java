/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import java.math.BigDecimal;

public final class HyperNetDemandChecks {
    private static void require(boolean test, String reason) { if (!test) throw new AssertionError(reason); }
    public static void main(String[] args) {
        var history = new HyperNetDemand();
        var one = new HyperNetDemand.Sample(new BigDecimal("0.1"), 1);
        var two = new HyperNetDemand.Sample(new BigDecimal("0.2"), 1);
        history.update(10, one.add(two));
        require(history.completed(10).consumers() == 0, "unfinished tick exposed");
        require(history.completed(11).amount().compareTo(new BigDecimal("0.3")) == 0, "decimal sum lost precision");
        history.update(10, one); // Replacement, never double count a repeated controller tick.
        require(history.completed(11).consumers() == 1, "same tick accumulated");
        history.update(11, two);
        require(history.completed(11).equals(one), "tick order changes preceding sample");
        require(history.completed(12).equals(two), "previous tick missing");
        require(history.completed(13).equals(HyperNetDemand.EMPTY), "stale demand retained");
        history.update(20, one);
        require(history.completed(20).equals(HyperNetDemand.EMPTY), "offline time filled in");
        var enormous = new HyperNetDemand.Sample(BigDecimal.valueOf(Double.MAX_VALUE), Integer.MAX_VALUE).add(one);
        require(enormous.displayedAmount() == Double.MAX_VALUE && enormous.consumers() == Integer.MAX_VALUE, "display bounds");
        for (long tick = 21; tick < 200_000; tick++) history.update(tick, tick % 2 == 0 ? one : two);
        require(history.completed(200_000).equals(two), "long snapshot replacement");
        history.clear();
        require(history.completed(200_000).equals(HyperNetDemand.EMPTY), "closed sample retained");
        try { new HyperNetDemand.Sample(BigDecimal.ONE.negate(), 1); throw new AssertionError("negative demand"); }
        catch (IllegalArgumentException expected) { }
        System.out.println("[hypernet-demand] PASS: exact sums, replacement, preceding tick, expiry, bounds, 200000 snapshots, close");
    }
}
