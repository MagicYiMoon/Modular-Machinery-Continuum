/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import org.openzen.zencode.shared.LiteralSourceFile;
import java.util.*;

/** Compiles and invokes the public ZenCode mutation API on a real ordinary/factory run. */
public final class RuntimeModifierScriptRegressionChecks {
    private RuntimeModifierScriptRegressionChecks() { }
    public static void run(TileController tile, FactoryRecipeThread core, MachineRecipe recipe) throws Exception {
        var run = core == null ? tile.getRuntimeRecipeContext() : core.getRuntimeRecipeContext();
        String handler = core == null ? "addPreTickHandler" : "addFactoryPreTickHandler";
        String script = """
                import mods.modularmachinery.RecipeBuilder;
                import mods.modularmachinery.RecipeModifierBuilder;
                RecipeBuilder.newBuilder("%s", "%s", 4).%s((event) => {
                    val thread = event.recipeThread;
                    if (thread == null) return;
                    val speed = RecipeModifierBuilder.create("modularmachinery:duration", "input", 0.5f, 1, false).build();
                    thread.addModifier("script_semi", speed);
                    thread.addPermanentModifier("script_permanent", speed);
                    event.controller.addModifier("script_broadcast", speed);
                    event.controller.addPermanentModifier("script_global", speed);
                    if (!thread.hasModifier("script_semi") || !thread.hasModifier("script_broadcast")
                        || !thread.hasPermanentModifier("script_permanent") || !event.controller.hasModifier("script_global")) return;
                    thread.removeModifier("script_semi");
                    thread.removePermanentModifier("script_permanent");
                    event.controller.removeModifier("script_broadcast");
                    event.controller.removePermanentModifier("script_global");
                    if (thread.hasModifier("script_semi") || thread.hasModifier("script_broadcast")
                        || thread.hasPermanentModifier("script_permanent") || event.controller.hasModifier("script_global")) return;
                    thread.addPermanentModifier("compiled_speed", speed);
                    val updated = thread.context;
                    val original = event.context;
                    if (updated != null && original != null && updated.effectiveDuration == 1
                        && original.effectiveDuration == 2) {
                        %s
                        event.preventProgressing("compiled_runtime_modifier_ok");
                    }
                }).build();
                """.formatted(recipe.registryName(), recipe.machine(), handler, core == null ? "" :
                "val exact = event.factoryRecipeThread; if (exact == null || !exact.hasPermanentModifier(\"compiled_speed\")) return;");
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        try {
            try {
                CraftTweakerAPI.getScriptRunManager().createScriptRun(
                        List.of(new LiteralSourceFile("runtime_modifier_regression.zs", script)), configuration).execute();
            } catch (Throwable failure) { throw new Exception("runtime modifier ZenCode compilation failed", failure); }
            if (core == null) {
                tile.observeRuntimeRecipeContext(run);
                var event = new RecipeTickEvent(tile, run, Phase.START); event.postEvent();
                if (!event.isPreventProgressing() || !"compiled_runtime_modifier_ok".equals(event.getFailureReason()))
                    throw new AssertionError("ordinary ZenCode mutation callback failed");
            } else {
                core.observeRuntimeRecipeContext(run);
                var event = new FactoryRecipeTickEvent(core, tile, Phase.START, run); event.postEvent();
                if (!event.isPreventProgressing()) throw new AssertionError("factory ZenCode mutation callback failed");
            }
            var thread = core == null ? tile.getRecipeThreadList()[0] : new hellfirepvp.modularmachinery.common.machine.RecipeThread(tile, core);
            thread.removePermanentModifier("compiled_speed");
            Modularmachinery.LOG.info("[MMC][runtime-modifier-script] PASS: real CT {} add/remove/query, broadcast/global scopes, same-event refresh and immutable observations", core == null ? "ordinary" : "factory");
        } finally {
            for (String name : List.of("recipes", "handlers", "callbacks")) {
                var field = ScriptRecipeRegistry.class.getDeclaredField(name); field.setAccessible(true);
                ((Map<?, ?>) field.get(null)).remove(recipe.registryName());
            }
        }
    }
}
