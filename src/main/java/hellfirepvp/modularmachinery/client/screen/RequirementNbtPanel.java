/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.RequirementNbtEditor;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import net.minecraft.client.gui.*;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;
/** Detached multiline NBT drafts with separate runtime/display data and source format preservation. */
final class RequirementNbtPanel {
    record Draft(int dataMode,String data,int displayMode,String display){}
    private final List<AbstractWidget> widgets=new ArrayList<>();
    private Font font;private MultiLineEditBox field;
    private Button dataTab,displayTab,format,save;private boolean visible,pending,output,preview;
    private JsonObject source=new JsonObject();private int selected;
    private final String[] text={"",""},initial={"",""};private final int[] modes={0,0},initialModes={0,0};private final boolean[] cleared={false,false};
    private String object="";private boolean local;
    private static final String[] KEYS={"nbt","nbt-display"};
    void init(Font font,int left,int top,Consumer<AbstractWidget> register,Consumer<Draft> accept,Runnable cancel){
        remember();this.font=font;save=null;widgets.clear();
        dataTab=button("匹配／输出数据",left+56,top+48,160,b->{remember();selected=0;restore();update();});
        displayTab=button("显示数据",left+224,top+48,160,b->{remember();selected=1;restore();update();});
        format=button("",left+56,top+74,160,b->{remember();modes[selected]=1-modes[selected];update();});
        format.setTooltip(Tooltip.create(Component.literal("JSON 对象：例如 {\"quality\":\">=3\"}\n模式文本：例如 {quality:>=3}\n切换只改变解释方式，不会自动转换内容；空白移除字段")));
        button("清除",left+224,top+74,68,b->{cleared[selected]=true;field.setValue("");remember();update();});
        button("预览",left+298,top+74,86,b->{remember();preview=!preview;update();});
        field=new MultiLineEditBox(font,left+56,top+102,328,64,Component.literal("留空移除此字段"),Component.literal("NBT 内容")){
            @Override public boolean keyPressed(int key,int scan,int modifiers){return active && visible && super.keyPressed(key,scan,modifiers);}
            @Override public boolean charTyped(char ch,int modifiers){return active && visible && super.charTyped(ch,modifiers);}
            @Override public boolean mouseClicked(double x,double y,int b){return active && visible && super.mouseClicked(x,y,b);}
            @Override public boolean mouseDragged(double x,double y,int b,double dx,double dy){return active && visible && super.mouseDragged(x,y,b,dx,dy);}
        };
        field.setCharacterLimit(RequirementNbtEditor.MAX_TEXT);field.setValue(text[selected]);field.setValueListener(v->{text[selected]=v;update();});widgets.add(field);
        save=button("保存",left+240,top+222,66,b->{remember();if(error().isEmpty())accept.accept(draft());});
        button("取消",left+312,top+222,72,b->cancel.run());widgets.forEach(register);update();
    }
    private Button button(String label,int x,int y,int width,Consumer<Button> action){var b=Button.builder(Component.literal(label),action::accept).bounds(x,y,width,20).build();widgets.add(b);return b;}
    void open(JsonObject req){open(req,false);}
    void open(JsonObject req,boolean local){
        this.local=local;
        source=req.deepCopy();output="output".equals(req.get("io-type").getAsString());selected=0;preview=false;
        object=(req.has("item")?req.get("item"):req.get("fluid")).getAsString();
        for(int i=0;i<2;i++){
            initial[i]=text[i]=RequirementNbtEditor.text(req,KEYS[i]);
            if(text[i].length()>RequirementNbtEditor.MAX_TEXT)throw new IllegalArgumentException("原 NBT 超过 8192 字符，请修改源文件");
            initialModes[i]=modes[i]=RequirementNbtEditor.mode(req,KEYS[i]);cleared[i]=false;
        }
        restore();setVisible(true,false);
    }
    private void remember(){if(field!=null)text[selected]=field.getValue();}
    private void restore(){if(field!=null)field.setValue(text[selected]);}
    private Draft draft(){return new Draft(mode(0),text[0],mode(1),text[1]);}
    private int mode(int i){return !cleared[i] && text[i].equals(initial[i]) && modes[i]==initialModes[i]?-1:modes[i];}
    private JsonObject merged(){var d=draft();return RequirementNbtEditor.merge(source,d.dataMode,d.data,d.displayMode,d.display);}
    private String error(){if(!source.has("io-type"))return "";try{merged();return "";}catch(RuntimeException invalid){return invalid.getMessage()==null?"NBT 无效":invalid.getMessage();}}
    void tick(){if(field!=null)field.tick();}
    void setVisible(boolean visible,boolean pending){this.visible=visible;this.pending=pending;update();}
    private void update(){
        for(var w:widgets){w.visible=visible;w.active=visible && !pending;}
        if(save==null)return;
        dataTab.setMessage(Component.literal(output?"实际输出 · nbt":"匹配条件 · nbt"));dataTab.active &= selected!=0;displayTab.active &= selected!=1;
        save.setMessage(Component.literal(local?"应用":"保存"));
        format.setMessage(Component.literal(modes[selected]==0?"格式：JSON 对象":"格式：模式文本"));save.active &= error().isEmpty();
    }
    void draw(GuiGraphics g,String status,boolean failed){
        update();g.fill(0,0,440,260,0xE0101216);g.fill(40,8,400,252,0xFF242A31);
        line(g,"需求 · NBT 数据",18,0xFFFFFF);line(g,object+(output?"（输出）":"（输入）"),34,0xB5C5D5);
        String error=error();
        if(preview && error.isEmpty()){
            var parsed=RecipeNbt.read(merged());
            String actual=output?"实际输出："+Objects.toString(parsed.output(),"无"):"输入：子集匹配；数字比较／正则；不改变被消耗物";
            line(g,actual,184,0xD9C98E);line(g,"显示："+Objects.toString(parsed.display(),"无；沿用资源默认外观"),196,0x9BDEAA);
        }else{
            line(g,selected==1?"显示数据不参与匹配或实际产出；空白跟随 nbt":output?"输出使用解析后的具体数据，比较符会被去掉":"输入使用子集匹配；数字比较及字符串正则",184,0xB5C5D5);
            line(g,local?"空白移除此层字段（候选仍继承组默认值）；应用后保存整组":"空白移除字段；{} 保留为空对象；预览不会保存",196,0xB5C5D5);
        }
        line(g,error.isEmpty()?status:error,210,!error.isEmpty()||failed?0xFF8888:0x9BDEAA);
    }
    void tooltip(GuiGraphics g,int mx,int my,int x,int y){
        if(x<56 || x>=384)return;
        if(y>=207 && y<221 && !error().isEmpty())wrappedTooltip(g,error(),mx,my);
        if(preview && y>=181 && y<206 && error().isEmpty()){
            var parsed=RecipeNbt.read(merged());String text=y<194?(output?"实际输出："+Objects.toString(parsed.output(),"无"):"输入 nbt 仅用于匹配，不写入被消耗的物品／流体") : "显示数据："+Objects.toString(parsed.display(),"无");
            wrappedTooltip(g,text,mx,my);
        }
    }
    private void wrappedTooltip(GuiGraphics g,String text,int mx,int my){
        var lines=new ArrayList<>(font.split(Component.literal(text),Math.min(328,g.guiWidth()-24)));
        int maximum=Math.max(2,Math.min(12,(g.guiHeight()-32)/Math.max(10,font.lineHeight)));
        if(lines.size()>maximum){lines=new ArrayList<>(lines.subList(0,maximum-1));lines.add(Component.literal("数据较长；请在编辑框查看原文").getVisualOrderText());}
        g.renderTooltip(font,lines,mx,my);
    }
    private void line(GuiGraphics g,String s,int y,int color){g.drawString(font,font.plainSubstrByWidth(s,328),56,y,color,false);}
}
