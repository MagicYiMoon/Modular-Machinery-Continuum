/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Upstream attribution is retained as
 * GPL-3.0 requires. Modifications made 2026-10.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.crafting.adapter;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The two services a server-side adapter needs, plus the record of what the last expansion did.
 *
 * <h2>Why the adapter asks the running server rather than being handed a level</h2>
 *
 * <p>This port's recipe loader ({@code RecipeLoader}) reads files from
 * {@code config/modularmachinery/recipes/} and is driven by {@code MachineReloadListener}, which is a vanilla
 * reload listener: it is handed a {@code ResourceManager} and nothing else. Upstream's adapters were reached
 * the same way -- through {@code RecipeAdapterRegistry.createRecipesFor} from {@code RecipeRegistry}, whose
 * only context is {@code CommonProxy.dataHolder} -- so a level is not in the call chain on either side.</p>
 *
 * <p><b>The 1.20.1 equivalent of "the server is running" is {@code ServerLifecycleHooks}, and it must be
 * checked rather than assumed.</b> {@code RecipeLoader.load} runs from {@code AddReloadListenerEvent}, which
 * fires on the dedicated-server boot sequence and on {@code /reload} -- in both cases the server object
 * exists and its recipe manager is populated by the time Forge reaches this listener, because a
 * {@code RecipeManager} is applied by the same reload. But the loader is also reachable from
 * {@code CraftTweakerIntegration}'s reload coordination, so "is a server up" is a question with a real
 * answer, and a null one is REPORTED rather than treated as "no recipes".</p>
 *
 * <p>This is deliberately not a cached static: a single-player world leaving to the title screen and
 * re-entering produces a new server with a new recipe manager, and a cached one would make the second world
 * convert the first world's recipes.</p>
 *
 * <h2>The probe record, and why a conversion needs one</h2>
 *
 * <p>The adapter's two failure modes are silent by construction: a vanilla smelting recipe whose input is a
 * <b>tag</b> converts into nothing, and a conversion that produced fewer recipes than the furnace has leaves
 * no missing recipe behind to notice. {@code AdapterMinecraftFurnace.Probe} counts both, and this class holds
 * the last one. That is what makes the difference measurable at load rather than a claim in a comment.</p>
 */
public final class RecipeAdapters {

    /** Last expansion result per adapter, replaced wholesale by {@link #captureProbes()}. */
    private static final Map<ResourceLocation, AdapterMinecraftFurnace.Probe> PROBES = new LinkedHashMap<>();

    /** Adapters a template named that could not be resolved or could not run, with the reason. */
    private static final List<String> NOT_EXPANDED = new ArrayList<>();

    private RecipeAdapters() {
    }

    /** @return the running server's recipe manager, or {@code null} when no server is up */
    @Nullable
    public static RecipeManager recipeManager() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? null : server.getRecipeManager();
    }

    /**
     * @return the registry access a recipe's result stack is resolved against
     *
     * <p>{@code SmeltingRecipe.getResultItem(RegistryAccess)} requires one because a result stack may name a
     * registry entry. The server's own is what the tick path uses, so an adapter cannot resolve a stack
     * differently from the rest of the game.</p>
     */
    public static RegistryAccess registryAccess() {
        var server = ServerLifecycleHooks.getCurrentServer();
        return server == null ? RegistryAccess.EMPTY : server.registryAccess();
    }

    static void recordProbe(ResourceLocation adapter, AdapterMinecraftFurnace.Probe probe) {
        synchronized (PROBES) {
            PROBES.put(adapter, probe);
        }
    }

    /**
     * Records that a template named {@code adapter} and nothing came of it.
     *
     * <p><b>This is the entry the user's contract turns on.</b> A recipe carrying an adapter name that cannot
     * be resolved must not be silently absent; it must be named at load time. The loader appends these to its
     * own problem list.</p>
     */
    static void recordNotExpanded(ResourceLocation adapter, String reason) {
        synchronized (NOT_EXPANDED) {
            NOT_EXPANDED.add(adapter + ": " + reason);
        }
    }

    /** Empties the probe map, returning what was in it; the loader calls this at the start of every load. */
    public static Map<ResourceLocation, AdapterMinecraftFurnace.Probe> captureProbes() {
        synchronized (PROBES) {
            Map<ResourceLocation, AdapterMinecraftFurnace.Probe> out = new LinkedHashMap<>(PROBES);
            PROBES.clear();
            return out;
        }
    }

    /** Empties the not-expanded list, returning what was in it. */
    public static List<String> captureNotExpanded() {
        synchronized (NOT_EXPANDED) {
            List<String> out = List.copyOf(NOT_EXPANDED);
            NOT_EXPANDED.clear();
            return out;
        }
    }

    /** Reports every unresolved adapter name at WARN, beside the other load-time reports. */
    public static void reportNotExpanded(List<String> notExpanded) {
        if (notExpanded.isEmpty()) {
            return;
        }
        Modularmachinery.LOG.warn("[MMCE] {} adapter template(s) could not be expanded into runnable "
                + "recipes -- each one produces NOTHING until it is fixed:", notExpanded.size());
        notExpanded.forEach(entry -> Modularmachinery.LOG.warn("[MMCE]   {}", entry));
    }
}
