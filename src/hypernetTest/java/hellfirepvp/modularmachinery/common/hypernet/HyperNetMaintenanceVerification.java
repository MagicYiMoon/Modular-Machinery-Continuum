/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
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

/** Real material recipes and real core work; opt-in disposable dedicated-server world only. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetMaintenanceVerification {
    private static MinecraftServer server;private static ServerLevel world;
    private static TileController center,processor,consumer,normal;private static HyperNetNode old;
    private static HyperNetDefinition centerDefinition;
    private static int phase,tick,since;private static boolean finished,failed;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void near(double value,double expected,String message){require(Math.abs(value-expected)<1e-9,message+" actual="+value);}
    private static void next(int value){phase=value;since=tick;Modularmachinery.LOG.info("[MMC][hypernet-maintenance] phase {} tick {}",phase,tick);}
    private static DynamicMachine machine(TileController c){return MachineReloadListener.getLoader().findByName(c.getMachineName());}
    private static HyperNetNode pn(){return processor.getHyperNetNode();}private static HyperNetNode cn(){return center.getHyperNetNode();}
    private static MachineContext context(TileController c){return new MachineContext(world,c.getBlockPos(),Direction.NORTH,machine(c),new MachineVariables(),null);}
    private static RequirementHyperNetMaintenance cooling(int amount){return new RequirementHyperNetMaintenance(RequirementHyperNetMaintenance.Kind.COOLING,amount);}
    private static RequirementHyperNetMaintenance repair(int amount){return new RequirementHyperNetMaintenance(RequirementHyperNetMaintenance.Kind.CIRCUIT_REPAIR,amount);}
    public static void begin(MinecraftServer srv,ServerLevel level,TileController c,TileController p,TileController a){
        server=srv;world=level;center=c;processor=p;consumer=a;
        try {
            parserChecks();
            if(net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")) {
                for(var id:List.of("hn_cooling_demo","hn_repair_demo")) {
                    var loaded=MachineReloadListener.getRecipeLoader().getRecipe(id);require(loaded!=null,"CT maintenance example missing: "+id);
                    var req=loaded.requirements().stream().filter(RequirementHyperNetMaintenance.class::isInstance).map(RequirementHyperNetMaintenance.class::cast).findFirst().orElseThrow();
                    require(req.amount()==(id.contains("cooling")?20:100),"CT maintenance amount");
                }
                require(MachineReloadListener.getLoader().findByName("hn_sample_center").getHyperNetDefinition().circuit().capacity()==10000,"example circuit definition");
            }
            var original=machine(p).getHyperNetDefinition();centerDefinition=machine(c).getHyperNetDefinition();
            machine(p).setHyperNetDefinition(new HyperNetDefinition(original.role(),original.workingThread(),0,0,0,original.modules(),"",new HyperNetThermalDefinition(20,0,"")));
            extend(p,4);extend(c,2);
            machine(p).setParallelizable(true);machine(p).setMaxParallelism(2);machine(p).setInternalParallelism(2);
            machine(c).setParallelizable(true);machine(c).setMaxParallelism(2);machine(c).setInternalParallelism(2);
            machine(p).addCoreThreadPreset(HyperNetDefinition.RADIATOR_THREAD,List.of("hm_cool","hm_zero"));
            machine(c).addCoreThreadPreset(HyperNetDefinition.MAINTENANCE_THREAD,List.of("hm_repair"));
            var fixed=cooling(3);fixed.setParallelizeUnaffected(true);
            recipe(p.getMachineName(),"hm_cool",12,Items.DIRT,Items.GRAVEL,HyperNetDefinition.RADIATOR_THREAD,List.of(cooling(7),fixed));
            recipe(p.getMachineName(),"hm_zero",1,Items.SAND,Items.CLAY_BALL,HyperNetDefinition.RADIATOR_THREAD,List.of(cooling(3)));
            recipe(c.getMachineName(),"hm_repair",100,Items.IRON_INGOT,Items.REDSTONE,HyperNetDefinition.MAINTENANCE_THREAD,List.of(repair(50),repair(50)));
            normal=buildNormal();
            MachineReloadListener.refreshRecipes();reform(p);reform(c);reform(normal);next(0);
        }catch(Throwable error){finish(error);}
    }
    private static void extend(TileController c,int y){
        machine(c).getPattern().addPart(new MachinePattern.Part(0,y,0,"modularmachinery:blockinputbus"));
        machine(c).getPattern().addPart(new MachinePattern.Part(0,y+1,0,"modularmachinery:blockoutputbus"));
        world.setBlockAndUpdate(c.getBlockPos().above(y),RegistryBlocks.itemInputBus.get().defaultBlockState());
        world.setBlockAndUpdate(c.getBlockPos().above(y+1),RegistryBlocks.itemOutputBus.get().defaultBlockState());
    }
    private static void reform(TileController c){
        var result=MachineFormation.tryForm(world,c.getBlockPos(),Direction.NORTH,machine(c),MachineReloadListener.getLoader());
        require(result.formed(),"maintenance formation: "+result.report());c.setFormed(true);c.acknowledgeStructureCheck();
    }
    private static TileController buildNormal()throws Exception{
        String json="{\"registryname\":\"hm_normal\",\"requires-blueprint\":false,\"parts\":["
                +"{\"x\":0,\"y\":-1,\"z\":0,\"elements\":\"modularmachinery:blockenergyinputhatch\"},"
                +"{\"x\":0,\"y\":1,\"z\":0,\"elements\":\"minecraft:gold_block\"},"
                +"{\"x\":0,\"y\":2,\"z\":0,\"elements\":\"modularmachinery:blockinputbus\"},"
                +"{\"x\":0,\"y\":3,\"z\":0,\"elements\":\"modularmachinery:blockoutputbus\"}],"
                +"\"hypernet\":{\"role\":\"processor\",\"thermal\":{\"overheat-threshold\":1000,\"heat-distribution\":4}}}";
        var file=Files.createTempDirectory("mmc-maintenance-").resolve("hm_normal.json");Files.writeString(file,json);
        MachineReloadListener.getLoader().parseMachineFile(file.toFile()).orElseThrow();Files.delete(file);Files.delete(file.getParent());
        recipe("hm_normal","hm_normal",120,Items.DIRT,Items.DIAMOND,"",List.of(cooling(80)));
        var pos=new BlockPos(2028,120,2508);world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(),Blocks.GOLD_BLOCK.defaultBlockState());
        world.setBlockAndUpdate(pos.above(2),RegistryBlocks.itemInputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(3),RegistryBlocks.itemOutputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos,RegistryBlocks.blockController.get().defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var c=(TileController)world.getBlockEntity(pos);c.setOwner(center.getOwner());c.setMachineName("hm_normal");return c;
    }
    private static void recipe(String machine,String id,int duration,Item in,Item out,String thread,List<Requirement> effects){
        var requirements=new ArrayList<>(effects);requirements.add(new RequirementEnergy(IOType.INPUT,1));
        requirements.add(new RequirementItem(IOType.INPUT,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(in),1));
        requirements.add(new RequirementItem(IOType.OUTPUT,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(out),1));
        var recipe=new MachineRecipe(machine,id,duration,requirements,null,false,-1,thread).withMetadata(List.of(),true,true);
        var encoded=RecipeSnapshotCodec.encode(recipe,Map.of());
        require(encoded.equals(RecipeSnapshotCodec.encode(RecipeSnapshotCodec.decode(encoded),Map.of())),"maintenance snapshot changed");
        ScriptRecipeRegistry.register(encoded);
    }
    private static int inputY(TileController c){return c==processor?4:2;}
    private static IItemHandlerModifiable inventory(TileController c,boolean out){return (IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(c.getBlockPos().above(inputY(c)+(out?1:0)))).getInventory();}
    private static void input(TileController c,Item item,int count){inventory(c,false).setStackInSlot(0,new ItemStack(item,count));}
    private static void blockOutput(TileController c,boolean block){var inv=inventory(c,true);for(int i=0;i<inv.getSlots();i++)inv.setStackInSlot(i,block?new ItemStack(Items.BEDROCK,64):ItemStack.EMPTY);}
    private static int output(TileController c,Item item){var inv=inventory(c,true);int total=0;for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(item))total+=inv.getStackInSlot(i).getCount();return total;}
    private static FactoryRecipeThread thread(TileController c,String name){return ((TileFactoryController)c).getFactoryRecipeThreadList().stream().filter(t->t.getThreadName().equals(name)).findFirst().orElse(null);}
    private static void fill(TileController c){if(c==null)return;var h=(TileEnergyHatch)world.getBlockEntity(c.getBlockPos().below());if(h.getStoredEnergy()<1000)h.produceInternal(1000-h.getStoredEnergy(),false);}
    private static void circuitConfig(int wear){
        var d=centerDefinition;machine(center).setHyperNetDefinition(new HyperNetDefinition(d.role(),d.workingThread(),100,1,1,d.modules(),d.connectionCard(),null,new HyperNetCircuitDefinition(100,wear,wear,1)));
        MachineReloadListener.refreshRecipes();
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(server==null||finished)return;
        try{
            if(event.phase==TickEvent.Phase.START){fill(center);fill(processor);fill(normal);return;}
            tick++;require(tick<1200&&tick-since<300,"maintenance timeout phase "+phase+" processor="+pn().working()+" available="+consumer.getHyperNetNode().available()+" formed="+processor.isFormed()+" circuit="+cn().circuitDurability());
            switch(phase){
                case 0->{if(tick<5||consumer.getHyperNetNode().available()<4)return;
                    require(context(processor).capabilities(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,MachineContext.ComponentKind.INPUT).size()==1
                            &&context(processor).capabilities(net.minecraftforge.common.capabilities.ForgeCapabilities.ITEM_HANDLER,MachineContext.ComponentKind.OUTPUT).size()==1,"upgrade inventories exposed to recipe IO");
                    require(consumer.getHyperNetNode().request(4,false).granted()==4,"first actual heating");near(pn().storedHeat(),8,"first work heat");next(1);}
                case 1->{double amount=Math.min(consumer.getHyperNetNode().available(),(20-pn().storedHeat())/2);if(amount<=0)return;require(consumer.getHyperNetNode().request(amount,false).granted()>0,"actual heating");if(pn().storedHeat()<20)return;near(pn().storedHeat(),20,"actual overheat");require(pn().overheated(),"not latched");
                    var fixed=cooling(3);fixed.setParallelizeUnaffected(true);var reqs=List.<Requirement>of(cooling(7),fixed);
                    require(HyperNetMaintenance.plan(context(processor),reqs,2,true).valid()&&!HyperNetMaintenance.plan(context(processor),reqs,3,true).valid(),"aggregate cooling/exemption");
                    var original=machine(processor).getHyperNetDefinition();
                    machine(processor).setHyperNetDefinition(new HyperNetDefinition(original.role(),original.workingThread(),0,0,0,original.modules(),"",new HyperNetThermalDefinition(20,0,"absent_segment")));
                    require(!HyperNetMaintenance.plan(context(processor),List.of(cooling(7)),2,true).valid()
                            &&HyperNetMaintenance.plan(context(processor),List.of(cooling(7)),1,true).valid(),"dynamic cooling segment cap");
                    machine(processor).setHyperNetDefinition(original);
                    input(processor,Items.DIRT,2);next(2);}
                case 2->{var t=thread(processor,HyperNetDefinition.RADIATOR_THREAD);if(t==null||!t.hasActiveRecipe()||t.getRecipeTicks()<1)return;
                    require(t.getParallelism()==2&&inventory(processor,false).getStackInSlot(0).isEmpty(),"cooling batch/materials");near(pn().storedHeat(),20,"cooling applied at start");blockOutput(processor,true);require(MachineTicker.outputAllocationFailure(context(processor),t.getRuntimeRecipeContext().getRequirements(),2)!=null,"fixture did not block actual cooling output");next(3);}
                case 3->{var t=thread(processor,HyperNetDefinition.RADIATOR_THREAD);near(pn().storedHeat(),20,"output wait cooled repeatedly slots="+inventory(processor,true).getStackInSlot(0)+" duration="+t.getRecipeDuration()+" progress="+t.getRecipeTicks()+" name="+t.getRecipeName());if(t.getRecipeTicks()<t.getRecipeDuration()||tick-since<18)return;
                    require(t.getBlockedReason()!=null,"missing output blocker");old=pn();processor.load(processor.saveWithoutMetadata());require(old.isClosed(),"load retained old thermal runtime");near(pn().storedHeat(),20,"load lost heat");blockOutput(processor,false);next(4);}
                case 4->{if(output(processor,Items.GRAVEL)==0)return;require(output(processor,Items.GRAVEL)==2,"cooling outputs batch");near(pn().storedHeat(),3,"cooling completion aggregate wrong");require(pn().overheated(),"latch cleared early");input(processor,Items.SAND,1);next(5);}
                case 5->{if(output(processor,Items.CLAY_BALL)==0)return;near(pn().storedHeat(),0,"zero cooling failed");require(!pn().overheated(),"active zero did not release latch");
                    consumer.getHyperNetNode().disconnect();circuitConfig(100);require(cn().circuitDurability()==100,"fresh circuit not full");
                    require(!HyperNetMaintenance.plan(context(center),List.of(repair(1)),1,true).valid(),"full circuit accepted repair");next(6);}
                case 6->{if(cn().circuitDurability()>0)return;require(!cn().working()&&pn().available()==0,"dead circuit network remained running");
                    require(HyperNetMaintenance.plan(context(center),List.of(repair(50),repair(50)),1,true).valid()
                            &&!HyperNetMaintenance.plan(context(center),List.of(repair(50),repair(50)),2,true).valid(),"repair aggregate room");input(center,Items.IRON_INGOT,1);next(7);}
                case 7->{require(cn().circuitDurability()==0,"repair applied before finish");var t=thread(center,HyperNetDefinition.MAINTENANCE_THREAD);if(t==null||t.getRecipeTicks()<4)return;
                    require(inventory(center,false).getStackInSlot(0).isEmpty(),"repair input not paid");t.abandonRecipe();require(output(center,Items.REDSTONE)==0&&cn().circuitDurability()==0,"cancel applied repair/output");input(center,Items.IRON_INGOT,1);next(8);}
                case 8->{var t=thread(center,HyperNetDefinition.MAINTENANCE_THREAD);require(cn().circuitDurability()==0,"repair early");if(t==null||!t.hasActiveRecipe()||t.getRecipeTicks()<3)return;
                    require(t.getParallelism()==1,"repair did not limit batch by room");old=cn();center.load(center.saveWithoutMetadata());require(old.isClosed()&&cn().circuitDurability()==0,"repair reload reset circuit");blockOutput(center,true);next(9);}
                case 9->{var t=thread(center,HyperNetDefinition.MAINTENANCE_THREAD);require(cn().circuitDurability()==0,"blocked output repaired");if(t==null||t.getRecipeTicks()<t.getRecipeDuration()||tick-since<105)return;blockOutput(center,false);next(10);}
                case 10->{if(output(center,Items.REDSTONE)==0)return;require(output(center,Items.REDSTONE)==1&&cn().circuitDurability()==100,"repair completion result");circuitConfig(0);next(11);}
                case 11->{if(!cn().working()||pn().available()==0)return;require(cn().circuitDurability()==100,"reload changed repaired circuit");
                    var state=new HyperNetThermalState(normal,machine(normal).getHyperNetDefinition().thermal(),normal.getMachineName());state.generate(java.math.BigDecimal.valueOf(50));normal.load(normal.saveWithoutMetadata());near(normal.getHyperNetNode().storedHeat(),100,"normal fixture thermal load");
                    input(normal,Items.DIRT,1);next(12);}
                case 12->{if(!normal.hasActiveRecipe())return;require(inventory(normal,false).getStackInSlot(0).isEmpty(),"normal cooling material not paid");require(normal.getHyperNetNode().storedHeat()>0,"normal cooling started without initial heat");next(13);}
                case 13->{if(output(normal,Items.DIAMOND)==0)return;require(output(normal,Items.DIAMOND)==1,"normal cooling output");near(normal.getHyperNetNode().storedHeat(),0,"passive/active cooling underflow");
                    var saved=center.saveWithoutMetadata();old=cn();center.load(saved);require(old.isClosed()&&cn().circuitDurability()==100,"saved repaired durability lost");
                    require(!center.getUpdateTag().contains("hypernetData"),"private circuit leaked");next(14);}
                case 14->{if(tick-since<4||world.getGameTime()%20!=0)return;stateChecks();finish(null);}
            }
        }catch(Throwable error){finish(error);}
    }
    private static void parserChecks(){
        for(String type:List.of("hypernet_cooling","hypernet_circuit_repair")){
            String base="{\"type\":\"modularmachinery:"+type+"\",\"io-type\":\"input\",\"amount\":5}";
            RecipeLoader.validateEditorRequirement(JsonParser.parseString(base).getAsJsonObject());
            for(var bad:List.of(base.replace("input","output"),base.replace(":5",":-1"),base.replace(":5",":5.5"),base.replace(":5",":2147483648"),base.replace(":5",":\"5\""),base.replace("}",",\"typo\":1}"),base.replace("}",",\"triggerTime\":1}"),base.replace("}",",\"selector-tag\":\"x\"}"))){
                try{RecipeLoader.validateEditorRequirement(JsonParser.parseString(bad).getAsJsonObject());throw new AssertionError("invalid maintenance JSON accepted: "+bad);}catch(IllegalArgumentException expected){}
            }
        }
        for(var bad:List.of("{\"capacity\":100,\"min-wear\":5,\"max-wear\":4,\"wear-chance\":1}","{\"capacity\":0,\"min-wear\":0,\"max-wear\":0,\"wear-chance\":1}","{\"capacity\":100,\"min-wear\":1,\"max-wear\":1,\"wear-chance\":2}")){
            try{HyperNetCircuitDefinition.parse(JsonParser.parseString(bad));throw new AssertionError("invalid circuit accepted");}catch(IllegalArgumentException expected){}
        }
    }
    private static void stateChecks() {
        var saved=center.getHyperNetData();
        try {
            // Isolated state arithmetic/bad-format checks; the runtime/material tests above do not inject durability.
            var data=saved.copy();var tag=data.getCompound("circuit");tag.putInt("durability",5);data.put("circuit",tag);center.setHyperNetData(data);
            var state=new HyperNetCircuitState(center,new HyperNetCircuitDefinition(101,0,0,0),center.getMachineName());
            require(!state.permitsWork(),"5/101 incorrectly meets 5 percent");state.repair(java.math.BigDecimal.ONE);
            require(state.permitsWork()&&state.stored()==6,"exact 5 percent gate");state.repair(java.math.BigDecimal.valueOf(Long.MAX_VALUE));
            require(state.stored()==101,"repair overflow/overfill");
            data=center.getHyperNetData();data.getCompound("circuit").putInt("durability",100);data.getCompound("circuit").putLong("lastWearTick",world.getGameTime()-20);center.setHyperNetData(data);
            var wearDefinition=new HyperNetCircuitDefinition(100,100,100,1);
            state=new HyperNetCircuitState(center,wearDefinition,center.getMachineName());state.wear(2);require(state.stored()==0,"fixed wear amount");state.repair(java.math.BigDecimal.valueOf(100));
            state=new HyperNetCircuitState(center,wearDefinition,center.getMachineName());state.wear(2);require(state.stored()==100,"same-tick reload rerolled wear");
            data=center.getHyperNetData();data.putString("circuit","broken");center.setHyperNetData(data);
            require(new HyperNetCircuitState(center,wearDefinition,center.getMachineName()).stored()==0,"corrupt circuit became full");
        } finally {center.setHyperNetData(saved);}
    }
    private static void finish(Throwable error){
        if(finished)return;finished=true;failed=error!=null;
        if(error!=null)Modularmachinery.LOG.error("[MMC][hypernet-maintenance] FAIL phase "+phase,error);
        else Modularmachinery.LOG.info("[MMC][hypernet-maintenance] runtime PASS; awaiting shutdown");server.halt(false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        if(event.getServer()!=server||failed)return;
        require(old!=null&&old.isClosed(),"old maintenance runtime still open");
        Modularmachinery.LOG.info("[MMC][hypernet-maintenance] OVERALL: PASS (physical heat, real cooling/repair materials, aggregates/batch, output wait, cancel, reload, passive overlap/zero recovery, circuit wear/core stop/revival, snapshots/strict JSON)");
    }
}
