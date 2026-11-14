/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.efabricator;

import appeng.api.config.Actionable;
import appeng.api.crafting.*;
import appeng.api.networking.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.*;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEBlocks;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.*;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import java.util.*;
import java.util.concurrent.Future;

/** Disposable actual AE planner/native CPU/provider fixture. No fabricated completion or generated energy in EF. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class EFabricatorNetworkVerification {
    private static final BlockPos BASE=new BlockPos(0,120,0), CHANNEL=BASE.south(), POWER=BASE.south(2), CASING=BASE.east(4);
    private static final ResourceLocation ID=new ResourceLocation("modularmachinery",EFabricatorRegistration.machineName(EFabricatorSpecs.Tier.L4));
    private static final ResourceLocation NBT_RECIPE=new ResourceLocation("mmc_ef_test","nbt"), FLUID_RECIPE=new ResourceLocation("mmc_ef_test","fluid");
    private static final AEKey LOG=AEItemKey.of(Items.OAK_LOG), PLANK=AEItemKey.of(Items.OAK_PLANKS);
    private static final IActionSource SOURCE=IActionSource.empty();
    private static MinecraftServer server;
    private static ServerLevel world;
    private static TileEFabricatorController host;
    private static TileEFabricatorWorker a,b;
    private static TileEFabricatorPatternBus bus;
    private static EFabricatorMEChannelNode channel;
    private static IItemHandler inventory;
    private static DynamicMachine previous;
    private static List<Recipe<?>> originalRecipes,fixtureRecipes;
    private static ItemStack plankPattern;
    private static IGrid mounted;
    private static Future<ICraftingPlan> calculation;
    private static ICraftingPlan plan;
    private static CraftingCPUCluster cpu;
    private static CompoundTag queued;
    private static CompoundTag pending;
    private static List<? extends String> previousCoolants;
    private static hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank cold,hot;
    private static double chargedEnergy;
    private static boolean cooledEnergyChecked;
    private static int ticks,since,phase;
    private static final Stock stock=new Stock();
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("modularmachinery.efNetworkTest"))return;
        server=event.getServer();world=server.overworld();
        try {
            previousCoolants=List.copyOf(hellfirepvp.modularmachinery.common.data.Config.efCoolants());
            for(int x=-1;x<=1;x++)for(int z=-1;z<=1;z++)world.setChunkForced(x,z,true);
            for(int x=-2;x<=12;x++)for(int z=0;z<=3;z++)world.setBlockAndUpdate(BASE.offset(x,0,z),Blocks.AIR.defaultBlockState());
            originalRecipes=new ArrayList<>(world.getRecipeManager().getRecipes());fixtureRecipes=new ArrayList<>(originalRecipes);
            var result=new ItemStack(Items.DIAMOND,2);result.getOrCreateTag().putString("EF-Test","output-NBT");
            fixtureRecipes.add(new ShapelessRecipe(NBT_RECIPE,"",CraftingBookCategory.MISC,result,NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.STONE))));
            fixtureRecipes.add(new ShapelessRecipe(FLUID_RECIPE,"",CraftingBookCategory.MISC,new ItemStack(Items.CLAY_BALL),NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.WATER_BUCKET))));
            world.getRecipeManager().replaceRecipes(fixtureRecipes);
            var structure=new MachinePattern();
            structure.addPart(new MachinePattern.Part(1,0,0,"modularmachinery:efabricator_worker"));
            structure.addPart(new MachinePattern.Part(2,0,0,"modularmachinery:efabricator_worker"));
            structure.addPart(new MachinePattern.Part(3,0,0,"modularmachinery:efabricator_pattern_bus"));
            structure.addPart(new MachinePattern.Part(4,0,0,"modularmachinery:efabricator_casing"));
            structure.addPart(new MachinePattern.Part(5,0,0,"modularmachinery:efabricator_parallel_proc_l4"));
            structure.addPart(new MachinePattern.Part(6,0,0,"modularmachinery:blockfluidinputhatch"));
            structure.addPart(new MachinePattern.Part(7,0,0,"modularmachinery:blockfluidoutputhatch"));
            structure.addPart(new MachinePattern.Part(0,0,1,"modularmachinery:efabricator_me_channel"));
            previous=MachineReloadListener.getLoader().getMachines().put(ID,new DynamicMachine(ID.getPath(),"EF real network fixture",structure));
            world.setBlockAndUpdate(BASE,EFabricatorRegistration.CONTROLLERS.get(EFabricatorSpecs.Tier.L4).get().defaultBlockState());
            world.setBlockAndUpdate(BASE.east(),EFabricatorRegistration.WORKER.get().defaultBlockState());
            world.setBlockAndUpdate(BASE.east(2),EFabricatorRegistration.WORKER.get().defaultBlockState());
            world.setBlockAndUpdate(BASE.east(3),EFabricatorRegistration.PATTERN_BUS.get().defaultBlockState());
            world.setBlockAndUpdate(CASING,EFabricatorRegistration.CASING.get().defaultBlockState());
            world.setBlockAndUpdate(BASE.east(5),EFabricatorRegistration.PARTS.get("efabricator_parallel_proc_l4").get().defaultBlockState());
            world.setBlockAndUpdate(BASE.east(6),hellfirepvp.modularmachinery.common.registry.RegistryBlocks.fluidInputHatch.get().defaultBlockState());
            world.setBlockAndUpdate(BASE.east(7),hellfirepvp.modularmachinery.common.registry.RegistryBlocks.fluidOutputHatch.get().defaultBlockState());
            cold=(hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank)world.getBlockEntity(BASE.east(6));
            hot=(hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank)world.getBlockEntity(BASE.east(7));
            world.setBlockAndUpdate(CHANNEL,EFabricatorRegistration.CHANNEL.get().defaultBlockState());power(true);
            world.setBlockAndUpdate(POWER.east(),AEBlocks.CRAFTING_STORAGE_1K.block().defaultBlockState());
            host=(TileEFabricatorController)world.getBlockEntity(BASE);a=(TileEFabricatorWorker)world.getBlockEntity(BASE.east());b=(TileEFabricatorWorker)world.getBlockEntity(BASE.east(2));
            bus=(TileEFabricatorPatternBus)world.getBlockEntity(BASE.east(3));channel=(EFabricatorMEChannelNode)world.getBlockEntity(CHANNEL);
            inventory=bus.getCapability(ForgeCapabilities.ITEM_HANDLER,null).orElseThrow(AssertionError::new);host.refreshStructure();require(host.isOperational(),"not formed");
            plankPattern=encode("minecraft:oak_planks",slots(new ItemStack(Items.OAK_LOG)),false,false);
            put(0,plankPattern);put(1,plankPattern);
            put(71,PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(LOG,1)},new GenericStack[]{new GenericStack(AEItemKey.of(Items.NETHER_STAR),1)}));
            Modularmachinery.LOG.info("[MMC][ef-network] Waiting for a real AE grid, native CPU and EF provider");
        }catch(Throwable failure){finish(failure);}
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||server==null)return;
        try {
            // Node destruction/recreation may produce a different real grid. The fixture's
            // storage must rejoin it; otherwise "refusal" only tests an absent provider.
            if(channel.isNetworkActive())mount();
            if(++ticks>4200||ticks-since>500)throw new AssertionError("EF timeout phase "+phase+"; queue="+total()+", outputs="+host.pendingOutputs()+", active="+channel.isNetworkActive());
            switch(phase){
                case 0 -> {
                    if(!ready())return;
                    require(channel.getActionableNode().hasFlag(GridFlags.REQUIRE_CHANNEL)&&channel.getActionableNode().hasFlag(GridFlags.DENSE_CAPACITY),"wrong channel flags");
                    require(channel.validPatternCount()==2&&channel.getAvailablePatterns().size()==1&&channel.getActionableNode().getIdlePowerUsage()==66,"duplicate slots, processing filter or source idle drain");
                    EFabricatorCoolingChecks.run();boundaries();executionBoundaries();host.setModes(false,true);next(1);
                }
                case 1 -> {
                    if(!ready())return;mount();stock.set(LOG,32);stock.set(PLANK,0);
                    require(grid().getCraftingService().isCraftable(PLANK)&&!grid().getCraftingService().isCraftable(AEItemKey.of(Items.NETHER_STAR)),"EF patterns not reflected in actual crafting service");
                    cpu=(CraftingCPUCluster)grid().getCraftingService().getCpus().stream().findFirst().orElseThrow();
                    calculation=grid().getCraftingService().beginCraftingCalculation(world,()->new appeng.me.helpers.MachineSource(channel),PLANK,4,CalculationStrategy.REPORT_MISSING_ITEMS);next(2);
                }
                case 2 -> {
                    if(!calculation.isDone())return;plan=calculation.get();
                    require(!plan.simulation()&&plan.missingItems().isEmpty()&&plan.patternTimes().values().stream().mapToLong(Long::longValue).sum()==1,"real AE planner did not use one EF craft");
                    require(grid().getCraftingService().submitJob(plan,null,cpu,true,SOURCE).successful(),"native CPU did not submit EF plan");next(3);
                }
                case 3 -> {
                    if(total()==0)return;
                    require(total()==1&&stock.count(LOG)==31&&stock.count(PLANK)==0&&cpu.isBusy(),"real push duplicated/lost materials or invented completion");
                    var work=works().get(0);require(work.crafts()==1&&work.output().is(Items.OAK_PLANKS)&&work.outputAmount()==4&&work.remaining().stream().allMatch(ItemStack::isEmpty),"real queued craft mismatch");
                    checkMonitor(1,0,0,0);
                    hellfirepvp.modularmachinery.integration.top.EFabricatorProbeRegressionChecks.observe(world,a,true);
                    queued=a.workData();var disk=a.saveWithoutMetadata();a.load(disk);host.refreshStructure();
                    require(a.workData().equals(queued)&&total()==1,"real admitted work changed on reload");
                    require(a.appearance()==EFabricatorAppearance.RUN&&b.appearance()==EFabricatorAppearance.ON,"real admitted queue did not select source RUN/idle artwork");
                    power(false);next(4);
                }
                case 4 -> {
                    if(ticks-since<90||channel.isNetworkActive())return;
                    require(channel.getAvailablePatterns().isEmpty()&&a.workData().equals(queued)&&total()==1,"power loss changed saved work or advertised recipes");
                    checkPush(details(0),inputs(details(0)),false);power(true);next(5);
                }
                case 5 -> {
                    if(!ready())return;mount();require(a.workData().equals(queued)&&stock.count(LOG)==31,"power recovery replayed accepted input");
                    for(int i=2;i<=9;i++)world.setBlockAndUpdate(POWER.east(i),AEBlocks.INTERFACE.block().defaultBlockState());next(6);
                }
                case 6 -> {
                    if(ticks-since<130||grid().getPathingService().isNetworkBooting())return;
                    require(!channel.isNetworkActive()&&channel.getAvailablePatterns().isEmpty(),"channel shortage still advertises EF patterns");
                    checkPush(details(0),inputs(details(0)),false);
                    for(int i=2;i<=9;i++)world.setBlockAndUpdate(POWER.east(i),Blocks.AIR.defaultBlockState());next(7);
                }
                case 7 -> {
                    if(!ready())return;
                    world.setBlockAndUpdate(CASING,Blocks.AIR.defaultBlockState());host.refreshStructure();
                    require(a.appearance()==EFabricatorAppearance.OFF&&b.appearance()==EFabricatorAppearance.OFF,"broken real AE structure kept worker lights on");
                    require(channel.getActionableNode()==null&&channel.isBusy()&&a.workData().equals(queued),"broken structure kept node or lost work");
                    checkPush(details(0),inputs(details(0)),false);
                    world.setBlockAndUpdate(CASING,EFabricatorRegistration.CASING.get().defaultBlockState());host.refreshStructure();next(8);
                }
                case 8 -> {
                    if(!ready())return;
                    var node=channel.getActionableNode();var busData=bus.patternData();var nodeTag=channel.saveWithoutMetadata();
                    require(nodeTag.contains("MENode")&&channel.getUpdateTag().isEmpty(),"node not saved or private data sent to client");
                    channel.onChunkUnloaded();require(channel.getActionableNode()==null&&channel.isBusy(),"channel unload left usable provider");
                    checkPush(details(0),inputs(details(0)),false);
                    channel.onLoad();host.refreshStructure();channel.tick();require(channel.getActionableNode()!=node,"unloaded node reused");
                    require(bus.patternData().equals(busData)&&a.workData().equals(queued),"unload changed patterns or accepted work");next(9);
                }
                case 9 -> {
                    if(!ready())return;
                    require(total()==1&&a.workData().equals(queued)&&stock.count(LOG)==31&&stock.count(PLANK)==0,"accepted craft replayed across network lifecycle");
                    Modularmachinery.LOG.info("[MMC][ef-admission-lifecycle] PASS: real queued order retained across NBT, power/channel loss, broken structure and unload callback while active cooling has no coolant");
                    stock.limitPlanks=0;host.setModes(false,false);next(10);
                }
                case 10 -> {
                    if(total()!=0)return;
                    require(host.totalCrafted()==1&&pending(PLANK)==4&&stock.count(LOG)==31&&stock.count(PLANK)==0,"real work execution lost/duplicated products");
                    require(a.energyStored()==EFabricatorSpecs.ENERGY_CACHE-100&&b.energyStored()==EFabricatorSpecs.ENERGY_CACHE,"network-charged worker did not pay exactly 100 AE");
                    hellfirepvp.modularmachinery.integration.top.EFabricatorProbeRegressionChecks.observe(world,a,false);
                    Modularmachinery.LOG.info("[MMC][ef-top-network] PASS: TOP reports native CPU admitted work, actual AE charged cache and consumed queue after manufacture; reads and native codec preserve production data");
                    require(a.appearance()==EFabricatorAppearance.RUN,"real fabrication did not hold RUN lamp after completion");
                    chargedEnergy=a.energyStored();pending=host.productionData();next(11);
                    checkMonitor(0,1,4,0);
                }
                case 11 -> {
                    if(ticks-since<80)return;
                    require(host.productionData().equals(pending)&&a.energyStored()==chargedEnergy&&total()==0,"refused outputs changed or executed again");
                    require(a.appearance()==EFabricatorAppearance.ON,"completed worker RUN lamp stuck under output backpressure");
                    Modularmachinery.LOG.info("[MMC][ef-appearance-network] PASS: native CPU queue selects RUN, physical break forces OFF, real AE energy/production holds RUN then returns ON while ME refuses outputs; no fabricated completion");
                    stock.limitPlanks=2;next(12);
                }
                case 12 -> {
                    if(stock.count(PLANK)!=2)return;
                    require(pending(PLANK)==2&&host.totalCrafted()==1&&total()==0,"partial storage delivery lost remainder");
                    pending=host.productionData();power(false);next(13);
                }
                case 13 -> {
                    if(ticks-since<90||channel.isNetworkActive())return;
                    require(host.productionData().equals(pending)&&stock.count(PLANK)==2,"power loss lost pending product");
                    var disk=host.saveWithoutMetadata();host.load(disk);host.refreshStructure();
                    require(host.productionData().equals(pending)&&!host.getUpdateTag().contains("EfProduction"),"production reload lost data or broadcast private outputs");
                    host.onChunkUnloaded();host.onLoad();host.refreshStructure();
                    require(host.productionData().equals(pending),"host unload callback lost refused output");
                    stock.limitPlanks=Long.MAX_VALUE;power(true);next(14);
                }
                case 14 -> {
                    if(!ready())return;mount();
                    if(!host.pendingOutputs().isEmpty()||cpu.isBusy())return;
                    require(stock.count(LOG)==31&&stock.count(PLANK)==4&&host.totalCrafted()==1&&total()==0,"real order did not finish with exact conservation");
                    host.setModes(false,true);
                    require(grid().getCraftingService().submitJob(plan,null,cpu,true,SOURCE).successful(),"second actual order rejected");next(15);
                }
                case 15 -> {
                    if(total()!=1)return;
                    require(stock.count(LOG)==30&&host.totalCrafted()==1,"cancel fixture did not push actual materials");
                    cpu.cancelJob();stock.limitPlanks=4;host.setModes(false,false);next(16);
                }
                case 16 -> {
                    if(total()!=0||pending(PLANK)!=4)return;
                    require(stock.count(LOG)==30&&stock.count(PLANK)==4&&host.totalCrafted()==2,"CPU cancellation refunded consumed materials or erased work");
                    pending=host.productionData();next(17);
                }
                case 17 -> {
                    if(ticks-since<60)return;
                    require(host.productionData().equals(pending)&&stock.count(LOG)==30,"cancelled in-flight products replayed");
                    stock.limitPlanks=Long.MAX_VALUE;next(18);
                }
                case 18 -> {
                    if(!host.pendingOutputs().isEmpty()||stock.count(PLANK)!=8)return;
                    stock.set(AEItemKey.of(Items.MILK_BUCKET),3);stock.set(AEItemKey.of(Items.SUGAR),2);
                    stock.set(AEItemKey.of(Items.EGG),1);stock.set(AEItemKey.of(Items.WHEAT),3);
                    calculation=grid().getCraftingService().beginCraftingCalculation(world,()->new appeng.me.helpers.MachineSource(channel),AEItemKey.of(Items.CAKE),1,CalculationStrategy.REPORT_MISSING_ITEMS);next(19);
                }
                case 19 -> {
                    if(!calculation.isDone())return;var cake=calculation.get();
                    require(!cake.simulation()&&cake.missingItems().isEmpty()&&grid().getCraftingService().submitJob(cake,null,cpu,true,SOURCE).successful(),"actual cake order could not submit");next(20);
                }
                case 20 -> {
                    if(cpu.isBusy()||stock.count(AEItemKey.of(Items.CAKE))!=1||!host.pendingOutputs().isEmpty())return;
                    require(host.totalCrafted()==3&&total()==0&&stock.count(AEItemKey.of(Items.BUCKET))==3
                            &&stock.count(AEItemKey.of(Items.MILK_BUCKET))==0&&stock.count(AEItemKey.of(Items.SUGAR))==0
                            &&stock.count(AEItemKey.of(Items.EGG))==0&&stock.count(AEItemKey.of(Items.WHEAT))==0,"actual cake products/container conservation");
                    Modularmachinery.LOG.info("[MMC][ef-execution] PASS: real native CPU orders charged and fabricated, exact one-craft energy, full/partial storage refusal, retained output NBT and host unload callback, resume delivery, CPU cancellation preserves in-flight products, actual cake and three buckets; no synthetic CPU completion");
                    // Explicit test-only water/lava rule exercises real hatches, not a new default coolant.
                    coolantRule(true);host.setModes(true,true);
                    cold.getTank().fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    hot.getTank().setCapacity(320);hot.getTank().setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1));
                    require(grid().getCraftingService().submitJob(plan,null,cpu,true,SOURCE).successful(),"cooled actual order submission");next(21);
                }
                case 21 -> {
                    if(total()!=1)return;
                    queued=a.workData();next(22);
                }
                case 22 -> {
                    if(ticks-since<60)return;
                    require(total()==1&&a.workData().equals(queued)&&host.totalCrafted()==3&&host.coolingUnits()==0
                            &&cold.getTank().getFluidAmount()==1&&hot.getTank().getFluidAmount()==1,"wrong hot fluid consumed cold input or made free work");
                    hot.getTank().setFluid(net.minecraftforge.fluids.FluidStack.EMPTY);hot.getTank().setCapacity(319);next(23);
                }
                case 23 -> {
                    if(ticks-since<60)return;
                    require(total()==1&&a.workData().equals(queued)&&host.coolingUnits()==0&&cold.getTank().getFluidAmount()==1
                            &&hot.getTank().isEmpty(),"319 mB room accepted a 320 mB conversion");
                    checkMonitor(1,3,0,0);
                    hot.getTank().setCapacity(320);next(24);
                }
                case 24 -> {
                    if(host.totalCrafted()==4&&!cooledEnergyChecked){
                        require(a.energyStored()==499900,"cooled overclock manufacturing charge: "+a.energyStored());cooledEnergyChecked=true;
                    }
                    if(cpu.isBusy()||total()!=0||!host.pendingOutputs().isEmpty())return;
                    require(host.totalCrafted()==4&&stock.count(LOG)==29&&stock.count(PLANK)==12&&host.coolingUnits()==10
                            &&cooledEnergyChecked&&cold.getTank().isEmpty()&&hot.getTank().getFluidAmount()==320
                            &&hot.getTank().getFluid().getFluid()==net.minecraft.world.level.material.Fluids.LAVA,"cooled real order conservation: crafts="+host.totalCrafted()+", logs="+stock.count(LOG)+", planks="+stock.count(PLANK)+", coolant="+host.coolingUnits()+", energy="+a.energyStored()+", cold="+cold.getTank().getFluidAmount()+", hot="+hot.getTank().getFluidAmount());
                    var disk=host.saveWithoutMetadata();pending=host.productionData();host.load(disk);host.refreshStructure();
                    require(host.productionData().equals(pending)&&host.coolingUnits()==10&&!host.getUpdateTag().contains("EfProduction"),"cooling cache lost or leaked on NBT roundtrip");
                    coolantRule(false);cold.getTank().fill(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,1),net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
                    hot.getTank().setFluid(net.minecraftforge.fluids.FluidStack.EMPTY);next(25);
                }
                case 25 -> {
                    if(!ready())return;
                    require(grid().getCraftingService().submitJob(plan,null,cpu,true,SOURCE).successful(),"reload-blocked cooled order submission");next(26);
                }
                case 26 -> {
                    if(total()!=1)return;
                    require(host.coolingUnits()==1,"source admission acceleration did not cost nine cooling units");queued=a.workData();next(27);
                }
                case 27 -> {
                    if(ticks-since<60)return;
                    require(a.workData().equals(queued)&&host.totalCrafted()==4&&host.coolingUnits()==1
                            &&cold.getTank().getFluidAmount()==1&&hot.getTank().isEmpty(),"removed coolant config kept converting or manufactured without five units");
                    cooledEnergyChecked=false;coolantRule(true);next(28);
                }
                case 28 -> {
                    if(host.totalCrafted()==5&&!cooledEnergyChecked){
                        require(a.energyStored()==499900,"resumed cooled overclock manufacturing charge: "+a.energyStored());cooledEnergyChecked=true;
                    }
                    if(cpu.isBusy()||total()!=0||!host.pendingOutputs().isEmpty())return;
                    require(host.totalCrafted()==5&&stock.count(LOG)==28&&stock.count(PLANK)==16&&host.coolingUnits()==11
                            &&cooledEnergyChecked&&cold.getTank().isEmpty()&&hot.getTank().getFluidAmount()==320,"config recovery replayed or lost actual order");
                    checkMonitor(0,5,0,320);
                    Modularmachinery.LOG.info("[MMC][ef-menu-network] PASS: monitor reads native AE order FIFO, network-charged caches, refused products, insufficient hot-fluid capacity and completed cooling orders; wire totals match actual bound parts and viewing does not change private work/production data");
                    Modularmachinery.LOG.info("[MMC][ef-cooling-network] PASS: real AE CPU orders and actual bound fluid hatches, wrong/319-of-320 hot output pauses without consuming cold input, cooled overclock pays 100 AE + five units, source admission speedup pays nine units, private saved cache and removed/restored config resume with exact fluid/material conservation");
                    Modularmachinery.LOG.info("[MMC][ef-network] PASS: real AE planner/native CPU/provider admission, lifecycle retention, manufacturing, powered output delivery and active coolant conversion");finish(null);
                }
            }
        }catch(Throwable failure){finish(failure);}
    }
    private static void checkMonitor(int expectedQueue,long expectedCrafted,long expectedPending,long expectedHot){
        var beforeA=a.workData();var beforeB=b.workData();var beforeHost=host.productionData();
        var player=new net.minecraftforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"EfLiveMonitor"));
        player.setPos(BASE.getX()+.5,BASE.getY(),BASE.getZ()+2);
        var session=UUID.randomUUID();var menu=hellfirepvp.modularmachinery.common.menu.MenuEFabricatorController.forController(3,player.getInventory(),host,session);
        player.containerMenu=menu;
        try {
            menu.broadcastChanges();var d=menu.snapshot();
            require(d.readable()&&(d.flags()&15)==15&&d.workerCount()==2&&d.buses()==1&&d.processors()==1,"live monitor binding/network status");
            require(d.queued()==expectedQueue&&d.crafted()==expectedCrafted&&d.pending()==expectedPending&&d.output()==expectedHot,"live monitor actual order/fluid values");
            require(d.energy()==a.energyStored()+b.energyStored()&&d.coolant()==host.coolingUnits(),"live monitor cache totals");
            if(expectedQueue>0)require(d.workers().get(0).icon().is(Items.OAK_PLANKS)&&!d.workers().get(0).icon().hasTag(),"live FIFO icon");
            var buffer=new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
            try {
                d.write(buffer);var wire=EFabricatorMonitor.Snapshot.read(buffer);
                require(buffer.readableBytes()==0&&wire.queued()==expectedQueue&&wire.crafted()==expectedCrafted&&wire.energy()==d.energy()&&wire.output()==expectedHot,"live monitor wire values");
            }finally{buffer.release();}
            require(a.workData().equals(beforeA)&&b.workData().equals(beforeB)&&host.productionData().equals(beforeHost),"monitor mutated real production");
        }finally{player.containerMenu=player.inventoryMenu;}
    }
    private static void boundaries(){
        var stick=slots(new ItemStack(Items.OAK_PLANKS));stick[3]=new ItemStack(Items.OAK_PLANKS);put(2,encode("minecraft:stick",stick,false,false));
        var cake=slots(new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.MILK_BUCKET),new ItemStack(Items.SUGAR),new ItemStack(Items.EGG),new ItemStack(Items.SUGAR),new ItemStack(Items.WHEAT),new ItemStack(Items.WHEAT),new ItemStack(Items.WHEAT));put(3,encode("minecraft:cake",cake,false,false));
        var stone=new ItemStack(Items.STONE);stone.getOrCreateTag().putString("EF-Test","input-NBT");put(4,encode(NBT_RECIPE.toString(),slots(stone),false,false));
        put(5,encode(FLUID_RECIPE.toString(),slots(new ItemStack(Items.WATER_BUCKET)),true,true));
        channel.tick();
        require(channel.validPatternCount()==6&&channel.getAvailablePatterns().size()==5&&channel.getActionableNode().getIdlePowerUsage()==70,"valid pattern count/drain");
        long revision=host.patternRevision();require(inventory.insertItem(6,plankPattern.copy(),true).isEmpty()&&host.patternRevision()==revision,"simulation changed publication");
        checkPush(details(0),inputs(details(0)),true);checkPush(details(2),inputs(details(2)),true);
        checkPush(details(3),inputs(details(3)),true);
        var work=works().get(2);require(work.output().is(Items.CAKE)&&work.remaining().stream().filter(s->s.is(Items.BUCKET)).mapToInt(ItemStack::getCount).sum()==3,"cake container returns");
        checkPush(details(4),inputs(details(4)),true);require(works().get(3).output().getTag().getString("EF-Test").equals("output-NBT"),"output NBT lost");
        var fluid=details(5);var fluidInputs=inputs(fluid);require(fluidInputs[0].getFirstKey() instanceof AEFluidKey,"fixture did not use actual fluid substitution");
        checkPush(fluid,fluidInputs,true);require(works().get(4).remaining().stream().allMatch(ItemStack::isEmpty),"pure fluid created a free bucket");
        var bucket=new KeyCounter[]{new KeyCounter()};bucket[0].add(AEItemKey.of(Items.WATER_BUCKET),1);checkPush(fluid,bucket,true);
        require(works().get(5).remaining().stream().filter(s->s.is(Items.BUCKET)).mapToInt(ItemStack::getCount).sum()==1,"consumed bucket not returned");
        var insufficient=inputs(details(2));var k=insufficient[0].getFirstKey();insufficient[0].set(k,1);checkPush(details(2),insufficient,false);
        var excess=inputs(details(2));excess[0].set(excess[0].getFirstKey(),3);checkPush(details(2),excess,false);
        var negative=inputs(details(0));negative[0].set(negative[0].getFirstKey(),-1);checkPush(details(0),negative,false);
        var wrong=new KeyCounter[]{new KeyCounter()};wrong[0].add(AEItemKey.of(Items.STONE),1);checkPush(details(0),wrong,false);checkPush(details(4),wrong,false);
        var shortFluid=inputs(fluid);var fk=shortFluid[0].getFirstKey();shortFluid[0].set(fk,shortFluid[0].get(fk)-1);checkPush(fluid,shortFluid,false);
        checkPush(details(71),inputs(details(71)),false);checkPush(details(0),new KeyCounter[0],false);checkPush(details(0),null,false);
        var once=inputs(details(0));checkPush(details(0),once,true);checkPush(details(0),once,false);
        clear();var dummy=new EFabricatorQueue.Work(32,new ItemStack(Items.OAK_PLANKS,4),List.of());
        require(a.offerWork(dummy)==32&&b.offerWork(dummy.withCrafts(31))==31,"capacity fixture");checkPush(details(0),inputs(details(0)),true);
        require(total()==64&&a.queuedCrafts()==32&&b.queuedCrafts()==32&&channel.isBusy(),"work did not route to second worker");checkPush(details(0),inputs(details(0)),false);
        host.setModes(true,false);checkPush(details(0),inputs(details(0)),true);host.setModes(false,false);
        require(total()==65&&channel.isBusy(),"downclock deleted work or accepted too much");checkPush(details(0),inputs(details(0)),false);clear();
        var stale=details(0);inventory.extractItem(0,1,false);checkPush(stale,inputs(stale),false);channel.tick();
        require(channel.validPatternCount()==5&&grid().getCraftingService().isCraftable(PLANK),"duplicate removal withdrew remaining recipe");
        inventory.extractItem(1,1,false);channel.tick();require(!grid().getCraftingService().isCraftable(PLANK),"last pattern removal left ghost recipe");checkPush(stale,inputs(stale),false);
        put(0,plankPattern);put(1,plankPattern);channel.tick();
        checkPatternMenus();
        var savedBus=bus.patternData();var opaque=new CompoundTag();opaque.putInt("Version",999);opaque.putString("Unknown","keep");bus.restorePatternData(opaque);channel.tick();
        require(channel.getAvailablePatterns().isEmpty()&&bus.patternData().equals(opaque),"unreadable bus advertised or erased data");bus.restorePatternData(savedBus);channel.tick();
        var oldNbt=details(4);replaceRecipes(fixtureRecipes.stream().filter(r->!r.getId().equals(NBT_RECIPE)).toList());checkPush(oldNbt,inputs(oldNbt),false);channel.tick();
        require(channel.getAvailablePatterns().stream().noneMatch(p->p.getDefinition().equals(oldNbt.getDefinition()))&&!inventory.getStackInSlot(4).isEmpty(),"deleted recipe stayed active or erased its encoded item");
        replaceRecipes(fixtureRecipes);channel.tick();checkPush(details(4),inputs(details(4)),true);clear();
        Modularmachinery.LOG.info("[MMC][ef-admission] PASS: native recipe/grid/remainder behavior, cake buckets, actual AE fluid vs bucket returns, NBT, malformed/under/excess input untouched, no replay, full/downclock queues, duplicate slots, simulated changes, removed/unknown patterns and recipe reload invalidation; explicit boundary data is cleared before the real CPU test");
    }
    private static void replaceRecipes(List<Recipe<?>> recipes){world.getRecipeManager().replaceRecipes(recipes);MachineReloadListener.refreshRecipes();host.refreshStructure();}
    private static void checkPatternMenus(){
        var player=new net.minecraftforge.common.util.FakePlayer(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"EfPatternNative"));
        var pos=bus.getBlockPos();player.setPos(pos.getX()+.5,pos.getY(),pos.getZ()+2);
        var menu=hellfirepvp.modularmachinery.common.menu.MenuEFabricatorPatternBus.forBus(6,player.getInventory(),bus);player.containerMenu=menu;
        var before=bus.patternData();
        menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,player);channel.tick();
        require(!menu.getCarried().isEmpty()&&grid().getCraftingService().isCraftable(PLANK),"menu removed duplicate recipe too early");
        menu.clicked(72,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
        menu.clicked(1,0,net.minecraft.world.inventory.ClickType.PICKUP,player);channel.tick();
        require(!menu.getCarried().isEmpty()&&!grid().getCraftingService().isCraftable(PLANK),"menu removal left ghost AE pattern");
        menu.clicked(1,0,net.minecraft.world.inventory.ClickType.PICKUP,player);channel.tick();require(grid().getCraftingService().isCraftable(PLANK),"menu reinsertion not published");
        menu.clicked(72,0,net.minecraft.world.inventory.ClickType.PICKUP,player);menu.clicked(0,0,net.minecraft.world.inventory.ClickType.PICKUP,player);channel.tick();
        require(menu.getCarried().isEmpty()&&bus.patternData().equals(before),"menu inventory roundtrip changed patterns");
        player.setPos(host.getBlockPos().getX()+.5,host.getBlockPos().getY(),host.getBlockPos().getZ()+2);
        var session=UUID.randomUUID();var search=hellfirepvp.modularmachinery.common.menu.MenuEFabricatorPatternSearch.forController(7,player.getInventory(),host,session);player.containerMenu=search;search.broadcastChanges();
        require(search.snapshot().entries().stream().anyMatch(e->e.slot()==0&&e.description().valid()&&e.description().icon().is(Items.OAK_PLANKS)),"native published pattern not in search");
        require(bus.patternData().equals(before)&&grid().getCraftingService().isCraftable(PLANK),"viewing pattern search changed publication");player.containerMenu=player.inventoryMenu;
        Modularmachinery.LOG.info("[MMC][ef-pattern-menu-network] PASS: actual bus menu pickup removes last/duplicate AE publication correctly, insertion republishes, exact inventory conservation and read-only search reflects real native patterns before CPU orders");
    }
    private static long pending(AEKey key){return host.pendingOutputs().stream().filter(e->key.equals(AEItemKey.of(e.item()))).mapToLong(EFabricatorOutputBuffer.Entry::amount).sum();}
    private static void energy(TileEFabricatorWorker worker,double amount){var tag=worker.workData();tag.putDouble("Energy",amount);worker.restoreWorkData(tag);}
    private static void coolantRule(boolean enabled){hellfirepvp.modularmachinery.common.data.Config.efCoolantsSetting().set(enabled?List.of("minecraft:water|1|minecraft:lava|320|15"):List.of());}
    private static void cache(int units){var tag=host.productionData();var cooling=new CompoundTag();cooling.putInt("Version",1);cooling.putInt("Cache",units);tag.put("Cooling",cooling);host.restoreProductionData(tag);}
    private static void executionBoundaries(){
        // Explicit saved-state data for arithmetic boundaries; cleared before any actual CPU order.
        clear();host.restoreProductionData(new CompoundTag());host.setModes(false,false);
        var work=new EFabricatorQueue.Work(3,new ItemStack(Items.APPLE),List.of(new ItemStack(Items.BUCKET)));
        require(a.offerWork(work)==3,"execution boundary queue");energy(a,250.5);
        require(a.doWork(10)==2&&a.queuedCrafts()==1&&a.energyStored()==50.5&&pending(AEItemKey.of(Items.APPLE))==2&&pending(AEItemKey.of(Items.BUCKET))==2,"exact energy-limited batch/remainders");
        var disk=a.saveWithoutMetadata();a.load(disk);host.refreshStructure();require(a.energyStored()==50.5&&a.queuedCrafts()==1,"fractional energy or remaining queue lost");
        clear();host.restoreProductionData(new CompoundTag());host.setModes(true,false);energy(a,400.5);
        require(a.offerWork(work.withCrafts(1))==1&&a.doWork(10)==1&&a.energyStored()==.5,"uncooled F4 overclock must cost 400 AE/craft");
        clear();host.restoreProductionData(new CompoundTag());host.setModes(true,true);energy(a,1000.25);a.offerWork(work);
        var unchanged=a.workData();require(a.doWork(10)==0&&a.workData().equals(unchanged)&&host.pendingOutputs().isEmpty(),"active cooling executed without coolant");
        cache(17);require(a.doWork(10)==3&&a.queuedCrafts()==0&&a.energyStored()==700.25&&host.coolingUnits()==2,"cooled execution actual cost/limit");
        b.offerWork(work.withCrafts(1));energy(b,1000);unchanged=b.workData();
        require(b.doWork(10)==0&&b.workData().equals(unchanged),"second worker reused spent cooling units");
        clear();host.restoreProductionData(new CompoundTag());cache(100);
        require(host.offerSingleWork(work.withCrafts(1))&&host.coolingUnits()==62&&host.ticksUntilWork()==1,"source first admission acceleration");
        require(host.offerSingleWork(work.withCrafts(1))&&host.coolingUnits()==62&&host.ticksUntilWork()==1,"admission acceleration charged more than once per cycle");
        clear();host.restoreProductionData(new CompoundTag());host.setModes(false,false);energy(a,500000);energy(b,500000);
        a.offerWork(work.withCrafts(5));b.offerWork(work.withCrafts(32));
        for(int i=0;i<20;i++)host.tick();
        require(host.parallelism()==24&&host.totalCrafted()==24&&a.queuedCrafts()==0&&b.queuedCrafts()==13
                &&a.energyStored()==499500&&b.energyStored()==498100,"shared parallel allowance or exact completed-craft accounting");
        clear();host.restoreProductionData(new CompoundTag());
        Modularmachinery.LOG.info("[MMC][ef-execution-boundaries] PASS: explicit saved-energy fixtures verify fractional retention, partial FIFO batch, exact product/container amounts, 100/400 AE cost, shared parallelism by completed crafts and no free active cooling; all fixture work/products cleared before actual orders");
    }
    private static void clear(){a.restoreWorkData(new CompoundTag());b.restoreWorkData(new CompoundTag());}
    private static List<EFabricatorQueue.Work> works(){
        var result=new ArrayList<EFabricatorQueue.Work>();
        for(var worker:List.of(a,b)){var q=EFabricatorQueue.load(worker.workData().getCompound("Queue"));while(!q.isEmpty())result.add(q.poll(512));}return result;
    }
    private static void checkPush(IPatternDetails pattern,KeyCounter[] input,boolean expected){
        var beforeA=a.workData();var beforeB=b.workData();int count=total();
        var snapshots=input==null?null:Arrays.stream(input).map(c->{if(c==null)return null;var out=new KeyCounter();out.addAll(c);return out;}).toArray(KeyCounter[]::new);
        require(channel.pushPattern(pattern,input)==expected,"push result expected "+expected+", pattern "+pattern.getDefinition().getItem());
        if(expected){require(total()==count+1&&Arrays.stream(input).allMatch(KeyCounter::isEmpty),"accepted input not consumed exactly once");}
        else {require(beforeA.equals(a.workData())&&beforeB.equals(b.workData()),"rejected input changed queue");if(input!=null)for(int i=0;i<input.length;i++){
            require((input[i]==null)==(snapshots[i]==null),"rejected null holder changed");if(input[i]!=null){require(input[i].keySet().equals(snapshots[i].keySet()),"rejected input keys changed");for(var entry:snapshots[i])require(input[i].get(entry.getKey())==entry.getLongValue(),"rejected input count changed");}
        }}
    }
    private static int total(){return a==null||b==null?-1:a.queuedCrafts()+b.queuedCrafts();}
    private static void put(int slot,ItemStack pattern){require(inventory.insertItem(slot,pattern.copy(),false).isEmpty(),"could not insert pattern slot "+slot);}
    private static IPatternDetails details(int slot){return Objects.requireNonNull(PatternDetailsHelper.decodePattern(inventory.getStackInSlot(slot),world,false));}
    private static KeyCounter[] inputs(IPatternDetails p){return Arrays.stream(p.getInputs()).map(in->{var out=new KeyCounter();var key=in.getPossibleInputs()[0];out.add(key.what(),key.amount()*in.getMultiplier());return out;}).toArray(KeyCounter[]::new);}
    private static ItemStack[] slots(ItemStack... items){var out=new ItemStack[9];Arrays.fill(out,ItemStack.EMPTY);System.arraycopy(items,0,out,0,items.length);return out;}
    private static ItemStack encode(String id,ItemStack[] inputs,boolean substitutes,boolean fluids){var recipe=(CraftingRecipe)world.getRecipeManager().byKey(new ResourceLocation(id)).orElseThrow();var grid=new net.minecraft.world.inventory.TransientCraftingContainer(new appeng.menu.AutoCraftingMenu(),3,3);for(int i=0;i<9;i++)grid.setItem(i,inputs[i].copy());return PatternDetailsHelper.encodeCraftingPattern(recipe,inputs,recipe.assemble(grid,world.registryAccess()),substitutes,fluids);}
    private static void power(boolean on){world.setBlockAndUpdate(POWER,on?AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState():Blocks.AIR.defaultBlockState());}
    private static IGrid grid(){return channel.getActionableNode().getGrid();}
    private static boolean ready(){return ticks-since>=100&&channel.isNetworkActive()&&!grid().getPathingService().isNetworkBooting();}
    private static void next(int value){phase=value;since=ticks;Modularmachinery.LOG.info("[MMC][ef-network] phase {} tick {}",phase,ticks);}
    private static void mount(){if(mounted!=grid()){mounted=grid();mounted.getStorageService().addGlobalStorageProvider(mounts->mounts.mount(stock));}}
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    private static void finish(Throwable failure){
        if(failure==null)Modularmachinery.LOG.info("[MMC][ef-network] OVERALL: PASS");else Modularmachinery.LOG.error("[MMC][ef-network] OVERALL: FAIL phase {}",phase,failure);
        if(world!=null&&originalRecipes!=null)world.getRecipeManager().replaceRecipes(originalRecipes);
        if(previousCoolants!=null)hellfirepvp.modularmachinery.common.data.Config.efCoolantsSetting().set(previousCoolants);
        if(previous==null)MachineReloadListener.getLoader().getMachines().remove(ID);else MachineReloadListener.getLoader().getMachines().put(ID,previous);
        var stop=server;server=null;if(stop!=null)stop.halt(false);
    }
    private static final class Stock implements MEStorage {
        private final Map<AEKey,Long> values=new HashMap<>();
        private long limitPlanks=Long.MAX_VALUE;
        long count(AEKey key){return values.getOrDefault(key,0L);}
        void set(AEKey key,long value){values.put(key,value);if(mounted!=null)mounted.getStorageService().invalidateCache();}
        @Override public long insert(AEKey key,long amount,Actionable mode,IActionSource source){long accepted=key.equals(PLANK)?Math.min(amount,Math.max(0,limitPlanks-count(key))):amount;if(mode==Actionable.MODULATE)set(key,count(key)+accepted);return accepted;}
        @Override public long extract(AEKey key,long amount,Actionable mode,IActionSource source){long taken=Math.min(amount,count(key));if(mode==Actionable.MODULATE)set(key,count(key)-taken);return taken;}
        @Override public void getAvailableStacks(KeyCounter out){values.forEach(out::add);}
        @Override public Component getDescription(){return Component.literal("EF fixture storage");}
    }
}
