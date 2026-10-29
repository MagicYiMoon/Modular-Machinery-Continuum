/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.jei;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.client.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Automated in-engine screenshots; no desktop control or changes to non-fixture saves. */
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID,value=Dist.CLIENT)
public final class ClientVisualVerification {
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if(Boolean.getBoolean("modularmachinery.releaseGallery") || Boolean.getBoolean("modularmachinery.editorOptionalVisualTest") || Boolean.getBoolean("modularmachinery.editorExpandedVisualTest") || Boolean.getBoolean("modularmachinery.editorNbtVisualTest") || Boolean.getBoolean("modularmachinery.editorBloodAuraVisualTest") || Boolean.getBoolean("modularmachinery.editorCopyVisualTest") || Boolean.getBoolean("modularmachinery.editorPneumaticVisualTest") || Boolean.getBoolean("modularmachinery.editorResourcesVisualTest"))return;
        if(event.phase!=TickEvent.Phase.END || !Boolean.getBoolean("modularmachinery.clientVisualTest"))return;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null || mc.getOverlay()!=null)return;
        ticks++;
        switch(ticks) {
            case 20 -> command("setup");
            case 80 -> command("beamclose");
            case 100 -> shot("01-beams-six-directions.png");
            case 120 -> command("pressure");
            case 160 -> shot("02-pressure-heat.png");
            case 180 -> {mc.player.closeContainer();command("ordinary");}
            case 220 -> shot("03-controller-performance.png");
            case 240 -> {mc.player.closeContainer();command("factory");}
            case 280 -> shot("04-factory-performance.png");
            case 300 -> {mc.player.closeContainer();if(currentRuntime()!=null)currentRuntime().getRecipesGui().showRecipes(currentRuntime().getRecipeManager().getRecipeCategory(JeiPluginModularMachinery.MACHINE_RECIPE),currentRuntime().getRecipeManager().createRecipeLookup(JeiPluginModularMachinery.MACHINE_RECIPE).get().filter(r->r.registryName().contains("pneumaticcraft:refinery")).toList(),java.util.List.of());}
            case 340 -> shot("05-jei-imports.png");
            case 360 -> {mc.setScreen(null);mc.reloadResourcePacks();}
            case 400 -> command("beamclose");
            case 440 -> shot("06-beams-after-reload.png");
            case 480 -> {Modularmachinery.LOG.info("[MMC][client-visual] CAPTURE COMPLETE; inspect PNGs, this marker alone is not a visual PASS");mc.stop();}
        }
    }
    private static mezz.jei.api.runtime.IJeiRuntime currentRuntime(){return JeiPluginModularMachinery.currentRuntime();}
    private static void command(String name){Minecraft.getInstance().player.connection.sendCommand("mmcvisual "+name);}
    private static void shot(String name) {
        Modularmachinery.LOG.info("[MMC][client-visual] capture screen={}",Minecraft.getInstance().screen==null?"world":Minecraft.getInstance().screen.getClass().getName());
        var mc=Minecraft.getInstance();Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),result->Modularmachinery.LOG.info("[MMC][client-visual] {}: {}",name,result.getString()));
    }
}
