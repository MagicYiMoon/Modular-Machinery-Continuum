/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.BlockController;
import hellfirepvp.modularmachinery.common.data.ModDataHolder;
import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.MenuHyperNetResearch;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.server.level.*;
import net.minecraft.world.*;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.*;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.nio.file.*;
import java.util.*;

/** Disposable integrated world: real card, source slots, actual server menu packets and paid research. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class HyperNetTerminalClientVerification {
    private static final BlockPos C=new BlockPos(0,100,0),P=C.east(4),D=C.east(8),S=C.east(12),T=C.south(5);
    private static TileController center,processor,database,station;private static TileHyperNetTerminal terminal;
    private static ServerLevel world;private static ServerPlayer actor;private static boolean connected;private static boolean keepClosed;private static volatile boolean outputWaiting,outputFinished;private static int noticeAt;
    private static int stage,ticks,waiting;private static long token;private static boolean clearedToasts;
    private static boolean iconTest(){return Boolean.getBoolean("modularmachinery.hypernetIconVisualTest");}
    private static boolean creativeTest(){return Boolean.getBoolean("modularmachinery.hypernetCreativeVisualTest");}
    private static boolean networkTest(){return Boolean.getBoolean("modularmachinery.hypernetNetworkVisualTest");}
    private static boolean completionTest(){return Boolean.getBoolean("modularmachinery.hypernetCompletionVisualTest");}
    private static ItemStack networkCard=ItemStack.EMPTY;private static boolean hoverNetwork,hoverCard,hoverCreative;
    @SubscribeEvent public static void render(net.minecraftforge.client.event.ScreenEvent.Render.Pre e){
        if((networkTest()&&(hoverNetwork||hoverCard)||creativeTest()&&hoverCreative)&&e.getScreen() instanceof ScreenHyperNetResearch s){
            float fit=Math.min(1,Math.min((s.width-4)/350F,(s.height-4)/250F));
            int x=(int)Math.round(s.width/2D+(s.getGuiLeft()+(hoverCreative?324:hoverCard?166:140)-s.width/2D)*fit);
            int y=(int)Math.round(s.height/2D+(s.getGuiTop()+(hoverCreative?150:hoverCard?234:190)-s.height/2D)*fit);
            e.setCanceled(true);s.renderWithTooltip(e.getGuiGraphics(),x,y,e.getPartialTick());
        }
    }
    private static boolean enabled(){return Boolean.getBoolean("modularmachinery.hypernetTerminalVisualTest");}
    private static void require(boolean v,String s){if(!v)throw new AssertionError(s);}
    @SubscribeEvent public static void commands(RegisterCommandsEvent e){if(!enabled())return;
        e.getDispatcher().register(Commands.literal("hnterm").requires(s->s.hasPermission(2)).executes(c->{setup(c.getSource().getPlayerOrException());return 1;}));
        if(networkTest())e.getDispatcher().register(Commands.literal("hnnet").requires(s->s.hasPermission(2))
                .then(Commands.literal("remove").executes(c->{networkCard=terminal.cards().getStackInSlot(0).copy();terminal.cards().setStackInSlot(0,ItemStack.EMPTY);return 1;}))
                .then(Commands.literal("blank").executes(c->{terminal.cards().setStackInSlot(0,new ItemStack(RegistryItems.HYPERNET_CONNECT_CARD.get()));return 1;}))
                .then(Commands.literal("restore").executes(c->{terminal.cards().setStackInSlot(0,networkCard.copy());return 1;}))
                .then(Commands.literal("stop").executes(c->{world.setBlockAndUpdate(C.east(),Blocks.REDSTONE_BLOCK.defaultBlockState());return 1;}))
                .then(Commands.literal("start").executes(c->{world.setBlockAndUpdate(C.east(),Blocks.AIR.defaultBlockState());return 1;})));
        if(completionTest())e.getDispatcher().register(Commands.literal("hntermclose").requires(s->s.hasPermission(2)).executes(c->{
            var out=(net.minecraftforge.items.IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(S.above(3))).getInventory();
            for(int i=0;i<out.getSlots();i++)out.setStackInSlot(i,new ItemStack(Items.COBBLESTONE,64));
            keepClosed=true;c.getSource().getPlayerOrException().closeContainer();return 1;
        }));
        if(completionTest())e.getDispatcher().register(Commands.literal("hntermreload").requires(s->s.hasPermission(2)).executes(c->{
            require(station.getHyperNetNode().researchTask().awaitingCoreCompletion(),"reload must retain committed task");
            var saved=station.saveWithoutMetadata();station.load(saved);return 1;
        }));
        if(completionTest())e.getDispatcher().register(Commands.literal("hntermfinish").requires(s->s.hasPermission(2)).executes(c->{
            var out=(net.minecraftforge.items.IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(S.above(3))).getInventory();
            for(int i=0;i<out.getSlots();i++)out.setStackInSlot(i,ItemStack.EMPTY);return 1;
        }));
    }
    private static TileController build(BlockPos pos,String name,boolean factory){
        var machine=MachineReloadListener.getLoader().findByName(name);require(machine!=null,"machine "+name);
        world.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        for(var part:machine.getPattern().getParts()){
            var b=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(part.elements()));
            require(b!=null&&b!=Blocks.AIR,"part "+part.elements());world.setBlockAndUpdate(pos.offset(part.x(),part.y(),part.z()),b.defaultBlockState());
        }
        var b=((BlockItem)DedicatedControllers.stackOrGeneric(name,factory).getItem()).getBlock();
        world.setBlockAndUpdate(pos,b.defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var t=(TileController)world.getBlockEntity(pos);t.setOwner(actor.getUUID());t.setMachineName(name);
        require(MachineFormation.tryForm(world,pos,Direction.NORTH,machine,MachineReloadListener.getLoader()).formed(),"formation "+name);
        t.setFormed(true);t.acknowledgeStructureCheck();return t;
    }
    private static void setup(ServerPlayer player){try{
        actor=player;world=player.serverLevel();world.setDayTime(6000);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        for(int x=-3;x<17;x++)for(int z=-3;z<9;z++)world.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState());
        var dir=ModDataHolder.INSTANCE.getMachineryDirectory().toPath().getParent().resolve("research");Files.createDirectories(dir);
        for(int i=0;i<24;i++){
            var j=new JsonObject();j.addProperty("id",String.format(Locale.ROOT,"terminal_%02d",i));j.addProperty("display-name",i==0?"基础材料 / Basic Materials":"研究 "+i+" / Research "+i);
            j.addProperty("required-points",i==23?3:completionTest()&&i==21?60:10000);j.addProperty("minimum-per-tick",1);j.addProperty("tech-level",i==23||completionTest()&&i==21?0:i%3);j.addProperty("preview-item",i%2==0?"minecraft:iron_ingot":"minecraft:redstone");
            if(iconTest()&&i==23)new HyperNetResearchIcon("{id:'minecraft:potion',Count:16b,tag:{Potion:'minecraft:strong_healing',CustomPotionColor:3368703,CustomModelData:17,Enchantments:[{id:'minecraft:unbreaking',lvl:2s}]}}").put(j);
            if(creativeTest()&&i==20){j.addProperty("tech-level",0);j.addProperty("minimum-per-tick",100);}
            if(creativeTest()&&i==19){var deps=new JsonArray();deps.add("terminal_20");j.add("dependencies",deps);}
            if(completionTest()&&i==21)j.addProperty("display-name","&b完成通知：这是用于验证长中文名称不会超出提示边框的研究 / Long research name");
            if(i==22){var deps=new JsonArray();deps.add("terminal_23");j.add("dependencies",deps);j.addProperty("hidden",true);}
            var descriptions=new JsonArray();for(int line=0;line<24;line++)descriptions.add("说明 "+line+"：通过网络分配实际算力。 Research consumes physical module power.");j.add("descriptions",descriptions);
            var unlocks=new JsonArray();unlocks.add("解锁对应的材料加工配方。");j.add("unlock-descriptions",unlocks);
            Files.writeString(dir.resolve(String.format(Locale.ROOT,"terminal_%02d.json",i)),j.toString());
        }
        MachineReloadListener.reloadNow();
        center=build(C,"hn_sample_center",true);processor=build(P,"hn_sample_processor",true);database=build(D,"hn_sample_database",true);station=build(S,"hn_sample_research_station",true);terminal=(TileHyperNetTerminal)build(T,"hypernet_terminal",false);
        var inv=(net.minecraftforge.items.IItemHandlerModifiable)((TileUpgradeBus)world.getBlockEntity(P.above(2))).getUpgradeInventory();
        inv.setStackInSlot(0,new ItemStack(Items.DIAMOND,2));inv.setStackInSlot(1,new ItemStack(Items.REDSTONE,2));inv.setStackInSlot(2,new ItemStack(Items.EMERALD));
        player.teleportTo(world,T.getX()+.5,T.getY(),T.getZ()+2,180,0);
        player.getInventory().clearContent();player.getInventory().setItem(9,new ItemStack(Items.DIAMOND,32));player.getInventory().setItem(10,new ItemStack(Items.REDSTONE,16));
        player.getInventory().setItem(0,new ItemStack(Items.IRON_INGOT,64));player.getInventory().setItem(1,new ItemStack(RegistryItems.hypernetTerminal.get()));
    }catch(Exception ex){throw new RuntimeException(ex);}}
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent e){if(!enabled()||world==null||e.phase!=TickEvent.Phase.END)return;
        for(var t:List.of(center,processor,database,station,terminal))((TileEnergyInputHatch)world.getBlockEntity(t.getBlockPos().below())).produceInternal(10000,false);
        if(!center.getHyperNetNode().working()||!processor.getHyperNetNode().working())return;
        if(!connected){for(var t:List.of(processor,database,station))require(t.getHyperNetNode().connect(C,actor.getUUID())==HyperNetNode.ConnectResult.SUCCESS,"connect "+t.getMachineName());
            var card=new ItemStack(RegistryItems.HYPERNET_CONNECT_CARD.get());actor.setItemInHand(InteractionHand.MAIN_HAND,card);
            var click=new PlayerInteractEvent.RightClickBlock(actor,InteractionHand.MAIN_HAND,C,new BlockHitResult(Vec3.atCenterOf(C),Direction.NORTH,C,false));MinecraftForge.EVENT_BUS.post(click);require(card.hasTag(),"written card");
            terminal.cards().setStackInSlot(0,card.copy());actor.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_INGOT,64));connected=true;}
        if(completionTest()){
            var task=station.getHyperNetNode().researchTask();outputWaiting=task.awaitingCoreCompletion();
            outputFinished=keepClosed&&database.getHyperNetNode().researchFailure(List.of("terminal_21"))==null&&task.id().isEmpty();
        }
        if(!keepClosed&&actor.containerMenu==actor.inventoryMenu&&database.getHyperNetNode().working())MenuHyperNetResearch.open(actor,terminal);
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(!enabled()||e.phase!=TickEvent.Phase.END)return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        try{
            require(++waiting<1600,"timeout stage "+stage);if(++ticks%10!=0)return;
            if(stage==0){mc.player.connection.sendCommand("hnterm");next();return;}
            if(completionTest()&&stage>=17){
                require(!(mc.screen instanceof ScreenHyperNetResearch),"terminal reopened during closed-menu notice test");
                if(stage==17){
                    if(!outputWaiting||mc.getToasts().getToast(ResearchCompleteToast.class,"terminal_21")==null)return;
                    require(noticeCount("terminal_21")==1,"duplicate completion notice");noticeAt=ticks;next();return;
                }
                if(stage==18){if(ticks-noticeAt<20)return;shot("hn-terminal-10-completion-closed.png");mc.player.connection.sendCommand("hntermreload");next();return;}
                if(stage==19){
                    require(noticeCount("terminal_21")<=1,"notice duplicated during blocked-output reload");
                    if(ticks-noticeAt<180)return;
                    require(outputWaiting&&noticeCount("terminal_21")==0,"notice did not expire/repeated while output blocked");
                    mc.player.connection.sendCommand("hntermfinish");next();return;
                }
                if(stage==20){if(!outputFinished||waiting<40)return;require(noticeCount("terminal_21")==0,"core completion repeated committed notice");
                    Modularmachinery.LOG.info("[MMC][research-completion-client] PASS: targeted real packet, completed catalog and icon, closed terminal, output-blocked achieved save/load, single notice/expiry, no repeat after core finish; inspect PNG separately");mc.stop();next();return;}
            }
            if(!(mc.screen instanceof ScreenHyperNetResearch s)||s.getMenu().snapshot()==null)return;
            var data=s.getMenu().snapshot();
            switch(stage){
                case 1->{if(data.rows().isEmpty()||!data.status().isEmpty())return;if(!clearedToasts){mc.getToasts().clear();clearedToasts=true;return;}require(s.getMenu().slots.size()==37,"physical slots");
                    if(networkTest()){
                        require(data.network().online()&&data.network().offered()>0&&data.network().connections()==4,"idle network supply/connections");
                        var f=ScreenHyperNetResearch.class.getDeclaredMethod("flops",double.class);f.setAccessible(true);
                        require(f.invoke(null,0D).equals("0T FloPS")&&f.invoke(null,999.94D).equals("999.9T FloPS")&&f.invoke(null,1000D).equals("1P FloPS"),"source FLOPS thresholds/one maximum decimal");
                    }shot("hn-terminal-01-source-layout.png");click(40,52,0);next();}
                case 2->{token=data.token();click(101,233,0);next();}
                case 3->{if(data.token()<=token||data.page()!=data.pages()-1)return;shot("hn-terminal-02-last-page.png");click(101,45,0);next();}
                case 4->{if(data.page()!=0)return;var box=(EditBox)s.children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow();box.setValue("terminal_00");next();}
                case 5->{if(!data.query().equals("terminal_00")||data.total()!=1)return;shot("hn-terminal-03-search.png");click(337,150,0);click(324,150,0);next();}
                case 6->{if(!data.task().equals("terminal_00")||new java.math.BigDecimal(data.taskProgress()).signum()==0)return;
                    if(networkTest()){if(data.network().consumed()==0)return;require(data.network().online()&&data.network().delivered()>0,"working research consumption/delivery");}if(completionTest())require(noticeCount("terminal_00")==0,"premature completion notice");shot("hn-terminal-04-running.png");click(324,150,1);next();}
                case 7->{click(324,150,1);next();}
                case 8->{if(!data.task().isEmpty())return;if(completionTest())require(noticeCount("terminal_00")==0,"cancel emitted completion notice");require(new java.math.BigDecimal(data.selected().row().progress()).signum()>0,"cancel lost progress");shot("hn-terminal-05-cancelled.png");
                    var slot=s.getMenu().slots.get(0);click(slot.x+8,slot.y+8,0);next();}
                case 9->{require(!s.getMenu().getCarried().isEmpty()&&s.getMenu().getCarried().is(Items.DIAMOND),"scaled player slot hit");click(s.getMenu().slots.get(2).x+8,s.getMenu().slots.get(2).y+8,0);next();}
                case 10->{require(s.getMenu().getCarried().isEmpty()&&s.getMenu().slots.get(2).getItem().is(Items.DIAMOND),"scaled slot placement");shot("hn-terminal-06-slots.png");
                    ((EditBox)s.children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow()).setValue("terminal_23");next();}
                case 11->{if(!data.query().equals("terminal_23")||data.total()!=1)return;require(data.selected().row().state()==HyperNetResearchTerminal.State.AVAILABLE,"short research admission");click(324,150,0);next();}
                case 12->{if(data.selected().row().state()!=HyperNetResearchTerminal.State.COMPLETED||!data.task().isEmpty())return;if(completionTest()||iconTest())require(mc.getToasts().getToast(ResearchCompleteToast.class,"terminal_23")!=null,"missing real completion notice");
                    if(iconTest()){
                        var icon=data.selected().row().previewIcon().stack();checkIcon(icon);
                        var toast=mc.getToasts().getToast(ResearchCompleteToast.class,"terminal_23");var f=ResearchCompleteToast.class.getDeclaredField("icon");f.setAccessible(true);checkIcon((ItemStack)f.get(toast));
                    }shot("hn-terminal-07-completed-unlocks.png");click(304,150,0);next();}
                case 13->{shot("hn-terminal-08-original-description.png");((EditBox)s.children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow()).setValue("terminal_22");next();}
                case 14->{if(!data.query().equals("terminal_22"))return;require(data.total()==1&&data.selected().row().state()==HyperNetResearchTerminal.State.AVAILABLE,"hidden research did not become available");shot("hn-terminal-09-dependency-unlocked.png");
                    Modularmachinery.LOG.info("[MMC][hypernet-terminal-client] OVERALL: PASS: source layout, card and physical player slots, page scrollbar, text search, description scrollbar, network packets start/cancel and paid progress, real research completion, unlock/original description toggle, hidden dependency reveal, physical={}x{} GUI={}x{} language={}; inspect screenshots separately",mc.getWindow().getWidth(),mc.getWindow().getHeight(),s.width,s.height,mc.options.languageCode);
                    if(iconTest()){Modularmachinery.LOG.info("[MMC][research-icon-client] PASS: server-owned full stack in terminal and real completion toast, count one, potion/custom model/enchantment data preserved; physical={}x{} GUI={}x{} language={}",mc.getWindow().getWidth(),mc.getWindow().getHeight(),s.width,s.height,mc.options.languageCode);mc.stop();}else if(creativeTest()){mc.getToasts().clear();stage=39;}else if(networkTest()){mc.getToasts().clear();stage=29;}else if(completionTest()){mc.getToasts().clear();((EditBox)s.children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow()).setValue("terminal_21");}else mc.stop();next();}
                case 40->{
                    int w=Boolean.getBoolean("modularmachinery.hypernetTerminalSmallTest")?854:1280;
                    if(mc.getWindow().getWidth()!=w||mc.getWindow().getHeight()!=(w==854?480:800)){
                        org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().getWindow());org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),w,w==854?480:800);return;}
                    ((EditBox)s.children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow()).setValue("terminal_20");next();}
                case 41->{if(!data.query().equals("terminal_20")||data.selected()==null)return;
                    require(data.selected().row().reason().endsWith("computation")&&data.network().offered()<100,"creative fixture must lack normal computation");
                    require(mc.player.isCreative()&&mc.player.hasPermissions(2),"real client not creative OP");hoverCreative=true;next();}
                case 42->{shot("hn-creative-01-hint.png");hoverCreative=false;click(324,150,2);next();}
                case 43->{if(data.selected()==null||data.selected().row().state()!=HyperNetResearchTerminal.State.COMPLETED||!data.task().isEmpty())return;
                    require(mc.getToasts().getToast(ResearchCompleteToast.class,"terminal_20")!=null,"creative completion notification missing");
                    shot("hn-creative-02-completed.png");((EditBox)s.children().stream().filter(c->c instanceof EditBox).findFirst().orElseThrow()).setValue("terminal_19");next();}
                case 44->{if(!data.query().equals("terminal_19")||data.selected()==null)return;
                    require(data.selected().row().state()==HyperNetResearchTerminal.State.AVAILABLE,"creative result failed prerequisite gate");
                    Modularmachinery.LOG.info("[MMC][research-creative-client] PASS: real OP middle-click packet, source tooltip, insufficient normal compute, normal core database commit/notification and dependent research; physical={}x{} GUI={}x{} language={}",mc.getWindow().getWidth(),mc.getWindow().getHeight(),s.width,s.height,mc.options.languageCode);mc.stop();next();}
                case 30->{
                    int targetWidth=Boolean.getBoolean("modularmachinery.hypernetTerminalSmallTest")?854:1280;
                    int targetHeight=targetWidth==854?480:800;
                    if(mc.getWindow().getWidth()!=targetWidth||mc.getWindow().getHeight()!=targetHeight){
                        org.lwjgl.glfw.GLFW.glfwRestoreWindow(mc.getWindow().getWindow());
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),targetWidth,targetHeight);return;
                    }
                    if(!data.task().isEmpty()||data.network().consumed()!=0||!data.network().online())return;
                    require(data.network().offered()>0&&data.network().connections()==4,"idle no-task stats disappeared");hoverNetwork=true;next();}
                case 31->{shot("hn-network-01-tooltip.png");hoverNetwork=false;mc.player.connection.sendCommand("hnnet remove");next();}
                case 32->{if(data.network().online()||!s.getMenu().slots.get(36).getItem().isEmpty())return;offline(data);shot("hn-network-02-card-removed.png");mc.player.connection.sendCommand("hnnet restore");next();}
                case 33->{if(!data.network().online())return;require(data.network().offered()>0,"card recovery retained zero supply");shot("hn-network-03-reconnected.png");mc.player.connection.sendCommand("hnnet stop");next();}
                case 34->{if(data.network().online())return;offline(data);shot("hn-network-04-center-stopped.png");mc.player.connection.sendCommand("hnnet start");next();}
                case 35->{if(!data.network().online()||data.network().connections()!=4)return;require(data.network().offered()>0,"center recovery missing supply");
                    shot("hn-network-05-center-recovered.png");
                    mc.player.connection.sendCommand("hnnet blank");next();}
                case 36->{var card=s.getMenu().slots.get(36).getItem();if(data.network().online()||card.isEmpty()||HyperNetCardInteractions.isBoundCard(card))return;
                    offline(data);require(card.is(RegistryItems.HYPERNET_CONNECT_CARD.get())&&card.getMaxStackSize()==1,"blank dedicated card");
                    shot("hn-network-06-invalid-card.png");mc.player.connection.sendCommand("hnnet restore");next();}
                case 37->{if(!data.network().online()||!HyperNetCardInteractions.isBoundCard(s.getMenu().slots.get(36).getItem()))return;hoverCard=true;next();}
                case 38->{shot("hn-network-07-card-tooltip.png");hoverCard=false;
                    Modularmachinery.LOG.info("[MMC][research-network-client] PASS: source FLOPS threshold/decimal formatting, 15-tick snapshots, idle supply and 4 connected peers, research consumption and delivery, tooltip, card disconnect/reconnect, center stop/recovery, dedicated card model and single-stack bound/empty/invalid states, physical={}x{} GUI={}x{} language={}; inspect PNGs separately",mc.getWindow().getWidth(),mc.getWindow().getHeight(),s.width,s.height,mc.options.languageCode);mc.stop();next();}
                case 15->{if(!data.query().equals("terminal_21")||data.total()!=1)return;require(data.selected().row().state()==HyperNetResearchTerminal.State.AVAILABLE,"closed-menu task admission");click(324,150,0);next();}
                case 16->{if(!data.task().equals("terminal_21")||new java.math.BigDecimal(data.taskProgress()).signum()==0)return;mc.player.connection.sendCommand("hntermclose");next();}
            }
        }catch(Throwable error){shot("hn-terminal-FAIL-"+stage+".png");Modularmachinery.LOG.error("[MMC][hypernet-terminal-client] FAIL stage "+stage,error);mc.stop();}
    }
    private static void checkIcon(ItemStack stack){require(stack.is(Items.POTION)&&stack.getCount()==1&&stack.getTag()!=null&&stack.getTag().getInt("CustomPotionColor")==3368703&&stack.getTag().getInt("CustomModelData")==17&&stack.isEnchanted(),"full item preview lost NBT");}
    private static void offline(HyperNetResearchTerminal.Snapshot data){
        require(data.network().consumed()==0&&data.network().offered()==0&&data.network().delivered()==0&&data.network().connections()==0,"offline snapshot retained another network's values");
    }
    // Inspect the vanilla toast queue and visible entries only in this disposable fixture.
    private static int noticeCount(String id)throws Exception{
        var manager=Minecraft.getInstance().getToasts();int count=0;
        for(String name:List.of("visible","queued")){
            var field=manager.getClass().getDeclaredField(name);field.setAccessible(true);
            for(var entry:(Iterable<?>)field.get(manager)){
                Object toast=entry;
                if(!(toast instanceof net.minecraft.client.gui.components.toasts.Toast)){
                    for(var f:entry.getClass().getDeclaredFields())if(net.minecraft.client.gui.components.toasts.Toast.class.isAssignableFrom(f.getType())){f.setAccessible(true);toast=f.get(entry);break;}
                }
                if(toast instanceof ResearchCompleteToast t&&t.getToken().equals(id))count++;
            }
        }
        return count;
    }
    private static void next(){stage++;waiting=0;Modularmachinery.LOG.info("[MMC][hypernet-terminal-client] stage {}",stage);}
    private static void click(double x,double y,int button){var s=(ScreenHyperNetResearch)Minecraft.getInstance().screen;
        float fit=Math.min(1,Math.min((s.width-4)/350F,(s.height-4)/250F));double px=s.width/2D+(s.getGuiLeft()+x-s.width/2D)*fit,py=s.height/2D+(s.getGuiTop()+y-s.height/2D)*fit;
        s.mouseClicked(px,py,button);s.mouseReleased(px,py,button);}
    private static void shot(String file){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),c->{});}
}
