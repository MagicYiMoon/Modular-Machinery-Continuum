/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Opt-in real widgets and save acknowledgements, isolated flat world only. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualEditorHyperNet {
    private static final List<Runnable> steps=new ArrayList<>();private static int ticks,index,waiting;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.editorHyperNetVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        try{
            if(steps.isEmpty())plan();
            if(mc.screen instanceof ScreenMachineEditor s&&s.getMenu().saves().pending()){require(++waiting<1200,"save timed out");return;}
            if(++ticks%12!=0)return;
            if(index<steps.size())steps.get(index++).run();
        }catch(Throwable failure){Modularmachinery.LOG.error("[MMC][hypernet-editor-client] FAIL step "+index,failure);mc.stop();}
    }
    private static void add(Runnable action){steps.add(action);}
    private static void plan(){
        add(()->Minecraft.getInstance().player.connection.sendCommand("modularmachinery gui"));
        add(()->select("000_hypernet_processor"));
        add(()->{shot("hn-editor-01-main.png");press("算力/说明");});
        add(()->{bounds();field("显示阶数").setValue("&bT3");field("说明 1").setValue("&6液冷数据处理器");field("说明 2").setValue("阶数只作提示，能力由参数决定");press("预览");});
        add(()->shot("hn-editor-02-tier-preview.png"));
        add(()->{press("返回输入");press("网络");});
        add(()->{bounds();shot("hn-editor-03-processor.png");press("模块");});
        add(()->{bounds();field("容量").setValue("12.5");field("满载 FE/t").setValue("40");shot("hn-editor-04-modules.png");press("模块等级与说明");});
        add(()->{field("显示阶数").setValue("&eT2");field("说明 1").setValue("&a模块说明");press("预览");});
        add(()->shot("hn-editor-05-module-preview.png"));
        add(()->{press("返回模块");press("散热");field("过热阈值 HU").setValue("0");press("保存");});
        add(()->{require(button("散热").visible,"invalid save closed form");field("过热阈值 HU").setValue("abc");Minecraft.getInstance().options.guiScale().set(2);Minecraft.getInstance().resizeDisplay();});
        add(()->{require(field("过热阈值 HU").getValue().equals("abc"),"resize lost invalid numeric draft");press("保存");require(!screen().getMenu().saves().pending(),"invalid numeric draft sent");field("过热阈值 HU").setValue("1200");field("散热参数").setValue("7");Minecraft.getInstance().options.guiScale().set(3);Minecraft.getInstance().resizeDisplay();});
        add(()->{shot("hn-editor-06-thermal.png");press("保存");require(screen().getMenu().saves().pending()&&!field("过热阈值 HU").active&&!button("保存").active,"pending config editable");});
        add(()->{var settings=config("000_hypernet_processor");require(settings.getAsJsonObject("display-info").get("tier").getAsString().equals("&bT3"),"tier save missing");require(settings.getAsJsonObject("hypernet").getAsJsonObject("thermal").get("overheat-threshold").getAsInt()==1200,"thermal save missing");
            var module=settings.getAsJsonObject("hypernet").getAsJsonArray("modules").get(0).getAsJsonObject();require(module.get("capacity").getAsDouble()==12.5&&module.get("energy").getAsLong()==40&&module.getAsJsonObject("display-info").get("tier").getAsString().equals("&eT2"),"module data missing");press("算力/说明");});
        add(()->{field("说明 3").setValue("resize draft");Minecraft.getInstance().options.guiScale().set(2);Minecraft.getInstance().resizeDisplay();});
        add(()->{bounds();require(field("说明 3").getValue().equals("resize draft"),"resize lost draft");shot("hn-editor-07-scale2.png");press("返回");});
        add(()->{Minecraft.getInstance().options.guiScale().set(3);Minecraft.getInstance().resizeDisplay();select("000_hypernet_center");press("算力/说明");press("网络");});
        add(()->{press("公平调度：关");bounds();field("承载算力 /t").setValue("6000");field("连接数上限").setValue("32");shot("hn-editor-08-center.png");press("电路维修");});
        add(()->{field("耐久容量").setValue("15000");shot("hn-editor-09-circuit.png");press("保存");});
        add(()->{require(config("000_hypernet_center").getAsJsonObject("hypernet").get("fair-scheduling").getAsBoolean(),"fairness save missing");require(config("000_hypernet_center").getAsJsonObject("hypernet").getAsJsonObject("circuit").get("capacity").getAsInt()==15000,"circuit save missing");select("000_hypernet_processor");click(420,27);press("资源／算力");});
        add(()->{if(button("持续算力").active)press("持续算力");field("算力 /t").setValue("1.25");bounds();shot("hn-editor-10-computation.png");press("算力不足：暂停并保留进度");press("保存");});
        add(()->{require(lastRequirement().get("triggerFailure").getAsBoolean(),"failure policy save missing");click(420,27);press("资源／算力");press("主动冷却");field("完成时冷却 HU").setValue("20");});
        add(()->{shot("hn-editor-11-cooling.png");press("保存");});
        add(()->{require(lastRequirement().get("type").getAsString().endsWith("hypernet_cooling"),"cooling save missing");click(420,27);press("资源／算力");press("电路维修");field("完成时修复耐久").setValue("100");});
        add(()->{shot("hn-editor-12-repair.png");press("保存");});
        add(()->{require(lastRequirement().get("type").getAsString().endsWith("hypernet_circuit_repair"),"repair save missing");shot("hn-editor-13-saved.png");Modularmachinery.LOG.info("[MMC][hypernet-editor-client] PASS: real widgets, tier and module info preview, center/processor/module/thermal/circuit forms, rejected invalid save retained draft, authoritative saves, computation failure/cooling/repair creation, modal and GUI scales 3/2 resize; inspect PNGs separately");});
        add(()->Minecraft.getInstance().stop());
    }
    private static ScreenMachineEditor screen(){require(Minecraft.getInstance().screen instanceof ScreenMachineEditor,"wrong screen");return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static void select(String name){
        var s=screen();var array=JsonParser.parseString(s.getMenu().snapshot()).getAsJsonObject().getAsJsonArray("machines");int at=-1;
        for(int i=0;i<array.size();i++)if(array.get(i).getAsJsonObject().get("name").getAsString().equals(name))at=i;
        require(at>=0,"missing machine");
        try{var scroll=ScreenMachineEditor.class.getDeclaredField("machineScroll");scroll.setAccessible(true);scroll.setInt(s,at);}catch(Exception e){throw new RuntimeException(e);}
        click(40,30);
    }
    private static JsonObject config(String name){var root=JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject();for(var e:root.getAsJsonArray("machines")){var j=e.getAsJsonObject();if(j.get("name").getAsString().equals(name))return j.getAsJsonObject("hypernetSettings");}throw new AssertionError("missing config");}
    private static JsonObject lastRequirement(){var root=JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject();for(var e:root.getAsJsonArray("machines"))for(var r:e.getAsJsonObject().getAsJsonArray("recipes")){var j=r.getAsJsonObject();if(j.get("name").getAsString().equals("000_hypernet_visual_recipe")){var a=j.getAsJsonArray("requirements");return a.get(a.size()-1).getAsJsonObject();}}throw new AssertionError("missing recipe");}
    private static Button button(String text){return screen().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow(()->new AssertionError("button missing: "+text));}
    private static EditBox field(String text){return screen().children().stream().filter(w->w instanceof EditBox b&&b.visible&&b.getMessage().getString().equals(text)).map(w->(EditBox)w).findFirst().orElseThrow(()->new AssertionError("field missing: "+text));}
    private static void press(String text){var b=button(text);require(b.active,"button disabled: "+text);screen().mouseClicked(screen().editorScreenX(b.getX()+b.getWidth()/2d),screen().editorScreenY(b.getY()+b.getHeight()/2d),0);screen().mouseReleased(screen().editorScreenX(b.getX()+1),screen().editorScreenY(b.getY()+1),0);}
    private static void click(int x,int y){var s=screen();s.mouseClicked(s.editorScreenX(s.getGuiLeft()+x),s.editorScreenY(s.getGuiTop()+y),0);s.mouseReleased(s.editorScreenX(s.getGuiLeft()+x),s.editorScreenY(s.getGuiTop()+y),0);}
    private static void bounds(){var s=screen();for(var child:s.children())if(child instanceof AbstractWidget w&&w.visible)require(s.editorScreenX(w.getX())>=0 && s.editorScreenY(w.getY())>=0 && s.editorScreenX(w.getX()+w.getWidth())<=s.width && s.editorScreenY(w.getY()+w.getHeight())<=s.height,"widget outside screen");}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][hypernet-editor-client] {}",name));}
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
