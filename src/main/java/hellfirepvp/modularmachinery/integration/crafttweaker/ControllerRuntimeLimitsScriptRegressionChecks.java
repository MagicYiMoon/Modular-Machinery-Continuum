/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.*;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.*;
import hellfirepvp.modularmachinery.common.tiles.*;
import org.openzen.zencode.shared.LiteralSourceFile;
import java.util.*;

/** Compile source property names and execute them through the actual block ticker's START/END events. */
public final class ControllerRuntimeLimitsScriptRegressionChecks {
    private ControllerRuntimeLimitsScriptRegressionChecks() { }
    public static void run(TileController tile, String target) throws Exception {
        String script = """
                import mods.modularmachinery.MMEvents;
                import crafttweaker.api.data.IData;
                MMEvents.onMachinePreTick("%s", event => {
                    val c = event.controller;
                    c.customData["limits_start_age"] = c.ticksExisted as IData;
                    c.extraThreadCount = 2;
                });
                MMEvents.onMachinePostTick("%s", event => {
                    val c = event.controller;
                    c.customData["limits_end_age"] = c.ticksExisted as IData;
                    c.customData["limits_extra"] = c.extraThreadCount as IData;
                });
                """.formatted(target, target);
        var field = ScriptMachineEventRegistry.class.getDeclaredField("HANDLERS"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var handlers = (Map<String, Object>) field.get(null); Object previous = handlers.remove(target);
        var configuration = new ScriptRunConfiguration(CraftTweakerConstants.DEFAULT_LOADER_NAME,
                CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID, ScriptRunConfiguration.RunKind.EXECUTE);
        var recipes = MachineReloadListener.getRecipeLoader().getByMachine();
        var savedRecipes = new LinkedHashMap<>(recipes);
        try {
            try { CraftTweakerAPI.getScriptRunManager().createScriptRun(List.of(new LiteralSourceFile("controller_runtime_limits_regression.zs", script)), configuration).execute(); }
            catch (Throwable failure) { throw new Exception("runtime limits ZenCode compilation failed", failure); }
            // CT completion legitimately reloads recipes; reinstall the in-memory fixture before invoking its ticker.
            recipes.clear(); recipes.putAll(savedRecipes);
            int before = tile.getTicksExisted(); ControllerRuntimeLimitsRegressionChecks.tick(tile);
            if (tile.getTicksExisted() != before + 1 || tile.getCustomDataTag().getInt("limits_start_age") != before
                    || tile.getCustomDataTag().getInt("limits_end_age") != before
                    || tile.getCustomDataTag().getInt("limits_extra") != (tile instanceof TileFactoryController ? 2 : 0))
                throw new AssertionError("compiled age/delta properties failed in actual tick events: before=" + before
                        + " after=" + tile.getTicksExisted() + " data=" + tile.getCustomDataTag());
            Modularmachinery.LOG.info("[MMC][controller-runtime-limits-script] PASS: {} lifetime START/END and extraThreadCount assignment", tile.getBlockState().getBlock());
        } finally {
            recipes.clear(); recipes.putAll(savedRecipes);
            handlers.remove(target); if (previous != null) handlers.put(target, previous);
        }
    }
}
