/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import appeng.api.config.*;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.stacks.*;
import appeng.blockentity.crafting.*;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.*;
import appeng.me.helpers.MachineSource;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import net.minecraft.core.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Real AE inventories, split grids and actual asynchronous chunk unload/reload. Disposable worlds only. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class ECalculatorLifecycleVerification {
    // Core at x=512 is in a different chunk from controller/network at x=510.
    private static final BlockPos BASE=new BlockPos(510,120,520), CHANNEL=BASE.south(), CUT=CHANNEL.south(),
            HUB=CHANNEL.west(30).south(3), REQUESTER=HUB.south(), CORE=BASE.east(2), BARREL=BASE.west(7), FOREIGN=HUB.west(3);
    private static final AEKey LOG=AEItemKey.of(Items.OAK_LOG), PLANK=AEItemKey.of(Items.OAK_PLANKS);
    private static MinecraftServer server;
    private static ServerLevel world;
    private static TileECalculatorController controller;
    private static TileECalculatorPart core,oldCore;
    private static ECalculatorMEChannelNode channel;
    private static InterfaceBlockEntity requester;
    private static MolecularAssemblerBlockEntity assembler;
    private static ItemStack encodedPattern;
    private static ECalculatorCpuHost cpu;
    private static ICraftingLink request;
    private static UUID taskId;
    private static long reserved;
    private static int ticks,phase,since;
    private static boolean unloaded;
    private static boolean staleGridChecked;
    private static int traceCount;
    public static boolean traceUnloadTicket(){return phase==8&&ticks-since>20&&traceCount++<8;}

    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("modularmachinery.ecLifecycleTest"))return;
        server=event.getServer();world=server.overworld();
        try {
            for(int x:new int[]{29,31,32})world.setChunkForced(x,32,true);
            world.setBlockAndUpdate(BASE,ECalculatorRegistration.CONTROLLERS.get(9).get().defaultBlockState());
            String[] ids={"ecalculator_cell_drive","ecalculator_thread_core_l6","ecalculator_thread_core_hyper_l4","ecalculator_parallel_proc_l4"};
            for(int i=0;i<ids.length;i++)world.setBlockAndUpdate(BASE.east(i+1),ECalculatorRegistration.PARTS.get(ids[i]).get().defaultBlockState());
            world.setBlockAndUpdate(BASE.east(5),ECalculatorRegistration.CASING.get().defaultBlockState());
            world.setBlockAndUpdate(CHANNEL,ECalculatorRegistration.CHANNEL.get().defaultBlockState());
            bridge(true);
            var driveState=AEBlocks.DRIVE.block().defaultBlockState();
            driveState=appeng.api.orientation.IOrientationStrategy.get(driveState).setFacing(driveState,Direction.WEST);
            world.setBlockAndUpdate(HUB,driveState);
            ((DriveBlockEntity)world.getBlockEntity(HUB)).getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_64K.stack());
            world.getBlockEntity(HUB).setChanged();
            world.setBlockAndUpdate(HUB.below(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            world.setBlockAndUpdate(CHANNEL.below(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            world.setBlockAndUpdate(FOREIGN,AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            world.setBlockAndUpdate(REQUESTER,AEBlocks.INTERFACE.block().defaultBlockState());
            world.setBlockAndUpdate(CHANNEL.west(),AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
            world.setBlockAndUpdate(CHANNEL.west(2),AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
            world.setBlockAndUpdate(BARREL,Blocks.BARREL.defaultBlockState());
            var provider=(PatternProviderBlockEntity)world.getBlockEntity(CHANNEL.west());
            var recipe=(CraftingRecipe)world.getRecipeManager().byKey(new ResourceLocation("minecraft","oak_planks")).orElseThrow();
            var inputs=new ItemStack[9];Arrays.fill(inputs,ItemStack.EMPTY);inputs[0]=new ItemStack(Items.OAK_LOG);
            provider.getLogic().getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeCraftingPattern(recipe,inputs,new ItemStack(Items.OAK_PLANKS,4),false,false));provider.setChanged();
            encodedPattern=provider.getLogic().getPatternInv().getStackInSlot(0).copy();
            require(((TileECalculatorDrive)world.getBlockEntity(BASE.east())).insertMemory(new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.C).get())),"memory insert");
            controller=(TileECalculatorController)world.getBlockEntity(BASE);core=(TileECalculatorPart)world.getBlockEntity(CORE);
            channel=(ECalculatorMEChannelNode)world.getBlockEntity(CHANNEL);requester=(InterfaceBlockEntity)world.getBlockEntity(REQUESTER);
            assembler=(MolecularAssemblerBlockEntity)world.getBlockEntity(CHANNEL.west(2));
            controller.refreshStructure();require(controller.isOperational(),"fixture disk definition did not form");
        }catch(Throwable failure){finish(failure);}
    }
    private static void bridge(boolean joined) {
        // Native zero-idle energy cells carry the network without consuming channels.
        world.setBlockAndUpdate(CUT,joined?AEBlocks.ENERGY_CELL.block().defaultBlockState():Blocks.AIR.defaultBlockState());
        for(int x=HUB.getX();x<=CUT.getX();x++)world.setBlockAndUpdate(new BlockPos(x,CUT.getY(),CUT.getZ()+1),AEBlocks.ENERGY_CELL.block().defaultBlockState());
    }
    private static boolean ready(){return channel.isNetworkActive() && !channel.getActionableNode().getGrid().getPathingService().isNetworkBooting();}
    private static long stocked(){var s=requester.getInterfaceLogic().getStorage().getStack(0);return s!=null&&s.what().equals(PLANK)?s.amount():0;}
    private static ICraftingLink pending(){return requester.getInterfaceLogic().getRequestedJobs().stream().filter(l->!l.isDone()&&!l.isCanceled()).findFirst().orElse(null);}
    private static boolean capture(){
        var link=pending();if(link==null)return false;
        for(var tasks:channel.cores())for(var candidate:tasks.cpus()){
            var other=candidate.cluster.craftingLogic.getLastLink();
            if(other!=null&&other.getCraftingID().equals(link.getCraftingID())&&candidate.cluster.craftingLogic.getWaitingFor(PLANK)>0&&assembler.getCurrentPattern()!=null){
                cpu=candidate;request=link;taskId=link.getCraftingID();reserved=cpu.bytes;return true;
            }
        }return false;
    }
    private static void enableRequest(){requester.getUpgrades().setItemDirect(0,AEItems.CRAFTING_CARD.stack());requester.getInterfaceLogic().getConfig().setStack(0,new GenericStack(PLANK,64));requester.setChanged();}
    private static void disableRequest(){requester.getUpgrades().setItemDirect(0,ItemStack.EMPTY);requester.setChanged();}
    private static void next(int value){phase=value;since=ticks;Modularmachinery.LOG.info("[MMC][ec-lifecycle] phase {} tick {}",phase,ticks);}
    private static boolean settled(){return ready()&&channel.availableBytes()==controller.totalBytes()&&assembler.getCurrentPattern()==null;}
    private static void collect(){
        long count=stocked();if(count==0)return;
        require(requester.getInterfaceLogic().getStorage().extract(0,PLANK,count,Actionable.MODULATE)==count,"collect requester");
        var barrel=(BarrelBlockEntity)world.getBlockEntity(BARREL);
        for(int i=0;i<barrel.getContainerSize()&&count>0;i++)if(barrel.getItem(i).isEmpty()){int n=(int)Math.min(64,count);barrel.setItem(i,new ItemStack(Items.OAK_PLANKS,n));count-=n;}
        require(count==0,"barrel full");barrel.setChanged();
    }
    private static void conservation(){
        var stacks=channel.getActionableNode().getGrid().getStorageService().getInventory().getAvailableStacks();
        long logs=stacks.get(LOG),planks=stacks.get(PLANK)+stocked();
        var barrel=(BarrelBlockEntity)world.getBlockEntity(BARREL);for(int i=0;i<barrel.getContainerSize();i++)if(barrel.getItem(i).is(Items.OAK_PLANKS))planks+=barrel.getItem(i).getCount();
        require(logs*4+planks==256,"material conservation failed: logs="+logs+", planks="+planks);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||server==null)return;
        try{
            ticks++;require(ticks<5000&&ticks-since<1400,"timeout phase="+phase);
            switch(phase){
                case 0->{
                    if(ticks<100||!ready())return;
                    world.setBlockAndUpdate(BASE.west(),Blocks.REDSTONE_BLOCK.defaultBlockState());controller.tick();
                    require(!controller.isOperational(),"host tick did not apply redstone stop");
                    world.setBlockAndUpdate(BASE.west(),Blocks.AIR.defaultBlockState());controller.tick();
                    require(controller.isOperational(),"host tick did not resume after redstone removal");
                    require(channel.getActionableNode().getGrid().getStorageService().getInventory().insert(LOG,64,Actionable.MODULATE,new MachineSource(channel))==64,"initial native storage");
                    enableRequest();next(1);
                }
                case 1->{if(!capture())return;checkStaleGridRoster();require(staleGridChecked,"independent AE grid was not ready");bridge(false);next(2);}
                case 2->{
                    if(ticks-since<20)return;
                    Modularmachinery.LOG.info("[MMC][ec-lifecycle] 20-tick split native cancellation={}",request.isCanceled());
                    if(request.isCanceled())disableRequest();
                    bridge(true);next(3);
                }
                case 3->{
                    if(request.isCanceled())disableRequest();
                    if(!settled()||ticks-since<100)return;
                    require(request.isCanceled()||request.isDone()&&stocked()==64,"short split neither safely cancelled nor finished original request");
                    require(request.getCraftingID().equals(taskId),"short split replaced original request");
                    conservation();collect();enableRequest();next(4);
                }
                case 4->{if(!capture())return;bridge(false);checkStaleGridRoster();next(5);}
                case 5->{
                    checkStaleGridRoster();
                    if(ticks-since<160)return;
                    require(staleGridChecked,"did not exercise a stale roster across two live grids");
                    require(request.isCanceled(),"long split did not cancel native requester");
                    require(cpu.cluster.craftingLogic.getLastLink()==null||cpu.cluster.craftingLogic.getLastLink().isCanceled(),"long split did not cancel CPU link");
                    disableRequest();
                    bridge(true);next(6);
                }
                case 6->{if(!settled()||ticks-since<100)return;conservation();collect();enableRequest();next(7);}
                case 7->{
                    if(!capture())return;oldCore=core;
                    // Ticket removal is asynchronous (~160 ticks here). Stop new dispatch, not the requester,
                    // so a genuine queued job with reserved materials still exists when the chunk finally unloads.
                    var provider=(PatternProviderBlockEntity)world.getBlockEntity(CHANNEL.west());
                    provider.getLogic().getPatternInv().setItemDirect(0,ItemStack.EMPTY);provider.setChanged();
                    world.setChunkForced(31,32,false);world.setChunkForced(32,32,false);next(8);
                }
                case 8->{
                    if((ticks-since)%200==0)Modularmachinery.LOG.info("[MMC][ec-lifecycle] unloading core chunk {}",world.getChunkSource().getChunkDebugData(new net.minecraft.world.level.ChunkPos(CORE)));
                    if(world.hasChunkAt(CORE))return;
                    unloaded=true;
                    require(cpu.cluster.craftingLogic.hasJob(),"job completed before asynchronous chunk unload");
                    require(!cpu.active(),"inaccessible CPU reference remained active");
                    require(cpu.cluster.craftingLogic.insert(PLANK,4,Actionable.SIMULATE)==0,"unloaded CPU accepts late output");
                    next(9);
                }
                case 9->{
                    if(ticks-since<160)return;
                    require(request.isCanceled(),"missing core did not cancel requester");
                    disableRequest();
                    world.setChunkForced(31,32,true);world.setChunkForced(32,32,true);
                    world.getChunk(32,32); // Forced tickets are scheduled; explicitly wait for the full disk chunk here.
                    core=(TileECalculatorPart)world.getBlockEntity(CORE);
                    require(unloaded&&core!=null,"core did not become accessible again");
                    var restored=((ECalculatorTasks)core.cpuAccess()).cpus();
                    require(restored.size()==1&&restored.get(0).bytes==reserved&&restored.get(0).cluster.craftingLogic.getLastLink().getCraftingID().equals(taskId),"saved core task ID/memory lost during chunk unload");
                    controller.refreshStructure();next(10);
                }
                case 10->{
                    if(!settled()||ticks-since<180)return;
                    require(cpu.cluster.isDestroyed()&&!cpu.active(),"old reference revived on rebind");
                    conservation();collect();
                    var provider=(PatternProviderBlockEntity)world.getBlockEntity(CHANNEL.west());
                    provider.getLogic().getPatternInv().setItemDirect(0,encodedPattern.copy());provider.setChanged();
                    enableRequest();next(11);
                }
                case 11->{
                    if(!capture())return;oldCore=core;
                    var provider=(PatternProviderBlockEntity)world.getBlockEntity(CHANNEL.west());
                    provider.getLogic().getPatternInv().setItemDirect(0,ItemStack.EMPTY);provider.setChanged();
                    for(int x:new int[]{29,31,32})world.setChunkForced(x,32,false);
                    next(12);
                }
                case 12->{
                    if(world.hasChunkAt(CORE)||!cpu.cluster.isDestroyed())return;
                    require(cpu.cluster.craftingLogic.hasJob()&&!cpu.active(),"full unload did not preserve queued CPU work");
                    next(13);
                }
                case 13->{
                    if(ticks-since<100)return;
                    for(int x:new int[]{29,31,32})world.setChunkForced(x,32,true);
                    world.getChunk(31,32);world.getChunk(32,32);world.getChunk(30,32);
                    core=(TileECalculatorPart)world.getBlockEntity(CORE);
                    require(core!=null&&core!=oldCore,"full unload did not deserialize a fresh core");
                    controller=(TileECalculatorController)world.getBlockEntity(BASE);channel=(ECalculatorMEChannelNode)world.getBlockEntity(CHANNEL);
                    requester=(InterfaceBlockEntity)world.getBlockEntity(REQUESTER);assembler=(MolecularAssemblerBlockEntity)world.getBlockEntity(CHANNEL.west(2));
                    var restored=((ECalculatorTasks)core.cpuAccess()).cpus();
                    require(restored.size()==1&&restored.get(0).bytes==reserved&&restored.get(0).cluster.craftingLogic.getLastLink().getCraftingID().equals(taskId),"full unload lost saved CPU ID/memory");
                    var provider=(PatternProviderBlockEntity)world.getBlockEntity(CHANNEL.west());
                    provider.getLogic().getPatternInv().setItemDirect(0,encodedPattern.copy());provider.setChanged();
                    controller.refreshStructure();next(14);
                }
                case 14->{
                    if(!settled()||ticks-since<200)return;
                    disableRequest();conservation();
                    require(cpu.cluster.isDestroyed()&&!cpu.active(),"fully unloaded old CPU revived");
                    Modularmachinery.LOG.info("[MMC][ec-lifecycle] PASS: source grid split completion/cancellation, foreign stale roster rejection, redstone stop/resume, inaccessible core pauses and cancels, full multi-chunk unload deserializes new core with same ID/memory, retired references reject output, native material conservation");finish(null);
                }
            }
        }catch(Throwable failure){finish(failure);}
    }
    @SuppressWarnings("unchecked")
    private static void checkStaleGridRoster() throws ReflectiveOperationException {
        if(staleGridChecked||!channel.isNetworkActive()||!cpu.cluster.craftingLogic.hasJob()||cpu.cluster.craftingLogic.getWaitingFor(PLANK)==0)return;
        var other=((appeng.blockentity.grid.AENetworkBlockEntity)world.getBlockEntity(FOREIGN)).getMainNode().getGrid();
        if(other==null||other==channel.getActionableNode().getGrid())return;
        var service=(appeng.me.service.CraftingService)other.getCraftingService();
        var field=appeng.me.service.CraftingService.class.getDeclaredField("craftingCPUClusters");field.setAccessible(true);
        var roster=(Set<appeng.me.cluster.implementations.CraftingCPUCluster>)field.get(service);
        // Reproduce the deferred-list window deliberately. Both grids and the job are real; only roster staleness is injected.
        boolean added=roster.add(cpu.cluster);
        try{
            require(service.insertIntoCpus(PLANK,4,Actionable.SIMULATE)==0,"other grid's stale CPU roster accepted an in-flight output");
            require(cpu.cluster.craftingLogic.executeCrafting(1,service,other.getEnergyService(),world)==0,"other grid executed CPU work");
        }
        finally{if(added)roster.remove(cpu.cluster);}
        staleGridChecked=true;
    }
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void finish(Throwable failure){
        if(failure==null)Modularmachinery.LOG.info("[MMC][ec-lifecycle] OVERALL: PASS");
        else Modularmachinery.LOG.error("[MMC][ec-lifecycle] OVERALL: FAIL phase {}",phase,failure);
        var stopping=server;server=null;if(stopping!=null)stopping.halt(false);
    }
}
