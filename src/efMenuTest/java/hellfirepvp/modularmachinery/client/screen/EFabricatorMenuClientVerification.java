/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.MenuEFabricatorController;
import hellfirepvp.modularmachinery.common.network.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Disposable display fixture: loaded FIFO records, not AE orders. Exercises actual menus/packets/slots. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class EFabricatorMenuClientVerification {
    private static final BlockPos BASE=new BlockPos(0,120,0);
    private static TileEFabricatorController owner;
    private static int total,ticks;
    private static boolean configured,language;
    private static volatile boolean opened;
    @Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class EarlyGuideConfig {
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void register(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            if(!Boolean.getBoolean("modularmachinery.efMenuVisualTest")||!net.minecraftforge.fml.ModList.get().isLoaded("guideme"))return;
            try {
                var type=Class.forName("guideme.internal.GuideMEClient");var instance=type.getMethod("instance").invoke(null);
                var field=type.getDeclaredField("clientConfig");field.setAccessible(true);var config=field.get(instance);
                var specField=config.getClass().getDeclaredField("spec");specField.setAccessible(true);
                var spec=(net.minecraftforge.common.ForgeConfigSpec)specField.get(config);
                if(!spec.isLoaded()){var memory=com.electronwill.nightconfig.core.CommentedConfig.inMemory();spec.correct(memory);spec.setConfig(memory);}
            }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.efMenuVisualTest"))return;
        var mc=Minecraft.getInstance(); if(++total>5000){fail(new AssertionError("client timeout"));return;}
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        try {
            if (EFabricatorTopClientVerification.active()) { EFabricatorTopClientVerification.tick(); return; }
            if(!language){language=true;mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();return;}
            if(!configured){configured=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(3);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();mouseRaw(0,0);server(()->setup(mc));return;}
            if(!opened||!(mc.screen instanceof ScreenEFabricatorController))return;
            if (Boolean.getBoolean("modularmachinery.efTopVisualTest")) { EFabricatorTopClientVerification.begin(owner); return; }
            switch(++ticks) {
                case 35 -> {
                    var d=menu().snapshot();require(d.workerCount()==13&&d.workers().size()==12&&d.pages()==2,"wire worker pages");
                    require(d.queued()==26&&d.energy()==3250000&&d.crafted()==7777777777L&&d.coolant()==45000,"loaded real queues/cache/totals");
                    require(d.input()==4000&&d.output()==1000&&d.readable(),"bound real fluid hatches");
                    require(d.workers().get(0).icon().is(Items.DIAMOND)&&!d.workers().get(0).icon().hasTag(),"private display data");
                    require(menu().slots.size()==36,"player inventory slots");bounds();shot("ef-menu-01-zh-scale3.png");
                    server(()->{var data=owner.productionData();data.putLong("TotalCrafted",owner.totalCrafted()+200);owner.restoreProductionData(data);});
                }
                case 40 -> mouse(13,42);
                case 45 -> shot("ef-menu-02-worker-tooltip.png");
                case 48 -> {mouse(0,0);screen().mouseScrolled(screen().getGuiLeft()+40,screen().getGuiTop()+40,-1);}
                case 70 -> {require(menu().snapshot().page()==1&&menu().snapshot().workers().size()==1,"actual page response");
                    require(menu().craftsPerTick()>0,"accepted live total did not produce source throughput");
                    double rate=menu().craftsPerTick();for(int i=0;i<500;i++)require(menu().craftsPerTick()==rate,"rate inspection mutated sampling");
                    mouse(80,7);shot("ef-menu-03-last-page.png");}
                case 74 -> {shot("ef-menu-14-throughput-zh.png");click(177,7);}
                case 90 -> {require(menu().snapshot().page()==0,"page arrow");screen().mouseClicked(screen().getGuiLeft()+22,screen().getGuiTop()+93,0);}
                case 93 -> shot("ef-menu-12-overclock-pressed.png");
                case 94 -> screen().mouseReleased(screen().getGuiLeft()+22,screen().getGuiTop()+93,0);
                case 110 -> {require(menu().snapshot().overclock()&&menu().snapshot().queueCapacity()==6656,"overclock response");mouse(22,93);shot("ef-menu-04-overclock.png");click(62,93);}
                case 135 -> {require(menu().snapshot().cooling()&&menu().snapshot().delay()==7,"active cooling response");mouse(62,93);shot("ef-menu-05-cooling.png");
                    ModularmachineryNetwork.CHANNEL.sendToServer(new EFabricatorMonitorPackets.Action(menu().containerId,UUID.randomUUID(),0,false));}
                case 155 -> {require(menu().snapshot().overclock(),"foreign session altered mode");require(menu().craftsPerTick()==0,"idle heartbeats retained throughput");mouse(0,0);mc.options.guiScale().set(2);mc.resizeDisplay();}
                case 175 -> {bounds();shot("ef-menu-06-zh-scale2.png");
                    server(()->{var data=owner.productionData();data.putLong("TotalCrafted",owner.totalCrafted()+400);owner.restoreProductionData(data);});
                    mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks();}
                case 205 -> mouse(80,7);
                case 210 -> {bounds();require(menu().craftsPerTick()>0,"English throughput sample");shot("ef-menu-15-throughput-en.png");shot("ef-menu-07-en-scale2.png");org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),854,480);}
                case 240 -> {bounds();shot("ef-menu-08-small-window.png");}
                case 250 -> mouse(192,162);
                case 260 -> shot("ef-menu-09-fluid-tooltip.png");
                case 265 -> {mouse(0,0);server(()->{var bad=new CompoundTag();bad.putInt("Version",999);((TileEFabricatorWorker)owner.getLevel().getBlockEntity(BASE.east())).restoreWorkData(bad);});}
                case 300 -> {require(!menu().snapshot().readable()&&!menu().snapshot().workers().get(0).readable(),"invalid queue masked as empty");shot("ef-menu-10-unknown.png");
                    require(menu().craftsPerTick()==0,"unreadable server data retained throughput");
                    server(()->{owner.getLevel().setBlockAndUpdate(BASE.east(13),Blocks.AIR.defaultBlockState());owner.refreshStructure();});}
                case 335 -> {require((menu().snapshot().flags()&2)==0&&menu().snapshot().workers().isEmpty(),"structure loss retained cores");shot("ef-menu-11-unformed.png");}
                case 345 -> {Modularmachinery.LOG.info("[MMC][ef-menu-client] PASS: actual controller right-click/packets, 13 loaded FIFO cores, bound fluid hatches, long totals, player-only slots, paging/wheel, modes and foreign-session rejection, Chinese/English, scales 3/2, 854x480, unknown data and structure loss. Loaded display fixture, not native AE orders; inspect screenshots.");Modularmachinery.LOG.info("[MMC][ef-menu-client] OVERALL: PASS");}
                case 365 -> mc.stop();
            }
        }catch(Throwable failure){fail(failure);}
    }
    private static void setup(Minecraft mc) {
        var world=mc.getSingleplayerServer().overworld();var pattern=new MachinePattern();
        for(int i=1;i<=13;i++){pattern.addPart(new MachinePattern.Part(i,0,0,"modularmachinery:efabricator_worker"));world.setBlockAndUpdate(BASE.east(i),EFabricatorRegistration.WORKER.get().defaultBlockState());}
        for(int dy : new int[]{-1,1}){String id="efabricator_parallel_proc_l"+(dy>0?6:9);pattern.addPart(new MachinePattern.Part(1,dy,0,"modularmachinery:"+id));world.setBlockAndUpdate(BASE.east().offset(0,dy,0),EFabricatorRegistration.PARTS.get(id).get().defaultBlockState());}
        pattern.addPart(new MachinePattern.Part(0,0,1,"modularmachinery:blockfluidinputhatch"));pattern.addPart(new MachinePattern.Part(0,0,-1,"modularmachinery:blockfluidoutputhatch"));
        var id=new ResourceLocation("modularmachinery",EFabricatorRegistration.machineName(EFabricatorSpecs.Tier.L9));
        MachineReloadListener.getLoader().getMachines().put(id,new DynamicMachine(id.getPath(),"EF menu display fixture",pattern));
        world.setBlockAndUpdate(BASE,EFabricatorRegistration.CONTROLLERS.get(EFabricatorSpecs.Tier.L9).get().defaultBlockState());
        world.setBlockAndUpdate(BASE.south(),hellfirepvp.modularmachinery.common.registry.RegistryBlocks.fluidInputHatch.get().defaultBlockState());
        world.setBlockAndUpdate(BASE.north(),hellfirepvp.modularmachinery.common.registry.RegistryBlocks.fluidOutputHatch.get().defaultBlockState());
        var in=(hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank)world.getBlockEntity(BASE.south());var out=(hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank)world.getBlockEntity(BASE.north());
        in.getTank().setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER,4000));
        out.getTank().setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.LAVA,1000));
        owner=(TileEFabricatorController)world.getBlockEntity(BASE);owner.refreshStructure();require(owner.isAssembled(),"fixture not formed");
        owner.setModes(false,false); // A copied, previously completed fixture may already have both modes enabled.
        var prod=owner.productionData();prod.putLong("TotalCrafted",7777777777L);var cooling=new CompoundTag();cooling.putInt("Version",1);cooling.putInt("Cache",45000);prod.put("Cooling",cooling);owner.restoreProductionData(prod);require(owner.productionReadable(),"cooling display fixture codec");
        for(int i=1;i<=13;i++){
            var q=new EFabricatorQueue();var icon=new ItemStack(i==1?Items.DIAMOND:i%2==0?Items.OAK_PLANKS:Items.CAKE);icon.getOrCreateTag().putString("Private","hidden");q.offer(new EFabricatorQueue.Work(2,icon,List.of()),32);
            var tag=new CompoundTag();tag.putInt("Version",1);tag.putInt("Energy",250000);tag.put("Queue",q.save());((TileEFabricatorWorker)world.getBlockEntity(BASE.east(i))).restoreWorkData(tag);
        }
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(world,.5,120,2.5,180,0);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setShiftKeyDown(false);
        player.getInventory().setItem(9,new ItemStack(Items.APPLE,3));
        owner.getBlockState().use(world,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(BASE),Direction.NORTH,BASE,false));
        require(player.containerMenu instanceof MenuEFabricatorController,"actual right-click failed");opened=true;
    }
    private static ScreenEFabricatorController screen(){return (ScreenEFabricatorController)Minecraft.getInstance().screen;}
    private static MenuEFabricatorController menu(){return screen().getMenu();}
    private static void click(int x,int y){screen().mouseClicked(screen().getGuiLeft()+x,screen().getGuiTop()+y,0);screen().mouseReleased(screen().getGuiLeft()+x,screen().getGuiTop()+y,0);}
    private static void mouseRaw(double x,double y){var mc=Minecraft.getInstance();org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),x,y);}
    private static void mouse(int x,int y){var mc=Minecraft.getInstance();double s=mc.getWindow().getGuiScale();mouseRaw((screen().getGuiLeft()+x)*s,(screen().getGuiTop()+y)*s);}
    private static void bounds(){require(screen().getGuiLeft()>=0&&screen().getGuiTop()>=0&&screen().getGuiLeft()+243<=screen().width&&screen().getGuiTop()+202<=screen().height,"panel outside window");}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][ef-menu-client] {}",r.getString()));}
    private static void server(Runnable task){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{task.run();}catch(Throwable failure){fail(failure);}});}
    private static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    private static void fail(Throwable f){Modularmachinery.LOG.error("[MMC][ef-menu-client] OVERALL: FAIL tick {}",ticks,f);Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
}
