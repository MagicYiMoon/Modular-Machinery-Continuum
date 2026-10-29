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
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualEditorExpanded {
    private static int ticks,waiting,captureDelay;private static String capture;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.editorExpandedVisualTest"))return;var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        if(captureDelay>0&&--captureDelay==0)Screenshot.grab(mc.gameDirectory,capture,mc.getMainRenderTarget(),r->{});
        ticks++;
        try{switch(ticks){
            case 20->{mc.player.connection.sendCommand("clear");mc.player.connection.sendCommand("give @s minecraft:diamond{quality:5} 7");}
            case 40->mc.player.connection.sendCommand("modularmachinery gui");
            case 80->{s();click(320,46);}
            case 95->{press("时间 tick");boxes().get(0).setValue("23000");boxes().get(1).setValue("1000");shot("expanded-01-time.png");}
            case 110->press("保存");
            case 140->{if(pending())return;require(req(0).get("condition").getAsString().equals("time"),"time save");invoke("openRequirementEditor",0);press("群系／标签");field().setValue("minecraft:plains\n#minecraft:is_forest");shot("expanded-02-biomes.png");}
            case 155->{field().setValue("not_an_id");require(!button("保存").active,"invalid ID");field().setValue("minecraft:plains\n#minecraft:is_forest");mc.options.guiScale().set(2);mc.resizeDisplay();}
            case 180->{require(field().getValue().contains("is_forest"),"environment resize lost draft");shot("expanded-03-resize.png");press("月相");press("☐ 4");shot("expanded-04-moon.png");}
            case 195->{press("天气");press("下雪");shot("expanded-05-weather.png");press("保存");}
            case 225->{if(pending())return;require(req(0).get("weather").getAsString().equals("snowing"),"weather save");mc.options.guiScale().set(3);mc.resizeDisplay();invoke("openTypePicker",false);require(button("环境条件（输入）").active,"environment add entry");shot("expanded-06-types.png");press("环境条件（输入）");}
            case 240->{press("维度");field().setValue("minecraft:overworld\nmissing:dimension");press("保存");}
            case 270->{if(pending())return;require(req(4).getAsJsonArray("values").size()==2,"dimension append");invoke("openRequirementEditor",1);press("候选 NBT");field().setValue("{\"quality\":\">=3\"}");press("预览");shot("expanded-07-candidate.png");}
            case 285->{press("应用");press("默认 NBT");field().setValue("{\"default\":1}");press("应用");shot("expanded-08-group.png");press("保存");}
            case 315->{if(pending())return;require(req(1).has("nbt")&&req(1).getAsJsonArray("items").get(0).getAsJsonObject().has("nbt"),"nested group save");invoke("openRequirementEditor",2);press("候选 NBT");field().setValue("{\"grade\":9}");press("显示数据");field().setValue("{\"grade\":99}");press("预览");shot("expanded-09-output.png");}
            case 330->{press("应用");press("保存");}
            case 360->{if(pending())return;require(req(2).getAsJsonArray("items").get(0).getAsJsonObject().has("nbt-display"),"random output NBT");invoke("openRequirementEditor",3);press("默认 NBT");field().setValue("{\"quality\":\">=5\"}");press("取消");press("取消");require(!req(3).has("nbt"),"nested cancel persisted");invoke("openTypePicker",false);press("物品");press("背包");}
            case 375->{boxes().get(0).setValue("@minecraft diamond");shot("expanded-10-inventory.png");}
            case 390->{click(110,87);require(boxes().stream().anyMatch(b->b.getValue().equals("7")),"inventory amount");shot("expanded-11-inventory-draft.png");press("保存");}
            case 420->{if(pending())return;require(req(5).get("amount").getAsInt()==7&&req(5).has("nbt"),"inventory NBT ack");require(mc.player.getInventory().countItem(net.minecraft.world.item.Items.DIAMOND)==7,"inventory consumed by editor");press("复制");boxes().get(1).setValue("001_expanded_target");}
            case 435->{var row=s().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getMessage().getString().contains("Expanded Target")).map(w->(Button)w).findFirst().orElseThrow();press(row);boxes().get(0).setValue("000_expanded_cross_copy");shot("expanded-12-copy.png");if(!button("确认复制").active){var panel=ScreenMachineEditor.class.getDeclaredField("copyPanel");panel.setAccessible(true);var error=RecipeCopyPanel.class.getDeclaredMethod("error");error.setAccessible(true);throw new AssertionError("copy validation: "+error.invoke(panel.get(s())));}press("确认复制");}
            case 470->{if(pending())return;var f=ScreenMachineEditor.class.getDeclaredField("machineIndex");f.setAccessible(true);require(f.getInt(s())==1,"copy did not select target");Modularmachinery.LOG.info("[MMC][expanded-editor-client] PASS: native environment tabs/ranges/list/moon/weather/add/resize, nested candidate/default/output/cancel, inventory search/count/NBT and non-consumption, cross-machine copy and target selection; screenshots require separate inspection");}
            case 490->{invoke("openTypePicker",true);press("物品");press("背包");press("带入 NBT ✓");boxes().get(0).setValue("diamond");mc.options.guiScale().set(2);mc.resizeDisplay();require(button("带入 NBT ×").active,"NBT-off switch lost on resize");shot("expanded-16-inventory-output.png");}
            case 510->{click(110,87);press("保存");}
            case 540->{if(pending())return;var root=JsonParser.parseString(s().getMenu().snapshot()).getAsJsonObject();var reqs=root.getAsJsonArray("machines").get(1).getAsJsonObject().getAsJsonArray("recipes").get(0).getAsJsonObject().getAsJsonArray("requirements");var last=reqs.get(reqs.size()-1).getAsJsonObject();require(last.get("amount").getAsInt()==7&&!last.has("nbt"),"NBT-off output save");Modularmachinery.LOG.info("[MMC][inventory-output-client] PASS: output selection, NBT-off resize and authoritative save");mc.options.guiScale().set(3);mc.resizeDisplay();press("导入模板");}
            case 555->{boxes().get(0).setValue("000_inventory_template");shot("expanded-13-template.png");press("修改器");}
            case 560->{press("+ 修改器");boxes().get(0).setValue("2");shot("expanded-19-template-modifier.png");}
            case 565->{press("应用");press("修改器");require(boxes().get(0).getValue().equals("2.0"),"template modifier apply lost");boxes().get(0).setValue("3");press("取消");press("追加需求（0）");}
            case 575->{invoke("openTypePicker",false);press("物品");press("背包");boxes().get(0).setValue("diamond");}
            case 590->{click(110,87);press("保存");require(!s().getMenu().saves().pending(),"template draft sent a server mutation");shot("expanded-14-template-draft.png");}
            case 605->{press("完成追加需求");mc.getSingleplayerServer().execute(()->{try{java.nio.file.Files.writeString(templateFile(),"occupied by external editor");}catch(Exception ex){throw new IllegalStateException(ex);}});}
            case 625->{require(button("追加需求（1）").active,"template requirement did not return");bounds();press("保存并生成");require(!button("保存并生成").active,"pending save button remained active");}
            case 660->{if(pending())return;require(boxes().get(0).getValue().equals("000_inventory_template"),"rejection lost filename draft");require(button("追加需求（1）").active,"rejection lost requirements draft");var f=ScreenMachineEditor.class.getDeclaredField("saveFailed");f.setAccessible(true);require(f.getBoolean(s()),"occupied file did not reject");require(java.nio.file.Files.readString(templateFile()).equals("occupied by external editor"),"occupied file overwritten");shot("expanded-17-template-rejected.png");mc.options.guiScale().set(2);mc.resizeDisplay();bounds();require(boxes().get(0).getValue().equals("000_inventory_template")&&button("追加需求（1）").active,"resize lost rejected draft");mc.getSingleplayerServer().execute(()->{try{java.nio.file.Files.delete(templateFile());}catch(Exception ex){throw new IllegalStateException(ex);}});}
            case 685->{shot("expanded-18-template-resized.png");press("保存并生成");}
            case 720->{if(pending())return;var root=JsonParser.parseString(s().getMenu().snapshot()).getAsJsonObject();var entry=root.getAsJsonArray("adapterTemplates").asList().stream().filter(t->t.getAsJsonObject().get("path").getAsString().equals("000_inventory_template.adapter.json")).findFirst().orElseThrow().getAsJsonObject();require(entry.getAsJsonObject("source").getAsJsonArray("requirements").get(0).getAsJsonObject().get("amount").getAsInt()==7,"template inventory count");require(entry.getAsJsonObject("source").getAsJsonArray("modifiers").get(0).getAsJsonObject().get("multiplier").getAsFloat()==2F,"template modifier cancel/rejection/resize changed draft");require(entry.get("expanded").getAsBoolean()&&entry.get("imported").getAsInt()>0,"template generated no furnace recipes");shot("expanded-15-template-saved.png");Modularmachinery.LOG.info("[MMC][template-editor-client] PASS: adapter selection, modifier apply/cancel, detached inventory requirement, return, pending lock, actual server rejection, disk protection, retained draft at GUI scales 3/2, retry, authoritative save and generated recipe report");}
            case 745->mc.stop();
        }}catch(Throwable failure){Modularmachinery.LOG.error("[MMC][expanded-editor-client] FAIL tick {}",ticks,failure);mc.stop();}
    }
    private static ScreenMachineEditor s(){require(Minecraft.getInstance().screen instanceof ScreenMachineEditor,"wrong screen");return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static List<EditBox> boxes(){return s().children().stream().filter(w->w instanceof EditBox b&&b.visible).map(w->(EditBox)w).toList();}
    private static MultiLineEditBox field(){return s().children().stream().filter(w->w instanceof MultiLineEditBox b&&b.visible).map(w->(MultiLineEditBox)w).findFirst().orElseThrow();}
    private static Button button(String text){return s().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow(()->new AssertionError("missing button "+text));}
    private static void press(String text){press(button(text));}private static void press(Button b){require(b.active,"inactive "+b.getMessage().getString());s().mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d,0);s().mouseReleased(b.getX()+1,b.getY()+1,0);}
    private static void click(int x,int y){s().mouseClicked(s().getGuiLeft()+x,s().getGuiTop()+y,0);s().mouseReleased(s().getGuiLeft()+x,s().getGuiTop()+y,0);}
    private static void invoke(String method,Object arg)throws Exception{var m=ScreenMachineEditor.class.getDeclaredMethod(method,arg instanceof Boolean?boolean.class:int.class);m.setAccessible(true);m.invoke(s(),arg);}
    private static JsonObject req(int index){for(var m:JsonParser.parseString(s().getMenu().snapshot()).getAsJsonObject().getAsJsonArray("machines"))for(var r:m.getAsJsonObject().getAsJsonArray("recipes"))if(r.getAsJsonObject().get("name").getAsString().equals("000_expanded"))return r.getAsJsonObject().getAsJsonArray("requirements").get(index).getAsJsonObject();throw new AssertionError("missing recipe");}
    private static boolean pending(){if(s().getMenu().saves().pending()){ticks--;require(++waiting<600,"save timeout");return true;}require(s().getMenu().snapshot()!=null,"snapshot missing");return false;}
    private static void shot(String name){capture=name;captureDelay=3;}
    private static java.nio.file.Path templateFile(){return hellfirepvp.modularmachinery.common.data.ModDataHolder.INSTANCE.getRecipeDirectory().toPath().resolve("000_inventory_template.adapter.json");}
    private static void bounds(){for(var child:s().children())if(child instanceof AbstractWidget w&&w.visible)require(w.getX()>=0&&w.getY()>=0&&w.getX()+w.getWidth()<=s().width&&w.getY()+w.getHeight()<=s().height,"template widget outside viewport: "+w.getMessage().getString());}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
