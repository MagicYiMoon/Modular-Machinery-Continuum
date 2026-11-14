/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import appeng.api.config.*;
import appeng.api.crafting.*;
import appeng.api.networking.crafting.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.screen.ScreenECalculatorController;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.MenuECalculatorController;
import hellfirepvp.modularmachinery.common.network.*;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
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
import java.util.concurrent.Future;

/** Opt-in disposable world only. Actual AE plans/tasks and production menu packets; never shipped. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID, value=Dist.CLIENT)
public final class ECalculatorMenuClientVerification {
    private static final BlockPos BASE=new BlockPos(0,120,0), CHANNEL=BASE.south();
    private static final AEKey LOG=AEItemKey.of(Items.OAK_LOG), DIAMOND=AEItemKey.of(Items.DIAMOND);
    private static ECalculatorMEChannelNode channel;
    private static TileECalculatorController owner;
    private static Future<ICraftingPlan> calculation;
    private static final CounterStorage storage=new CounterStorage();
    private static int total,ticks,phase;
    private static boolean configured,language;
    private static volatile boolean opened;
    private static UUID firstVisible,oldPageTask;
    private static MenuECalculatorController serverMenu;
    private static UUID menuSession;
    @Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class EarlyGuideConfig {
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void register(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            if(!Boolean.getBoolean("modularmachinery.ecMenuVisualTest"))return;
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
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.ecMenuVisualTest"))return;
        var mc=Minecraft.getInstance();
        if(++total>6000){fail(new AssertionError("client timeout"));return;}
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        try {
            if(!language){language=true;mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();return;}
            if(!configured){configured=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(3);mc.resizeDisplay();
                mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();
                org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),0,0);server(()->setup(mc));return;}
            if(Boolean.getBoolean("modularmachinery.ecTopVisualTest")&&ECalculatorTopClientVerification.active()){ECalculatorTopClientVerification.tick();return;}
            if(!opened||!(mc.screen instanceof ScreenECalculatorController))return;
            switch(++ticks) {
                case 35 -> {
                    var data=menu().snapshot();
                    require(data.taskCount()==7&&data.tasks().size()==4&&data.taskPages()==2&&data.cores().size()==12&&data.corePages()==2,"live bounded task/core pages");
                    require(data.used()>Integer.MAX_VALUE&&data.capacity()==16_777_216_000L&&data.normal()==1&&data.hyper()==6,"actual long task reservations or hyper counts");
                    require(data.tasks().get(0).name().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents
                            && data.tasks().get(0).name().getString().equals(new ItemStack(Items.DIAMOND).getHoverName().getString()),"localized task name was flattened by the server");
                    require(menu().slots.isEmpty(),"virtual icon exposed as inventory slot");
                    require(menu().statistics().ready() && menu().statistics().nanosPerTick()>0
                            && menu().statistics().tasks().stream().allMatch(t->t.measurement().ready()&&t.measurement().operationsPerTick()==0),
                            "real statistics packets missing, or blocked providers counted as successful pattern pushes");
                    bounds();shot("ec-menu-01-zh-scale3.png");oldPageTask=data.tasks().get(0).id();
                }
                case 37 -> mouse(142,47);
                case 39 -> shot("ec-menu-12-statistics-total.png");
                case 40 -> mouse(26,75);
                case 43 -> shot("ec-menu-10-core-tooltip.png");
                case 45 -> { mouse(0,0);screen().mouseScrolled(screen().getGuiLeft()+40,screen().getGuiTop()+75,-1); }
                case 65 -> { require(menu().snapshot().corePage()==1&&menu().snapshot().cores().size()==1,"core wheel request/response");shot("ec-menu-02-core-last-page.png");
                    screen().mouseScrolled(screen().getGuiLeft()+200,screen().getGuiTop()+175,-1); }
                case 72 -> mouse(110,153);
                case 76 -> shot("ec-menu-13-statistics-task.png");
                case 80 -> mouse(0,0);
                case 90 -> {
                    require(menu().snapshot().taskPage()==1&&menu().snapshot().tasks().size()==3,"task wheel request/response");shot("ec-menu-03-task-last-page.png");
                    // Replay a real previous-page UUID. Server must not use a row number to cancel another task.
                    ModularmachineryNetwork.CHANNEL.sendToServer(new ECalculatorMonitorPackets.Cancel(menu().containerId,menu().session(),oldPageTask));
                }
                case 110 -> {
                    require(menu().snapshot().taskCount()==7,"old-page cancel changed a task");firstVisible=menu().snapshot().tasks().get(0).id();
                    click(156,146);mouse(156,146);
                }
                case 125 -> { require(menu().snapshot().taskCount()==7,"single cancel click was destructive");shot("ec-menu-04-cancel-confirm.png");click(156,146); }
                case 145 -> mouse(111,153);
                case 155 -> {
                    require(menu().snapshot().tasks().stream().anyMatch(t->t.id().equals(firstVisible)&&!t.running()),"blocked AE material return disappeared");
                    require(menu().snapshot().used()>Integer.MAX_VALUE,"cancel released retained memory");shot("ec-menu-05-returning.png");
                    mouse(0,0);server(()->storage.accept=true);
                }
                case 190 -> {
                    require(menu().snapshot().taskCount()==6,"accepted return did not release task");
                    mc.options.guiScale().set(2);mc.resizeDisplay();
                }
                case 210 -> { bounds();shot("ec-menu-06-zh-scale2.png");mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks(); }
                case 240 -> { bounds();require(menu().snapshot().tasks().get(0).name().getString().equals(new ItemStack(Items.DIAMOND).getHoverName().getString()),"task names failed to follow client language");
                    shot("ec-menu-07-en-scale2.png");org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),854,480); }
                case 270 -> { bounds();shot("ec-menu-08-small-window.png"); }
                case 290 -> {
                    server(()->{
                        var world=mc.getSingleplayerServer().overworld();world.setBlockAndUpdate(CHANNEL.south(),Blocks.AIR.defaultBlockState());
                    });
                }
                case 400 -> {
                    require((menu().snapshot().flags()&8)==0&&menu().snapshot().tasks().stream().noneMatch(t->t.online()),"offline task display");
                    require(menu().snapshot().taskCount()==6,"offline tasks hidden");shot("ec-menu-09-offline.png");
                }
                case 410 -> server(()->{
                    var opaque=new net.minecraft.nbt.CompoundTag();opaque.putString("AE2Crafting","unknown-format");
                    ((TileECalculatorPart)owner.getLevel().getBlockEntity(BASE.east(14))).restoreBackend(opaque);
                });
                case 440 -> { require((menu().snapshot().flags()&16)!=0&&!menu().snapshot().cores().get(0).readable(),"unreadable task data appeared empty");require(!menu().statistics().ready(),"unreadable statistics presented as measured zero");shot("ec-menu-11-unreadable.png"); }
                case 450 -> {
                    Modularmachinery.LOG.info("[MMC][ec-statistics-client] PASS: real bounded statistics packet, current task identities/samples, actual measured CPU time, blocked provider zero operations, unknown corrupt-core totals; total/task tooltips captured");
                    Modularmachinery.LOG.info("[MMC][ec-menu-client] PASS: actual right-click menu, 13 cores/7 AE tasks, bounded wire pages, long reservations, normal/hyper, old-page replay rejection, double-click cancel/blocked return/release, localized name components, Chinese/English, scales 3/2, 854x480, offline task retention and unknown usage; screenshots require inspection");
                    Modularmachinery.LOG.info("[MMC][ec-menu-client] OVERALL: PASS");
                    if(Boolean.getBoolean("modularmachinery.ecTopVisualTest")) ECalculatorTopClientVerification.begin(owner, CHANNEL);
                }
                case 470 -> mc.stop();
            }
        }catch(Throwable failure){fail(failure);}
    }
    private static void setup(Minecraft mc) {
        var server=mc.getSingleplayerServer();var world=server.overworld();
        var pattern=new MachinePattern();
        pattern.addPart(new MachinePattern.Part(1,0,0,"modularmachinery:ecalculator_cell_drive"));
        for(int i=0;i<13;i++){
            String name=i==0?"ecalculator_thread_core_l4":i==1?"ecalculator_thread_core_hyper_l4":"ecalculator_thread_core_hyper_l6";
            pattern.addPart(new MachinePattern.Part(i+2,0,0,"modularmachinery:"+name));
            world.setBlockAndUpdate(BASE.east(i+2),ECalculatorRegistration.PARTS.get(name).get().defaultBlockState());
        }
        pattern.addPart(new MachinePattern.Part(15,0,0,"modularmachinery:ecalculator_parallel_proc_l4"));
        pattern.addPart(new MachinePattern.Part(0,0,1,"modularmachinery:ecalculator_me_channel"));
        var id=new ResourceLocation("modularmachinery",ECalculatorRegistration.machineName(9));
        MachineReloadListener.getLoader().getMachines().put(id,new DynamicMachine(id.getPath(),"EC menu fixture",pattern));
        world.setBlockAndUpdate(BASE,ECalculatorRegistration.CONTROLLERS.get(9).get().defaultBlockState());
        world.setBlockAndUpdate(BASE.east(),ECalculatorRegistration.DRIVE.get().defaultBlockState());
        world.setBlockAndUpdate(BASE.east(15),ECalculatorRegistration.PARTS.get("ecalculator_parallel_proc_l4").get().defaultBlockState());
        world.setBlockAndUpdate(CHANNEL,ECalculatorRegistration.CHANNEL.get().defaultBlockState());
        world.setBlockAndUpdate(CHANNEL.south(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        world.setBlockAndUpdate(CHANNEL.west(),AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        var provider=(PatternProviderBlockEntity)world.getBlockEntity(CHANNEL.west());
        provider.getLogic().getPatternInv().setItemDirect(0,PatternDetailsHelper.encodeProcessingPattern(new GenericStack[]{new GenericStack(LOG,1)},new GenericStack[]{new GenericStack(DIAMOND,1)}));
        ((TileECalculatorDrive)world.getBlockEntity(BASE.east())).insertMemory(new ItemStack(ECalculatorRegistration.MEMORIES.get(ECalculatorSpecs.Memory.C).get()));
        owner=(TileECalculatorController)world.getBlockEntity(BASE);owner.refreshStructure();require(owner.isOperational(),"fixture not formed");
        channel=(ECalculatorMEChannelNode)world.getBlockEntity(CHANNEL);
        var player=server.getPlayerList().getPlayers().get(0);
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
        player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(world,.5,120,2.5,180,0);
        player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.setShiftKeyDown(false);
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.ecMenuVisualTest")||owner==null||opened)return;
        try {
            if(!channel.isNetworkActive()||channel.getActionableNode().getGrid().getPathingService().isNetworkBooting())return;
            var grid=channel.getActionableNode().getGrid();
            if(phase==0){storage.values.put(LOG,64L);grid.getStorageService().addGlobalStorageProvider(m->m.mount(storage));phase=1;return;}
            if(phase==1){if(!grid.getCraftingService().isCraftable(DIAMOND))return;
                calculation=grid.getCraftingService().beginCraftingCalculation(owner.getLevel(),()->new appeng.me.helpers.MachineSource(channel),DIAMOND,1,CalculationStrategy.REPORT_MISSING_ITEMS);phase=2;return;}
            if(!calculation.isDone())return;var plan=calculation.get();require(!plan.simulation(),"real AE plan failed");
            for(int i=0;i<7;i++){
                channel.tick();var cpu=channel.publishedCpus().stream().filter(c->c.tasks==null).findFirst().orElseThrow();
                var large=new PlanView(plan,i==0?3_000_000_000L:100_000_000L);
                require(grid.getCraftingService().submitJob(large,null,cpu.cluster,true,IActionSource.empty()).successful(),"real AE task submit "+i);
            }
            storage.accept=false;
            var server=Minecraft.getInstance().getSingleplayerServer();var player=server.getPlayerList().getPlayers().get(0);
            var state=owner.getBlockState();state.use(owner.getLevel(),player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(BASE),Direction.NORTH,BASE,false));
            require(player.containerMenu instanceof MenuECalculatorController,"actual right click failed to open EC menu");
            serverMenu=(MenuECalculatorController)player.containerMenu;menuSession=serverMenu.session();opened=true;
        }catch(Throwable failure){fail(failure);}
    }
    private record PlanView(ICraftingPlan original,long bytes) implements ICraftingPlan {
        public GenericStack finalOutput(){return original.finalOutput();}public boolean simulation(){return original.simulation();}
        public boolean multiplePaths(){return original.multiplePaths();}public KeyCounter usedItems(){return original.usedItems();}
        public KeyCounter emittedItems(){return original.emittedItems();}public KeyCounter missingItems(){return original.missingItems();}
        public Map<IPatternDetails,Long> patternTimes(){return original.patternTimes();}
    }
    private static final class CounterStorage implements MEStorage {
        final Map<AEKey,Long> values=new LinkedHashMap<>();boolean accept;
        public long insert(AEKey key,long amount,Actionable mode,IActionSource source){if(!accept)return 0;if(mode==Actionable.MODULATE)values.merge(key,amount,Long::sum);return amount;}
        public long extract(AEKey key,long amount,Actionable mode,IActionSource source){long taken=Math.min(values.getOrDefault(key,0L),amount);if(mode==Actionable.MODULATE)values.put(key,values.getOrDefault(key,0L)-taken);return taken;}
        public void getAvailableStacks(KeyCounter out){values.forEach(out::add);}public Component getDescription(){return Component.literal("Disposable EC menu test inventory");}
    }
    private static ScreenECalculatorController screen(){return (ScreenECalculatorController)Minecraft.getInstance().screen;}
    private static MenuECalculatorController menu(){return screen().getMenu();}
    private static void click(int x,int y){screen().mouseClicked(screen().getGuiLeft()+x,screen().getGuiTop()+y,0);}
    private static void mouse(int x,int y){var mc=Minecraft.getInstance();double scale=mc.getWindow().getGuiScale();org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),(screen().getGuiLeft()+x)*scale,(screen().getGuiTop()+y)*scale);}
    private static void bounds(){require(screen().getGuiLeft()>=0&&screen().getGuiTop()>=0&&screen().getGuiLeft()+255<=screen().width&&screen().getGuiTop()+221<=screen().height,"panel outside window");}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][ec-menu-client] {}",r.getString()));}
    private static void server(Runnable task){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{task.run();}catch(Throwable failure){fail(failure);}});}
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
    private static void fail(Throwable failure){Modularmachinery.LOG.error("[MMC][ec-menu-client] OVERALL: FAIL phase {}, tick {}",phase,ticks,failure);Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
}
