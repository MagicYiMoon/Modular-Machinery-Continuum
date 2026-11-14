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

/** Actual widgets and integrated server saves, isolated MMC-Visual-Fixture only. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualEditorResearch {
    private static final List<Runnable> steps=new ArrayList<>();private static int ticks,index,waiting;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent e){
        if(e.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.editorResearchVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        try{
            if(steps.isEmpty())plan();
            if(mc.screen instanceof ScreenMachineEditor s&&(s.getMenu().saves().pending()||busy(s))){require(++waiting<1000,"save/query timeout");return;}
            waiting=0;if(++ticks%12!=0)return;if(index<steps.size())steps.get(index++).run();
        }catch(Throwable failure){Modularmachinery.LOG.error("[MMC][research-editor-client] FAIL step "+index,failure);mc.stop();}
    }
    private static boolean busy(ScreenMachineEditor s){for(var child:s.children())if(child instanceof EditBox b&&b.visible&&b.getMessage().getString().equals("搜索研究"))return !b.active;return false;}
    private static void add(Runnable r){steps.add(r);}
    private static void plan(){
        add(()->Minecraft.getInstance().player.connection.sendCommand("give @s minecraft:potion{Potion:\"minecraft:strong_healing\",CustomPotionColor:3368703,CustomModelData:17,Enchantments:[{id:\"minecraft:unbreaking\",lvl:2s}]} 8"));
        add(()->Minecraft.getInstance().player.connection.sendCommand("modularmachinery gui"));
        add(()->{select("000_research_database");press("算力/说明");press("网络");});
        add(()->{field("成果容量 1～4096").setValue("128");bounds();shot("research-editor-01-database.png");press("保存");});
        add(()->{require(config("000_research_database").get("storage-capacity").getAsInt()==128,"database capacity not saved");select("000_research_station");press("算力/说明");press("网络");});
        add(()->{field("最高科技等级").setValue("3.5");bounds();shot("research-editor-02-station.png");press("保存");});
        add(()->{require(config("000_research_station").get("max-tech-level").getAsDouble()==3.5,"station level not saved");press("算力/说明");press("研究");});
        add(()->{bounds();shot("research-editor-03-list.png");press("+ 新建");});
        add(()->{field("研究 ID").setValue("visual_research_created");field("显示名称").setValue("&b量子基础研究");field("科技等级").setValue("2");field("所需总点数").setValue("25000");field("最低算力 /t").setValue("12.5");press("隐藏：关");});
        add(()->{shot("research-editor-04-basic.png");press("前置");press("+ 前置研究");});
        add(()->{field("搜索研究").setValue("visual_dependency");press("搜索");});
        add(()->{press("前置测试研究");press("说明");press("+ 一行");field("第 1 行").setValue("&a研究说明：需稳定提供算力。");press("+ 一行");field("第 2 行").setValue("&7完成成果由数据库保存。");press("解锁");press("+ 一行");field("第 1 行").setValue("&6可解锁后续配方。");press("图标");});
        add(()->{field("搜索图标").setValue("@minecraft diamond");press("搜索");bounds();});
        add(()->{shot("research-editor-05-icons.png");var icon=screen().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getWidth()==22&&b.getHeight()==22).map(w->(Button)w).findFirst().orElseThrow();screen().mouseClicked(screen().editorScreenX(icon.getX()+10),screen().editorScreenY(icon.getY()+10),0);press("预览");});
        add(()->{bounds();shot("research-editor-06-preview.png");press("查看解锁说明");});
        add(()->{shot("research-editor-07-unlocks.png");press("基本");field("所需总点数").setValue("abc");Minecraft.getInstance().options.guiScale().set(2);Minecraft.getInstance().resizeDisplay();});
        add(()->{require(field("所需总点数").getValue().equals("abc"),"resize lost invalid numeric draft");press("保存研究");require(!screen().getMenu().saves().pending(),"invalid numeric request sent");field("所需总点数").setValue("25000");Minecraft.getInstance().options.guiScale().set(3);Minecraft.getInstance().resizeDisplay();});
        add(()->{bounds();press("网络");press("研究");require(field("所需总点数").getValue().equals("25000"),"outer tabs lost research draft");press("说明");press("+ 一行");field("第 3 行").setValue("第三行");press("+ 一行");field("第 4 行").setValue("第四行");press("+ 一行");field("第 5 行").setValue("第五行（第二页）");bounds();});
        add(()->{shot("research-editor-07b-lines-page2.png");press("基本");bounds();press("保存研究");require(screen().getMenu().saves().pending()&&!button("保存研究").active,"pending research form active");});
        add(()->{press("算力/说明");press("研究");});
        add(()->{field("搜索研究").setValue("visual_research_created");press("搜索");});
        add(()->{press("§b量子基础研究");});
        add(()->{require(!field("研究 ID").active&&field("研究 ID").getValue().equals("visual_research_created"),"existing id editable");require(field("所需总点数").getValue().equals("25000"),"research not saved");shot("research-editor-08-saved.png");press("前置");require(button("visual_dependency").visible,"dependency lost");press("图标");field("搜索图标").setValue("#minecraft:logs");press("搜索");});
        add(()->{bounds();shot("research-editor-09-tags.png");require(screen().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getWidth()==22).count()>0,"tag search empty");press("基本");field("显示名称").setValue("&b量子研究（已编辑）");press("保存研究");});
        add(()->{press("算力/说明");press("研究");});
        add(()->{field("搜索研究").setValue("visual_research_created");press("搜索");});
        add(()->press("§b量子研究（已编辑）"));
        add(()->{press("图标");field("搜索图标").setValue("@minecraft potion");press("搜索");press("背包");bounds();});
        add(()->{shot("research-icon-editor-01-inventory.png");var icon=screen().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getWidth()==22&&b.getHeight()==22).map(w->(Button)w).findFirst().orElseThrow();screen().mouseClicked(screen().editorScreenX(icon.getX()+10),screen().editorScreenY(icon.getY()+10),0);press("图标");press("NBT");});
        add(()->{bounds();var raw=field("物品数据 SNBT").getValue();require(raw.contains("CustomPotionColor")&&raw.contains("Enchantments")&&raw.contains("CustomModelData"),"inventory icon lost NBT");field("物品数据 SNBT").setValue("{broken");press("应用 NBT");require(field("物品数据 SNBT").visible,"invalid NBT silently applied");field("物品数据 SNBT").setValue(raw);Minecraft.getInstance().options.guiScale().set(2);Minecraft.getInstance().resizeDisplay();});
        add(()->{require(field("物品数据 SNBT").getValue().contains("CustomPotionColor"),"NBT lost on resize");Minecraft.getInstance().options.guiScale().set(3);Minecraft.getInstance().resizeDisplay();});
        add(()->{bounds();shot("research-icon-editor-02-snbt.png");press("应用 NBT");press("基本");});
        add(()->{shot("research-icon-editor-03-preview.png");press("保存研究");});
        add(()->{press("算力/说明");press("研究");});
        add(()->{field("搜索研究").setValue("visual_research_created");press("搜索");});
        add(()->press("§b量子研究（已编辑）"));
        add(()->{press("图标");press("NBT");});
        add(()->{var icon=new hellfirepvp.modularmachinery.common.hypernet.HyperNetResearchIcon(field("物品数据 SNBT").getValue()).stack();require(icon.is(net.minecraft.world.item.Items.POTION)&&icon.getCount()==1&&icon.getTag().getInt("CustomPotionColor")==3368703&&icon.isEnchanted(),"saved full icon lost data");int count=0;for(int i=0;i<Minecraft.getInstance().player.getInventory().getContainerSize();i++){var stack=Minecraft.getInstance().player.getInventory().getItem(i);if(stack.is(net.minecraft.world.item.Items.POTION)){require(stack.getTag()!=null&&stack.getTag().getInt("CustomModelData")==17&&stack.getTag().getInt("CustomPotionColor")==3368703&&stack.isEnchanted(),"original inventory NBT changed");count+=stack.getCount();}}require(count==8,"inventory icon selection consumed/mutated items");shot("research-icon-editor-04-reloaded.png");Modularmachinery.LOG.info("[MMC][research-icon-editor-client] PASS: actual inventory selection preserves count/nbt, invalid SNBT rejection, GUI resize and authoritative save/reload");});
        add(()->{press("返回");select("hypernet_terminal");press("算力/说明");press("网络");require(screen().children().stream().noneMatch(w->w instanceof EditBox b&&b.visible&&b.getMessage().getString().equals("核心线程")),"terminal has core thread");shot("research-editor-10-terminal.png");press("角色：独立终端");press("保存");});
        add(()->{require(config("hypernet_terminal").get("role").getAsString().equals("terminal"),"terminal identity changed");shot("research-editor-11-complete.png");Modularmachinery.LOG.info("[MMC][research-editor-client] PASS: actual database/station/terminal forms, paged research search, create/edit and authoritative saves, stable ID, dependency picker, descriptions/unlocks/format preview, item text/@namespace/#tag search, invalid numeric and GUI scale resize retention, pending disable; inspect PNGs separately");});
        add(()->Minecraft.getInstance().stop());
    }
    private static ScreenMachineEditor screen(){require(Minecraft.getInstance().screen instanceof ScreenMachineEditor,"wrong screen");return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static void select(String name){var s=screen();var array=JsonParser.parseString(s.getMenu().snapshot()).getAsJsonObject().getAsJsonArray("machines");int at=-1;for(int i=0;i<array.size();i++)if(array.get(i).getAsJsonObject().get("name").getAsString().equals(name))at=i;require(at>=0,"missing machine "+name);try{var f=ScreenMachineEditor.class.getDeclaredField("machineScroll");f.setAccessible(true);f.setInt(s,at);}catch(Exception e){throw new RuntimeException(e);}s.mouseClicked(s.editorScreenX(s.getGuiLeft()+40),s.editorScreenY(s.getGuiTop()+30),0);}
    private static JsonObject config(String name){for(var e:JsonParser.parseString(screen().getMenu().snapshot()).getAsJsonObject().getAsJsonArray("machines")){var j=e.getAsJsonObject();if(j.get("name").getAsString().equals(name))return j.getAsJsonObject("hypernetSettings").getAsJsonObject("hypernet");}throw new AssertionError("missing config");}
    private static Button button(String text){return screen().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow(()->new AssertionError("missing button "+text));}
    private static EditBox field(String text){return screen().children().stream().filter(w->w instanceof EditBox b&&b.visible&&b.getMessage().getString().equals(text)).map(w->(EditBox)w).findFirst().orElseThrow(()->new AssertionError("missing field "+text));}
    private static void press(String text){var b=button(text);require(b.active,"inactive "+text);screen().mouseClicked(screen().editorScreenX(b.getX()+b.getWidth()/2d),screen().editorScreenY(b.getY()+b.getHeight()/2d),0);screen().mouseReleased(screen().editorScreenX(b.getX()+1),screen().editorScreenY(b.getY()+1),0);}
    private static void bounds(){var s=screen();for(var child:s.children())if(child instanceof AbstractWidget w&&w.visible)require(s.editorScreenX(w.getX())>=0 && s.editorScreenY(w.getY())>=0 && s.editorScreenX(w.getX()+w.getWidth())<=s.width && s.editorScreenY(w.getY()+w.getHeight())<=s.height,"outside screen "+w.getMessage().getString());}
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),r->Modularmachinery.LOG.info("[MMC][research-editor-client] {}",name));}
    private static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
