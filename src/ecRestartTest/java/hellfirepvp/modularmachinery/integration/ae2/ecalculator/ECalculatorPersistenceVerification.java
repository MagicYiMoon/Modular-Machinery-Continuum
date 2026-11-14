/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import appeng.api.config.*;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.*;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.*;
import appeng.me.helpers.MachineSource;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
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
import java.nio.file.*;
import java.util.*;

/** Two real JVM invocations against ONE disposable save. All AE storage/requester/assembler data stays in world NBT. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class ECalculatorPersistenceVerification {
    private static final BlockPos BASE=new BlockPos(0,120,0), CHANNEL=BASE.south(), HUB=BASE.south(2), SUPPLY=HUB.below(), REQUESTER=HUB.south(), BARREL=BASE.east(8);
    private static final AEKey LOG=AEItemKey.of(Items.OAK_LOG), PLANK=AEItemKey.of(Items.OAK_PLANKS);
    private static final Path MARKER=Path.of("ec-restart-state.nbt");
    private static MinecraftServer server;
    private static ServerLevel world;
    private static TileECalculatorController controller;
    private static TileECalculatorPart core;
    private static ECalculatorMEChannelNode channel;
    private static InterfaceBlockEntity requester;
    private static MolecularAssemblerBlockEntity assembler;
    private static int ticks,phase,since;
    private static UUID pausedId;
    private static ECalculatorCpuHost pausedCpu;
    private static boolean seed;
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        String mode=System.getProperty("modularmachinery.ecRestartTest");if(mode==null)return;
        server=event.getServer();world=server.overworld();seed=mode.equals("seed");
        try {
            require(seed || mode.equals("resume"),"unknown fixture mode");
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setChunkForced(x,z,true);
            if(seed) {
                require(!Files.exists(MARKER),"seed must use a new disposable directory");
                for(int x=-3;x<=10;x++)for(int z=0;z<=4;z++)world.setBlockAndUpdate(BASE.offset(x,0,z),Blocks.AIR.defaultBlockState());
                world.setBlockAndUpdate(BASE,ECalculatorRegistration.CONTROLLERS.get(9).get().defaultBlockState());
                String[] ids={"ecalculator_cell_drive","ecalculator_thread_core_l6","ecalculator_thread_core_hyper_l4","ecalculator_parallel_proc_l4"};
                for(int i=0;i<ids.length;i++)world.setBlockAndUpdate(BASE.east(i+1),ECalculatorRegistration.PARTS.get(ids[i]).get().defaultBlockState());
                world.setBlockAndUpdate(BASE.east(5),ECalculatorRegistration.CASING.get().defaultBlockState());
                world.setBlockAndUpdate(CHANNEL,ECalculatorRegistration.CHANNEL.get().defaultBlockState());
                var driveState=AEBlocks.DRIVE.block().defaultBlockState();
                driveState=appeng.api.orientation.IOrientationStrategy.get(driveState).setFacing(driveState,Direction.WEST);
                world.setBlockAndUpdate(HUB,driveState); // The drive's front does not accept ME connections.
                ((DriveBlockEntity)world.getBlockEntity(HUB)).getInternalInventory().setItemDirect(0,AEItems.ITEM_CELL_64K.stack());
                world.getBlockEntity(HUB).setChanged();power(true);
                world.setBlockAndUpdate(REQUESTER,AEBlocks.INTERFACE.block().defaultBlockState());
                world.setBlockAndUpdate(CHANNEL.west(),AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
                world.setBlockAndUpdate(CHANNEL.west(2),AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
                world.setBlockAndUpdate(BARREL,Blocks.BARREL.defaultBlockState());
                var provider=(PatternProviderBlockEntity)world.getBlockEntity(CHANNEL.west());
                var recipe=(CraftingRecipe)world.getRecipeManager().byKey(new ResourceLocation("minecraft","oak_planks")).orElseThrow();
                var inputs=new ItemStack[9];Arrays.fill(inputs,ItemStack.EMPTY);inputs[0]=new ItemStack(Items.OAK_LOG);
                provider.getLogic().getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeCraftingPattern(recipe,inputs,new ItemStack(Items.OAK_PLANKS,4),false,false));provider.setChanged();
                require(((TileECalculatorDrive)world.getBlockEntity(BASE.east())).insertMemory(new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.C).get())),"memory insert");
            }
            controller=(TileECalculatorController)world.getBlockEntity(BASE);channel=(ECalculatorMEChannelNode)world.getBlockEntity(CHANNEL);
            core=(TileECalculatorPart)world.getBlockEntity(BASE.east(2));requester=(InterfaceBlockEntity)world.getBlockEntity(REQUESTER);
            assembler=(MolecularAssemblerBlockEntity)world.getBlockEntity(CHANNEL.west(2));
            controller.refreshStructure();require(controller.isOperational(),"disk machine definition did not form");
            if(!seed) {
                var marker=NbtIo.readCompressed(MARKER.toFile());require(marker!=null && marker.getString("Stage").equals("seed"),"missing successful seed marker");
                var cpus=((ECalculatorTasks)core.cpuAccess()).cpus();require(cpus.size()==1,"saved CPU not restored before first tick");
                var cpu=cpus.get(0);var link=cpu.cluster.craftingLogic.getLastLink();
                require(link!=null && link.getCraftingID().equals(marker.getUUID("CraftId")) && cpu.bytes==marker.getLong("ReservedBytes") && !link.isCanceled(),"CPU task ID/memory changed on process restart");
                require(requester.getInterfaceLogic().getRequestedJobs().stream().anyMatch(l->l.getCraftingID().equals(link.getCraftingID()) && !l.isCanceled()),"native interface requester link was not loaded from world NBT");
                checkUnreadableKeysAndPatterns(core.backendSnapshot());
                phase=2;
                Modularmachinery.LOG.info("[MMC][ec-persistence] Reloaded actual world CPU and native requester link {} before server ticks",link.getCraftingID());
            }
            Modularmachinery.LOG.info("[MMC][ec-persistence] {} waiting for real ME grid",seed?"SEED":"RESUME");
        } catch(Throwable failure) {finish(failure);}
    }
    private static void power(boolean on) {world.setBlockAndUpdate(SUPPLY,on?AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState():Blocks.AIR.defaultBlockState());}
    private static MEStorage network() {return channel.getActionableNode().getGrid().getStorageService().getInventory();}
    private static boolean ready() {return channel.isNetworkActive() && !channel.getActionableNode().getGrid().getPathingService().isNetworkBooting();}
    private static long stocked() {var stack=requester.getInterfaceLogic().getStorage().getStack(0);return stack!=null && stack.what().equals(PLANK)?stack.amount():0;}
    private static ICraftingLink requested() {return requester.getInterfaceLogic().getRequestedJobs().stream().filter(l->!l.isCanceled() && !l.isDone()).findFirst().orElse(null);}
    private static ECalculatorCpuHost inflight() {
        var link=requested();if(link==null)return null;
        for(var tasks:channel.cores())for(var cpu:tasks.cpus()) {
            var other=cpu.cluster.craftingLogic.getLastLink();
            if(other!=null && other.getCraftingID().equals(link.getCraftingID()) && cpu.cluster.craftingLogic.getWaitingFor(PLANK)>0)return cpu;
        }return null;
    }
    private static boolean finished() {return requested()==null && stocked()==64 && channel.availableBytes()==controller.totalBytes();}
    private static void next(int value) {phase=value;since=ticks;Modularmachinery.LOG.info("[MMC][ec-persistence] phase {} tick {}",phase,ticks);}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || server==null)return;
        try {
            ticks++;require(ticks<3000 && ticks-since<650,"timeout phase="+phase+", stocked="+stocked()+", requested="+requester.getInterfaceLogic().getRequestedJobs().size());
            switch(phase) {
                case 0 -> {
                    if(ticks<100 || !ready())return;
                    require(network().insert(LOG,64,Actionable.MODULATE,new MachineSource(channel))==64,"native 64K cell did not accept raw materials");
                    requester.getUpgrades().setItemDirect(0,AEItems.CRAFTING_CARD.stack());
                    requester.getInterfaceLogic().getConfig().setStack(0,new GenericStack(PLANK,64));requester.setChanged();next(1);
                }
                case 1 -> {
                    var cpu=inflight();if(cpu==null)return;
                    require(stocked()<64 && cpu.cluster.craftingLogic.getWaitingFor(PLANK)>0,"task already completed before save");
                    var marker=new CompoundTag();marker.putString("Stage","seed");marker.putUUID("CraftId",cpu.cluster.craftingLogic.getLastLink().getCraftingID());marker.putLong("ReservedBytes",cpu.bytes);
                    marker.putLong("AlreadyDelivered",stocked());marker.putLong("WaitingFor",cpu.cluster.craftingLogic.getWaitingFor(PLANK));
                    require(assembler.getCurrentPattern()!=null,"no actual assembler recipe in flight");
                    NbtIo.writeCompressed(marker,MARKER.toFile());
                    Modularmachinery.LOG.info("[MMC][ec-persistence] SEED PASS: live native interface request, actual assembler working, CPU inventory/job/links and native ME cell will be saved by server shutdown; waiting={} delivered={}",marker.getLong("WaitingFor"),stocked());
                    finish(null);
                }
                case 2 -> {
                    if(!ready() || !finished())return;
                    conservation(48,64,0);collect(0);next(3);
                }
                case 3 -> {
                    var cpu=inflight();if(cpu==null)return;pausedCpu=cpu;pausedId=requested().getCraftingID();
                    power(false);channel.getActionableNode().getGrid().getEnergyService().extractAEPower(Double.MAX_VALUE,Actionable.MODULATE,PowerMultiplier.ONE);next(4);
                }
                case 4 -> {
                    if(ticks-since<140)return;
                    require(!channel.isNetworkActive() && pausedCpu.cluster.craftingLogic.hasJob() && !pausedCpu.cluster.craftingLogic.getLastLink().isCanceled(),"offline requester CPU was cancelled");
                    require(requested()!=null && requested().getCraftingID().equals(pausedId),"requester lost/replaced job while power was off");
                    power(true);next(5);
                }
                case 5 -> {
                    if(!ready() || !finished())return;
                    conservation(32,64,64);collect(1);next(6);
                }
                case 6 -> {
                    var cpu=inflight();if(cpu==null)return;pausedCpu=cpu;pausedId=requested().getCraftingID();
                    for(int i=1;i<=6;i++)world.setBlockAndUpdate(HUB.east(i),AEBlocks.INTERFACE.block().defaultBlockState());next(7);
                }
                case 7 -> {
                    if(ticks-since<160)return;
                    require(!channel.isNetworkActive() && pausedCpu.cluster.craftingLogic.hasJob() && requested()!=null && requested().getCraftingID().equals(pausedId),"channel-limited request cancelled or replaced");
                    for(int i=1;i<=6;i++)world.setBlockAndUpdate(HUB.east(i),Blocks.AIR.defaultBlockState());next(8);
                }
                case 8 -> {
                    if(!ready() || !finished())return;
                    conservation(16,64,128);collect(2);next(9);
                }
                case 9 -> {
                    var cpu=inflight();if(cpu==null)return;pausedCpu=cpu;
                    power(false);channel.getActionableNode().getGrid().getEnergyService().extractAEPower(Double.MAX_VALUE,Actionable.MODULATE,PowerMultiplier.ONE);next(11);
                }
                case 11 -> {
                    if(ticks-since<60)return;
                    require(!channel.isNetworkActive() && requested()!=null,"offline cancellation fixture did not pause");
                    var chunk=world.getChunkAt(core.getBlockPos());chunk.setUnsaved(false);
                    requested().cancel();
                    require(chunk.isUnsaved() && pausedCpu.cluster.craftingLogic.hasJob() && pausedCpu.cluster.craftingLogic.getLastLink().isCanceled(),"offline link cancellation failed to dirty the CPU core");
                    require(core.backendSnapshot().getCompound("AE2Crafting").getList("CPUs",10).getCompound(0).getCompound("Logic").getCompound("job").getCompound("link").getBoolean("canceled"),"cancelled link flag missing from saved core data");
                    requester.getUpgrades().setItemDirect(0,ItemStack.EMPTY);requester.setChanged();power(true);next(10);
                }
                case 10 -> {
                    if(ticks-since<150 || channel.availableBytes()!=controller.totalBytes())return;
                    require(requested()==null && pausedCpu.cluster.isDestroyed(),"requester cancel did not clear CPU/link");
                    long logs=network().getAvailableStacks().get(LOG), planks=network().getAvailableStacks().get(PLANK)+stocked()+barrelCount();
                    require(4*logs+planks==256,"cancelled in-flight recipe lost/duplicated materials: logs="+logs+", planks="+planks);
                    var marker=NbtIo.readCompressed(MARKER.toFile());marker.putString("Stage","complete");NbtIo.writeCompressed(marker,MARKER.toFile());
                    Modularmachinery.LOG.info("[MMC][ec-persistence] PASS: two actual server JVMs/world NBT, original CPU/requester IDs, native 64K storage and assembler inventory, 64 planks completed after restart, same-link 140-tick power pause and 160-tick channel pause, offline requester cancellation dirties core and serializes flag, in-flight material conservation, unreadable key/pattern preservation");finish(null);
                }
            }
        } catch(Throwable failure) {finish(failure);}
    }
    private static void collect(int slot) {
        require(requester.getInterfaceLogic().getStorage().extract(0,PLANK,64,Actionable.MODULATE)==64,"collect real requester output");
        var barrel=(BarrelBlockEntity)world.getBlockEntity(BARREL);require(barrel.getItem(slot).isEmpty(),"test would overwrite physical output");barrel.setItem(slot,new ItemStack(Items.OAK_PLANKS,64));barrel.setChanged();
    }
    private static long barrelCount() {var barrel=(BarrelBlockEntity)world.getBlockEntity(BARREL);long count=0;for(int i=0;i<barrel.getContainerSize();i++)if(barrel.getItem(i).is(Items.OAK_PLANKS))count+=barrel.getItem(i).getCount();return count;}
    private static void conservation(long logs,long planks,long collected) {require(network().getAvailableStacks().get(LOG)==logs && stocked()==planks && barrelCount()==collected && network().getAvailableStacks().get(PLANK)==0,"native drive/requester material conservation");}
    private static void checkUnreadableKeysAndPatterns(CompoundTag valid) {
        for(boolean pattern:new boolean[]{false,true}) {
            var corrupt=valid.copy();var logic=corrupt.getCompound("AE2Crafting").getList("CPUs",10).getCompound(0).getCompound("Logic");
            if(pattern) {
                var list=logic.getCompound("job").getList("tasks",10);require(!list.isEmpty(),"restart fixture has no saved pattern");
                list.getCompound(0).putString("id","minecraft:stone");
            } else {
                var entry=new CompoundTag();entry.putString("#c","missing:dependency");entry.putLong("#",9);logic.getList("inventory",10).add(entry);
            }
            var detached=new TileECalculatorPart(BASE.above(8),core.getBlockState());detached.setLevel(world);detached.restoreBackend(corrupt);
            require(detached.cpuAccess() instanceof ECalculatorTasks tasks && !tasks.valid() && detached.backendSnapshot().equals(corrupt),"unreadable AE key/pattern was silently discarded");
        }
    }
    private static void require(boolean value,String message) {if(!value)throw new AssertionError(message);}
    private static void finish(Throwable failure) {
        if(failure==null)Modularmachinery.LOG.info("[MMC][ec-persistence] {} OVERALL: PASS",seed?"SEED":"RESUME");
        else Modularmachinery.LOG.error("[MMC][ec-persistence] {} OVERALL: FAIL phase {}",seed?"SEED":"RESUME",phase,failure);
        var stopping=server;server=null;if(stopping!=null)stopping.halt(false);
    }
}
