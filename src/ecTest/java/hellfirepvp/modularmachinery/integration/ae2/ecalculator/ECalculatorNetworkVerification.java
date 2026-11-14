/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.*;
import appeng.api.stacks.*;
import appeng.api.storage.*;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.menu.ISubMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;
import java.util.concurrent.Future;

/** Disposable real grid/pattern-provider/assembler fixture, never included in release artifacts. */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID)
public final class ECalculatorNetworkVerification {
    private static final BlockPos BASE = new BlockPos(0,120,0), CHANNEL = BASE.south(), POWER = BASE.south(2), CASING = BASE.east(5);
    private static final AEKey LOG = AEItemKey.of(Items.OAK_LOG), PLANK = AEItemKey.of(Items.OAK_PLANKS);
    private static final ResourceLocation ID = new ResourceLocation("modularmachinery", ECalculatorRegistration.machineName(9));
    private static final IActionSource SOURCE = IActionSource.empty();
    private static final CounterStorage storage = new CounterStorage();
    private static MinecraftServer server;
    private static ServerLevel world;
    private static TileECalculatorController controller;
    private static ECalculatorMEChannelNode channel;
    private static TileECalculatorPart normal, hyper;
    private static IGrid grid;
    private static DynamicMachine previous;
    private static ICraftingPlan plan;
    private static Future<ICraftingPlan> calculation;
    private static CraftingCPUCluster oldCpu, nativeCpu;
    private static final List<ECalculatorCpuHost> running = new ArrayList<>();
    private static int ticks, phase, since;
    private static long baselineLogs;
    private static AEKey rollbackKey;
    private static ECalculatorCpuHost rollbackCpu;
    private static ECalculatorCpuHost measuredNormal;
    private static final List<ECalculatorCpuHost> statisticsCpus = new ArrayList<>();
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if (!Boolean.getBoolean("modularmachinery.ecNetworkTest")) return;
        server = event.getServer(); world = server.overworld();
        try {
            for (int x=-1;x<=1;x++) for(int z=-1;z<=1;z++) world.setChunkForced(x,z,true);
            for(int x=-3;x<=12;x++) for(int z=0;z<=3;z++) world.setBlockAndUpdate(BASE.offset(x,0,z),Blocks.AIR.defaultBlockState());
            var pattern=new MachinePattern();
            String[] ids={"ecalculator_cell_drive","ecalculator_thread_core_l4","ecalculator_thread_core_hyper_l4","ecalculator_parallel_proc_l4","ecalculator_casing"};
            for(int i=0;i<ids.length;i++) pattern.addPart(new MachinePattern.Part(i+1,0,0,"modularmachinery:"+ids[i]));
            pattern.addPart(new MachinePattern.Part(0,0,1,"modularmachinery:ecalculator_me_channel"));
            previous=MachineReloadListener.getLoader().getMachines().put(ID,new DynamicMachine(ID.getPath(),"EC network fixture",pattern));
            world.setBlockAndUpdate(BASE,ECalculatorRegistration.CONTROLLERS.get(9).get().defaultBlockState());
            for(int i=0;i<ids.length;i++) world.setBlockAndUpdate(BASE.east(i+1), i==4 ? ECalculatorRegistration.CASING.get().defaultBlockState() : ECalculatorRegistration.PARTS.get(ids[i]).get().defaultBlockState());
            world.setBlockAndUpdate(CHANNEL,ECalculatorRegistration.CHANNEL.get().defaultBlockState());
            power(true);
            world.setBlockAndUpdate(POWER.east(),AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
            world.setBlockAndUpdate(CHANNEL.west(),AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
            world.setBlockAndUpdate(CHANNEL.west(2),AEBlocks.MOLECULAR_ASSEMBLER.block().defaultBlockState());
            world.setBlockAndUpdate(BASE.east(12),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
            var provider=(PatternProviderBlockEntity)world.getBlockEntity(CHANNEL.west());
            var recipe=(CraftingRecipe)world.getRecipeManager().byKey(new ResourceLocation("minecraft","oak_planks")).orElseThrow();
            var inputs=new ItemStack[9];Arrays.fill(inputs,ItemStack.EMPTY);inputs[0]=new ItemStack(Items.OAK_LOG);
            provider.getLogic().getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeCraftingPattern(recipe,inputs,new ItemStack(Items.OAK_PLANKS,4),false,false));
            controller=(TileECalculatorController)world.getBlockEntity(BASE);
            channel=(ECalculatorMEChannelNode)world.getBlockEntity(CHANNEL);
            normal=(TileECalculatorPart)world.getBlockEntity(BASE.east(2)); hyper=(TileECalculatorPart)world.getBlockEntity(BASE.east(3));
            require(((TileECalculatorDrive)world.getBlockEntity(BASE.east())).insertMemory(new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.C).get())),"memory insert");
            controller.refreshStructure();require(controller.isOperational(),"fixture not formed");
            Modularmachinery.LOG.info("[MMC][ec-network] Waiting for real AE grid boot");
        } catch(Throwable failure) { finish(failure); }
    }
    private static void power(boolean on) { world.setBlockAndUpdate(POWER,on ? AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState() : Blocks.AIR.defaultBlockState()); }
    private static boolean ready() { return ticks-since>=100 && channel.isNetworkActive() && !grid().getPathingService().isNetworkBooting(); }
    private static IGrid grid() { return channel.getActionableNode().getGrid(); }
    private static ECalculatorCpuHost idle() { return channel.publishedCpus().stream().filter(c->c.tasks==null).findFirst().orElseThrow(); }
    private static void next(int step) { phase=step;since=ticks;Modularmachinery.LOG.info("[MMC][ec-network] phase {} tick {}",phase,ticks); }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || server==null)return;
        try {
            ticks++;if(ticks>2400 || ticks-since>500)throw new AssertionError("timeout at phase "+phase+", stock="+storage.values+", statistics CPUs="+statisticsCpus.stream().map(c->"destroyed="+c.cluster.isDestroyed()+",job="+c.cluster.craftingLogic.hasJob()+",inventory="+c.cluster.craftingLogic.getInventory().list+",sample="+c.measurement()).toList());
            switch(phase) {
                case 0 -> {
                    if(!ready())return;grid=grid();
                    appearance(ECalculatorAppearance.Status.ON);
                    require(channel.getActionableNode().hasFlag(GridFlags.REQUIRE_CHANNEL) && channel.getActionableNode().hasFlag(GridFlags.DENSE_CAPACITY),"channel flags");
                    require(idle().cluster.getAvailableStorage()==16_777_216_000L && idle().cluster.getCoProcessors()==256,"CPU advertised memory/parallel");
                    nativeCpu=(CraftingCPUCluster)grid.getCraftingService().getCpus().stream().filter(c->((ECalculatorCpuBridge)c).mmc$ecHost()==null).findFirst().orElseThrow();
                    require(nativeCpu.getAvailableStorage()==1024 && nativeCpu.getCoProcessors()==0,"native CPU metadata changed");
                    grid.getStorageService().addGlobalStorageProvider(mounts->mounts.mount(storage));storage.set(LOG,32);
                    next(11);
                }
                case 11 -> {
                    if(ticks-since<5)return;
                    require(grid.getCraftingService().isCraftable(PLANK),"provider pattern not available");
                    require(grid.getStorageService().getInventory().extract(LOG,32,Actionable.SIMULATE,SOURCE)==32,"fixture storage not mounted");
                    calculation=grid.getCraftingService().beginCraftingCalculation(world,()->new appeng.me.helpers.MachineSource(channel),PLANK,4,CalculationStrategy.REPORT_MISSING_ITEMS);next(1);
                }
                case 1 -> {
                    if(!calculation.isDone())return;plan=calculation.get();require(!plan.simulation() && plan.bytes()>0,"real AE calculation failed: simulation="+plan.simulation()+", bytes="+plan.bytes()+", missing="+plan.missingItems());
                    var offer=idle();storage.set(LOG,0);
                    require(!grid.getCraftingService().submitJob(plan,null,offer.cluster,true,SOURCE).successful() && offer.tasks==null && channel.availableBytes()==controller.totalBytes(),"missing ingredient leaked reservation");
                    storage.set(LOG,32);
                    var other=((IInWorldGridNodeHost)world.getBlockEntity(BASE.east(12))).getGridNode(Direction.WEST).getGrid();
                    require(!offer.cluster.submitJob(other,plan,SOURCE,null).successful() && offer.tasks==null,"wrong-grid submission accepted");
                    require(!offer.cluster.craftingLogic.trySubmitJob(grid,plan,SOURCE,null).successful(),"direct submission bypassed memory reservation");
                    require(grid.getCraftingService().submitJob(plan,null,nativeCpu,true,SOURCE).successful(),"native CPU no longer submits");next(2);
                }
                case 2 -> {
                    if(nativeCpu.isBusy() || storage.count(PLANK)<4)return;
                    require(storage.count(LOG)==31 && storage.count(PLANK)==4,"native crafting conservation");storage.set(PLANK,0);
                    var offer=idle();oldCpu=offer.cluster;
                    measuredNormal=offer;
                    // Exercise AE's actual server menu submission method with a player action source.
                    var player=new FakePlayer(world,new GameProfile(UUID.randomUUID(),"ECMenuTest"));
                    var host=new MenuHost();var menu=new CraftConfirmMenu(1,player.getInventory(),host);
                    setField(menu,"result",plan);setField(menu,"selectedCpu",oldCpu);menu.startJob();
                    require(host.returned && oldCpu.isBusy() && offer.tasks==normal.cpuAccess() && !offer.hyper && offer.bytes==plan.bytes(),"terminal menu failed normal-slot submission");
                    require(channel.availableBytes()==controller.totalBytes()-plan.bytes(),"normal memory reservation");next(3);
                    controller.refreshPartAppearances();
                    require(normal.appearance().status()==ECalculatorAppearance.Status.RUN,"normal CPU did not light RUN");
                }
                case 3 -> {
                    if(oldCpu.isBusy() || storage.count(PLANK)<4 || !oldCpu.isDestroyed())return;
                    var measured=measuredNormal.measurement();
                    require(measured.ready() && measured.nanosPerTick()>0 && Math.abs(measured.operationsPerTick()*measured.samples()-1)<.000001,
                            "real normal task must count one pushed pattern, not four output items: "+measured);
                    appearance(ECalculatorAppearance.Status.ON);
                    require(storage.count(LOG)==30 && storage.count(PLANK)==4 && channel.availableBytes()==controller.totalBytes(),"EC complete/release conservation");storage.set(PLANK,0);
                    channel.tick();oldCpu=idle().cluster;require(grid.getCraftingService().submitJob(plan,null,oldCpu,true,SOURCE).successful(),"persistence task submit");
                    world.setBlockAndUpdate(CASING,Blocks.AIR.defaultBlockState());controller.refreshStructure();
                    require(!oldCpu.isActive() && oldCpu.craftingLogic.insert(PLANK,4,Actionable.MODULATE)==0,"broken structure accepted in-flight output");
                    var disk=normal.saveWithoutMetadata();require(disk.getCompound("BackendData").getCompound("AE2Crafting").getList("CPUs",10).size()==1,"actual job not saved");
                    world.setBlockAndUpdate(normal.getBlockPos(),Blocks.AIR.defaultBlockState());
                    world.setBlockAndUpdate(BASE.east(2),ECalculatorRegistration.PARTS.get("ecalculator_thread_core_l4").get().defaultBlockState());
                    normal=(TileECalculatorPart)world.getBlockEntity(BASE.east(2));normal.load(disk);
                    require(oldCpu.isDestroyed(),"removed core retained live CPU");
                    world.setBlockAndUpdate(CASING,ECalculatorRegistration.CASING.get().defaultBlockState());controller.refreshStructure();next(4);
                }
                case 4 -> {
                    if(!ready() || storage.count(PLANK)<4)return;
                    require(storage.count(LOG)==29 && storage.count(PLANK)==4 && channel.availableBytes()==controller.totalBytes(),"restored job lost/duplicated ingredients");
                    require(!grid.getCraftingService().submitJob(plan,null,oldCpu,true,SOURCE).successful(),"retired CPU revived");
                    storage.set(PLANK,0);baselineLogs=storage.count(LOG);
                    for(int i=0;i<3;i++) {channel.tick();var offer=idle();require(grid.getCraftingService().submitJob(plan,null,offer.cluster,true,SOURCE).successful(),"multi task submission "+i);running.add(offer);require(offer.hyper==(i>0),"normal/hyper slot ordering");}
                    channel.tick();require(channel.publishedCpus().size()==3 && channel.publishedCpus().stream().noneMatch(c->c.tasks==null),"full slots still advertised idle");
                    appearance(ECalculatorAppearance.Status.RUN);
                    long extra=(long)(plan.bytes()*0.1F);require(channel.availableBytes()==controller.totalBytes()-3*plan.bytes()-2*extra,"hyper extra memory mismatch");
                    storage.accept=false;checkMonitor(running);for(var cpu:running)cpu.cluster.cancelJob();
                    require(running.stream().allMatch(c->!c.cluster.isDestroyed() && !c.cluster.craftingLogic.getInventory().list.isEmpty()),"cancel discarded unreturnable inventory");next(5);
                }
                case 5 -> {
                    if(ticks-since<10)return;require(channel.availableBytes()<controller.totalBytes(),"memory freed before cancelled items returned");
                    appearance(ECalculatorAppearance.Status.RUN);
                    storage.accept=true;next(6);
                }
                case 6 -> {
                    if(running.stream().anyMatch(c->!c.cluster.isDestroyed()))return;
                    appearance(ECalculatorAppearance.Status.ON);
                    require(storage.count(LOG)==baselineLogs && storage.count(PLANK)==0 && channel.availableBytes()==controller.totalBytes(),"cancel return/release conservation");
                    channel.tick();oldCpu=idle().cluster;power(false);next(7);
                }
                case 7 -> {
                    if(ticks-since<80 || channel.isNetworkActive())return;
                    appearance(ECalculatorAppearance.Status.ON);
                    require(!oldCpu.isActive() && oldCpu.craftingLogic.insert(PLANK,4,Actionable.MODULATE)==0 && !oldCpu.submitJob(grid,plan,SOURCE,null).successful(),"unpowered CPU accepted task/input");power(true);next(8);
                }
                case 8 -> {
                    if(!ready())return;
                    require(!grid.getCraftingService().submitJob(plan,null,oldCpu,true,SOURCE).successful(),"old idle lease revived after power return");
                    for(int i=2;i<=9;i++)world.setBlockAndUpdate(POWER.east(i),AEBlocks.INTERFACE.block().defaultBlockState());next(9);
                }
                case 9 -> {
                    if(ticks-since<120 || grid().getPathingService().isNetworkBooting())return;
                    require(!channel.isNetworkActive() && grid().getCraftingService().getCpus().stream().noneMatch(c->((ECalculatorCpuBridge)c).mmc$ecHost()!=null),"channel limit bypassed");
                    for(int i=2;i<=9;i++)world.setBlockAndUpdate(POWER.east(i),Blocks.AIR.defaultBlockState());next(10);
                }
                case 10 -> {
                    if(!ready())return;
                    require(idle().cluster.isActive() && nativeCpu.isActive(),"CPU not restored after channel recovery");
                    if(grid!=grid()) { grid=grid();grid.getStorageService().addGlobalStorageProvider(mounts->mounts.mount(storage));next(15);return; }
                    next(15);
                }
                case 15 -> {
                    if(ticks-since<5)return;
                    checkMemoryBoundaries();
                    next(12);
                }
                case 12 -> {
                    if(channel.availableBytes()!=controller.totalBytes())return;
                    var corrupt=new net.minecraft.nbt.CompoundTag();var badState=new net.minecraft.nbt.CompoundTag();badState.putInt("Version",1);
                    var badList=new net.minecraft.nbt.ListTag();badList.add(net.minecraft.nbt.StringTag.valueOf("must not silently clear"));badState.put("CPUs",badList);corrupt.put("AE2Crafting",badState);
                    normal.restoreBackend(corrupt);channel.tick();require(channel.availableBytes()==0 && normal.backendSnapshot().equals(corrupt),"invalid task list was cleared or memory reused");
                    normal.restoreBackend(new net.minecraft.nbt.CompoundTag());channel.tick();
                    var used=new KeyCounter();used.add(AEItemKey.of(Items.DIAMOND),1);used.add(AEItemKey.of(Items.EMERALD),1);rollbackKey=used.iterator().next().getKey();storage.set(rollbackKey,1);
                    storage.accept=false;rollbackCpu=idle();var failed=grid().getCraftingService().submitJob(new PlanView(plan,plan.bytes(),used),null,rollbackCpu.cluster,true,SOURCE);
                    require(!failed.successful() && rollbackCpu.tasks!=null && rollbackCpu.cluster.isBusy() && !rollbackCpu.cluster.craftingLogic.hasJob() && rollbackCpu.cluster.craftingLogic.getInventory().list.get(rollbackKey)==1 && storage.count(rollbackKey)==0,"missing-ingredient rollback lost refused items");
                    next(13);
                }
                case 13 -> {
                    if(ticks-since<5)return;
                    require(!rollbackCpu.cluster.isDestroyed() && rollbackCpu.cluster.craftingLogic.getInventory().list.get(rollbackKey)==1,"failed submission released unreturnable items");
                    storage.accept=true;next(14);
                }
                case 14 -> {
                    if(!rollbackCpu.cluster.isDestroyed())return;
                    require(storage.count(rollbackKey)==1 && channel.availableBytes()==controller.totalBytes(),"failed-extraction rollback return conservation");
                    Modularmachinery.LOG.info("[MMC][ec-appearance-ae] PASS: real normal/hyper CPU RUN, retained cancel-return RUN, task release ON, power loss keeps bound ON, metadata refresh preserves CPU identities and world palette");
                    storage.set(LOG,16);storage.set(PLANK,0);storage.set(AEItemKey.of(Items.DIAMOND),3);
                    // Retain a real unused initial item so completed CPUs survive storage refusal long enough to measure.
                    // Standalone final outputs stay in the assembler when the network refuses them, not in CPU inventory.
                    var statisticsInputs=new KeyCounter();for(var entry:plan.usedItems())statisticsInputs.add(entry.getKey(),entry.getLongValue());
                    statisticsInputs.add(AEItemKey.of(Items.DIAMOND),1);
                    for(int i=0;i<3;i++) { channel.tick();var cpu=idle();require(grid().getCraftingService().submitJob(new PlanView(plan,plan.bytes(),statisticsInputs),null,cpu.cluster,true,SOURCE).successful(),"statistics actual task submit");statisticsCpus.add(cpu); }
                    storage.accept=false;storage.acceptPlanks=true;next(20);
                }
                case 20 -> {
                    if(statisticsCpus.stream().anyMatch(c->c.cluster.craftingLogic.hasJob() || c.cluster.craftingLogic.getInventory().list.get(AEItemKey.of(Items.DIAMOND))!=1))return;
                    require(statisticsCpus.stream().filter(c->c.hyper).count()==2,"statistics hyper-task fixture");
                    for(var cpu:statisticsCpus) {
                        var m=cpu.measurement();
                        require(m.ready() && m.nanosPerTick()>0 && m.samples()<100 && Math.abs(m.operationsPerTick()*m.samples()-1)<.000001,
                                "actual normal/hyper patterns != one, or failed/returning updates repeated stale operations: "+m);
                    }
                    checkStatisticsMonitor(true);
                    power(false);next(21);
                }
                case 21 -> {
                    if(ticks-since<140 || channel.isNetworkActive())return;
                    for(var cpu:statisticsCpus) require(!cpu.cluster.isDestroyed() && cpu.measurement().samples()==100 && cpu.measurement().operationsPerTick()==0,
                            "paused/return-only CPU reused stale successful operations: "+cpu.measurement());
                    checkStatisticsMonitor(false);
                    power(true);next(22);
                }
                case 22 -> {
                    if(!ready())return;
                    // Removing the power block can move the channel to a new grid; fixture mounts belong to a grid.
                    if(grid!=grid()) { grid=grid();grid.getStorageService().addGlobalStorageProvider(mounts->mounts.mount(storage));next(22);return; }
                    storage.accept=true;storage.acceptPlanks=false;next(23);
                }
                case 23 -> {
                    if(statisticsCpus.stream().anyMatch(c->!c.cluster.isDestroyed()))return;
                    if(storage.count(PLANK)<12)return;
                    require(storage.count(LOG)==13 && storage.count(PLANK)==12 && storage.count(AEItemKey.of(Items.DIAMOND))==3 && channel.availableBytes()==controller.totalBytes(),"statistics task conservation/release");
                    require(((ECalculatorCpuBridge)(Object)nativeCpu).mmc$ecHost()==null,"native CPU acquired EC measurement state");
                    Modularmachinery.LOG.info("[MMC][ec-statistics-ae] PASS: real normal/two hyper tasks each push one pattern for four items, real nanos, empty/return/paused ticks do not reuse usedOps, 100-update expiry, bounded menu totals/page codec, no saved metrics, native CPU isolation and material conservation");
                    Modularmachinery.LOG.info("[MMC][ec-network] PASS: real planner/provider/assembler, AE menu startJob, native CPU coexistence, normal/hyper slots, long memory/10-percent threshold, failed-extraction refused return retention, wrong-grid/direct/stale rejection, invalid NBT preservation, core task NBT replacement, cancellation with blocked return, power/channel loss and recovery");finish(null);
                }
            }
        } catch(Throwable failure) { finish(failure); }
    }
    private static void checkMemoryBoundaries() {
        channel.tick();var offered=idle();long total=controller.totalBytes();
        require(!grid().getCraftingService().submitJob(new PlanView(plan,Long.MAX_VALUE,plan.usedItems()),null,offered.cluster,true,SOURCE).successful() && offered.tasks==null && channel.availableBytes()==total,"overflow/overcommit accepted");
        long bytes=(long)(total*0.46);var large=new PlanView(plan,bytes,plan.usedItems());
        var first=idle();var result=grid().getCraftingService().submitJob(large,null,first.cluster,true,SOURCE);require(result.successful(),"long normal allocation: "+result.errorCode()+" "+result.errorDetail()+", stock="+storage.count(LOG)+", visible="+grid().getStorageService().getInventory().getAvailableStacks().get(LOG));
        channel.tick();var second=idle();require(grid().getCraftingService().submitJob(large,null,second.cluster,true,SOURCE).successful(),"long hyper allocation");channel.tick();
        require(second.hyper && second.bytes==bytes+(long)(bytes*0.1F) && channel.availableBytes()<total*0.1F && channel.publishedCpus().stream().noneMatch(c->c.tasks==null),"10-percent idle threshold/float hyper charge");
        checkMonitor(List.of(first,second));first.cluster.cancelJob();second.cluster.cancelJob();
    }
    private static void checkStatisticsMonitor(boolean pushed) {
        var player=new FakePlayer(world,new GameProfile(UUID.randomUUID(),"ECStatisticsAE"));player.setPos(BASE.getX()+.5,BASE.getY(),BASE.getZ()+2);
        var session=UUID.randomUUID();var menu=hellfirepvp.modularmachinery.common.menu.MenuECalculatorController.forController(4,player.getInventory(),controller,session);
        player.containerMenu=menu;menu.broadcastChanges();var page=menu.statistics();
        require(page.ready() && page.tasks().size()==3 && (pushed ? page.operationsPerTick()>0 : page.operationsPerTick()==0),"real measured monitor totals");
        require(page.nanosPerTick()==statisticsCpus.stream().mapToLong(c->c.measurement().nanosPerTick()).sum(),"aggregate CPU nanos");
        for(var cpu:statisticsCpus) require(page.task(cpu.monitorId).equals(cpu.measurement()),"task statistics identity");
        var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        try {page.write(buffer);require(ECalculatorStatistics.Page.read(buffer).equals(page) && buffer.readableBytes()==0,"actual statistics page codec");}finally{buffer.release();}
        require(!normal.backendSnapshot().getCompound("AE2Crafting").getAllKeys().contains("Statistics"),"runtime statistics persisted in tasks");
        player.containerMenu=player.inventoryMenu;
    }
    private static void appearance(ECalculatorAppearance.Status expected) {
        var cores=controller.parts().stream().filter(p->p.specification().normalSlots()+p.specification().hyperSlots()>0).toList();
        var before=cores.stream().map(p->((ECalculatorTasks)p.cpuAccess()).cpus()).toList();
        controller.refreshPartAppearances();
        for(int i=0;i<cores.size();i++) {
            var core=cores.get(i);
            require(core.appearance().status()==expected,"core appearance "+expected+" at "+core.getBlockPos()+" got "+core.appearance());
            require(core.getBlockState().getValue(ECalculatorAppearance.STATUS)==ECalculatorAppearance.Status.OFF,"appearance changed authoritative state");
            require(((ECalculatorTasks)core.cpuAccess()).cpus().equals(before.get(i)),"appearance replaced crafting CPUs");
            require(core.getUpdateTag().getAllKeys().equals(Set.of("Appearance")),"task data sent in appearance packet");
        }
    }
    private static void checkMonitor(List<ECalculatorCpuHost> cpus) {
        var player=new FakePlayer(world,new GameProfile(UUID.randomUUID(),"ECMonitorAE"));
        player.setPos(BASE.getX()+.5,BASE.getY(),BASE.getZ()+2);
        var session=UUID.randomUUID();
        var menu=hellfirepvp.modularmachinery.common.menu.MenuECalculatorController.forController(3,player.getInventory(),controller,session);
        player.containerMenu=menu;menu.broadcastChanges();
        var data=menu.snapshot();
        require(data.tasks().size()==cpus.size() && data.used()==cpus.stream().mapToLong(c->c.bytes).sum(),"monitor actual task count/memory");
        require(data.hyper()==cpus.stream().filter(c->c.hyper).count() && data.normal()==cpus.stream().filter(c->!c.hyper).count(),"monitor explicit normal/hyper slots");
        var selected=cpus.get(0);var other=cpus.get(1);
        require(data.tasks().stream().anyMatch(t->t.id().equals(selected.monitorId)&&t.icon().is(Items.OAK_PLANKS)&&t.amount()==4),"monitor actual output icon/amount/identity");
        require(!menu.cancel(player,UUID.randomUUID(),selected.monitorId) && !menu.cancel(player,session,UUID.randomUUID()),"foreign session/task cancellation");
        player.setPos(BASE.getX()+20,BASE.getY(),BASE.getZ());
        require(!menu.cancel(player,session,selected.monitorId),"distant cancellation");
        player.setPos(BASE.getX()+.5,BASE.getY(),BASE.getZ()+2);
        require(menu.cancel(player,session,selected.monitorId) && !selected.cluster.craftingLogic.hasJob()
                && other.cluster.craftingLogic.hasJob(),"cancel selected identity affected another task");
        require(!menu.cancel(player,session,selected.monitorId),"stale/repeated cancel accepted");
        if(!storage.accept)require(menu.snapshot().tasks().stream().anyMatch(t->t.id().equals(selected.monitorId)&&!t.running()&&t.bytes()==selected.bytes),"blocked return vanished from monitor");
        player.containerMenu=player.inventoryMenu;
        require(!menu.cancel(player,session,other.monitorId),"closed menu cancellation");
        Modularmachinery.LOG.info("[MMC][ec-menu-ae] PASS: actual AE output and tasks, explicit hyper slots, long memory={}, identity/session/distance/closed-menu checks, selected cancel and retained return state",data.used());
    }
    /** The reservation estimate changes; recipes/ingredients/outputs still come from AE's genuine plan. */
    private record PlanView(ICraftingPlan original,long bytes,KeyCounter usedItems) implements ICraftingPlan {
        @Override public GenericStack finalOutput(){return original.finalOutput();}
        @Override public boolean simulation(){return original.simulation();}
        @Override public boolean multiplePaths(){return original.multiplePaths();}
        @Override public KeyCounter emittedItems(){return original.emittedItems();}
        @Override public KeyCounter missingItems(){return original.missingItems();}
        @Override public Map<appeng.api.crafting.IPatternDetails,Long> patternTimes(){return original.patternTimes();}
    }
    private static void setField(Object obj,String name,Object value)throws Exception {var field=obj.getClass().getDeclaredField(name);field.setAccessible(true);field.set(obj,value);}
    private static void require(boolean condition,String message) {if(!condition)throw new AssertionError(message);}
    private static void finish(Throwable failure) {
        if(failure==null)Modularmachinery.LOG.info("[MMC][ec-network] OVERALL: PASS");else Modularmachinery.LOG.error("[MMC][ec-network] OVERALL: FAIL phase {}",phase,failure);
        if(previous==null)MachineReloadListener.getLoader().getMachines().remove(ID);else MachineReloadListener.getLoader().getMachines().put(ID,previous);
        var stopping=server;server=null;if(stopping!=null)stopping.halt(false);
    }
    private static final class MenuHost extends net.minecraft.world.level.block.entity.BlockEntity implements ISubMenuHost,IActionHost {
        boolean returned;
        MenuHost() {super(ECalculatorRegistration.PART_TILE.get(),CHANNEL,ECalculatorRegistration.CHANNEL.get().defaultBlockState());setLevel(world);}
        @Override public void returnToMainMenu(Player player,ISubMenu menu) {returned=true;}
        @Override public ItemStack getMainMenuIcon() {return new ItemStack(ECalculatorRegistration.CHANNEL.get());}
        @Override public IGridNode getActionableNode() {return channel.getActionableNode();}
    }
    private static final class CounterStorage implements MEStorage {
        final Map<AEKey,Long> values=new LinkedHashMap<>();boolean accept=true,acceptPlanks;
        void set(AEKey key,long amount) {values.put(key,amount);if(grid!=null)grid.getStorageService().invalidateCache();}
        long count(AEKey key) {return values.getOrDefault(key,0L);}
        @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source) {if(!accept && !(acceptPlanks && key.equals(PLANK)))return 0;if(mode==Actionable.MODULATE)set(key,count(key)+amount);return amount;}
        @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source) {long taken=Math.min(count(key),amount);if(mode==Actionable.MODULATE)set(key,count(key)-taken);return taken;}
        @Override public void getAvailableStacks(KeyCounter out) {values.forEach(out::add);}
        @Override public Component getDescription() {return Component.literal("EC fixture storage");}
    }
}
