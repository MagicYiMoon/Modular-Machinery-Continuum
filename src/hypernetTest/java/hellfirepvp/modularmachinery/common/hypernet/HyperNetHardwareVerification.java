/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.hypernet;

import com.mojang.authlib.GameProfile;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.*;
import hellfirepvp.modularmachinery.common.block.prop.EnergyHatchData;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import hellfirepvp.modularmachinery.common.upgrade.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.math.*;
import java.nio.file.*;
import java.util.*;

/** Opt-in real server fixture: no supply lambda, NBT capacity, or synthetic recipe ticks. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class HyperNetHardwareVerification {
    private static final BlockPos C=new BlockPos(2028,120,2028),P=C.east(80),P2=P.east(80),A=P2.east(80),D=A.east(6),H=P2.south(80);
    private static final UUID ACTOR=UUID.fromString("ad7762e3-0c61-4ab5-8367-b512cba87c93");
    private static MinecraftServer server;private static ServerLevel world;private static FakePlayer player;
    private static TileController c,p,p2,a,d,h;private static int tick,phase,since;private static boolean finished,failed;
    private static ItemStack card;private static HyperNetNode oldP,oldC;
    private static HyperNetNode cn(){return c.getHyperNetNode();}private static HyperNetNode pn(){return p.getHyperNetNode();}
    private static HyperNetNode an(){return a.getHyperNetNode();}private static HyperNetNode p2n(){return p2.getHyperNetNode();}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static void next(int value){phase=value;since=tick;Modularmachinery.LOG.info("[MMC][hypernet-hardware] phase {} tick {}",phase,tick);}
    private static void close(double actual,double expected,String message){require(Math.abs(actual-expected)<1e-8,message+" actual="+actual);}
    @SubscribeEvent public static void start(ServerStartedEvent event){
        if(!Boolean.getBoolean("modularmachinery.hypernetHardwareTest")||Boolean.getBoolean("modularmachinery.hypernetStationResumeTest"))return;
        server=event.getServer();world=server.overworld();player=FakePlayerFactory.get(world,new GameProfile(ACTOR,"HyperNetTest"));
        try{
            curveChecks();parserChecks();
            if(net.minecraftforge.fml.ModList.get().isLoaded("crafttweaker")){
                var example=MachineReloadListener.getLoader().findByName("hn_sample_processor");
                require(example!=null&&example.getHyperNetDefinition().modules().size()==3,"example JSON not loaded");
                require(RegistryMachineUpgrade.getPrototype("hn_example_cpu")!=null
                        &&example.getCoreThreadPresets().containsKey(HyperNetDefinition.PROCESSOR_THREAD),"example ZenCode registration failed");
            }
            register(Items.DIAMOND,"hn_cpu",2,false);register(Items.EMERALD,"hn_gpu",1,false);
            register(Items.REDSTONE,"hn_ram",2,false);register(Items.LAPIS_LAZULI,"hn_dynamic",1,true);
            var denied=new SimpleMachineUpgrade(UpgradeType.of("hn_denied","denied",64));denied.getType().addCompatibleMachine("a_different_machine");
            RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(Items.IRON_INGOT),denied);
            for(var pos:List.of(C,P,P2,A,H))world.setChunkForced(pos.getX()>>4,pos.getZ()>>4,true);
            definition("hw_center","{\"role\":\"center\",\"carrying\":100,\"max-connections\":8}",HyperNetDefinition.CENTER_THREAD,false);
            definition("hw_processor",processorProfile(false),HyperNetDefinition.PROCESSOR_THREAD,true);
            definition("hw_expensive",processorProfile(true),HyperNetDefinition.PROCESSOR_THREAD,true);
            definition("hw_node","{\"role\":\"node\"}",null,false);
            MachineReloadListener.refreshRecipes();
            c=build(C,"hw_center",false);d=build(D,"hw_center",false);
            p=build(P,"hw_processor",true);p2=build(P2,"hw_processor",true);h=build(H,"hw_expensive",true);a=build(A,"hw_node",false);
        }catch(Throwable error){finish(error);}
    }
    private static void register(Item item,String id,int limit,boolean dynamic){
        var type=UpgradeType.of(id,id,limit);
        RegistryMachineUpgrade.addFixedUpgrade(new ItemStack(item),dynamic?new SimpleDynamicMachineUpgrade(type):new SimpleMachineUpgrade(type));
    }
    private static String processorProfile(boolean expensive){
        return "{\"role\":\"processor\",\"modules\":["
                +"{\"upgrade\":\"hn_cpu\",\"kind\":\"cpu\",\"capacity\":2,\"energy\":"+(expensive?200:20)+"},"
                +"{\"upgrade\":\"hn_gpu\",\"kind\":\"gpu\",\"capacity\":3,\"energy\":30},"
                +"{\"upgrade\":\"hn_ram\",\"kind\":\"ram\",\"capacity\":4,\"energy\":"+(expensive?120:12)+"},"
                +"{\"upgrade\":\"hn_dynamic\",\"kind\":\"gpu\",\"capacity\":1,\"energy\":2},"
                +"{\"upgrade\":\"hn_denied\",\"kind\":\"cpu\",\"capacity\":1000000,\"energy\":0}]}";
    }
    private static void definition(String name,String profile,String thread,boolean modules)throws Exception{
        String json="{\"registryname\":\""+name+"\",\"requires-blueprint\":false,\"has-factory\":"+(thread!=null)
                +",\"parts\":[{\"x\":0,\"y\":-1,\"z\":0,\"elements\":\"modularmachinery:blockenergyinputhatch\"},"
                +"{\"x\":0,\"y\":1,\"z\":0,\"elements\":\"minecraft:"+(modules?"iron_block":name.equals("hw_center")?"stone":"gold_block")+"\"}"
                +(modules?",{\"x\":0,\"y\":2,\"z\":0,\"elements\":\"modularmachinery:blockupgradebus\"},{\"x\":0,\"y\":3,\"z\":0,\"elements\":\"modularmachinery:blockupgradebus\"}":"")
                +"],\"hypernet\":"+profile+"}";
        var file=Files.createTempDirectory("mmc-hardware-").resolve(name+".json");Files.writeString(file,json);
        var machine=MachineReloadListener.serverDefinitions().machines().parseMachineFile(file.toFile()).orElseThrow();
        Files.delete(file);Files.delete(file.getParent());
        if(thread!=null){
            machine.addCoreThreadPreset(thread,List.of(name+"_work"));
            var recipe=new MachineRecipe(name,name+"_work",Boolean.getBoolean("modularmachinery.hypernetFairTest")?2000:200,List.of(new RequirementEnergy(IOType.INPUT,1)),null,false,1,thread);
            ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(recipe,Map.of()));
        }
        var copy=MachineLoader.fromSnapshot(MachineReloadListener.serverDefinitions().machines().snapshot()).findByName(name);
        require(copy.getHyperNetDefinition().equals(machine.getHyperNetDefinition()),"module/card snapshot roundtrip");
    }
    private static TileController build(BlockPos pos,String name,boolean modules){
        world.setBlockAndUpdate(pos.below(),RegistryBlocks.energyInputHatch.get().defaultBlockState().setValue(BlockEnergyInputHatch.SIZE,EnergyHatchData.TINY));
        world.setBlockAndUpdate(pos.above(),(modules?Blocks.IRON_BLOCK:name.equals("hw_center")?Blocks.STONE:Blocks.GOLD_BLOCK).defaultBlockState());
        if(modules)for(int i=2;i<=3;i++)world.setBlockAndUpdate(pos.above(i),RegistryBlocks.upgradeBus.get().defaultBlockState()
                .setValue(BlockUpgradeBus.TYPE,hellfirepvp.modularmachinery.common.block.prop.UpgradeBusData.REINFORCED));
        world.setBlockAndUpdate(pos,(name.equals("hw_node")?RegistryBlocks.blockController.get():RegistryBlocks.blockFactoryController.get()).defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var controller=(TileController)world.getBlockEntity(pos);controller.setOwner(ACTOR);controller.setMachineName(name);
        var result=MachineFormation.tryForm(world,pos,Direction.NORTH,MachineReloadListener.getLoader().findByName(name),MachineReloadListener.getLoader());
        require(result.formed(),"formation: "+result.report());controller.setFormed(true);controller.acknowledgeStructureCheck();energy(pos,1000);return controller;
    }
    private static TileUpgradeBus bus(BlockPos pos,int index){return (TileUpgradeBus)world.getBlockEntity(pos.above(2+index));}
    private static void slot(BlockPos pos,int index,int slot,ItemStack stack){
        // Inventory writes, like a player/hopper. setUpgradeStack is only an existing dynamic item's internal update.
        ((net.minecraftforge.items.IItemHandlerModifiable)bus(pos,index).getUpgradeInventory()).setStackInSlot(slot,stack);
    }
    private static TileEnergyHatch power(BlockPos pos){return (TileEnergyHatch)world.getBlockEntity(pos.below());}
    private static void energy(BlockPos pos,int target){
        var hatch=power(pos);
        while(hatch.getStoredEnergy()>target)require(hatch.consumeInternal(hatch.getStoredEnergy()-target,false)>0,"drain");
        while(hatch.getStoredEnergy()<target)require(hatch.produceInternal(target-hatch.getStoredEnergy(),false)>0,"fill");
    }
    private static void install(BlockPos pos){
        slot(pos,0,0,new ItemStack(Items.DIAMOND,64));slot(pos,0,1,new ItemStack(Items.REDSTONE,2));
        slot(pos,0,2,new ItemStack(Items.EMERALD));slot(pos,1,0,new ItemStack(Items.DIAMOND,64));
        slot(pos,1,3,new ItemStack(Items.IRON_INGOT,64));
    }
    private static PlayerInteractEvent.RightClickBlock click(FakePlayer actor,BlockPos pos,ItemStack held){
        actor.setItemInHand(InteractionHand.MAIN_HAND,held);actor.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+1.5);
        var event=new PlayerInteractEvent.RightClickBlock(actor,InteractionHand.MAIN_HAND,pos,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
        MinecraftForge.EVENT_BUS.post(event);return event;
    }
    private static void cardChecks(){
        var plain=click(player,C,new ItemStack(Items.APPLE));require(!plain.isCanceled(),"non-card interaction swallowed");
        card=new ItemStack(hellfirepvp.modularmachinery.common.registry.RegistryItems.HYPERNET_CONNECT_CARD.get());require(click(player,C,card).isCanceled()&&card.hasTag(),"center card gesture");
        require(card.getMaxStackSize()==1&&HyperNetCardInteractions.isBoundCard(card),"dedicated single-stack card binding");
        var saved=ItemStack.of(card.save(new CompoundTag()));require(ItemStack.isSameItemSameTags(saved,card),"card inventory persistence");
        var written=card.copy();var stranger=FakePlayerFactory.get(world,new GameProfile(UUID.randomUUID(),"OtherOwner"));
        var denied=new ItemStack(hellfirepvp.modularmachinery.common.registry.RegistryItems.HYPERNET_CONNECT_CARD.get());click(stranger,C,denied);require(!denied.hasTag(),"foreign player rewrote center card");
        click(player,A,new ItemStack(hellfirepvp.modularmachinery.common.registry.RegistryItems.HYPERNET_CONNECT_CARD.get()));require(!a.getHyperNetData().contains("binding"),"blank card connected");
        for(String corrupt:List.of("dimension","machine","networkOwner","item","pos","version")){
            var bad=written.copy();var tag=bad.getTag().getCompound("mmcHyperNetCard");
            switch(corrupt){
                case "dimension"->tag.putString(corrupt,"minecraft:the_nether");
                case "networkOwner"->tag.putUUID(corrupt,UUID.randomUUID());
                case "pos"->tag.putString(corrupt,"bad-type");
                case "version"->tag.putInt(corrupt,99);
                default->tag.putString(corrupt,"minecraft:air");
            }
            require(click(player,A,bad).isCanceled()&&!a.getHyperNetData().contains("binding"),"invalid card accepted: "+corrupt);
        }
        var unloaded=new BlockPos(900000,100,900000);var bad=written.copy();bad.getTag().getCompound("mmcHyperNetCard").putLong("pos",unloaded.asLong());
        click(player,A,bad);require(!a.getHyperNetData().contains("binding")&&world.getChunkSource().getChunkNow(unloaded.getX()>>4,unloaded.getZ()>>4)==null,"card forced target chunk");
        click(stranger,A,written.copy());require(!a.getHyperNetData().contains("binding"),"foreign node ownership bypass");
        require(click(player,P,written.copy()).isCanceled()&&p.getHyperNetData().contains("binding"),"processor card gesture");
        require(click(player,A,written.copy()).isCanceled()&&a.getHyperNetData().contains("binding"),"consumer card gesture");card=written;
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END||server==null||finished)return;
        try{
            tick++;require(tick<1500&&tick-since<400,"timeout phase "+phase);
            if(tick%100==0)Modularmachinery.LOG.info("[MMC][hypernet-hardware] diagnostic phase={} formed={} working={} available={} upgrades={}",
                    phase,p.isFormed(),pn().working(),an().available(),ControllerUpgradeQueries.installed(p,true));
            if(tick%100==0)for(var pos:List.of(C,D,P,P2,H))energy(pos,1000);
            switch(phase){
                case 0->{
                    if(tick<30||!cn().working()||!d.getHyperNetNode().working())return;
                    require(!pn().working(),"processor core ran without CPU/RAM");cardChecks();require(an().available()==0,"empty hardware supplied");
                    install(P);next(1);
                }
                case 1->{
                    if(!pn().working()||an().available()!=7)return;
                    int before=power(P).getStoredEnergy();
                    var savedStatus=p.getHyperNetData();var priorStats=cn().status();
                    for(int n=0;n<20;n++){cn().status();pn().status();an().status();}
                    require(p.getHyperNetData().equals(savedStatus)&&power(P).getStoredEnergy()==before&&cn().status().equals(priorStats),"statistics query changed state");
                    require(an().request(3,true).granted()==3&&power(P).getStoredEnergy()==before&&an().available()==7,"simulation spent energy/credit");
                    require(an().request(8,false).granted()==0&&power(P).getStoredEnergy()==before,"shortfall charged");
                    require(an().request(3,false).granted()==3&&power(P).getStoredEnergy()==before-39,"real CPU and RAM cost");
                    require(an().request(.01,false).granted()==.01&&power(P).getStoredEnergy()==before-41,"fraction cost rounded down");
                    require(an().request(.01,false).granted()==.01&&power(P).getStoredEnergy()==before-41,"fraction cost charged twice");
                    require(an().request(3.98,false).granted()==3.98&&power(P).getStoredEnergy()==before-91&&an().available()==0,"full load conservation");
                    require(pn().connect(D,ACTOR)==HyperNetNode.ConnectResult.SUCCESS&&d.getHyperNetNode().available()==0,"network switch refilled hardware");
                    require(pn().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS&&an().available()==0,"return refilled hardware");next(2);
                }
                case 2->{
                    if(an().available()!=7)return;
                    require(cn().status().consumed()==7&&pn().status().consumed()==7&&an().status().consumed()==7,"committed computation not recorded on center/provider/consumer");
                    double average=pn().averageLoad();require(average>0&&average<=7,"working processor did not sample completed paid work");
                    for(int n=0;n<500;n++)require(pn().averageLoad()==average,"inspection advanced source load mean");
                    int monitorPower=power(P).getStoredEnergy();HyperNetMonitorVerification.run(c,p,a,player);
                    require(power(P).getStoredEnergy()==monitorPower,"monitor charged FE");
                    if(net.minecraftforge.fml.ModList.get().isLoaded("theoneprobe")) {
                        int storedPower=power(P).getStoredEnergy();
                        HyperNetProbeVerification.run(c,player,7);HyperNetProbeVerification.run(p,player,7);HyperNetProbeVerification.run(a,player,7);
                        require(power(P).getStoredEnergy()==storedPower,"TOP charged module FE");
                        Modularmachinery.LOG.info("[MMC][hypernet-top] PASS: center/provider/consumer committed values, read-only state and FE, registered TOP provider, translated arguments native wire roundtrip and private binding exclusion");
                    }
                    slot(P,0,1,ItemStack.EMPTY);require(an().available()==0,"removed RAM supplied immediately");
                    slot(P,0,1,new ItemStack(Items.REDSTONE,2));require(an().available()==0,"same-tick reinsertion refilled");next(3);
                }
                case 3->{
                    if(an().available()!=7)return;
                    slot(P,0,2,ItemStack.EMPTY);slot(P,1,1,new ItemStack(Items.LAPIS_LAZULI));slot(P,1,2,new ItemStack(Items.LAPIS_LAZULI));
                    require(an().available()==0,"changed modules retained offer");next(4);
                }
                case 4->{
                    if(an().available()!=6)return;
                    var dynamic=(DynamicMachineUpgrade)bus(P,1).getInstalledUpgrades().stream().filter(upgrade->upgrade instanceof DynamicMachineUpgrade).findFirst().orElseThrow();
                    dynamic.invalidate();require(an().available()==0,"invalid dynamic module retained offer");next(5);
                }
                case 5->{
                    if(an().available()!=5)return;
                    energy(P,0);int before=power(P).getStoredEnergy();
                    require(an().request(1,false).granted()==0&&power(P).getStoredEnergy()==before,"power failure charged or supplied");
                    energy(P,1000);require(an().available()==0,"same-tick recharge refilled withdrawn offer");next(6);
                }
                case 6->{
                    if(!pn().working()||an().available()!=5)return;
                    install(P2);require(p2n().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"second processor connect");next(7);
                }
                case 7->{
                    if(!p2n().working()||an().available()!=12)return;
                    int first=power(P).getStoredEnergy(),second=power(P2).getStoredEnergy();
                    require(an().request(12,true).granted()==12&&power(P).getStoredEnergy()==first&&power(P2).getStoredEnergy()==second,"multi-provider simulation charged");
                    require(an().request(13,false).granted()==0&&power(P).getStoredEnergy()==first&&power(P2).getStoredEnergy()==second,"multi-provider partial debit");
                    require(an().request(1e-20,false).granted()==1e-20,"tiny load rejected");
                    require(an().request(11,false).granted()==11&&power(P).getStoredEnergy()==first-57&&power(P2).getStoredEnergy()==second-80,
                            "exact multi-provider fee rounded away tiny prior load");
                    pn().disconnect();p2n().disconnect();
                    slot(H,0,0,new ItemStack(Items.DIAMOND));slot(H,0,1,new ItemStack(Items.REDSTONE));
                    require(h.getHyperNetNode().connect(C,ACTOR)==HyperNetNode.ConnectResult.SUCCESS,"high-cost connect");next(8);
                }
                case 8->{
                    if(!h.getHyperNetNode().working()||an().available()<=0)return;
                    int before=power(H).getStoredEnergy();require(an().available()<1,"native transfer ignored");
                    require(an().request(1,false).granted()==0&&power(H).getStoredEnergy()==before,"above-transfer debit");
                    require(an().request(.5,false).granted()==.5&&power(H).getStoredEnergy()==before-65,"partial load exact FE");
                    require(an().request(.5,false).granted()==0&&power(H).getStoredEnergy()==before-65,"multiple requests bypassed transfer limit");
                    double rest=an().available();require(rest>0&&an().request(rest,false).granted()==rest&&before-power(H).getStoredEnergy()<=100,"bounded transfer remainder");
                    h.getHyperNetNode().disconnect();slot(H,0,0,ItemStack.EMPTY);next(9);
                }
                case 9->{
                    if(tick-since<3)return;
                    require(!h.getHyperNetNode().working()&&((TileFactoryController)h).getFactoryRecipeThreadList().stream()
                            .filter(thread->thread.isPresetThread()).noneMatch(thread->thread.isWorking()),"RAM-only core continued work");
                    require(h.getHyperNetNode().averageLoad()==0,"stopped processor retained displayed load");
                    // A stopped (unpowered) forwarding thread can still write a card on its formed center.
                    energy(D,0);if(d.getHyperNetNode().working())return;
                    var stoppedCard=new ItemStack(hellfirepvp.modularmachinery.common.registry.RegistryItems.HYPERNET_CONNECT_CARD.get());click(player,D,stoppedCard);require(stoppedCard.hasTag(),"stopped formed center cannot write card");
                    oldP=pn();oldC=cn();MachineReloadListener.refreshRecipes();require(!oldP.working()&&!oldC.working()&&oldP.isClosed()&&oldC.isClosed(),"reload retained hardware runtime");
                    HyperNetMonitorVerification.afterReload(c,player);
                    next(10);
                }
                case 10->{
                    if(tick-since<4||!cn().working()||!pn().working())return;
                    click(player,P,card.copy());click(player,A,card.copy());require(an().available()==5,"fresh runtime did not rebuild physical modules");
                    var saved=p.saveWithoutMetadata();require(saved.contains("hypernetData")&&!p.getUpdateTag().contains("hypernetData"),"private hardware binding leak");
                    slot(P,0,1,ItemStack.EMPTY);require(an().available()==0,"missing RAM retained supply after reload");next(11);
                }
                case 11->{
                    if(tick-since<3)return;
                    require(!pn().working()&&((TileFactoryController)p).getFactoryRecipeThreadList().stream()
                            .filter(thread->thread.isPresetThread()).noneMatch(thread->thread.isWorking()),"CPU-only core continued work");
                    slot(P,0,1,new ItemStack(Items.REDSTONE,2));next(12);
                }
                case 12->{
                    if(!pn().working()||an().available()!=5)return;
                    oldP=pn();oldC=cn();
                    if(Boolean.getBoolean("modularmachinery.hypernetMaintenanceTest")) {
                        finished=true;HyperNetMaintenanceVerification.begin(server,world,c,p,a);
                    } else if(Boolean.getBoolean("modularmachinery.hypernetThermalTest")) {
                        finished=true;HyperNetThermalVerification.begin(server,world,c,p,a);
                    } else if(Boolean.getBoolean("modularmachinery.hypernetDatabaseTest")) {
                        finished=true;HyperNetDatabaseVerification.begin(server,world);
                    } else if(Boolean.getBoolean("modularmachinery.hypernetStationTest")) {
                        finished=true;HyperNetStationVerification.begin(server,world);
                    } else if(Boolean.getBoolean("modularmachinery.hypernetResearchTest")) {
                        finished=true;HyperNetResearchVerification.begin(server,world);
                    } else if(Boolean.getBoolean("modularmachinery.hypernetCoreCycleTest")) {
                        finished=true;HyperNetCoreCycleVerification.begin(server,world);
                    } else if(Boolean.getBoolean("modularmachinery.hypernetFairTest")) {
                        finished=true;HyperNetFairVerification.begin(server,world,c,p);
                    } else if(Boolean.getBoolean("modularmachinery.hypernetRecipeTest")) {
                        finished=true;HyperNetRecipeVerification.begin(server,world,c,p);
                    } else finish(null);
                }
            }
        }catch(Throwable error){finish(error);}
    }
    private static void curveChecks(){
        var stats=new HyperNetStatistics();stats.advance(100);stats.offer(100,10);
        stats.commit(100,new BigDecimal(".1"));stats.commit(100,new BigDecimal(".2"));
        require(stats.completed(100).consumed()==0&&stats.completed(101).consumed()==.3&&stats.completed(101).offered()==10,"statistics precise buckets");
        stats.advance(101);require(stats.completed(101).consumed()==.3,"statistics tick advance");
        stats.advance(102);require(stats.completed(102).consumed()==0&&stats.completed(105).offered()==0,"idle/offline stale statistics");
        var cpu=new HyperNetModule("cpu",HyperNetModule.Kind.CPU,2,20);var ram=new HyperNetModule("ram",HyperNetModule.Kind.RAM,4,12);
        var curve=new HyperNetLoad(List.of(new HyperNetLoad.Installed(cpu,2),new HyperNetLoad.Installed(ram,2)));
        require(curve.capacity()==4&&curve.cost(new BigDecimal("4")).equals(BigInteger.valueOf(52)),"load curve capacity/cost");
        BigDecimal used=BigDecimal.ZERO;BigInteger paid=BigInteger.ZERO;
        for(int i=0;i<4000;i++){paid=paid.add(curve.increment(used,.001));used=used.add(new BigDecimal(".001"));}
        require(paid.equals(BigInteger.valueOf(52)),"split requests changed total fee");
        require(new HyperNetLoad(List.of(new HyperNetLoad.Installed(cpu,2))).capacity()==0,"missing RAM capacity");
        require(new HyperNetLoad(List.of(new HyperNetLoad.Installed(ram,2))).capacity()==0,"missing CPU capacity");
        var huge=new HyperNetLoad(List.of(new HyperNetLoad.Installed(new HyperNetModule("c",HyperNetModule.Kind.CPU,1e308,Long.MAX_VALUE),Integer.MAX_VALUE),
                new HyperNetLoad.Installed(new HyperNetModule("r",HyperNetModule.Kind.RAM,1e308,Long.MAX_VALUE),Integer.MAX_VALUE)));
        require(huge.capacity()==0xFFFFFF&&huge.cost(BigDecimal.ONE).equals(BigInteger.TWO),"extreme arithmetic overflow");
        require(curve.affordable(BigDecimal.ZERO,1)==0,"unpowered fractional work is free");
        var tiny=new HyperNetLoad(List.of(
                new HyperNetLoad.Installed(new HyperNetModule("tiny",HyperNetModule.Kind.CPU,1e-300,Long.MAX_VALUE),1),
                new HyperNetLoad.Installed(new HyperNetModule("large",HyperNetModule.Kind.GPU,2,0),1),
                new HyperNetLoad.Installed(new HyperNetModule("ram",HyperNetModule.Kind.RAM,2,1),1)));
        double affordable=tiny.affordable(BigDecimal.ZERO,2);
        require(affordable>=Double.MIN_VALUE&&tiny.cost(BigDecimal.valueOf(affordable)).compareTo(BigInteger.TWO)<=0,
                "power search lost affordable subnormal load");
    }
    private static void parserChecks(){
        String[] invalid={"{\"role\":\"center\",\"carrying\":1,\"max-connections\":1,\"modules\":[]}",
                "{\"role\":\"node\",\"connection-card\":\"minecraft:paper\"}",
                "{\"role\":\"center\",\"carrying\":1,\"max-connections\":1,\"connection-card\":\"minecraft:air\"}"};
        for(var json:invalid){try{HyperNetDefinition.parse(com.google.gson.JsonParser.parseString(json));throw new AssertionError("invalid profile accepted: "+json);}catch(IllegalArgumentException expected){}}
        for(String field:List.of("\"energy\":-1","\"energy\":1.5","\"energy\":9223372036854775808","\"capacity\":0","\"capacity\":1e999","\"kind\":\"cpux\"")){
            String json="[{\"upgrade\":\"test\",\"kind\":\"cpu\",\"capacity\":2,\"energy\":1}]";
            String key=field.substring(0,field.indexOf(':'));json=json.replaceAll(key+"[^,}]*",field);
            try{HyperNetModule.parse(com.google.gson.JsonParser.parseString(json));throw new AssertionError("invalid module accepted: "+field);}catch(IllegalArgumentException expected){}
        }
    }
    private static void finish(Throwable error){
        if(finished)return;finished=true;failed=error!=null;
        if(error!=null)Modularmachinery.LOG.error("[MMC][hypernet-hardware] FAIL phase "+phase,error);
        else Modularmachinery.LOG.info("[MMC][hypernet-hardware] runtime checks PASS; awaiting stop cleanup");
        server.halt(false);
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){
        if(server!=event.getServer()||failed)return;
        if(oldP==null||!oldP.isClosed()||!oldC.isClosed())Modularmachinery.LOG.error("[MMC][hypernet-hardware] OVERALL: FAIL stop cleanup");
        else Modularmachinery.LOG.info("[MMC][hypernet-hardware] OVERALL: PASS (physical CPU/GPU/RAM, static/dynamic caps, native FE, atomic simulations, hot swaps, real card events, revision and shutdown)");
    }
}
