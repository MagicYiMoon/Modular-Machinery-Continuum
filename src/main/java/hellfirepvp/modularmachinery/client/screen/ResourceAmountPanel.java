/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.editor.ResourceAmountEditor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Resource-specific fields; optional APIs never enter the client screen's class signatures. */
final class ResourceAmountPanel {
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final Map<String, EditBox> amounts = new LinkedHashMap<>();
    private final Map<String, Button> tabs = new LinkedHashMap<>();
    private final Set<String> available = new HashSet<>();
    private List<String> auraTypes = List.of();
    private Font font;
    private Button mode, kind, save;
    private EditBox limit;
    private String selected = "mana", willKind = "raw", auraKind = "";
    private String willLimit = "0", auraLimit = "-2147483648";
    private boolean output, rate, editing, visible, pending;
    private static final List<String> IDS = List.of("mana", "ember", "lifeessence", "will", "aura");
    private static final List<String> LABELS = List.of("Botania 魔力", "Embers 余烬", "生命源质 LP", "恶魔意志", "自然灵气");
    void init(Font font, int left, int top, Consumer<AbstractWidget> adder, Consumer<JsonObject> apply, Runnable cancel) {
        var drafts = new HashMap<String,String>(); amounts.forEach((id, box) -> drafts.put(id,box.getValue())); rememberLimit();
        this.font = font; save = null; widgets.clear(); amounts.clear(); tabs.clear();
        for (int i=0;i<IDS.size();i++) {
            String id=IDS.get(i);
            int x=i<2 ? 74+i*152 : 74+(i-2)*100, y=i<2 ? 46 : 70, width=i<2 ? 140 : 92;
            var tab=Button.builder(Component.literal(LABELS.get(i)), b -> { rememberLimit(); selected=id; restoreLimit(); update(); })
                    .bounds(left+x,top+y,width,20).build(); tabs.put(id,tab); widgets.add(tab);
            var box=new EditBox(font,left+74,top+116,292,18,Component.literal(amountTitle(id)));
            box.setMaxLength(128); boolean decimal=id.equals("ember") || id.equals("will");
            box.setFilter(s -> s.chars().allMatch(c -> c>='0' && c<='9' || decimal && ".eE+-".indexOf(c)>=0));
            box.setValue(drafts.getOrDefault(id,"1000")); amounts.put(id,box); widgets.add(box);
            box.setResponder(s -> update());
        }
        mode=Button.builder(Component.empty(),b -> {rate=!rate;update();}).bounds(left+74,top+146,292,20).build();
        mode.setTooltip(Tooltip.create(Component.literal("单次：开工输入、完工输出\n逐 tick：每个成功推进的加工 tick 处理\n倍率在保存后的批量设置中修改")));widgets.add(mode);
        kind=Button.builder(Component.empty(),b -> {
            if(selected.equals("will")) willKind=next(ResourceAmountEditor.WILL_TYPES,willKind);
            else auraKind=next(auraTypes,auraKind);update();
        }).bounds(left+74,top+146,292,20).build();widgets.add(kind);
        limit=new EditBox(font,left+74,top+184,292,18,Component.literal("资源阈值"));limit.setMaxLength(128);
        limit.setFilter(s -> s.chars().allMatch(c -> c>='0' && c<='9' || ".eE+-".indexOf(c)>=0));restoreLimit();
        limit.setResponder(s -> update());widgets.add(limit);
        save=Button.builder(Component.literal("保存"),b -> {if(error().isEmpty())apply.accept(value());}).bounds(left+228,top+222,66,20).build();widgets.add(save);
        widgets.add(Button.builder(Component.literal("取消"),b -> cancel.run()).bounds(left+300,top+222,66,20).build());
        widgets.forEach(adder);update();
    }
    private static String amountTitle(String id) { return switch(id) {
        case "mana" -> "魔力用量"; case "ember" -> "余烬用量"; case "lifeessence" -> "LP 用量"; case "will" -> "意志用量"; default -> "灵气用量";
    }; }
    private static String next(List<String> list,String current) { return list.isEmpty() ? "" : list.get((list.indexOf(current)+1)%list.size()); }
    private boolean bounded() {return selected.equals("will") || selected.equals("aura");}
    private boolean rateable() {return selected.equals("mana") || selected.equals("lifeessence");}
    private void rememberLimit() {if(limit==null)return;if(selected.equals("will"))willLimit=limit.getValue();if(selected.equals("aura"))auraLimit=limit.getValue();}
    private void restoreLimit() {if(limit!=null)limit.setValue(selected.equals("will")?willLimit:auraLimit);}
    void open(JsonObject existing, boolean output, JsonObject document) {
        this.output=output; available.clear();
        if(flag(document,"manaEditorAvailable"))available.add("mana");
        if(flag(document,"emberEditorAvailable") && !output)available.add("ember");
        if(flag(document,"bloodMagicEditorAvailable")){available.add("lifeessence");available.add("will");}
        var types=new ArrayList<String>();
        if(document.has("editorAuraTypes"))document.getAsJsonArray("editorAuraTypes").forEach(e -> types.add(e.getAsString()));
        auraTypes=List.copyOf(types);if(flag(document,"auraEditorAvailable") && !auraTypes.isEmpty())available.add("aura");
        editing=existing!=null;
        selected=editing?ResourceAmountEditor.validate(existing).get("type").getAsString().substring("modularmachinery:".length())
                : IDS.stream().filter(available::contains).findFirst().orElse("mana");
        var clean=editing?ResourceAmountEditor.validate(existing):null;
        rate=clean!=null && clean.has("perTick") && clean.get("perTick").getAsBoolean();
        willKind=editing && selected.equals("will") ? clean.get("will-type").getAsString():"raw";
        auraKind=editing && selected.equals("aura") ? clean.get("aura-type").getAsString():auraTypes.contains("naturesaura:overworld")?"naturesaura:overworld":auraTypes.isEmpty()?"":auraTypes.get(0);
        willLimit=editing && selected.equals("will")?clean.get(output?"max":"min").getAsString():output?"100":"0";
        auraLimit=editing && selected.equals("aura")?clean.get(output?"max-aura":"min-aura").getAsString():output?"2147483647":"-2147483648";
        amounts.forEach((id,box)->box.setValue("1000"));
        if(clean!=null)amounts.get(selected).setValue(clean.get(selected.equals("ember")?"ember":"amount").getAsString());
        restoreLimit();setVisible(true,false);
    }
    private static boolean flag(JsonObject document,String key){return document.has(key) && document.get(key).getAsBoolean();}
    private JsonObject value() {
        if(!available.contains(selected))throw new IllegalArgumentException("服务器未提供此资源方向");
        String amount=amounts.get(selected).getValue();
        return switch(selected){
            case "mana" -> ResourceAmountEditor.mana(output,Integer.parseInt(amount),rate);
            case "ember" -> ResourceAmountEditor.ember(Double.parseDouble(amount));
            case "lifeessence" -> ResourceAmountEditor.life(output,Integer.parseInt(amount),rate);
            case "will" -> ResourceAmountEditor.will(output,Double.parseDouble(amount),willKind,output?0:Double.parseDouble(limit.getValue()),output?Double.parseDouble(limit.getValue()):100);
            default -> {
                if(!auraTypes.contains(auraKind))throw new IllegalArgumentException("服务器未注册此灵气类型");
                yield ResourceAmountEditor.aura(output,Integer.parseInt(amount),auraKind,output?Integer.MIN_VALUE:Integer.parseInt(limit.getValue()),output?Integer.parseInt(limit.getValue()):Integer.MAX_VALUE);
            }
        };
    }
    private String error(){try{value();return "";}catch(NumberFormatException invalid){return "请检查用量／阈值：整数不可越界，小数须为有限数";}catch(IllegalArgumentException invalid){return invalid.getMessage();}}
    void setVisible(boolean visible,boolean pending){this.visible=visible;this.pending=pending;update();}
    private void update(){
        for(var w:widgets){w.visible=visible;w.active=visible && !pending;}
        if(save==null)return;
        tabs.forEach((id,b)->{b.visible &= available.contains(id);b.active &= b.visible && !editing && !selected.equals(id);});
        amounts.forEach((id,b)->{b.visible &= selected.equals(id);b.active &= b.visible;});
        mode.visible &= rateable();mode.active &= mode.visible;
        mode.setMessage(Component.literal((rate?"逐 tick：":"单次：")+(selected.equals("lifeessence")?"LP":"Mana")+(rate?"/t":"")+"（单击切换）"));
        kind.visible &= bounded();kind.active &= kind.visible;
        String current=selected.equals("will")?willKind:auraKind;
        kind.setMessage(Component.literal(font.plainSubstrByWidth((selected.equals("will")?"意志":"灵气")+"类型："+current+"（切换）",270)));
        kind.setTooltip(Tooltip.create(Component.literal(current+"\n单击切换类型，输入输出方向保持不变\n"+(selected.equals("will")?"raw 原始／corrosive 腐蚀／destructive 破坏\nvengeful 复仇／steadfast 坚韧；整批只算一份": "来自服务器实际注册类型列表；用量为原生单位"))));
        limit.visible &= bounded();limit.active &= limit.visible;
        limit.setTooltip(Tooltip.create(Component.literal(selected.equals("will")?"有限非负小数；输入消耗后至少保留 min，输出产出后不得超过 max\nmin/max 分别生效，不要求 min≤max": "int 范围整数，允许负数；输入消耗后至少保留 min-aura\n输出产出后不得超过 max-aura；阈值不随批量放大")));
        save.active &= error().isEmpty();
    }
    void draw(GuiGraphics g,String status,boolean failed){
        update();g.fill(0,0,440,260,0xE0101216);g.fill(58,18,382,252,0xFF242A31);
        String label=LABELS.get(IDS.indexOf(selected));
        line(g,(editing?"编辑":"添加")+(output?"输出":"输入")+" · "+label,28,0xFFFFFF);
        line(g,amountTitle(selected)+"（"+(selected.equals("ember")?"Ember，小数":selected.equals("will")?"Will，小数，整批一份":selected.equals("lifeessence")?rate?"LP/t，整数":"LP，整数":selected.equals("mana")?rate?"Mana/t，整数":"Mana，整数":"Aura，原生整数")+"）",103,0xDDDDDD);
        if(bounded())line(g,output?"产出后上限（"+(selected.equals("will")?"max":"max-aura")+"）":"消耗后至少保留（"+(selected.equals("will")?"min":"min-aura")+"）",172,0xDDDDDD);
        else if(rateable()){
            line(g,rate?"每个成功推进的加工 tick 处理":output?"完工时输出到所选接口":"开工时从所选接口扣除",174,0xB5C5D5);
            line(g,selected.equals("mana")?"普通／火花魔力仓共用需求，无概率":"绑定血球所有者的灵魂网络，无概率",187,0xB5C5D5);
            line(g,"保存后可设置接口分组和批量倍率策略",198,0xD9C98E);
        }else{
            line(g,"仅开工时单次输入；支持小数和科学计数法",151,0xB5C5D5);
            line(g,"无输出／逐 tick／概率；0 表示无消耗",166,0xB5C5D5);
            line(g,"保存后可设置接口分组和批量倍率策略",184,0xD9C98E);
        }
        String error=error();line(g,error.isEmpty()?status:error,208,!error.isEmpty()||failed?0xFF8888:0x9BDEAA);
    }
    void tooltip(GuiGraphics g,int mouseX,int mouseY,int x,int y){if(x>=74 && x<366 && y>=205 && y<220 && !error().isEmpty())g.renderComponentTooltip(font,List.of(Component.literal(error())),mouseX,mouseY);}
    private void line(GuiGraphics g,String text,int y,int color){g.drawString(font,font.plainSubstrByWidth(text,292),74,y,color,false);}
}
