/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.efabricator;

import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.crafting.*;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.*;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.helpers.MachineSource;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Future;

/** Two real JVMs, a real AE cell/CPU, and asynchronous partial/full chunk unload. No synthetic order completion. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class EFabricatorPersistenceVerification {
    // Adjacent chunks may lose their FULL future while remaining in a neighbour's ticket halo.
    // Separate the worker far enough to require a genuine unload while the host stays loaded.
    private static final int HOST_CHUNK=31, WORKER_CHUNK=48;
    private static final BlockPos BASE=new BlockPos(510,120,520), WORKER=BASE.east(258), BUS=BASE.east(259),
            CHANNEL=BASE.south(), DRIVE=CHANNEL.south(), CPU=DRIVE.west(), BARREL=BASE.west(6);
    private static final Path MARKER=Path.of("ef-persistence-state.nbt");
    private static final AEKey LOG=AEItemKey.of(Items.OAK_LOG), PLANK=AEItemKey.of(Items.OAK_PLANKS), STONE=AEItemKey.of(Items.STONE);
    private static MinecraftServer server;
    private static ServerLevel world;
    private static TileEFabricatorController host,oldHost;
    private static TileEFabricatorWorker worker,oldWorker;
    private static TileEFabricatorPatternBus bus;
    private static EFabricatorMEChannelNode channel,oldChannel;
    private static IItemHandler oldInventory;
    private static DriveBlockEntity drive;
    private static CraftingCPUCluster cpu;
    private static Future<ICraftingPlan> calculation;
    private static CompoundTag marker;
    private static boolean seed;
    private static int ticks,phase,since,workerUnloads,hostUnloads;

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        String mode=System.getProperty("modularmachinery.efPersistenceTest"); if(mode==null)return;
        server=event.getServer();world=server.overworld();seed=mode.equals("seed");
        try {
            require(seed||mode.equals("resume"),"unknown persistence test mode");
            if(seed)require(!Files.exists(MARKER),"seed requires a new disposable save");
            for(int x:new int[]{HOST_CHUNK,WORKER_CHUNK})world.setChunkForced(x,32,true);
            world.getChunk(HOST_CHUNK,32);world.getChunk(WORKER_CHUNK,32);
            if(seed) {
                for(int x=503;x<=515;x++)for(int z=519;z<=524;z++)world.setBlockAndUpdate(new BlockPos(x,120,z),Blocks.AIR.defaultBlockState());
                world.setBlockAndUpdate(BASE,EFabricatorRegistration.CONTROLLERS.get(EFabricatorSpecs.Tier.L4).get().defaultBlockState());
                world.setBlockAndUpdate(BASE.east(),EFabricatorRegistration.CASING.get().defaultBlockState());
                world.setBlockAndUpdate(WORKER,EFabricatorRegistration.WORKER.get().defaultBlockState());
                world.setBlockAndUpdate(BUS,EFabricatorRegistration.PATTERN_BUS.get().defaultBlockState());
                world.setBlockAndUpdate(CHANNEL,EFabricatorRegistration.CHANNEL.get().defaultBlockState());
                var state=AEBlocks.DRIVE.block().defaultBlockState();
                state=appeng.api.orientation.IOrientationStrategy.get(state).setFacing(state,Direction.EAST);
                world.setBlockAndUpdate(DRIVE,state);
                ((DriveBlockEntity)world.getBlockEntity(DRIVE)).getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_1K.stack());
                world.getBlockEntity(DRIVE).setChanged();
                world.setBlockAndUpdate(DRIVE.below(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                world.setBlockAndUpdate(CPU,AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
                world.setBlockAndUpdate(BARREL,Blocks.BARREL.defaultBlockState());
            }
            grab();host.refreshStructure();require(host.isOperational(),"disk EF definition did not form");
            if(seed) {
                host.setModes(false,true); // Deliberately hold accepted work until the real CPU has dispatched all four crafts.
                var recipe=(CraftingRecipe)world.getRecipeManager().byKey(new ResourceLocation("minecraft:oak_planks")).orElseThrow();
                var inputs=new ItemStack[9];Arrays.fill(inputs,ItemStack.EMPTY);inputs[0]=new ItemStack(Items.OAK_LOG);
                var encoded=PatternDetailsHelper.encodeCraftingPattern(recipe,inputs,new ItemStack(Items.OAK_PLANKS,4),false,false);
                require(inventory().insertItem(0,encoded,false).isEmpty(),"pattern insertion failed");
            } else {
                marker=NbtIo.readCompressed(MARKER.toFile());
                require(marker!=null&&marker.getString("Stage").equals("seed"),"missing successful seed marker");
                snapshots();require(worker.queuedCrafts()==3&&pending()==2&&host.totalCrafted()==1,"pre-tick restored queue/output mismatch");
                require(!host.isActiveCooling()&&worker.energyStored()==499900,"saved mode/charged energy changed");
                phase=10;
                Modularmachinery.LOG.info("[MMC][ef-persistence] Restored three queued crafts, two refused planks and 499900 AE from world NBT before ticking");
            }
        } catch(Throwable failure){finish(failure);}
    }
    private static void grab() {
        host=(TileEFabricatorController)world.getBlockEntity(BASE);worker=(TileEFabricatorWorker)world.getBlockEntity(WORKER);
        bus=(TileEFabricatorPatternBus)world.getBlockEntity(BUS);channel=(EFabricatorMEChannelNode)world.getBlockEntity(CHANNEL);
        drive=(DriveBlockEntity)world.getBlockEntity(DRIVE);
        require(host!=null&&worker!=null&&bus!=null&&channel!=null&&drive!=null,"saved block entities missing");
    }
    private static IItemHandler inventory(){return bus.getCapability(ForgeCapabilities.ITEM_HANDLER,null).orElseThrow(AssertionError::new);}
    private static MEStorage cell(){return Objects.requireNonNull(drive.getCellInventory(0),"native cell not loaded");}
    private static MachineSource source(){return new MachineSource(channel);}
    private static boolean ready(){return channel.isNetworkActive()&&!channel.getActionableNode().getGrid().getPathingService().isNetworkBooting();}
    private static CraftingCPUCluster nativeCpu(){return Objects.requireNonNull(((CraftingBlockEntity)world.getBlockEntity(CPU)).getCluster(),"native CPU absent");}
    private static long count(AEKey key){return cell().getAvailableStacks().get(key);}
    private static long pending(){return host.pendingOutputs().stream().filter(e->PLANK.equals(AEItemKey.of(e.item()))).mapToLong(EFabricatorOutputBuffer.Entry::amount).sum();}
    private static void snapshots(){
        require(worker.workData().equals(marker.getCompound("Worker")),"queued work or charged energy changed on disk roundtrip");
        require(host.productionData().equals(marker.getCompound("Production")),"partly delivered output changed on disk roundtrip");
        require(bus.patternData().equals(marker.getCompound("Patterns")),"encoded pattern changed on disk roundtrip");
        require(!host.getUpdateTag().contains("EfProduction")&&worker.getUpdateTag().isEmpty()&&bus.getUpdateTag().isEmpty(),"private state broadcast in chunk update");
    }
    private static void originalCpu(){
        cpu=nativeCpu();var link=cpu.craftingLogic.getLastLink();
        require(link!=null&&link.getCraftingID().equals(marker.getUUID("CraftId"))&&!link.isCanceled(),"original native CPU job/link lost or replaced");
    }
    private static void leaveTwoSlots(){
        // One explicit sentinel plank allocates its cell type after planning. It is not a fabricated EF result.
        require(cell().insert(PLANK,1,Actionable.MODULATE,source())==1,"sentinel plank insert");
        long room=cell().insert(STONE,100000,Actionable.SIMULATE,source());
        require(room>100&&room<=100000&&cell().insert(STONE,room,Actionable.MODULATE,source())==room,"native filler insert");
        require(cell().extract(STONE,2,Actionable.MODULATE,source())==2,"free two native cell slots");
        var barrel=(BarrelBlockEntity)world.getBlockEntity(BARREL);barrel.setItem(0,new ItemStack(Items.STONE,2));barrel.setChanged();
        require(cell().insert(PLANK,4,Actionable.SIMULATE,source())==2,"native 1K cell does not have exactly two item slots");
    }
    private static void retainReferences(){
        oldHost=host;oldWorker=worker;oldChannel=channel;oldInventory=inventory();
    }
    private static void staleWorker(){
        require(!oldWorker.isActivePart()&&oldWorker.doWork(1)==0
                &&oldWorker.offerWork(new EFabricatorQueue.Work(1,new ItemStack(Items.OAK_PLANKS,4),List.of()))==0,"unloaded worker still accepts/executes work");
        require(oldWorker.workData().equals(marker.getCompound("Worker"))&&oldInventory.getStackInSlot(0).isEmpty(),"unloaded reference changed work or exposed patterns");
    }
    private static boolean loaded(int x){return world.getChunkSource().getChunkNow(x,32)!=null;}
    @SubscribeEvent public static void unload(ChunkEvent.Unload event){
        if(server==null||event.getLevel()!=world||event.getChunk().getPos().z!=32)return;
        if(event.getChunk().getPos().x==WORKER_CHUNK)workerUnloads++;
        if(event.getChunk().getPos().x==HOST_CHUNK)hostUnloads++;
    }
    private static void next(int value){phase=value;since=ticks;Modularmachinery.LOG.info("[MMC][ef-persistence] phase {} tick {}",phase,ticks);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||server==null)return;
        try {
            ticks++;require(ticks<6000&&ticks-since<1800,"timeout phase="+phase+", chunks="+loaded(HOST_CHUNK)+"/"+loaded(WORKER_CHUNK)+", unload events="+hostUnloads+"/"+workerUnloads);
            switch(phase){
                case 0 -> {
                    if(ticks<100||!ready())return;
                    require(cell().insert(LOG,32,Actionable.MODULATE,source())==32,"native initial log stock");
                    calculation=channel.getActionableNode().getGrid().getCraftingService().beginCraftingCalculation(world,()->source(),PLANK,16,CalculationStrategy.REPORT_MISSING_ITEMS);next(1);
                }
                case 1 -> {
                    if(!calculation.isDone())return;var plan=calculation.get();cpu=nativeCpu();
                    require(!plan.simulation()&&plan.missingItems().isEmpty()&&plan.patternTimes().values().stream().mapToLong(Long::longValue).sum()==4,"native planner did not require four crafts");
                    require(channel.getActionableNode().getGrid().getCraftingService().submitJob(plan,null,cpu,true,source()).successful(),"actual native order submission failed");next(2);
                }
                case 2 -> {
                    if(worker.queuedCrafts()!=4)return;
                    require(count(LOG)==28&&count(PLANK)==0&&host.totalCrafted()==0,"actual CPU material admission mismatch");
                    leaveTwoSlots();host.setModes(false,false);next(3);
                }
                case 3 -> {
                    if(pending()!=2||count(PLANK)!=3)return;
                    require(worker.queuedCrafts()==3&&worker.energyStored()==499900&&host.totalCrafted()==1,"seed must retain both queued work and partly delivered products");
                    marker=new CompoundTag();marker.putString("Stage","seed");
                    marker.put("Worker",worker.workData());marker.put("Production",host.productionData());marker.put("Patterns",bus.patternData());
                    marker.putUUID("CraftId",cpu.craftingLogic.getLastLink().getCraftingID());marker.putLong("Stone",count(STONE));
                    NbtIo.writeCompressed(marker,MARKER.toFile());
                    Modularmachinery.LOG.info("[MMC][ef-persistence] SEED PASS: real native CPU dispatched four crafts; one paid 100 AE, two of four planks entered a real capacity-limited 1K cell; three crafts/two planks/499900 AE/encoded pattern/native CPU link will persist in world NBT");finish(null);
                }
                case 10 -> {
                    if(ticks<100||!ready())return;
                    snapshots();originalCpu();require(count(LOG)==28&&count(PLANK)==3,"native cell stock did not survive process restart");
                    retainReferences();world.setChunkForced(WORKER_CHUNK,32,false);next(11);
                }
                case 11 -> {
                    // FULL futures can disappear before ChunkMap's asynchronous unload callback.
                    if(loaded(WORKER_CHUNK)||workerUnloads==0)return;
                    require(loaded(HOST_CHUNK),"host chunk unloaded during the isolated worker test");
                    staleWorker();require(!host.isOperational()&&!channel.isNetworkActive()&&host.productionData().equals(marker.getCompound("Production")),"partial unload kept EF active or altered refused outputs");next(12);
                }
                case 12 -> {
                    if(ticks-since<80)return;
                    world.setChunkForced(WORKER_CHUNK,32,true);world.getChunk(WORKER_CHUNK,32);grab();
                    require(worker!=oldWorker,"worker chunk did not deserialize a fresh block entity");host.refreshStructure();snapshots();next(13);
                }
                case 13 -> {
                    if(!ready()||ticks-since<100)return;
                    snapshots();originalCpu();staleWorker();
                    retainReferences();for(int x:new int[]{HOST_CHUNK,WORKER_CHUNK})world.setChunkForced(x,32,false);next(14);
                }
                case 14 -> {
                    if(loaded(HOST_CHUNK)||loaded(WORKER_CHUNK)||hostUnloads==0||workerUnloads<2)return;
                    staleWorker();
                    require(!oldHost.isOperational()&&!oldChannel.isNetworkActive()
                            &&oldChannel.insertOutput(new ItemStack(Items.OAK_PLANKS),1)==0,"fully unloaded references can still manufacture or deliver");next(15);
                }
                case 15 -> {
                    if(ticks-since<100)return;
                    for(int x:new int[]{HOST_CHUNK,WORKER_CHUNK})world.setChunkForced(x,32,true);
                    world.getChunk(HOST_CHUNK,32);world.getChunk(WORKER_CHUNK,32);grab();
                    require(host!=oldHost&&worker!=oldWorker&&channel!=oldChannel,"full unload did not deserialize fresh EF instances");
                    host.refreshStructure();snapshots();next(16);
                }
                case 16 -> {
                    if(!ready()||ticks-since<100)return;
                    snapshots();originalCpu();staleWorker();require(count(LOG)==28&&count(PLANK)==3,"chunk reload duplicated or erased native stock");
                    require(cell().extract(STONE,64,Actionable.MODULATE,source())==64,"could not free real output capacity");
                    var barrel=(BarrelBlockEntity)world.getBlockEntity(BARREL);require(barrel.getItem(1).isEmpty(),"would overwrite filler");
                    barrel.setItem(1,new ItemStack(Items.STONE,64));barrel.setChanged();next(17);
                }
                case 17 -> {
                    if(worker.queuedCrafts()!=0||!host.pendingOutputs().isEmpty()||nativeCpu().isBusy())return;
                    require(host.totalCrafted()==4&&count(LOG)==28&&count(PLANK)==17,"resumed actual order did not conserve all raw materials and products");
                    require(count(STONE)==marker.getLong("Stone")-64,"native filler changed during recovery");
                    var barrel=(BarrelBlockEntity)world.getBlockEntity(BARREL);
                    require(barrel.getItem(0).is(Items.STONE)&&barrel.getItem(0).getCount()==2&&barrel.getItem(1).is(Items.STONE)&&barrel.getItem(1).getCount()==64,"physical filler retention mismatch");next(18);
                }
                case 18 -> {
                    if(ticks-since<100)return;
                    require(worker.queuedCrafts()==0&&host.pendingOutputs().isEmpty()&&host.totalCrafted()==4&&count(LOG)==28&&count(PLANK)==17,"completed work replayed after settling");
                    marker.putString("Stage","complete");NbtIo.writeCompressed(marker,MARKER.toFile());
                    Modularmachinery.LOG.info("[MMC][ef-lifecycle] PASS: actual partial/full multi-chunk unload events, fresh disk block entities, old worker/inventory/channel references inactive, exact queued work/energy/pattern/output retention and original native CPU job");
                    Modularmachinery.LOG.info("[MMC][ef-persistence] PASS: two real server JVMs, native capacity-limited AE cell and CPU, three pending crafts plus two refused planks restored, actual paid manufacturing/delivery resumed, 32 logs + one explicit sentinel plank conserved as 28 logs + 17 planks; no synthetic completions");finish(null);
                }
            }
        }catch(Throwable failure){finish(failure);}
    }
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void finish(Throwable failure){
        if(failure==null)Modularmachinery.LOG.info("[MMC][ef-persistence] {} OVERALL: PASS",seed?"SEED":"RESUME");
        else Modularmachinery.LOG.error("[MMC][ef-persistence] {} OVERALL: FAIL phase {}",seed?"SEED":"RESUME",phase,failure);
        var stopping=server;server=null;if(stopping!=null)stopping.halt(false);
    }
}
