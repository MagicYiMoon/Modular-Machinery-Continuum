/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.Modularmachinery;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives {@link MachineLoader} at server start and on {@code /reload}.
 *
 * <p><b>Why a reload listener rather than a one-shot load.</b> Upstream read its definitions at a fixed
 * point during pre-initialisation, so changing a machine file required a full restart. Registering as
 * a {@link ResourceManagerReloadListener} through {@link AddReloadListenerEvent} — which fires on both
 * server start and {@code /reload} — preserves the existing file format and lifecycle contract while
 * gaining hot reload for free. Pack authors change machine definitions constantly while tuning, and
 * requiring a restart for that is the kind of friction that gets a mod worked around rather than used.
 *
 * <p>This deliberately does <b>not</b> move the definitions into {@code data/} or route them through
 * vanilla's recipe system; see {@link MachineLoader} for why.
 */
@Mod.EventBusSubscriber(modid = Modularmachinery.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MachineReloadListener {

    public record Definitions(MachineLoader machines,
            hellfirepvp.modularmachinery.common.crafting.RecipeLoader recipes) {
        public static Definitions empty() {
            return new Definitions(new MachineLoader(),
                    new hellfirepvp.modularmachinery.common.crafting.RecipeLoader());
        }
    }

    private static final Definitions serverDefinitions = Definitions.empty();
    private static volatile Definitions clientDefinitions = Definitions.empty();
    private static final java.util.concurrent.atomic.AtomicLong serverDefinitionRevision =
            new java.util.concurrent.atomic.AtomicLong();

    public static long getServerDefinitionRevision() {
        return serverDefinitionRevision.get();
    }

    private static Definitions current() {
        return net.minecraftforge.fml.util.thread.EffectiveSide.get().isServer()
                ? serverDefinitions : clientDefinitions;
    }

    public static Definitions serverDefinitions() {
        return serverDefinitions;
    }

    /** Called after vanilla has installed its new recipe manager, before sending the snapshot. */
    public static void refreshRecipes() {
        serverDefinitions.recipes().load(serverDefinitions.machines());
        serverDefinitionRevision.incrementAndGet();
    }

    /** Runs synchronously in CT's server reload apply stage, before script evaluation. */
    public static void prepareScriptReload() {
        ScriptMachineModifierRegistry.clear();
        hellfirepvp.modularmachinery.common.crafting.ScriptRecipeRegistry.clear();
        ScriptMachineEventRegistry.clear();
        hellfirepvp.modularmachinery.common.upgrade.RegistryMachineUpgrade.clearScriptRegistrations();
        hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry.clear();
        load(null);
        Modularmachinery.LOG.info("[MMCE] prepared machine definitions for CraftTweaker server scripts");
    }

    public static void installClientDefinitions(Definitions definitions) {
        clientDefinitions = definitions;
        notifyClientRecipesChanged();
    }

    public static void clearClientDefinitions() {
        clientDefinitions = Definitions.empty();
    }

    private MachineReloadListener() {
    }

    /** @return the active loader, whose parsed definitions the structure matcher will consume */
    public static MachineLoader getLoader() {
        return current().machines();
    }

    /** @return the active recipe loader */
    public static hellfirepvp.modularmachinery.common.crafting.RecipeLoader getRecipeLoader() {
        return current().recipes();
    }

    @SubscribeEvent
    public static void onAddReloadListeners(AddReloadListenerEvent event) {
        event.addListener((ResourceManagerReloadListener) resourceManager -> load(resourceManager));
    }

    













 
    public static void reloadNow() {
        // CT also runs on remote clients. Only the server owns definitions; a local
        // script run must not replace the server's synchronized recipes with local files.
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            Runnable reload = () -> {
                load(null);
                hellfirepvp.modularmachinery.common.network.DefinitionSync.sendToAll(server);
            };
            // Editor confirmations must observe the completed reload, not a queued write's old registry.
            if (server.isSameThread()) reload.run();
            else server.execute(reload);
        }
    }

    private static void load(ResourceManager resourceManager) {
        try {
            serverDefinitions.machines().load(resourceManager);
            // CT's listener can execute before or after this listener, including initial startup.
            // Reapply declarations to newly parsed definitions instead of discarding script settings.
            ScriptMachineModifierRegistry.reapply(serverDefinitions.machines());
            // Recipes are loaded after definitions because every recipe names a machine that must
            // already be known; loading them in the other order would reject every recipe.
            serverDefinitions.recipes().load(serverDefinitions.machines());
        } catch (Exception e) {
            // A failure here must not take the server down: the definitions are user-editable content
            // and a bad file should degrade to "no machines available", not a boot crash.
            Modularmachinery.LOG.error("[MMCE] failed to load machine definitions; "
                    + "some definitions may be unavailable", e);
        } finally {
            serverDefinitionRevision.incrementAndGet();
        }
    }

    /** Optional integration boundary: both distribution and mod presence must match. */
    public static void notifyClientRecipesChanged() {
        if (net.minecraftforge.fml.ModList.get().isLoaded("jei")) {
            net.minecraftforge.fml.DistExecutor.runWhenOn(
                    net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> hellfirepvp.modularmachinery.integration.jei.JeiPluginModularMachinery::requestReload);
        }
    }
}
