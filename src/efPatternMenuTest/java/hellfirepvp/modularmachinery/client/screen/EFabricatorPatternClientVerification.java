/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.*;
import hellfirepvp.modularmachinery.common.network.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Disposable real encoded-pattern inventories; interactions use ordinary menu and production packets. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class EFabricatorPatternClientVerification {
    private static final BlockPos BASE = new BlockPos(0,120,0);
    private static TileEFabricatorController owner;
    private static TileEFabricatorPatternBus a,b;
    private static int total,ticks;
    private static boolean language,configured;
    private static volatile boolean opened;
    @Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT,bus=Mod.EventBusSubscriber.Bus.MOD)
    public static final class EarlyGuideConfig {
        @SubscribeEvent(priority=net.minecraftforge.eventbus.api.EventPriority.LOWEST)
        public static void register(net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
            if(!Boolean.getBoolean("modularmachinery.efPatternVisualTest")||!net.minecraftforge.fml.ModList.get().isLoaded("guideme"))return;
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
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.efPatternVisualTest"))return;
        var mc=Minecraft.getInstance();if(++total>5000){fail(new AssertionError("timeout"));return;}
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        try {
            if(!language){language=true;mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();return;}
            if(!configured){configured=true;mc.options.pauseOnLostFocus=false;mc.options.guiScale().set(3);mc.resizeDisplay();mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);mc.getToasts().clear();mouse(0,0);server(()->setup(mc));return;}
            if(!opened)return;
            switch(++ticks){
                case 35 -> {require(mc.screen instanceof ScreenEFabricatorPatternBus&&mc.player.containerMenu.slots.size()==108,"bus menu/slots");bounds();shot("ef-pattern-01-bus-zh.png");slot(72);}
                case 45 -> {require(!mc.player.containerMenu.getCarried().isEmpty(),"bus carried pickup");slot(71);}
                case 60 -> {require(mc.player.containerMenu.getCarried().isEmpty()&&!mc.player.containerMenu.slots.get(71).getItem().isEmpty(),"last pattern slot placement");shot("ef-pattern-02-last-slot.png");slot(71);}
                case 70 -> slot(72);
                case 85 -> {require(mc.player.containerMenu.getCarried().isEmpty(),"pattern returned to player");server(()->use(mc,BASE));}
                case 110 -> {require(mc.screen instanceof ScreenEFabricatorController,"monitor navigation");click(218,5);}
                case 140 -> {require(mc.screen instanceof ScreenEFabricatorPatternSearch,"search navigation");require(data().total()==50&&data().entries().size()==48&&data().pages()==2,"search real inventory page");bounds();shot("ef-pattern-03-search-zh.png");mouse(17,52);}
                case 145 -> shot("ef-pattern-04-tooltip.png");
                case 150 -> {mouse(0,0);screen().mouseScrolled(screen().getGuiLeft()+30,screen().getGuiTop()+50,-1);}
                case 175 -> {require(data().page()==1&&data().entries().size()==2,"scroll page");shot("ef-pattern-05-page2.png");click(231,44);}
                case 195 -> {require(data().page()==0,"scrollbar top click");screen().mouseClicked(screen().getGuiLeft()+231,screen().getGuiTop()+49,0);screen().mouseDragged(screen().getGuiLeft()+231,screen().getGuiTop()+120,0,0,71);screen().mouseReleased(screen().getGuiLeft()+231,screen().getGuiTop()+120,0);}
                case 200 -> require(data().page()==1,"scrollbar bottom drag");
                case 201 -> search(true,"金锭");
                case 225 -> {require(data().total()==1&&data().entries().get(0).description().icon().is(Items.GOLD_INGOT)&&!data().entries().get(0).description().valid(),"Chinese output search/processing warning");shot("ef-pattern-06-chinese-search.png");search(true,"橡木木板");}
                case 250 -> {require(data().total()==49,"Chinese crafting search");shot("ef-pattern-07-crafting-search.png");click(17,52);}
                case 270 -> {require(!mc.player.containerMenu.getCarried().isEmpty()&&data().total()==48,"search real pickup");shot("ef-pattern-08-picked-pattern.png");click(17,52);}
                case 290 -> {require(mc.player.containerMenu.getCarried().isEmpty()&&data().total()==49,"search real insertion");search(true,"");search(false,"oak_log");}
                case 305 -> {require(data().total()==49,"input ID search");shot("ef-pattern-09-id-search.png");search(false,"@minecraft");}
                case 335 -> {require(data().total()==50,"mod ID search");search(false,"");search(true,"no_such_product");}
                case 365 -> {require(data().total()==0,"empty results");shot("ef-pattern-10-no-match.png");search(true,"");
                    var box=boxes().get(0);screen().setFocused(box);box.setFocused(true);screen().keyPressed(69,0,0);screen().charTyped('e',0);require(mc.screen instanceof ScreenEFabricatorPatternSearch&&box.getValue().equals("e"),"inventory E intercepted search input");box.setValue("");box.setFocused(false);screen().setFocused(null);mc.options.guiScale().set(2);mc.resizeDisplay();}
                case 395 -> {bounds();shot("ef-pattern-11-zh-scale2.png");mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks();}
                case 435 -> {bounds();shot("ef-pattern-12-en.png");org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),854,480);}
                case 465 -> {bounds();shot("ef-pattern-13-small.png");
                    var menu=(MenuEFabricatorPatternSearch)mc.player.containerMenu;ModularmachineryNetwork.CHANNEL.sendToServer(new EFabricatorPatternPackets.Action(menu.containerId,UUID.randomUUID(),0,data().token(),0));}
                case 485 -> {require(mc.player.containerMenu.getCarried().isEmpty()&&data().total()==50,"foreign session took pattern");server(()->{var bad=new CompoundTag();bad.putInt("Version",999);a.restorePatternData(bad);});}
                case 515 -> {require(!data().readable()&&data().total()==1,"unknown bus hidden/preserved");shot("ef-pattern-14-unknown.png");server(()->{owner.getLevel().setBlockAndUpdate(BASE.east(2),Blocks.AIR.defaultBlockState());owner.refreshStructure();});}
                case 545 -> {require(!data().formed()&&data().total()==0,"structure loss removed remote entries");shot("ef-pattern-15-unformed.png");click(218,5);}
                case 570 -> require(mc.screen instanceof ScreenEFabricatorController,"return navigation");
                case 575 -> server(()->use(mc,BASE.east()));
                case 600 -> {require(mc.screen instanceof ScreenEFabricatorPatternBus&&!((MenuEFabricatorPatternBus)mc.player.containerMenu).readable(),"unreadable physical bus status");bounds();shot("ef-pattern-16-bus-unknown-small-en.png");Modularmachinery.LOG.info("[MMC][ef-pattern-client] PASS: actual bus right-click, real encoded inventory/cursor/last-slot, monitor/search navigation, production packets, 50 patterns and two pages, scrollbar click/drag/wheel, Chinese names/IDs/@mod/no-match, input E, pickup/reinsert conservation, foreign session, unknown/broken structure, Chinese/English/scales3/2/854x480 and unreadable physical bus; inspect screenshots.");Modularmachinery.LOG.info("[MMC][ef-pattern-client] OVERALL: PASS");}
                case 620 -> mc.stop();
            }
        }catch(Throwable failure){fail(failure);}
    }
    private static void setup(Minecraft mc){
        var world=mc.getSingleplayerServer().overworld();var pattern=new MachinePattern();for(int i=1;i<=2;i++){pattern.addPart(new MachinePattern.Part(i,0,0,"modularmachinery:efabricator_pattern_bus"));world.setBlockAndUpdate(BASE.east(i),EFabricatorRegistration.PATTERN_BUS.get().defaultBlockState());}
        var id=new ResourceLocation("modularmachinery",EFabricatorRegistration.machineName(EFabricatorSpecs.Tier.L4));MachineReloadListener.getLoader().getMachines().put(id,new DynamicMachine(id.getPath(),"EF pattern display fixture",pattern));
        world.setBlockAndUpdate(BASE,EFabricatorRegistration.CONTROLLERS.get(EFabricatorSpecs.Tier.L4).get().defaultBlockState());owner=(TileEFabricatorController)world.getBlockEntity(BASE);owner.refreshStructure();require(owner.isAssembled(),"fixture structure");
        a=(TileEFabricatorPatternBus)world.getBlockEntity(BASE.east());b=(TileEFabricatorPatternBus)world.getBlockEntity(BASE.east(2));var patterns=hellfirepvp.modularmachinery.integration.ae2.efabricator.EFabricatorMenuPatterns.create(world);
        EFabricatorPatternMenuRegressionChecks.load(a,patterns.get(0),49);EFabricatorPatternMenuRegressionChecks.load(b,patterns.get(1),1);
        var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);player.getAbilities().flying=true;player.onUpdateAbilities();player.teleportTo(world,.5,120,2.5,180,0);
        player.setShiftKeyDown(false);player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);player.getInventory().setItem(9,patterns.get(0).copy());use(mc,BASE.east());require(player.containerMenu instanceof MenuEFabricatorPatternBus,"actual bus use");opened=true;
    }
    private static void use(Minecraft mc,BlockPos pos){var world=mc.getSingleplayerServer().overworld();var player=mc.getSingleplayerServer().getPlayerList().getPlayers().get(0);world.getBlockState(pos).use(world,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));}
    private static net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> screen(){return (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)Minecraft.getInstance().screen;}
    private static EFabricatorPatternView.Snapshot data(){return ((MenuEFabricatorPatternSearch)Minecraft.getInstance().player.containerMenu).snapshot();}
    private static List<EditBox> boxes(){return screen().children().stream().filter(e->e instanceof EditBox).map(e->(EditBox)e).sorted(Comparator.comparingInt(EditBox::getX)).toList();}
    private static void search(boolean output,String value){var box=boxes().get(output?1:0);boxes().forEach(b->b.setFocused(false));screen().setFocused(box);box.setFocused(true);box.setValue("");for(char c:value.toCharArray())screen().charTyped(c,0);}
    private static void slot(int slot){var mc=Minecraft.getInstance();mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,ClickType.PICKUP,mc.player);}
    private static void click(int x,int y){screen().mouseClicked(screen().getGuiLeft()+x,screen().getGuiTop()+y,0);screen().mouseReleased(screen().getGuiLeft()+x,screen().getGuiTop()+y,0);}
    private static void mouse(int x,int y){var mc=Minecraft.getInstance();double scale=mc.getWindow().getGuiScale();int left=mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s?s.getGuiLeft():0;int top=mc.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s?s.getGuiTop():0;org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(),(left+x)*scale,(top+y)*scale);}
    private static void bounds(){var s=screen();require(s.getGuiLeft()>=0&&s.getGuiTop()>=0&&s.getGuiLeft()+s.getXSize()<=s.width&&s.getGuiTop()+s.getYSize()<=s.height,"outside window");}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][ef-pattern-client] {}",r.getString()));}
    private static void server(Runnable task){Minecraft.getInstance().getSingleplayerServer().execute(()->{try{task.run();}catch(Throwable failure){fail(failure);}});}
    private static void require(boolean value,String why){if(!value)throw new AssertionError(why);}
    private static void fail(Throwable f){Modularmachinery.LOG.error("[MMC][ef-pattern-client] OVERALL: FAIL tick {}",ticks,f);Minecraft.getInstance().execute(()->Minecraft.getInstance().stop());}
}
