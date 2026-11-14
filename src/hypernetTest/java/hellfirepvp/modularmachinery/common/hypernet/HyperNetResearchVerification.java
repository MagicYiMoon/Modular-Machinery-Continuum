/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.ResearchRequirementType;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;

/** Real ordinary/factory recipe gates; progress is written by trusted fixture API, not a research station. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetResearchVerification {
    private static MinecraftServer server; private static ServerLevel world;
    private static TileController c,d,e,a,f;
    private static int tick,phase,since; private static boolean finished,failed;
    private static HyperNetNode oldC,oldD; private static Path researchDir;
    private static CompoundTag saved;
    private static boolean initiallyConnected;
    private static void require(boolean v,String message){if(!v)throw new AssertionError(message);}
    private static void rejects(Runnable action){try{action.run();throw new AssertionError("invalid input accepted");}catch(IllegalArgumentException | JsonParseException expected){}}
    private static void next(int value){phase=value;since=tick;Modularmachinery.LOG.info("[MMC][hypernet-research] phase {} tick {}",phase,tick);}
    private static HyperNetResearch definition(String id,List<String> deps){return new HyperNetResearch(id,id,1,new BigDecimal("100"),BigDecimal.ONE,deps,List.of(),List.of(),false);}
    public static void begin(MinecraftServer srv,ServerLevel level){
        server=srv;world=level;
        try {
            researchDir=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().getParent().resolve("research");
            Files.createDirectories(researchDir);
            Files.writeString(researchDir.resolve("alpha.json"),"{\"id\":\"alpha\",\"display-name\":\"基础研究\",\"required-points\":100,\"minimum-per-tick\":1}");
            Files.writeString(researchDir.resolve("beta.json"),"{\"id\":\"beta\",\"dependencies\":[\"alpha\"],\"required-points\":100,\"minimum-per-tick\":1}");
            HyperNetResearchRegistry.reload(researchDir); ledgerChecks();
            if (net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")) {
                var script = MachineReloadListener.getRecipeLoader().getRecipe("hn_research_script_probe");
                require(script != null && script.requirements().stream().anyMatch(r -> r instanceof RequirementResearch gate && gate.research().equals(List.of("alpha","beta"))),"ZenCode requireResearch varargs");
            }
            machine("hr_center","{\"role\":\"center\",\"carrying\":10,\"max-connections\":16}");
            machine("hr_db","{\"role\":\"database\",\"storage-capacity\":1}");
            machine("hr_normal","{\"role\":\"node\"}");machine("hr_factory","{\"role\":\"node\"}");
            core("hr_center");core("hr_db");consumer("hr_normal",Items.DIAMOND);consumer("hr_factory",Items.EMERALD);
            MachineReloadListener.refreshRecipes();
            c=build(new BlockPos(2028,120,2908),"hr_center",true);d=build(c.getBlockPos().east(80),"hr_db",true);
            e=build(d.getBlockPos().east(80),"hr_db",true);a=build(e.getBlockPos().east(80),"hr_normal",false);f=build(a.getBlockPos().east(80),"hr_factory",true);
            input(a,2);input(f,2);next(0);
        } catch(Throwable error){finish(error);}
    }
    private static void ledgerChecks() throws Exception {
        require(definition("研究一",List.of()).id().equals("研究一"),"Unicode research id");
        rejects(()->HyperNetResearchRegistry.validate(List.of(definition("a",List.of("unknown")))));
        rejects(()->HyperNetResearchRegistry.validate(List.of(definition("a",List.of("b")),definition("b",List.of("a")))));
        rejects(()->HyperNetResearchRegistry.validate(List.of(definition("a",List.of()),definition("a",List.of()))));
        var chain=new ArrayList<HyperNetResearch>();for(int i=0;i<4096;i++)chain.add(definition("r"+i,i==0?List.of():List.of("r"+(i-1))));
        require(HyperNetResearchRegistry.validate(chain).size()==4096,"deep graph validation");
        rejects(()->HyperNetResearch.parse(JsonParser.parseString("{\"id\":\"x\",\"required-points\":0,\"minimum-per-tick\":1}").getAsJsonObject()));
        rejects(()->HyperNetResearch.parse(JsonParser.parseString("{\"id\":\"x\",\"required-points\":1,\"minimum-per-tick\":1,\"cycle-research\":true}").getAsJsonObject()));
        for (var n : List.of(new BigDecimal("0E-2147483647"),new BigDecimal("1E-10000"),new BigDecimal("1E10000"),new BigDecimal("-1")))
            require(!new HyperNetDatabaseState("m",1,null).write("alpha",n),"unsafe decimal progress accepted");
        var shrink=new HyperNetDatabaseState("m",2,null);shrink.write("alpha",BigDecimal.TEN);shrink.write("beta",BigDecimal.TEN);
        var reduced=new HyperNetDatabaseState("m",1,shrink.save());
        require(reduced.valid()&&reduced.used()==2&&reduced.write("alpha",new BigDecimal("100"))&&reduced.complete("alpha")&&reduced.used()==2,"capacity shrink lost existing slots");
        var db=new HyperNetDatabaseState("m",1,null);require(db.write("alpha",new BigDecimal("40.25")),"progress");
        var precise=new HyperNetDatabaseState("m",1,null);var earned=BigDecimal.ONE.subtract(new BigDecimal("1E-300"));
        require(precise.write("alpha",earned)&&new HyperNetDatabaseState("m",1,precise.save()).progress("alpha").equals(earned),"exact accumulated progress beyond definition precision lost");
        require(!HyperNetResearch.supportedProgress(new BigDecimal("0."+"1".repeat(1025))),"unbounded accumulated precision accepted");
        require(!db.write("alpha",new BigDecimal("40"))&&!db.write("beta",BigDecimal.ZERO)&&!db.complete("alpha"),"capacity/regression/incomplete");
        require(db.write("alpha",new BigDecimal("100"))&&db.complete("alpha")&&db.complete("alpha")&&db.used()==1,"atomic slot conversion/idempotence");
        var raw=db.save();require(new HyperNetDatabaseState("m",1,raw).has("alpha"),"NBT round trip");
        var future=raw.copy();future.putInt("version",2);require(!new HyperNetDatabaseState("m",1,future).valid(),"future version");
        require(!new HyperNetDatabaseState("foreign",1,raw).valid(),"machine identity");
        var corrupt=raw.copy();corrupt.putString("completed","bad");require(!new HyperNetDatabaseState("m",1,corrupt).valid(),"corrupt list");
        var malformed=new HyperNetDatabaseState("m",1,null);require(malformed.write("alpha",BigDecimal.ZERO),"zero progress");
        var exponent=malformed.save();exponent.getCompound("progress").putString("alpha","0E-2147483647");
        require(!new HyperNetDatabaseState("m",1,exponent).valid(),"zero exponent bomb retained");
        var oversized=raw.copy();var tooMany=new ListTag();for(int i=0;i<4097;i++)tooMany.add(StringTag.valueOf("r"+i));oversized.put("completed",tooMany);
        require(!new HyperNetDatabaseState("m",1,oversized).valid(),"oversized NBT accepted");
        var duplicate=raw.copy();duplicate.getList("completed",Tag.TAG_STRING).add(StringTag.valueOf("alpha"));require(!new HyperNetDatabaseState("m",1,duplicate).valid(),"duplicate NBT");
        var empty=Files.createTempDirectory("mmc-research-empty-");HyperNetResearchRegistry.reload(empty);var unknown=new HyperNetDatabaseState("m",1,raw);
        require(!unknown.has("alpha")&&unknown.used()==1&&unknown.save().equals(raw),"unknown research preservation");Files.delete(empty);HyperNetResearchRegistry.reload(researchDir);
        require(unknown.has("alpha"),"unknown research restored");
        var type=new ResearchRequirementType();var good=JsonParser.parseString("{\"type\":\"modularmachinery:research\",\"io-type\":\"input\",\"research\":[\"alpha\",\"beta\"]}").getAsJsonObject();
        rejects(()->type.createRequirement(IOType.OUTPUT,good));
        for(var key:List.of("chance","parallelizeUnaffected","amount")){var bad=good.deepCopy();bad.addProperty(key,1);rejects(()->type.createRequirement(IOType.INPUT,bad));}
        var tooLong=good.deepCopy();var many=new JsonArray();for(int i=0;i<65;i++)many.add("r"+i);tooLong.add("research",many);rejects(()->type.createRequirement(IOType.INPUT,tooLong));
        rejects(()->new RequirementResearch(List.of("alpha","alpha")));
        for (var profile : List.of("{\"role\":\"database\"}","{\"role\":\"database\",\"storage-capacity\":0}","{\"role\":\"database\",\"storage-capacity\":1.5}","{\"role\":\"database\",\"storage-capacity\":4097}","{\"role\":\"node\",\"storage-capacity\":1}"))
            rejects(()->HyperNetDefinition.parse(JsonParser.parseString(profile)));
        var bad=good.deepCopy();bad.addProperty("triggerTime",1);rejects(()->type.createRequirement(IOType.INPUT,bad));
        Modularmachinery.LOG.info("[MMC][hypernet-research] catalog/ledger/strict parser checks PASS");
    }
    private static void machine(String name,String profile)throws Exception {
        var path=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(name+".json");
        Files.writeString(path,"{\"registryname\":\""+name+"\",\"requires-blueprint\":false,\"has-factory\":true,\"parts\":[{\"y\":-1,\"elements\":\"modularmachinery:blockenergyinputhatch\"},{\"y\":1,\"elements\":\"minecraft:gold_block\"},{\"y\":5,\"elements\":\"modularmachinery:blockinputbus\"},{\"y\":7,\"elements\":\"modularmachinery:blockoutputbus\"}],\"hypernet\":"+profile+"}");
        var m=MachineReloadListener.getLoader().parseMachineFile(path.toFile()).orElseThrow();
        require(MachineLoader.fromSnapshot(MachineReloadListener.getLoader().snapshot()).findByName(name).getHyperNetDefinition().equals(m.getHyperNetDefinition()),"machine snapshot");
    }
    private static void presets(){for(var name:List.of("hr_center","hr_db"))MachineReloadListener.getLoader().findByName(name).addCoreThreadPreset(HyperNetDefinition.CENTER_THREAD,List.of(name+"_work"));}
    private static void core(String name){
        MachineReloadListener.getLoader().findByName(name).addCoreThreadPreset(HyperNetDefinition.CENTER_THREAD,List.of(name+"_work"));
        ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(new MachineRecipe(name,name+"_work",2,List.of(new RequirementEnergy(IOType.INPUT,1)),null,false,1,HyperNetDefinition.CENTER_THREAD),Map.of()));
    }
    private static void consumer(String name,Item out){
        var recipe=new MachineRecipe(name,name+"_work",30,List.of(new RequirementResearch(List.of("alpha","beta")),new RequirementItem(IOType.INPUT,"minecraft:dirt",1),new RequirementItem(IOType.OUTPUT,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(out),1)),null,true,1,"");
        var encoded=RecipeSnapshotCodec.encode(recipe,Map.of());require(((RequirementResearch)RecipeSnapshotCodec.decode(encoded).requirements().get(0)).research().equals(List.of("alpha","beta")),"recipe snapshot");ScriptRecipeRegistry.register(encoded);
    }
    private static TileController build(BlockPos pos,String name,boolean factory){
        world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());world.setBlockAndUpdate(pos.above(),Blocks.GOLD_BLOCK.defaultBlockState());
        world.setBlockAndUpdate(pos.above(5),RegistryBlocks.itemInputBus.get().defaultBlockState());world.setBlockAndUpdate(pos.above(7),RegistryBlocks.itemOutputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos,(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get()).defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var t=(TileController)world.getBlockEntity(pos);t.setOwner(UUID.fromString("ad7762e3-0c61-4ab5-8367-b512cba87c93"));t.setMachineName(name);
        require(MachineFormation.tryForm(world,pos,Direction.NORTH,MachineReloadListener.getLoader().findByName(name),MachineReloadListener.getLoader()).formed(),"formation "+name);t.setFormed(true);t.acknowledgeStructureCheck();return t;
    }
    private static IItemHandlerModifiable inventory(TileController t,int y){return (IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(t.getBlockPos().above(y))).getInventory();}
    private static void input(TileController t,int n){inventory(t,5).setStackInSlot(0,new ItemStack(Items.DIRT,n));}
    private static int count(TileController t,int y){int count=0;var inv=inventory(t,y);for(int i=0;i<inv.getSlots();i++)count+=inv.getStackInSlot(i).getCount();return count;}
    private static void search(){a.requestNormalRecipeSearch();((TileFactoryController)f).requestImmediateRecipeSearch();}
    private static void connect(TileController t){require(t.getHyperNetNode().connect(c.getBlockPos(),c.getOwner())==HyperNetNode.ConnectResult.SUCCESS,"connection "+t.getMachineName());}
    private static TileEnergyHatch power(TileController t){return (TileEnergyHatch)world.getBlockEntity(t.getBlockPos().below());}
    private static void fill(TileController t){var h=power(t);if(h.getStoredEnergy()<1000)h.produceInternal(1000-h.getStoredEnergy(),false);}
    private static boolean cores(){return c.getHyperNetNode().working()&&d.getHyperNetNode().working()&&e.getHyperNetNode().working();}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(server==null||finished)return;
        try {
            if(event.phase==TickEvent.Phase.START){for(var t:List.of(c,d,e))fill(t);return;}
            tick++;require(tick<650&&tick-since<140,"timeout phase "+phase);
            switch(phase){
                case 0->{
                    if(tick<5||!c.getHyperNetNode().working())return;
                    if(!initiallyConnected){for(var t:List.of(d,e,a,f))connect(t);initiallyConnected=true;}
                    if(!cores())return;search();next(1);
                }
                case 1->{
                    require(count(a,5)==2&&count(f,5)==2&&count(a,7)==0&&count(f,7)==0,"missing research consumed input");
                    if(tick-since<6)return;
                    require(d.getHyperNetNode().writeResearchProgress("alpha",new BigDecimal("100"))&&d.getHyperNetNode().completeResearch("alpha"),"store alpha");
                    require(!d.getHyperNetNode().writeResearchProgress("beta",BigDecimal.ZERO),"capacity ignored");
                    require(a.getHyperNetNode().researchFailure(List.of("alpha"))==null&&a.getHyperNetNode().researchFailure(List.of("alpha","beta")).endsWith("missing"),"partial union");
                    require(e.getHyperNetNode().writeResearchProgress("beta",new BigDecimal("25.5")),"store progress");
                    var data=e.getHyperNetData();var disk=e.saveWithoutMetadata();require(disk.getCompound("hypernetData").equals(data)&&!e.getUpdateTag().contains("hypernetData"),"private persistence");
                    var reloaded=new HyperNetDatabaseState("hr_db",1,data.get("database"));require(reloaded.progress("beta").equals(new BigDecimal("25.5")),"partial progress persistence");
                    require(e.getHyperNetNode().writeResearchProgress("beta",new BigDecimal("100"))&&e.getHyperNetNode().completeResearch("beta"),"finish beta");
                    long fe=power(c).getStoredEnergy()+power(d).getStoredEnergy()+power(e).getStoredEnergy();saved=e.getHyperNetData();
                    int connections=c.getHyperNetNode().connectionCount();
                    for(int i=0;i<1000;i++)require(a.getHyperNetNode().researchFailure(List.of("alpha","beta"))==null,"read-only gate");
                    require(fe==power(c).getStoredEnergy()+power(d).getStoredEnergy()+power(e).getStoredEnergy()&&saved.equals(e.getHyperNetData())&&connections==c.getHyperNetNode().connectionCount(),"query mutated FE/NBT/connections");
                    search();next(2);
                }
                case 2->{
                    if(count(a,5)!=1||count(f,5)!=1)return;
                    world.setBlockAndUpdate(e.getBlockPos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());next(3);
                }
                case 3->{
                    require(a.getHyperNetNode().researchFailure(List.of("beta"))!=null,"stopped database unlocked new craft");
                    require(count(a,5)==1&&count(f,5)==1,"second startup bypass");
                    if(count(a,7)!=1||count(f,7)!=1)return;
                    require(tick-since>=25,"unexpected shortened recipe");world.setBlockAndUpdate(e.getBlockPos().east(),Blocks.AIR.defaultBlockState());next(4);
                }
                case 4->{
                    if(!cores())return;search();if(count(a,7)!=2||count(f,7)!=2)return;
                    saved=e.getHyperNetData();e.getHyperNetNode().disconnect();require(a.getHyperNetNode().researchFailure(List.of("beta"))!=null,"disconnected database unlocked");
                    e.setHyperNetData(saved);connect(e);
                    var bad=saved.copy();bad.getCompound("database").putInt("version",8);e.setHyperNetData(bad);
                    require(!e.getHyperNetNode().writeResearchProgress("beta",BigDecimal.ZERO)&&a.getHyperNetNode().researchFailure(List.of("beta"))!=null&&e.getHyperNetData().equals(bad),"future data overwritten/used");e.setHyperNetData(saved);
                    // Real definition reload retires all runtime instances; saved physical records remain.
                    oldC=c.getHyperNetNode();oldD=d.getHyperNetNode();MachineReloadListener.reloadNow();presets();next(5);
                }
                case 5->{
                    if(!cores())return;for(var t:List.of(d,e,a,f))connect(t);
                    require(oldC.isClosed()&&oldD.isClosed()&&saved.equals(e.getHyperNetData())&&a.getHyperNetNode().researchFailure(List.of("alpha","beta"))==null,"reload lost records/revived runtime");
                    var beta=Files.readString(researchDir.resolve("beta.json"));Files.writeString(researchDir.resolve("beta.json"),"{\"id\":\"beta\",\"required-points\":100,\"minimum-per-tick\":1,\"dependencies\":[\"absent\"]}");HyperNetResearchRegistry.reload(researchDir);
                    require(HyperNetResearchRegistry.snapshot().isEmpty()&&a.getHyperNetNode().researchFailure(List.of("alpha")).endsWith("unknown")&&saved.equals(e.getHyperNetData()),"invalid batch stayed unlocked/changed NBT");
                    Files.writeString(researchDir.resolve("beta.json"),beta);HyperNetResearchRegistry.reload(researchDir);
                    require(a.getHyperNetNode().researchFailure(List.of("alpha","beta"))==null,"catalog restore");
                    oldC=c.getHyperNetNode();oldD=d.getHyperNetNode();finish(null);
                }
            }
        }catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error){if(finished)return;finished=true;failed=error!=null;if(error!=null)Modularmachinery.LOG.error("[MMC][hypernet-research] FAIL phase "+phase,error);else Modularmachinery.LOG.info("[MMC][hypernet-research] runtime checks PASS");server.halt(false);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){if(server!=event.getServer()||failed)return;if(oldC==null||!oldC.isClosed()||!oldD.isClosed())Modularmachinery.LOG.error("[MMC][hypernet-research] OVERALL: FAIL cleanup");else Modularmachinery.LOG.info("[MMC][hypernet-research] OVERALL: PASS (catalog graphs, bounded private ledger, real ordinary/factory startup gates, database union, no input loss, mid-craft offline, disconnect, definition reload, unknown/future records, query conservation, shutdown; fixture API writes progress, no station claim)");}
}
