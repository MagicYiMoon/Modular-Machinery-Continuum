/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import java.math.BigDecimal;

/** Pure arithmetic tests independent of world/Forge, with Gson only for the immutable config type. */
public final class HyperNetHeatChecks {
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    public static void main(String[] args) {
        var def=new HyperNetThermalDefinition(100,4,"");
        var one=new HyperNetHeat(def,BigDecimal.ZERO,false);var split=new HyperNetHeat(def,BigDecimal.ZERO,false);
        one.generate(new BigDecimal("2"));for(int i=0;i<2000;i++)split.generate(new BigDecimal(".001"));
        require(one.stored().compareTo(split.stored())==0&&one.stored().compareTo(new BigDecimal("4"))==0,"split load reduced heat");
        var low=new HyperNetHeat(def,new BigDecimal("10"),false);low.cool(1);require(low.stored().compareTo(new BigDecimal("9"))==0,"low heat quarter cooling");
        var middle=new HyperNetHeat(def,new BigDecimal("50"),false);middle.cool(2);require(middle.stored().compareTo(new BigDecimal("44"))==0,"middle heat dynamic cooling");
        var high=new HyperNetHeat(def,new BigDecimal("90"),false);
        require(high.numerator().compareTo(new BigDecimal("10"))==0&&high.denominator().compareTo(new BigDecimal("15"))==0,"85% derating ratio");
        high.generate(new BigDecimal("6"));require(high.overheated()&&high.numerator().signum()==0,"threshold gate");
        high.cool(1);require(high.overheated()&&high.stored().compareTo(new BigDecimal("98"))==0,"latched overheat reset early");
        for(int i=0;i<25;i++)high.cool(1);require(!high.overheated()&&high.stored().signum()==0,"cool to zero recovery");
        var tiny=new HyperNetHeat(new HyperNetThermalDefinition(100,1,""),new BigDecimal(".3"),false);
        tiny.cool(1);require(tiny.stored().compareTo(new BigDecimal(".05"))==0,"fractional cooling truncated");
        tiny.cool(1);require(tiny.stored().signum()==0,"fractional cooling stuck forever");
        var maximum=new HyperNetHeat(def,new BigDecimal("1e100"),false);require(maximum.overheated()&&maximum.stored().compareTo(BigDecimal.valueOf(100+0xFFFFFFL*2))==0,"hostile heat unbounded");
        var zero=new HyperNetHeat(new HyperNetThermalDefinition(100,0,""),new BigDecimal("110"),true);zero.cool(Integer.MAX_VALUE);require(zero.stored().intValue()==110&&zero.overheated(),"disabled cooling reset heat");
        zero.remove(new BigDecimal("105"));require(zero.stored().intValue()==5&&zero.overheated(),"active cooling cleared latch early");
        zero.remove(new BigDecimal("99999999999999999999"));require(zero.stored().signum()==0&&!zero.overheated(),"active cooling underflow or stuck latch");
        zero.remove(BigDecimal.ONE);require(zero.stored().signum()==0,"repeated zero removal became negative");
        try{zero.remove(BigDecimal.ONE.negate());throw new AssertionError("negative cooling accepted");}catch(IllegalArgumentException expected){}
        System.out.println("[hypernet-heat] PASS: exact split load, heat zones, dynamic cooling, rational derating, latched overheat, fractional recovery and bounds");
    }
}
