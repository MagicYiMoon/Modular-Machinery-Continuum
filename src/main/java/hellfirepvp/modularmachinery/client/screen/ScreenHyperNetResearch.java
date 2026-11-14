/* SPDX-License-Identifier: GPL-3.0-only
 * Original 350x250 layout, list tint/progress and controls adapt NECO GuiHyperNetTerminal, b37fbb53.
 */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import hellfirepvp.modularmachinery.common.editor.MachineNameFormatting;
import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.hypernet.HyperNetResearchTerminal.*;
import hellfirepvp.modularmachinery.common.menu.MenuHyperNetResearch;
import hellfirepvp.modularmachinery.common.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.util.FormattedCharSequence;
import java.math.*;
import java.util.*;

/** Original terminal wells and slot coordinates; uniform fit only when the window is smaller than 350x250. */
public final class ScreenHyperNetResearch extends AbstractContainerScreen<MenuHyperNetResearch> {
    private static final ResourceLocation BG=new ResourceLocation("modularmachinery","textures/gui/neco/hypernet_monitor.png");
    private static final ResourceLocation ELEMENTS=new ResourceLocation("modularmachinery","textures/gui/neco/hypernet_terminal_elements.png");
    private EditBox search;private int searchDelay=-1,descriptionScroll;private long pending=-1,seen=-1;
    private boolean showOriginal,cancelConfirm;private String descriptionKey="",cancelTask="";private Detail previousDetail;
    private int dragging,cancelTicks,queuedPage=-1;
    private final Map<HyperNetResearchIcon,ItemStack> icons=new HashMap<>();
    private List<FormattedCharSequence> description=List.of();private float fit=1;
    public ScreenHyperNetResearch(MenuHyperNetResearch menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=350;imageHeight=250;}
    public static void receive(HyperNetResearchPackets.Update p){var player=Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu instanceof MenuHyperNetResearch menu&&menu.containerId==p.container())menu.accept(p.session(),p.data());}
    private static Component tr(String key,Object...args){return Component.translatable(HyperNetResearchTerminal.PREFIX+key,args);}
    private static Component name(String text){return SmoothUiFonts.compact(Component.literal(MachineNameFormatting.display(text)));}
    private static Component state(Row r){return tr("state."+r.state().name().toLowerCase(Locale.ROOT));}
    private static String flops(double value){
        var format=java.text.NumberFormat.getNumberInstance(Locale.ROOT);format.setMaximumFractionDigits(1);
        return format.format(value<1000?value:value/1000)+(value<1000?"T FloPS":"P FloPS");
    }
    private static String number(String raw){var n=new BigDecimal(raw);return n.signum()==0?"0":n.abs().compareTo(new BigDecimal("1E9"))<0&&n.abs().compareTo(new BigDecimal("1E-4"))>=0?n.setScale(3,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString():n.round(new MathContext(5)).toString();}
    private boolean canCreativeStart(Snapshot data){
        var player=Minecraft.getInstance().player;
        return player!=null&&player.isCreative()&&player.hasPermissions(2)&&data.selected()!=null
                &&data.task().isEmpty()&&data.selected().row().state()!=State.COMPLETED;
    }
    private void send(int action,int page,String selected){var data=menu.snapshot();if(data==null||pending>=data.token())return;
        ModularmachineryNetwork.CHANNEL.sendToServer(new HyperNetResearchPackets.Request(menu.containerId,menu.session(),data.token(),action,page,search.getValue(),selected));pending=data.token();}
    @Override protected void init(){super.init();fit=Math.min(1,Math.min((width-4)/350F,(height-4)/250F));
        String previous=search==null?"":search.getValue();
        search=addRenderableWidget(new EditBox(font,leftPos+25,topPos+31,77,12,tr("search")));search.setBordered(false);search.setMaxLength(128);search.setValue(previous);
        search.setResponder(s->{searchDelay=5;cancelConfirm=false;queuedPage=-1;});
    }
    @Override public void containerTick(){super.containerTick();var data=menu.snapshot();
        if(data==null)icons.clear();
        if(data!=null&&data.token()!=seen){seen=data.token();pending=-1;
            icons.keySet().retainAll(data.rows().stream().map(Row::previewIcon).toList());
            if(!data.canCancel()||!data.task().equals(cancelTask))cancelConfirm=false;
        }
        if(cancelTicks>0&&--cancelTicks==0)cancelConfirm=false;
        if(searchDelay>=0&&--searchDelay<0&&data!=null)send(0,0,"");
        if(queuedPage>=0&&pending<0&&data!=null){int page=Math.min(data.pages()-1,queuedPage);queuedPage=-1;if(page!=data.page())send(0,page,"");}
    }
    private void text(GuiGraphics g,Component value,int x,int y,int max,int color){
        var styled=SmoothUiFonts.compact(value);if(font.width(styled)>max)styled=Component.literal(font.substrByWidth(styled,Math.max(0,max-6)).getString()+"…").withStyle(styled.getStyle());
        g.drawString(font,styled,leftPos+x,topPos+y,color,false);
    }
    private void small(GuiGraphics g,Component value,int x,float y,int max,int color){
        g.pose().pushPose();g.pose().translate(leftPos+x,topPos+y,0);g.pose().scale(.72F,.72F,1);
        var styled=SmoothUiFonts.compact(value);if(font.width(styled)>max/.72F)styled=Component.literal(font.substrByWidth(styled,Math.max(0,(int)(max/.72F)-6)).getString()+"…").withStyle(styled.getStyle());
        g.drawString(font,styled,0,0,color,false);g.pose().popPose();
    }
    private boolean over(double x,double y,int left,int top,int w,int h){return x>=leftPos+left&&x<leftPos+left+w&&y>=topPos+top&&y<topPos+top+h;}
    private void updateDescription(Detail d){
        String key=d.row().id()+":"+showOriginal;
        if(d.equals(previousDetail)&&key.equals(descriptionKey))return;
        if(!key.equals(descriptionKey))descriptionScroll=0;descriptionKey=key;previousDetail=d;
        var list=new ArrayList<FormattedCharSequence>();
        var lines=d.row().state()==State.LOCKED?List.of(tr("locked_description").getString())
                :d.row().state()==State.COMPLETED&&!showOriginal?d.unlocks():d.descriptions();
        for(var line:lines)list.addAll(font.split(name(line),(int)(217/.72F)));
        description=List.copyOf(list);descriptionScroll=Math.max(0,Math.min(descriptionScroll,Math.max(0,description.size()-11)));
    }
    @Override protected void renderBg(GuiGraphics g,float partial,int mouseX,int mouseY){
        g.blit(BG,leftPos,topPos,0,0,350,250,350,250);text(g,tr("title"),8,8,332,0xFFFFFF);
        if(search.getValue().isEmpty()&&!search.isFocused())small(g,tr("search"),25,32,76,0xAAAAAA);
        var data=menu.snapshot();if(data==null){text(g,tr("waiting"),115,46,215,0xFFFFFF);return;}
        for(int i=0;i<data.rows().size();i++){
            var row=data.rows().get(i);int y=44+i*22;boolean selected=data.selected()!=null&&data.selected().row().id().equals(row.id());
            if(selected)g.setColor(.4F,.6F,1,1);else if(row.state()==State.LOCKED||row.state()==State.UNAVAILABLE)g.setColor(1,.6F,.6F,1);
            else if(row.state()!=State.COMPLETED)g.setColor(1,.85F,.45F,1);else g.setColor(.7F,.7F,.7F,1);
            g.blit(ELEMENTS,leftPos+8,topPos+y,0,0,92,22,256,256);
            double progress=new BigDecimal(row.progress()).divide(new BigDecimal(row.required()),8,RoundingMode.DOWN).doubleValue();
            if(progress>0){g.setColor(.6F,1,.6F,1);g.blit(ELEMENTS,leftPos+8,topPos+y,0,0,(int)(92*Math.min(1,progress)),22,256,256);}
            g.setColor(1,1,1,1);
            g.renderItem(icons.computeIfAbsent(row.previewIcon(),HyperNetResearchIcon::stack),leftPos+11,topPos+y+3);
            small(g,name(row.name()),29,y+4,68,0x303030);
            small(g,row.state()==State.RUNNING?tr("progress",String.format(Locale.ROOT,"%.2f",progress*100)):state(row),29,y+13,68,0x303030);
        }
        // Source scrollbar wells; bounded server pages replace the full catalog cache.
        g.fill(leftPos+101,topPos+44,leftPos+104,topPos+242,0xFF202020);
        int thumb=topPos+44+(int)(180F*data.page()/Math.max(1,data.pages()-1));g.fill(leftPos+101,thumb,leftPos+104,thumb+18,0xFF91A2B2);
        if(data.rows().isEmpty())text(g,tr("empty"),115,46,215,0xDDDDDD);
        var d=data.selected();if(d!=null){
            updateDescription(d);text(g,name(d.row().name()),115,46,155,0xFFFFFF);small(g,tr("tech",d.tech()),276,47,55,0xC6D9EF);
            g.pose().pushPose();g.pose().translate(leftPos+115,topPos+60,0);g.pose().scale(.72F,.72F,1);
            for(int i=0;i<11&&i+descriptionScroll<description.size();i++)g.drawString(font,description.get(i+descriptionScroll),0,i*10,0xEEEEEE,false);
            g.pose().popPose();
            small(g,tr("dependencies",d.dependencies().isEmpty()?"—":String.join(", ",d.dependencies())),115,141,174,0xDADADA);
            small(g,tr("points",number(d.row().progress()),number(d.row().required())),115,151,125,0xDADADA);
            small(g,tr("minimum",number(d.minimum())),242,151,48,0xDADADA);
            g.blit(ELEMENTS,leftPos+316,topPos+143,60,22,16,16,256,256);
            if(d.row().state()!=State.AVAILABLE)g.fill(leftPos+316,topPos+143,leftPos+332,topPos+159,0x66000000);
            if(d.row().state()==State.COMPLETED)g.blit(ELEMENTS,leftPos+296,topPos+143,76,22,16,16,256,256);
            int descThumb=topPos+44+(int)(100F*descriptionScroll/Math.max(1,description.size()-11));g.fill(leftPos+337,descThumb,leftPos+340,descThumb+18,0xFF91A2B2);
        }
        // Source row order; lift the final row to fit the smooth font above the texture border.
        var metrics=data.network();
        small(g,tr(metrics.online()?"network_online":"network_offline"),113,170,62,metrics.online()?0x77D794:0xEBA86D);
        if(metrics.online()){
            small(g,tr("network_consumed"),113,185,62,0x5555FF);
            small(g,Component.literal(flops(metrics.consumed())),113,192.2F,62,0x55FFFF);
            small(g,tr("network_offered"),113,199.4F,62,0x5555FF);
            small(g,Component.literal(flops(metrics.offered())),113,206.6F,62,0x55FFFF);
            small(g,tr("network_connections",metrics.connections()),113,213F,62,0x55FFFF);
        }
        var card=menu.slots.get(36).getItem();boolean bound=HyperNetCardInteractions.isBoundCard(card);
        small(g,tr(card.isEmpty()?"card_empty":bound?"card_inserted":"card_invalid"),113,231,42,
                card.isEmpty()?0xAAAAAA:bound?0x77D794:0xEB7777);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY){}
    private double logicalX(double x){return width/2D+(x-width/2D)/fit;}private double logicalY(double y){return height/2D+(y-height/2D)/fit;}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial){
        renderBackground(g);int x=(int)logicalX(mouseX),y=(int)logicalY(mouseY);
        g.pose().pushPose();g.pose().translate(width/2F,height/2F,0);g.pose().scale(fit,fit,1);g.pose().translate(-width/2F,-height/2F,0);
        super.render(g,x,y,partial);renderTooltip(g,x,y);
        var data=menu.snapshot();var tips=new ArrayList<Component>();
        if(data!=null&&over(x,y,316,143,16,16)){
            tips.add(tr("start_hint"));if(data.selected()!=null&&!data.selected().row().reason().isEmpty())tips.add(Component.translatable(data.selected().row().reason()));
            if(canCreativeStart(data))tips.add(tr("creative_hint"));
            if(data.canCancel())tips.add(tr(cancelConfirm?"cancel_confirm":"cancel_hint"));
        }else if(data!=null&&data.selected()!=null&&data.selected().row().state()==State.COMPLETED&&over(x,y,296,143,16,16)){
            tips.add(tr("toggle_description"));
        }else if(data!=null&&over(x,y,100,44,5,198)){
            tips.add(Component.literal((data.page()+1)+"/"+data.pages()));
        }else if(data!=null&&over(x,y,8,44,92,198)){
            int index=(y-topPos-44)/22;if(index<data.rows().size()){var r=data.rows().get(index);tips.add(name(r.name()));tips.add(Component.literal(r.id()));tips.add(state(r));if(!r.reason().isEmpty())tips.add(Component.translatable(r.reason()));}
        }else if(data!=null&&data.selected()!=null&&over(x,y,115,140,176,22)){
            var d=data.selected();tips.add(tr("dependencies",String.join(", ",d.dependencies())));tips.add(tr("points",d.row().progress(),d.row().required()));tips.add(tr("minimum",d.minimum()));
        }else if(data!=null&&over(x,y,112,167,64,57)){
            var m=data.network();tips.add(tr(m.online()?"network_online":"network_offline"));
            if(m.online()){
                tips.add(tr("network_consumed_full",HyperNetTooltips.number(m.consumed())));
                tips.add(tr("network_offered_full",HyperNetTooltips.number(m.offered())));
                tips.add(tr("network_delivered_full",HyperNetTooltips.number(m.delivered())));
                tips.add(tr("network_connections",m.connections()));tips.add(tr("network_tick",m.tick()));
                tips.add(tr("network_sample_hint"));
            }
            if(!data.status().isEmpty())tips.add(Component.translatable(data.status()));
            tips.add(tr("network_task",data.task().isEmpty()?"—":data.task()));
            if(data.awaiting())tips.add(tr("awaiting"));
        }else if(over(x,y,112,226,43,16))tips.add(tr("card_hint"));
        if(!tips.isEmpty()){var wrapped=new ArrayList<FormattedCharSequence>();for(var line:tips)wrapped.addAll(font.split(SmoothUiFonts.compact(line),240));g.renderTooltip(font,wrapped,x,y);}
        g.pose().popPose();
    }
    @Override public boolean mouseClicked(double x,double y,int button){x=logicalX(x);y=logicalY(y);var data=menu.snapshot();
        if(button==0&&data!=null&&over(x,y,100,44,5,198)){dragging=1;dragScrollbar(y);return true;}
        if(button==0&&data!=null&&over(x,y,336,44,5,118)){dragging=2;dragScrollbar(y);return true;}
        if(data!=null&&over(x,y,316,143,16,16)){
            if(button==0&&data.selected()!=null&&data.selected().row().state()==State.AVAILABLE){send(1,data.page(),data.selected().row().id());return true;}
            if(button==2&&canCreativeStart(data)){send(3,data.page(),data.selected().row().id());return true;}
            if(button==1&&data.canCancel()){if(cancelConfirm&&cancelTask.equals(data.task())){send(2,data.page(),data.task());cancelConfirm=false;}
                else{cancelConfirm=true;cancelTask=data.task();cancelTicks=60;}return true;}
        }
        if(button==0&&data!=null&&over(x,y,296,143,16,16)&&data.selected()!=null&&data.selected().row().state()==State.COMPLETED){showOriginal=!showOriginal;return true;}
        if(button==0&&data!=null&&over(x,y,8,44,92,198)){int index=((int)y-topPos-44)/22;if(index<data.rows().size()){send(0,data.page(),data.rows().get(index).id());cancelConfirm=false;return true;}}
        return super.mouseClicked(x,y,button);
    }
    private void dragScrollbar(double y){var data=menu.snapshot();if(data==null)return;
        double fraction=Math.max(0,Math.min(1,(y-topPos-44-9)/(dragging==1?180D:100D)));
        if(dragging==1){queuedPage=(int)Math.round(fraction*(data.pages()-1));if(pending<0&&queuedPage!=data.page()){send(0,queuedPage,"");queuedPage=-1;}}
        else descriptionScroll=(int)Math.round(fraction*Math.max(0,description.size()-11));
    }
    @Override public boolean mouseReleased(double x,double y,int button){if(button==0&&dragging!=0){dragScrollbar(logicalY(y));dragging=0;return true;}return super.mouseReleased(logicalX(x),logicalY(y),button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(button==0&&dragging!=0){dragScrollbar(logicalY(y));return true;}return super.mouseDragged(logicalX(x),logicalY(y),button,dx/fit,dy/fit);}
    @Override public boolean mouseScrolled(double x,double y,double amount){double lx=logicalX(x),ly=logicalY(y);var data=menu.snapshot();
        if(data!=null&&amount!=0&&over(lx,ly,7,44,98,198)){send(0,Math.max(0,Math.min(data.pages()-1,data.page()+(amount>0?-1:1))),"");return true;}
        if(data!=null&&over(lx,ly,112,58,230,82)){descriptionScroll=Math.max(0,Math.min(Math.max(0,description.size()-11),descriptionScroll+(amount>0?-1:1)));return true;}
        return super.mouseScrolled(lx,ly,amount);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers){if(search.isFocused()&&key!=256)return search.keyPressed(key,scan,modifiers)||search.canConsumeInput();return super.keyPressed(key,scan,modifiers);}
}
