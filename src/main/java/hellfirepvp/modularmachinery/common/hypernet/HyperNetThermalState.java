/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.nbt.*;
import java.math.BigDecimal;

/** Persistent physical heat; no offline catch-up and at most one cooling step per world tick. */
final class HyperNetThermalState {
    private final TileController owner;
    private final HyperNetThermalDefinition definition;
    private final String machine;
    private final HyperNetHeat heat;
    private long lastTick;
    HyperNetThermalState(TileController owner,HyperNetThermalDefinition definition,String machine) {
        this.owner=owner;this.definition=definition;this.machine=machine;
        var saved=owner.getHyperNetData();BigDecimal stored=BigDecimal.ZERO;boolean overheated=false;
        lastTick=owner.getLevel().getGameTime();
        if(saved.contains("thermal")) {
            var tag=saved.getCompound("thermal");
            try {
                if(!saved.contains("thermal",Tag.TAG_COMPOUND)||tag.getInt("version")!=1
                        ||!tag.getString("machine").equals(machine)||!tag.contains("heat",Tag.TAG_STRING)
                        ||!tag.contains("overheated",Tag.TAG_BYTE)||!tag.contains("lastTick",Tag.TAG_LONG))
                    throw new IllegalArgumentException("Invalid thermal state");
                var text=tag.getString("heat");
                if(text.length()>1500)throw new IllegalArgumentException("Oversized thermal state");
                stored=new BigDecimal(text);
                if(stored.signum()<0||stored.scale()>1200||stored.scale()< -10||stored.precision()>1250)
                    throw new IllegalArgumentException("Invalid thermal number");
                overheated=tag.getBoolean("overheated");
                // A foreign/future clock receives no free catch-up cooling.
                lastTick=Math.min(lastTick,tag.getLong("lastTick"));
            } catch(IllegalArgumentException invalid) {
                stored=BigDecimal.valueOf(definition.overheatThreshold());overheated=true;
            }
        }
        heat=new HyperNetHeat(definition,stored,overheated);save();
    }
    void refresh() {
        long now=owner.getLevel().getGameTime();if(now==lastTick)return;
        lastTick=now;
        if(heat.stored().signum()==0)return;
        var pattern=definition.dynamicPattern().isEmpty()?null:owner.getDynamicPattern(definition.dynamicPattern());
        heat.cool(pattern==null?1:pattern.getSize());save();
    }
    BigDecimal peekStored(){return heat.stored();}
    boolean peekOverheated(){return heat.overheated();}
    BigDecimal stored(){refresh();return heat.stored();}
    boolean overheated(){refresh();return heat.overheated();}
    BigDecimal numerator(){refresh();return heat.numerator();}
    BigDecimal denominator(){refresh();return heat.denominator();}
    void generate(BigDecimal amount){refresh();heat.generate(amount);save();}
    void remove(BigDecimal amount){refresh();heat.remove(amount);save();}
    private void save() {
        var data=owner.getHyperNetData();var tag=new CompoundTag();
        tag.putInt("version",1);tag.putString("machine",machine);tag.putString("heat",heat.stored().toPlainString());
        tag.putBoolean("overheated",heat.overheated());tag.putLong("lastTick",lastTick);
        data.put("thermal",tag);owner.setHyperNetData(data);
    }
}
