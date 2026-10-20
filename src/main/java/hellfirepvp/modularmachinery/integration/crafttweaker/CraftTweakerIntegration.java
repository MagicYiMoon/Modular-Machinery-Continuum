/* SPDX-License-Identifier: GPL-3.0-only
 * Port of upstream ModIntegrationCrafttweaker (HellFirePvP / Modular Machinery 2019), modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerConstants;
import com.blamejared.crafttweaker.api.plugin.CraftTweakerPlugin;
import com.blamejared.crafttweaker.api.plugin.ICraftTweakerPlugin;
import com.blamejared.crafttweaker.api.plugin.IListenerRegistrationHandler;
import com.blamejared.crafttweaker.api.zencode.scriptrun.ScriptRunConfiguration;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;

/** Optional CT 14 integration; server declarations are synchronized through DefinitionSync. */
@CraftTweakerPlugin("modularmachinery:crafttweaker")
public class CraftTweakerIntegration implements ICraftTweakerPlugin {
    @Override
    public void registerListeners(IListenerRegistrationHandler handler) {
        // CT 14 calls this AFTER executing scripts, not before. The optional mixin into
        // ScriptReloadListener.prepareRun supplies the pre-run reset, including empty scripts.
        handler.onExecuteRun(configuration -> {
            if (configuration.runKind() != ScriptRunConfiguration.RunKind.EXECUTE
                    || !configuration.loadSource().id().equals(CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID)
                    || !configuration.loader().name().equals(CraftTweakerConstants.DEFAULT_LOADER_NAME)) return;
            // Reload only recipes: replacing machines here would discard script modifiers/models.
            MachineReloadListener.refreshRecipes();
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) server.execute(() ->
                    hellfirepvp.modularmachinery.common.network.DefinitionSync.sendToAll(server));
        });
    }
}
