/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Bounded pages over one retained draft; resizing and tab switches never discard hidden settings. */
final class HyperNetSettingsPanel {
    private final ResearchDefinitionPanel research=new ResearchDefinitionPanel();
    private Button saveButton;
    private final List<AbstractWidget> widgets=new ArrayList<>(), fields=new ArrayList<>();
    private final List<EditBox> boxes=new ArrayList<>();
    private final List<String> labels=new ArrayList<>();
    private Font font; private int left,top,page,module,previewScroll; private boolean visible,pending,moduleInfo,preview,confirmRole;
    private JsonObject draft=new JsonObject(); private JsonArray upgrades=new JsonArray();
    private String machineName=""; private String error=""; private Consumer<AbstractWidget> adder,remover; private Consumer<JsonObject> save; private Runnable cancel;
    private static final String[] PAGES={"等级说明","网络","模块","散热","电路维修","研究"};
    void init(Font f,int l,int t,Consumer<AbstractWidget> add,Consumer<AbstractWidget> remove,Consumer<JsonObject> save,Runnable cancel,hellfirepvp.modularmachinery.common.menu.MenuMachineEditor menu,Consumer<Object> mutation){
        var retained=boxes.stream().map(EditBox::getValue).toList();boolean captured=capture();String retainedError=error;
        font=f;left=l;top=t;adder=add;remover=remove;this.save=save;this.cancel=cancel;
        widgets.clear();fields.clear();boxes.clear();labels.clear();
        research.init(f,l,t,add,remove,menu,mutation);
        for(int i=0;i<PAGES.length;i++){final int n=i;button(PAGES[i],62+i*52,36,50,()->{if(!capture())return;if(page==5)research.close();page=n;moduleInfo=false;preview=false;if(page==5)research.open();rebuild();});}
        saveButton=button("保存",242,232,62,()->{if(!capture())return;try{save.accept(HyperNetSettingsEditor.validate(draft));}catch(RuntimeException e){error=e.getMessage();}});
        button("返回",310,232,62,cancel);
        rebuild();
        for(int i=0;i<Math.min(retained.size(),boxes.size());i++)boxes.get(i).setValue(retained.get(i));
        if(!captured)error=retainedError;
        setVisible(false,false);
    }
    void open(JsonObject machine,JsonObject document){
        research.reset();
        machineName=machine.get("name").getAsString();
        draft=machine.getAsJsonObject("hypernetSettings").deepCopy();
        upgrades=document.has("hypernetUpgradeTypes")?document.getAsJsonArray("hypernetUpgradeTypes").deepCopy():new JsonArray();
        page=module=previewScroll=0;moduleInfo=preview=confirmRole=false;error="";rebuild();
    }
    private Button button(String text,int x,int y,int w,Runnable action){
        var b=Button.builder(Component.literal(text),ignored->action.run()).bounds(left+x,top+y,w,18).build();
        widgets.add(b);adder.accept(b);return b;
    }
    private Button localButton(String text,int x,int y,int w,Runnable action){var b=button(text,x,y,w,action);fields.add(b);return b;}
    private void field(String label,String value,int row,int max){
        int y=61+row*17;labels.add(label);
        var box=new EditBox(font,left+155,top+y,217,15,Component.literal(label));box.setMaxLength(max);box.setValue(MachineNameFormatting.editable(value));
        if(label.equals("显示阶数"))box.setTooltip(Tooltip.create(Component.literal("例如 T1、T2、三级；支持 &a 颜色代码\n仅作显示，不限制模块或配方；能力由参数决定")));
        boxes.add(box);widgets.add(box);fields.add(box);adder.accept(box);
    }
    private JsonObject network(){return draft.has("hypernet")&&!draft.get("hypernet").isJsonNull()?draft.getAsJsonObject("hypernet"):null;}
    private String role(){return network()==null?"off":network().get("role").getAsString();}
    private JsonArray modules(){var n=network();if(!n.has("modules"))n.add("modules",new JsonArray());return n.getAsJsonArray("modules");}
    private JsonObject selectedModule(){var a=modules();return a.isEmpty()?null:a.get(Math.min(module,a.size()-1)).getAsJsonObject();}
    private JsonObject info(){
        if(moduleInfo){var m=selectedModule();if(!m.has("display-info"))m.add("display-info",new JsonObject());return m.getAsJsonObject("display-info");}
        return draft.getAsJsonObject("display-info");
    }
    private static String value(JsonObject j,String key,String fallback){return j!=null&&j.has(key)?j.get(key).getAsString():fallback;}
    private void rebuild(){
        // Remove previous page widgets from rendering, focus and narration.
        for(var w:fields){w.visible=false;w.active=false;w.setFocused(false);widgets.remove(w);remover.accept(w);}fields.clear();boxes.clear();labels.clear();
        error="";
        if(page==5){update();return;}
        if((page==0||moduleInfo)&&draft.has("display-info")){
            var info=info();field("显示阶数",value(info,"tier",""),0,64);
            var tips=info.has("tooltips")?info.getAsJsonArray("tooltips"):new JsonArray();
            for(int i=0;i<8;i++)field("说明 "+(i+1),i<tips.size()?tips.get(i).getAsString():"",i+1,200);
            localButton(preview?"返回输入":"预览",62,216,76,()->{if(!capture())return;preview=!preview;previewScroll=0;rebuild();});
            if(moduleInfo)localButton("返回模块",142,216,76,()->{if(!capture())return;moduleInfo=false;rebuild();});
        }else if(page==1){
            localButton("角色："+switch(role()){case "off"->"停用";case "center"->"网络中心";case "processor"->"处理器";case "node"->"普通节点";case "database"->"数据库";case "research_station"->"研究站";default->"独立终端";},62,62,110,()->{
                if(machineName.equals("hypernet_terminal")){error="独立终端固定使用 terminal 角色";return;}
                if(!capture())return;String next=switch(role()){case "off"->"center";case "center"->"processor";case "processor"->"node";case "node"->"database";case "database"->"research_station";default->"off";};
                // Changing role is explicit; do not silently remove an existing configured backend.
                if(network()!=null&&network().size()>1&&!confirmRole){confirmRole=true;error="再次点击角色，将清除当前网络参数（未保存）";return;}confirmRole=false;
                if(next.equals("off"))draft.add("hypernet",JsonNull.INSTANCE);else {var n=new JsonObject();n.addProperty("role",next);
                    if(next.equals("center")){n.addProperty("carrying",1000);n.addProperty("max-connections",16);n.addProperty("max-nodes-per-type",16);}if(next.equals("database"))n.addProperty("storage-capacity",64);if(next.equals("research_station"))n.addProperty("max-tech-level",1);draft.add("hypernet",n);}rebuild();
            });
            localButton("停用网络",266,62,106,()->{if(machineName.equals("hypernet_terminal")){error="独立终端必须保留 terminal 角色";return;}if(!capture())return;draft.add("hypernet",JsonNull.INSTANCE);rebuild();});
            var n=network();if(n!=null){
                if(!role().equals("node")&&!role().equals("terminal"))field("核心线程",value(n,"working-thread",role().equals("research_station")?"novaeng.hypernet.research_station.hypercol":(role().equals("center")||role().equals("database"))?"novaeng.hypernet.data_forwarding_processor":"novaeng.hypernet.data_processor.compute_scheduler"),2,256);
                if(role().equals("database"))field("成果容量 1～4096",value(n,"storage-capacity","64"),3,10);
                if(role().equals("research_station"))field("最高科技等级",value(n,"max-tech-level","1"),3,128);
                if(role().equals("center")){
                    field("承载算力 /t",value(n,"carrying","1000"),3,128);field("连接数上限",value(n,"max-connections","16"),4,10);
                    field("每类节点上限",value(n,"max-nodes-per-type","1000"),5,10);field("连接卡物品 ID",value(n,"connection-card",hellfirepvp.modularmachinery.common.hypernet.HyperNetDefinition.DEFAULT_CONNECTION_CARD),6,256);
                    localButton("公平调度："+(n.has("fair-scheduling")&&n.get("fair-scheduling").getAsBoolean()?"开":"关"),155,181,217,()->{
                        if(!capture())return;n.addProperty("fair-scheduling",!n.has("fair-scheduling")||!n.get("fair-scheduling").getAsBoolean());rebuild();
                    }).setTooltip(Tooltip.create(Component.literal("默认关闭，沿用源版请求顺序。\n开启后，已运行配方轮流获得算力；缺材料会让位。")));
                }
            }
        }else if(page==2&&role().equals("processor")){
            var a=modules();module=Math.min(module,Math.max(0,a.size()-1));
            localButton("←",62,62,26,()->{if(!capture())return;module=Math.max(0,module-1);rebuild();});
            localButton("→",92,62,26,()->{if(!capture())return;module=Math.min(a.size()-1,module+1);rebuild();});
            localButton("+ 模块",212,62,76,()->{if(!capture())return;if(a.size()>=256){error="最多256个模块映射";return;}var m=new JsonObject();m.addProperty("upgrade",upgrades.isEmpty()?"":upgrades.get(0).getAsString());m.addProperty("kind","cpu");m.addProperty("capacity",1);m.addProperty("energy",0);a.add(m);module=a.size()-1;rebuild();});
            localButton("删除",294,62,78,()->{if(!a.isEmpty())a.remove(module);rebuild();});
            var m=selectedModule();if(m!=null){
                field("升级类型 ID",value(m,"upgrade",""),2,256);
                localButton("选注册类型",62,112,88,()->{if(!upgrades.isEmpty()){String current=boxes.get(0).getValue();int at=-1;for(int i=0;i<upgrades.size();i++)if(upgrades.get(i).getAsString().equals(current))at=i;boxes.get(0).setValue(upgrades.get((at+1)%upgrades.size()).getAsString());}});
                localButton("种类："+value(m,"kind","cpu"),155,109,217,()->{if(!capture())return;m.addProperty("kind",switch(value(m,"kind","cpu").toLowerCase(Locale.ROOT)){case "cpu"->"gpu";case "gpu"->"ram";default->"cpu";});rebuild();});
                field("容量",value(m,"capacity","1"),4,128);field("满载 FE/t",value(m,"energy","0"),5,20);
                localButton("模块等级与说明",155,169,217,()->{if(!capture())return;moduleInfo=true;rebuild();});
            }
        }else if(page==3&&role().equals("processor")){
            var n=network();localButton(n.has("thermal")?"停用热量":"启用热量",62,62,140,()->{if(!capture())return;if(n.has("thermal"))n.remove("thermal");else{var j=new JsonObject();j.addProperty("overheat-threshold",1000);j.addProperty("heat-distribution",4);n.add("thermal",j);}rebuild();});
            if(n.has("thermal")){var j=n.getAsJsonObject("thermal");field("过热阈值 HU",value(j,"overheat-threshold","1000"),2,10);field("散热参数",value(j,"heat-distribution","4"),3,10);field("动态结构名称",value(j,"dynamic-pattern",""),4,256);}
        }else if(page==4&&role().equals("center")){
            var n=network();localButton(n.has("circuit")?"停用电路磨损":"启用电路磨损",62,62,140,()->{if(!capture())return;if(n.has("circuit"))n.remove("circuit");else{var j=new JsonObject();j.addProperty("capacity",10000);j.addProperty("min-wear",1);j.addProperty("max-wear",3);j.addProperty("wear-chance",0.1);n.add("circuit",j);}rebuild();});
            if(n.has("circuit")){var j=n.getAsJsonObject("circuit");field("耐久容量",value(j,"capacity","10000"),2,10);field("最低磨损",value(j,"min-wear","1"),3,10);field("最高磨损(不含)",value(j,"max-wear","3"),4,10);field("磨损概率 0～1",value(j,"wear-chance","0.1"),5,128);}
        }
        update();
    }
    private void number(JsonObject j,String key,String text){try{j.addProperty(key,new java.math.BigDecimal(text));}catch(RuntimeException e){throw new IllegalArgumentException(key+" 必须是数字");}}
    private boolean capture(){
        if(boxes.isEmpty()||!draft.has("display-info"))return true;
        try{
            if(page==0||moduleInfo){var j=info();j.addProperty("tier",boxes.get(0).getValue());var a=new JsonArray();int last=8;while(last>0&&boxes.get(last).getValue().isEmpty())last--;for(int i=1;i<=last;i++)a.add(boxes.get(i).getValue());j.add("tooltips",a);}
            else if(page==1){var n=network();if(!role().equals("node")&&!role().equals("terminal"))n.addProperty("working-thread",boxes.get(0).getValue());if(role().equals("database"))number(n,"storage-capacity",boxes.get(1).getValue());if(role().equals("research_station"))number(n,"max-tech-level",boxes.get(1).getValue());if(role().equals("center")){number(n,"carrying",boxes.get(1).getValue());number(n,"max-connections",boxes.get(2).getValue());number(n,"max-nodes-per-type",boxes.get(3).getValue());String card=boxes.get(4).getValue();if(card.isBlank())n.remove("connection-card");else n.addProperty("connection-card",card);}}
            else if(page==2){var m=selectedModule();m.addProperty("upgrade",boxes.get(0).getValue());number(m,"capacity",boxes.get(1).getValue());number(m,"energy",boxes.get(2).getValue());}
            else if(page==3){var j=network().getAsJsonObject("thermal");number(j,"overheat-threshold",boxes.get(0).getValue());number(j,"heat-distribution",boxes.get(1).getValue());String p=boxes.get(2).getValue();if(p.isBlank())j.remove("dynamic-pattern");else j.addProperty("dynamic-pattern",p);}
            else if(page==4){var j=network().getAsJsonObject("circuit");for(int i=0;i<4;i++)number(j,new String[]{"capacity","min-wear","max-wear","wear-chance"}[i],boxes.get(i).getValue());}
            error="";return true;
        }catch(RuntimeException e){error=e.getMessage();return false;}
    }
    void setVisible(boolean visible,boolean pending){this.visible=visible;this.pending=pending;update();}
    private void update(){research.setVisible(visible&&page==5,pending);for(var w:widgets){w.visible=visible;w.active=visible&&!pending;}for(var w:widgets)if(w.getMessage().getString().equals("返回")){w.setY(top+(page==5?20:232));w.setHeight(page==5?14:18);}if(saveButton!=null&&page==5){saveButton.visible=false;saveButton.active=false;}if(preview)for(var b:boxes){b.visible=false;b.active=false;}}
    void scroll(double delta){if(page==5){research.scroll(delta);return;}if(preview)previewScroll=Math.max(0,previewScroll-(int)Math.signum(delta));}
    void tooltip(GuiGraphics g,int mouseX,int mouseY,int x,int y,String status){
        if(page==5){research.tooltip(g,mouseX,mouseY,x,y,status);return;}
        String message=error.isEmpty()?status:error;
        if(!message.isEmpty()&&x>=62&&x<234&&y>=232&&y<250)g.renderTooltip(font,font.split(Component.literal(message),300),mouseX,mouseY);
    }
    void draw(GuiGraphics g,String status,boolean failed){
        g.fill(0,0,440,260,0xED101216);g.fill(54,18,386,258,0xFF242A31);
        g.drawString(font,"算力网络 · 等级与说明",62,23,0xFFFFFF,false);
        if(page==5){research.draw(g,status,failed);return;}
        if(preview){var lines=new ArrayList<Component>();hellfirepvp.modularmachinery.common.machine.MachineDisplayInfo.parse(info()).append(lines);
            var wrapped=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(var line:lines)wrapped.addAll(font.split(line,300));
            int rows=Math.max(1,142/(font.lineHeight+2));previewScroll=Math.min(previewScroll,Math.max(0,wrapped.size()-rows));
            int y=66;for(int i=previewScroll;i<wrapped.size()&&i<previewScroll+rows;i++){g.drawString(font,wrapped.get(i),64,y,0xDDDDDD,false);y+=font.lineHeight+2;}
            if(wrapped.size()>rows)g.drawString(font,"滚轮翻阅说明",224,220,0xD9C98E,false);
        }
        else for(int i=0;i<labels.size();i++)g.drawString(font,font.plainSubstrByWidth(labels.get(i),90),62,65+(page==0||moduleInfo?i:page==1?i+(role().equals("center")?2:2):page==2?i==0?2:i+3: i+2)*17,0xDDDDDD,false);
        if(page==1){
            String note=switch(role()){case "terminal"->"独立终端：离线 200／联网 2000 FE/t；结构由作者配置";case "database"->"容量按成果条目计；缩小不会删除已存研究";case "research_station"->"科技上限控制可研究范围；核心配方绑定上方线程";default->"核心配方需绑定同名预设线程；停用会断开网络";};
            var lines=font.split(Component.literal(note),308);for(int i=0;i<Math.min(2,lines.size());i++)g.drawString(font,lines.get(i),62,199+i*11,0xD9C98E,false);
        }
        if(page==2&&!moduleInfo){if(!role().equals("processor"))g.drawString(font,"仅处理器可配置 CPU／GPU／RAM 模块",62,94,0xD9C98E,false);else {g.drawString(font,(modules().isEmpty()?"0":Integer.toString(module+1))+" / "+modules().size(),126,67,0xDDDDDD,false);g.drawString(font,"算力取 CPU+GPU、RAM 和系统上限的最小值",62,199,0xD9C98E,false);}}
        if(page==3&&!role().equals("processor"))g.drawString(font,"仅处理器可启用热量",62,94,0xD9C98E,false);
        if(page==4&&!role().equals("center"))g.drawString(font,"仅网络中心可启用电路耐久",62,94,0xD9C98E,false);
        String message=error.isEmpty()?status:error;g.drawString(font,font.plainSubstrByWidth(message,170),62,236,error.isEmpty()&&!failed?0x9BDEAA:0xFF8888,false);
    }
}
