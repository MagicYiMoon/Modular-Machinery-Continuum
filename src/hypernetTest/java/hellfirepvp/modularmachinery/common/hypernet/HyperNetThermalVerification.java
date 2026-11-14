/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.math.BigDecimal;
import java.util.*;

/** Physical CPU/RAM, actual core recipes, natural ticks and real disk unload/reload. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetThermalVerification {
    private static MinecraftServer server;private static ServerLevel world;
    private static TileController center,processor,consumer,unloadedProcessor;
    private static HyperNetNode oldProcessor;private static HyperNetDefinition original;
    private static int tick,phase,since,unloadTick=-1;private static boolean finished,failed;
    private static double held;
    private static final String AUX="test.thermal.independent";
    @SubscribeEvent public static void resume(ServerStartedEvent event) {
        if(!Boolean.getBoolean("modularmachinery.hypernetThermalResumeTest"))return;
        server=event.getServer();world=server.overworld();
        try {
            // A separate server process opens the world saved by the completed thermal fixture.
            // Do not rebuild blocks or manufacture thermal data on the read side.
            var pos=new BlockPos(2108,120,2028);world.getChunk(pos.getX()>>4,pos.getZ()>>4);
            processor=(TileController)world.getBlockEntity(pos);require(processor!=null,"restart missing real controller");
            var tag=processor.getHyperNetData().getCompound("thermal");
            require(tag.getString("machine").equals("hw_processor")&&tag.getString("heat").equals("20")&&tag.getBoolean("overheated"),"restart lost saved thermal NBT");
            var state=new HyperNetThermalState(processor,new HyperNetThermalDefinition(20,0,""),"hw_processor");
            require(state.stored().intValueExact()==20&&state.overheated(),"restart adapter reset heat or applied offline catch-up");
            require(!processor.getUpdateTag().contains("hypernetData"),"restart exposed private thermal data");
            Modularmachinery.LOG.info("[MMC][hypernet-thermal] restart persistence checks PASS");finished=true;server.halt(false);
        }catch(Throwable error){finish(error);}
    }
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    private static void near(double actual,double expected,String message){require(Math.abs(actual-expected)<1e-9,message+" actual="+actual+" expected="+expected);}
    private static void next(int value){phase=value;since=tick;Modularmachinery.LOG.info("[MMC][hypernet-thermal] phase {} tick {}",phase,tick);}
    private static HyperNetNode pn(){return processor.getHyperNetNode();}private static HyperNetNode an(){return consumer.getHyperNetNode();}
    public static void begin(MinecraftServer srv,ServerLevel level,TileController c,TileController p,TileController a) {
        server=srv;world=level;center=c;processor=p;consumer=a;
        try{
            parserChecks();var machine=MachineReloadListener.getLoader().findByName(p.getMachineName());original=machine.getHyperNetDefinition();
            machine.addCoreThreadPreset(AUX,List.of("ht_aux"));
            ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(new MachineRecipe(p.getMachineName(),"ht_aux",200,
                    List.of(new RequirementEnergy(IOType.INPUT,1)),null,false,1,AUX),Map.of()));
            configure(1);next(0);
        }catch(Throwable error){finish(error);}
    }
    private static void configure(int cooling) {
        var machine=MachineReloadListener.getLoader().findByName(processor.getMachineName());
        machine.setHyperNetDefinition(new HyperNetDefinition(original.role(),original.workingThread(),0,0,0,original.modules(),"",new HyperNetThermalDefinition(20,cooling,"")));
        MachineReloadListener.refreshRecipes();
    }
    private static TileEnergyHatch power(TileController controller){return (TileEnergyHatch)world.getBlockEntity(controller.getBlockPos().below());}
    private static void fill(TileController controller){
        var hatch=power(controller);while(hatch.getStoredEnergy()>1000)hatch.consumeInternal(hatch.getStoredEnergy()-1000,false);
        while(hatch.getStoredEnergy()<1000)hatch.produceInternal(1000-hatch.getStoredEnergy(),false);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(server==null||finished)return;
        try{
            if(event.phase==TickEvent.Phase.START){if(phase<8||phase>9){fill(center);fill(processor);}return;}
            tick++;require(tick<1800&&tick-since<1000,"thermal timeout phase "+phase);
            switch(phase){
                case 0->{
                    if(tick<5||!pn().working()||an().available()!=5)return;
                    int before=power(processor).getStoredEnergy();near(pn().storedHeat(),0,"new thermal config hot");
                    require(an().request(5,true).granted()==5&&an().request(6,false).granted()==0,"thermal request simulation/shortfall");
                    near(pn().storedHeat(),0,"simulation/shortfall generated heat");
                    for(int i=0;i<100;i++)require(an().request(.01,false).granted()==.01,"split request");
                    near(pn().storedHeat(),2,"split work heat lost");require(power(processor).getStoredEnergy()==before-13,"split load FE changed");next(1);
                }
                case 1->{
                    near(pn().storedHeat(),1.75,"low zone cooling");require(an().request(5,false).granted()==5,"second heat request");near(pn().storedHeat(),11.75,"actual work heat");next(2);
                }
                case 2->{
                    double heat=pn().storedHeat();near(heat,10.9125,"mid zone cooling");
                    require(an().request((19-heat)/2,false).granted()>0,"heat approach threshold");near(pn().storedHeat(),19,"approach heat");next(3);
                }
                case 3->{
                    near(pn().storedHeat(),18,"high zone cooling");near(an().available(),10.0/3,"rational efficiency capacity");
                    int before=power(processor).getStoredEnergy();require(an().request(1,true).granted()==1,"derated simulation");near(pn().storedHeat(),18,"derated simulation heat");
                    require(an().request(1,false).granted()==1,"derated physical request");
                    require(power(processor).getStoredEnergy()==before-20,"derating did not charge raw module work");
                    require(pn().overheated()&&!pn().working()&&an().available()==0,"overheat retained supply");near(pn().storedHeat(),20,"threshold heat");
                    oldProcessor=pn();held=pn().storedHeat();MachineReloadListener.refreshRecipes();
                    require(oldProcessor.request(1,false).granted()==0&&oldProcessor.isClosed(),"hot old runtime retained supply");
                    near(pn().storedHeat(),held,"same-tick reload cooled/reset heat");
                    require(pn().overheated()&&!processor.getUpdateTag().contains("hypernetData"),"hot state leak or reset");next(4);
                }
                case 4->{
                    require(pn().overheated()&&an().available()==0,"latched overheat reset above zero");
                    if(tick-since<4)return;
                    require(((TileFactoryController)processor).getFactoryRecipeThreadList().stream().anyMatch(thread->thread.getThreadName().equals(AUX)&&thread.isWorking()),"overheat blocked independent cooling/maintenance thread");
                    held=pn().storedHeat();require(held>0,"cooling skipped time");configure(0);
                    near(pn().storedHeat(),held,"config reload reset heat");next(5);
                }
                case 5->{
                    near(pn().storedHeat(),held,"zero dissipation changed heat");require(pn().overheated(),"disabled dissipation released latch");
                    if(tick-since<4)return;
                    // Actual NBT load replaces the runtime without erasing private physical state.
                    var saved=processor.saveWithoutMetadata();oldProcessor=pn();processor.load(saved);
                    require(oldProcessor.isClosed(),"NBT load did not retire runtime");near(pn().storedHeat(),held,"NBT load reset heat");next(6);
                }
                case 6->{
                    if(tick-since<4)return;near(pn().storedHeat(),held,"loaded state lost heat");
                    unloadedProcessor=processor;oldProcessor=pn();
                    // Release the whole fixture region so neighboring forced chunks cannot retain level 36 chunks.
                    for(long chunk:world.getForcedChunks().toLongArray())world.setChunkForced(net.minecraft.world.level.ChunkPos.getX(chunk),net.minecraft.world.level.ChunkPos.getZ(chunk),false);
                    next(8);
                }
                case 8->{
                    require(oldProcessor.request(1,false).granted()==0,"unloaded hot runtime supplied");
                    if(unloadTick<0||tick-unloadTick<15)return;
                    var pos=unloadedProcessor.getBlockPos();require(world.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4)==null,"thermal query forced chunk load");
                    for(var target:List.of(center.getBlockPos(),pos,consumer.getBlockPos())) {
                        world.setChunkForced(target.getX()>>4,target.getZ()>>4,true);world.getChunk(target.getX()>>4,target.getZ()>>4);
                    }
                    center=(TileController)world.getBlockEntity(center.getBlockPos());consumer=(TileController)world.getBlockEntity(consumer.getBlockPos());
                    processor=(TileController)world.getBlockEntity(pos);
                    require(processor!=null&&processor!=unloadedProcessor,"disk reload retained BE");near(pn().storedHeat(),held,"disk reload lost heat");
                    require(pn().overheated()&&oldProcessor.isClosed(),"disk reload reset latch");next(9);
                }
                case 9->{if(tick-since<4)return;configure(1);next(10);}
                case 10->{
                    if(pn().storedHeat()>0){require(pn().overheated()&&an().available()==0,"latched recovery early");return;}
                    require(!pn().overheated(),"zero heat stuck latch");next(11);
                }
                case 11->{
                    if(!pn().working()||an().available()==0)return;
                    // The hardware fixture invalidates one dynamic module only in memory. Disk load
                    // rebuilds both physical lapis instances; calculate the expected real inventory.
                    var modules=new ArrayList<HyperNetLoad.Installed>();
                    for(var upgrade:hellfirepvp.modularmachinery.common.upgrade.ControllerUpgradeQueries.installed(processor,true))
                        for(var module:original.modules())if(module.upgrade().equals(upgrade.getType().getName()))modules.add(new HyperNetLoad.Installed(module,upgrade.getStackSize()));
                    near(an().available(),new HyperNetLoad(modules).capacity(),"recovered physical module capacity");
                    require(an().request(.1,false).granted()==.1,"recovered processor cannot supply");near(pn().storedHeat(),.2,"recovered work heat");
                    // Corrupt private data is rejected to a bounded hot state, never a free cold node.
                    var saved=processor.saveWithoutMetadata();saved.getCompound("hypernetData").getCompound("thermal").putString("heat","NaN");
                    processor.load(saved);require(pn().overheated()&&pn().storedHeat()==20,"corrupt thermal state did not fail hot");
                    oldProcessor=pn();finish(null);
                }
            }
        }catch(Throwable error){finish(error);}
    }
    @SubscribeEvent public static void unload(ChunkEvent.Unload event) {
        if(world!=null&&event.getLevel()==world&&phase==8&&event.getChunk().getPos().equals(new net.minecraft.world.level.ChunkPos(unloadedProcessor.getBlockPos()))) {
            unloadTick=tick;Modularmachinery.LOG.info("[MMC][hypernet-thermal] real processor ChunkEvent.Unload tick {}",tick);
        }
    }
    private static void parserChecks()throws Exception {
        String valid="{\"role\":\"processor\",\"thermal\":{\"overheat-threshold\":20,\"heat-distribution\":1}}";
        require(HyperNetDefinition.parse(JsonParser.parseString(valid)).thermal().overheatThreshold()==20,"thermal parser");
        for(var json:List.of(valid.replace("20","0"),valid.replace("20","1.5"),valid.replace("20","2147483648"),valid.replace("20","\"20\""),
                valid.replace(":1}",":-1}"),valid.replace("\"heat-distribution\"","\"typo\""),valid.replace("processor","node"),valid.replace("\"thermal\":{","\"thermal\":{\"dynamic-pattern\":4,"))) {
            try{HyperNetDefinition.parse(JsonParser.parseString(json));throw new AssertionError("invalid thermal JSON accepted: "+json);}catch(IllegalArgumentException expected){}
        }
        var file=java.nio.file.Files.createTempDirectory("mmc-thermal-parser-").resolve("ht_snapshot.json");
        java.nio.file.Files.writeString(file,"{\"registryname\":\"ht_snapshot\",\"hypernet\":"+valid+"}");
        var machine=MachineReloadListener.serverDefinitions().machines().parseMachineFile(file.toFile()).orElseThrow();
        var copy=MachineLoader.fromSnapshot(MachineReloadListener.serverDefinitions().machines().snapshot()).findByName("ht_snapshot");
        require(copy.getHyperNetDefinition().thermal().equals(machine.getHyperNetDefinition().thermal()),"thermal machine snapshot changed");
        java.nio.file.Files.delete(file);java.nio.file.Files.delete(file.getParent());
    }
    private static void finish(Throwable error) {
        if(finished)return;finished=true;failed=error!=null;
        if(error!=null)Modularmachinery.LOG.error("[MMC][hypernet-thermal] FAIL phase "+phase,error);
        else Modularmachinery.LOG.info("[MMC][hypernet-thermal] runtime checks PASS; awaiting shutdown");server.halt(false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) {
        if(event.getServer()!=server||failed)return;
        if(Boolean.getBoolean("modularmachinery.hypernetThermalResumeTest")) {
            Modularmachinery.LOG.info("[MMC][hypernet-thermal-resume] OVERALL: PASS (separate server process, original world/controller NBT, heat/latch and no offline catch-up)");return;
        }
        require(oldProcessor!=null&&oldProcessor.isClosed(),"hot runtime shutdown cleanup");
        Modularmachinery.LOG.info("[MMC][hypernet-thermal] OVERALL: PASS (physical modules/FE, exact heat, rational derating, overheat/cooling, independent thread, NBT/revision, real disk unload/reload and shutdown)");
    }
}
