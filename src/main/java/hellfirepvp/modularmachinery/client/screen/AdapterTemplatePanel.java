/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.network.PktMachineEditorTemplate;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Registered adapter selector, detached template settings and last real expansion diagnostics. */
final class AdapterTemplatePanel {
    private final List<AbstractWidget> widgets=new ArrayList<>();private final List<Button> templateRows=new ArrayList<>(),adapterRows=new ArrayList<>();
    private Font font;private EditBox id,search;private Button save,mods,requirements,previous,next;
    private JsonObject root=new JsonObject(),owner=new JsonObject(),draft=new JsonObject(),entry=new JsonObject();
    private List<JsonObject> templates=List.of();private List<String> adapters=List.of();private String path="",digest="",query="",fileDraft="";private int templateOffset,adapterOffset;
    private boolean visible,pending,creating;private String feedback="";
    void init(Font font,int left,int top,Consumer<AbstractWidget> add,Consumer<PktMachineEditorTemplate> apply,Runnable cancel,Runnable modifiers,Runnable editRequirements){
        this.font=font;save=null;widgets.clear();templateRows.clear();adapterRows.clear();
        id=new EditBox(font,left+154,top+40,272,18,Component.literal("模板文件 ID"));id.setMaxLength(256);id.setValue(fileDraft);id.setResponder(v->{fileDraft=v;update();});widgets.add(id);
        search=new EditBox(font,left+154,top+80,272,18,Component.literal("搜索已注册适配器"));search.setMaxLength(200);search.setValue(query);search.setResponder(v->{query=v;adapterOffset=0;filter();update();});widgets.add(search);
        for(int i=0;i<6;i++){final int n=i;var row=Button.builder(Component.empty(),b->select(templates.get(templateOffset+n))).bounds(left+8,top+24+i*25,130,23).build();templateRows.add(row);widgets.add(row);}
        for(int i=0;i<3;i++){final int n=i;var row=Button.builder(Component.empty(),b->{draft.addProperty("adapter",adapters.get(adapterOffset+n));update();}).bounds(left+154,top+104+i*23,272,21).build();adapterRows.add(row);widgets.add(row);}
        previous=Button.builder(Component.literal("↑"),b->{templateOffset=Math.max(0,templateOffset-1);update();}).bounds(left+8,top+178,62,18).build();widgets.add(previous);
        next=Button.builder(Component.literal("↓"),b->{templateOffset=Math.min(Math.max(0,templates.size()-6),templateOffset+1);update();}).bounds(left+76,top+178,62,18).build();widgets.add(next);
        mods=Button.builder(Component.literal("修改器"),b->modifiers.run()).bounds(left+154,top+180,130,20).build();widgets.add(mods);
        requirements=Button.builder(Component.literal("追加需求"),b->editRequirements.run()).bounds(left+290,top+180,136,20).build();widgets.add(requirements);
        widgets.add(Button.builder(Component.literal("+ 新导入模板"),b->newTemplate()).bounds(left+8,top+231,130,20).build());
        save=Button.builder(Component.literal("保存并生成"),b->{if(error().isEmpty())apply.accept(new PktMachineEditorTemplate(creating?id.getValue():path,digest,owner.get("name").getAsString(),draft.toString()));}).bounds(left+268,top+231,78,20).build();widgets.add(save);
        widgets.add(Button.builder(Component.literal("返回"),b->cancel.run()).bounds(left+352,top+231,74,20).build());widgets.forEach(add);filter();update();
    }
    void open(JsonObject root,JsonObject owner){this.root=root;this.owner=owner;templateOffset=adapterOffset=0;loadTemplates();if(templates.isEmpty())newTemplate();else select(templates.get(0));setVisible(true,false);}
    void refresh(JsonObject root){this.root=root;String wanted=creating?fileDraft+".adapter.json":path;loadTemplates();templates.stream().filter(t->t.get("path").getAsString().equals(wanted)).findFirst().ifPresent(this::select);}
    private void loadTemplates(){var list=new ArrayList<JsonObject>();if(root.has("adapterTemplates"))for(var t:root.getAsJsonArray("adapterTemplates")){var entry=t.getAsJsonObject();var machine=entry.getAsJsonObject("source").get("machine");String current=owner.get("name").getAsString();if(machine.isJsonArray()?machine.getAsJsonArray().contains(new JsonPrimitive(current)):machine.getAsString().equals(current))list.add(entry);}templates=List.copyOf(list);filter();}
    private void filter(){var list=new ArrayList<String>();if(root.has("registeredAdapters"))for(var a:root.getAsJsonArray("registeredAdapters"))if(a.getAsString().toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT)))list.add(a.getAsString());adapters=List.copyOf(list);adapterOffset=Math.max(0,Math.min(adapterOffset,adapters.size()-3));}
    private void select(JsonObject entry){this.entry=entry;creating=false;path=entry.get("path").getAsString();digest=entry.get("digest").getAsString();draft=entry.getAsJsonObject("source").deepCopy();if(!draft.has("requirements"))draft.add("requirements",new JsonArray());if(!draft.has("modifiers"))draft.add("modifiers",new JsonArray());id.setValue(path);update();}
    private void newTemplate(){creating=true;path="";digest="";entry=new JsonObject();draft=new JsonObject();draft.addProperty("machine",owner.has("name")?owner.get("name").getAsString():"");draft.addProperty("adapter","minecraft:furnace");draft.add("requirements",new JsonArray());draft.add("modifiers",new JsonArray());id.setValue((owner.has("name")?owner.get("name").getAsString():"machine")+"_import");update();}
    JsonArray requirements(){return draft.getAsJsonArray("requirements");}void requirements(JsonArray array){draft.add("requirements",array.deepCopy());update();}
    JsonObject owner(){return owner;}JsonObject root(){return root;}
    JsonArray modifiers(){var result=draft.getAsJsonArray("modifiers").deepCopy();for(var e:result){var raw=e.getAsJsonObject();var clean=CatalystEditor.validateModifier(raw);clean.entrySet().forEach(v->raw.add(v.getKey(),v.getValue()));}return result;}
    void modifiers(JsonArray array){var result=array.deepCopy();result.forEach(e->e.getAsJsonObject().remove(IngredientGroupEditor.ORIGIN));draft.add("modifiers",result);update();}
    private String error(){if(!draft.has("adapter"))return "";try{if(creating)RecipeCopyEditor.validateId(id.getValue());String adapter=draft.get("adapter").getAsString();if(!root.has("registeredAdapters")||!root.getAsJsonArray("registeredAdapters").contains(new JsonPrimitive(adapter)))return "该适配器未注册，请选择可用适配器";if(draft.toString().length()>32767)return "模板数据超过本窗口 32767 字符上限";return "";}catch(RuntimeException e){return e.getMessage();}}
    void setVisible(boolean visible,boolean pending){this.visible=visible;this.pending=pending;update();}
    void scroll(int x,int y,double delta){if(!visible||pending)return;if(x>=8&&x<138&&y>=24&&y<174)templateOffset=Math.max(0,Math.min(templateOffset-(int)Math.signum(delta),Math.max(0,templates.size()-6)));else if(x>=154&&x<426&&y>=104&&y<173)adapterOffset=Math.max(0,Math.min(adapterOffset-(int)Math.signum(delta),Math.max(0,adapters.size()-3)));update();}
    private void update(){for(var w:widgets){w.visible=visible;w.active=visible&&!pending;}if(save==null)return;id.active&=creating;
        for(int i=0;i<6;i++){var row=templateRows.get(i);int n=templateOffset+i;row.visible=visible&&n<templates.size();if(n<templates.size()){String p=templates.get(n).get("path").getAsString();row.setMessage(Component.literal(font.plainSubstrByWidth((p.equals(path)?"✓ ":"")+p,120)));row.setTooltip(Tooltip.create(Component.literal(p+"\n切换模板会放弃当前未保存的草稿")));}}
        for(int i=0;i<3;i++){var row=adapterRows.get(i);int n=adapterOffset+i;row.visible=visible&&n<adapters.size();if(n<adapters.size()){row.setMessage(Component.literal(font.plainSubstrByWidth((draft.has("adapter")&&draft.get("adapter").getAsString().equals(adapters.get(n))?"✓ ":"")+adapters.get(n),260)));row.setTooltip(Tooltip.create(Component.literal(adapters.get(n))));}}
        previous.active&=templateOffset>0;next.active&=templateOffset+6<templates.size();save.active&=error().isEmpty();requirements.setMessage(Component.literal("追加需求（"+(draft.has("requirements")?requirements().size():0)+"）"));
        mods.setTooltip(null);try{modifiers();mods.active&=true;}catch(RuntimeException e){mods.active=false;mods.setTooltip(Tooltip.create(Component.literal("已有修改器超出面板范围，请修改源文件；其他字段保存会保留它们")));}
    }
    void draw(GuiGraphics g,String status,boolean failed){update();g.fill(0,0,440,260,0xF020242A);g.fill(4,18,142,225,0xFF30363D);line(g,"本机导入模板",8,6,130,0xFFFFFF);line(g,creating?"创建导入模板":"编辑导入模板",154,6,272,0xFFFFFF);
        line(g,creating?"新文件 ID（生成 .adapter.json）":"源文件（保持所属机器与其他字段）",154,26,272,0xDDDDDD);line(g,"适配器："+(draft.has("adapter")?draft.get("adapter").getAsString():""),154,65,272,0xD9C98E);
        String error=error();feedback=error.isEmpty()?status:error;line(g,feedback,8,206,130,!error.isEmpty()||failed?0xFF8888:0x9BDEAA);
        line(g,entry.has("imported")?"上次实际加载："+entry.get("imported").getAsString()+" 个配方":"保存模板后从源配方重新生成",154,205,272,0x9BDEAA);
        line(g,entry.has("report")&&!entry.getAsJsonArray("report").isEmpty()?entry.getAsJsonArray("report").get(0).getAsString():"悬停查看导入及跳过报告；滚轮浏览适配器",154,217,272,0xB5C5D5);
    }
    void tooltip(GuiGraphics g,int mx,int my,int x,int y){
        if(x>=8&&x<138&&y>=203&&y<225&&!feedback.isEmpty()){g.renderTooltip(font,font.split(Component.literal(feedback),Math.min(320,g.guiWidth()-16)),mx,my);return;}
        if(x>=154&&x<426&&y>=203&&y<230){var text=new StringBuilder();if(entry.has("report"))entry.getAsJsonArray("report").forEach(line->text.append(line.getAsString()).append('\n'));if(text.isEmpty())text.append("适配器未提供细分统计；实际加载数量以服务器报告为准。\n修改模板后需保存，生成配方本身保持只读。");var lines=font.split(Component.literal(text.toString()),Math.min(320,g.guiWidth()-16));g.renderTooltip(font,lines.subList(0,Math.min(12,lines.size())),mx,my);}
    }
    private void line(GuiGraphics g,String s,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(s,width),x,y,color,false);}
}
