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
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
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

/** Opt-in real database/core FE fixture; never a synthetic onWorking flag. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetDatabaseVerification {
    private static final UUID ACTOR=UUID.fromString("ad7762e3-0c61-4ab5-8367-b512cba87c93");
    private static final BlockPos C=new BlockPos(2028,120,3548),D=C.east(80);
    private static MinecraftServer server;private static ServerLevel world;private static TileController c,d;
    private static int tick,phase,since,fee=100,previousOutput,previousAuxTicks,previousDirt;
    private static boolean finished,failed,monitor,fill=true;
    private static CompoundTag good,bad;private static HyperNetNode old;
    private static Path researchDir;
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void next(int value){phase=value;since=tick;Modularmachinery.LOG.info("[MMC][hypernet-database] phase {} tick {}",phase,tick);}
    private static void research(String id)throws Exception{Files.writeString(researchDir.resolve(id+".json"),"{\"id\":\""+id+"\",\"required-points\":100,\"minimum-per-tick\":1}");}
    private static void machine(String name,boolean database)throws Exception{
        String json="{\"registryname\":\""+name+"\",\"requires-blueprint\":false,\"has-factory\":true,\"parts\":["
                +"{\"y\":-1,\"elements\":\"modularmachinery:blockenergyinputhatch\"},"
                +"{\"y\":-3,\"elements\":\"modularmachinery:blockenergyoutputhatch\"},"
                +"{\"y\":1,\"elements\":\"minecraft:gold_block\"},"
                +"{\"y\":5,\"elements\":\"modularmachinery:blockinputbus\"},"
                +"{\"y\":7,\"elements\":\"modularmachinery:blockoutputbus\"}],\"hypernet\":"
                +(database?"{\"role\":\"database\",\"storage-capacity\":16}":"{\"role\":\"center\",\"carrying\":5,\"max-connections\":8}")+"}";
        var path=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().resolve(name+".json");Files.writeString(path,json);
        MachineReloadListener.getLoader().parseMachineFile(path.toFile()).orElseThrow();
    }
    private static void recipe(String machine,String id,String thread,int duration,List<Requirement> requirements){
        ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(new MachineRecipe(machine,id,duration,requirements,null,false,1,thread),Map.of()));
        MachineReloadListener.getLoader().findByName(machine).addCoreThreadPreset(thread,List.of(id));
    }
    public static void begin(MinecraftServer srv,ServerLevel level){
        server=srv;world=level;
        try{
            researchDir=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().getParent().resolve("research");Files.createDirectories(researchDir);
            for(var id:List.of("fee_one","fee_two","fee_three"))research(id);HyperNetResearchRegistry.reload(researchDir);
            machine("df_center",false);machine("df_database",true);
            recipe("df_center","df_center_work",HyperNetDefinition.CENTER_THREAD,2,List.of(new RequirementEnergy(IOType.INPUT,1)));
            recipe("df_database","df_db_work",HyperNetDefinition.DATABASE_THREAD,2,List.of(new RequirementEnergy(IOType.INPUT,100),new RequirementEnergy(IOType.OUTPUT,10),new RequirementItem(IOType.INPUT,"minecraft:paper",1),new RequirementItem(IOType.OUTPUT,"minecraft:gold_nugget",1)));
            recipe("df_database","df_aux_work","fee_aux",3,List.of(new RequirementEnergy(IOType.INPUT,7),new RequirementItem(IOType.INPUT,"minecraft:cobblestone",1),new RequirementItem(IOType.OUTPUT,"minecraft:dirt",1)));
            MachineReloadListener.refreshRecipes();c=build(C,"df_center");d=build(D,"df_database");inv().setStackInSlot(0,new ItemStack(Items.PAPER,64));next(0);
        }catch(Throwable error){finish(error);}
    }
    private static TileController build(BlockPos pos,String name){
        world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState());world.setBlockAndUpdate(pos.below(3),RegistryBlocks.energyOutputHatch.get().defaultBlockState());world.setBlockAndUpdate(pos.above(),Blocks.GOLD_BLOCK.defaultBlockState());
        world.setBlockAndUpdate(pos.above(5),RegistryBlocks.itemInputBus.get().defaultBlockState());world.setBlockAndUpdate(pos.above(7),RegistryBlocks.itemOutputBus.get().defaultBlockState());
        world.setBlockAndUpdate(pos,RegistryBlocks.blockFactoryController.get().defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var tile=(TileController)world.getBlockEntity(pos);tile.setOwner(ACTOR);tile.setMachineName(name);
        require(MachineFormation.tryForm(world,pos,Direction.NORTH,MachineReloadListener.getLoader().findByName(name),MachineReloadListener.getLoader()).formed(),"formation "+name);tile.setFormed(true);tile.acknowledgeStructureCheck();return tile;
    }
    private static TileEnergyHatch input(TileController tile){return (TileEnergyHatch)world.getBlockEntity(tile.getBlockPos().below());}
    private static TileEnergyHatch output(){return (TileEnergyHatch)world.getBlockEntity(D.below(3));}
    private static void energy(TileController tile,int amount){var h=input(tile);if(h.getStoredEnergy()>amount)h.consumeInternal(h.getStoredEnergy()-amount,false);else if(h.getStoredEnergy()<amount)h.produceInternal(amount-h.getStoredEnergy(),false);}
    private static IItemHandlerModifiable inv(){return (IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(D.above(5))).getInventory();}
    private static int count(Item item){int count=0;var inventory=((TileItemBus)world.getBlockEntity(D.above(7))).getInventory();for(int i=0;i<inventory.getSlots();i++)if(inventory.getStackInSlot(i).is(item))count+=inventory.getStackInSlot(i).getCount();return count;}
    private static FactoryRecipeThread thread(String name){return ((TileFactoryController)d).getFactoryRecipeThreadList().stream().filter(t->t.getThreadName().equals(name)).findFirst().orElseThrow();}
    private static FactoryRecipeThread core(){return thread(HyperNetDefinition.DATABASE_THREAD);}
    private static void connect(){require(d.getHyperNetNode().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"connect database");}
    private static void complete(String id){require(d.getHyperNetNode().writeResearchProgress(id,new BigDecimal("100"))&&d.getHyperNetNode().completeResearch(id),"complete "+id);}
    private static void monitor(){monitor=true;previousOutput=output().getStoredEnergy();previousAuxTicks=thread("fee_aux").getRecipeTicks();previousDirt=count(Items.DIRT);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(server==null||finished||event.phase!=TickEvent.Phase.START)return;
        try{
            tick++;require(tick<650&&tick-since<150,"timeout "+phase);
            int paid=10000-input(d).getStoredEnergy();
            if(monitor){
                int auxTicks=thread("fee_aux").getRecipeTicks(),dirt=count(Items.DIRT);
                int auxAdvance=dirt>previousDirt?3-previousAuxTicks:auxTicks-previousAuxTicks;
                require(auxAdvance>=0&&auxAdvance<=1,"auxiliary progression");
                int spent=10000-input(d).getStoredEnergy();require(spent==fee+7*auxAdvance,"database FE mismatch phase="+phase+" expected="+(fee+7*auxAdvance)+" actual="+spent);
                require(output().getStoredEnergy()-previousOutput==(fee==0?0:10),"database multiplier changed output energy");
                previousAuxTicks=auxTicks;previousDirt=dirt;previousOutput=output().getStoredEnergy();
            }
            energy(c,10000);if(fill)energy(d,10000);
            switch(phase){
                case 0->{if(tick<5||!c.getHyperNetNode().working())return;
                    require(paid==0&&output().getStoredEnergy()==0&&inv().getStackInSlot(0).getCount()==64&&!d.getHyperNetNode().working(),"unbound database charged/started");connect();next(1);}
                case 1->{if(!d.getHyperNetNode().working())return;monitor();next(2);}
                case 2->{if(tick-since<5)return;complete("fee_one");require(d.getHyperNetNode().writeResearchProgress("fee_two",BigDecimal.TEN),"partial slot");fee=110;next(3);}
                case 3->{if(tick-since<4)return;complete("fee_two");fee=120;next(4);}
                case 4->{if(tick-since<4)return;require(d.getHyperNetNode().completeResearch("fee_two"),"idempotent completion");require(d.getHyperNetNode().writeResearchProgress("fee_three",BigDecimal.TEN),"second partial slot");next(5);}
                case 5->{if(tick-since<4)return;complete("fee_three");core().addPermanentModifier("energy",new RecipeModifier("modularmachinery:energy",IOType.INPUT,2,RecipeModifier.OPERATION_MULTIPLY,false));fee=260;next(6);}
                case 6->{if(tick-since<4)return;require(core().getPermanentModifiers().containsKey("energy")&&core().getSemiPermanentModifiers().isEmpty(),"native fee replaced/persisted user modifier");core().removePermanentModifier("energy");fee=130;inv().setStackInSlot(1,new ItemStack(Items.COBBLESTONE));((TileFactoryController)d).requestImmediateRecipeSearch();next(7);}
                case 7->{if(count(Items.DIRT)!=1)return;
                    require(inv().getStackInSlot(0).getCount()==63&&count(Items.GOLD_NUGGET)==0,"database core completed/repeated startup input");
                    var data=d.getHyperNetData();data.getCompound("database").getList("completed",Tag.TAG_STRING).add(StringTag.valueOf("fee_unknown"));d.setHyperNetData(data);next(8);}
                case 8->{if(tick-since<3)return;Files.delete(researchDir.resolve("fee_three.json"));HyperNetResearchRegistry.reload(researchDir);fee=120;next(9);}
                case 9->{if(tick-since<3)return;require(d.getHyperNetData().getCompound("database").getList("completed",Tag.TAG_STRING).contains(StringTag.valueOf("fee_three")),"unknown record deleted");research("fee_three");HyperNetResearchRegistry.reload(researchDir);fee=130;next(10);}
                case 10->{if(tick-since<3)return;good=d.getHyperNetData();bad=good.copy();bad.getCompound("database").putInt("version",8);d.setHyperNetData(bad);fee=0;next(11);}
                case 11->{if(tick-since<3)return;require(!d.getHyperNetNode().working()&&!d.getHyperNetNode().writeResearchProgress("fee_one",BigDecimal.ZERO)&&d.getHyperNetData().equals(bad),"future database charged/overwritten");d.setHyperNetData(good);fee=130;next(12);}
                case 12->{if(tick-since<3)return;monitor=false;fill=false;energy(d,129);previousOutput=output().getStoredEnergy();next(13);}
                case 13->{if(tick-since<3)return;require(input(d).getStoredEnergy()==129&&output().getStoredEnergy()==previousOutput,"shortfall partially charged/emitted energy");fill=true;energy(d,10000);monitor();next(14);}
                case 14->{if(tick-since<3)return;d.getHyperNetNode().disconnect();fee=0;next(15);}
                case 15->{if(tick-since<3)return;require(!d.getHyperNetNode().working(),"unbound database stayed online");connect();fee=130;next(16);}
                case 16->{if(tick-since<3)return;monitor=false;old=d.getHyperNetNode();var disk=d.saveWithoutMetadata();d.load(disk);require(old.isClosed(),"old runtime survived load");next(17);}
                case 17->{if(!d.getHyperNetNode().working()||paid!=130)return;monitor();next(18);}
                case 18->{if(tick-since<4)return;
                    require(d.getHyperNetNode().database().completedCount()==3&&d.getHyperNetNode().database().used()==4,"count/unknown record after load");
                    require(core().getPermanentModifiers().isEmpty()&&core().getSemiPermanentModifiers().isEmpty()&&inv().getStackInSlot(0).getCount()==63&&count(Items.GOLD_NUGGET)==0&&count(Items.DIRT)==1,"native multiplier persisted/compounded or core completed");
                    var before=d.getHyperNetData();int fe=input(d).getStoredEnergy();for(int i=0;i<1000;i++)core().activeModifiers();require(before.equals(d.getHyperNetData())&&fe==input(d).getStoredEnergy(),"modifier inspection mutated private data/FE");old=d.getHyperNetNode();finish(null);}
            }
        }catch(Throwable error){finish(error);}
    }
    private static void finish(Throwable error){if(finished)return;finished=true;failed=error!=null;if(error!=null)Modularmachinery.LOG.error("[MMC][hypernet-database] FAIL phase "+phase,error);else Modularmachinery.LOG.info("[MMC][hypernet-database] runtime checks PASS");server.halt(false);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){if(server!=event.getServer()||failed)return;require(old!=null&&old.isClosed(),"database shutdown cleanup");Modularmachinery.LOG.info("[MMC][hypernet-database] OVERALL: PASS (real core/native FE, completed-only dynamic fees, authored modifiers, unchanged FE output, auxiliary thread, continuous core, no unbound work, shortfall, unknown/future data, catalog and BE reload, inspection, shutdown)");}
}
