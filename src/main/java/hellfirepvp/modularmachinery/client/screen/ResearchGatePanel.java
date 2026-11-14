/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.*;
import hellfirepvp.modularmachinery.common.menu.MenuMachineEditor;
import hellfirepvp.modularmachinery.common.network.*;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;
import java.util.function.Consumer;

/** A bounded page with persistent selections. Existing unknown IDs stay visible until explicitly removed. */
final class ResearchGatePanel {
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private final LinkedHashSet<String> selected=new LinkedHashSet<>();
    private final Map<String,JsonObject> summaries=new HashMap<>();
    private final ArrayDeque<String> lookups=new ArrayDeque<>();
    private MenuMachineEditor menu;private Font font;private int left,top,page,pages=1,total;
    private String search="",error="",lastReply;private JsonArray rows=new JsonArray();
    private boolean visible,pending,loading,selectedOnly,catalogReady;private long expires;
    private EditBox searchBox;private Consumer<AbstractWidget> add,remove;private Consumer<List<String>> save;private Runnable cancel;
    void init(Font font,int left,int top,Consumer<AbstractWidget> add,Consumer<AbstractWidget> remove,MenuMachineEditor menu,Consumer<List<String>> save,Runnable cancel){
        if(searchBox!=null)search=searchBox.getValue();this.font=font;this.left=left;this.top=top;this.add=add;this.remove=remove;this.menu=menu;this.save=save;this.cancel=cancel;
        widgets.clear();searchBox=null;rebuild();
    }
    void open(JsonObject existing){
        close();selected.clear();summaries.clear();lookups.clear();rows=new JsonArray();page=0;pages=1;total=0;search=error="";selectedOnly=false;catalogReady=false;
        if(existing!=null)selected.addAll(ResearchGateEditor.ids(existing));lookups.addAll(selected);nextLookup();rebuild();
    }
    void close(){if(menu!=null)menu.clearResearchQuery();loading=false;}
    private void query(String id,boolean summary){
        var token=UUID.randomUUID();menu.beginResearchQuery(token);lastReply=null;loading=true;expires=System.currentTimeMillis()+15000;
        ModularmachineryNetwork.CHANNEL.sendToServer(new HyperNetResearchEditorPackets.Query(menu.containerId,token,page,search,id,summary));update();
    }
    private void nextLookup(){if(!lookups.isEmpty())query(lookups.removeFirst(),true);else query("",false);}
    void setVisible(boolean visible,boolean pending){
        this.visible=visible;this.pending=pending;
        if(visible&&loading){String reply=menu.researchPage();
            if(reply!=null&&!Objects.equals(reply,lastReply)){
                lastReply=reply;loading=false;
                try{var root=JsonParser.parseString(reply).getAsJsonObject();
                    if(root.has("error")){error=root.get("error").getAsString();catalogReady=false;lookups.clear();}
                    else if(root.has("summary")){var row=root.getAsJsonObject("summary");summaries.put(row.get("id").getAsString(),row);nextLookup();}
                    else{page=root.get("page").getAsInt();pages=root.get("pages").getAsInt();total=root.get("total").getAsInt();rows=root.getAsJsonArray("rows");for(var e:rows){var row=e.getAsJsonObject().deepCopy();row.addProperty("exists",true);summaries.put(row.get("id").getAsString(),row);}catalogReady=true;error="";}
                    rebuild();
                }catch(RuntimeException e){loading=false;error="研究列表读取失败，请重试";}
            }else if(System.currentTimeMillis()>expires){loading=false;error="读取超时，请重试";}
        }
        update();
    }
    private Button button(String title,int x,int y,int width,Runnable action){var b=Button.builder(Component.literal(title),ignored->action.run()).bounds(left+x,top+y,width,18).build();widgets.add(b);add.accept(b);return b;}
    private void applySearch(){search=searchBox.getValue();page=0;if(!selectedOnly)query("",false);rebuild();}
    private List<String> displayed(){
        if(!selectedOnly){var list=new ArrayList<String>();for(var row:rows)list.add(row.getAsJsonObject().get("id").getAsString());return list;}
        var terms=search.toLowerCase(Locale.ROOT).trim().split("\\s+");
        return selected.stream().filter(id->{var r=summaries.get(id);String text=(id+" "+(r==null?"":r.get("name").getAsString())).toLowerCase(Locale.ROOT);return Arrays.stream(terms).allMatch(text::contains);}).toList();
    }
    private void rebuild(){
        for(var w:widgets){w.visible=w.active=false;w.setFocused(false);remove.accept(w);}widgets.clear();
        button("全部",62,46,48,()->{search=searchBox.getValue();selectedOnly=false;page=0;query("",false);rebuild();});
        button("已选（"+selected.size()+"）",114,46,78,()->{search=searchBox.getValue();selectedOnly=true;page=0;rebuild();});
        searchBox=new EditBox(font,left+198,top+46,124,18,Component.literal("搜索研究门槛"));searchBox.setMaxLength(128);searchBox.setValue(search);searchBox.setHint(Component.literal("名称 / ID"));widgets.add(searchBox);add.accept(searchBox);
        button("搜索",328,46,44,this::applySearch);
        var ids=displayed();if(selectedOnly){total=ids.size();pages=Math.max(1,(total+7)/8);page=Math.min(page,pages-1);}
        var shown=selectedOnly?ids.subList(Math.min(ids.size(),page*8),Math.min(ids.size(),page*8+8)):ids;
        for(int i=0;i<shown.size();i++){
            String id=shown.get(i);var r=summaries.get(id);boolean exists=r!=null&&r.get("exists").getAsBoolean();String name=r==null?id:r.get("name").getAsString();if(name.isBlank())name=id;
            String prefix=exists?"":"[缺失] ";String title=(selected.contains(id)?"☑ ":"☐ ")+prefix+MachineNameFormatting.display(name);
            final var stack=!exists?new ItemStack(Items.BARRIER):hellfirepvp.modularmachinery.common.hypernet.HyperNetResearchIcon.parse(r).stack();
            var row=new Button(left+62,top+70+i*18,310,17,Component.literal(font.plainSubstrByWidth(title,278)),ignored->{
                search=searchBox.getValue();if(selected.contains(id))selected.remove(id);else if(selected.size()>=64){error="最多选择 64 项研究";return;}else selected.add(id);error="";rebuild();
            },supplier->supplier.get()){
                @Override public void renderWidget(GuiGraphics g,int x,int y,float partial){
                    // Reserve the first 20 px for a correctly centered, separately clipped item icon.
                    var title=getMessage();setMessage(Component.empty());super.renderWidget(g,x,y,partial);setMessage(title);
                    g.renderItem(stack,getX()+2,getY());g.drawString(font,title,getX()+22,getY()+4,exists?0xFFFFFF:0xFF8888,false);
                }
            };
            row.setTooltip(Tooltip.create(Component.literal(MachineNameFormatting.display(name)+"\n"+id+(exists?"\n同网工作数据库必须拥有此成果；单击勾选／取消":"\n研究定义缺失，原 ID 会保留；可单击取消，或修复文件后重载"))));widgets.add(row);add.accept(row);
        }
        button("←",62,214,28,()->{search=searchBox.getValue();page=Math.max(0,page-1);if(!selectedOnly)query("",false);rebuild();});
        button("→",344,214,28,()->{search=searchBox.getValue();page=Math.min(pages-1,page+1);if(!selectedOnly)query("",false);rebuild();});
        button("刷新",62,232,48,()->{search=searchBox.getValue();lookups.clear();lookups.addAll(selected);nextLookup();rebuild();});
        button("保存",242,232,62,()->{if(selected.isEmpty()){error="至少选择一项研究；删除门槛请返回后点需求行的 ×";return;}save.accept(List.copyOf(selected));});
        button("取消",310,232,62,cancel);
        update();
    }
    private void update(){for(var w:widgets){w.visible=visible;w.active=visible&&!pending&&!loading;}
        for(var w:widgets){String label=w.getMessage().getString();if(label.equals("取消"))w.active=visible&&!pending;if(label.equals("保存"))w.active&=catalogReady&&!selected.isEmpty();}
    }
    void scroll(double delta){if(!loading&&!pending&&delta!=0){search=searchBox.getValue();page=Math.max(0,Math.min(pages-1,page-(int)Math.signum(delta)));if(!selectedOnly)query("",false);rebuild();}}
    boolean enter(){if(searchBox!=null&&searchBox.isFocused()&&!loading&&!pending){applySearch();return true;}return false;}
    void draw(GuiGraphics g,String status,boolean failed){
        g.fill(0,0,440,260,0xED101216);g.fill(54,18,386,258,0xFF242A31);
        g.drawString(font,"研究门槛 · 开工时检查全部所选成果",62,23,0xFFFFFF,false);
        if(!loading&&displayed().isEmpty())g.drawString(font,selectedOnly?"尚未选择，或没有匹配的已选研究":"没有匹配的研究；可先到“算力/说明”创建",62,96,0xD9C98E,false);
        g.drawCenteredString(font,(page+1)+" / "+pages+"  ·  已选 "+selected.size()+" / 64",217,216,0xDDDDDD);
        String message=loading?"正在读取研究…":error.isEmpty()?status:error;
        if(message.isEmpty())message="不消耗成果，不随批量放大";
        g.drawString(font,font.plainSubstrByWidth(message,122),114,237,error.isEmpty()&&!failed?0xA9D9BA:0xFF8888,false);
    }
    void tooltip(GuiGraphics g,int mx,int my,int x,int y,String status){String message=error.isEmpty()?status:error;if(message.isEmpty())message="开工时检查全部所选研究，不消耗成果；数据库中途离线不会取消已启动的配方。\n未知 ID 不会静默删除。移除整个门槛请返回后点需求行的 ×。";if(x>=114&&x<238&&y>=232&&y<250)g.renderTooltip(font,font.split(Component.literal(message),300),mx,my);}
}
