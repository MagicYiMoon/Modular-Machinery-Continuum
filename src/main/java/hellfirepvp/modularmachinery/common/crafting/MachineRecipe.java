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
package hellfirepvp.modularmachinery.common.crafting;

import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * A loaded machine recipe.
 *
 * <p>Two forms exist in the shipped content and both must be supported:
 *
 * <ol>
 *   <li><b>A stateless recipe</b> — an explicit list of {@code requirements} plus a
 *       {@code recipeTime}. This is the mod's own format.</li>
 *   <li><b>An adapter</b> — {@code "adapter": "minecraft:furnace"} maps an existing vanilla recipe
 *       (here, smelting) into a machine, with {@code modifiers} scaling the resulting requirements.
 *       This is how a pack reuses the vanilla recipe set without restating every entry, and it is why
 *       the vanilla {@code RecipeManager} is read at all despite the mod having its own loader.</li>
 * </ol>
 *
 * <p>A recipe belongs to exactly one machine, named by {@code machine}. That is a deliberate coupling:
 * the recipe is validated against that machine's structure, so a recipe listing an energy requirement
 * for a machine with no energy hatch is a <b>load-time</b> error, not a runtime mystery.
 *
 * @param machine      the {@code registryname} of the machine this recipe runs on
 * @param registryName this recipe's own name, unique within the machine
 * @param recipeTime   duration in ticks
 * @param requirements the imposed requirements
 * @param adapter      the vanilla recipe type this recipe was generated from, or {@code null}
 * @param maxThreads   how many copies of THIS recipe a FACTORY may run at once, {@code -1} for unlimited
 * @param threadName   the factory thread this recipe is pinned to, {@code ""} for any
 * @param priority     recipe priority bucket; lower values are searched first, matching the upstream
 *                     {@code TreeMap<Integer, TreeSet<MachineRecipe>>} registry
 * @param commands     commands executed at recipe start, processing ticks and finish
 * @param tooltips     recipe-level lines shown by JEI (translation keys are resolved client-side)
 * @param parallelized whether this recipe may use the machine's parallel controller capacity
 * @param loadJEI      whether this recipe is registered with JEI
 */
public record MachineRecipe(
        String machine,
        String registryName,
        int recipeTime,
        List<Requirement> requirements,
        ResourceLocation adapter,
        /**
         * Whether a per-tick failure should DESTROY the in-flight recipe rather than let it retry.
         *
         * <p>RULE A0 -- upstream MachineRecipe:227 exposes this as {@code doesCancelRecipeOnPerTickFailure()}
         * over a field it calls {@code voidPerTickFailure}, and TileMachineController:290 passes it to
         * RecipeFailureEvent as the DEFAULT destructRecipe. THE FIELD NAME CARRIES THE DEFAULT: a recipe does
         * NOT void its inputs on a per-tick failure unless it SAYS SO, so this is false unless declared.</p>
         *
         * <p>ON A RECORD THE ACCESSOR IS GENERATED, so {@code cancelOnPerTickFailure()} is the whole getter.
         * The compact constructor below is deliberately NOT touched: it only defensively copies the
         * requirements list and validates nothing else, so a sixth component needs no constructor change.</p>
         *
         * <p><b>OWED: the JSON key is not read yet</b> (PLAN 531). Every recipe therefore gets false, WHICH IS
         * UPSTREAM OWN DEFAULT, so no existing pack changes behaviour -- but a pack that declares the flag
         * would be ignored until RecipeLoader reads it beside the single construction site.</p>
         */
        boolean cancelOnPerTickFailure,

        /**
         * Source per-recipe factory limit, -1 for unlimited. JSON and CraftTweaker can set it.
         * This port additionally enforces at most one active thread per recipe identity; a value of
         * zero disables factory scheduling, while positive limits cannot bypass that identity rule.
         * Batch copies within the one thread are controlled independently by parallelism.
         */
        int maxThreads,

        /**
         * The factory thread this recipe may ONLY run on, or {@code ""} for any thread.
         *
         * <p>Upstream: the field and its default ({@code MachineRecipe} L76 and L93, {@code ""}), the getter
         * (L236-L238), and the single reader {@code FactoryRecipeSearchTask.canCheck} L91-L94 -- a recipe whose
         * name is non-empty is skipped by any search whose own thread name does not match. Its writers are
         * CraftTweaker only ({@code RecipePrimer.setThreadName} L982-L984, and {@code mergeAdapter} L139-L141
         * for adapters), so the same A0 reasoning as {@link #maxThreads} applies and the key is read from the
         * recipe file under upstream's own name.</p>
         *
         * <p>Named core threads select their pinned recipes. Unnamed factory threads skip recipes
         * pinned to a name. Ordinary controllers do not apply the factory thread-name filter.</p>
         */
        String threadName,
        int priority,
        hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer commands,
        List<String> tooltips,
        boolean parallelized,
        boolean loadJEI,
        String displayName) {

    /** Compatibility constructor: existing recipes use their stable ID as their label. */
    public MachineRecipe(String machine, String registryName, int recipeTime,
                         List<Requirement> requirements, ResourceLocation adapter,
                         boolean cancelOnPerTickFailure, int maxThreads, String threadName,
                         int priority, hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer commands,
                         List<String> tooltips, boolean parallelized, boolean loadJEI) {
        this(machine, registryName, recipeTime, requirements, adapter, cancelOnPerTickFailure,
                maxThreads, threadName, priority, commands, tooltips, parallelized, loadJEI, "");
    }

    /** Compatibility constructor for the pre-metadata port. */
    public MachineRecipe(String machine, String registryName, int recipeTime,
                         List<Requirement> requirements, ResourceLocation adapter,
                         boolean cancelOnPerTickFailure, int maxThreads, String threadName,
                         int priority, hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer commands) {
        this(machine, registryName, recipeTime, requirements, adapter, cancelOnPerTickFailure,
                maxThreads, threadName, priority, commands, List.of(), hellfirepvp.modularmachinery.common.data.Config.recipeParallelizeEnabledByDefault(), true);
    }

    /** Compatibility constructor for code that creates a recipe without an explicit priority. */
    public MachineRecipe(String machine, String registryName, int recipeTime,
                         List<Requirement> requirements, ResourceLocation adapter,
                         boolean cancelOnPerTickFailure, int maxThreads, String threadName) {
        this(machine, registryName, recipeTime, requirements, adapter, cancelOnPerTickFailure,
                maxThreads, threadName, 0,
                hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer.empty());
    }

    /** Compatibility constructor for callers that provide priority but no command phases. */
    public MachineRecipe(String machine, String registryName, int recipeTime,
                         List<Requirement> requirements, ResourceLocation adapter,
                         boolean cancelOnPerTickFailure, int maxThreads, String threadName, int priority) {
        this(machine, registryName, recipeTime, requirements, adapter, cancelOnPerTickFailure,
                maxThreads, threadName, priority,
                hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer.empty());
    }

    public MachineRecipe {
        displayName = displayName == null || displayName.isBlank() ? "" :
                hellfirepvp.modularmachinery.common.editor.MachineNameFormatting.display(displayName);
        requirements = List.copyOf(requirements);
        tooltips = tooltips == null ? List.of() : List.copyOf(tooltips);
        // A NULL NAME WOULD BE AN NPE IN THE SEARCH, and the record is the one place every construction path
        // passes through. The loader never produces null (its string helper falls back), so this is a floor
        // rather than a validation -- it is also why the two new components need no other change.
        threadName = threadName == null ? "" : threadName;
    }

    /** Return a copy carrying an adapter/template priority. */
    public MachineRecipe withPriority(int value) {
        return new MachineRecipe(machine, registryName, recipeTime, requirements, adapter,
                cancelOnPerTickFailure, maxThreads, threadName, value, commands, tooltips, parallelized, loadJEI, displayName);
    }

    public hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer commands() {
        return commands;
    }

    public MachineRecipe withCommands(hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer value) {
        return new MachineRecipe(machine, registryName, recipeTime, requirements, adapter,
                cancelOnPerTickFailure, maxThreads, threadName, priority,
                value == null ? hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer.empty() : value,
                tooltips, parallelized, loadJEI, displayName);
    }

    public MachineRecipe withMetadata(List<String> value, boolean recipeParallelized, boolean showInJei) {
        return new MachineRecipe(machine, registryName, recipeTime, requirements, adapter,
                cancelOnPerTickFailure, maxThreads, threadName, priority, commands,
                value, recipeParallelized, showInJei, displayName);
    }

    /** Presentation only; registryName remains the identity for execution, saves and scripts. */
    public String displayNameOrId() {
        return displayName.isBlank() ? registryName : displayName;
    }

    public MachineRecipe withDisplayName(String value) {
        return new MachineRecipe(machine, registryName, recipeTime, requirements, adapter,
                cancelOnPerTickFailure, maxThreads, threadName, priority, commands,
                tooltips, parallelized, loadJEI, value);
    }

    public List<String> formattedTooltips() {
        // Kept as a common-side accessor.  JEI resolves translation keys on the client; returning a
        // defensive copy here keeps dedicated-server recipe snapshots free of client-only linkage.
        return List.copyOf(tooltips);
    }

    /**
     * @return whether a FACTORY may start another copy of this recipe while {@code running} copies are in
     *         flight -- upstream {@code FactoryRecipeSearchTask.canCheck} L97-L98: {@code -1} is unlimited.
     */
    public boolean allowsAnotherCopy(int running) {
        return maxThreads == -1 || running < maxThreads;
    }

    /**
     * @return whether a factory thread NAMED {@code threadName} may run this recipe -- upstream
     *         {@code FactoryRecipeSearchTask.canCheck} L91-L94, as the predicate that line spells out.
     */
    public boolean matchesThreadName(String threadName) {
        return this.threadName.isEmpty() || this.threadName.equals(threadName);
    }

    /**
     * @param ioType the direction to filter by
     * @return the requirements with that direction, in declaration order
     */
    public List<Requirement> requirementsOf(IOType ioType) {
        return requirements.stream().filter(r -> r.getIOType() == ioType).toList();
    }

    /**
     * Upstream's secondary recipe ordering weight. Inputs with an energy requirement are searched first,
     * then item inputs, then fluid inputs; outputs do not affect the weight.
     */
    public int sortingWeight() {
        int weight = 0;
        for (Requirement requirement : requirements) {
            if (requirement.getIOType() == IOType.OUTPUT) continue;
            String type = requirement.typeId();
            if ("modularmachinery:energy".equals(type)) weight += 50_000_000;
            else if ("modularmachinery:item".equals(type)
                    || "modularmachinery:ingredient_array_input".equals(type)) weight += 50_000;
            else if ("modularmachinery:fluid".equals(type)
                    || "modularmachinery:fluid_pertick".equals(type)) weight += 100;
        }
        return weight;
    }

    public boolean isAdapter() {
        return adapter != null;
    }

    /** @return a one-line summary, for load reporting and diagnostics */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append(registryName).append(" on ").append(machine)
                .append(" (").append(recipeTime).append("t)");
        if (isAdapter()) {
            sb.append(" [adapter ").append(adapter).append(']');
        }
        for (Requirement requirement : requirements) {
            sb.append("\n      ").append(requirement.describe());
        }
        return sb.toString();
    }
}
