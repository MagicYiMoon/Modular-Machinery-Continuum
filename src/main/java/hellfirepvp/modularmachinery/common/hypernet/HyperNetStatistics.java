/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import java.math.BigDecimal;

/** Two tick buckets. Reading never advances a clock or touches resources. Not saved as credit. */
public final class HyperNetStatistics {
    private long tick=Long.MIN_VALUE;
    private BigDecimal consumed=BigDecimal.ZERO,previous=BigDecimal.ZERO;
    private double offer,previousOffer;
    public void advance(long now){
        if(now<tick)throw new IllegalArgumentException("Statistics time moved backwards");
        if(now==tick)return;
        previous=now==tick+1?consumed:BigDecimal.ZERO;previousOffer=now==tick+1?offer:0;
        tick=now;consumed=BigDecimal.ZERO;offer=0;
    }
    public void commit(long now,BigDecimal amount){advance(now);consumed=consumed.add(amount);}
    public void offer(long now,double amount){advance(now);offer=amount;}
    public double current(){return consumed.doubleValue();}
    public BigDecimal currentExact(){return consumed;}
    public record Sample(double consumed,double offered){}
    BigDecimal completedConsumption(long now) {
        return now==tick?previous:now==tick+1?consumed:BigDecimal.ZERO;
    }
    public Sample completed(long now){
        return now==tick?new Sample(previous.doubleValue(),previousOffer)
                :now==tick+1?new Sample(consumed.doubleValue(),offer):new Sample(0,0);
    }
}
