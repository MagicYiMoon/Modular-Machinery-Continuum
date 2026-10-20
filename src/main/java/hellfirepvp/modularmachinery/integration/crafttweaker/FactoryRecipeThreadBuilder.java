/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import org.openzen.zencode.java.ZenCodeType;

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;

/**
 * CraftTweaker 14 facade for the source mod's fixed factory-thread preset.
 *
 * <p>The 1.12 class was a live {@code FactoryRecipeThread} object which could be created before a
 * machine existed and filled through deferred recipe lookups.  The 1.20.1 port stores the equivalent
 * recipe names on {@link DynamicMachine}; attaching a completed builder is deterministic and avoids
 * retaining a tile entity or a stale recipe object across {@code /reload}.</p>
 */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.FactoryRecipeThread")
public final class FactoryRecipeThreadBuilder {
    private final String threadName;
    private final List<String> recipeNames = new ArrayList<>();
    private final Map<String, RecipeModifier> permanentModifiers = new LinkedHashMap<>();
    private final Map<String, RecipeModifier> semiPermanentModifiers = new LinkedHashMap<>();

    private FactoryRecipeThreadBuilder(String threadName) {
        if (threadName == null || threadName.isBlank()) {
            throw new IllegalArgumentException("core factory thread name cannot be empty");
        }
        this.threadName = threadName;
    }

    /** Source-compatible factory entry point: FactoryRecipeThread.createCoreThread(name). */
    @ZenCodeType.Method
    public static FactoryRecipeThreadBuilder createCoreThread(String threadName) {
        return new FactoryRecipeThreadBuilder(threadName);
    }

    /** Adds one recipe registry path. Duplicate names are ignored in declaration order. */
    @ZenCodeType.Method
    public FactoryRecipeThreadBuilder addRecipe(String recipeName) {
        if (recipeName == null || recipeName.isBlank()) {
            throw new IllegalArgumentException("recipe name cannot be empty");
        }
        if (!recipeNames.contains(recipeName)) recipeNames.add(recipeName);
        return this;
    }

    /** Adds several recipe registry paths to this fixed thread. */
    @ZenCodeType.Method
    public FactoryRecipeThreadBuilder addRecipes(String[] recipes) {
        if (recipes != null) {
            for (String recipe : recipes) addRecipe(recipe);
        }
        return this;
    }

    /** Adds the source thread's semi-permanent modifier; it is cleared after a completed craft. */
    @ZenCodeType.Method
    public FactoryRecipeThreadBuilder addModifier(String key, RecipeModifier modifier) {
        if (key == null || key.isBlank() || modifier == null) throw new IllegalArgumentException("modifier key/value required");
        semiPermanentModifiers.put(key, modifier); return this;
    }

    /** Adds a modifier that survives every completed craft on this core thread. */
    @ZenCodeType.Method
    public FactoryRecipeThreadBuilder addPermanentModifier(String key, RecipeModifier modifier) {
        if (key == null || key.isBlank() || modifier == null) throw new IllegalArgumentException("modifier key/value required");
        permanentModifiers.put(key, modifier); return this;
    }

    /** Attaches the preset to a loaded machine. */
    @ZenCodeType.Method
    public boolean addToMachine(String machineName) {
        return MachineModifierFacade.addCoreThread(machineName, this);
    }

    @ZenCodeType.Getter("threadName")
    public String getThreadName() {
        return threadName;
    }

    @ZenCodeType.Getter("recipeNames")
    public List<String> getRecipeNames() {
        return List.copyOf(recipeNames);
    }

    public Map<String, RecipeModifier> getPermanentModifiers() { return Map.copyOf(permanentModifiers); }
    public Map<String, RecipeModifier> getSemiPermanentModifiers() { return Map.copyOf(semiPermanentModifiers); }
}
