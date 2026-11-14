/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.*;
import hellfirepvp.modularmachinery.common.network.PktHyperNetMonitor;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import java.util.*;
import static hellfirepvp.modularmachinery.common.hypernet.HyperNetMonitor.*;

/** Packet/session bounds and real controller lifetime/access checks. Paid network work is tested separately. */
public final class HyperNetMonitorRegressionChecks {
    public static void run(ServerLevel world,BlockPos pos){
        var id=new ResourceLocation("modularmachinery","hypernet_monitor_fixture");var definitions=MachineReloadListener.getLoader().getMachines();var previous=definitions.get(id);
        var player=new FakePlayer(world,new GameProfile(UUID.randomUUID(),"HNMonitor")){@Override public boolean hasPermissions(int n){return false;}};
        var operator=new FakePlayer(world,new GameProfile(UUID.randomUUID(),"HNMonitorOP")){@Override public boolean hasPermissions(int n){return true;}};
        var stranger=new FakePlayer(world,new GameProfile(UUID.randomUUID(),"HNMonitorOther")){@Override public boolean hasPermissions(int n){return false;}};
        var oldMenu=player.containerMenu;
        try{
            var machine=new DynamicMachine(id.getPath(),"Monitor fixture",new MachinePattern());
            machine.setHyperNetDefinition(new HyperNetDefinition(HyperNetDefinition.Role.NODE,"",0,0,0));definitions.put(id,machine);
            world.setBlockAndUpdate(pos,RegistryBlocks.blockController.get().defaultBlockState());var owner=(TileController)world.getBlockEntity(pos);
            owner.setMachineName(id.getPath());owner.setOwner(player.getUUID());player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2);
            var before=owner.getHyperNetData();var data=capture(owner,player,999);
            require(data.local().fault()==Fault.UNFORMED&&!data.networkVisible()&&data.nodes().isEmpty()&&data.page()==0,"unformed local diagnostic");
            require(owner.peekHyperNetNode()==null&&owner.getHyperNetData().equals(before),"monitor created runtime/private state");
            require(!canRead(owner,stranger)&&canRead(owner,operator),"controller ownership/OP read permission");
            try{capture(owner,stranger,0);throw new AssertionError("foreign capture allowed");}catch(IllegalArgumentException expected){}
            var session=UUID.randomUUID();var client=new MenuHyperNetMonitor(4,player.getInventory(),pos,session);
            var b=new FriendlyByteBuf(Unpooled.buffer());
            try{
                PktHyperNetMonitor.encode(new PktHyperNetMonitor(4,session,data),b);var decoded=PktHyperNetMonitor.decode(b);
                require(decoded.data().equals(data)&&b.readableBytes()==0,"native codec round trip");
                client.accept(UUID.randomUUID(),decoded.data());require(client.snapshot()==null,"foreign session accepted");
                client.accept(session,decoded.data());require(client.snapshot().equals(data),"current session lost");
            }finally{b.release();}
            var example=new Node(new Member(pos,"fixture","&bFixture","&eT2",HyperNetDefinition.Role.NODE),true,Fault.ONLINE,"",.125,1e200,Double.MAX_VALUE,0,0,0,0,.375,2);
            var page=new Snapshot(Long.MAX_VALUE,example,example,true,2,4,7,1,false,List.of(example));
            b=new FriendlyByteBuf(Unpooled.buffer());try{page.write(b);require(Snapshot.read(b).equals(page),"fraction/exponent/tick transport");}finally{b.release();}
            try{new Snapshot(0,example,null,false,0,0,7,0,false,Collections.nCopies(7,example));throw new AssertionError("unbounded page accepted");}catch(IllegalArgumentException expected){}
            try{new Node(example.member(),true,Fault.ONLINE,"",Double.NaN,0,0,0,0,0,0);throw new AssertionError("NaN accepted");}catch(IllegalArgumentException expected){}
            try{new Node(example.member(),true,Fault.ONLINE,"",0,0,0,0,0,0,0,Double.POSITIVE_INFINITY,1);throw new AssertionError("infinite demand accepted");}catch(IllegalArgumentException expected){}
            try{new Node(example.member(),true,Fault.ONLINE,"",0,0,0,0,0,0,0,1,-1);throw new AssertionError("negative consumer count accepted");}catch(IllegalArgumentException expected){}
            b=new FriendlyByteBuf(Unpooled.buffer());try{
                // A malicious list length is checked before allocating its entries.
                var safe=new Snapshot(0,example,null,false,0,0,0,0,false,List.of());safe.write(b);b.setByte(b.writerIndex()-1,127);
                try{Snapshot.read(b);throw new AssertionError("unbounded wire page accepted");}catch(IllegalArgumentException expected){}
            }finally{b.release();}
            var menu=MenuHyperNetMonitor.forController(4,player.getInventory(),owner,session);player.containerMenu=menu;menu.broadcastChanges();
            require(menu.stillValid(player)&&menu.slots.isEmpty()&&menu.quickMoveStack(player,0).isEmpty(),"slot-free physical menu");
            require(!menu.clickMenuButton(player,-1)&&!menu.clickMenuButton(player,3)&&!menu.clickMenuButton(stranger,0),"foreign/invalid menu action");
            player.containerMenu=oldMenu;require(!menu.clickMenuButton(player,0),"closed menu action");player.containerMenu=menu;
            player.setPos(pos.getX()+20,pos.getY(),pos.getZ());require(!menu.stillValid(player)&&!menu.clickMenuButton(player,0),"distance guard");
            player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2);owner.setMachineName("other");require(!menu.stillValid(player),"rebound controller retained monitor");
            owner.setMachineName(id.getPath());owner.setOwner(stranger.getUUID());require(!menu.stillValid(player),"changed owner retained monitor");
            owner.setOwner(player.getUUID());world.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());require(!menu.stillValid(player),"removed controller retained monitor");
            Modularmachinery.LOG.info("[MMC][hypernet-monitor] PASS: real controller/slot-free menu, ownership/OP access, no runtime or NBT creation, bounded native codec, decimals/large values, session/closed/distance/rebind/remove guards");
        }finally{player.containerMenu=oldMenu;world.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());if(previous==null)definitions.remove(id);else definitions.put(id,previous);}
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError("HyperNet monitor: "+message);}
}
