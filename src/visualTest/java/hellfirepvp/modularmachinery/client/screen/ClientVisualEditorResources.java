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
public final class ClientVisualEditorResources {
    private static int ticks,waiting;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e) {
        if(e.phase!=TickEvent.Phase.END || !Boolean.getBoolean("modularmachinery.editorResourcesVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
        ticks++;
        try {
            switch(ticks) {
                case 20 -> mc.player.connection.sendCommand("modularmachinery gui");
                case 60 -> {screen();shot("resource-01-main.png");click(320,46);}
                case 80 -> {bounds();require(!button("Embers 余烬").active,"existing type switch allowed");field("魔力用量").setValue("123");pressPrefix("单次：");}
                case 100 -> {shot("resource-02-mana-rate.png");field("魔力用量").setValue("2147483648");}
                case 120 -> {require(!button("保存").active,"integer overflow accepted");shot("resource-03-invalid-mana.png");field("魔力用量").setValue("123");}
                case 140 -> {press("保存");require(screen().getMenu().saves().pending() && !button("保存").active && !field("魔力用量").active,"pending modal writable");}
                case 180 -> {if(pending())return;var r=req(0);require(r.get("amount").getAsInt()==123 && r.get("perTick").getAsBoolean() && r.get("parallelizeUnaffected").getAsBoolean() && r.get("selector-tag").getAsString().equals("resources"),"saved mana/metadata");click(320,68);require(screen().children().stream().noneMatch(w->w instanceof Button b && b.visible && b.getMessage().getString().startsWith("单次：")),"Ember has rate button");field("余烬用量").setValue("-1");}
                case 200 -> {require(!button("保存").active,"negative Ember accepted");shot("resource-04-invalid-ember.png");field("余烬用量").setValue("12.125");}
                case 220 -> {shot("resource-05-ember-decimal.png");press("保存");}
                case 260 -> {if(pending())return;require(req(2).get("ember").getAsDouble()==12.125 && req(2).get("type").getAsString().equals("modularmachinery:ember") && req(2).get("authorNote").getAsString().equals("keep ember"),"decimal Ember/alias/metadata save");click(420,119);}
                case 280 -> {bounds();shot("resource-06-type-picker.png");press("魔法资源");require(screen().children().stream().noneMatch(w->w instanceof Button b && b.visible && b.getMessage().getString().equals("Embers 余烬")),"output Ember exposed");field("魔力用量").setValue("42");pressPrefix("单次：");}
                case 300 -> {shot("resource-07-mana-output.png");press("保存");}
                case 340 -> {if(pending())return;require(req(3).get("amount").getAsInt()==42 && req(3).get("perTick").getAsBoolean() && req(3).get("io-type").getAsString().equals("output"),"mana output saved");click(420,27);press("魔法资源");press("Embers 余烬");field("余烬用量").setValue("7.25e1");}
                case 360 -> {shot("resource-08-new-ember.png");press("保存");}
                case 400 -> {if(pending())return;require(req(4).get("ember").getAsDouble()==72.5,"new decimal Ember input");shot("resource-09-saved.png");click(320,68);field("余烬用量").setValue("111.125");mc.options.guiScale().set(2);mc.resizeDisplay();}
                case 440 -> {bounds();require(field("余烬用量").getValue().equals("111.125"),"resize lost decimal draft");shot("resource-10-scale2.png");press("取消");require(req(2).get("ember").getAsDouble()==12.125,"cancel wrote draft");}
                case 460 -> {mc.options.guiScale().set(3);mc.resizeDisplay();press("批量设置");pressPrefix("随批量放大：");}
                case 480 -> {shot("resource-11-batch.png");press("保存");}
                case 520 -> {if(pending())return;require(!req(2).get("parallelizeUnaffected").getAsBoolean(),"Ember batch follow did not save");press("批量设置");pressPrefix("不随批量放大：");press("保存");}
                case 560 -> {if(pending())return;require(req(2).get("parallelizeUnaffected").getAsBoolean(),"Ember batch exemption did not save");click(320,46);press("取消");press("批量设置");pressPrefix("随批量放大：");press("保存");}
                case 600 -> {if(pending())return;require(!req(0).get("parallelizeUnaffected").getAsBoolean(),"mana batch follow did not save");press("批量设置");pressPrefix("不随批量放大：");press("保存");}
                case 640 -> {if(pending())return;require(req(0).get("parallelizeUnaffected").getAsBoolean(),"mana batch exemption did not save");Modularmachinery.LOG.info("[MMC][resource-editor-client] PASS: real controls, mana integer/rate IO, Ember decimals/scientific and alias/metadata, invalid inputs, hidden output Ember/rate, type picker, server save/ack and pending guards, new input selection, cancel, GUI scales 3/2 draft preservation and actual batch follow/exempt saves for both resources; inspect PNGs separately");}
                case 670 -> mc.stop();
            }
        }catch(Throwable failure){Modularmachinery.LOG.error("[MMC][resource-editor-client] FAIL tick {}",ticks,failure);mc.stop();}
    }
    private static boolean pending(){if(screen().getMenu().saves().pending()){ticks--;require(++waiting<600,"save timeout");return true;}return false;}
    private static ScreenMachineEditor screen(){require(Minecraft.getInstance().screen instanceof ScreenMachineEditor,"wrong screen");return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static Button button(String text){return screen().children().stream().filter(w->w instanceof Button b && b.visible && b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow();}
    private static EditBox field(String text){return screen().children().stream().filter(w->w instanceof EditBox b && b.visible && b.getMessage().getString().equals(text)).map(w->(EditBox)w).findFirst().orElseThrow();}
    private static void pressPrefix(String prefix){var b=screen().children().stream().filter(w->w instanceof Button btn && btn.visible && btn.getMessage().getString().startsWith(prefix)).map(w->(Button)w).findFirst().orElseThrow();pressButton(b);}
    private static void press(String text){pressButton(button(text));}
    private static void pressButton(Button b){require(b.active,"inactive: "+b.getMessage().getString());screen().mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d,0);screen().mouseReleased(b.getX()+1,b.getY()+1,0);}
    private static void click(int x,int y){var s=screen();s.mouseClicked(s.getGuiLeft()+x,s.getGuiTop()+y,0);s.mouseReleased(s.getGuiLeft()+x,s.getGuiTop()+y,0);}
    private static JsonObject req(int i){var root=JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject();for(var m:root.getAsJsonArray("machines"))for(var r:m.getAsJsonObject().getAsJsonArray("recipes"))if(r.getAsJsonObject().get("name").getAsString().equals("000_resource_visual_recipe"))return r.getAsJsonObject().getAsJsonArray("requirements").get(i).getAsJsonObject();throw new AssertionError("missing fixture");}
    private static void bounds(){var s=screen();for(var child:s.children())if(child instanceof AbstractWidget w && w.visible)require(w.getX()>=0 && w.getY()>=0 && w.getX()+w.getWidth()<=s.width && w.getY()+w.getHeight()<=s.height,"widget outside screen");}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][resource-editor-client] {}: {}",name,r.getString()));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
