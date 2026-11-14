/* SPDX-License-Identifier: GPL-3.0-only
 * Adapts NECO old.ComputationCenter circuit durability, b37fbb53.
 */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.nbt.*;

/** Private persistent circuit. No wear while offline, stopped, or merely queried. */
final class HyperNetCircuitState {
    private final TileController owner;
    private final HyperNetCircuitDefinition definition;
    private final String machine;
    private int durability;
    private long lastWearTick;
    HyperNetCircuitState(TileController owner, HyperNetCircuitDefinition definition, String machine) {
        this.owner=owner;this.definition=definition;this.machine=machine;
        durability=definition.capacity();lastWearTick=owner.getLevel().getGameTime();
        var data=owner.getHyperNetData();
        if (data.contains("circuit")) {
            var tag=data.getCompound("circuit");
            if (!data.contains("circuit",Tag.TAG_COMPOUND) || tag.getInt("version")!=1
                    || !tag.getString("machine").equals(machine) || !tag.contains("durability",Tag.TAG_INT)
                    || !tag.contains("lastWearTick",Tag.TAG_LONG) || tag.getInt("durability")<0) durability=0;
            else {
                durability=Math.min(definition.capacity(),tag.getInt("durability"));
                lastWearTick=Math.min(lastWearTick,tag.getLong("lastWearTick"));
            }
        }
        save();
    }
    int stored(){return durability;}
    int room(){return definition.capacity()-durability;}
    boolean permitsWork(){return (long)durability*20>=definition.capacity();}
    void repair(java.math.BigDecimal amount) {
        if(amount.signum()<0)throw new IllegalArgumentException("Negative circuit repair");
        int added=amount.min(java.math.BigDecimal.valueOf(room())).intValueExact();
        if(added>0){durability+=added;save();}
    }
    boolean wearDue(){long now=owner.getLevel().getGameTime();return now%20==0&&now!=lastWearTick;}
    void wear(double load) {
        if(!wearDue())return;
        long now=owner.getLevel().getGameTime();
        lastWearTick=now;
        double chance=Math.min(1,Math.max(.01,definition.wearChance()*Math.max(0,load)));
        if(owner.getLevel().random.nextFloat()<=chance) {
            int min=definition.minWear(),max=definition.maxWear();
            int amount=min+(max==min?0:owner.getLevel().random.nextInt(max-min));
            durability=(int)Math.max(0,(long)durability-amount);
        }
        // Persist successful and unsuccessful checks: reloading cannot reroll this tick.
        save();
    }
    private void save(){
        var data=owner.getHyperNetData();var tag=new CompoundTag();tag.putInt("version",1);
        tag.putString("machine",machine);tag.putInt("durability",durability);tag.putLong("lastWearTick",lastWearTick);
        data.put("circuit",tag);owner.setHyperNetData(data);
    }
}
