/* SPDX-License-Identifier: GPL-3.0-only
 * Adapts NECO radiator/fix recipe start checks and finish effects, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementHyperNetMaintenance;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import java.math.BigDecimal;
import java.util.List;

/** Materials use ordinary recipe IO. Effects occur once, only after all outputs have room. */
public final class HyperNetMaintenance {
    public static final String INVALID="craftcheck.failure.hypernet.maintenance",HEAT="craftcheck.failure.hypernet.heat",
            ROOM="craftcheck.failure.hypernet.repair_space",SEGMENTS="craftcheck.failure.hypernet.cooling_segments";
    private HyperNetMaintenance(){}
    public record Plan(HyperNetNode node,BigDecimal cooling,BigDecimal repair,int copies,boolean hasCooling,boolean hasRepair,String failure) {
        public boolean valid(){return failure==null;}
        public boolean execute(){
            if(!valid())return false;
            if(node==null)return true;
            if(node.maintenanceFailure(cooling,repair,copies,false,hasCooling,hasRepair)!=null)return false;
            node.maintain(cooling,repair,hasCooling,hasRepair);return true;
        }
    }
    public static Plan plan(MachineContext context,List<Requirement> requirements,int parallelism,boolean start) {
        BigDecimal cooling=BigDecimal.ZERO,repair=BigDecimal.ZERO;boolean hasCooling=false,hasRepair=false;int copies=0;
        for(var r:requirements)if(r instanceof RequirementHyperNetMaintenance maintenance) {
            int batch=maintenance.isParallelizeUnaffected()?1:Math.max(1,parallelism);
            var amount=BigDecimal.valueOf(maintenance.amount()).multiply(BigDecimal.valueOf(batch));
            if(maintenance.kind()==RequirementHyperNetMaintenance.Kind.COOLING){hasCooling=true;cooling=cooling.add(amount);copies=Math.max(copies,batch);}
            else{hasRepair=true;repair=repair.add(amount);}
        }
        if(!hasCooling&&!hasRepair)return new Plan(null,cooling,repair,0,false,false,null);
        var world=context.getLevel();var pos=context.getControllerPos();
        var chunk=world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        var entity=chunk==null?null:chunk.getBlockEntity(pos,net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        var node=entity instanceof TileController controller&&context.getMachine()!=null
                &&context.getMachine().getRegistryName().equals(controller.getMachineName())?controller.getHyperNetNode():null;
        String failure=node==null?INVALID:node.maintenanceFailure(cooling,repair,copies,start,hasCooling,hasRepair);
        return new Plan(node,cooling,repair,copies,hasCooling,hasRepair,failure);
    }
}
