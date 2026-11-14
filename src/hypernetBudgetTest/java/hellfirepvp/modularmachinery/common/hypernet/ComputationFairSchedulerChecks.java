/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import java.math.BigDecimal;
import java.util.*;

public final class ComputationFairSchedulerChecks {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        var scheduler = new ComputationFairScheduler();
        var owner = UUID.randomUUID();
        var keys = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        int[] served = new int[3];
        for (int tick = 0; tick < 200000; tick++) {
            var remaining = n("5");
            for (int i = 0; i < keys.size(); i++) {
                var key = keys.get(i);
                if (!scheduler.permits(tick, owner, key, n("3"), remaining)) continue;
                require(scheduler.permits(tick, owner, key, n("3"), remaining), "precheck changed turn");
                scheduler.granted(tick, key); remaining = remaining.subtract(n("3")); served[i]++;
                require(!scheduler.permits(tick, owner, key, n("3"), remaining), "duplicate work");
            }
            require(remaining.compareTo(n("2")) == 0 && scheduler.size() == 3, "conservation/bounded tickets");
        }
        require(Arrays.stream(served).max().orElseThrow()-Arrays.stream(served).min().orElseThrow() <= 1, "fixed-order starvation");
        scheduler.clear();
        int continuous = 0;
        for(int tick=0;tick<100;tick++) {
            var newcomer=UUID.randomUUID();var remaining=n("3");
            if(scheduler.permits(tick,owner,newcomer,n("3"),remaining)) {
                scheduler.granted(tick,newcomer);remaining=BigDecimal.ZERO;
            }
            if(scheduler.permits(tick,owner,keys.get(0),n("3"),remaining)) {
                scheduler.granted(tick,keys.get(0));continuous++;
            }
            scheduler.abandon(newcomer);
        }
        require(continuous>=49,"repeated new recipes starved an existing recipe");
        scheduler.clear();
        var large = keys.get(0); var small = keys.get(1); var impossible = keys.get(2);
        int largeTicks = 0, smallTicks = 0;
        for (int tick = 0; tick < 100; tick++) {
            var remaining = n("0.3");
            require(!scheduler.permits(tick, owner, impossible, n("0.4"), remaining), "oversized admitted");
            if (scheduler.permits(tick, owner, small, n("0.1"), remaining)) {
                scheduler.granted(tick, small); remaining=remaining.subtract(n("0.1")); smallTicks++;
            }
            if (scheduler.permits(tick, owner, large, n("0.3"), remaining)) {
                scheduler.granted(tick, large); remaining=remaining.subtract(n("0.3")); largeTicks++;
            }
            require(remaining.signum() >= 0, "decimal overdraft");
        }
        require(largeTicks > 40 && smallTicks > 40, "mixed-size starvation");
        scheduler.clear();
        require(scheduler.permits(0,owner,large,n("3"),n("5")),"initial turn");
        scheduler.abandon(large);
        require(scheduler.permits(0,owner,small,n("3"),n("5")),"non-computation failure blocked peer");
        scheduler.granted(0,small);
        scheduler.retain(owner,Set.of());require(scheduler.size()==0,"finish/cancel cleanup");
        for(int i=0;i<ComputationFairScheduler.MAX_TICKETS+20;i++)
            scheduler.permits(1,owner,UUID.randomUUID(),n("3"),n("2"));
        require(scheduler.size()==ComputationFairScheduler.MAX_TICKETS,"unbounded queue");
        scheduler.permits(3,owner,large,n("3"),n("5"));require(scheduler.size()==1,"stale tickets");
        scheduler.removeOwner(owner);require(scheduler.size()==0,"disconnect cleanup");
        scheduler.permits(4,owner,large,n("3"),n("5"));
        require(scheduler.permits(4,owner,large,n("1.25"),n("5")),"changed modifier");
        require(scheduler.permits(4,owner,small,n("3.75"),n("5")),"changed amount kept stale reservation");
        scheduler.clear();require(scheduler.size()==0,"close cleanup");
        System.out.println("[hypernet-fair] PASS: 200000 ticks, fixed order, mixed sizes, exact decimals, idempotence, abandon, expiry, owner cleanup, 4096 bound");
    }
}
