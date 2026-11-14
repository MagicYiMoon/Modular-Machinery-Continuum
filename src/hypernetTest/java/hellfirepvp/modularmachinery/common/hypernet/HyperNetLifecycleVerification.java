/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Opt-in disposable-world checks. Supply lambdas are accounting fixtures, NOT CPU/RAM modules. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetLifecycleVerification {
    private static final BlockPos C=new BlockPos(1028,120,1028), P=C.east(80), A=C.east(160), B=A.south(4), EXTRA=B.south(4), D=A.east(6);
    private static final UUID ACTOR=UUID.fromString("14fd6e91-b48c-4576-9086-1fb315b38b77");
    private static MinecraftServer server;
    private static ServerLevel world;
    private static TileController center,processor,a,b,extra,secondCenter;
    private static TileController unloadedProcessor;
    private static HyperNetNode oldCenter,oldProcessor,oldConsumer;
    private static int ticks,phase,since;
    private static boolean failed,finished;
    private static boolean allTicketsReleased;
    private static int traceCount,unloadTick=-1;
    public static boolean traceUnloadTicket(){return phase==12&&ticks-since>20&&traceCount++<6;}
    @SubscribeEvent public static void unload(net.minecraftforge.event.level.ChunkEvent.Unload event){
        if(world!=null&&event.getLevel()==world&&event.getChunk().getPos().equals(new net.minecraft.world.level.ChunkPos(P))&&phase>=11){
            unloadTick=ticks;Modularmachinery.LOG.info("[MMC][hypernet-lifecycle] actual processor ChunkEvent.Unload tick {}",ticks);
        }
    }
    private static final AtomicInteger samples=new AtomicInteger();
    private static HyperNetNode cn(){return center.getHyperNetNode();}
    private static HyperNetNode pn(){return processor.getHyperNetNode();}
    private static HyperNetNode an(){return a.getHyperNetNode();}
    private static HyperNetNode bn(){return b.getHyperNetNode();}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void next(int p){phase=p;since=ticks;Modularmachinery.LOG.info("[MMC][hypernet-lifecycle] phase {} tick {}",p,ticks);}
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!Boolean.getBoolean("modularmachinery.hypernetLifecycleTest"))return;
        server=event.getServer();world=server.overworld();
        try{
            parserChecks();
            for(BlockPos pos:List.of(C,P,A))world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
            definition("hn_center","{\"role\":\"center\",\"carrying\":80,\"max-connections\":3,\"max-nodes-per-type\":2}",HyperNetDefinition.CENTER_THREAD);
            definition("hn_processor","{\"role\":\"processor\"}",HyperNetDefinition.PROCESSOR_THREAD);
            definition("hn_node","{\"role\":\"node\"}",null);
            MachineReloadListener.refreshRecipes();
            center=build(C,"hn_center",true);processor=build(P,"hn_processor",true);
            secondCenter=build(D,"hn_center",true);
            a=build(A,"hn_node",false);b=build(B,"hn_node",false);extra=build(EXTRA,"hn_node",false);
        }catch(Throwable error){finish(error);}
    }
    private static void parserChecks() throws Exception {
        String[] invalid={"null","{}","{\"role\":\"centre\"}","{\"role\":\"node\",\"carrying\":2}",
                "{\"role\":\"node\",\"working-thread\":\"ignored\"}","{\"role\":\"processor\",\"typo\":1}",
                "{\"role\":\"center\",\"carrying\":1,\"max-connections\":1.5}",
                "{\"role\":\"center\",\"carrying\":1,\"max-connections\":2147483648}",
                "{\"role\":\"center\",\"carrying\":1e999,\"max-connections\":1}",
                "{\"role\":\"center\",\"carrying\":\"80\",\"max-connections\":1}"};
        var loader=new MachineLoader();var path=Files.createTempDirectory("mmc-hypernet-invalid-");
        for(int i=0;i<invalid.length;i++){
            var file=path.resolve("invalid_"+i+".json");
            Files.writeString(file,"{\"registryname\":\"invalid_"+i+"\",\"hypernet\":"+invalid[i]+"}");
            require(loader.parseMachineFile(file.toFile()).isEmpty(),"invalid profile silently accepted: "+invalid[i]);
            Files.delete(file);
        }
        Files.delete(path);require(loader.getProblems().size()>=invalid.length,"profile errors not reported");
    }
    private static void definition(String name,String profile,String thread) throws Exception {
        String json="{\"registryname\":\""+name+"\",\"requires-blueprint\":false,\"has-factory\":"+(thread!=null)
                +",\"parts\":[{\"x\":0,\"y\":-1,\"z\":0,\"elements\":\"modularmachinery:blockenergyinputhatch\"},"
                +"{\"x\":0,\"y\":1,\"z\":0,\"elements\":\"minecraft:"+capName(name)+"\"}],\"hypernet\":"+profile+"}";
        var file=Files.createTempDirectory("mmc-hypernet-profile-").resolve(name+".json");Files.writeString(file,json);
        var machine=MachineReloadListener.serverDefinitions().machines().parseMachineFile(file.toFile()).orElseThrow();
        Files.delete(file);Files.delete(file.getParent());
        if(thread!=null){
            machine.addCoreThreadPreset(thread,List.of(name+"_work"));
            var recipe=new MachineRecipe(name,name+"_work",200,List.of(new RequirementEnergy(IOType.INPUT,1)),null,false,1,thread);
            ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(recipe,Map.of()));
        }
        var snapshot=MachineReloadListener.serverDefinitions().machines().snapshot();
        require(MachineLoader.fromSnapshot(snapshot).findByName(name).getHyperNetDefinition().equals(machine.getHyperNetDefinition()),"profile snapshot roundtrip");
    }
    private static TileController build(BlockPos pos,String name,boolean factory){
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(),cap(name).defaultBlockState());
        world.setBlockAndUpdate(pos,(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get()).defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var tile=(TileController)world.getBlockEntity(pos);tile.setOwner(ACTOR);tile.setMachineName(name);
        var attempt=MachineFormation.tryForm(world,pos,Direction.NORTH,MachineReloadListener.getLoader().findByName(name),MachineReloadListener.getLoader());
        require(attempt.formed(),"fixture formation: "+attempt.report());tile.setFormed(true);tile.acknowledgeStructureCheck();
        fill(pos);return tile;
    }
    private static TileEnergyHatch power(BlockPos pos){return (TileEnergyHatch)world.getBlockEntity(pos.below());}
    private static String capName(String name){return name.equals("hn_processor")?"iron_block":name.equals("hn_center")?"stone":"gold_block";}
    private static net.minecraft.world.level.block.Block cap(String name){return name.equals("hn_processor")?Blocks.IRON_BLOCK:name.equals("hn_center")?Blocks.STONE:Blocks.GOLD_BLOCK;}
    private static void fill(BlockPos pos){for(int i=0;i<100;i++)power(pos).produceInternal(1000,false);}
    private static void supply(){pn().processorSupply(t->{samples.incrementAndGet();return 100;});}
    private static void bind(){
        require(pn().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"processor connect");
        require(an().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"consumer A connect");
        require(bn().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"consumer B connect");
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||server==null||finished)return;
        try{
            ticks++;require(ticks<4000&&ticks-since<1200,"timeout phase="+phase);
            switch(phase){
                case 0->{
                    if(ticks<30||!cn().working()||!pn().working()||!secondCenter.getHyperNetNode().working())return;
                    require(power(C).getStoredEnergy()<power(C).getCapacity(),"center did not spend real FE");
                    require(power(P).getStoredEnergy()<power(P).getCapacity(),"processor did not spend real FE");
                    require(an().connect(C,UUID.randomUUID())==HyperNetNode.ConnectResult.NO_PERMISSION,"node ownership");
                    bind();require(cn().connectionCount()==3,"connected roster");
                    require(extra.getHyperNetNode().connect(C,ACTOR)==HyperNetNode.ConnectResult.CONNECTION_LIMIT,"total connection limit");
                    pn().disconnect();require(extra.getHyperNetNode().connect(C,ACTOR)==HyperNetNode.ConnectResult.TYPE_LIMIT,"node type limit");
                    require(pn().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"processor reattach");
                    require(an().available()==0,"unconfigured processor invented supply");supply();
                    require(an().available()==80,"center carrying limit");int n=samples.get();
                    require(an().request(60,true).granted()==60&&bn().available()==80,"simulation changed shared credit");
                    require(an().request(50,false).granted()==50&&bn().available()==30,"shared consumer balance");
                    require(bn().request(31,false).granted()==0&&an().available()==30,"shortfall consumed partial credit");
                    require(bn().request(30,false).granted()==30&&an().available()==0,"shared exact debit");
                    require(samples.get()==n,"queries resampled module");
                    require(pn().connect(D,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"second-center connection");
                    require(secondCenter.getHyperNetNode().available()==0,"same-tick network switch duplicated processor supply");
                    require(pn().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS&&cn().available()==0,"returning to first center refilled credit");
                    var data=a.saveWithoutMetadata();require(data.contains("hypernetData")&&!a.getUpdateTag().contains("hypernetData"),"private persistence/update separation");
                    var copy=a.getHyperNetData();copy.remove("binding");require(a.getHyperNetData().contains("binding"),"NBT copy isolation");
                    world.setBlockAndUpdate(C.west(),Blocks.REDSTONE_BLOCK.defaultBlockState());
                    require(an().available()==0&&cn().connectionCount()==0,"redstone did not immediately disable network");next(1);
                }
                case 1->{
                    if(ticks-since<4)return;require(cn().connectionCount()==0,"stopped center kept peers");
                    world.setBlockAndUpdate(C.west(),Blocks.AIR.defaultBlockState());next(2);
                }
                case 2->{
                    if(ticks-since<4||!cn().working()||an().available()!=80)return;
                    while(power(P).getStoredEnergy()>0)require(power(P).consumeInternal(Integer.MAX_VALUE,false)>0,"cannot drain fixture power");next(3);
                }
                case 3->{
                    if(pn().working()){require(ticks-since<5,"core thread still working without power");return;}
                    require(an().available()==0,"powerless processor supplied credit");fill(P);next(4);
                }
                case 4->{
                    if(!pn().working()||an().available()!=80)return;
                    oldProcessor=pn();world.setBlockAndUpdate(P.above(),Blocks.AIR.defaultBlockState());
                    require(an().available()==0,"broken structure supplied before next tick");next(5);
                }
                case 5->{
                    if(processor.isFormed())return;require(oldProcessor.isClosed()&&oldProcessor.available()==0,"unformed old reference remained active");
                    world.setBlockAndUpdate(P.above(),Blocks.IRON_BLOCK.defaultBlockState());next(6);
                }
                case 6->{
                    if(!pn().working())return;require(an().available()==0,"reformed runtime retained transient backend");supply();next(7);
                }
                case 7->{
                    if(an().available()!=80)return;oldCenter=cn();oldProcessor=pn();oldConsumer=an();
                    MachineReloadListener.refreshRecipes();
                    require(oldCenter.available()==0&&oldProcessor.available()==0&&oldConsumer.available()==0,"definition revision revived old runtimes");
                    cn();pn();an();bn();require(oldCenter.isClosed()&&oldProcessor.isClosed()&&oldConsumer.isClosed(),"replacement did not close old runtime");next(8);
                    require(cn().available()==0,"new center refilled credit in its creation tick");
                }
                case 8->{
                    if(ticks-since<4||!cn().working()||!pn().working())return;
                    require(an().available()==0,"reload persisted old compute credit");supply();next(9);
                }
                case 9->{
                    if(an().available()!=80)return;oldConsumer=an();CompoundTag tag=a.saveWithoutMetadata();
                    world.removeBlock(A,false);world.setBlockAndUpdate(A,RegistryBlocks.blockController.get().defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
                    a=(TileController)world.getBlockEntity(A);a.load(tag);
                    require(oldConsumer.isClosed()&&oldConsumer.available()==0,"same-position replacement revived old consumer");next(10);
                }
                case 10->{
                    if(ticks-since<4||an().available()!=80)return;
                    oldProcessor=pn();unloadedProcessor=processor;world.setChunkForced(P.getX()>>4,P.getZ()>>4,false);next(11);
                }
                case 11->{
                    if(world.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4)!=null)return;
                    require(oldProcessor.available()==0&&oldProcessor.isClosed()&&an().available()==0,"natural chunk unload left supply active");
                    require(world.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4)==null,"query force-loaded processor");next(12);
                }
                case 12->{
                    if(!allTicketsReleased){
                        // Nearby forced chunks retain an INACCESSIBLE LevelChunk as a generation dependency.
                        // The previous phase checked that this already cannot supply; now unload the whole region.
                        for(BlockPos pos:List.of(C,A))world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,false);
                        allTicketsReleased=true;
                    }
                    if((ticks-since)%200==0)Modularmachinery.LOG.info("[MMC][hypernet-lifecycle] unload state {}",world.getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(P)));
                    // Losing the accessible view is not an unload. Observe the real event and new disk BE.
                    if(unloadTick<0||ticks-unloadTick<20)return;
                    require(world.getChunkSource().getChunkNow(P.getX()>>4,P.getZ()>>4)==null,"query reloaded inaccessible chunk");
                    for(BlockPos pos:List.of(C,P,A)){
                        world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);world.getChunk(pos.getX()>>4,pos.getZ()>>4);
                    }
                    center=(TileController)world.getBlockEntity(C);a=(TileController)world.getBlockEntity(A);
                    b=(TileController)world.getBlockEntity(B);extra=(TileController)world.getBlockEntity(EXTRA);
                    secondCenter=(TileController)world.getBlockEntity(D);
                    processor=(TileController)world.getBlockEntity(P);
                    require(processor!=null,"disk reload missing processor BE");
                    require(processor!=unloadedProcessor,"disk reload retained old processor BE");
                    require(processor.getHyperNetNode()!=oldProcessor,"disk reload retained old processor runtime");next(13);
                }
                case 13->{
                    if(ticks-since<4||!pn().working())return;
                    require(an().available()==0,"disk reload manufactured credit/backend");supply();next(14);
                }
                case 14->{
                    if(an().available()!=80)return;
                    var saved=a.getHyperNetData();var foreign=saved.copy();foreign.getCompound("binding").putString("dimension","minecraft:the_nether");a.setHyperNetData(foreign);
                    require(an().available()==0,"cross-dimension binding accepted");a.setHyperNetData(saved);
                    require(an().available()==80&&cn().connectionCount()==3,"saved bindings failed reconnect");
                    oldCenter=cn();oldProcessor=pn();oldConsumer=an();finish(null);
                }
            }
        }catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error){
        if(finished)return;finished=true;failed=error!=null;
        if(error!=null)Modularmachinery.LOG.error("[MMC][hypernet-lifecycle] FAIL phase "+phase,error);
        else Modularmachinery.LOG.info("[MMC][hypernet-lifecycle] runtime checks PASS; awaiting real server-stop cleanup");
        server.halt(false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        if(server!=event.getServer()||failed)return;
        if(oldCenter==null||!oldCenter.isClosed()||!oldProcessor.isClosed()||!oldConsumer.isClosed())
            Modularmachinery.LOG.error("[MMC][hypernet-lifecycle] OVERALL: FAIL server-stop cleanup");
        else Modularmachinery.LOG.info("[MMC][hypernet-lifecycle] OVERALL: PASS (real FE/core threads, limits, shared accounting, stops, formation, revision, NBT, replacement, natural unload/reload and server-stop cleanup)");
    }
}
