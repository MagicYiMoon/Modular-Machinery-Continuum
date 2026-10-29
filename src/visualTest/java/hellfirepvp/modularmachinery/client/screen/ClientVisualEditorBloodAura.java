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
public final class ClientVisualEditorBloodAura {
    private static int ticks,waiting,captureTicks;
    private static String capture;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !Boolean.getBoolean("modularmachinery.editorBloodAuraVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
        if(captureTicks>0 && --captureTicks==0) {
            String name=capture;capture=null;
            Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][blood-aura-editor-client] {}: {}",name,r.getString()));
        }
        ticks++;
        try {
            switch(ticks) {
                case 20 -> mc.player.connection.sendCommand("modularmachinery gui");
                case 60 -> {screen();shot("blood-aura-01-main.png");}
                case 70 -> {click(320,46);}
                case 80 -> {bounds();require(!button("恶魔意志").active,"editing changed family");field("LP 用量").setValue("234");pressPrefix("单次：");}
                case 100 -> {shot("blood-aura-02-lp.png");}
                case 110 -> {press("保存");require(screen().getMenu().saves().pending() && !button("保存").active,"pending guard");}
                case 140 -> {if(pending())return;require(req(0).get("amount").getAsInt()==234 && req(0).get("perTick").getAsBoolean(),"LP rate save");click(320,68);}
                case 160 -> {bounds();require(field("资源阈值").getValue().equals("200.0"),"input will min");field("意志用量").setValue("2.125");pressPrefix("意志类型：");field("资源阈值").setValue("7.5");}
                case 180 -> {shot("blood-aura-03-will.png");}
                case 190 -> {press("保存");}
                case 220 -> {if(pending())return;require(req(2).get("amount").getAsDouble()==2.125 && req(2).get("will-type").getAsString().equals("corrosive") && req(2).get("min").getAsDouble()==7.5 && req(2).get("max").getAsDouble()==100,"will type/min/metadata");require(!button("批量设置").active,"will batch enabled");click(320,90);}
                case 240 -> {bounds();field("灵气用量").setValue("321");field("资源阈值").setValue("-200");shot("blood-aura-04-aura.png");}
                case 250 -> {press("保存");}
                case 280 -> {if(pending())return;require(req(3).get("amount").getAsInt()==321 && req(3).get("min-aura").getAsInt()==-200 && req(3).get("max-aura").getAsInt()==-100,"signed aura bounds");click(320,90);field("灵气用量").setValue("999");mc.options.guiScale().set(2);mc.resizeDisplay();}
                case 320 -> {bounds();require(field("灵气用量").getValue().equals("999"),"scale lost draft");shot("blood-aura-05-scale2.png");}
                case 330 -> {press("取消");require(req(3).get("amount").getAsInt()==321,"cancel saved draft");mc.options.guiScale().set(3);mc.resizeDisplay();press("批量设置");pressPrefix("随批量放大：");press("保存");}
                case 360 -> {if(pending())return;require(!req(3).get("parallelizeUnaffected").getAsBoolean(),"aura batch follow");press("批量设置");pressPrefix("不随批量放大：");press("保存");}
                case 400 -> {if(pending())return;require(req(3).get("parallelizeUnaffected").getAsBoolean(),"aura batch exempt");click(420,119);press("魔法资源");field("LP 用量").setValue("42");pressPrefix("单次：");shot("blood-aura-06-lp-output.png");}
                case 410 -> {press("保存");}
                case 440 -> {if(pending())return;require(req(4).get("amount").getAsInt()==42 && req(4).get("perTick").getAsBoolean(),"LP output");click(420,119);press("魔法资源");press("恶魔意志");field("意志用量").setValue("0.5");pressPrefix("意志类型：");pressPrefix("意志类型：");field("资源阈值").setValue("50");shot("blood-aura-07-will-output.png");}
                case 450 -> {press("保存");}
                case 480 -> {if(pending())return;require(req(5).get("amount").getAsDouble()==.5 && req(5).get("max").getAsDouble()==50 && req(5).get("will-type").getAsString().equals("destructive"),"will output type/max");click(420,119);press("魔法资源");press("自然灵气");field("灵气用量").setValue("12");field("资源阈值").setValue("-100");shot("blood-aura-08-aura-output.png");}
                case 490 -> {press("保存");}
                case 520 -> {if(pending())return;require(req(6).get("amount").getAsInt()==12 && req(6).get("max-aura").getAsInt()==-100,"aura output signed max");shot("blood-aura-09-saved.png");}
                case 530 -> {click(420,27);press("魔法资源");press("恶魔意志");field("意志用量").setValue("1e400");require(!button("保存").active,"infinity accepted");shot("blood-aura-10-invalid.png");}
                case 540 -> {press("取消");click(320,46);field("LP 用量").setValue("2147483648");require(!button("保存").active,"LP overflow accepted");press("取消");}
                case 560 -> {Modularmachinery.LOG.info("[MMC][blood-aura-editor-client] PASS: actual LP rate IO, decimal will type/min/max and no batch, signed aura bound IO and server type options, family lock, pending saves and acknowledgements, invalid non-finite/int-overflow, cancel, scale3/2 draft, aura batch follow/exempt; inspect screenshots separately");}
                case 590 -> mc.stop();
            }
        }catch(Throwable failure){Modularmachinery.LOG.error("[MMC][blood-aura-editor-client] FAIL tick {}",ticks,failure);mc.stop();}
    }
    private static boolean pending(){if(screen().getMenu().saves().pending()){ticks--;require(++waiting<600,"save timeout");return true;}return false;}
    private static ScreenMachineEditor screen(){require(Minecraft.getInstance().screen instanceof ScreenMachineEditor,"wrong screen");return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static Button button(String text){return screen().children().stream().filter(w->w instanceof Button b && b.visible && b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow();}
    private static EditBox field(String text){return screen().children().stream().filter(w->w instanceof EditBox b && b.visible && b.getMessage().getString().equals(text)).map(w->(EditBox)w).findFirst().orElseThrow();}
    private static void pressPrefix(String prefix){var b=screen().children().stream().filter(w->w instanceof Button btn && btn.visible && btn.getMessage().getString().startsWith(prefix)).map(w->(Button)w).findFirst().orElseThrow();pressButton(b);}
    private static void press(String text){pressButton(button(text));}
    private static void pressButton(Button b){require(b.active,"inactive: "+b.getMessage().getString());screen().mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d,0);screen().mouseReleased(b.getX()+1,b.getY()+1,0);}
    private static void click(int x,int y){var s=screen();s.mouseClicked(s.getGuiLeft()+x,s.getGuiTop()+y,0);s.mouseReleased(s.getGuiLeft()+x,s.getGuiTop()+y,0);}
    private static JsonObject req(int i){var root=JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject();for(var m:root.getAsJsonArray("machines"))for(var r:m.getAsJsonObject().getAsJsonArray("recipes"))if(r.getAsJsonObject().get("name").getAsString().equals("000_blood_aura_visual_recipe"))return r.getAsJsonObject().getAsJsonArray("requirements").get(i).getAsJsonObject();throw new AssertionError("missing fixture");}
    private static void bounds(){var s=screen();for(var child:s.children())if(child instanceof AbstractWidget w && w.visible)require(w.getX()>=0 && w.getY()>=0 && w.getX()+w.getWidth()<=s.width && w.getY()+w.getHeight()<=s.height,"widget outside screen");}
    private static void shot(String name){capture=name;captureTicks=3;}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
