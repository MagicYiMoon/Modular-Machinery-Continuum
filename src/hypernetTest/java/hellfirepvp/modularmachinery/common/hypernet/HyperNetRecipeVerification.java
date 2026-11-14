/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.google.gson.JsonParser;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.Requirements;
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
import java.math.BigDecimal;
import java.nio.file.Files;
import java.util.*;

/** Disposable real-world recipes, using the hardware fixture's physical CPU/RAM and native FE. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetRecipeVerification {
    private static MinecraftServer server; private static ServerLevel world;
    private static TileController center,processor,normal,cancel; private static TileFactoryController factory;
    private static final BlockPos N=new BlockPos(2028,120,2268),X=N.east(80),F=X.east(80);
    private static int tick,phase,since,held,heldEnergy; private static boolean finished,failed,sawContention,sawDemandContention;
    private static HyperNetNode oldNormal;
    private static double heldHeat;
    private static boolean heatTest(){return Boolean.getBoolean("modularmachinery.hypernetRecipeHeatTest");}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void next(int value){phase=value;since=tick;Modularmachinery.LOG.info("[MMC][hypernet-recipe] phase {} tick {}",phase,tick);}
    public static void begin(MinecraftServer srv,ServerLevel level,TileController c,TileController p){
        server=srv;world=level;center=c;processor=p;
        try{
            parserChecks();
            if(net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")) {
                var recipes=MachineReloadListener.getRecipeLoader();
                for(var id:List.of("hn_consumer_demo","hn_consumer_failure_demo","hn_json_consumer_demo")) {
                    var loaded=recipes.getRecipe(id);require(loaded!=null,"example recipe not loaded: "+id);
                    var demand=loaded.requirements().stream().filter(RequirementComputation.class::isInstance).map(RequirementComputation.class::cast).findFirst().orElseThrow();
                    require(demand.amount()==2&&demand.triggerFailure()==id.equals("hn_consumer_failure_demo"),"CT/JSON example demand flags");
                }
            }
            definition("hr_normal",Blocks.EMERALD_BLOCK,false);definition("hr_cancel",Blocks.LAPIS_BLOCK,false);definition("hr_factory",Blocks.QUARTZ_BLOCK,true);
            var fixed=new RequirementComputation(1,false);fixed.setParallelizeUnaffected(true);
            recipe("hr_normal","hr_batch",12,Items.DIRT,Items.DIAMOND,false,List.of(new RequirementComputation(2,false),fixed));
            recipe("hr_cancel","hr_cancel",20,Items.DIRT,Items.EMERALD,true,List.of(new RequirementComputation(1,true)));
            recipe("hr_factory","hr_a",10,Items.DIRT,Items.APPLE,false,List.of(new RequirementComputation(3,false)));
            recipe("hr_factory","hr_b",10,Items.COBBLESTONE,Items.CARROT,false,List.of(new RequirementComputation(3,false)));
            if(heatTest()) {
                var machine=MachineReloadListener.getLoader().findByName(processor.getMachineName());var original=machine.getHyperNetDefinition();
                machine.setHyperNetDefinition(new HyperNetDefinition(original.role(),original.workingThread(),0,0,0,original.modules(),"",new HyperNetThermalDefinition(1_000_000,0,"")));
            }
            MachineReloadListener.refreshRecipes();
            normal=build(N,"hr_normal",Blocks.EMERALD_BLOCK,false);cancel=build(X,"hr_cancel",Blocks.LAPIS_BLOCK,false);
            factory=(TileFactoryController)build(F,"hr_factory",Blocks.QUARTZ_BLOCK,true);
            // A disconnected consumer must not consume start items.
            input(normal,0,new ItemStack(Items.DIRT,2));energy(center,1000);energy(processor,1000);next(0);
        }catch(Throwable error){finish(error);}
    }
    private static void definition(String name,Block cap,boolean factory)throws Exception{
        String id=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(cap).toString();
        String json="{\"registryname\":\""+name+"\",\"requires-blueprint\":false,\"has-factory\":"+factory+",\"failure-action\":\"reset\",\"parts\":["
                +"{\"x\":0,\"y\":-1,\"z\":0,\"elements\":\"modularmachinery:blockenergyinputhatch\"},"
                +"{\"x\":0,\"y\":1,\"z\":0,\"elements\":\""+id+"\"},"
                +"{\"x\":0,\"y\":2,\"z\":0,\"elements\":\"modularmachinery:blockinputbus\"},"
                +"{\"x\":0,\"y\":3,\"z\":0,\"elements\":\"modularmachinery:blockoutputbus\"}],\"hypernet\":{\"role\":\"node\"}}";
        var file=Files.createTempDirectory("mmc-hypernet-recipe-").resolve(name+".json");Files.writeString(file,json);
        var machine=MachineReloadListener.serverDefinitions().machines().parseMachineFile(file.toFile()).orElseThrow();
        Files.delete(file);Files.delete(file.getParent());
        machine.setFailureAction(RecipeFailureActions.RESET);machine.setParallelizable(true);
        machine.setMaxParallelism(factory?1:4);machine.setInternalParallelism(factory?1:4);machine.setMaxThreads(2);
    }
    private static void recipe(String machine,String id,int duration,Item in,Item out,boolean cancel,List<Requirement> computation){
        var requirements=new ArrayList<Requirement>(computation);
        requirements.add(new RequirementEnergy(IOType.INPUT,1));
        requirements.add(new RequirementItem(IOType.INPUT,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(in),1));
        requirements.add(new RequirementItem(IOType.OUTPUT,net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(out),1));
        if(machine.equals("hr_normal")) {
            var delayed=new RequirementItem(IOType.INPUT,"minecraft:stick",1);delayed.setTriggerTime(3);requirements.add(delayed);
        }
        var recipe=new MachineRecipe(machine,id,duration,requirements,null,cancel,-1,"").withMetadata(List.of(),true,true);
        var decoded=RecipeSnapshotCodec.decode(RecipeSnapshotCodec.encode(recipe,Map.of()));
        require(decoded.requirements().stream().filter(RequirementComputation.class::isInstance).count()==computation.size(),"snapshot lost computation");
        var encoded=RecipeSnapshotCodec.encode(decoded,Map.of());
        require(encoded.equals(RecipeSnapshotCodec.encode(recipe,Map.of())),"snapshot changed values/exemption");
        ScriptRecipeRegistry.register(encoded);
    }
    private static TileController build(BlockPos pos,String name,Block cap,boolean factory){
        world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(),cap.defaultBlockState());
        world.setBlockAndUpdate(pos.above(2),RegistryBlocks.itemInputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos.above(3),RegistryBlocks.itemOutputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos,(factory?RegistryBlocks.blockFactoryController.get():RegistryBlocks.blockController.get()).defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var controller=(TileController)world.getBlockEntity(pos);controller.setOwner(center.getOwner());controller.setMachineName(name);
        var result=MachineFormation.tryForm(world,pos,Direction.NORTH,MachineReloadListener.getLoader().findByName(name),MachineReloadListener.getLoader());
        require(result.formed(),"recipe formation: "+result.report());controller.setFormed(true);controller.acknowledgeStructureCheck();energy(controller,1000);return controller;
    }
    private static TileEnergyHatch power(TileController controller){return (TileEnergyHatch)world.getBlockEntity(controller.getBlockPos().below());}
    private static void energy(TileController controller,int target){
        var hatch=power(controller);
        while(hatch.getStoredEnergy()>target)require(hatch.consumeInternal(hatch.getStoredEnergy()-target,false)>0,"drain");
        while(hatch.getStoredEnergy()<target)require(hatch.produceInternal(target-hatch.getStoredEnergy(),false)>0,"fill");
    }
    private static IItemHandlerModifiable inventory(TileController controller,int y){return (IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(controller.getBlockPos().above(y))).getInventory();}
    private static void input(TileController controller,int slot,ItemStack stack){inventory(controller,2).setStackInSlot(slot,stack);}
    private static int output(TileController controller,Item item){var inv=inventory(controller,3);int amount=0;for(int i=0;i<inv.getSlots();i++)if(inv.getStackInSlot(i).is(item))amount+=inv.getStackInSlot(i).getCount();return amount;}
    private static MachineContext context(TileController controller){return new MachineContext(world,controller.getBlockPos(),Direction.NORTH,
            MachineReloadListener.getLoader().findByName(controller.getMachineName()),new MachineVariables(),null);}
    private static void connect(TileController controller){require(controller.getHyperNetNode().connect(center.getBlockPos(),center.getOwner())==HyperNetNode.ConnectResult.SUCCESS,"consumer connect");}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(server==null||finished)return;
        try{
            if(event.phase==TickEvent.Phase.START){energy(center,1000);energy(processor,1000);return;}
            tick++;require(tick<1200&&tick-since<350,"recipe timeout phase "+phase);
            // 5 points/t, at most 57 module FE plus the processor's 1 FE core recipe.
            require(1000-power(processor).getStoredEnergy()<=58,"shared recipes overspent provider budget/FE");
            switch(phase){
                case 0->{
                    if(tick<12||!center.getHyperNetNode().working()||!processor.getHyperNetNode().working())return;
                    require(!normal.hasActiveRecipe()&&inventory(normal,2).getStackInSlot(0).getCount()==2,"unbound recipe consumed start input");
                    connect(processor);connect(normal);connect(cancel);connect(factory);input(normal,0,ItemStack.EMPTY);next(1);
                }
                case 1->{
                    if(normal.getHyperNetNode().available()!=5)return;
                    helperChecks();input(normal,0,new ItemStack(Items.DIRT,2));input(normal,1,new ItemStack(Items.STICK,2));next(2);
                }
                case 2->{
                    // Hold before the delayed payment at tick 3, including the intervening FE-starvation check.
                    if(!normal.hasActiveRecipe()||normal.getRecipeTicks()<2)return;
                    require(normal.getParallelism()==2,"batch was not bounded by aggregate 2*n+1 demand");
                    checkDemand(normal,5,1);checkDemand(center,5,1);
                    MachineReloadListener.getLoader().findByName(normal.getMachineName()).setFailureAction(RecipeFailureActions.STILL);
                    held=normal.getRecipeTicks();energy(normal,0);next(20);
                }
                case 20->{
                    require(normal.hasActiveRecipe()&&normal.getRecipeTicks()==held,"unpowered consumer advanced");
                    if(tick-since<3)return;
                    checkDemand(normal,5,1);checkDemand(center,5,1);
                    require(normal.getHyperNetNode().status().consumed()==0,"unpowered consumer paid computation");
                    if(net.minecraftforge.fml.ModList.get().isLoaded("theoneprobe")) {
                        var viewer=net.minecraftforge.common.util.FakePlayerFactory.get(world,
                                new com.mojang.authlib.GameProfile(center.getOwner(),"HyperNetTest"));
                        HyperNetProbeVerification.run(normal,viewer,0);
                        var snapshot=HyperNetMonitor.capture(center,viewer,0);
                        require(snapshot.center().demanded()==5&&snapshot.center().demandConsumers()==1,"monitor lost active demand");
                    }
                    normal.addModifier("hn_demand_test",new hellfirepvp.modularmachinery.common.modifier.RecipeModifier(
                            "modularmachinery:computation",IOType.INPUT,2,Requirement.OPERATION_MULTIPLY,false));next(21);
                }
                case 21->{
                    require(normal.hasActiveRecipe()&&normal.getRecipeTicks()==held,"modified shortfall advanced");
                    if(tick-since<3)return;checkDemand(normal,10,1);checkDemand(center,10,1);
                    normal.removeModifier("hn_demand_test");
                    world.setBlockAndUpdate(normal.getBlockPos().east(),Blocks.REDSTONE_BLOCK.defaultBlockState());next(22);
                }
                case 22->{
                    require(normal.hasActiveRecipe()&&normal.getRecipeTicks()==held,"redstone discarded/advanced run");
                    if(tick-since<3)return;checkDemand(normal,0,0);checkDemand(center,0,0);
                    world.setBlockAndUpdate(normal.getBlockPos().east(),Blocks.AIR.defaultBlockState());
                    MachineReloadListener.getLoader().findByName(normal.getMachineName()).setFailureAction(RecipeFailureActions.RESET);
                    energy(normal,1000);
                    held=normal.getRecipeTicks();heldEnergy=power(normal).getStoredEnergy();oldNormal=normal.getHyperNetNode();oldNormal.disconnect();next(3);
                    if(heatTest()){heldHeat=processor.getHyperNetNode().storedHeat();require(heldHeat>0,"actual recipe did not heat provider");}
                    var output=inventory(normal,3);for(int i=0;i<output.getSlots();i++)output.setStackInSlot(i,new ItemStack(Items.STONE,64));
                }
                case 3->{
                    require(normal.hasActiveRecipe()&&normal.getRecipeTicks()==held&&power(normal).getStoredEnergy()==heldEnergy,"default shortfall reset/cancelled/charged run");
                    require(inventory(normal,2).getStackInSlot(1).getCount()==2,"paused recipe consumed delayed items");
                    if(heatTest())require(processor.getHyperNetNode().storedHeat()==heldHeat,"paused recipe generated heat");
                    if(tick-since<6)return;require(ComputationTickIO.DISCONNECTED.equals(normal.getStatusMessage()),"missing network status");
                    checkDemand(normal,5,1);checkDemand(center,0,0);connect(normal);next(4);
                }
                case 4->{
                    if(normal.getRecipeTicks()<normal.getRecipeDuration())return;
                    heldEnergy=power(normal).getStoredEnergy();next(40);
                    if(heatTest())heldHeat=processor.getHyperNetNode().storedHeat();
                }
                case 40->{
                    require(normal.hasActiveRecipe()&&power(normal).getStoredEnergy()==heldEnergy,"output-blocked run consumed FE");
                    require(1000-power(processor).getStoredEnergy()<=1,"output-blocked run paid module load");
                    if(heatTest())require(processor.getHyperNetNode().storedHeat()==heldHeat,"output-blocked recipe generated heat");
                    checkDemand(normal,0,0);checkDemand(center,0,0);
                    if(tick-since<5)return;var output=inventory(normal,3);for(int i=0;i<output.getSlots();i++)output.setStackInSlot(i,ItemStack.EMPTY);next(41);
                }
                case 41->{
                    if(output(normal,Items.DIAMOND)!=2)return;
                    require(!normal.hasActiveRecipe(),"completed batch retained run");input(cancel,0,new ItemStack(Items.DIRT));next(5);
                }
                case 5->{
                    if(!cancel.hasActiveRecipe()||cancel.getRecipeTicks()<3)return;
                    cancel.getHyperNetNode().disconnect();next(6);
                }
                case 6->{
                    if(tick-since<3)return;require(!cancel.hasActiveRecipe()&&output(cancel,Items.EMERALD)==0,"explicit failure did not cancel");
                    checkDemand(cancel,0,0);
                    input(factory,0,new ItemStack(Items.DIRT));input(factory,1,new ItemStack(Items.COBBLESTONE));next(7);
                }
                case 7->{
                    var threads=factory.getFactoryRecipeThreadList();
                    if(threads.stream().filter(thread->thread.hasActiveRecipe()).count()==2&&threads.stream().anyMatch(thread->ComputationTickIO.SHORTFALL.equals(thread.getBlockedReason())))sawContention=true;
                    if(factory.getHyperNetNode().status().demandConsumers()==2) {
                        checkDemand(factory,6,2);checkDemand(center,6,2);sawDemandContention=true;
                        require(center.getHyperNetNode().status().consumed()<=5,"demand reserved extra budget");
                    }
                    if(output(factory,Items.APPLE)!=1||output(factory,Items.CARROT)!=1)return;
                    require(sawContention,"factory did not demonstrate shared-budget pause");
                    require(sawDemandContention,"factory did not register both competing demands");
                    oldNormal=normal.getHyperNetNode();MachineReloadListener.refreshRecipes();
                    require(oldNormal.request(1,false).granted()==0&&oldNormal.isClosed(),"reload retained recipe node");next(8);
                    require(oldNormal.status().demanded()==0&&oldNormal.status().demandConsumers()==0,"retired demand survived reload");
                }
                case 8->{if(tick-since<4)return;
                    Modularmachinery.LOG.info("[MMC][hypernet-demand] PASS: actual batch/exemption and live modifier, FE-starved demand without payment, redstone stop/resume, unbound local demand excluded from center, completed-output wait, cancellation, competing factory threads and reload; queries are read-only");
                    finish(null);}
            }
        }catch(Throwable error){finish(error);}
    }
    private static void helperChecks(){
        var context=context(normal);var first=new RequirementComputation(2,false);var fixed=new RequirementComputation(1,false);fixed.setParallelizeUnaffected(true);
        checkDemand(normal,0,0);
        List<Requirement> all=List.of(first,fixed);
        require(first.getMaxParallelism(context,4,all)==2&&ComputationTickIO.plan(context,all,2).amount().compareTo(new BigDecimal("5"))==0,"aggregate batch/exemption");
        require(!ComputationTickIO.plan(context,all,3).valid(),"aggregate shortfall accepted");
        require(!ComputationTickIO.plan(context,List.of(new RequirementComputation(Double.MAX_VALUE,false),new RequirementComputation(Double.MAX_VALUE,false)),2).valid(),"overflow accepted");
        var scaled=(RequirementComputation)first.scaled(2,Requirement.OPERATION_MULTIPLY,IOType.INPUT,false);require(scaled.amount()==4,"modifier lost");
        require(ComputationTickIO.amount(List.of(scaled,fixed),2).compareTo(new BigDecimal("9"))==0,"modified demand/exemption sum");
        require(!ComputationTickIO.pauses(List.of(first,new RequirementComputation(1,true)),ComputationTickIO.SHORTFALL),"mixed failure flags ignored");
        int before=power(processor).getStoredEnergy();double available=normal.getHyperNetNode().available();
        double heat=processor.getHyperNetNode().storedHeat();
        energy(normal,0);var recipe=new MachineRecipe("hr_normal","helper",5,List.of(first,new RequirementEnergy(IOType.INPUT,10)),null,false,-1,"");
        require(MachineTicker.processPerTick(recipe,recipe.requirements(),context,1,false)!=null,"unpowered consumer passed");
        require(power(processor).getStoredEnergy()==before&&normal.getHyperNetNode().available()==available,"consumer simulation failure paid provider");
        require(processor.getHyperNetNode().storedHeat()==heat,"consumer simulation failure generated heat");
        require(MachineTicker.processPerTick(recipe,recipe.requirements(),context,1,true)==null&&power(processor).getStoredEnergy()==before,"completed output wait paid provider");energy(normal,1000);
        var noBackend=new MachineContext(world,N.above(10),Direction.NORTH,context.getMachine(),new MachineVariables(),null);
        require(!ComputationTickIO.plan(noBackend,List.of(new RequirementComputation(0,false)),1).valid(),"missing backend bypassed zero demand");
        require(ComputationTickIO.plan(context,List.of(new RequirementComputation(0,false)),1).execute(),"connected zero demand failed");
        checkDemand(normal,0,0); // Candidate probes and simulated budgets never register active consumers.
    }
    private static void checkDemand(TileController controller,double amount,int consumers) {
        var node=controller.peekHyperNetNode();var before=controller.getHyperNetData();var state=node.status();
        int stored=power(processor).getStoredEnergy();
        require(state.demanded()==amount&&state.demandConsumers()==consumers,"demand at "+controller.getBlockPos()+": "+state);
        for(int i=0;i<20;i++)require(node.status().equals(state),"inspection changed demand");
        require(before.equals(controller.getHyperNetData())&&power(processor).getStoredEnergy()==stored,"demand query changed NBT/FE");
    }
    private static void parserChecks(){
        Requirements.registerBuiltIns();
        String base="{\"type\":\"modularmachinery:computation\",\"io-type\":\"input\",\"amount\":2.5}";
        var requirement=(RequirementComputation)RecipeLoader.validateEditorRequirement(JsonParser.parseString(base).getAsJsonObject());
        require(requirement.amount()==2.5&&!requirement.triggerFailure(),"default parser");
        for(String bad:List.of(base.replace("input","output"),base.replace("2.5","\"2.5\""),base.replace("2.5","-1"),base.replace("2.5","1e999"),base.replace("2.5","1e-999"),base.replace("2.5","-1e-999"),
                base.replace("}",",\"triggerFailure\":\"true\"}"),base.replace("}",",\"typo\":1}"),base.replace("}",",\"selector-tag\":\"x\"}"),
                base.replace("}",",\"triggerTime\":1}"),base.replace("}",",\"triggerRepeatable\":true}"),base.replace("}",",\"ignoreOutputCheck\":true}"))){
            try{RecipeLoader.validateEditorRequirement(JsonParser.parseString(bad).getAsJsonObject());throw new AssertionError("invalid computation JSON accepted: "+bad);}catch(IllegalArgumentException expected){}
        }
        for(double bad: new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1})try{new RequirementComputation(bad,false);throw new AssertionError("invalid amount accepted");}catch(IllegalArgumentException expected){}
    }
    private static void finish(Throwable error){
        if(finished)return;finished=true;failed=error!=null;
        if(error!=null)Modularmachinery.LOG.error("[MMC][hypernet-recipe] FAIL phase "+phase,error);
        else Modularmachinery.LOG.info("[MMC][hypernet-recipe] runtime checks PASS; awaiting shutdown");server.halt(false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        if(event.getServer()!=server||failed)return;
        require(oldNormal!=null&&oldNormal.isClosed(),"shutdown cleanup");
        Modularmachinery.LOG.info("[MMC][hypernet-recipe] OVERALL: PASS (strict JSON/snapshots, aggregate batch, physical module FE, ordinary pause/resume/cancel, factory contention, reload/shutdown)");
        if(heatTest())Modularmachinery.LOG.info("[MMC][hypernet-recipe-heat] OVERALL: PASS (natural recipes heat physical provider, pause/output wait/simulation failure do not)");
    }
}
