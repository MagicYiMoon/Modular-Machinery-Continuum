/* SPDX-License-Identifier: GPL-3.0-only
 * Completion notice/layout adapt NECO ResearchCompleteToast and PktResearchTaskComplete, b37fbb53.
 */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import hellfirepvp.modularmachinery.common.editor.MachineNameFormatting;
import hellfirepvp.modularmachinery.common.network.HyperNetResearchPackets;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

/** Five-second vanilla-sized notice; no cached world, menu or research registry references. */
public final class ResearchCompleteToast implements Toast {
    private final HyperNetResearchPackets.Complete notice;
    private final ItemStack icon;
    public ResearchCompleteToast(HyperNetResearchPackets.Complete notice){
        this.notice=notice;
        icon=notice.previewIcon().stack();
    }
    public static void receive(HyperNetResearchPackets.Complete notice){
        var mc=Minecraft.getInstance();
        if(mc.player!=null&&mc.level!=null)mc.getToasts().addToast(new ResearchCompleteToast(notice));
    }
    @Override public Object getToken(){return notice.id();}
    @Override public Visibility render(GuiGraphics g,ToastComponent toasts,long elapsed){
        var font=toasts.getMinecraft().font;
        g.blit(TEXTURE,0,0,0,32,width(),height());
        var title=SmoothUiFonts.compact(Component.translatable("modularmachinery.hypernet.terminal.research_complete"));
        var name=SmoothUiFonts.compact(Component.literal(MachineNameFormatting.display(notice.name().isEmpty()?notice.id():notice.name())));
        if(font.width(name)>124)name=Component.literal(font.substrByWidth(name,Math.max(0,124-font.width("…"))).getString()+"…").withStyle(name.getStyle());
        g.drawString(font,title,30,7,0x500050,false);
        g.drawString(font,name,30,18,0,false);
        g.renderItem(icon,8,8);
        return elapsed<5000*toasts.getNotificationDisplayTimeMultiplier()?Visibility.SHOW:Visibility.HIDE;
    }
}
