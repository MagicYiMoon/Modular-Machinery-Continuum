/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Documentation screenshots in an isolated demonstration world; never included in release classes. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualReleaseGallery {
    private static int ticks,wait,captureDelay;
    private static String capture;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(event.phase!=TickEvent.Phase.END||!Boolean.getBoolean("modularmachinery.releaseGallery"))return;
        var mc=Minecraft.getInstance();if(mc.player==null||mc.level==null||mc.getOverlay()!=null)return;
        mc.getTutorial().setStep(TutorialSteps.NONE);mc.getToasts().clear();
        if(captureDelay>0&&--captureDelay==0)Screenshot.grab(mc.gameDirectory,capture,mc.getMainRenderTarget(),r->{});
        ticks++;
        try {
            switch(ticks) {
                case 20->{mc.player.connection.sendCommand("clear");mc.player.connection.sendCommand("give @s minecraft:diamond{quality:5} 7");mc.player.connection.sendCommand("give @s minecraft:iron_ingot 16");mc.player.connection.sendCommand("give @s minecraft:coal 8");}
                case 40->mc.player.connection.sendCommand("modularmachinery gui");
                case 70->{if(!(mc.screen instanceof ScreenMachineEditor)){ticks--;if(++wait>600)throw new AssertionError("editor timeout");return;}var f=ScreenMachineEditor.class.getDeclaredField("saveStatus");f.setAccessible(true);f.set(s(),"已保存并重新加载");shot("01-editor-overview.png");}
                case 90->{invoke("openTypePicker",boolean.class,false);press("物品");press("背包");shot("02-inventory-picker.png");}
                case 110->{invoke("closePicker");invoke("closeEditor");invoke("openRequirementEditor",int.class,1);shot("03-environment-editor.png");}
                case 130->{invoke("closeEnvironment");invoke("openRecipeCopy");var target=s().children().stream().filter(w->w instanceof Button b&&b.visible&&b.getMessage().getString().contains("进阶加工厂")).map(w->(Button)w).findFirst().orElseThrow();s().mouseClicked(target.getX()+10,target.getY()+10,0);s().mouseReleased(target.getX()+10,target.getY()+10,0);shot("04-cross-machine-copy.png");}
                case 150->{invoke("closeRecipeCopy");invoke("openAdapterTemplates");shot("05-adapter-template.png");}
                case 170->{press("修改器");press("+ 修改器");for(var w:s().children())if(w instanceof EditBox b&&b.visible){b.setValue("2");break;}shot("06-template-modifier.png");}
                case 190->{press("取消");press("追加需求（0）");invoke("openTypePicker",boolean.class,false);press("物品");press("背包");s().mouseClicked(s().getGuiLeft()+110,s().getGuiTop()+87,0);s().mouseReleased(s().getGuiLeft()+110,s().getGuiTop()+87,0);press("保存");shot("07-template-requirements.png");}
                case 210->{press("完成追加需求");invoke("closeAdapterTemplates");mc.options.guiScale().set(2);mc.resizeDisplay();shot("08-editor-scale2.png");}
                case 230->{Modularmachinery.LOG.info("[MMC][release-gallery] PASS: eight real GUI captures, no tutorial overlays, normal and scale2 status row, inventory/environment/copy/template controls");mc.stop();}
            }
        } catch(Throwable failure) {Modularmachinery.LOG.error("[MMC][release-gallery] FAIL tick {}",ticks,failure);mc.stop();}
    }
    private static ScreenMachineEditor s(){return (ScreenMachineEditor)Minecraft.getInstance().screen;}
    private static void invoke(String method)throws Exception{var m=ScreenMachineEditor.class.getDeclaredMethod(method);m.setAccessible(true);m.invoke(s());}
    private static void invoke(String method,Class<?> type,Object arg)throws Exception{var m=ScreenMachineEditor.class.getDeclaredMethod(method,type);m.setAccessible(true);m.invoke(s(),arg);}
    private static void press(String text){var b=s().children().stream().filter(w->w instanceof Button b0&&b0.visible&&b0.active&&b0.getMessage().getString().equals(text)).map(w->(Button)w).findFirst().orElseThrow(()->new AssertionError(text));s().mouseClicked(b.getX()+b.getWidth()/2d,b.getY()+b.getHeight()/2d,0);s().mouseReleased(b.getX()+1,b.getY()+1,0);}
    private static void shot(String name){org.lwjgl.glfw.GLFW.glfwSetCursorPos(Minecraft.getInstance().getWindow().getWindow(),0,0);capture=name;captureDelay=3;}
}
