/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.*;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.editor.RecipeCopyEditor;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Real editor widget clicks and server acknowledgements in a disposable integrated-server world. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualEditorCopy {
    private static int ticks, waiting;
    private static String target;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("modularmachinery.editorCopyVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
        ticks++;
        try {
            switch(ticks) {
                case 20 -> mc.player.connection.sendCommand("modularmachinery gui");
                case 60 -> {var screen=screen();if(screen.getMenu().snapshot().equals("{\"machines\":[]}")){ticks--;require(++waiting<400,"initial snapshot timeout");return;}shot("editor-copy-01-main.png");}
                case 80 -> press("复制");
                case 100 -> {bounds();shot("editor-copy-02-modal-scale3.png");}
                case 120 -> {field().setValue("cancelled_copy");press("取消");require(button("复制").visible && button("复制").active,"cancel leaked modal or disabled main widgets");}
                case 140 -> press("复制");
                case 160 -> {field().setValue("../escape");press("确认复制");require(!screen().getMenu().saves().pending(),"invalid local ID reached server");}
                case 180 -> {shot("editor-copy-03-validation.png");require(field().getValue().equals("../escape"),"failed form lost input");field().setValue("000_editor_copy_visual_recipe");press("确认复制");}
                case 200 -> {if(screen().getMenu().saves().pending()){ticks--;require(++waiting<600,"rejection acknowledgement timeout");return;}require(field().getValue().equals("000_editor_copy_visual_recipe") && button("确认复制").active,"server rejection lost draft or locked modal");}
                case 220 -> {shot("editor-copy-04-server-rejection.png");field().setValue("000_editor_copy_visual_recipe_copy");target=field().getValue();press("确认复制");}
                case 240 -> {
                    var screen=screen();if(screen.getMenu().saves().pending()){ticks--;require(++waiting<600,"copy acknowledgement timeout");return;}
                    boolean found=false,cancelled=false;
                    var root=JsonParser.parseString(screen.getMenu().snapshot()).getAsJsonObject();
                    for(var machine:root.getAsJsonArray("machines"))for(var recipe:machine.getAsJsonObject().getAsJsonArray("recipes")) {
                        var id=recipe.getAsJsonObject().get("name").getAsString();found|=id.equals(target);cancelled|=id.equals("cancelled_copy");
                    }
                    require(found && !cancelled && button("复制").visible,"copy missing from confirmed snapshot or cancel created recipe");shot("editor-copy-05-saved-selected.png");
                }
                case 260 -> {press("复制");require(field().getValue().equals(RecipeCopyEditor.suggestId(target)),"saved copy not selected");mc.options.guiScale().set(2);mc.resizeDisplay();}
                case 300 -> {bounds();require(field().getValue().equals(RecipeCopyEditor.suggestId(target)),"resize lost modal draft");shot("editor-copy-06-modal-scale2.png");press("取消");}
                case 320 -> {mc.options.guiScale().set(3);mc.resizeDisplay();Modularmachinery.LOG.info("[MMC][editor-copy-client] PASS: actual button clicks, modal bounds at scales 3/2, cancel, local validation, server rejection/draft/retry, network save/acknowledgement, selected copied recipe and resize draft preservation; inspect PNGs separately");}
                case 360 -> mc.stop();
            }
        }catch(Throwable failure){Modularmachinery.LOG.error("[MMC][editor-copy-client] FAIL at tick {}",ticks,failure);mc.stop();}
    }
    private static ScreenMachineEditor screen(){require(Minecraft.getInstance().screen instanceof ScreenMachineEditor,"wrong screen");return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static Button button(String text){return screen().children().stream().filter(w->w instanceof Button b && b.visible && b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow();}
    private static EditBox field(){return screen().children().stream().filter(w->w instanceof EditBox box && box.visible && box.getMessage().getString().equals("新配方 ID")).map(w->(EditBox)w).findFirst().orElseThrow();}
    private static void press(String text){var button=button(text);require(button.active,"inactive button: "+text);screen().mouseClicked(button.getX()+button.getWidth()/2d,button.getY()+button.getHeight()/2d,0);screen().mouseReleased(button.getX()+1,button.getY()+1,0);}
    private static void bounds(){var screen=screen();for(var child:screen.children())if(child instanceof AbstractWidget w && w.visible)require(w.getX()>=0 && w.getY()>=0 && w.getX()+w.getWidth()<=screen.width && w.getY()+w.getHeight()<=screen.height,"visible modal widget outside screen");}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][editor-copy-client] {}: {}",name,r.getString()));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
