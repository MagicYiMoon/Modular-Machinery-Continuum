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

/** Opt-in real widget and integrated-server verification in an isolated world. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualEditorResearchGate {
    private static final List<Runnable> steps=new ArrayList<>();private static int ticks,index,waiting;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.editorResearchGateVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        try{
            if(steps.isEmpty()){if(mc.screen!=null)return;plan();}
            if(index>0&&index<steps.size()-1&&!(mc.screen instanceof ScreenMachineEditor)){require(++waiting<1000,"editor open timeout");return;}
            if(mc.screen instanceof ScreenMachineEditor s&&(s.getMenu().saves().pending()||busy(s))){require(++waiting<1000,"save/query timeout");return;}
            waiting=0;if(++ticks%12!=0)return;if(index<steps.size())steps.get(index++).run();
        }catch(Throwable failure){Modularmachinery.LOG.error("[MMC][research-gate-client] FAIL step "+index,failure);mc.stop();}
    }
    private static boolean busy(ScreenMachineEditor s){for(var w:s.children())if(w instanceof EditBox b&&b.visible&&b.getMessage().getString().equals("搜索研究门槛"))return !b.active;return false;}
    private static void add(Runnable r){steps.add(r);}
    private static void plan(){
        add(()->Minecraft.getInstance().player.connection.sendCommand("modularmachinery gui"));
        if(Boolean.getBoolean("modularmachinery.editorViewportVisualTest"))viewportPlan();
        add(()->{select();click(420,27);});
        add(()->{bounds();shot("research-gate-01-types.png");press("研究门槛");});
        add(()->{bounds();require(!button("保存").active,"empty gate save enabled");shot("research-gate-02-list.png");pressRow("研究 00");press("→");});
        add(()->{pressRow("研究 08");press("已选（2）");});
        add(()->{bounds();shot("research-gate-03-selected.png");require(rows()==2,"cross-page selection lost");field().setValue("gate_08");press("搜索");require(rows()==1,"ID search failed");press("取消");require(requirements().size()==2,"cancel saved a gate");click(420,27);press("研究门槛");});
        add(()->{field().setValue("研究 03");press("搜索");});
        add(()->{require(rows()==1,"name search failed");pressRow("研究 03");Minecraft.getInstance().options.guiScale().set(2);Minecraft.getInstance().resizeDisplay();});
        add(()->{require(field().getValue().equals("研究 03"),"resize lost search");press("已选（1）");bounds();});
        add(()->{shot("research-gate-04-resize.png");Minecraft.getInstance().options.guiScale().set(3);Minecraft.getInstance().resizeDisplay();});
        add(()->{bounds();press("保存");require(screen().getMenu().saves().pending()&&!button("保存").active&&!field().active,"pending controls active");});
        add(()->{require(requirements().size()==3&&requirements().get(2).getAsJsonObject().getAsJsonArray("research").get(0).getAsString().equals("gate_03"),"authoritative gate save failed");click(310,48);});
        add(()->{press("已选（2）");});
        add(()->{require(row("[缺失]").getMessage().getString().contains("gate_removed"),"missing ID not displayed");bounds();shot("research-gate-05-missing.png");pressRow("[缺失]");press("保存");});
        add(()->{require(requirements().get(0).getAsJsonObject().getAsJsonArray("research").size()==1,"missing ID removal not saved");click(420,119);require(screen().children().stream().noneMatch(w->w instanceof Button b&&b.visible&&b.getMessage().getString().equals("研究门槛")),"output gate entry visible");press("取消");});
        add(()->{bounds();shot("research-gate-06-saved.png");press("导入模板");press("追加需求（0）");click(420,27);press("研究门槛");});
        add(()->{field().setValue("gate_05");press("搜索");});
        add(()->{pressRow("研究 05");press("保存");require(!screen().getMenu().saves().pending(),"local template draft sent a save");press("完成追加需求");require(button("追加需求（1）").visible,"template gate draft lost");});
        add(()->{shot("research-gate-07-template.png");press("保存并生成");});
        add(()->{var root=JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject();require(root.getAsJsonArray("adapterTemplates").asList().stream().anyMatch(e->e.getAsJsonObject().getAsJsonObject("source").getAsJsonArray("requirements").asList().stream().anyMatch(r->r.getAsJsonObject().has("research")&&r.getAsJsonObject().getAsJsonArray("research").get(0).getAsString().equals("gate_05"))),"template gate not saved");press("返回");});
        add(()->{Modularmachinery.LOG.info("[MMC][research-gate-client] PASS: real paged widgets, name/ID search, cross-page selection, cancel, authoritative create/edit, missing ID visibility/removal, resize retention, pending disable input-only entry, template child query/local draft and authoritative template generation; inspect PNGs separately");});
        add(()->Minecraft.getInstance().stop());
    }
    private static double hoverX,hoverY;private static boolean hover;
    @SubscribeEvent public static void render(net.minecraftforge.client.event.ScreenEvent.Render.Pre e){
        if(hover&&Boolean.getBoolean("modularmachinery.editorViewportVisualTest")&&e.getScreen() instanceof ScreenMachineEditor s){
            e.setCanceled(true);s.renderWithTooltip(e.getGuiGraphics(),(int)Math.round(s.editorScreenX(hoverX)),(int)Math.round(s.editorScreenY(hoverY)),e.getPartialTick());
        }
    }
    private static void viewportPlan(){
        add(()->{select();bounds();canvasBounds();});
        add(()->{shot("viewport-01-main.png");press("复制");bounds();var field=edit("新配方 ID");field.setValue("viewport_"+"long_id_".repeat(14));});
        add(()->{bounds();shot("viewport-02-copy-long-field.png");press("取消");click(310,140);press("取消");press("NBT 数据");});
        add(()->{bounds();multiline().setValue("{\n"+java.util.stream.IntStream.range(0,50).mapToObj(i->"  \"line"+i+"\": \""+"long text ".repeat(8)+"\"").collect(java.util.stream.Collectors.joining(",\n"))+"\n}");});
        add(()->{shot("viewport-03-nbt-clipping.png");var s=screen();var f=multiline();String before=f.getValue();resetScroll(f);double x=f.getX()+10,y=f.getY()+10;
            s.mouseClicked(s.editorScreenX(x),s.editorScreenY(y),0);s.mouseDragged(s.editorScreenX(x+150),s.editorScreenY(y+24),0,s.editorScreenX(x+150)-s.editorScreenX(x),s.editorScreenY(y+24)-s.editorScreenY(y));s.mouseReleased(s.editorScreenX(x+150),s.editorScreenY(y+24),0);
            require(s.charTyped('X',0)&&f.getValue().length()<before.length(),"scaled multiline drag selection failed");
            // Drag selection legitimately may remove most lines. Restore scrollable content
            // before independently checking the wheel path.
            f.setValue(before);resetScroll(f);require(f.getInnerHeight()>f.getHeight(),"wheel fixture not scrollable");
            s.mouseScrolled(s.editorScreenX(f.getX()+20),s.editorScreenY(f.getY()+20),-3);
            require(scrollAmount(f)>0,"scaled multiline wheel failed");});
        add(()->{bounds();shot("viewport-04-nbt-drag-scroll.png");press("取消");var name=edit("显示名称");name.setValue("&b视口测试名称");press("预览");});
        add(()->{bounds();shot("viewport-05-name-preview.png");press("返回");press("保存名称");});
        add(()->{require(JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject().getAsJsonArray("machines").asList().stream().anyMatch(e->e.getAsJsonObject().get("localizedName").getAsString().contains("视口测试名称")),"bottom name save failed");
            var s=screen();int before=machineScroll();s.mouseScrolled(s.editorScreenX(s.getGuiLeft()+40),s.editorScreenY(s.getGuiTop()+90),-1);require(machineScroll()>before,"machine list wheel missed scaled area");select();var b=button("复制");hoverX=b.getX()+b.getWidth()/2d;hoverY=b.getY()+b.getHeight()/2d;hover=true;});
        add(()->{shot("viewport-06-widget-tooltip.png");hover=false;bounds();Modularmachinery.LOG.info("[MMC][editor-viewport-client] PASS: visible whole main canvas and widgets, top copy/bottom name preview and authoritative save, scissored long EditBox and multiline NBT, real drag selection and wheel, scaled list wheel, tooltip rendered at transformed hover; inspect PNGs separately");});
    }
    private static EditBox edit(String label){return screen().children().stream().filter(w->w instanceof EditBox b&&b.visible&&b.getMessage().getString().equals(label)).map(w->(EditBox)w).findFirst().orElseThrow();}
    private static MultiLineEditBox multiline(){return screen().children().stream().filter(w->w instanceof MultiLineEditBox b&&b.visible).map(w->(MultiLineEditBox)w).findFirst().orElseThrow();}
    private static void resetScroll(AbstractScrollWidget widget){try{var f=AbstractScrollWidget.class.getDeclaredField("scrollAmount");f.setAccessible(true);f.setDouble(widget,0);}catch(Exception e){throw new RuntimeException(e);}}
    private static double scrollAmount(AbstractScrollWidget widget){try{var f=AbstractScrollWidget.class.getDeclaredField("scrollAmount");f.setAccessible(true);return f.getDouble(widget);}catch(Exception e){throw new RuntimeException(e);}}
    private static int machineScroll(){try{var f=ScreenMachineEditor.class.getDeclaredField("machineScroll");f.setAccessible(true);return f.getInt(screen());}catch(Exception e){throw new RuntimeException(e);}}
    private static void canvasBounds(){var s=screen();require(s.editorScreenX(s.getGuiLeft())>=0&&s.editorScreenY(s.getGuiTop())>=0&&s.editorScreenX(s.getGuiLeft()+440)<=s.width&&s.editorScreenY(s.getGuiTop()+260)<=s.height,"canvas cropped");require(s.editorScreenX(1)-s.editorScreenX(0)<=1.00001,"canvas enlarged");}
    private static ScreenMachineEditor screen(){require(Minecraft.getInstance().screen instanceof ScreenMachineEditor,"wrong screen");return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static void select(){var s=screen();var a=JsonParser.parseString(s.getMenu().snapshot()).getAsJsonObject().getAsJsonArray("machines");int at=-1;for(int i=0;i<a.size();i++)if(a.get(i).getAsJsonObject().get("name").getAsString().equals("000_gate_machine"))at=i;require(at>=0,"machine missing");try{var f=ScreenMachineEditor.class.getDeclaredField("machineScroll");f.setAccessible(true);f.setInt(s,at);}catch(Exception e){throw new RuntimeException(e);}click(40,30);}
    private static JsonArray requirements(){for(var m:JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject().getAsJsonArray("machines"))for(var r:m.getAsJsonObject().getAsJsonArray("recipes"))if(r.getAsJsonObject().get("name").getAsString().equals("000_gate_recipe"))return r.getAsJsonObject().getAsJsonArray("requirements");throw new AssertionError("recipe missing");}
    private static Button button(String name){return screen().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getMessage().getString().equals(name)).map(w->(Button)w).findFirst().orElseThrow(()->new AssertionError("button missing "+name));}
    private static Button row(String name){return screen().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getWidth()==310&&b.getMessage().getString().contains(name)).map(w->(Button)w).findFirst().orElseThrow(()->new AssertionError("row missing "+name));}
    private static void pressRow(String name){var b=row(name);require(b.active,"inactive row");click(b.getX()-screen().getGuiLeft()+b.getWidth()/2,b.getY()-screen().getGuiTop()+b.getHeight()/2);}
    private static long rows(){return screen().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getWidth()==310).count();}
    private static EditBox field(){return screen().children().stream().filter(w->w instanceof EditBox b&&b.visible&&b.getMessage().getString().equals("搜索研究门槛")).map(w->(EditBox)w).findFirst().orElseThrow();}
    private static void press(String name){var b=button(name);require(b.active,"inactive "+name);click(b.getX()-screen().getGuiLeft()+b.getWidth()/2,b.getY()-screen().getGuiTop()+b.getHeight()/2);}
    private static void click(int x,int y){var s=screen();s.mouseClicked(s.editorScreenX(s.getGuiLeft()+x),s.editorScreenY(s.getGuiTop()+y),0);s.mouseReleased(s.editorScreenX(s.getGuiLeft()+x),s.editorScreenY(s.getGuiTop()+y),0);}
    private static void bounds(){var s=screen();for(var child:s.children())if(child instanceof AbstractWidget w&&w.visible)require(s.editorScreenX(w.getX())>=-0.01&&s.editorScreenY(w.getY())>=-0.01&&s.editorScreenX(w.getX()+w.getWidth())<=s.width+0.01&&s.editorScreenY(w.getY()+w.getHeight())<=s.height+0.01,"outside screen "+w.getMessage().getString());}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][research-gate-client] {}",name));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
