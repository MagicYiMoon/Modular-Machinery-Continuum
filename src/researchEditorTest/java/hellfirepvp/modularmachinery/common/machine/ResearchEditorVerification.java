/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;
import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=Modularmachinery.MODID)
public final class ResearchEditorVerification {
 @SubscribeEvent public static void start(ServerStartedEvent e){if(!Boolean.getBoolean("modularmachinery.researchEditorTest"))return;try{HyperNetEditorRegressionChecks.run(e.getServer().overworld());HyperNetResearchEditorRegressionChecks.run(e.getServer().overworld());ResearchGateEditorRegressionChecks.run(e.getServer().overworld());Modularmachinery.LOG.info("[MMC][research-editor-fixture] PASS");}catch(Throwable failure){Modularmachinery.LOG.error("[MMC][research-editor-fixture] FAIL",failure);}finally{e.getServer().halt(false);}}
}
