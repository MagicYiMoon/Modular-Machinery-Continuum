/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import hellfirepvp.modularmachinery.common.upgrade.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.math.*;
import java.nio.file.*;
import java.util.*;

/** Real module FE, real station core, completion outputs and cross-process task persistence. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetStationVerification {
    private static final UUID ACTOR=UUID.fromString("ad7762e3-0c61-4ab5-8367-b512cba87c93");
    private static final BlockPos C=new BlockPos(2028,120,3228),P=C.east(80),D=P.east(80),A=D.east(80),S=A.east(80),S2=S.east(80),C2=S2.east(80);
    private static MinecraftServer server;private static ServerLevel world;
    private static TileController c,p,d,a,s,s2,c2;
    private static int tick,phase,since;private static boolean finished,failed,feedS=true,readyFinish,resume;
    private static BigDecimal frozen=BigDecimal.ZERO,base=BigDecimal.ZERO,lastBeta=BigDecimal.ZERO;
    private static int previousConsumerTicks;private static boolean betaAccounting;
    private static HyperNetNode oldNode;
    private static ItemStack[] heldOutputs;
    private static boolean initiallyConnected;
    private static void require(boolean v,String msg){if(!v)throw new AssertionError(msg);}
    private static void parserChecks(){
        for(var profile:List.of("{\"role\":\"research_station\"}","{\"role\":\"research_station\",\"max-tech-level\":-1}",
                "{\"role\":\"research_station\",\"max-tech-level\":\"1\"}","{\"role\":\"research_station\",\"max-tech-level\":1E309}",
                "{\"role\":\"node\",\"max-tech-level\":0}")){
            boolean rejected=false;try{HyperNetDefinition.parse(JsonParser.parseString(profile));}catch(IllegalArgumentException|JsonParseException expected){rejected=true;}
            require(rejected,"invalid station profile admitted "+profile);
        }
        var definition=HyperNetDefinition.parse(JsonParser.parseString("{\"role\":\"research_station\",\"max-tech-level\":1.5}"));
        require(definition.maxTechLevel()==1.5&&definition.workingThread().equals(HyperNetDefinition.RESEARCH_THREAD),"station default thread/decimal technology");
    }
    private static void next(int n){phase=n;since=tick;Modularmachinery.LOG.info("[MMC][hypernet-station] phase {} tick {} points {}",phase,tick,points());}
    private static Path mainDir(){return ModDataHolder.INSTANCE.getMachineryDirectory().toPath().getParent();}
    private static void definition(String name,String role)throws Exception{
        String profile=switch(role){
            case "center"->"{\"role\":\"center\",\"carrying\":5,\"max-connections\":16,\"connection-card\":\"minecraft:paper\"}";
            case "processor"->"{\"role\":\"processor\",\"modules\":[{\"upgrade\":\"hn_cpu\",\"kind\":\"cpu\",\"capacity\":5,\"energy\":50},{\"upgrade\":\"hn_ram\",\"kind\":\"ram\",\"capacity\":5,\"energy\":5}]}";
            case "database"->"{\"role\":\"database\",\"storage-capacity\":3}";
            case "research_station"->"{\"role\":\"research_station\",\"max-tech-level\":1}";
            default->"{\"role\":\"node\"}";
        };
        String parts="{\"y\":-1,\"elements\":\"modularmachinery:blockenergyinputhatch\"},{\"y\":1,\"elements\":\"minecraft:gold_block\"},{\"y\":5,\"elements\":\"modularmachinery:blockinputbus\"},{\"y\":7,\"elements\":\"modularmachinery:blockoutputbus\"}";
        if(role.equals("processor"))parts+=",{\"y\":2,\"elements\":\"modularmachinery:blockupgradebus\"}";
        if(role.equals("research_station"))parts+=",{\"y\":3,\"elements\":\"modularmachinery:blocksmartinterface\"}";
        String info=role.equals("research_station")?",\"smart-interface-types\":[{\"type\":\"overclocking\",\"default-value\":1}]":"";
        var path=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(name+".json");
        Files.writeString(path,"{\"registryname\":\""+name+"\",\"requires-blueprint\":false,\"has-factory\":true,\"parts\":["+parts+"],\"hypernet\":"+profile+info+"}");
    }
    private static String thread(String name){return name.equals("hs_station")?HyperNetDefinition.RESEARCH_THREAD:name.equals("hs_processor")?HyperNetDefinition.PROCESSOR_THREAD:HyperNetDefinition.CENTER_THREAD;}
    private static void core(String name)throws Exception{
        List<Requirement> req=new ArrayList<>();req.add(new RequirementEnergy(IOType.INPUT,1));
        if(name.equals("hs_station")){req.add(new RequirementItem(IOType.INPUT,"minecraft:paper",1));req.add(new RequirementItem(IOType.OUTPUT,"minecraft:gold_nugget",1));}
        var recipe=new MachineRecipe(name,name+"_work",name.equals("hs_station")?5:2,req,null,false,1,thread(name));
        writeRecipe(recipe,name);
    }
    private static void writeRecipe(MachineRecipe recipe,String file)throws Exception{
        var json=RecipeSnapshotCodec.encode(recipe,Map.of());
        json.add("registryname",json.remove("name"));json.add("recipeTime",json.remove("duration"));
        json.add("cancelIfPerTickFails",json.remove("cancel"));
        Files.writeString(ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve(file+".json"),json.toString());
    }
    private static void presets(){for(var name:List.of("hs_center","hs_processor","hs_db","hs_station"))MachineReloadListener.getLoader().findByName(name).addCoreThreadPreset(thread(name),List.of(name+"_work"));}
    private static void research(String id,int total,String dependency,int tech)throws Exception{
        var j=new JsonObject();j.addProperty("id",id);j.addProperty("required-points",total);j.addProperty("minimum-per-tick",2);j.addProperty("tech-level",tech);
        if(!dependency.isEmpty()){var deps=new JsonArray();deps.add(dependency);j.add("dependencies",deps);}
        Files.writeString(mainDir().resolve("research").resolve(id+".json"),j.toString());
    }
    public static void begin(MinecraftServer srv,ServerLevel level){
        server=srv;world=level;
        try{
            parserChecks();
            if(net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")){
                var example=MachineReloadListener.getLoader().findByName("hn_sample_research_station");
                require(example!=null&&example.getHyperNetDefinition().role()==HyperNetDefinition.Role.RESEARCH_STATION
                        &&example.getCoreThreadPresets().containsKey(HyperNetDefinition.RESEARCH_THREAD)
                        &&MachineReloadListener.getRecipeLoader().getRecipe("hn_station_work")!=null,"research station example/ZenCode core missing");
            }
            for(var entry:Map.of("hs_center","center","hs_processor","processor","hs_db","database","hs_station","research_station","hs_node","node").entrySet())definition(entry.getKey(),entry.getValue());
            for(var name:List.of("hs_center","hs_processor","hs_db","hs_station"))core(name);
            Files.createDirectories(mainDir().resolve("research"));research("station_root",100,"",1);research("station_beta",19,"station_root",1);research("station_resume",200,"station_beta",1);research("station_high",10,"",3);
            var recipe=new MachineRecipe("hs_node","hs_consumer",500,List.of(new RequirementComputation(1,false),new RequirementItem(IOType.INPUT,"minecraft:dirt",1)),null,false,1,"");
            writeRecipe(recipe,"hs_consumer");
            if(Boolean.getBoolean("modularmachinery.hypernetTerminalTest"))HyperNetTerminalVerification.prepare();
            MachineReloadListener.reloadNow();presets();
            var station=MachineReloadListener.getLoader().findByName("hs_station");
            require(station.getHyperNetDefinition().equals(MachineLoader.fromSnapshot(MachineReloadListener.getLoader().snapshot()).findByName("hs_station").getHyperNetDefinition()),"station snapshot changed role/technology");
            for(var name:List.of("hs_center_work","hs_processor_work","hs_db_work","hs_station_work","hs_consumer"))
                require(MachineReloadListener.getRecipeLoader().getRecipe(name)!=null,"fixture disk recipe missing "+name);
            c=build(C,"hs_center",true);p=build(P,"hs_processor",true);d=build(D,"hs_db",true);a=build(A,"hs_node",false);s=build(S,"hs_station",true);s2=build(S2,"hs_station",true);c2=build(C2,"hs_center",true);
            var inv=(IItemHandlerModifiable)((TileUpgradeBus)world.getBlockEntity(P.above(2))).getUpgradeInventory();inv.setStackInSlot(0,new ItemStack(Items.DIAMOND));inv.setStackInSlot(1,new ItemStack(Items.REDSTONE));
            input(s,Items.PAPER,64);input(s2,Items.PAPER,64);next(0);
        }catch(Throwable error){finish(error);}
    }
    @SubscribeEvent public static void started(ServerStartedEvent event){
        if(!Boolean.getBoolean("modularmachinery.hypernetStationResumeTest"))return;
        server=event.getServer();world=server.overworld();resume=true;
        try{
            RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(Items.DIAMOND),new SimpleMachineUpgrade(UpgradeType.of("hn_cpu","hn_cpu",2)));
            RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(Items.REDSTONE),new SimpleMachineUpgrade(UpgradeType.of("hn_ram","hn_ram",2)));
            presets();for(var pos:List.of(C,P,D,A,S,S2,C2))world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
            c=(TileController)world.getBlockEntity(C);p=(TileController)world.getBlockEntity(P);d=(TileController)world.getBlockEntity(D);a=(TileController)world.getBlockEntity(A);s=(TileController)world.getBlockEntity(S);s2=(TileController)world.getBlockEntity(S2);c2=(TileController)world.getBlockEntity(C2);
            var journal=JsonParser.parseString(Files.readString(mainDir().resolve("station-resume-check.json"))).getAsJsonObject();
            base=new BigDecimal(journal.get("points").getAsString());
            require(s.getHyperNetData().getCompound("researchTask").getString("progress").equals(journal.get("points").getAsString()),"saved task changed across process/offline work");
            require(s.getHyperNetNode().researchTask().id().equals("station_resume")&&points().compareTo(base)==0&&count(s,7)==2,"disk task/core outputs not restored");
            world.setBlockAndUpdate(S.east(),Blocks.AIR.defaultBlockState());overclock(1);next(20);
        }catch(Throwable error){finish(error);}
    }
    private static TileController build(BlockPos pos,String name,boolean factory){
        world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());world.setBlockAndUpdate(pos.above(),Blocks.GOLD_BLOCK.defaultBlockState());
        world.setBlockAndUpdate(pos.above(5),RegistryBlocks.itemInputBus.get().defaultBlockState());world.setBlockAndUpdate(pos.above(7),RegistryBlocks.itemOutputBus.get().defaultBlockState());
        if(name.equals("hs_processor"))world.setBlockAndUpdate(pos.above(2),RegistryBlocks.upgradeBus.get().defaultBlockState());
        if(name.equals("hs_station"))world.setBlockAndUpdate(pos.above(3),RegistryBlocks.smartInterface.get().defaultBlockState());
        world.setBlockAndUpdate(pos,(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get()).defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var t=(TileController)world.getBlockEntity(pos);t.setOwner(ACTOR);t.setMachineName(name);
        require(MachineFormation.tryForm(world,pos,Direction.NORTH,MachineReloadListener.getLoader().findByName(name),MachineReloadListener.getLoader()).formed(),"formation "+name);t.setFormed(true);t.acknowledgeStructureCheck();return t;
    }
    private static void connect(TileController t){require(t.getHyperNetNode().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"connect "+t.getMachineName());}
    private static TileEnergyHatch power(TileController t){return (TileEnergyHatch)world.getBlockEntity(t.getBlockPos().below());}
    private static void energy(TileController t,int amount){var h=power(t);if(h.getStoredEnergy()>amount)h.consumeInternal(h.getStoredEnergy()-amount,false);else if(h.getStoredEnergy()<amount)h.produceInternal(amount-h.getStoredEnergy(),false);}
    private static IItemHandlerModifiable inv(TileController t,int y){return (IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(t.getBlockPos().above(y))).getInventory();}
    private static void input(TileController t,Item item,int amount){inv(t,5).setStackInSlot(0,new ItemStack(item,amount));}
    private static int count(TileController t,int y){int n=0;var inv=inv(t,y);for(int i=0;i<inv.getSlots();i++)n+=inv.getStackInSlot(i).getCount();return n;}
    private static BigDecimal points(){return s==null?BigDecimal.ZERO:s.getHyperNetNode().researchTask().progress();}
    private static FactoryRecipeThread coreThread(){return ((TileFactoryController)s).getFactoryRecipeThreadList().stream().filter(t->t.getThreadName().equals(HyperNetDefinition.RESEARCH_THREAD)).findFirst().orElseThrow();}
    private static void overclock(double value){var tile=(TileSmartInterface)world.getBlockEntity(S.above(3));tile.getOrCreate("overclocking").setNumber(value);tile.setChanged();}
    private static void provide(String id){var failure=s.getHyperNetNode().provideResearchTask(id,ACTOR);require(failure==null,"provide "+id+": "+failure);}
    private static void freeze(BlockPos pos,boolean value){world.setBlockAndUpdate(pos.east(),(value?Blocks.REDSTONE_BLOCK:Blocks.AIR).defaultBlockState());}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(server==null||finished)return;
        try{
            if(event.phase==TickEvent.Phase.END){if(readyFinish)finish(null);return;}
            tick++;require(tick<1000&&tick-since<180,"timeout phase "+phase+" task="+s.getHyperNetNode().researchTask());
            if(betaAccounting){
                var snapshot=s.getHyperNetNode().researchTask();BigDecimal current=snapshot.id().isEmpty()?BigDecimal.valueOf(19):snapshot.progress();
                int advance=a.getRecipeTicks()-previousConsumerTicks;BigDecimal delta=current.subtract(lastBeta);
                require(delta.signum()>=0&&delta.compareTo(BigDecimal.valueOf(4))<=0,"overclock exceeded leftover budget delta="+delta+" normal="+advance);
                if(delta.signum()>0){require(advance==1,"research overclock starved normal recipe");require(1000-power(p).getStoredEnergy()==1+11*(delta.intValueExact()+advance),"physical FE/compute conservation");}
                previousConsumerTicks=a.getRecipeTicks();lastBeta=current;
            }
            for(var t:List.of(c,p,d,c2))energy(t,1000);energy(s,feedS?1000:0);energy(s2,1000);
            switch(phase){
                case 0->{if(tick<5||!c.getHyperNetNode().working()||!p.getHyperNetNode().working())return;
                    if(!initiallyConnected){for(var t:List.of(p,d,a,s))connect(t);initiallyConnected=true;}
                    if(!d.getHyperNetNode().working())return;
                    if(Boolean.getBoolean("modularmachinery.hypernetTerminalTest")&&!HyperNetTerminalVerification.verify(world,c,p,d,s,ACTOR))return;
                    require(s2.getHyperNetNode().connect(C,ACTOR)==HyperNetNode.ConnectResult.TYPE_LIMIT,"second station admitted");
                    require(!s.getHyperNetNode().working()&&count(s,5)==64,"idle station core consumed material");
                    for(var pair:Map.of("station_beta","dependency","station_high","tech","absent","unknown").entrySet())require(s.getHyperNetNode().provideResearchTask(pair.getKey(),ACTOR).endsWith(pair.getValue()),"bad task admitted");
                    require(s.getHyperNetNode().provideResearchTask("station_root",UUID.randomUUID()).endsWith("permission"),"task ownership");
                    require(s.getHyperNetNode().provideResearchTask(null,ACTOR).endsWith("unknown"),"null task crashed/accepted");
                    provide("station_root");require(s.getHyperNetNode().provideResearchTask("station_beta",ACTOR).endsWith("busy"),"task replaced without cancellation");next(1);
                }
                case 1->{if(points().compareTo(BigDecimal.valueOf(12))<0)return;
                    require(count(s,7)==0&&count(s,5)==63,"short recipe produced before research completion");
                    var old=points();int fe=power(p).getStoredEnergy();String duplicate=HyperNetNode.workResearchCore(s,coreThread());require(duplicate!=null&&duplicate.endsWith("wait_tick")&&points().equals(old)&&power(p).getStoredEnergy()==fe,"duplicate work billed/granted");
                    var tag=s.getHyperNetData();for(int i=0;i<1000;i++)s.getHyperNetNode().researchTask();require(tag.equals(s.getHyperNetData())&&!s.getUpdateTag().contains("hypernetData"),"snapshot changed/leaked private task");
                    frozen=points();feedS=false;energy(s,0);next(2);
                }
                case 2->{if(tick-since<3)return;require(points().compareTo(frozen)==0,"unpowered station progressed");feedS=true;freeze(S,true);next(3);}
                case 3->{if(tick-since<3)return;require(points().compareTo(frozen)==0,"redstone station progressed");freeze(S,false);freeze(D,true);next(4);}
                case 4->{if(tick-since<3)return;require(points().compareTo(frozen)==0,"offline database lost paid work");freeze(D,false);next(5);}
                case 5->{if(points().compareTo(frozen)<=0)return;
                    var tag=s.saveWithoutMetadata();oldNode=s.getHyperNetNode();frozen=points();s.load(tag);require(oldNode.isClosed(),"load retained old station runtime");next(6);
                }
                case 6->{if(points().compareTo(frozen)<=0)return;
                    require(s.getHyperNetNode().cancelResearchTask(ACTOR)&&s.getHyperNetNode().researchTask().id().isEmpty()&&!coreThread().hasActiveRecipe(),"cancel did not clear task/core");
                    require(count(s,7)==0,"cancel emitted output");provide("station_root");require(points().compareTo(frozen)>=0,"cancel/reassign lost database progress");next(7);
                }
                case 7->{if(!s.getHyperNetNode().researchTask().id().isEmpty())return;
                    require(count(s,7)==1&&a.getHyperNetNode().researchFailure(List.of("station_root"))==null,"finished research not stored/unlocked");
                    require(s.getHyperNetNode().provideResearchTask("station_root",ACTOR).endsWith("already_complete"),"completed research restarted");
                    input(a,Items.DIRT,1);a.requestNormalRecipeSearch();next(70);
                }
                case 70->{if(a.getRecipeTicks()<3)return;
                    provide("station_beta");overclock(5);lastBeta=BigDecimal.ZERO;previousConsumerTicks=a.getRecipeTicks();betaAccounting=true;next(8);
                }
                case 8->{if(points().signum()>0&&heldOutputs==null){
                        var out=inv(s,7);heldOutputs=new ItemStack[out.getSlots()];for(int i=0;i<out.getSlots();i++){heldOutputs[i]=out.getStackInSlot(i).copy();out.setStackInSlot(i,new ItemStack(Items.COBBLESTONE,64));}
                    }
                    if(!s.getHyperNetNode().researchTask().awaitingCoreCompletion())return;betaAccounting=false;
                    require(lastBeta.intValueExact()==19&&d.getHyperNetNode().database().progress("station_beta")==null,"final point/progress cleanup");freeze(D,true);next(81);
                }
                case 81->{if(tick-since<3)return;
                    require(s.getHyperNetNode().researchTask().awaitingCoreCompletion()&&points().intValueExact()==19,"output wait cleared/repeated research");
                    var out=inv(s,7);for(int i=0;i<out.getSlots();i++){require(out.getStackInSlot(i).is(Items.COBBLESTONE)&&out.getStackInSlot(i).getCount()==64,"blocked output changed");out.setStackInSlot(i,heldOutputs[i]);}next(82);
                }
                case 82->{if(!s.getHyperNetNode().researchTask().id().isEmpty())return;
                    require(count(s,7)==2,"committed research could not finish after database stopped/duplicate output");freeze(D,false);next(83);
                }
                case 83->{if(!d.getHyperNetNode().working())return;
                    freeze(A,true);overclock(1);
                    var tag=s.getHyperNetData();var bad=tag.copy();var future=new CompoundTag();future.putInt("version",8);bad.put("researchTask",future);s.setHyperNetData(bad);
                    require(!s.getHyperNetNode().researchTask().valid()&&s.getHyperNetNode().provideResearchTask("station_resume",ACTOR).endsWith("invalid_data")&&!s.getHyperNetNode().cancelResearchTask(ACTOR)&&s.getHyperNetData().equals(bad),"future task overwritten/used");s.setHyperNetData(tag);
                    provide("station_resume");next(9);
                }
                case 9->{if(points().compareTo(BigDecimal.valueOf(6))<0)return;frozen=points();
                    require(s.getHyperNetNode().connect(C2,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"move station network");
                    require(s2.getHyperNetNode().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"freed station slot not reusable");next(10);
                }
                case 10->{if(tick-since<3)return;require(points().compareTo(frozen)==0,"task crossed network");
                    require(s.getHyperNetNode().connect(C,ACTOR)==HyperNetNode.ConnectResult.TYPE_LIMIT,"per-network station cap bypass");s2.getHyperNetNode().disconnect();connect(s);next(11);
                }
                case 11->{if(points().compareTo(frozen)<=0)return;
                    freeze(S,true);var journal=new JsonObject();journal.addProperty("points",points().toString());Files.writeString(mainDir().resolve("station-resume-check.json"),journal.toString());
                    oldNode=s.getHyperNetNode();readyFinish=true;next(12);
                }
                case 20->{if(points().compareTo(base)<=0)return;
                    require(points().subtract(base).compareTo(BigDecimal.valueOf(2L*(tick-since)))<=0,"offline catch-up research");
                    require(d.getHyperNetNode().researchFailure(List.of("station_root","station_beta"))==null,"completed research lost after restart");
                    require(d.getHyperNetNode().researchProgress("station_resume").compareTo(base)>=0&&count(s,7)==2,"partial progress/output after restart");oldNode=s.getHyperNetNode();freeze(S,true);readyFinish=true;next(21);
                }
            }
        }catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error){if(finished)return;finished=true;failed=error!=null;if(error!=null)Modularmachinery.LOG.error("[MMC][hypernet-station] FAIL phase "+phase,error);else Modularmachinery.LOG.info("[MMC][hypernet-station] runtime checks PASS resume={}",resume);server.halt(false);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){if(server!=event.getServer()||failed)return;if(oldNode==null||!oldNode.isClosed())Modularmachinery.LOG.error("[MMC][hypernet-station] OVERALL: FAIL cleanup");else Modularmachinery.LOG.info("[MMC][hypernet-station] OVERALL: PASS resume={} (real core/module FE, prerequisites/tier/owner, singleton, no-task and genuine stops, cancellation/recovery, leftover overclock with normal consumer, completion/output conservation, private persistence, network isolation and shutdown)",resume);}
}
