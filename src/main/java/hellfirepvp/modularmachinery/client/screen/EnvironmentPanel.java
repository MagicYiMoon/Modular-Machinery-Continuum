/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.EnvironmentEditor;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Non-consuming environmental constraints with explicit units and discrete moon/weather choices. */
final class EnvironmentPanel {
    private static final String[] CONDITIONS={"altitude","time","dimensions","biomes","moonphase","weather"};
    private static final String[] TITLES={"高度 Y","时间 tick","维度","群系／标签","月相","天气"};
    private static final String[] WEATHER={"sunny","raining","snowing","thundering"};
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private final Map<String,String[]> drafts=new HashMap<>();
    private final List<Button> tabs=new ArrayList<>(),phases=new ArrayList<>(),weathers=new ArrayList<>();
    private final boolean[] selected=new boolean[8];
    private Font font; private EditBox min,max; private MultiLineEditBox values; private Button save;
    private String condition="altitude",weather="sunny"; private boolean visible,pending,editing;
    void init(Font font,int left,int top,Consumer<AbstractWidget> adder,Consumer<JsonObject> apply,Runnable cancel) {
        remember();this.font=font;widgets.clear();tabs.clear();phases.clear();weathers.clear();
        for(int i=0;i<6;i++){final String c=CONDITIONS[i];var b=Button.builder(Component.literal(TITLES[i]),btn->{remember();condition=c;restore();update();}).bounds(left+74+i%3*100,top+46+i/3*24,92,20).build();tabs.add(b);widgets.add(b);}
        min=new EditBox(font,left+74,top+116,140,18,Component.literal("最小值"));max=new EditBox(font,left+226,top+116,140,18,Component.literal("最大值"));
        for(var b:List.of(min,max)){b.setMaxLength(16);b.setFilter(s->s.matches("-?[0-9]*"));b.setResponder(s->update());widgets.add(b);}
        values=new MultiLineEditBox(font,left+74,top+116,292,64,Component.literal("每行一个命名空间 ID"),Component.literal("维度或群系列表")) {
            @Override public boolean keyPressed(int k,int s,int m){return active&&visible&&super.keyPressed(k,s,m);}
            @Override public boolean charTyped(char c,int m){return active&&visible&&super.charTyped(c,m);}
            @Override public boolean mouseClicked(double x,double y,int b){return active&&visible&&super.mouseClicked(x,y,b);}
        };
        values.setCharacterLimit(8192);values.setValueListener(s->update());widgets.add(values);
        for(int i=0;i<8;i++){final int n=i;var b=Button.builder(Component.literal(""),btn->{selected[n]=!selected[n];update();}).bounds(left+74+i%4*75,top+116+i/4*28,67,22).build();phases.add(b);widgets.add(b);}
        String[] weatherNames={"晴天","下雨","下雪","雷暴"};
        for(int i=0;i<4;i++){final String w=WEATHER[i];var b=Button.builder(Component.literal(weatherNames[i]),btn->{weather=w;update();}).bounds(left+74+i%2*152,top+116+i/2*28,140,22).build();weathers.add(b);widgets.add(b);}
        save=Button.builder(Component.literal("保存"),btn->{if(error().isEmpty())apply.accept(value());}).bounds(left+228,top+222,66,20).build();widgets.add(save);
        widgets.add(Button.builder(Component.literal("取消"),btn->cancel.run()).bounds(left+300,top+222,66,20).build());
        restore();widgets.forEach(adder);update();
    }
    private void remember(){if(min!=null)drafts.put(condition,new String[]{min.getValue(),max.getValue(),values.getValue()});}
    private void restore(){var d=drafts.getOrDefault(condition,new String[]{condition.equals("time")?"0":"64",condition.equals("time")?"23999":"128",condition.equals("biomes")?"minecraft:plains\n#minecraft:is_forest":"minecraft:overworld"});min.setValue(d[0]);max.setValue(d[1]);values.setValue(d[2]);}
    void open(JsonObject existing){editing=existing!=null;drafts.clear();Arrays.fill(selected,false);selected[0]=true;weather="sunny";
        var j=existing==null?JsonParser.parseString("{\"type\":\"modularmachinery:environment\",\"io-type\":\"input\",\"condition\":\"altitude\",\"min\":64,\"max\":128}").getAsJsonObject():EnvironmentEditor.validate(existing);
        condition=j.get("condition").getAsString();String text="";
        if(j.has("values")){var lines=new ArrayList<String>();j.getAsJsonArray("values").forEach(v->lines.add(v.getAsString()));text=String.join("\n",lines);}
        drafts.put(condition,new String[]{j.has("min")?j.get("min").getAsString():"0",j.has("max")?j.get("max").getAsString():"23999",text});
        if(condition.equals("moonphase")){Arrays.fill(selected,false);j.getAsJsonArray("values").forEach(v->selected[v.getAsInt()]=true);}
        if(j.has("weather"))weather=j.get("weather").getAsString();restore();setVisible(true,false);
    }
    private JsonObject value(){var j=new JsonObject();j.addProperty("type","modularmachinery:environment");j.addProperty("io-type","input");j.addProperty("condition",condition);
        switch(condition){case "altitude","time"->{j.addProperty("min",Integer.parseInt(min.getValue()));j.addProperty("max",Integer.parseInt(max.getValue()));}
            case "weather"->j.addProperty("weather",weather);
            default->{var list=new JsonArray();if(condition.equals("moonphase")){for(int i=0;i<8;i++)if(selected[i])list.add(i);}else for(String line:values.getValue().split("[\\s,，]+"))if(!line.isBlank())list.add(line);j.add("values",list);}}
        return EnvironmentEditor.validate(j);
    }
    private String error(){if(min==null||max==null||values==null)return "";try{value();return "";}catch(NumberFormatException e){return "请输入有效的 32 位整数";}catch(RuntimeException e){return switch(condition){case "altitude"->"高度须为整数，且最小 Y ≤ 最大 Y";case "time"->"时间两端须在 0～23999；最小值较大时跨午夜";case "moonphase"->"至少选择一个月相（0～7）";default->"请填写 1～256 个完整 ID；仅群系支持 #标签";};}}
    void setVisible(boolean visible,boolean pending){this.visible=visible;this.pending=pending;update();}
    private void update(){for(var w:widgets){w.visible=visible;w.active=visible&&!pending;}if(save==null||tabs.size()!=6||phases.size()!=8||weathers.size()!=4)return;
        for(int i=0;i<6;i++)tabs.get(i).active&=!CONDITIONS[i].equals(condition);
        min.visible=max.visible=visible&&(condition.equals("altitude")||condition.equals("time"));values.visible=visible&&(condition.equals("dimensions")||condition.equals("biomes"));
        for(int i=0;i<8;i++){var b=phases.get(i);b.visible=visible&&condition.equals("moonphase");b.setMessage(Component.literal((selected[i]?"☑ ":"☐ ")+i));}
        for(int i=0;i<4;i++){var b=weathers.get(i);b.visible=visible&&condition.equals("weather");b.active&=!WEATHER[i].equals(weather);}
        save.active&=error().isEmpty();
    }
    void tick(){min.tick();max.tick();}
    void draw(GuiGraphics g,String status,boolean failed){update();g.fill(0,0,440,260,0xE0101216);g.fill(58,18,382,252,0xFF242A31);
        line(g,(editing?"编辑":"添加")+"输入 · 环境条件",28,0xFFFFFF);
        line(g,switch(condition){case "altitude"->"最小 Y（包含）              最大 Y（包含）";case "time"->"开始时间（包含）            结束时间（包含）";case "dimensions"->"维度 ID，每行一项，也可用空格或逗号分隔";case "biomes"->"群系 ID／#标签，每行一项，任一匹配即可";case "moonphase"->"选择允许的月相；0 满月，4 新月（仅夜间）";default->"选择天气；下雪还检查检测器上方天空及群系";},102,0xDDDDDD);
        if(condition.equals("time")){line(g,"0～23999 tick；开始值较大表示跨午夜",147,0xD9C98E);line(g,"0 日出 · 6000 正午 · 12000 日落 · 18000 午夜",162,0xB5C5D5);}
        if(condition.equals("altitude"))line(g,"检测器自身的世界 Y 坐标，支持负数",147,0xB5C5D5);
        line(g,"需环境检测器；接口分组可在保存后单独设置",184,0xD9C98E);line(g,"开工和每个加工 tick 检查；不消耗，不随批量缩放",197,0xB5C5D5);
        String error=error();line(g,error.isEmpty()?status:error,209,!error.isEmpty()||failed?0xFF8888:0x9BDEAA);
    }
    void tooltip(GuiGraphics g,int mx,int my,int x,int y){if(x>=74&&x<366&&y>=205&&y<222&&!error().isEmpty())g.renderComponentTooltip(font,List.of(Component.literal(error())),mx,my);}
    private void line(GuiGraphics g,String s,int y,int color){g.drawString(font,font.plainSubstrByWidth(s,292),74,y,color,false);}
}
