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
/** Actual multiline NBT controls and server acknowledgements, only in a disposable visual fixture. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualEditorNbt {
    private static int ticks,waiting,captureTicks;
    private static String capture;
    private static boolean showLongTooltip;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END || !Boolean.getBoolean("modularmachinery.editorNbtVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
        if(captureTicks>0 && --captureTicks==0){String name=capture;capture=null;Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][nbt-editor-client] {}: {}",name,r.getString()));}
        ticks++;
        try{switch(ticks){
            case 20 -> mc.player.connection.sendCommand("modularmachinery gui");
            case 60 -> {screen();require(button("NBT 数据").active,"NBT button disabled");shot("nbt-01-main.png");}
            case 70 -> press("NBT 数据");
            case 80 -> {bounds();screen().mouseClicked(field().getX()+10,field().getY()+10,0);screen().keyPressed(69,18,0);require(Minecraft.getInstance().screen==screen(),"inventory shortcut closed NBT editor");screen().charTyped('e',0);require(field().getValue().contains("e"),"typing did not reach multiline field");require(button("匹配条件 · nbt").visible,"input tab");field().setValue("{\n  \"quality\": \">=3\",\n  \"name\": \"Coal|Carbon\"\n}");press("预览");shot("nbt-02-input-json.png");}
            case 90 -> {press("显示数据");field().setValue("{\"display\":{\"Name\":\"Demo\"}}");shot("nbt-03-display.png");}
            case 100 -> {press("匹配条件 · nbt");require(field().getValue().contains("Coal|Carbon"),"tab lost data");press("保存");require(screen().getMenu().saves().pending() && !button("保存").active,"pending guard");}
            case 130 -> {if(pending())return;require(req(0).getAsJsonObject("nbt").get("quality").getAsString().equals(">=3") && req(0).has("nbt-display"),"input/display ack");click(320,138);}
            case 150 -> {bounds();require(button("实际输出 · nbt").visible,"output tab");press("格式：JSON 对象");field().setValue("{quality:7}");press("显示数据");press("格式：JSON 对象");field().setValue("{quality:99}");press("实际输出 · nbt");press("预览");shot("nbt-04-output-pattern.png");}
            case 160 -> press("保存");
            case 190 -> {if(pending())return;require(req(1).get("nbt").getAsString().equals("{quality:7}") && req(1).get("nbt-display").getAsString().equals("{quality:99}"),"output pattern save");click(320,138);press("显示数据");press("清除");shot("nbt-05-clear-display.png");}
            case 200 -> press("保存");
            case 230 -> {if(pending())return;require(!req(1).has("nbt-display") && req(1).get("nbt").isJsonPrimitive(),"clear or source format");click(320,68);field().setValue("{\"purity\":\">=2.0d\"}");press("预览");shot("nbt-06-fluid-input.png");}
            case 240 -> press("保存");
            case 270 -> {if(pending())return;require(req(2).getAsJsonObject("nbt").get("purity").getAsString().equals(">=2.0d"),"fluid save");click(320,160);press("格式：JSON 对象");field().setValue("{grade:5}");press("显示数据");field().setValue("{\"grade\":88}");press("实际输出 · nbt");press("预览");shot("nbt-07-fluid-output.png");}
            case 280 -> press("保存");
            case 310 -> {if(pending())return;require(req(3).get("nbt").getAsString().equals("{grade:5}"),"fluid output");click(320,90);field().setValue("{\"purity\":\">=4.0d\"}");mc.options.guiScale().set(2);mc.resizeDisplay();}
            case 350 -> {bounds();require(field().getValue().contains(">=4.0d"),"resize lost draft");shot("nbt-08-scale2.png");}
            case 360 -> {press("取消");require(!req(4).getAsJsonObject("nbt").has("purity"),"cancel persisted");mc.options.guiScale().set(3);mc.resizeDisplay();click(320,90);field().setValue("{}");press("保存");}
            case 390 -> {if(pending())return;require(req(4).getAsJsonObject("nbt").size()==0,"explicit empty lost");click(320,182);press("清除");press("保存");}
            case 420 -> {if(pending())return;require(!req(5).has("nbt"),"data clear");click(320,46);field().setValue("{bad:1}");require(!button("保存").active,"unquoted JSON accepted");shot("nbt-09-invalid-json.png");}
            case 430 -> {field().setValue("{\"name\":\"[\"}");require(!button("保存").active,"invalid regex accepted");shot("nbt-10-invalid-regex.png");}
            case 440 -> {field().setValue("[]");require(!button("保存").active,"root list accepted");field().setValue("{\"x\":1,\"x\":2}");require(!button("保存").active,"duplicate JSON accepted");press("取消");}
            case 450 -> {press("NBT 数据");var longJson=new StringBuilder("{\n");for(int i=0;i<40;i++)longJson.append("  \"key").append(i).append("\":").append(i).append(",\n");longJson.append("  \"long\":\"").append("x".repeat(2000)).append("\"\n}");field().setValue(longJson.toString());press("显示数据");field().setValue(longJson.toString());require(button("保存").active,"long multiline invalid");screen().mouseScrolled(field().getX()+20,field().getY()+20,-40);press("预览");shot("nbt-11-long-multiline.png");}
            case 455 -> {showLongTooltip=true;shot("nbt-12-long-tooltip.png");}
            case 460 -> {showLongTooltip=false;press("取消");require(req(0).getAsJsonObject("nbt").get("quality").getAsString().equals(">=3"),"invalid cancel changed data");for(int i=0;i<6;i++)require(req(i).get("authorNote").getAsString().equals("keep") && req(i).get("parallelizeUnaffected").getAsBoolean() && req(i).get("selector-tag").getAsString().equals("nbt"),"metadata lost");Modularmachinery.LOG.info("[MMC][nbt-editor-client] PASS: actual NBT button and existing NBT row clicks, multiline JSON and pattern formats, input/output tabs, independent display preview, pending/network/reload acknowledgements, item/fluid/pertick, clear vs {}, original representation and metadata, scale3/2 draft, cancel, invalid JSON/root/duplicate/regex; inspect screenshots separately");}
            case 490 -> mc.stop();
        }}catch(Throwable failure){Modularmachinery.LOG.error("[MMC][nbt-editor-client] FAIL tick {}",ticks,failure);mc.stop();}
    }
    @SubscribeEvent public static void render(net.minecraftforge.client.event.ScreenEvent.Render.Post e){
        if(showLongTooltip && e.getScreen() instanceof ScreenMachineEditor s && Boolean.getBoolean("modularmachinery.editorNbtVisualTest")){
            try {var f=ScreenMachineEditor.class.getDeclaredField("nbtPanel");f.setAccessible(true);((RequirementNbtPanel)f.get(s)).tooltip(e.getGuiGraphics(),s.getGuiLeft()+100,s.getGuiTop()+198,100,198);}
            catch(ReflectiveOperationException invalid){throw new AssertionError(invalid);}
        }
    }
    private static boolean pending(){if(screen().getMenu().saves().pending()){ticks--;require(++waiting<600,"save timeout");return true;}return false;}
    private static ScreenMachineEditor screen(){require(Minecraft.getInstance().screen instanceof ScreenMachineEditor,"wrong screen");return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static Button button(String text){return screen().children().stream().filter(w->w instanceof Button b && b.visible && b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow();}
    private static MultiLineEditBox field(){return screen().children().stream().filter(w->w instanceof MultiLineEditBox b && b.visible).map(w->(MultiLineEditBox)w).findFirst().orElseThrow();}
    private static void press(String text){var b=button(text);require(b.active,"inactive: "+text);screen().mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d,0);screen().mouseReleased(b.getX()+1,b.getY()+1,0);}
    private static void click(int x,int y){var s=screen();s.mouseClicked(s.getGuiLeft()+x,s.getGuiTop()+y,0);s.mouseReleased(s.getGuiLeft()+x,s.getGuiTop()+y,0);}
    private static JsonObject req(int i){var root=JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject();for(var m:root.getAsJsonArray("machines"))for(var r:m.getAsJsonObject().getAsJsonArray("recipes"))if(r.getAsJsonObject().get("name").getAsString().equals("000_nbt_visual_recipe"))return r.getAsJsonObject().getAsJsonArray("requirements").get(i).getAsJsonObject();throw new AssertionError("missing fixture");}
    private static void bounds(){var s=screen();for(var child:s.children())if(child instanceof AbstractWidget w && w.visible){require(w.getX()>=0 && w.getY()>=0 && w.getX()+w.getWidth()<=s.width && w.getY()+w.getHeight()<=s.height,"widget outside screen");require(!(w instanceof EditBox),"main name/search field visible over modal");}}
    private static void shot(String name){capture=name;captureTicks=3;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
