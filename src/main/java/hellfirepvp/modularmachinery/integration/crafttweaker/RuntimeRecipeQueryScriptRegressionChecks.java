/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.*;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.tiles.*;
import org.openzen.zencode.shared.LiteralSourceFile;
import java.util.*;

/** Compile nullable ZenCode queries, then invoke the installed callback against an actual live run. */
public final class RuntimeRecipeQueryScriptRegressionChecks {
    private RuntimeRecipeQueryScriptRegressionChecks() { }

    public static void run(TileController tile, FactoryRecipeThread core, MachineRecipe recipe) throws Exception {
        RecipeCraftingContext run = core == null ? tile.getRuntimeRecipeContext() : core.getRuntimeRecipeContext();
        String handler = core == null ? "addPreTickHandler" : "addFactoryPreTickHandler";
        String script = """
                import mods.modularmachinery.RecipeBuilder;
                RecipeBuilder.newBuilder("%s", "%s", 2)
                    .%s((event) => {
                        val thread = event.recipeThread;
                        val active = event.activeRecipe;
                        val context = event.context;
                        val all = event.controller.activeRecipeList;
                        val threads = event.controller.recipeThreadList;
                        val first = event.controller.activeRecipe;
                        if (thread != null && active != null && context != null && first != null) {
                            val current = thread.activeRecipe;
                            val live = thread.context;
                            if (current != null && live != null && thread.valid && thread.isWorking
                                && thread.threadName == "%s" && current.registryName == active.registryName
                                && active.tick == 0 && active.totalTick == %s && !active.completed
                                && active.parallelism == 1 && active.maxParallelism == 1
                                && context.recipeName == active.registryName && live.effectiveDuration == %s
                                && context.inputDescriptions.length == 1 && context.outputDescriptions.length == 1
                                && context.requirementDescriptions.length == 2 && all.length == %s && threads.length == %s) {
                                %s
                                event.preventProgressing("compiled_runtime_query_ok");
                            }
                        }
                    }).build();
                """.formatted(recipe.registryName(), recipe.machine(), handler, core == null ? "" : "ore",
                        core == null ? 2 : 4, core == null ? 2 : 4, core == null ? 1 : 2, core == null ? 1 : 2,
                        core == null ? "" : "val factoryThread = event.factoryRecipeThread;\n"
                                + "if (factoryThread == null || factoryThread.threadName != thread.threadName) return;");
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        try {
            try {
                CraftTweakerAPI.getScriptRunManager().createScriptRun(
                        List.of(new LiteralSourceFile("runtime_query_regression.zs", script)), configuration).execute();
            } catch (Throwable failure) { throw new Exception("runtime query ZenCode compilation failed", failure); }
            // Post-execution listeners refresh definitions, so re-observe the same evaluation for this probe.
            if (core == null) {
                tile.observeRuntimeRecipeContext(run);
                var event = new RecipeTickEvent(tile, run, Phase.START); event.postEvent();
                if (!event.isPreventProgressing() || !"compiled_runtime_query_ok".equals(event.getFailureReason()))
                    throw new AssertionError("compiled ordinary query callback did not validate live fields");
            } else {
                core.observeRuntimeRecipeContext(run);
                var event = new FactoryRecipeTickEvent(core, tile, Phase.START, run); event.postEvent();
                if (!event.isPreventProgressing())
                    throw new AssertionError("compiled factory query callback did not validate its own thread/context");
            }
            Modularmachinery.LOG.info("[MMC][runtime-query-script] PASS: real CT {} nullable getters/arrays/context and event-thread identity", core == null ? "ordinary" : "factory");
        } finally {
            for (String name : List.of("recipes", "handlers", "callbacks")) {
                var field = ScriptRecipeRegistry.class.getDeclaredField(name); field.setAccessible(true);
                ((Map<?, ?>) field.get(null)).remove(recipe.registryName());
            }
        }
    }
}
