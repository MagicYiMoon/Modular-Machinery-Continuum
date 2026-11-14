/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.ecalculator;

import appeng.core.definitions.AEBlocks;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.ecalculator.*;
import mcjty.theoneprobe.api.*;
import mcjty.theoneprobe.apiimpl.ProbeInfo;
import mcjty.theoneprobe.apiimpl.elements.ElementText;
import mcjty.theoneprobe.network.*;
import mcjty.theoneprobe.rendering.OverlayRenderer;
import net.minecraft.client.*;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Native TOP request/reply/renderer, with the existing disposable real-AE-task fixture. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ECalculatorTopClientVerification {
    private static boolean active;
    private static int ticks, frames;
    private static TileECalculatorController owner;
    private static BlockPos channel, target, busiest;
    private static ProbeInfo shown;
    public static boolean active(){return active;}
    public static void begin(TileECalculatorController controller, BlockPos channelPos){
        active=true;owner=controller;channel=channelPos;target=controller.getBlockPos();
        var mc=Minecraft.getInstance();mc.player.closeContainer();
        org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),1280,800);
        mc.options.guiScale().set(2);mc.resizeDisplay();
        mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();
        mcjty.theoneprobe.config.Config.isVisible.set(false);
        mc.getSingleplayerServer().execute(()->{
            ((TileECalculatorPart)owner.getLevel().getBlockEntity(owner.getBlockPos().east(14))).restoreBackend(new net.minecraft.nbt.CompoundTag());
            owner.refreshStructure();
            busiest=owner.parts().stream().filter(p->p.cpuAccess()!=null)
                    .max(Comparator.comparingInt(p->p.cpuAccess().monitorTasks().size())).orElseThrow().getBlockPos();
        });
    }
    public static void tick() throws Exception {
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        ticks++;
        if(ticks%10==0){
            var hit=new BlockHitResult(Vec3.atCenterOf(target),Direction.SOUTH,target,false);
            PacketHandler.INSTANCE.sendToServer(new PacketGetInfo(mc.level.dimension(),target,ProbeMode.NORMAL,hit,ItemStack.EMPTY));
        }
        shown=cached(target);
        switch(ticks){
            case 60 -> {require(has("offline")&&has("memory")&&has("cpu_time"),"offline host real TOP reply");shot("ec-top-01-host-offline-zh.png");target=busiest;shown=null;}
            case 100 -> {require(has("bound")&&has("paused")&&has("cpu_time"),"offline core retained tasks"); colors(shown);shot("ec-top-02-core-paused-zh.png");
                mc.getSingleplayerServer().execute(()->owner.getLevel().setBlockAndUpdate(channel.south(),AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState()));}
            case 190 -> {require(has("bound")&&(has("hyper")||has("normal")),"powered core real TOP tasks");shot("ec-top-03-core-online-zh.png");
                mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks();}
            case 230 -> {require(has("cpu_time"),"English core reply");shot("ec-top-04-core-en.png");target=owner.getBlockPos();shown=null;}
            case 270 -> {require(has("online")&&has("threads"),"host online reply");shot("ec-top-05-host-online-en.png");
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),854,480);mc.options.guiScale().set(2);mc.resizeDisplay();}
            case 310 -> {require(has("memory"),"small host reply");shot("ec-top-06-small-host.png");target=busiest;shown=null;}
            case 350 -> {require(has("cpu_time")&&frames>60,"native TOP renderer was not exercised");shot("ec-top-07-small-core.png");
                Modularmachinery.LOG.info("[MMC][ec-top-client] PASS: native TOP request/reply and renderer; real AE tasks paused/bound offline and running online, host long memory/thread totals, Chinese/English and 854x480; screenshots require inspection");
                Modularmachinery.LOG.info("[MMC][ec-top-client] OVERALL: PASS");}
            case 375 -> mc.stop();
        }
        if(ticks>700)throw new AssertionError("TOP fixture timeout");
    }
    @SubscribeEvent public static void render(RenderGuiEvent.Post event){
        if(!active||shown==null)return;
        var mc=Minecraft.getInstance();
        // Render the received native overlay at a stable position; no reconstructed mock labels or bars.
        require(shown.getWidth()+12<=mc.getWindow().getGuiScaledWidth()&&shown.getHeight()+12<=mc.getWindow().getGuiScaledHeight(),"TOP overlay outside window");
        OverlayRenderer.renderOverlay(mcjty.theoneprobe.config.Config.getDefaultOverlayStyle().copy().location(6,-1,6,-1),shown,event.getGuiGraphics());frames++;
    }
    @SuppressWarnings("unchecked") private static ProbeInfo cached(BlockPos pos)throws Exception{
        var f=OverlayRenderer.class.getDeclaredField("cachedInfo");f.setAccessible(true);
        var map=(Map<org.apache.commons.lang3.tuple.Pair<?,?>,org.apache.commons.lang3.tuple.Pair<Long,ProbeInfo>>)f.get(null);
        var value=map.get(org.apache.commons.lang3.tuple.Pair.of(Minecraft.getInstance().level.dimension(),pos));
        return value==null?null:value.getRight();
    }
    private static boolean has(String key){return shown!=null&&has(shown,"top.modularmachinery.ec."+key);}
    private static void colors(IProbeInfo info) throws Exception {
        boolean green=false, purple=false;
        colorsNested(info);
        var texts=new ArrayList<Component>(); collect(info,texts);
        for(var text:texts) {
            if(text.getContents() instanceof TranslatableContents t && t.getKey().equals("top.modularmachinery.ec.bound"))
                green=text.getStyle().getColor()!=null&&text.getStyle().getColor().getValue()==net.minecraft.ChatFormatting.GREEN.getColor();
            if(text.getContents() instanceof TranslatableContents t && t.getKey().equals("top.modularmachinery.ec.operations"))
                for(var arg:t.getArgs())if(arg instanceof Component c&&c.getStyle().getColor()!=null&&c.getStyle().getColor().getValue()==net.minecraft.ChatFormatting.DARK_PURPLE.getColor())purple=true;
        }
        require(green&&purple,"native TOP reply lost binding/statistic colors");
    }
    private static void colorsNested(IProbeInfo info) throws Exception {
        for(var e:info.getElements()) {
            if(e instanceof IProbeInfo nested)colorsNested(nested);
            if(e instanceof ElementText){var tf=e.getClass().getDeclaredField("text");tf.setAccessible(true);
                if(((Component)tf.get(e)).getContents() instanceof TranslatableContents t&&t.getKey().startsWith("top.modularmachinery.ec.")){
                    var f=e.getClass().getDeclaredField("legacy");f.setAccessible(true);require(!f.getBoolean(e),"legacy EC text renderer");}}
        }
    }
    private static void collect(IProbeInfo info,List<Component> texts)throws Exception {
        for(var e:info.getElements()){
            if(e instanceof IProbeInfo nested)collect(nested,texts);
            if(e instanceof ElementText){var f=e.getClass().getDeclaredField("text");f.setAccessible(true);texts.add((Component)f.get(e));}
        }
    }
    private static boolean has(IProbeInfo info,String key){
        for(var e:info.getElements()){
            if(e instanceof IProbeInfo nested&&has(nested,key))return true;
            if(e instanceof ElementText)try{var f=e.getClass().getDeclaredField("text");f.setAccessible(true);
                if(((Component)f.get(e)).getContents() instanceof TranslatableContents t&&t.getKey().equals(key))return true;
            }catch(ReflectiveOperationException failure){throw new AssertionError(failure);}
        }return false;
    }
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][ec-top-client] {}",r.getString()));}
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
