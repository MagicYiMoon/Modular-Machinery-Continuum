/* SPDX-License-Identifier: GPL-3.0-only
 * Reuses NECO guiterminal.png, KasumiNova and contributors, b37fbb53; monitoring content/layout is new port code.
 */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import hellfirepvp.modularmachinery.common.editor.MachineNameFormatting;
import hellfirepvp.modularmachinery.common.hypernet.*;
import hellfirepvp.modularmachinery.common.hypernet.HyperNetMonitor.*;
import hellfirepvp.modularmachinery.common.menu.MenuHyperNetMonitor;
import hellfirepvp.modularmachinery.common.network.PktHyperNetMonitor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

/** Fixed 310x220 logical pixels fit Minecraft's minimum GUI area; all text remains at native font size. */
public final class ScreenHyperNetMonitor extends AbstractContainerScreen<MenuHyperNetMonitor> {
    private static final ResourceLocation BACKGROUND=new ResourceLocation("modularmachinery","textures/gui/neco/hypernet_monitor.png");
    private BlockPos selected;
    private boolean local;
    private Button previous,next,centerButton;
    public ScreenHyperNetMonitor(MenuHyperNetMonitor menu,Inventory inventory,Component title){
        super(menu,inventory,title);imageWidth=310;imageHeight=220;
    }
    public static void receive(PktHyperNetMonitor packet){
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu instanceof MenuHyperNetMonitor menu&&menu.containerId==packet.container())menu.accept(packet.session(),packet.data());
    }
    private static Component tr(String key,Object... args){return Component.translatable("gui.modularmachinery.hypernet."+key,args);}
    private static Component fault(Node n){return tr("fault."+n.fault().name().toLowerCase(Locale.ROOT));}
    private void send(int action){if(minecraft.gameMode!=null)minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action);}
    @Override protected void init(){
        super.init();
        centerButton=addRenderableWidget(Button.builder(tr("center"),b->{local=false;selected=null;}).bounds(leftPos+101,topPos+20,45,16).build());
        addRenderableWidget(Button.builder(tr("local"),b->{local=true;selected=null;}).bounds(leftPos+149,topPos+20,45,16).build());
        addRenderableWidget(Button.builder(tr("back"),b->send(2)).bounds(leftPos+260,topPos+20,43,16).build());
        previous=addRenderableWidget(Button.builder(Component.literal("←"),b->send(0)).bounds(leftPos+8,topPos+193,21,16).build());
        next=addRenderableWidget(Button.builder(Component.literal("→"),b->send(1)).bounds(leftPos+69,topPos+193,21,16).build());
        updateButtons();
    }
    private void updateButtons(){var data=menu.snapshot();previous.active=data!=null&&data.page()>0;next.active=data!=null&&data.page()+1<data.pages();centerButton.active=data!=null&&data.center()!=null;}
    @Override public void containerTick(){super.containerTick();updateButtons();}
    private Node selectedNode(Snapshot data){
        if(local)return data.local();
        if(selected!=null)for(var row:data.nodes())if(selected.equals(row.member().pos()))return row;
        return data.center()==null?data.local():data.center();
    }
    private void text(GuiGraphics g,Component component,int x,int y,int width,int color){
        var styled=SmoothUiFonts.compact(component);
        if(font.width(styled)>width){var style=styled.getStyle();int size=Math.max(0,width-font.width(SmoothUiFonts.compact(Component.literal("…"))));
            styled=Component.literal(font.substrByWidth(styled,size).getString()+"…").withStyle(style);}
        g.drawString(font,styled,leftPos+x,topPos+y,color,false);
    }
    private static String number(double n){return n==0||n>=.000001&&n<1e12
            ?java.math.BigDecimal.valueOf(n).stripTrailingZeros().toPlainString():HyperNetTooltips.number(n);}
    private static String coordinates(BlockPos pos){return pos.getX()+", "+pos.getY()+", "+pos.getZ();}
    @Override protected void renderBg(GuiGraphics g,float partialTick,int mouseX,int mouseY){
        g.blit(BACKGROUND,leftPos,topPos,310,220,0,0,350,250,350,250);
        // The research terminal's inventory wells are covered: this monitor has no inventory or research actions.
        g.fill(leftPos+5,topPos+25,leftPos+94,topPos+211,0xFFBEBEBE);
        g.fill(leftPos+99,topPos+38,leftPos+304,topPos+144,0xFF161616);
        g.fill(leftPos+99,topPos+145,leftPos+304,topPos+213,0xFF191B20);
        text(g,tr("title"),8,8,295,0xFFFFFFFF);
        var data=menu.snapshot();if(data==null){text(g,tr("waiting"),105,45,192,0xDADADA);return;}
        var current=selectedNode(data);
        for(int i=0;i<data.nodes().size();i++){
            var row=data.nodes().get(i);int y=38+i*25;boolean chosen=!local&&selected!=null&&selected.equals(row.member().pos());
            g.fill(leftPos+7,topPos+y,leftPos+92,topPos+y+24,chosen?0xFF4B6888:0xFF95999D);
            text(g,Component.literal(MachineNameFormatting.display(row.member().name())),10,y+3,78,chosen?0xFFFFFF:0x151515);
            text(g,tr("role."+row.member().role().name().toLowerCase(Locale.ROOT)),10,y+14,66,0x222222);
            g.fill(leftPos+83,topPos+y+15,leftPos+88,topPos+y+20,row.online()?0xFF35AF63:0xFFD07743);
        }
        text(g,Component.literal((data.page()+1)+"/"+data.pages()),31,197,36,0x222222);
        var m=current.member();
        text(g,Component.literal(MachineNameFormatting.display(m.name())),104,41,194,0xFFFFFF);
        text(g,Component.literal(coordinates(m.pos())),104,53,194,0xAAAAAA);
        text(g,tr("role_tier",tr("role."+m.role().name().toLowerCase(Locale.ROOT)),MachineNameFormatting.display(m.tier())),104,65,194,0xC6D9EF);
        var reason=font.split(SmoothUiFonts.compact(fault(current)),192);int y=80;
        for(int i=0;i<Math.min(2,reason.size());i++){g.drawString(font,reason.get(i),leftPos+104,topPos+y,current.fault()==Fault.ONLINE?0x77D794:0xF0BF79,false);y+=10;}
        if(!current.detail().isEmpty())text(g,Component.translatable(current.detail()),104,103,194,0xBBBBBB);
        text(g,tr("demand_actual",number(current.demanded()),number(current.consumed())),104,117,194,0xEEEEEE);
        text(g,tr("capacity",number(current.capacity())),104,130,194,0xC6D9EF);
        if(current.heatLimit()>0)text(g,tr("heat",java.math.BigDecimal.valueOf(current.heat())
                .setScale(2,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),current.heatLimit()),104,150,194,0xEFBC7C);
        if(current.durabilityLimit()>0)text(g,tr("durability",current.durability(),current.durabilityLimit()),104,150,194,0xEFBC7C);
        if(data.center()!=null){
            text(g,tr("offer",number(data.center().offered())),104,165,194,0xC6D9EF);
            text(g,tr("connections",data.connections(),data.maxConnections(),data.total()),104,178,194,0xC6D9EF);
        }else text(g,tr("details_unavailable"),104,165,194,0xC6D9EF);
        text(g,tr("tick",data.tick()),104,193,194,0xAAAAAA);
        text(g,tr(data.limited()?"limited":"roster_hint"),104,205,194,0xAAAAAA);
    }
    @Override protected void renderLabels(GuiGraphics g,int x,int y){}
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        renderBackground(g);super.render(g,mouseX,mouseY,partialTick);
        var data=menu.snapshot();if(data==null)return;
        int x=mouseX-leftPos,y=mouseY-topPos;Node current=null;
        if(x>=7&&x<93&&y>=38&&y<188){int index=(y-38)/25;if(index<data.nodes().size())current=data.nodes().get(index);}
        else if(x>=100&&x<303&&y>=39&&y<215)current=selectedNode(data);
        if(current!=null){
            var lines=new ArrayList<Component>();var m=current.member();
            lines.add(Component.literal(MachineNameFormatting.display(m.name())));lines.add(Component.literal(m.machine()));
            lines.add(Component.literal(coordinates(m.pos())));lines.add(fault(current));
            if(!current.detail().isEmpty())lines.add(Component.translatable(current.detail()));
            lines.add(tr("demand",number(current.demanded()),current.demandConsumers()));
            lines.add(tr("consumed",number(current.consumed())));lines.add(tr("capacity",number(current.capacity())));
            if(current.heatLimit()>0)lines.add(tr("heat",HyperNetTooltips.number(current.heat()),current.heatLimit()));
            if(current.durabilityLimit()>0)lines.add(tr("durability",current.durability(),current.durabilityLimit()));
            if(data.center()!=null){lines.add(tr("offer",number(data.center().offered())));lines.add(tr("connections",data.connections(),data.maxConnections(),data.total()));}
            lines.add(tr(data.limited()?"limited":"roster_hint"));
            var wrapped=new ArrayList<net.minecraft.util.FormattedCharSequence>();for(var line:lines)wrapped.addAll(font.split(line,260));
            g.renderTooltip(font,wrapped,mouseX,mouseY);
        }
    }
    @Override public boolean mouseClicked(double x,double y,int button){
        var data=menu.snapshot();double lx=x-leftPos,ly=y-topPos;
        if(button==0&&data!=null&&lx>=7&&lx<93&&ly>=38&&ly<188){int index=((int)ly-38)/25;
            if(index<data.nodes().size()){selected=data.nodes().get(index).member().pos();local=false;return true;}}
        return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseScrolled(double x,double y,double delta){
        if(x>=leftPos+5&&x<leftPos+95&&y>=topPos+25&&y<topPos+211){if(delta!=0)send(delta>0?0:1);return true;}
        return super.mouseScrolled(x,y,delta);
    }
}
