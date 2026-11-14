/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.block.*;
import hellfirepvp.modularmachinery.common.block.prop.*;
import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.menu.*;
import hellfirepvp.modularmachinery.common.registry.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.commands.Commands;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.*;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import java.util.*;

/** Disposable client only. Uses real server commands, controller menus, native page buttons and paid hardware. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class HyperNetMonitorClientVerification {
    private static final BlockPos C=new BlockPos(0,100,0),P=C.east(4);
    private static final List<TileController> nodes=new ArrayList<>();private static TileController center,processor;
    private static ServerLevel world;private static ServerPlayer actor;private static boolean connected,automatic=true,centerPower=true;
    private static int stage,ticks,waiting;
    private static boolean enabled(){return Boolean.getBoolean("modularmachinery.hypernetMonitorVisualTest");}
    @SubscribeEvent public static void commands(RegisterCommandsEvent event){
        if(!enabled())return;var root=Commands.literal("hnmon").requires(s->s.hasPermission(2));
        for(String action:List.of("setup","center","consumer","unbound","rebind","missing","stop","restore","deny","allow","reload"))
            root.then(Commands.literal(action).executes(c->{action(c.getSource().getPlayerOrException(),action);return 1;}));
        event.getDispatcher().register(root);
    }
    private static TileController build(BlockPos pos,String name,boolean factory){
        var machine=MachineReloadListener.getLoader().findByName(name);require(machine!=null,"missing startup machine "+name);
        for(var part:machine.getPattern().getParts()){
            var target=pos.offset(part.x(),part.y(),part.z());
            // Sample machines use vertical single-state parts; no source-pack machinery or chunks are copied.
            var block=net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(new net.minecraft.resources.ResourceLocation(part.elements()));
            require(block!=null&&block!=Blocks.AIR,"missing sample part "+part.elements());world.setBlockAndUpdate(target,block.defaultBlockState());
        }
        var item=DedicatedControllers.stackOrGeneric(name,factory);var block=((BlockItem)item.getItem()).getBlock();
        world.setBlockAndUpdate(pos,block.defaultBlockState().setValue(BlockController.FACING,Direction.NORTH));
        var owner=(TileController)world.getBlockEntity(pos);owner.setOwner(actor.getUUID());owner.setMachineName(name);
        require(MachineFormation.tryForm(world,pos,Direction.NORTH,machine,MachineReloadListener.getLoader()).formed(),"sample formation");
        owner.setFormed(true);owner.acknowledgeStructureCheck();return owner;
    }
    private static void action(ServerPlayer player,String action){
        switch(action){
            case "setup"->{
                actor=player;world=player.serverLevel();nodes.clear();world.setDayTime(6000);player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
                player.getAbilities().flying=true;player.onUpdateAbilities();
                // Initial client abilities packets can cancel flight; a real floor keeps the range anchor stable.
                for(int x=-3;x<=20;x++)for(int z=-4;z<=12;z++)world.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState());
                center=build(C,"hn_sample_center",true);processor=build(P,"hn_sample_processor",true);
                // The normal bus's three real slots hold CPU, RAM and GPU; input bus is separated by a casing.
                var inventory=(net.minecraftforge.items.IItemHandlerModifiable)((TileUpgradeBus)world.getBlockEntity(P.above(2))).getUpgradeInventory();
                inventory.setStackInSlot(0,new ItemStack(Items.DIAMOND,2));inventory.setStackInSlot(1,new ItemStack(Items.REDSTONE,2));inventory.setStackInSlot(2,new ItemStack(Items.EMERALD));
                for(int i=0;i<7;i++)nodes.add(build(new BlockPos(8+i/3*4,100,i%3*4),"hn_sample_node",false));
                player.teleportTo(world,.5,100,-2,0,0);connected=false;
            }
            case "center","consumer"->{
                var owner=action.equals("center")?center:nodes.get(0);var pos=owner.getBlockPos();player.teleportTo(world,pos.getX()+.5,pos.getY(),pos.getZ()-2,0,0);
                NetworkHooks.openScreen(player,new SimpleMenuProvider((id,inv,p)->owner instanceof TileFactoryController f
                        ?MenuFactoryController.forController(id,inv,f):MenuMachineController.forController(id,inv,owner),Component.literal("HyperNet test")),pos);
            }
            case "unbound"->{automatic=false;nodes.get(0).getHyperNetNode().disconnect();}
            case "rebind"->{automatic=true;nodes.get(0).getHyperNetNode().connect(C,player.getUUID());}
            case "missing"->((net.minecraftforge.items.IItemHandlerModifiable)((TileUpgradeBus)world.getBlockEntity(P.above(2))).getUpgradeInventory()).setStackInSlot(1,ItemStack.EMPTY);
            case "stop"->{centerPower=false;var hatch=(TileEnergyInputHatch)world.getBlockEntity(C.below());while(hatch.getStoredEnergy()>0)hatch.consumeInternal(Integer.MAX_VALUE,false);}
            case "restore"->{centerPower=true;((net.minecraftforge.items.IItemHandlerModifiable)((TileUpgradeBus)world.getBlockEntity(P.above(2))).getUpgradeInventory()).setStackInSlot(1,new ItemStack(Items.REDSTONE,2));}
            case "deny"->nodes.get(0).setOwner(UUID.randomUUID());
            case "allow"->nodes.get(0).setOwner(player.getUUID());
            case "reload"->MachineReloadListener.reloadNow();
        }
    }
    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event){
        if(!enabled()||world==null||event.phase!=TickEvent.Phase.END)return;
        for(var owner:all())if((owner!=center||centerPower)&&world.getBlockEntity(owner.getBlockPos().below()) instanceof TileEnergyInputHatch hatch)hatch.produceInternal(1000,false);
        // One actual consumer recipe, alongside the direct 1.25-point API load.
        var input=(net.minecraftforge.items.IItemHandlerModifiable)((TileItemBus)world.getBlockEntity(nodes.get(0).getBlockPos().above(2))).getInventory();
        if(input.getStackInSlot(0).getCount()<32)input.setStackInSlot(0,new ItemStack(Items.SAND,64));
        if(!center.getHyperNetNode().working()||!processor.getHyperNetNode().working()){
            if(world.getGameTime()%200==0)Modularmachinery.LOG.info("[MMC][hypernet-monitor-client] pending hardware center={} processor={}",
                    HyperNetMonitor.capture(center,actor,0).local(),HyperNetMonitor.capture(processor,actor,0).local());
            return;
        }
        if(!connected){
            require(processor.getHyperNetNode().connect(C,actor.getUUID())==HyperNetNode.ConnectResult.SUCCESS,"processor connect");
            for(var node:nodes)require(node.getHyperNetNode().connect(C,actor.getUUID())==HyperNetNode.ConnectResult.SUCCESS,"consumer connect");connected=true;
        }
        if(automatic)nodes.get(0).getHyperNetNode().request(1.25,false);
    }
    private static List<TileController> all(){var result=new ArrayList<TileController>();result.add(center);result.add(processor);result.addAll(nodes);return result;}
    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event){
        if(!enabled()||event.phase!=TickEvent.Phase.END)return;var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        try{
            require(++waiting<2400,"timeout stage "+stage);if(++ticks%20!=0)return;
            if(waiting%200==0)Modularmachinery.LOG.info("[MMC][hypernet-monitor-client] waiting stage {} screen={} menu={} pos={}",stage,
                    mc.screen==null?"none":mc.screen.getClass().getSimpleName(),mc.player.containerMenu.getClass().getSimpleName(),mc.player.blockPosition());
            switch(stage){
                case 0->{command("setup");next();}
                case 1->{if(ticks<80)return;command("center");next();}
                case 2->{if(!(mc.screen instanceof ScreenFactoryController))return;bounds();shot("hn-monitor-01-entry.png");pressKey("button_short");next();}
                case 3->{if(!ready()||monitor().getMenu().snapshot().total()!=8||monitor().getMenu().snapshot().center().consumed()!=3.25
                            ||monitor().getMenu().snapshot().center().demanded()!=2||monitor().getMenu().snapshot().center().demandConsumers()!=1)return;
                    bounds();shot("hn-monitor-02-center.png");click(20,43);next();}
                case 4->{shot("hn-monitor-03-processor.png");scroll(-1);next();}
                case 5->{if(!ready()||monitor().getMenu().snapshot().page()!=1)return;bounds();shot("hn-monitor-04-page2.png");scroll(1);next();}
                case 6->{if(!ready()||monitor().getMenu().snapshot().page()!=0)return;press("→");next();}
                case 7->{if(!ready()||monitor().getMenu().snapshot().page()!=1)return;next();}
                case 8->{bounds();shot("hn-monitor-05-layout.png");pressKey("back");next();}
                case 9->{if(!(mc.screen instanceof ScreenFactoryController))return;command("consumer");next();}
                case 10->{if(!(mc.screen instanceof ScreenMachineController))return;bounds();shot("hn-monitor-06-ordinary.png");pressKey("button_short");next();}
                case 11->{if(!ready()||monitor().getMenu().snapshot().local().demanded()!=2)return;pressKey("local");next();}
                case 12->{shot("hn-monitor-07-consumer.png");command("unbound");next();}
                case 13->{if(!ready()||monitor().getMenu().snapshot().local().fault()!=HyperNetMonitor.Fault.UNBOUND)return;
                    shot("hn-monitor-08-unbound.png");command("rebind");command("missing");next();}
                case 14->{if(!ready()||monitor().getMenu().snapshot().nodes().stream().noneMatch(n->n.fault()==HyperNetMonitor.Fault.MODULES_MISSING))return;
                    click(20,43);next();}
                case 15->{shot("hn-monitor-09-missing-modules.png");command("stop");next();}
                case 16->{if(!ready()||monitor().getMenu().snapshot().center()==null||monitor().getMenu().snapshot().center().fault()!=HyperNetMonitor.Fault.CORE_STOPPED)return;
                    pressKey("center");next();}
                case 17->{shot("hn-monitor-10-stopped-center.png");command("restore");next();}
                case 18->{if(!ready()||!monitor().getMenu().snapshot().center().online())return;command("reload");next();}
                case 19->{if(mc.screen instanceof ScreenHyperNetMonitor)return;
                    Modularmachinery.LOG.info("[MMC][hypernet-monitor-client] OVERALL: PASS: native ordinary/dedicated factory entry, actual recipe demand 2 in one thread plus direct API 1.25 load, paid 3.25-point samples, two-page eight-peer roster, selection/back/arrows/scroll, language={} physical={}x{} GUI={}x{}, unbound/missing-module/stopped-core views, actual reload closes old window; inspect screenshots separately",
                            mc.options.languageCode,mc.getWindow().getWidth(),mc.getWindow().getHeight(),mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());mc.stop();next();}
            }
        }catch(Throwable failure){shot("hn-monitor-FAIL-stage-"+stage+".png");Modularmachinery.LOG.error("[MMC][hypernet-monitor-client] FAIL stage "+stage,failure);mc.stop();}
    }
    private static void next(){stage++;waiting=0;Modularmachinery.LOG.info("[MMC][hypernet-monitor-client] stage {}",stage);}
    private static boolean ready(){return Minecraft.getInstance().screen instanceof ScreenHyperNetMonitor s&&s.getMenu().snapshot()!=null;}
    private static ScreenHyperNetMonitor monitor(){require(ready(),"monitor not ready");return (ScreenHyperNetMonitor)Minecraft.getInstance().screen;}
    private static void command(String s){Minecraft.getInstance().player.connection.sendCommand("hnmon "+s);}
    private static void pressKey(String key){press(Component.translatable("gui.modularmachinery.hypernet."+key).getString());}
    private static void press(String text){var screen=Minecraft.getInstance().screen;var b=screen.children().stream().filter(c->c instanceof Button w&&w.visible&&w.active&&w.getMessage().getString().equals(text)).map(c->(Button)c).findFirst().orElseThrow(()->new AssertionError("missing button "+text));
        screen.mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d,0);screen.mouseReleased(b.getX()+1,b.getY()+1,0);}
    private static void scroll(double delta){var s=monitor();require(s.mouseScrolled(s.getGuiLeft()+30,s.getGuiTop()+60,delta),"list scroll");}
    private static void click(int x,int y){var s=monitor();s.mouseClicked(s.getGuiLeft()+x,s.getGuiTop()+y,0);s.mouseReleased(s.getGuiLeft()+x,s.getGuiTop()+y,0);}
    private static void bounds(){var s=Minecraft.getInstance().screen;for(var child:s.children())if(child instanceof AbstractWidget w&&w.visible)
        require(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=s.width&&w.getY()+w.getHeight()<=s.height,"widget outside GUI");}
    private static void shot(String file){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,file,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][hypernet-monitor-client] {}",file));}
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
