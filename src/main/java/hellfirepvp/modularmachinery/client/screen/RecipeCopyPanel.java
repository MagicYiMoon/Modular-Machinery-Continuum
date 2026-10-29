/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.RecipeCopyEditor;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Target machine search and reference validation precede the authoritative copy operation. */
final class RecipeCopyPanel {
    record Target(String sourceId,String machine,String newId){}
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private final List<Button> rows=new ArrayList<>();
    private List<JsonObject> machines=List.of(),filtered=List.of();private JsonObject recipe=new JsonObject();
    private Font font;private EditBox id,search;private Button save,up,down;
    private String source="",original="",machine="",draft="",query="";private int offset;private boolean visible,pending;
    void init(Font font,int left,int top,Consumer<AbstractWidget> adder,Consumer<Target> apply,Runnable cancel){
        this.font=font;save=null;widgets.clear();rows.clear();
        id=new EditBox(font,left+56,top+57,328,18,Component.literal("新配方 ID"));id.setMaxLength(121);id.setValue(draft);id.setResponder(v->{draft=v;update();});widgets.add(id);
        search=new EditBox(font,left+56,top+96,304,18,Component.literal("搜索目标机器"));search.setMaxLength(256);search.setValue(query);search.setResponder(v->{query=v;offset=0;filter();update();});widgets.add(search);
        for(int i=0;i<3;i++){final int n=i;var row=Button.builder(Component.empty(),b->{machine=filtered.get(offset+n).get("name").getAsString();update();}).bounds(left+56,top+120+i*22,304,20).build();rows.add(row);widgets.add(row);}
        up=Button.builder(Component.literal("↑"),b->scroll(1)).bounds(left+364,top+120,20,20).build();widgets.add(up);
        down=Button.builder(Component.literal("↓"),b->scroll(-1)).bounds(left+364,top+164,20,20).build();widgets.add(down);
        save=Button.builder(Component.literal("确认复制"),b->{if(error().isEmpty())apply.accept(new Target(source,machine,id.getValue()));}).bounds(left+226,top+222,80,20).build();widgets.add(save);
        widgets.add(Button.builder(Component.literal("取消"),b->cancel.run()).bounds(left+312,top+222,72,20).build());widgets.forEach(adder);filter();update();
    }
    void open(JsonObject recipe,JsonObject owner,JsonArray candidates){this.recipe=recipe.deepCopy();source=recipe.get("name").getAsString();original=machine=owner.get("name").getAsString();
        var list=new ArrayList<JsonObject>();candidates.forEach(m->list.add(m.getAsJsonObject()));machines=List.copyOf(list);offset=0;id.setValue(RecipeCopyEditor.suggestId(source));search.setValue("");filter();update();}
    private void filter(){String q=query.toLowerCase(Locale.ROOT);filtered=machines.stream().filter(m->(m.get("name").getAsString()+" "+m.get("localizedName").getAsString()).toLowerCase(Locale.ROOT).contains(q)).toList();offset=Math.max(0,Math.min(offset,filtered.size()-3));}
    void scroll(double delta){if(!visible||pending)return;offset=Math.max(0,Math.min(offset+(delta>0?-1:1),Math.max(0,filtered.size()-3)));update();}
    EditBox field(){return id;}
    private String error(){if(id==null)return "";try{RecipeCopyEditor.validateId(id.getValue());var target=machines.stream().filter(m->m.get("name").getAsString().equals(machine)).findFirst().orElseThrow(()->new IllegalArgumentException("请选择目标机器"));if(!machine.equals(original))RecipeCopyEditor.validateReferences(recipe,target,id.getValue());return "";}catch(RuntimeException e){return e.getMessage();}}
    void setVisible(boolean visible,boolean pending){this.visible=visible;this.pending=pending;update();}
    private void update(){for(var w:widgets){w.visible=visible;w.active=visible&&!pending;}if(save==null)return;
        for(int i=0;i<3;i++){var row=rows.get(i);int n=offset+i;row.visible=visible&&n<filtered.size();if(n>=filtered.size())continue;var target=filtered.get(n);String name=target.get("name").getAsString();row.setMessage(Component.literal(font.plainSubstrByWidth((name.equals(machine)?"✓ ":"")+target.get("localizedName").getAsString()+" · "+name,288)));row.setTooltip(Tooltip.create(Component.literal(name)));}
        up.active&=offset>0;down.active&=offset+3<filtered.size();save.active&=error().isEmpty();
    }
    void draw(GuiGraphics g,String status,boolean failed){update();g.fill(0,0,440,260,0xE0101216);g.fill(40,8,400,252,0xFF242A31);
        line(g,"复制为新配方",18,0xFFFFFF);line(g,"来源："+source,34,0xB5C5D5);line(g,"新配方 ID（文件名，全局唯一）",46,0xDDDDDD);
        line(g,"目标机器 · 名称／ID 搜索（"+filtered.size()+"）",82,0xDDDDDD);
        line(g,"当前目标："+machine,188,0xD9C98E);line(g,"保留全部 JSON 字段；新 ID 可与原配方同时运行",200,0xB5C5D5);
        String e=error();line(g,e.isEmpty()?status:e,212,!e.isEmpty()||failed?0xFF8888:0x9BDEAA);
    }
    private void line(GuiGraphics g,String s,int y,int color){g.drawString(font,font.plainSubstrByWidth(s,328),56,y,color,false);}
}
