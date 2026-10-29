/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.CraftTweakerConstants;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.geo.*;
import hellfirepvp.modularmachinery.common.machine.GeoAnimationRegressionChecks;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import org.openzen.zencode.shared.LiteralSourceFile;
import software.bernie.geckolib.core.animation.AnimationController;

/** Real CT compilation and invocation of the animation event and inherited controller getter. */
public final class GeoAnimationScriptRegressionChecks {
    private GeoAnimationScriptRegressionChecks() { }
    public static void run(TileController tile, GeoAnimationRegressionChecks.PlaybackModel model) throws Exception {
        String script = """
                import mods.modularmachinery.MMEvents;
                MMEvents.onControllerModelAnimation("mmc_geo_animation_regression", (event) => {
                    val current = event.currentAnimationName;
                    if (event.controller.isWorking
                        && event.controller.formedMachineName == "mmc_geo_animation_regression"
                        && event.currentModelName == "mmc_geo_animation_regression_base"
                        && event.animationSpeed > (0 as double) && event.animationTick >= (0 as double)
                        && event.animationState >= 0 && event.animationState <= 2
                        && event.limbSwing > (0 as float) && event.limbSwingAmount > (0 as float)
                        && event.partialTick > (0 as float) && event.moving) {
                        event.transitionLengthTicks = 2.5 as double;
                        if (event.transitionLengthTicks == (2.5 as double)) {
                            event.setAnimation("ct_intro", false);
                            event.addAnimation("ct_loop", true);
                            event.playState = 0;
                        }
                    }
                });
                """;
        var recipesBefore = hellfirepvp.modularmachinery.common.crafting.ScriptRecipeRegistry.snapshots();
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.CLIENT_RECIPES_UPDATED_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        try {
            CraftTweakerAPI.getScriptRunManager().createScriptRun(
                    java.util.List.of(new LiteralSourceFile("geo_animation_regression.zs", script)), configuration).execute();
        } catch (Throwable failure) { throw new Exception("animation ZenCode compilation/execution failed", failure); }
        if (!recipesBefore.equals(hellfirepvp.modularmachinery.common.crafting.ScriptRecipeRegistry.snapshots()))
            throw new AssertionError("animation client replay changed server recipe declarations");
        AnimationController<TileController> controller = tile.getAnimatableInstanceCache()
                .<TileController>getManagerForId(1).getAnimationControllers().get("mmce_machine");
        tile.setFormed(true);
        tile.beginRecipe("animation_script_work", 200, 1);
        GeoAnimationRegressionChecks.process(model, tile, controller, 40);
        var raw = controller.getCurrentRawAnimation();
        if (raw == null || raw.getStageCount() != 2
                || !raw.getAnimationStages().get(0).animationName().equals("ct_intro")
                || !raw.getAnimationStages().get(1).animationName().equals("ct_loop")
                || ((MachineAnimationController) controller).getTransitionLengthTicks() != 2.5)
            throw new AssertionError("compiled animation callback did not read context and install the requested sequence");
        Modularmachinery.LOG.info("[MMC][geo-animation-script] PASS: real CT context/controller getters, fractional setter and queued callback");
    }
}
