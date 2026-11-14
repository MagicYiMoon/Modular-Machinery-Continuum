/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.nio.file.*;
import java.util.*;

/** Real short core cycles, material/FE conservation and genuine stop conditions. Opt-in only. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetCoreCycleVerification {
    private static MinecraftServer server; private static ServerLevel world;
    private static TileController c,p,a,f;
    private static int tick,phase,since,cFin,pFin,cStart,pStart,baseC,baseP;
    private static int cEnds,pEnds; private static boolean endDueC,endDueP; private static String eventFault;
    private static boolean finished,failed,feedCenter=true,feedProcessor=true;
    private static HyperNetNode oldC,oldP;
    private static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
    private static void next(int value){phase=value;since=tick;Modularmachinery.LOG.info("[MMC][hypernet-core-cycle] phase {} tick {}",phase,tick);}
    public static void begin(MinecraftServer srv,ServerLevel level){
        server=srv;world=level;
        try{
            definition("hc_center","center","stone");definition("hc_processor","processor","iron_block");
            definition("hc_normal","node","emerald_block");definition("hc_factory","node","lapis_block");
            coreRecipe("hc_center",1,HyperNetDefinition.CENTER_THREAD);
            coreRecipe("hc_processor",2,HyperNetDefinition.PROCESSOR_THREAD);
            consumerRecipe("hc_normal",Items.DIAMOND);consumerRecipe("hc_factory",Items.EMERALD);
            MachineReloadListener.refreshRecipes();
            // Consumers are inserted first, so their next tick may precede the provider's next core tick.
            a=build(new BlockPos(2028,120,2748),"hc_normal",false,Blocks.EMERALD_BLOCK);
            f=build(a.getBlockPos().east(80),"hc_factory",true,Blocks.LAPIS_BLOCK);
            c=build(f.getBlockPos().east(80),"hc_center",true,Blocks.STONE);
            p=build(c.getBlockPos().east(80),"hc_processor",true,Blocks.IRON_BLOCK);
            var bus=(IItemHandlerModifiable)((TileUpgradeBus)world.getBlockEntity(p.getBlockPos().above(2))).getUpgradeInventory();
            bus.setStackInSlot(0,new ItemStack(Items.DIAMOND));bus.setStackInSlot(1,new ItemStack(Items.EMERALD));bus.setStackInSlot(2,new ItemStack(Items.REDSTONE));
            stock(c);stock(p);next(0);
        }catch(Throwable e){finish(e);}
    }
    private static void definition(String name,String role,String cap)throws Exception{
        String profile=role.equals("center")?"{\"role\":\"center\",\"carrying\":5,\"max-connections\":8,\"fair-scheduling\":"+Boolean.getBoolean("modularmachinery.hypernetFairTest")+"}":role.equals("node")?"{\"role\":\"node\"}":
                "{\"role\":\"processor\",\"modules\":[{\"upgrade\":\"hn_cpu\",\"kind\":\"cpu\",\"capacity\":2,\"energy\":20},{\"upgrade\":\"hn_gpu\",\"kind\":\"gpu\",\"capacity\":3,\"energy\":30},{\"upgrade\":\"hn_ram\",\"kind\":\"ram\",\"capacity\":8,\"energy\":8}]}";
        String parts="{\"y\":-1,\"elements\":\"modularmachinery:blockenergyinputhatch\"},{\"y\":1,\"elements\":\"minecraft:"+cap+"\"},{\"y\":5,\"elements\":\"modularmachinery:blockinputbus\"},{\"y\":7,\"elements\":\"modularmachinery:blockoutputbus\"}";
        if(role.equals("processor"))parts+=",{\"y\":2,\"elements\":\"modularmachinery:blockupgradebus\"}";
        var path=Files.createTempDirectory("mmc-core-cycle-").resolve(name+".json");
        Files.writeString(path,"{\"registryname\":\""+name+"\",\"requires-blueprint\":false,\"has-factory\":true,\"parts\":["+parts+"],\"hypernet\":"+profile+"}");
        var machine=MachineReloadListener.getLoader().parseMachineFile(path.toFile()).orElseThrow();Files.delete(path);Files.delete(path.getParent());
        if(!role.equals("node"))machine.addCoreThreadPreset(role.equals("center")?HyperNetDefinition.CENTER_THREAD:HyperNetDefinition.PROCESSOR_THREAD,List.of(name+"_work"));
    }
    private static void coreRecipe(String name,int duration,String thread){
        var recipe=new MachineRecipe(name,name+"_work",duration,List.of(new RequirementEnergy(IOType.INPUT,1),new RequirementItem(IOType.INPUT,"minecraft:cobblestone",1),new RequirementItem(IOType.OUTPUT,"minecraft:stone",1)),null,false,1,thread);
        ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(recipe,Map.of()));
        var handlers=new HashMap<Class<?>,List<java.util.function.Consumer<?>>>();
        handlers.put(FactoryRecipeStartEvent.class,List.of((java.util.function.Consumer<FactoryRecipeStartEvent>)HyperNetCoreCycleVerification::started));
        handlers.put(FactoryRecipeFinishEvent.class,List.of((java.util.function.Consumer<FactoryRecipeFinishEvent>)HyperNetCoreCycleVerification::completed));
        handlers.put(FactoryRecipeTickEvent.class,List.of((java.util.function.Consumer<FactoryRecipeTickEvent>)HyperNetCoreCycleVerification::tickEnd));
        ScriptRecipeRegistry.registerHandlers(name+"_work",handlers);
    }
    private static void consumerRecipe(String name,Item out){
        boolean fair=Boolean.getBoolean("modularmachinery.hypernetFairTest");
        var recipe=new MachineRecipe(name,name+"_work",fair?40:80,List.of(new RequirementComputation(fair?3:1,true),new RequirementItem(IOType.INPUT,"minecraft:dirt",1),new RequirementItem(IOType.OUTPUT,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(out),1)),null,true,1,"");
        ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(recipe,Map.of()));
    }
    private static TileController build(BlockPos pos,String name,boolean factory,net.minecraft.world.level.block.Block cap){
        world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(),cap.defaultBlockState());
        if(name.equals("hc_processor"))world.setBlockAndUpdate(pos.above(2),RegistryBlocks.upgradeBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(5),RegistryBlocks.itemInputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(7),RegistryBlocks.itemOutputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos,(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get()).defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var controller=(TileController)world.getBlockEntity(pos);controller.setOwner(UUID.fromString("ad7762e3-0c61-4ab5-8367-b512cba87c93"));controller.setMachineName(name);
        require(MachineFormation.tryForm(world,pos,Direction.NORTH,MachineReloadListener.getLoader().findByName(name),MachineReloadListener.getLoader()).formed(),"formation "+name);
        controller.setFormed(true);controller.acknowledgeStructureCheck();energy(controller,1000);return controller;
    }
    private static IItemHandlerModifiable inv(TileController t,int y){return (IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(t.getBlockPos().above(y))).getInventory();}
    private static void clear(IItemHandlerModifiable inv){for(int i=0;i<inv.getSlots();i++)inv.setStackInSlot(i,ItemStack.EMPTY);}
    private static void stock(TileController t){for(int i=0;i<4;i++)inv(t,5).setStackInSlot(i,new ItemStack(Items.COBBLESTONE,64));}
    private static int count(TileController t,int y,Item item){int n=0;var inv=inv(t,y);for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(item))n+=inv.getStackInSlot(i).getCount();return n;}
    private static TileEnergyHatch power(TileController t){return (TileEnergyHatch)world.getBlockEntity(t.getBlockPos().below());}
    private static void energy(TileController t,int amount){var h=power(t);while(h.getStoredEnergy()>amount)require(h.consumeInternal(h.getStoredEnergy()-amount,false)>0,"drain");while(h.getStoredEnergy()<amount)require(h.produceInternal(amount-h.getStoredEnergy(),false)>0,"fill");}
    private static void connect(TileController t){require(t.getHyperNetNode().connect(c.getBlockPos(),c.getOwner())==HyperNetNode.ConnectResult.SUCCESS,"connect "+t.getMachineName());}
    private static void live(){require(c.getHyperNetNode().working(),"center dropped between successful cycles");require(p.getHyperNetNode().working(),"processor dropped between successful cycles");require(a.getHyperNetNode().available()>=0,"read");}
    private static void started(FactoryRecipeStartEvent e){if(e.getController()==c)cStart++;if(e.getController()==p)pStart++;}
    private static void completed(FactoryRecipeFinishEvent e){if(e.getController()==c){cFin++;endDueC=true;}if(e.getController()==p){pFin++;endDueP=true;}}
    private static void tickEnd(FactoryRecipeTickEvent e){
        if(e.getRecipePhase()!=hellfirepvp.modularmachinery.common.event.machine.Phase.END)return;
        boolean due=e.getController()==c?endDueC:e.getController()==p&&endDueP;
        if(!due)return;
        if(e.getActiveRecipe()==null||!e.getActiveRecipe().isCompleted()||!e.getFactoryRecipeThread().isIdle())eventFault="closing END saw next craft instead of completed craft";
        if(e.getController()==c){cEnds++;endDueC=false;}else{pEnds++;endDueP=false;}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent e){
        if(server==null||finished)return;
        try{
            if(e.phase==TickEvent.Phase.START){if(feedCenter)energy(c,1000);if(feedProcessor)energy(p,1000);return;}
            tick++;require(tick<1000&&tick-since<180,"timeout "+phase);
            switch(phase){
                case 0->{
                    if(tick<5)return;
                    if(!c.getHyperNetNode().working()||!p.getHyperNetNode().working()){
                        if(tick%20==0)Modularmachinery.LOG.info("[MMC][hypernet-core-cycle] warmup c={}/{} p={}/{} upgrades={}",cStart,cFin,pStart,pFin,hellfirepvp.modularmachinery.common.upgrade.ControllerUpgradeQueries.installed(p,true).stream().map(u->u.getType().getName()+":"+u.getStackSize()).toList());
                        return;
                    }
                    live();connect(p);connect(a);connect(f);
                    if(a.getHyperNetNode().available()!=5)return;
                    inv(a,5).setStackInSlot(0,new ItemStack(Items.DIRT));inv(f,5).setStackInSlot(0,new ItemStack(Items.DIRT));
                    a.requestNormalRecipeSearch();((TileFactoryController)f).requestImmediateRecipeSearch();baseC=cFin;baseP=pFin;next(1);
                }
                case 1->{
                    live();require(cStart==cFin+1&&pStart==pFin+1,"cycle start/finish pairing");
                    require(eventFault==null&&cEnds==cFin&&pEnds==pFin,"closing event lifetime: "+eventFault);
                    require(count(c,5,Items.COBBLESTONE)==256-cStart&&count(p,5,Items.COBBLESTONE)==256-pStart,"duplicate/missing start input");
                    require(count(c,7,Items.STONE)==cFin&&count(p,7,Items.STONE)==pFin,"duplicate/missing finish output");
                    require(power(c).getStoredEnergy()==999,"one-tick center billed twice or skipped");
                    require(1000-power(p).getStoredEnergy()<=59,"processor exceeded physical FE");
                    if(tick-since<100)return;
                    require(cFin-baseC==100&&pFin-baseP==50,"core cycles lost work ticks");
                    require(count(a,7,Items.DIAMOND)==1&&count(f,7,Items.EMERALD)==1,"consumer cancelled by cycle boundary");
                    // A real missing start material must stop the core rather than receive a grace lease.
                    clear(inv(c,5));next(2);
                }
                case 2->{
                    if(tick-since<3)return;
                    require(!c.getHyperNetNode().working()&&a.getHyperNetNode().available()==0,"material-starved center kept network");stock(c);((TileFactoryController)c).requestImmediateRecipeSearch();next(3);
                }
                case 3->{
                    if(!c.getHyperNetNode().working()||!p.getHyperNetNode().working()||a.getHyperNetNode().available()!=5)return;
                    // Completed core output blocked: must neither restart nor duplicate its output.
                    var out=inv(p,7);for(int i=0;i<out.getSlots();i++)out.setStackInSlot(i,new ItemStack(Items.APPLE,64));baseP=pFin;next(4);
                }
                case 4->{
                    if(tick-since<5)return;
                    require(!p.getHyperNetNode().working()&&a.getHyperNetNode().available()==0&&pFin==baseP,"output wait was bridged/billed as completion");
                    clear(inv(p,7));next(5);
                }
                case 5->{
                    if(!p.getHyperNetNode().working()||a.getHyperNetNode().available()!=5)return;
                    feedCenter=false;energy(c,0);next(6);
                }
                case 6->{
                    if(tick-since<3)return;
                    require(!c.getHyperNetNode().working()&&a.getHyperNetNode().available()==0,"unpowered center kept network");feedCenter=true;next(7);
                }
                case 7->{
                    if(!c.getHyperNetNode().working()||a.getHyperNetNode().available()!=5)return;
                    world.setBlockAndUpdate(p.getBlockPos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());next(8);
                }
                case 8->{
                    require(!p.getHyperNetNode().working()&&a.getHyperNetNode().available()==0,"redstone stop bridged");world.setBlockAndUpdate(p.getBlockPos().east(),Blocks.AIR.defaultBlockState());next(9);
                }
                case 9->{
                    if(!p.getHyperNetNode().working()||a.getHyperNetNode().available()!=5)return;
                    var bus=(IItemHandlerModifiable)((TileUpgradeBus)world.getBlockEntity(p.getBlockPos().above(2))).getUpgradeInventory();bus.setStackInSlot(2,ItemStack.EMPTY);next(10);
                }
                case 10->{
                    require(!p.getHyperNetNode().working()&&a.getHyperNetNode().available()==0,"missing RAM bridged");
                    var bus=(IItemHandlerModifiable)((TileUpgradeBus)world.getBlockEntity(p.getBlockPos().above(2))).getUpgradeInventory();bus.setStackInSlot(2,new ItemStack(Items.REDSTONE));next(11);
                }
                case 11->{
                    if(!p.getHyperNetNode().working()||a.getHyperNetNode().available()!=5)return;
                    feedProcessor=false;energy(p,0);next(12);
                }
                case 12->{
                    if(tick-since<3)return;
                    require(!p.getHyperNetNode().working()&&a.getHyperNetNode().available()==0,"unpowered processor kept network");feedProcessor=true;next(13);
                }
                case 13->{
                    if(!p.getHyperNetNode().working()||a.getHyperNetNode().available()!=5)return;
                    oldC=c.getHyperNetNode();oldP=p.getHyperNetNode();finish(null);
                }
            }
        }catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error){if(finished)return;finished=true;failed=error!=null;if(error!=null)Modularmachinery.LOG.error("[MMC][hypernet-core-cycle] FAIL phase "+phase,error);else Modularmachinery.LOG.info("[MMC][hypernet-core-cycle] runtime checks PASS");server.halt(false);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){if(server!=e.getServer()||failed)return;if(oldC==null||!oldC.isClosed()||!oldP.isClosed())Modularmachinery.LOG.error("[MMC][hypernet-core-cycle] OVERALL: FAIL cleanup");else Modularmachinery.LOG.info("[MMC][hypernet-core-cycle] OVERALL: PASS (1/2 tick real cores, ordinary/factory consumption, start/finish/material/FE conservation, genuine shortages, output wait, redstone, RAM and shutdown)");}
}
