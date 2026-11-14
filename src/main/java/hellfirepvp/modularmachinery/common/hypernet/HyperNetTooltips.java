/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.upgrade.RegistryMachineUpgrade;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetTooltips {
    private HyperNetTooltips(){}
    public static String number(double value){return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toString();}
    public static void append(DynamicMachine machine,List<Component> lines){
        machine.getDisplayInfo().append(lines);var d=machine.getHyperNetDefinition();if(d==null)return;
        lines.add(Component.translatable("tooltip.modularmachinery.hypernet.role."+d.role().name().toLowerCase(Locale.ROOT)));
        if(d.role()==HyperNetDefinition.Role.CENTER)lines.add(Component.translatable("tooltip.modularmachinery.hypernet.limits",number(d.carrying()),d.maxConnections()));
        if(d.thermal()!=null)lines.add(Component.translatable("tooltip.modularmachinery.hypernet.thermal",d.thermal().overheatThreshold(),d.thermal().heatDistribution()));
        if(d.circuit()!=null)lines.add(Component.translatable("tooltip.modularmachinery.hypernet.circuit",d.circuit().capacity()));
    }
    @SubscribeEvent public static void modules(ItemTooltipEvent event){
        var loader=MachineReloadListener.getLoader();if(loader==null||event.getItemStack().isEmpty())return;
        var types=new HashSet<String>();RegistryMachineUpgrade.upgradesFor(event.getItemStack()).forEach(u->types.add(u.getType().getName()));
        if(types.isEmpty())return;
        // A mapping is machine-specific. Show its machine name rather than imply a global module tier.
        var seen=new HashSet<String>();
        for(var machine:loader.all()){
            var d=machine.getHyperNetDefinition();if(d==null)continue;
            for(var module:d.modules())if(types.contains(module.upgrade())){
                String key=machine.getRegistryName()+"/"+module.upgrade();if(!seen.add(key))continue;
                event.getToolTip().add(Component.translatable("tooltip.modularmachinery.hypernet.module",machine.getLocalizedName(),module.kind().name(),number(module.capacity()),module.energy()));
                module.displayInfo().append(event.getToolTip());
            }
        }
    }
}
