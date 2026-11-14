/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting.requirement;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.hypernet.HyperNetMaintenance;
import hellfirepvp.modularmachinery.common.machine.*;
import java.util.List;

/** A start-time gate and finish-time effect, never an input consumed at start or per tick. */
public final class RequirementHyperNetMaintenance implements Requirement.Parallelizable {
    public enum Kind { COOLING, CIRCUIT_REPAIR }
    private final Kind kind;private final int amount;private boolean unaffected;private int parallelism=1;
    public RequirementHyperNetMaintenance(Kind kind,int amount) {
        this.kind=java.util.Objects.requireNonNull(kind);if(amount<0)throw new IllegalArgumentException("Negative maintenance amount");this.amount=amount;
    }
    public Kind kind(){return kind;}public int amount(){return amount;}
    @Override public String typeId(){return kind==Kind.COOLING?"modularmachinery:hypernet_cooling":"modularmachinery:hypernet_circuit_repair";}
    @Override public IOType getIOType(){return IOType.INPUT;}
    @Override public boolean canStart(MachineContext context){return HyperNetMaintenance.plan(context,List.of(this),1,true).valid();}
    @Override public boolean canFinish(MachineContext context){return true;}
    @Override public String describeFailure(MachineContext context){return HyperNetMaintenance.plan(context,List.of(this),1,true).failure();}
    @Override public String describe(){return amount+(kind==Kind.COOLING?" HU cooling on completion":" circuit repair on completion");}
    @Override public int getMaxParallelism(MachineContext context,int max,List<Requirement> siblings){
        var all=siblings==null||siblings.isEmpty()?List.<Requirement>of(this):siblings;
        int low=0,high=Math.max(0,max);
        while(low<high){int mid=low+(int)(((long)high-low+1)/2);if(HyperNetMaintenance.plan(context,all,mid,true).valid())low=mid;else high=mid-1;}
        return low;
    }
    @Override public int getParallelism(){return parallelism;}
    @Override public void setParallelism(int value){if(!unaffected)parallelism=Math.max(1,value);}
    @Override public boolean isParallelizeUnaffected(){return unaffected;}
    @Override public void setParallelizeUnaffected(boolean value){unaffected=value;if(value)parallelism=1;}
    @Override public Requirement scaled(float value,int operation,IOType io,boolean chance){
        double next=chance||io==IOType.OUTPUT?amount:operation==OPERATION_MULTIPLY?(double)amount*value:(double)amount+value;
        if(!Double.isFinite(next)||next>Integer.MAX_VALUE)throw new IllegalArgumentException("Maintenance modifier exceeds integer range");
        var copy=new RequirementHyperNetMaintenance(kind,(int)Math.ceil(Math.max(0,next)));
        copy.setParallelizeUnaffected(unaffected);copy.setParallelism(parallelism);return copy;
    }
}
