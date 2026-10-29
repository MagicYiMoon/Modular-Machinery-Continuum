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
/** Test-only actual widget clicks and server save acknowledgements; isolated disposable world. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualEditorPneumatic {
    private static int ticks,waiting;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !Boolean.getBoolean("modularmachinery.editorPneumaticVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
        ticks++;
        try {
            switch(ticks) {
                case 20 -> mc.player.connection.sendCommand("modularmachinery gui");
                case 60 -> {screen();shot("pnc-01-main.png");click(320,46);}
                case 80 -> {bounds();require(!button("持续温度条件").active,"edit allowed changing family");field("空气用量").setValue("0");field("压力阈值 bar").setValue("-0.5");pressPrefix("单次：");}
                case 100 -> {shot("pnc-03-vacuum-rate.png");field("空气用量").setValue("2147483648");}
                case 110 -> {require(!button("保存").active,"overflow accepted");shot("pnc-02-invalid.png");}
                case 120 -> field("空气用量").setValue("0");
                case 130 -> {press("保存");require(screen().getMenu().saves().pending() && !button("保存").active && !field("空气用量").active,"pending modal remained writable");}
                case 160 -> {if(pending())return;var r=req(0);require(r.get("air").getAsInt()==0 && r.get("perTick").getAsBoolean() && r.get("pressure").getAsFloat()==-0.5F && r.get("parallelizeUnaffected").getAsBoolean() && r.get("selector-tag").getAsString().equals("air"),"saved input semantics/metadata");click(420,27);press("气动／温度");press("持续温度条件");}
                case 180 -> {field("最低温度 K").setValue("600");field("最高温度 K").setValue("400");}
                case 200 -> {require(!button("保存").active,"inverted range accepted");shot("pnc-04-temperature-invalid.png");field("最低温度 K").setValue("400");field("最高温度 K").setValue("600");}
                case 220 -> {shot("pnc-05-temperature.png");press("保存");}
                case 260 -> {if(pending())return;require(req(2).get("min").getAsInt()==400 && req(2).get("max").getAsInt()==600,"temperature save");click(420,119);press("气动／温度");require(screen().children().stream().noneMatch(w->w instanceof Button b && b.visible && b.getMessage().getString().equals("持续温度条件")),"temperature output available");require(screen().children().stream().noneMatch(w->w instanceof EditBox b && b.visible && b.getMessage().getString().equals("压力阈值 bar")),"output pressure shown as effective");field("空气用量").setValue("25");pressPrefix("单次：");}
                case 280 -> {shot("pnc-06-output.png");press("保存");}
                case 320 -> {if(pending())return;require(req(3).get("air").getAsInt()==25 && req(3).get("perTick").getAsBoolean() && req(3).get("io-type").getAsString().equals("output"),"output save");shot("pnc-07-saved.png");click(320,46);field("空气用量").setValue("123");mc.options.guiScale().set(2);mc.resizeDisplay();}
                case 360 -> {bounds();require(field("空气用量").getValue().equals("123"),"resize lost draft");shot("pnc-08-scale2.png");press("取消");require(req(0).get("air").getAsInt()==0,"cancel wrote draft");}
                case 380 -> {mc.options.guiScale().set(3);mc.resizeDisplay();Modularmachinery.LOG.info("[MMC][pneumatic-editor-client] PASS: actual modal clicks, integer validation, zero-air/vacuum rate, temperature input bounds, output air rate, real network/reload acknowledgements and metadata, hidden output pressure/temperature, cancel, bounds at GUI scales 3/2 and draft retention; inspect PNGs separately");}
                case 410 -> mc.stop();
            }
        }catch(Throwable failure){Modularmachinery.LOG.error("[MMC][pneumatic-editor-client] FAIL tick {}",ticks,failure);mc.stop();}
    }
    private static boolean pending(){if(screen().getMenu().saves().pending()){ticks--;require(++waiting<600,"save timeout");return true;}return false;}
    private static ScreenMachineEditor screen(){require(Minecraft.getInstance().screen instanceof ScreenMachineEditor,"wrong screen");return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static Button button(String text){return screen().children().stream().filter(w->w instanceof Button b && b.visible && b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow();}
    private static EditBox field(String text){return screen().children().stream().filter(w->w instanceof EditBox b && b.visible && b.getMessage().getString().equals(text)).map(w->(EditBox)w).findFirst().orElseThrow();}
    private static void pressPrefix(String prefix){var b=screen().children().stream().filter(w->w instanceof Button btn && btn.visible && btn.getMessage().getString().startsWith(prefix)).map(w->(Button)w).findFirst().orElseThrow();pressButton(b);}
    private static void press(String text){pressButton(button(text));}
    private static void pressButton(Button b){require(b.active,"inactive: "+b.getMessage().getString());screen().mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d,0);screen().mouseReleased(b.getX()+1,b.getY()+1,0);}
    private static void click(int x,int y){var s=screen();s.mouseClicked(s.getGuiLeft()+x,s.getGuiTop()+y,0);s.mouseReleased(s.getGuiLeft()+x,s.getGuiTop()+y,0);}
    private static JsonObject req(int i){var root=JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject();for(var m:root.getAsJsonArray("machines"))for(var r:m.getAsJsonObject().getAsJsonArray("recipes"))if(r.getAsJsonObject().get("name").getAsString().equals("000_pnc_visual_recipe"))return r.getAsJsonObject().getAsJsonArray("requirements").get(i).getAsJsonObject();throw new AssertionError("missing fixture");}
    private static void bounds(){var s=screen();for(var child:s.children())if(child instanceof AbstractWidget w && w.visible)require(w.getX()>=0 && w.getY()>=0 && w.getX()+w.getWidth()<=s.width && w.getY()+w.getHeight()<=s.height,"widget outside screen");}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][pneumatic-editor-client] {}: {}",name,r.getString()));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
