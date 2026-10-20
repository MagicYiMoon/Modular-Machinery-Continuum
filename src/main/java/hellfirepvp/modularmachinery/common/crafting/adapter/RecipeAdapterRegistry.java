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
import hellfirepvp.modularmachinery.common.event.RegisterRecipeAdaptersEvent;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraftforge.common.MinecraftForge;

/**
 * The registry a recipe's {@code "adapter"} key is resolved against -- upstream
 * {@code RecipeAdapterRegistry} plus {@code RegistriesMM.ADAPTER_REGISTRY}.
 *
 * <h2>Upstream shape, read from the body</h2>
 *
 * <p>Upstream's {@code RecipeAdapterRegistry.createRecipesFor(owningMachine, adapterKey, modifiers,
 * additionalRequirements, eventHandlers, recipeTooltips)} looks the adapter up in a Forge registry and
 * returns {@code null} when it is not there (U42-U46); {@code registerDynamicMachineAdapters()} then
 * registers one adapter PER MACHINE (U49-U53), which is the generic "copy another machine's recipes"
 * adapter. The null return is what {@code RecipeAdapterAccessor.loadRecipesForAdapter} turns into an empty
 * cache (U106-U109) -- and <b>that empty cache is silent</b>: upstream logs nothing when an adapter name does
 * not resolve, so a template naming a typo produces no recipe and no message. This port reports it; see
 * {@link RecipeAdapters#recordNotExpanded}.</p>
 *
 * <h2>Deviations, and their reasons</h2>
 *
 * <p><b>1. A map rather than a Forge registry.</b> Same era fact as {@code RequirementTypeRegistry}:
 * 1.20.1 has no {@code IForgeRegistryEntry}, so entries cannot carry a registry-assigned name, and a
 * {@link ResourceLocation}-keyed map is what upstream's registry becomes. Registration is by name, which is
 * all {@code "adapter": "minecraft:furnace"} needs.</p>
 *
 * <p><b>2. Dynamic machine adapters are registered after machine definitions load.</b> The upstream CE
 * registers one {@code DynamicMachineRecipeAdapter} for every machine, allowing a recipe template to use
 * {@code "adapter": "modularmachinery:other_machine"} and copy that machine's recipes. 1.20.1 has no
 * Forge registry entry lifecycle for this late-bound data, so the adapters live in this map and are replaced
 * on every definition reload.</p>
 *
 * <p><b>3. The two CraftTweaker parameters of {@code createRecipesFor} are not passed on</b> -- see
 * {@link RecipeAdapter}, deviation 2, which is where the reason lives because that is the signature
 * upstream declares them on.</p>
 */
public final class RecipeAdapterRegistry {

    private static final Map<ResourceLocation, RecipeAdapter> ADAPTERS = new LinkedHashMap<>();

    private static boolean registered = false;

    /** Names installed by the last dynamic-machine pass, so /reload cannot leave stale machine adapters. */
    private static final Set<ResourceLocation> DYNAMIC_NAMES = new LinkedHashSet<>();

    private RecipeAdapterRegistry() {
    }

    /**
     * Registers this mod's built-in adapters, once.
     *
     * <p>Same shape and same reasoning as {@code Requirements.registerBuiltIns()}: called by the loader
     * before it consults the registry rather than from a static block, so the registry cannot be consulted
     * before its built-ins are in it, and safe to call on every reload.</p>
     */
    public static synchronized void registerBuiltIns() {
        if (registered) {
            return;
        }
        registered = true;
        register(AdapterMinecraftFurnace.NAME, new AdapterMinecraftFurnace());
        register(AdapterMinecraftCooking.BLASTING,
                new AdapterMinecraftCooking<>(AdapterMinecraftCooking.BLASTING,
                        net.minecraft.world.item.crafting.RecipeType.BLASTING, 100));
        register(AdapterMinecraftCooking.SMOKING,
                new AdapterMinecraftCooking<>(AdapterMinecraftCooking.SMOKING,
                        net.minecraft.world.item.crafting.RecipeType.SMOKING, 100));
        register(AdapterMinecraftCooking.CAMPFIRE,
                new AdapterMinecraftCooking<>(AdapterMinecraftCooking.CAMPFIRE,
                        net.minecraft.world.item.crafting.RecipeType.CAMPFIRE_COOKING, 600));

        // Source-compatible extension point for optional 1.20.1 integrations.  The base jar never names
        // IC2, NuclearCraft, TConstruct or Thermal classes; an addon that has a current API can register its
        // own adapter here without making those mods mandatory for every MMCE installation.
        MinecraftForge.EVENT_BUS.post(new RegisterRecipeAdaptersEvent());
    }

    /**
     * Installs the source-compatible per-machine adapters for the current machine definitions.
     *
     * <p>This must be called after {@link hellfirepvp.modularmachinery.common.machine.MachineLoader#load}
     * and before adapter templates are expanded. It is intentionally idempotent and removes the previous
     * pass first, because machines can be added or removed by a config reload.</p>
     */
    public static synchronized void registerDynamicMachineAdapters(
            Collection<DynamicMachine> machines) {
        registerBuiltIns();
        for (ResourceLocation name : DYNAMIC_NAMES) {
            ADAPTERS.remove(name);
        }
        DYNAMIC_NAMES.clear();
        if (machines == null) {
            return;
        }
        for (DynamicMachine machine : machines) {
            if (machine == null || machine.getRegistryName() == null || machine.getRegistryName().isBlank()) {
                continue;
            }
            ResourceLocation name = new ResourceLocation(Modularmachinery.MODID, machine.getRegistryName());
            register(name, new DynamicMachineRecipeAdapter(name, machine));
            DYNAMIC_NAMES.add(name);
        }
    }

    /** Convenience lifecycle entry point used by the server definition reload path. */
    public static synchronized void registerDynamicMachineAdapters() {
        registerDynamicMachineAdapters(MachineReloadListener.getLoader().all());
    }

    /**
     * Registers an adapter.
     *
     * <p>A second registration under the same name REPLACES the first, matching
     * {@link hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementTypeRegistry}: an addon
     * that wants to override the furnace adapter is doing something this mod should allow rather than crash
     * on.</p>
     *
     * @return the adapter registered under this name before, or {@code null}
     */
    public static synchronized RecipeAdapter register(ResourceLocation name, RecipeAdapter adapter) {
        if (name == null || adapter == null) {
            return null;
        }
        RecipeAdapter previous = ADAPTERS.put(name, adapter);
        if (previous != null) {
            Modularmachinery.LOG.info("[MMCE] recipe adapter {} re-registered; the previous one is replaced",
                    name);
        }
        return previous;
    }

    /** @return the adapter registered under {@code name}, or {@code null} when there is none */
    @Nullable
    public static synchronized RecipeAdapter get(@Nullable ResourceLocation name) {
        return name == null ? null : ADAPTERS.get(name);
    }

    /** @return every registered adapter, in registration order; the loader lists these when one is unknown */
    public static synchronized List<ResourceLocation> names() {
        return List.copyOf(ADAPTERS.keySet());
    }

    /** @return the registry, unmodifiable */
    public static synchronized Map<ResourceLocation, RecipeAdapter> all() {
        return Collections.unmodifiableMap(ADAPTERS);
    }

    /**
     * Expands one template into the recipes its adapter generates.
     *
     * <p>Returns {@code null} -- the port's contract for "this template produced nothing" -- in both of the
     * ways that can happen, and <b>records which one it was</b>, because the two need different fixes: an
     * unknown adapter name is a typo or a missing addon, while an adapter that runs and finds nothing is a
     * target-mod problem.</p>
     *
     * @param adapterKey             the template's {@code "adapter"} value
     * @param owningMachine          the machine the generated recipes belong to
     * @param modifiers              the template's {@code modifiers}
     * @param additionalRequirements the template's {@code requirements}
     * @return the generated recipes, or {@code null} when nothing was generated
     */
    @Nullable
    public static Collection<MachineRecipe> createRecipesFor(ResourceLocation adapterKey, String owningMachine,
                                                             List<RecipeModifier> modifiers,
                                                             List<Requirement> additionalRequirements) {
        registerBuiltIns();
        RecipeAdapter adapter = get(adapterKey);
        if (adapter == null) {
            RecipeAdapters.recordNotExpanded(adapterKey, "no adapter is registered under that name; "
                    + "registered: " + names());
            return null;
        }
        Collection<MachineRecipe> recipes = adapter.createRecipesFor(owningMachine, modifiers,
                additionalRequirements);
        if (recipes == null || recipes.isEmpty()) {
            // NOT ONLY the null case: the shipped adapter records its own reason (no recipe manager, a
            // non-finite modifier value) before returning empty, so this line must not overwrite it with a
            // less specific one. It only speaks when the adapter had nothing to say.
            RecipeAdapters.recordNotExpanded(adapterKey, "the adapter ran and generated no recipes");
            return null;
        }
        return recipes;
    }
}
