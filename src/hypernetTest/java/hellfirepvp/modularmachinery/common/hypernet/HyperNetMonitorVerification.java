/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.menu.MenuHyperNetMonitor;
import net.minecraft.core.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import java.util.*;
import static hellfirepvp.modularmachinery.common.hypernet.HyperNetMonitor.*;

/** Reads the actual paid CPU/RAM fixture, then exercises bounded views and temporary node faults. */
final class HyperNetMonitorVerification {
    private static MenuHyperNetMonitor oldWindow;
    static void run(TileController center,TileController processor,TileController consumer,Player viewer){
        var world=(ServerLevel)center.getLevel();var position=viewer.position();var originalMenu=viewer.containerMenu;
        var saved=List.of(center.getHyperNetData(),processor.getHyperNetData(),consumer.getHyperNetData());
        var runtime=center.peekHyperNetNode();var status=runtime.status();var nodes=new ArrayList<TileController>();
        try{
            var data=capture(center,viewer,0);require(data.center().consumed()==7&&data.total()==2,"paid center/roster");
            require(capture(processor,viewer,0).local().consumed()==7&&capture(consumer,viewer,0).local().consumed()==7,"paid role quantities");
            for(int i=0;i<20;i++)capture(center,viewer,0);
            require(saved.get(0).equals(center.getHyperNetData())&&saved.get(1).equals(processor.getHyperNetData())&&saved.get(2).equals(consumer.getHyperNetData())
                    &&status.equals(runtime.status()),"read changed state or budget");
            for(int i=0;i<5;i++){
                var pos=center.getBlockPos().west((i+1)*2).north(4);
                world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());
                world.setBlockAndUpdate(pos.above(),Blocks.GOLD_BLOCK.defaultBlockState());
                world.setBlockAndUpdate(pos,RegistryBlocks.blockController.get().defaultBlockState());
                var node=(TileController)world.getBlockEntity(pos);node.setOwner(viewer.getUUID());node.setMachineName(consumer.getMachineName());nodes.add(node);
                require(MachineFormation.tryForm(world,pos,Direction.NORTH,MachineReloadListener.getLoader().findByName(node.getMachineName()),MachineReloadListener.getLoader()).formed(),"page fixture formation");
                node.setFormed(true);node.acknowledgeStructureCheck();
                require(node.getHyperNetNode().connect(center.getBlockPos(),viewer.getUUID())==HyperNetNode.ConnectResult.SUCCESS,"page fixture connection");
            }
            var first=capture(center,viewer,0);var second=capture(center,viewer,1);
            require(first.total()==7&&first.nodes().size()==6&&second.nodes().size()==1&&first.pages()==2,"physical seven-node pages");
            require(capture(center,viewer,Integer.MAX_VALUE).page()==1&&capture(center,viewer,-5).page()==0,"page clamps");
            var peer=nodes.get(0);peer.setFormed(false);
            require(capture(center,viewer,0).nodes().stream().anyMatch(n->n.member().pos().equals(peer.getBlockPos())&&n.fault()==Fault.UNFORMED),"unformed registered peer disappeared");
            var far=new BlockPos(999000,120,999000);var privateData=consumer.getHyperNetData();var edited=privateData.copy();edited.getCompound("binding").putLong("pos",far.asLong());consumer.setHyperNetData(edited);
            try{require(capture(consumer,viewer,0).local().fault()==Fault.CENTER_UNAVAILABLE&&world.getChunkSource().getChunkNow(far.getX()>>4,far.getZ()>>4)==null,"monitor loaded absent center chunk");}
            finally{consumer.setHyperNetData(privateData);}
            viewer.setPos(center.getBlockPos().getX()+.5,center.getBlockPos().getY(),center.getBlockPos().getZ()+2);
            oldWindow=MenuHyperNetMonitor.forController(88,viewer.getInventory(),center,UUID.randomUUID());viewer.containerMenu=oldWindow;oldWindow.broadcastChanges();
            require(oldWindow.stillValid(viewer)&&oldWindow.snapshot().total()==7&&oldWindow.slots.isEmpty(),"real connected menu");
            require(oldWindow.clickMenuButton(viewer,1),"real page request");
            Modularmachinery.LOG.info("[MMC][hypernet-monitor-network] PASS: actual 7-point paid role samples, repeated reads, seven formed/connected peers over two pages, local/unformed/absent-center diagnostics, no chunk load, slot-free menu");
        }finally{
            viewer.containerMenu=originalMenu;viewer.setPos(position.x,position.y,position.z);
            for(var node:nodes){var existing=node.peekHyperNetNode();if(existing!=null)existing.disconnect();for(int y=-1;y<=1;y++)world.setBlockAndUpdate(node.getBlockPos().above(y),Blocks.AIR.defaultBlockState());}
        }
    }
    static void afterReload(TileController center,Player viewer){
        var position=viewer.position();try{
            viewer.setPos(center.getBlockPos().getX()+.5,center.getBlockPos().getY(),center.getBlockPos().getZ()+2);
            require(oldWindow!=null&&!oldWindow.stillValid(viewer),"old monitor survived definition revision");
            Modularmachinery.LOG.info("[MMC][hypernet-monitor-reload] PASS: old session invalidated by actual definition revision");
        }finally{viewer.setPos(position.x,position.y,position.z);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError("HyperNet monitor network: "+message);}
}
