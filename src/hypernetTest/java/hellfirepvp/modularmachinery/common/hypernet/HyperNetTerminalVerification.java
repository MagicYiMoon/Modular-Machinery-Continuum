/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.menu.MenuHyperNetResearch;
import hellfirepvp.modularmachinery.common.network.HyperNetResearchPackets;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;

/** Companion to the real station fixture; uses physical hatches, a real written card and authenticated menus. */
public final class HyperNetTerminalVerification {
    private static int stage;private static TileHyperNetTerminal terminal,other;private static FakePlayer player;
    private static ItemStack card;private static MenuHyperNetResearch menu;private static Object identity;
    private static HyperNetResearchPackets.Request stale,heartbeatStart;private static java.math.BigDecimal points;
    private static void require(boolean v,String s){if(!v)throw new AssertionError("terminal stage "+stage+": "+s);}
    static void prepare()throws Exception{
        Files.copy(Path.of("../examples/neco-hypernet/hypernet_terminal.json"),ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve("hypernet_terminal.json"),StandardCopyOption.REPLACE_EXISTING);
    }
    private static TileHyperNetTerminal build(ServerLevel world,BlockPos pos,UUID actor){
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(),RegistryBlocks.blockCasing.get().defaultBlockState());
        world.setBlockAndUpdate(pos,RegistryBlocks.hypernetTerminal.get().defaultBlockState());
        var t=(TileHyperNetTerminal)world.getBlockEntity(pos);t.setOwner(actor);t.setMachineName("hypernet_terminal");t.refreshStructure();require(t.isFormed(),"formation");return t;
    }
    private static TileEnergyHatch power(TileHyperNetTerminal t){return (TileEnergyHatch)t.getLevel().getBlockEntity(t.getBlockPos().below());}
    private static void energy(TileHyperNetTerminal t,int n){var p=power(t);p.consumeInternal(p.getStoredEnergy(),false);p.produceInternal(n,false);require(p.getStoredEnergy()==n,"energy setup");}
    private static void open(){menu=MenuHyperNetResearch.forController(15,player.getInventory(),terminal,UUID.randomUUID());player.containerMenu=menu;menu.broadcastChanges();require(menu.snapshot()!=null,"snapshot");}
    private static HyperNetResearchPackets.Request request(int action,String selected){return new HyperNetResearchPackets.Request(menu.containerId,menu.session(),menu.snapshot().token(),action,0,"",selected);}
    private static void rejected(Runnable action){boolean rejected=false;try{action.run();}catch(RuntimeException expected){rejected=true;}require(rejected,"malformed packet accepted");}
    private static void metrics(ServerLevel world,TileController center,TileController processor,TileController station,boolean online){
        var view=HyperNetResearchTerminal.capture(terminal,player,90,0,"","").data();var m=view.network();
        require(m.tick()==Math.max(0,world.getGameTime()-1)&&m.online()==online,"network sample/online identity");
        if(online){
            var status=center.getHyperNetNode().status();var research=station.getHyperNetNode().status();
            double consumption=status.demanded()+(research.online()?research.consumed():0);
            require(m.consumed()==consumption&&m.offered()==status.offered()&&m.delivered()==status.consumed()
                    &&m.connections()==status.connections(),"source demand/supply/connections differs from authenticated center");
        }else require(m.consumed()==0&&m.offered()==0&&m.delivered()==0&&m.connections()==0,"offline network leaked old statistics");
        var tiles=List.of(terminal,center,processor,station);var tags=tiles.stream().map(TileController::getHyperNetData).toList();
        var powers=tiles.stream().map(t->((TileEnergyHatch)world.getBlockEntity(t.getBlockPos().below())).getStoredEnergy()).toList();
        for(int i=0;i<500;i++)require(HyperNetResearchTerminal.capture(terminal,player,91,0,"","").data().network().equals(m),"read changed metrics");
        for(int i=0;i<tiles.size();i++)require(tags.get(i).equals(tiles.get(i).getHyperNetData())&&powers.get(i)==((TileEnergyHatch)world.getBlockEntity(tiles.get(i).getBlockPos().below())).getStoredEnergy(),"statistics query mutated FE/NBT");
        var buf=new FriendlyByteBuf(Unpooled.buffer());try{view.write(buf);require(HyperNetResearchTerminal.Snapshot.read(buf).equals(view)&&!buf.isReadable(),"network snapshot codec");}finally{buf.release();}
    }
    private static void creativeChecks(ServerLevel world,TileController station,TileController database,UUID actor){
        var list=world.getServer().getPlayerList();var profile=player.getGameProfile();
        var mode=player.gameMode.getGameModeForPlayer();boolean op=list.isOp(profile);
        try{
            list.deop(profile);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);open();
            require(!menu.request(player,request(3,"station_root")),"survival creative packet admitted");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);open();require(!player.hasPermissions(2),"fixture still has OP");
            require(!menu.request(player,request(3,"station_root"))&&station.getHyperNetNode().provideCreativeResearchTask("station_root",player).endsWith("permission"),"non-OP creative entry admitted");
            list.op(profile);player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);open();
            require(!menu.request(player,request(3,"station_root")),"survival OP creative packet admitted");
            player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);require(player.hasPermissions(2),"fixture OP grant failed");
            var node=station.getHyperNetNode();var before=station.getHyperNetData();
            require(node.provideCreativeResearchTask("station_beta",player).endsWith("dependency"),"creative bypassed dependencies");
            require(node.provideCreativeResearchTask("station_high",player).endsWith("tech"),"creative bypassed station tier");
            require(before.equals(station.getHyperNetData()),"rejected creative request mutated task");
            open();var valid=request(3,"station_root");
            var buf=new FriendlyByteBuf(Unpooled.buffer());try{HyperNetResearchPackets.Request.encode(valid,buf);require(HyperNetResearchPackets.Request.decode(buf).equals(valid),"creative request codec");}finally{buf.release();}
            require(!menu.request(player,new HyperNetResearchPackets.Request(menu.containerId,UUID.randomUUID(),valid.token(),3,0,"","station_root")),"foreign creative session");
            open();valid=request(3,"station_root");
            int stored=power(terminal).getStoredEnergy();
            require(menu.request(player,valid)&&!menu.request(player,valid),"creative start/replay protection");
            require(node.researchTask().progress().compareTo(java.math.BigDecimal.valueOf(100))==0&&!node.researchTask().awaitingCoreCompletion(),"creative did not queue full points");
            require(database.getHyperNetNode().researchFailure(List.of("station_root"))!=null&&stored==power(terminal).getStoredEnergy(),"creative directly unlocked/billed computation");
            var full=station.getHyperNetData();require(full.getCompound("researchTask").getString("progress").equals("100"),"creative points not persisted");
            require(node.provideCreativeResearchTask("station_beta",player).endsWith("busy")&&full.equals(station.getHyperNetData()),"creative overwrote active task");
            station.load(station.saveWithoutMetadata());
            require(node.isClosed()&&station.getHyperNetNode().researchTask().progress().compareTo(java.math.BigDecimal.valueOf(100))==0
                    &&!station.getHyperNetNode().researchTask().awaitingCoreCompletion(),"pending full points lost/committed on real load");
            require(database.getHyperNetNode().researchFailure(List.of("station_root"))!=null,"reload directly completed creative task");
            require(station.getHyperNetNode().cancelResearchTask(actor),"creative task cleanup");
            Modularmachinery.LOG.info("[MMC][research-creative-server] PASS: real mode/OP grants, forbidden packet and API, dependency/tier, session/replay, persisted full points, no direct database completion and no task overwrite");
        }finally{if(!op)list.deop(profile);else list.op(profile);player.setGameMode(mode);player.containerMenu=player.inventoryMenu;}
    }
    static boolean verify(ServerLevel world,TileController center,TileController processor,TileController database,TileController station,UUID actor)throws Exception{
        if(stage==16)return true;
        switch(stage){
            case 0->{
                require(DedicatedControllers.registrationDefinitions().stream().anyMatch(d->d.name().equals("hypernet_terminal")&&!d.factory()),"fixed terminal manifest on startup");
                require(DedicatedControllers.stack("hypernet_terminal",true).isEmpty(),"nonexistent terminal factory variant");
                player=FakePlayerFactory.get(world,new GameProfile(actor,"TerminalTest"));
                terminal=build(world,center.getBlockPos().south(5),actor);other=build(world,center.getBlockPos().south(5).east(4),actor);
                player.setPos(terminal.getBlockPos().getX()+.5,terminal.getBlockPos().getY(),terminal.getBlockPos().getZ()+1.5);
                card=new ItemStack(Items.PAPER,2);player.setItemInHand(InteractionHand.MAIN_HAND,card);
                var e=new PlayerInteractEvent.RightClickBlock(player,InteractionHand.MAIN_HAND,center.getBlockPos(),new BlockHitResult(Vec3.atCenterOf(center.getBlockPos()),Direction.NORTH,center.getBlockPos(),false));
                MinecraftForge.EVENT_BUS.post(e);require(e.isCanceled()&&card.hasTag(),"real card gesture");
                player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);
            }
            case 1->{
                require(power(terminal).getStoredEnergy()==9800&&terminal.servicePowered(),"disconnected 200 FE/t");
                open();require(menu.slots.size()==37&&menu.slots.get(36).x==159&&menu.slots.get(36).y==226,"native card/player slot coordinates");
                metrics(world,center,processor,station,false);
                int fe=power(terminal).getStoredEnergy();var data=terminal.getHyperNetData();
                for(int i=0;i<500;i++)HyperNetResearchTerminal.capture(terminal,player,1,0,"","");
                require(fe==power(terminal).getStoredEnergy()&&data.equals(terminal.getHyperNetData()),"view has billing/writes");
                var b=new FriendlyByteBuf(Unpooled.buffer());try{menu.snapshot().write(b);require(HyperNetResearchTerminal.Snapshot.read(b).equals(menu.snapshot()),"snapshot codec");}finally{b.release();}
                var notice=new HyperNetResearchPackets.Complete("研究完成".repeat(64),"&b中文研究 / Completed "+"A".repeat(200),"minecraft:iron_ingot");
                var noticeBuffer=new FriendlyByteBuf(Unpooled.buffer());try{HyperNetResearchPackets.Complete.encode(notice,noticeBuffer);require(HyperNetResearchPackets.Complete.decode(noticeBuffer).equals(notice)&&!noticeBuffer.isReadable(),"completion bounded UTF codec");}finally{noticeBuffer.release();}
                rejected(()->new HyperNetResearchPackets.Complete("", "name", "minecraft:paper"));
                rejected(()->new HyperNetResearchPackets.Complete("valid", "x".repeat(257), "minecraft:paper"));
                rejected(()->new HyperNetResearchPackets.Complete("valid", "name", "INVALID ITEM"));
                var badNotice=new FriendlyByteBuf(Unpooled.buffer());try{badNotice.writeUtf("valid");badNotice.writeUtf("x".repeat(257));badNotice.writeUtf("minecraft:paper");rejected(()->HyperNetResearchPackets.Complete.decode(badNotice));}finally{badNotice.release();}
                for(double bad:List.of(Double.NaN,Double.POSITIVE_INFINITY,-1D)){
                    rejected(()->new HyperNetResearchTerminal.Network(0,true,bad,1,0,0));
                    rejected(()->new HyperNetResearchTerminal.Network(0,true,0,bad,0,0));
                    rejected(()->new HyperNetResearchTerminal.Network(0,true,0,1,0,bad));
                }
                rejected(()->new HyperNetResearchTerminal.Network(-1,true,0,1,0,0));
                rejected(()->new HyperNetResearchTerminal.Network(0,true,0,1,-1,0));
                rejected(()->new HyperNetResearchTerminal.Network(0,false,0,1,0,0));
                var malformed=new FriendlyByteBuf(Unpooled.buffer());try{
                    menu.snapshot().write(malformed);malformed.setDouble(malformed.writerIndex()-8,Double.NaN);
                    rejected(()->HyperNetResearchTerminal.Snapshot.read(malformed));
                }finally{malformed.release();}
                rejected(()->new HyperNetResearchPackets.Request(15,menu.session(),1,4,0,"",""));
                rejected(()->new HyperNetResearchPackets.Request(15,menu.session(),1,0,456,"",""));
                rejected(()->new HyperNetResearchPackets.Request(15,menu.session(),1,0,0,"\n",""));
                var oversized=new FriendlyByteBuf(Unpooled.buffer(HyperNetResearchTerminal.MAX_PACKET+1));try{oversized.writeZero(HyperNetResearchTerminal.MAX_PACKET+1);rejected(()->HyperNetResearchTerminal.Snapshot.read(oversized));}finally{oversized.release();}
                player.getInventory().setItem(9,card.copy());require(!menu.quickMoveStack(player,0).isEmpty()&&terminal.cards().getStackInSlot(0).getCount()==1&&player.getInventory().getItem(9).getCount()==1,"shift insert only one card");
                other.cards().setStackInSlot(0,card.copyWithCount(1));
            }
            case 2->{require(HyperNetResearchTerminal.center(terminal,player)==center.getHyperNetNode(),"card connects");require(power(terminal).getStoredEnergy()==9800,"connect tick uses offline cost");}
            case 3->{require(power(terminal).getStoredEnergy()==8000,"connected 2000 FE/t");require(!other.getHyperNetNode().terminalConnected(),"singleton terminal");
                require(!menu.quickMoveStack(player,36).isEmpty()&&terminal.cards().getStackInSlot(0).isEmpty(),"shift card back to player");other.cards().setStackInSlot(0,ItemStack.EMPTY);}
            case 4->{require(HyperNetResearchTerminal.center(terminal,player)==null,"card removal disconnects");metrics(world,center,processor,station,false);terminal.cards().setStackInSlot(0,card.copyWithCount(1));}
            case 5->{require(power(terminal).getStoredEnergy()==199&&!terminal.servicePowered(),"199 FE cannot pay offline cost, no partial debit");}
            case 6->{require(power(terminal).getStoredEnergy()==0&&terminal.servicePowered()&&terminal.getHyperNetNode().terminalConnected(),"exact 200 and reconnect");}
            case 7->{require(power(terminal).getStoredEnergy()==1999&&!terminal.servicePowered()&&!terminal.getHyperNetNode().terminalConnected(),"1999 FE cannot pay connected cost");}
            case 8->{
                // Source polls retained cards every twenty ticks after a power interruption.
                if(terminal.servicePowered()&&!terminal.getHyperNetNode().terminalConnected())return false;
                require(terminal.servicePowered()&&terminal.getHyperNetNode().terminalConnected(),"power/card recovery");open();metrics(world,center,processor,station,true);
                var high=HyperNetResearchTerminal.capture(terminal,player,2,0,"station_high","").data();require(high.selected().row().state()==HyperNetResearchTerminal.State.UNAVAILABLE&&high.selected().row().reason().endsWith("tech"),"tier gate shown "+high);
                require(menu.request(player,request(0,"station_root")),"select root");
                heartbeatStart=request(1,"station_root");
            }
            case 9->{menu.broadcastChanges();require(menu.snapshot().selected().row().id().equals("station_root"),"selection persisted");
                require(heartbeatStart.token()!=menu.snapshot().token(),"heartbeat must have advanced");
                var start=heartbeatStart;require(menu.request(player,start),"start from preceding in-flight heartbeat");require(!menu.request(player,start),"duplicate start");
                identity=station.getHyperNetNode().inspectionResearchTaskIdentity();world.setBlockAndUpdate(station.getBlockPos().east(),Blocks.AIR.defaultBlockState());}
            case 10->{require(station.getHyperNetNode().researchTask().progress().signum()>0,"real research work");require(identity==station.getHyperNetNode().inspectionResearchTaskIdentity(),"ordinary work invalidated identity");
                metrics(world,center,processor,station,true);require(HyperNetResearchTerminal.capture(terminal,player,92,0,"","").data().network().offered()>0,"missing real hardware offer");
                open();stale=request(2,"station_root");points=station.getHyperNetNode().researchTask().progress();
                require(station.getHyperNetNode().cancelResearchTask(actor)&&station.getHyperNetNode().provideResearchTask("station_root",actor)==null,"ABA replacement");
                require(!menu.request(player,stale)&&station.getHyperNetNode().researchTask().id().equals("station_root"),"old cancel hit same-id new task");}
            case 11->{open();require(menu.request(player,request(2,"station_root")),"current cancel");require(station.getHyperNetNode().researchTask().id().isEmpty()&&database.getHyperNetNode().researchProgress("station_root").compareTo(points)>=0,"cancel keeps earned progress");
                open();var request=request(1,"station_root");require(!menu.request(player,new HyperNetResearchPackets.Request(15,UUID.randomUUID(),request.token(),1,0,"","station_root")),"foreign session");
                player.setPos(terminal.getBlockPos().getX()+50,120,terminal.getBlockPos().getZ());require(!menu.request(player,request),"out-of-range request");
                player.setPos(terminal.getBlockPos().getX()+.5,terminal.getBlockPos().getY(),terminal.getBlockPos().getZ()+1.5);
                terminal.setOwner(UUID.randomUUID());require(!menu.stillValid(player)&&!menu.request(player,request),"changed owner");terminal.setOwner(actor);
                player.containerMenu=player.inventoryMenu;require(!menu.request(player,request),"closed window");
                creativeChecks(world,station,database,actor);
            }
            case 12->{
                open();terminal.cards().setStackInSlot(0,card.copyWithCount(1));var old=terminal.getHyperNetNode();
                var saved=terminal.saveWithoutMetadata();terminal.load(saved);require(old.isClosed()&&ItemStack.isSameItemSameTags(terminal.cards().getStackInSlot(0),card)&&!terminal.getUpdateTag().contains("hypernetTerminalCard"),"load cleanup/private card");
            }
            case 13->{require(terminal.isFormed()&&terminal.servicePowered(),"load rebind");world.setBlockAndUpdate(terminal.getBlockPos().above(),Blocks.AIR.defaultBlockState());terminal.requestStructureCheck();}
            case 14->{require(!terminal.isFormed()&&!terminal.servicePowered(),"broken structure stopped service");world.setBlockAndUpdate(terminal.getBlockPos().above(),RegistryBlocks.blockCasing.get().defaultBlockState());terminal.requestStructureCheck();}
            case 15->{require(terminal.isFormed()&&terminal.servicePowered(),"rebuilt structure resumes");
                player.containerMenu=player.inventoryMenu;
                terminal.cards().setStackInSlot(0,card.copyWithCount(1));var box=new net.minecraft.world.phys.AABB(terminal.getBlockPos()).inflate(2);
                int before=world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).size();world.setBlockAndUpdate(terminal.getBlockPos(),Blocks.AIR.defaultBlockState());
                require(world.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,box).size()==before+1&&!menu.stillValid(player),"removal drops card/closes menu");
                ((net.minecraftforge.items.IItemHandlerModifiable)((TileItemInputBus)world.getBlockEntity(station.getBlockPos().above(5))).getInventory()).setStackInSlot(0,new ItemStack(Items.PAPER,64));
                Modularmachinery.LOG.info("[MMC][hypernet-terminal] OVERALL: PASS (physical structure/card singleton, exact offline/online FE and recovery, native slots, bounded codec, read-only capture, menu start/cancel, same-id ABA and progress identity, range/session/owner/closed rejection, private load/rebind, break/rebuild, card drop)");
            }
        }
        stage++;if(stage<16){energy(terminal,stage==5?199:stage==6?200:stage==7?1999:10000);energy(other,10000);}
        return stage==16;
    }
}
