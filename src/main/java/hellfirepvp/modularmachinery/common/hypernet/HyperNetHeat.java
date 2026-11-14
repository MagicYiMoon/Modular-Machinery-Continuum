/* SPDX-License-Identifier: GPL-3.0-only
 * Adapts NECO old.DataProcessor heat/efficiency/distribution, b37fbb53.
 * Decimal accumulation and fractional cooling floor are port corrections.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import java.math.*;

/** Pure thermal state. The world adapter owns tick cadence and persistence. */
final class HyperNetHeat {
    private static final BigDecimal TWO=BigDecimal.valueOf(2),QUARTER=new BigDecimal(".25"),
            THREE_QUARTERS=new BigDecimal(".75"),DERATE=new BigDecimal(".85"),SLOPE=new BigDecimal(".15");
    private final HyperNetThermalDefinition definition;
    private BigDecimal heat;
    private boolean overheated;
    HyperNetHeat(HyperNetThermalDefinition definition,BigDecimal heat,boolean overheated) {
        this.definition=definition;
        if(heat==null||heat.signum()<0)throw new IllegalArgumentException("Invalid stored processor heat");
        // One advancing tick can cross the threshold. Bound hostile/corrupt persisted values.
        var maximum=BigDecimal.valueOf(definition.overheatThreshold()).add(BigDecimal.valueOf(0xFFFFFFL*2));
        this.heat=heat.min(maximum);this.overheated=this.heat.signum()>0&&(overheated||this.heat.compareTo(threshold())>=0);
    }
    private BigDecimal threshold(){return BigDecimal.valueOf(definition.overheatThreshold());}
    BigDecimal stored(){return heat;}
    boolean overheated(){return overheated;}
    /** Exact rational derating, represented by numerator/denominator rather than rounded doubles. */
    BigDecimal numerator(){return overheated?BigDecimal.ZERO:heat.compareTo(threshold().multiply(DERATE))<0?BigDecimal.ONE:threshold().subtract(heat).max(BigDecimal.ZERO);}
    BigDecimal denominator(){return heat.compareTo(threshold().multiply(DERATE))<0?BigDecimal.ONE:threshold().multiply(SLOPE);}
    void generate(BigDecimal delivered) {
        if(delivered.signum()<0)throw new IllegalArgumentException("Negative thermal work");
        heat=heat.add(delivered.multiply(TWO));
        if(heat.compareTo(threshold())>=0)overheated=true;
    }
    void cool(int extensions) {
        if(heat.signum()==0)return;
        // Source uses the latched overheat ratio of 1 until completely cooled.
        BigDecimal threshold=threshold(),factor;
        if(overheated||heat.compareTo(threshold.multiply(THREE_QUARTERS))>0)factor=BigDecimal.ONE;
        else if(heat.compareTo(threshold.multiply(QUARTER))<=0)factor=QUARTER;
        else factor=QUARTER.add(heat.divide(threshold,40,RoundingMode.FLOOR));
        var cooling=BigDecimal.valueOf(definition.heatDistribution()).multiply(BigDecimal.valueOf(Math.max(1,extensions))).multiply(factor);
        // Fractional cooling is retained: tiny positive dissipation must eventually reach zero.
        heat=heat.subtract(cooling).max(BigDecimal.ZERO);
        if(heat.signum()==0)overheated=false;
    }
    void remove(BigDecimal amount) {
        if(amount.signum()<0)throw new IllegalArgumentException("Negative active cooling");
        heat=heat.subtract(amount).max(BigDecimal.ZERO);
        if(heat.signum()==0)overheated=false;
    }
}
