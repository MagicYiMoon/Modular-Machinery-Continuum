/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.efabricator.*;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.*;

/** Actual baked models and native item renderer, at the title screen; no player worlds touched. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class EFabricatorBaseClientVerification {
    private static boolean language, opened;
    private static int total,ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.efBaseVisualTest"))return;
        var mc=Minecraft.getInstance();
        try {
            if(++total>4000)throw new AssertionError("EF resource test timeout");
            if(mc.getOverlay()!=null)return;
            if(!opened){
                // A fresh disposable directory shows onboarding before the title screen.
                if(mc.screen instanceof AccessibilityOnboardingScreen){mc.setScreen(new TitleScreen());return;}
                if(!(mc.screen instanceof TitleScreen))return;
                if(!language){language=true;mc.options.languageCode="zh_cn";mc.getLanguageManager().setSelected("zh_cn");mc.reloadResourcePacks();return;}
                mc.options.guiScale().set(2);mc.resizeDisplay();mc.options.pauseOnLostFocus=false;
                verifyModels(mc);mc.setScreen(new Assets());opened=true;return;
            }
            switch(++ticks){
                case 30 -> shot("ef-base-01-zh.png");
                case 40 -> {mc.options.languageCode="en_us";mc.getLanguageManager().setSelected("en_us");mc.reloadResourcePacks();}
                case 65 -> {verifyModels(mc);shot("ef-base-02-en.png");
                    Modularmachinery.LOG.info("[MMC][ef-base-client] PASS: 11 real baked block/item models, all physical blockstates/facings, nonmissing face and particle sprites, Chinese/English names and native item rendering; offline artwork only, screenshots require inspection");
                    Modularmachinery.LOG.info("[MMC][ef-base-client] OVERALL: PASS");}
                case 85 -> mc.stop();
            }
        }catch(Throwable failure){Modularmachinery.LOG.error("[MMC][ef-base-client] OVERALL: FAIL",failure);mc.stop();}
    }
    private static void verifyModels(Minecraft mc){
        for(var holder:EFabricatorRegistration.BLOCKS.getEntries())for(var state:holder.get().getStateDefinition().getPossibleStates()){
            var model=mc.getBlockRenderer().getBlockModel(state);require(model!=mc.getModelManager().getMissingModel(),"missing block model "+state);
            int count=0;
            for(int face=-1;face<6;face++)for(var quad:model.getQuads(state,face<0?null:Direction.values()[face],RandomSource.create(0))){
                require(!quad.getSprite().contents().name().getPath().equals("missingno"),"missing face sprite "+state);count++;
            }
            require(count>0&&!model.getParticleIcon().contents().name().getPath().equals("missingno"),"empty model/particle "+state);
        }
        for(var holder:EFabricatorRegistration.ITEM_FORMS.values()){
            var stack=new ItemStack(holder.get());var model=mc.getItemRenderer().getModel(stack,null,null,0);
            require(model!=mc.getModelManager().getMissingModel()&&!model.getParticleIcon().contents().name().getPath().equals("missingno"),"missing item model "+stack);
            require(!stack.getHoverName().getString().equals(holder.get().getDescriptionId()),"missing localized name "+stack);
        }
    }
    private static final class Assets extends Screen {
        private final List<ItemStack> items=EFabricatorRegistration.ITEM_FORMS.values().stream().map(i->new ItemStack(i.get())).toList();
        private Assets(){super(Component.literal("EF source artwork"));}
        @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
            graphics.fill(0,0,width,height,0xFF171923);
            boolean en=Minecraft.getInstance().options.languageCode.equals("en_us");
            graphics.drawString(font,en?"EF physical components - original offline artwork":"EF 基础部件 · 源版离线模型",18,14,0xFFFFFF,false);
            graphics.drawString(font,en?"ME fabrication and formed/status artwork are a later stage.":"本轮仅验证部件资源；ME 制造和成型／状态外观后续接入。",18,32,0xBBC6D6,false);
            for(int i=0;i<items.size();i++){
                int x=18+i/6*(width/2),y=62+i%6*46;
                graphics.fill(x-3,y-4,x+width/2-20,y+34,0xFF242735);
                graphics.renderItem(items.get(i),x,y);graphics.drawString(font,items.get(i).getHoverName(),x+24,y+4,0xEEEFF4,false);
            }
        }
    }
    private static void shot(String name){var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),c->Modularmachinery.LOG.info("[MMC][ef-base-client] {}",c.getString()));}
    private static void require(boolean condition,String why){if(!condition)throw new AssertionError(why);}
}
