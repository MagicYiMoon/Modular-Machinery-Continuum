/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Native recipes with physical CPU/RAM/FE. Progress is checked while competitors are still running. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetFairVerification {
    private static MinecraftServer server; private static ServerLevel world;
    private static TileController center,processor,a,b; private static TileFactoryController factory;
    private static int tick,phase,since,held,other; private static boolean finished,failed,sawQueue;
    private static HyperNetNode oldCenter;
    public static void begin(MinecraftServer srv,ServerLevel level,TileController c,TileController p) {
        server=srv;world=level;center=c;processor=p;
        try {
            var machine=MachineReloadListener.getLoader().findByName(c.getMachineName());var n=machine.getHyperNetDefinition();
            require(!n.fairScheduling(),"legacy center default changed");
            machine.setHyperNetDefinition(new HyperNetDefinition(n.role(),n.workingThread(),n.carrying(),n.maxConnections(),n.maxNodesPerType(),n.modules(),n.connectionCard(),n.thermal(),n.circuit(),true));
            definition("hf_a",Blocks.EMERALD_BLOCK,false);definition("hf_b",Blocks.LAPIS_BLOCK,false);definition("hf_factory",Blocks.QUARTZ_BLOCK,true);
            recipe("hf_a","hf_a",Items.DIRT,Items.DIAMOND);recipe("hf_b","hf_b",Items.DIRT,Items.EMERALD);
            recipe("hf_factory","hf_fa",Items.DIRT,Items.APPLE);recipe("hf_factory","hf_fb",Items.COBBLESTONE,Items.CARROT);
            MachineReloadListener.refreshRecipes();
            var pos=new BlockPos(2028,120,2508);
            a=build(pos,"hf_a",Blocks.EMERALD_BLOCK,false);b=build(pos.east(80),"hf_b",Blocks.LAPIS_BLOCK,false);
            factory=(TileFactoryController)build(pos.east(160),"hf_factory",Blocks.QUARTZ_BLOCK,true);
            next(0);
        } catch(Throwable e) { finish(e); }
    }
    private static void definition(String name,Block cap,boolean factory)throws Exception {
        String id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(cap).toString();
        var path=Files.createTempDirectory("mmc-fair-").resolve(name+".json");
        Files.writeString(path,"{\"registryname\":\""+name+"\",\"requires-blueprint\":false,\"has-factory\":"+factory+",\"failure-action\":\"still\",\"parts\":["
                +"{\"y\":-1,\"elements\":\"modularmachinery:blockenergyinputhatch\"},{\"y\":1,\"elements\":\""+id+"\"},"
                +"{\"y\":2,\"elements\":\"modularmachinery:blockinputbus\"},{\"y\":3,\"elements\":\"modularmachinery:blockoutputbus\"}],\"hypernet\":{\"role\":\"node\"}}");
        var machine=MachineReloadListener.getLoader().parseMachineFile(path.toFile()).orElseThrow();Files.delete(path);Files.delete(path.getParent());
        machine.setMaxThreads(2);
    }
    private static void recipe(String machine,String name,Item in,Item out) {
        var delayed=new RequirementItem(IOType.INPUT,"minecraft:stick",1);delayed.setTriggerTime(3);
        var recipe=new MachineRecipe(machine,name,120,List.of(new RequirementComputation(3,true),new RequirementEnergy(IOType.INPUT,1),
                new RequirementItem(IOType.INPUT,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(in),1),delayed,
                new RequirementItem(IOType.OUTPUT,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(out),1)),null,machine.equals("hf_factory"),-1,"");
        ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(recipe,Map.of()));
    }
    private static TileController build(BlockPos pos,String name,Block cap,boolean factory) {
        world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(),cap.defaultBlockState());
        world.setBlockAndUpdate(pos.above(2),RegistryBlocks.itemInputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(3),RegistryBlocks.itemOutputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos,(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get()).defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var controller=(TileController)world.getBlockEntity(pos);controller.setOwner(center.getOwner());controller.setMachineName(name);
        require(MachineFormation.tryForm(world,pos,Direction.NORTH,MachineReloadListener.getLoader().findByName(name),MachineReloadListener.getLoader()).formed(),"formation");
        controller.setFormed(true);controller.acknowledgeStructureCheck();energy(controller,1000);return controller;
    }
    private static TileEnergyHatch power(TileController controller) { return (TileEnergyHatch)world.getBlockEntity(controller.getBlockPos().below()); }
    private static void energy(TileController c,int target) {
        var hatch=power(c);
        while(hatch.getStoredEnergy()>target)require(hatch.consumeInternal(hatch.getStoredEnergy()-target,false)>0,"drain");
        while(hatch.getStoredEnergy()<target)require(hatch.produceInternal(target-hatch.getStoredEnergy(),false)>0,"fill");
    }
    private static IItemHandlerModifiable inv(TileController c,int y) { return (IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(c.getBlockPos().above(y))).getInventory(); }
    private static void supply(TileController c,Item item,int sticks) { inv(c,2).setStackInSlot(0,new ItemStack(item));inv(c,2).setStackInSlot(2,new ItemStack(Items.STICK,sticks)); }
    private static void connect(TileController c) { require(c.getHyperNetNode().connect(center.getBlockPos(),center.getOwner())==HyperNetNode.ConnectResult.SUCCESS,"connect"); }
    private static int output(TileController c,Item item) { int total=0;var inv=inv(c,3);for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(item))total+=inv.getStackInSlot(i).getCount();return total; }
    private static void next(int value) { phase=value;since=tick;Modularmachinery.LOG.info("[MMC][hypernet-fair-native] phase {} tick {}",phase,tick); }
    private static void require(boolean value,String message) { if(!value)throw new AssertionError(message); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(server==null||finished)return;
        try {
            if(event.phase==TickEvent.Phase.START) { energy(center,1000);energy(processor,1000);return; }
            tick++;require(tick<1600&&tick-since<400,"timeout phase "+phase);
            require(1000-power(processor).getStoredEnergy()<=58,"overspent physical module FE");
            switch(phase) {
                case 0 -> {
                    if(tick<8||!center.getHyperNetNode().working()||!processor.getHyperNetNode().working())return;
                    connect(processor);connect(a);connect(b);connect(factory);
                    if(a.getHyperNetNode().available()!=5)return;
                    require(center.getHyperNetNode().schedulingTickets()==0,"idle ticket");
                    var context=new MachineContext(world,a.getBlockPos(),Direction.NORTH,MachineReloadListener.getLoader().findByName("hf_a"),new MachineVariables(),null);
                    for(int i=0;i<50;i++)require(ComputationTickIO.plan(context,List.of(new RequirementComputation(3,true)),1).valid(),"candidate simulation");
                    require(center.getHyperNetNode().schedulingTickets()==0&&a.getHyperNetNode().available()==5,"simulation queued/charged");
                    supply(a,Items.DIRT,1);supply(b,Items.DIRT,1);a.requestNormalRecipeSearch();b.requestNormalRecipeSearch();next(1);
                }
                case 1 -> {
                    if(!a.hasActiveRecipe()||!b.hasActiveRecipe())return;
                    held=a.getRecipeTicks();other=b.getRecipeTicks();next(12);
                }
                case 12 -> {
                    if(tick%20==0)Modularmachinery.LOG.info("[MMC][hypernet-fair-native] ordinary a={}/{} b={}/{} tickets={}",a.getRecipeTicks(),a.getStatusMessage(),b.getRecipeTicks(),b.getStatusMessage(),center.getHyperNetNode().schedulingTickets());
                    sawQueue|=ComputationTickIO.QUEUED.equals(a.getStatusMessage())||ComputationTickIO.QUEUED.equals(b.getStatusMessage());
                    if(tick-since<60)return;
                    require(a.hasActiveRecipe()&&b.hasActiveRecipe()&&a.getRecipeTicks()>20&&b.getRecipeTicks()>20,"ordinary peer starved/cancelled");
                    require(Math.abs((a.getRecipeTicks()-held)-(b.getRecipeTicks()-other))<=2&&sawQueue,"ordinary unfair progress/queue status");
                    var context=new MachineContext(world,b.getBlockPos(),Direction.NORTH,MachineReloadListener.getLoader().findByName("hf_b"),new MachineVariables(),null);
                    int tickets=center.getHyperNetNode().schedulingTickets();double before=b.getHyperNetNode().available();int fe=power(processor).getStoredEnergy();
                    require(before==2,"expected contention remainder");
                    for(int i=0;i<50;i++)require(ComputationTickIO.startPlan(context,List.of(new RequirementComputation(3,true)),1).valid(),"spent tick offer denied fair admission");
                    require(center.getHyperNetNode().schedulingTickets()==tickets&&b.getHyperNetNode().available()==before&&power(processor).getStoredEnergy()==fe,"admission queued/charged");
                    held=a.getRecipeTicks();other=b.getRecipeTicks();energy(a,0);next(2);
                }
                case 2 -> {
                    require(a.hasActiveRecipe()&&a.getRecipeTicks()==held,"missing FE advanced/cancelled");
                    if(tick-since<8)return;
                    require(b.getRecipeTicks()>=other+6,"missing FE held peer reservation");energy(a,1000);next(3);
                }
                case 3 -> {
                    if(output(a,Items.DIAMOND)!=1||output(b,Items.EMERALD)!=1)return;
                    require(center.getHyperNetNode().schedulingTickets()==0,"finished runs retained tickets");
                    supply(factory,Items.DIRT,2);inv(factory,2).setStackInSlot(1,new ItemStack(Items.COBBLESTONE));factory.requestImmediateRecipeSearch();next(4);
                }
                case 4 -> {
                    var threads=factory.getFactoryRecipeThreadList().stream().filter(t->t.hasActiveRecipe()).toList();
                    if(threads.size()<2)return;
                    held=threads.get(0).getRecipeTicks();other=threads.get(1).getRecipeTicks();next(40);
                }
                case 40 -> {
                    if(tick%10==0)Modularmachinery.LOG.info("[MMC][hypernet-fair-native] factory {} tickets={} remaining={} sticks={}",factory.getFactoryRecipeThreadList().stream().map(t->t.getRecipeName()+":"+t.getRecipeTicks()+":"+t.getBlockedReason()+":"+t.computationTicket()).toList(),center.getHyperNetNode().schedulingTickets(),factory.getHyperNetNode().available(),inv(factory,2).getStackInSlot(2));
                    if(tick-since<60)return;
                    var threads=factory.getFactoryRecipeThreadList().stream().filter(t->t.hasActiveRecipe()).toList();
                    require(threads.size()==2&&threads.stream().allMatch(t->t.getRecipeTicks()>20&&t.getRecipeTicks()<120),"factory peer starved/cancelled: "+threads.stream().map(t->t.getRecipeName()+":"+t.getRecipeTicks()+":"+t.getBlockedReason()).toList());
                    require(Math.abs((threads.get(0).getRecipeTicks()-held)-(threads.get(1).getRecipeTicks()-other))<=2,"factory unfair progress");
                    Modularmachinery.LOG.info("[MMC][hypernet-fair-native] factory concurrent deltas {}/{}",threads.get(0).getRecipeTicks()-held,threads.get(1).getRecipeTicks()-other);
                    require(inv(factory,2).getStackInSlot(2).isEmpty(),"delayed materials not paid once per run");
                    held=threads.stream().mapToInt(t->t.getRecipeTicks()).sum();factory.getHyperNetNode().disconnect();
                    require(center.getHyperNetNode().schedulingTickets()==0,"disconnect kept tickets");next(5);
                }
                case 5 -> {
                    if(tick-since<4)return;
                    require(factory.getFactoryRecipeThreadList().stream().noneMatch(t->t.hasActiveRecipe()),"triggerFailure disconnect did not cancel");
                    supply(factory,Items.DIRT,2);inv(factory,2).setStackInSlot(1,new ItemStack(Items.COBBLESTONE));connect(factory);factory.requestImmediateRecipeSearch();next(6);
                }
                case 6 -> {
                    if(output(factory,Items.APPLE)!=1||output(factory,Items.CARROT)!=1)return;
                    require(center.getHyperNetNode().schedulingTickets()==0,"factory completion cleanup");
                    oldCenter=center.getHyperNetNode();MachineReloadListener.refreshRecipes();require(!oldCenter.working()&&oldCenter.isClosed()&&oldCenter.schedulingTickets()==0,"reload retained scheduler");finish(null);
                }
            }
        } catch(Throwable e) { finish(e); }
    }
    private static void finish(Throwable e) {
        if(finished)return;finished=true;failed=e!=null;
        if(e!=null)Modularmachinery.LOG.error("[MMC][hypernet-fair-native] FAIL phase "+phase,e);
        else Modularmachinery.LOG.info("[MMC][hypernet-fair-native] runtime PASS; awaiting shutdown");
        server.halt(false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if(event.getServer()!=server||failed)return;
        require(oldCenter!=null&&oldCenter.isClosed(),"shutdown cleanup");
        Modularmachinery.LOG.info("[MMC][hypernet-fair-native] OVERALL: PASS (ordinary/factory concurrent progress, triggerFailure queue pause, exact outputs, delayed inputs, native FE, candidate purity, missing FE release, disconnect/reload/shutdown)");
    }
}
