/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.hypernet.HyperNetResearch;
import hellfirepvp.modularmachinery.common.hypernet.HyperNetResearchIcon;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import hellfirepvp.modularmachinery.common.editor.MachineNameFormatting;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Consumer;

/** Paged authoring form. Keeps one draft; full research descriptions are fetched only on selection. */
final class ResearchDefinitionPanel {
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private final Map<String,EditBox> boxes=new LinkedHashMap<>();
    private final Map<String,Integer> labelY=new LinkedHashMap<>();
    private Font font;private int left,top,tab,linePage,iconPage,listPage,previewScroll;
    private Consumer<AbstractWidget> add,remove;private Consumer<Object> save;private MenuMachineEditor menu;
    private JsonObject draft,pageData;private String originalId="",revision="",query="",lastPage,error="";
    private boolean visible,pending,dependencyPicker,loading,opened;private int loadingTicks;
    private String cachedIconKey;private ItemStack cachedStack=ItemStack.EMPTY;
    private ItemStack previewStack(){String key=draft.has("preview-stack")?"stack:"+draft.get("preview-stack").getAsString():"item:"+(draft.has("preview-item")?draft.get("preview-item").getAsString():"minecraft:paper");if(!key.equals(cachedIconKey)){cachedIconKey=key;cachedStack=HyperNetResearchIcon.parse(draft).stack();}return cachedStack;}
    private String[] previewKeys={"descriptions","unlock-descriptions"};private int previewKind;
    private static final String[] TABS={"基本","前置","说明","解锁","图标","预览"};
    void init(Font f,int x,int y,Consumer<AbstractWidget> add,Consumer<AbstractWidget> remove,MenuMachineEditor menu,Consumer<Object> save){
        var retained=new LinkedHashMap<String,String>();boxes.forEach((k,v)->retained.put(k,v.getValue()));boolean captured=capture();String retainedError=error;
        font=f;left=x;top=y;this.add=add;this.remove=remove;this.menu=menu;this.save=save;
        widgets.clear();boxes.clear();labelY.clear();rebuild();retained.forEach((k,v)->{if(boxes.containsKey(k))boxes.get(k).setValue(v);});if(!captured)error=retainedError;
    }
    void reset(){close();opened=false;}
    void open(){if(opened)return;opened=true;draft=null;pageData=null;originalId="";query="";listPage=tab=linePage=previewScroll=0;iconNbtEditor=false;dependencyPicker=false;error="";request("");rebuild();}
    void close(){menu.clearResearchQuery();loading=false;}
    void setVisible(boolean value,boolean pending){
        this.visible=value;this.pending=pending;
        if(visible&&loading){String received=menu.researchPage();if(received!=null&&!Objects.equals(lastPage,received)){
            lastPage=received;loading=false;
            try{pageData=JsonParser.parseString(received).getAsJsonObject();revision=pageData.get("revision").getAsString();if(pageData.has("page"))listPage=pageData.get("page").getAsInt();
                if(pageData.has("error"))error=pageData.get("error").getAsString();
                if(pageData.has("definition")){draft=pageData.getAsJsonObject("definition").deepCopy();originalId=draft.get("id").getAsString();tab=linePage=previewScroll=0;iconNbtEditor=false;dependencyPicker=false;}
                rebuild();
            }catch(RuntimeException e){error="研究数据无效，请刷新";}
        }else if(++loadingTicks>200){loading=false;error="读取超时，请刷新列表";rebuild();}}
        update();
    }
    private void request(String id){
        var token=UUID.randomUUID();menu.beginResearchQuery(token);lastPage=null;loading=true;loadingTicks=0;
        ModularmachineryNetwork.CHANNEL.sendToServer(new HyperNetResearchEditorPackets.Query(menu.containerId,token,listPage,query,id));
    }
    private void clear(){for(var w:widgets){w.visible=w.active=false;w.setFocused(false);remove.accept(w);}widgets.clear();boxes.clear();labelY.clear();}
    private <T extends AbstractWidget>T widget(T w){widgets.add(w);add.accept(w);return w;}
    private Button button(String text,int x,int y,int w,Runnable action){return widget(Button.builder(Component.literal(text),b->action.run()).bounds(left+x,top+y,w,18).build());}
    private EditBox box(String label,String value,int x,int y,int w,int max,boolean drawLabel){var b=new EditBox(font,left+x,top+y,w,16,Component.literal(label));b.setMaxLength(max);b.setValue(value);boxes.put(label,b);if(drawLabel)labelY.put(label,y+4);return widget(b);}
    private void field(String label,String key,int y,int max){box(label,MachineNameFormatting.editable(draft.get(key).getAsString()),156,y,216,max,true);}
    private JsonArray array(String key){if(!draft.has(key))draft.add(key,new JsonArray());return draft.getAsJsonArray(key);}
    private void rebuild(){
        clear();
        if(draft==null||dependencyPicker){list();update();return;}
        for(int i=0;i<TABS.length;i++){final int at=i;button(TABS[i],62+i*52,62,50,()->{if(!capture())return;tab=at;linePage=iconPage=previewScroll=0;rebuild();});}
        button("返回列表",62,232,74,()->{if(!capture())return;draft=null;dependencyPicker=false;query="";listPage=0;request("");rebuild();});
        button("保存研究",294,232,78,()->{if(!capture())return;try{HyperNetResearch.parse(draft);if(draft.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length>256*1024)throw new IllegalArgumentException("研究文件超过 256 KiB");save.accept(new PktMachineEditorResearch(originalId,revision,draft.toString()));}catch(RuntimeException e){error=e.getMessage();}});
        if(tab==0){
            field("研究 ID","id",86,256);boxes.get("研究 ID").setTooltip(Tooltip.create(Component.literal("已有 ID 不可改名；数据库成果、依赖和配方使用此 ID")));
            field("显示名称","display-name",106,256);field("科技等级","tech-level",126,128);field("所需总点数","required-points",146,128);field("最低算力 /t","minimum-per-tick",166,128);
            button("隐藏："+(draft.get("hidden").getAsBoolean()?"开":"关"),156,188,150,()->{if(!capture())return;draft.addProperty("hidden",!draft.get("hidden").getAsBoolean());rebuild();});
        }else if(tab==1){
            var a=array("dependencies");linePage=Math.min(linePage,Math.max(0,(a.size()-1)/6));
            for(int i=linePage*6;i<Math.min(a.size(),linePage*6+6);i++){int at=i,y=86+(i%6)*18;
                button(font.plainSubstrByWidth(a.get(i).getAsString(),268),62,y,278,()->{}).setTooltip(Tooltip.create(Component.literal(a.get(i).getAsString())));
                button("×",346,y,26,()->{a.remove(at);rebuild();}).setFGColor(0xFF7777);
            }
            button("+ 前置研究",62,202,100,()->{if(a.size()>=64){error="最多 64 个前置研究";return;}dependencyPicker=true;query="";listPage=0;request("");rebuild();});
            button("←",220,202,28,()->{linePage=Math.max(0,linePage-1);rebuild();});button("→",344,202,28,()->{linePage=Math.min(Math.max(0,(a.size()-1)/6),linePage+1);rebuild();});
        }else if(tab==2||tab==3){
            var a=array(tab==2?"descriptions":"unlock-descriptions");linePage=Math.min(linePage,Math.max(0,(a.size()-1)/4));
            for(int i=linePage*4;i<Math.min(a.size(),linePage*4+4);i++){int at=i,y=87+(i%4)*25;
                box("第 "+(i+1)+" 行",MachineNameFormatting.editable(a.get(i).getAsString()),106,y,232,1024,false);
                labelY.put("第 "+(i+1)+" 行",y+4);button("×",346,y,26,()->{if(!capture())return;a.remove(at);rebuild();}).setFGColor(0xFF7777);
            }
            button("+ 一行",62,198,70,()->{if(!capture())return;if(a.size()>=64){error="最多 64 行";return;}a.add("");linePage=(a.size()-1)/4;rebuild();});
            button("←",220,198,28,()->{if(!capture())return;linePage=Math.max(0,linePage-1);rebuild();});button("→",344,198,28,()->{if(!capture())return;linePage=Math.min(Math.max(0,(a.size()-1)/4),linePage+1);rebuild();});
        }else if(tab==4){if(iconNbtEditor)iconData();else icons();}
        else {
            button(previewKind==0?"查看解锁说明":"查看研究说明",62,84,130,()->{previewKind=1-previewKind;previewScroll=0;rebuild();});
        }
        update();
    }
    private void list(){
        box("搜索研究",query,62,62,184,128,false);
        button("搜索",250,62,48,()->{error="";query=boxes.get("搜索研究").getValue();listPage=0;request("");rebuild();});
        button(dependencyPicker?"取消":"+ 新建",302,62,70,()->{if(dependencyPicker){dependencyPicker=false;rebuild();return;}if(loading||revision.equals("unavailable")||revision.isEmpty())return;
            draft=new HyperNetResearch("new_research_"+UUID.randomUUID().toString().substring(0,8),"新研究",0,java.math.BigDecimal.valueOf(100),java.math.BigDecimal.ONE,List.of(),List.of(),List.of(),false).json();originalId="";tab=linePage=0;error="";rebuild();});
        if(pageData!=null&&pageData.has("rows"))for(var e:pageData.getAsJsonArray("rows")){
            var r=e.getAsJsonObject();String id=r.get("id").getAsString();int at=pageData.getAsJsonArray("rows").asList().indexOf(e);
            var b=button(font.plainSubstrByWidth(MachineNameFormatting.display(r.get("name").getAsString()),285),62,84+at*16,310,()->{
                if(loading)return;
                if(dependencyPicker){var a=array("dependencies");if(id.equals(draft.get("id").getAsString())){error="不能依赖自身";return;}for(var dep:a)if(dep.getAsString().equals(id)){error="该前置已添加";return;}a.add(id);dependencyPicker=false;rebuild();}
                else {error="";request(id);update();}
            });b.setHeight(15);b.setTooltip(Tooltip.create(Component.literal(id)));
        }
        button("←",62,213,30,()->{if(loading)return;listPage=Math.max(0,listPage-1);request("");rebuild();});
        button("→",342,213,30,()->{if(loading)return;int pages=pageData!=null&&pageData.has("pages")?pageData.get("pages").getAsInt():1;listPage=Math.min(pages-1,listPage+1);request("");rebuild();});
        button("刷新",302,232,70,()->{query=boxes.get("搜索研究").getValue();request("");rebuild();});
    }
    private List<Item> filteredItems=List.of();private String iconSearch="",iconNbtDraft="";private boolean inventoryIcons,iconNbtEditor;
    private void filterIcons(){
        String[] terms=iconSearch.toLowerCase(Locale.ROOT).trim().split("\\s+");
        filteredItems=ForgeRegistries.ITEMS.getValues().stream().filter(i->{if(i==Items.AIR)return false;var id=ForgeRegistries.ITEMS.getKey(i);String text=(id+" "+i.getDescription().getString()).toLowerCase(Locale.ROOT);
            for(var term:terms){if(term.startsWith("@")){if(!id.getNamespace().contains(term.substring(1)))return false;}
                else if(term.startsWith("#")){String tag=term.substring(1);if(i.builtInRegistryHolder().tags().noneMatch(t->t.location().toString().toLowerCase(Locale.ROOT).contains(tag)))return false;}
                else if(!text.contains(term))return false;}return true;}).sorted(Comparator.comparing(i->ForgeRegistries.ITEMS.getKey(i).toString())).toList();
    }
    private void icons(){
        box("搜索图标",iconSearch,62,86,152,128,false);button("搜索",218,86,46,()->{iconSearch=boxes.get("搜索图标").getValue();iconPage=0;filterIcons();rebuild();});
        button(inventoryIcons?"全部":"背包",268,86,48,()->{inventoryIcons=!inventoryIcons;iconPage=0;rebuild();});
        button("NBT",320,86,52,()->{iconNbtDraft=HyperNetResearchIcon.parse(draft).snbt();iconNbtEditor=true;rebuild();});
        filterIcons();var candidates=new ArrayList<ItemStack>();
        if(inventoryIcons){var player=net.minecraft.client.Minecraft.getInstance().player;if(player!=null)for(int slot=0;slot<player.getInventory().getContainerSize();slot++){
            var stack=player.getInventory().getItem(slot);if(!stack.isEmpty()&&filteredItems.contains(stack.getItem()))candidates.add(stack.copy());
        }}
        iconCount=inventoryIcons?candidates.size():filteredItems.size();iconPage=Math.min(iconPage,Math.max(0,(iconCount-1)/30));
        for(int i=iconPage*30;i<Math.min(iconCount,iconPage*30+30);i++){
            var stack=inventoryIcons?candidates.get(i).copyWithCount(1):new ItemStack(filteredItems.get(i));var item=stack.getItem();int x=62+(i%30%10)*31,y=110+(i%30/10)*27;
            var b=new Button(left+x,top+y,22,22,Component.empty(),ignored->{try{HyperNetResearchIcon.ofStack(stack).put(draft);tab=0;error="";rebuild();}catch(IllegalArgumentException e){error=e.getMessage();}},supplier->supplier.get()){
                @Override public void renderWidget(GuiGraphics g,int mx,int my,float partial){super.renderWidget(g,mx,my,partial);g.renderItem(stack,getX()+3,getY()+3);}
            };b.setTooltip(Tooltip.create(Component.literal(stack.getHoverName().getString()+"\n"+ForgeRegistries.ITEMS.getKey(item))));widget(b);
        }
        button("←",62,198,30,()->{iconPage=Math.max(0,iconPage-1);rebuild();});button("→",342,198,30,()->{iconPage=Math.min(Math.max(0,(iconCount-1)/30),iconPage+1);rebuild();});
    }
    private int iconCount;
    private void iconData(){
        button("返回图标",62,86,80,()->{iconNbtEditor=false;error="";rebuild();});
        box("物品数据 SNBT",iconNbtDraft,62,122,310,HyperNetResearchIcon.MAX_BYTES,false).setTooltip(Tooltip.create(Component.literal("完整物品数据：id、Count、tag、ForgeCaps；最多16 KiB，数量自动归一为1")));
        button("应用 NBT",274,86,98,()->{try{var icon=new HyperNetResearchIcon(boxes.get("物品数据 SNBT").getValue());icon.put(draft);iconNbtDraft=icon.snbt();iconNbtEditor=false;error="";rebuild();}catch(IllegalArgumentException e){error=e.getMessage();}});
    }
    private boolean capture(){
        if(draft==null||dependencyPicker||boxes.isEmpty())return true;
        try {
            if(tab==0&&boxes.containsKey("研究 ID")){
                draft.addProperty("id",boxes.get("研究 ID").getValue());draft.addProperty("display-name",boxes.get("显示名称").getValue());
                for(var e:Map.of("科技等级","tech-level","所需总点数","required-points","最低算力 /t","minimum-per-tick").entrySet())
                    draft.addProperty(e.getValue(),new java.math.BigDecimal(boxes.get(e.getKey()).getValue()));
            }else if(tab==2||tab==3){var a=array(tab==2?"descriptions":"unlock-descriptions");for(int i=linePage*4;i<Math.min(a.size(),linePage*4+4);i++)a.set(i,new JsonPrimitive(boxes.get("第 "+(i+1)+" 行").getValue()));}
            else if(tab==4){if(boxes.containsKey("搜索图标"))iconSearch=boxes.get("搜索图标").getValue();if(boxes.containsKey("物品数据 SNBT")){iconNbtDraft=boxes.get("物品数据 SNBT").getValue();new HyperNetResearchIcon(iconNbtDraft).put(draft);}}
            error="";return true;
        }catch(RuntimeException e){error=tab==4?"物品数据无效："+e.getMessage():"请检查数字输入（允许小数和科学计数法）";return false;}
    }
    private void update(){for(var w:widgets){w.visible=visible;w.active=visible&&!pending&&!loading;}if(draft!=null&&!originalId.isEmpty()&&boxes.containsKey("研究 ID"))boxes.get("研究 ID").active=false;}
    void scroll(double delta){if(draft!=null&&tab==5)previewScroll=Math.max(0,previewScroll-(int)Math.signum(delta));}
    void draw(GuiGraphics g,String status,boolean failed){
        for(var e:labelY.entrySet())g.drawString(font,font.plainSubstrByWidth(e.getKey(),90),62,e.getValue(),0xDDDDDD,false);
        if(draft==null||dependencyPicker){String count=pageData!=null&&pageData.has("pages")?(listPage+1)+" / "+pageData.get("pages").getAsInt()+"  ·  "+pageData.get("total").getAsInt()+" 项":"读取研究目录";g.drawCenteredString(font,count,217,217,0xDDDDDD);}
        else if(tab==0){
            g.renderItem(previewStack(),345,188);
            g.drawString(font,"修改定义不会授予成果，也不会清除数据库进度",62,215,0xD9C98E,false);
        }else if(tab==2||tab==3){g.drawString(font,"支持 &a 颜色代码；预览页查看最终显示",62,219,0xD9C98E,false);}
        else if(tab==4){if(iconNbtEditor){
            g.drawString(font,"完整物品 SNBT（编辑后点击应用）",62,109,0xDDDDDD,false);
            g.renderItem(previewStack(),209,166);
            g.drawString(font,"保留药水、附魔、自定义模型和模组数据",62,197,0xD9C98E,false);
            g.drawString(font,"预览数量固定为 1；不会修改或消耗背包物品",62,213,0xD9C98E,false);
        }else{g.drawCenteredString(font,(iconPage+1)+" / "+Math.max(1,(iconCount+29)/30),217,203,0xDDDDDD);g.drawString(font,inventoryIcons?"仅复制背包物品作为图标；保留 NBT，不消耗物品":"文字／物品 ID／@模组／#标签；空格组合搜索",62,219,0xD9C98E,false);}}
        else if(tab==5){
            var lines=new ArrayList<net.minecraft.util.FormattedCharSequence>();
            lines.addAll(font.split(Component.literal(MachineNameFormatting.display(draft.get("display-name").getAsString())),302));
            lines.add(net.minecraft.util.FormattedCharSequence.EMPTY);
            for(var line:array(previewKeys[previewKind]))lines.addAll(font.split(Component.literal(MachineNameFormatting.display(line.getAsString())),302));
            int rows=10;previewScroll=Math.min(previewScroll,Math.max(0,lines.size()-rows));for(int i=previewScroll;i<Math.min(lines.size(),previewScroll+rows);i++)g.drawString(font,lines.get(i),62,109+(i-previewScroll)*11,0xDDDDDD,false);
        }
        String message=loading?"正在读取研究目录…":error.isEmpty()?status:error;
        g.drawString(font,font.plainSubstrByWidth(message,150),140,236,error.isEmpty()&&!failed?0x9BDEAA:0xFF8888,false);
    }
    void tooltip(GuiGraphics g,int mx,int my,int x,int y,String status){String message=error.isEmpty()?status:error;if(!message.isEmpty()&&x>=140&&x<290&&y>=232&&y<250)g.renderTooltip(font,font.split(Component.literal(message),300),mx,my);}
}
