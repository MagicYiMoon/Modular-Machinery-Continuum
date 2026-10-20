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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Collection;
import java.util.Collections;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.modifier.MultiBlockModifierReplacement;

/**
 * A loaded machine definition.
 *
 * <p>Upstream's {@code DynamicMachine} was a Gson-deserialised class holding the pattern plus a
 * deserialiser that resolved metadata immediately. Here the definition is kept as plain data with its
 * pattern in controller-relative coordinates, and resolution to {@code BlockState}s happens in a
 * separate pass so that a definition can be loaded, reported on, and only then accepted or rejected —
 * which matters because the source JSON is user-editable content.
 */
public final class DynamicMachine {

    private final String registryName;
    private final String localizedName;
    private final MachinePattern pattern;
    /** Source AbstractMachine prefix used by structure preview titles. */
    private String prefix = "";

    /**
     * This machine's modifiers, parsed from its JSON.
     *
     * <p><b>Section 224 found this as an untyped {@code Map<String, Object>} that nothing ever wrote to, and a
     * {@code getModifiers()} that nothing ever called</b> -- while the shipped machinery JSON declared modifiers
     * and even described their effect to the player ("Replace block above controller with machine vent for
     * double item output!"). The comment said "Populated later." and later never came.
     *
     * <p>It is now TYPED and POPULATED by {@code MachineLoader}. What is still missing is the second half of the
     * modifier feature: the structure-time test deciding whether a modifier is active, and the point in the
     * crafting path where it is applied. Both are recorded in PLAN sections 225 and 233 rather than implied.
     */
    /**
     * Whether forming this machine REQUIRES a blueprint to be bound first (PLAN section 292).
     *
     * <p>Upstream carries this as AbstractMachine.requiresBlueprint, and its JSON key
     * requires-blueprint shipped in the port own data from the start -- only nothing read it (section 263).
     *
     * <p>True by default, and that is the deliberate direction. A machine that must be bound explicitly can
     * never be formed by accident; a machine that may be detected by its structure can. So the permissive
     * behaviour is OPT-IN in the data, which is also what the shipped definitions do: of the four, only
     * assembly_line declares requires-blueprint false, and the other three omit the key.
     */
    private boolean requiresBlueprint = false;

    /**
     * Upstream's optional client presentation flag.  The value is kept on the definition even when
     * no optional model-hider integration is installed, so snapshots and integrations can make the
     * same decision as the source mod without changing machine behaviour.
     */
    private boolean hideComponentsWhenFormed = false;

    /**
     * What happens to recipe progress when a tick fails (PLAN section 310).
     *
     * <p>Upstream reads this from the machine JSON failure-action key and applies it in
     * ActiveMachineRecipe.doFailureAction. The port had neither, and its own shipped assembly_line.json
     * declares "decrease" -- so that machine progress decayed in the mod and did not in the port.
     *
     * <p>Defaults to the global {@code general/default-failure-actions} configuration, whose upstream default is STILL.
     */
    private RecipeFailureActions failureAction = RecipeFailureActions.getDefaultAction();

    /**
     * This machine's colour, per upstream AbstractMachine:19 (PLAN section 319).
     *
     * <p><b>The default is the CONFIG value, not zero</b> — upstream writes
     * {@code protected int definedColor = Config.machineColor;}, so a machine that declares no colour uses
     * the global one. That is also why the shipped {@code assembly_line.json} declaring {@code "FFFFFF"}
     * changes nothing by default: white is what the config already is.
     *
     * <p>Read from the JSON's {@code color} as a HEX STRING (upstream parses it with
     * {@code Integer.parseInt(hexStr, 16)} and rejects anything else).
     */
    private boolean hasFactory;
    public boolean isHasFactory() { return hasFactory; }
    public void setHasFactory(boolean value) { hasFactory = value; }

    private int definedColor = hellfirepvp.modularmachinery.common.data.Config.machineColor();
    // Server config supplies defaults; JSON and CraftTweaker builders/modifiers may override them.
    // Capture them per definition so config reloads cannot change a paid run in place.
    public static final int DEFAULT_MAX_PARALLELISM = 2048;
    private int maxParallelism = hellfirepvp.modularmachinery.common.data.Config.maxMachineParallelism();
    private int internalParallelism = 0;
    private boolean parallelizable = hellfirepvp.modularmachinery.common.data.Config.machineParallelizeEnabledByDefault();

    /**
     * The machine's repeating structure segments, keyed by their JSON name (PLAN section 322).
     *
     * <p>Upstream holds the same map and reads it in the matcher, the preview and the auto-assembly handler.
     * The port read none of it, which is why the shipped assembly_line formed from its wall alone while the mod
     * refuses to form it without the line.
     *
     * <p>Empty is the normal state: one shipped machine of five declares a dynamic pattern.
     */
    private final java.util.Map<String, DynamicPattern> dynamicPatterns = new java.util.HashMap<>();
    private final List<RecipeModifier> modifiers = new ArrayList<>();
    /** Optional structure conditions supplied by the scripting API.  These are not formation parts. */
    private final List<MultiBlockModifierReplacement> multiBlockModifierReplacements = new ArrayList<>();

    public DynamicMachine(String registryName, String localizedName, MachinePattern pattern) {
        this.registryName = registryName;
        this.localizedName = localizedName;
        this.pattern = pattern;
    }


    /** Returns whether this machine needs a blueprint bound before it can form. */
    public boolean isRequiresBlueprint() {
        return requiresBlueprint;
    }

    /** @return whether component block models should be hidden while this machine is formed */
    public boolean isHideComponentsWhenFormed() {
        return hideComponentsWhenFormed;
    }

    public void setHideComponentsWhenFormed(boolean hideComponentsWhenFormed) {
        this.hideComponentsWhenFormed = hideComponentsWhenFormed;
    }
    /** Returns what happens to recipe progress when a tick fails. */
    public RecipeFailureActions getFailureAction() {
        return failureAction;
    }


    /** @return this machine's dynamic patterns, keyed by name; empty for most machines */
    public java.util.Map<String, DynamicPattern> getDynamicPatterns() {
        return dynamicPatterns;
    }

    /** Adds one dynamic pattern under its own name, as upstream does. */
    public void addDynamicPattern(DynamicPattern pattern) {
        if (pattern != null) {
            dynamicPatterns.put(pattern.getName(), pattern);
        }
    }

    /** Returns this machine's colour, which defaults to the config value. */
    public int getDefinedColor() {
        return definedColor;
    }

    /** Sets this machine's colour. Named as upstream names it, so the two read alike. */
    public void setDefinedColor(int definedColor) {
        this.definedColor = definedColor;
    }
    /**
     * Sets what happens to recipe progress when a tick fails.
     *
     * <p>A null argument is ignored rather than stored: the field is read on every failing tick and a null
     * would be a crash in the crafting path, while a machine JSON cannot produce null through byName.
     */
    public void setFailureAction(RecipeFailureActions failureAction) {
        if (failureAction != null) {
            this.failureAction = failureAction;
        }
    }

    /**
     * Sets whether a blueprint is needed before forming.
     *
     * <p>False opts this machine into STRUCTURE DETECTION: an unbound controller may pick it out by
     * matching its pattern, which is what the flag means upstream.
     */
    public void setRequiresBlueprint(boolean requiresBlueprint) {
        this.requiresBlueprint = requiresBlueprint;
    }
    public String getRegistryName() {
        return registryName;
    }

    public String getLocalizedName() {
        return localizedName;
    }

    public String getPrefix() {
        return prefix == null || prefix.isBlank() ? getTranslationKey() + ".prefix" : prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix == null ? "" : prefix;
    }

    public MachinePattern getPattern() {
        return pattern;
    }

    /**
     * This machine's modifier REPLACEMENTS: each a structure condition plus the rules it activates.
     *
     * <p>Held alongside the flattened {@link #modifiers} because the two answer different questions. The flat
     * list is what a crafting context will apply once a condition is known to hold; this list is what decides
     * WHETHER it holds -- upstream's {@code matches(controller)}, the structure-time test.
     */
    private final List<hellfirepvp.modularmachinery.common.modifier.ModifierReplacement> modifierReplacements =
            new ArrayList<>();

    /** @return this machine's modifier replacements, in declaration order */
    public List<hellfirepvp.modularmachinery.common.modifier.ModifierReplacement> getModifierReplacements() {
        return modifierReplacements;
    }

    /**
     * Replaces both views of the machine's modifiers at once.
     *
     * <p>Taking the replacements and deriving the flat list from them means the two cannot disagree: a rule
     * that reached the flat list without a condition to activate it would be a rule that silently applies
     * always, which is exactly the wrong behaviour this shape exists to prevent.
     */
    public void setModifierReplacements(
            List<hellfirepvp.modularmachinery.common.modifier.ModifierReplacement> parsed) {
        this.modifierReplacements.clear();
        if (parsed != null) {
            this.modifierReplacements.addAll(parsed);
        }
        rebuildDeclaredModifiers();
    }

    /** @return optional multi-cell modifier conditions, in declaration order */
    public List<MultiBlockModifierReplacement> getMultiBlockModifierReplacements() {
        return multiBlockModifierReplacements;
    }

    /** Alias matching the upstream DynamicMachine API. */
    public List<MultiBlockModifierReplacement> getMultiBlockModifiers() {
        return multiBlockModifierReplacements;
    }

    /** Replaces the optional multi-cell conditions and keeps the flattened modifier view complete. */
    public void setMultiBlockModifierReplacements(List<MultiBlockModifierReplacement> parsed) {
        this.multiBlockModifierReplacements.clear();
        if (parsed != null) {
            this.multiBlockModifierReplacements.addAll(parsed);
        }
        rebuildDeclaredModifiers();
    }

    /** Adds one scripted multi-cell condition. */
    public void addMultiBlockModifierReplacement(MultiBlockModifierReplacement replacement) {
        if (replacement != null) {
            this.multiBlockModifierReplacements.add(replacement);
            this.modifiers.addAll(replacement.getModifiers());
        }
    }

    private void rebuildDeclaredModifiers() {
        // Single-block replacements are the JSON-backed source of the first part of this list.
        this.modifiers.clear();
        for (hellfirepvp.modularmachinery.common.modifier.ModifierReplacement replacement
                : modifierReplacements) {
            this.modifiers.addAll(replacement.getModifiers());
        }
        for (MultiBlockModifierReplacement replacement : multiBlockModifierReplacements) {
            this.modifiers.addAll(replacement.getModifiers());
        }
    }

    /** @return this machine's modifiers, in declaration order; empty when it declares none */
    public List<RecipeModifier> getModifiers() {
        return modifiers;
    }

    /**
     * Replaces the modifier list. Called by {@code MachineLoader} once the machine's JSON has been read.
     *
     * <p>A setter rather than a constructor parameter because a machine is built before its JSON is walked.
     */
    public void setModifiers(List<RecipeModifier> parsed) {
        this.modifiers.clear();
        if (parsed != null) {
            this.modifiers.addAll(parsed);
        }
    }

    /** @return the translation key for this machine's display name */
    public String getTranslationKey() {
        return "machine.modularmachinery." + registryName + ".name";
    }

    @Override
    public String toString() {
        return "DynamicMachine[" + registryName + ", parts=" + pattern.size() + "]";
    }
    /** @return the ceiling this machine allows, before the parallel controllers add to it */
    public int getMaxParallelism() {
        return maxParallelism;
    }

    /** @return this machine own base parallelism, which a pack declares in the machine JSON */
    /** Sets the ceiling directly. Upstream AbstractMachine U102-U104 is a plain assignment and so is this. */
    public void setMaxParallelism(int maxParallelism) {
        this.maxParallelism = maxParallelism;
    }

    public int getInternalParallelism() {
        return internalParallelism;
    }

    /**
     * Sets the machine own base parallelism.
     *
     * <p>AND IT RAISES THE CEILING WHEN THE BASE EXCEEDS IT -- upstream AbstractMachine U110-U115 does exactly this,
     * and it is the sort of detail that is invisible from a signature: a machine declaring a base of 64 would
     * otherwise be silently clamped to a ceiling of 8 and report a parallelism it never runs at.</p>
     */
    public void setInternalParallelism(int internalParallelism) {
        if (maxParallelism < internalParallelism) {
            this.maxParallelism = internalParallelism;
        }
        this.internalParallelism = internalParallelism;
    }

    /** @return whether this machine may run a recipe in parallel at all */
    public boolean isParallelizable() {
        return parallelizable;
    }

    public void setParallelizable(boolean parallelizable) {
        this.parallelizable = parallelizable;
    }

    // ------------------------------------------------------------------
    // Smart interface types (PLAN 663, B2)
    // ------------------------------------------------------------------

    /**
     * The smart interface TYPES this machine declares, keyed by their own name.
     *
     * <p><b>UPSTREAM'S FIELD AND ALL SIX OF ITS ACCESSORS, KEPT NAME FOR NAME</b>
     * ({@code DynamicMachine} U75 and U107-U140): a machine that declares none of these can never have a
     * value published to one of its interfaces, because the binding walk in {@code MachineFormation} is
     * gated on this map being non-empty. That gate is upstream's own
     * ({@code checkAndAddSmartInterface}'s first line) and it is why the requirement's producer is this
     * declaration rather than the interface block.</p>
     *
     * <p><b>RULE A0 — THE SOURCE OF THE DECLARATIONS IS THE PORT'S, AND THE EVIDENCE IS TWO FILES.</b>
     * Upstream populates this map from CraftTweaker only: {@code MachineBuilder.addSmartInterfaceType}
     * (U337-U342) and {@code MachineModifier.addSmartInterfaceType} (U27-U40) are its only writers — verified
     * by grepping every {@code addSmartInterfaceType} caller in the upstream tree — and the script layer is
     * also where upstream's own reload CLEARS it ({@code ModIntegrationCrafttweaker} U84,
     * {@code loadedMachine.getSmartInterfaceTypes().clear()}). There is no JSON key for it upstream: the
     * machine deserialiser's key list ({@code DynamicMachine} U611-U676) contains no smart-interface entry at
     * all. The port also supports JSON {@code smart-interface-types}; CraftTweaker builders and
     * modifiers can add or replace types after loading the definition.</p>
     *
     * <p><b>A {@link LinkedHashMap} RATHER THAN UPSTREAM'S {@code HashMap}, AND THE DIFFERENCE IS ONLY
     * TIE-BREAKING:</b> the binding walk sorts the candidates with {@link
     * hellfirepvp.modularmachinery.common.util.SmartInterfaceType#compareTo}, so priority decides first;
     * two types with EQUAL priority are then picked in iteration order, which upstream resolves by String
     * hash and this port resolves by declaration order. Declaration order is the one a pack author can
     * predict, and the whole point of the priority field is predictability.</p>
     */
    private final Map<String, hellfirepvp.modularmachinery.common.util.SmartInterfaceType> smartInterfaceTypes =
            new LinkedHashMap<>();

    /** @return whether this machine declares a smart interface type with this name */
    public boolean hasSmartInterfaceType(String type) {
        return smartInterfaceTypes.containsKey(type);
    }

    /** @return this machine's declared smart interface types, keyed by name */
    public Map<String, hellfirepvp.modularmachinery.common.util.SmartInterfaceType> getSmartInterfaceTypes() {
        return smartInterfaceTypes;
    }

    /** @return the declared type with this name, or {@code null} when the machine does not declare it */
    public hellfirepvp.modularmachinery.common.util.SmartInterfaceType getSmartInterfaceType(String type) {
        return smartInterfaceTypes.get(type);
    }

    /** Declares one smart interface type. Called by {@code MachineLoader} once the JSON has been read. */
    public void addSmartInterfaceType(hellfirepvp.modularmachinery.common.util.SmartInterfaceType type) {
        smartInterfaceTypes.put(type.getType(), type);
    }

    /** @return the highest-priority declared type, or empty — the type a surplus interface receives */
    public java.util.Optional<hellfirepvp.modularmachinery.common.util.SmartInterfaceType>
            getFirstSmartInterfaceType() {
        return smartInterfaceTypes.values().stream().sorted().findFirst();
    }

    /** @return true when this machine declares no smart interface types at all (upstream's own gate) */
    public boolean smartInterfaceTypesIsEmpty() {
        return smartInterfaceTypes.isEmpty();
    }

    /**
     * The declared types whose names are NOT in {@code ignoredTypes}.
     *
     * <p>Upstream U131-U140, used by the binding walk to hand each interface a DIFFERENT declared type where
     * it can. The set passed in is the types already assigned during the current walk, so a machine with two
     * interfaces and two declared types ends up with one each rather than both on the highest priority.</p>
     */
    public Map<String, hellfirepvp.modularmachinery.common.util.SmartInterfaceType> getFilteredType(
            java.util.Collection<String> ignoredTypes) {
        Map<String, hellfirepvp.modularmachinery.common.util.SmartInterfaceType> filtered = new LinkedHashMap<>();
        smartInterfaceTypes.forEach((type, data) -> {
            if (!ignoredTypes.contains(type)) {
                filtered.put(type, data);
            }
        });
        return filtered;
    }

    // ------------------------------------------------------------------
    // Factory capacity (PLAN 662)
    // ------------------------------------------------------------------

    /** Factory runs per machine, independent of each run's batch multiplier. */
    private int maxThreads = hellfirepvp.modularmachinery.common.data.Config.defaultFactoryMaxThreads();

    /** Fixed factory-thread recipe sets supplied by a Java/CraftTweaker integration. */
    private final Map<String, List<String>> coreThreadPresets = new LinkedHashMap<>();
    private final Map<String, Map<String, RecipeModifier>> coreThreadPermanentModifiers = new LinkedHashMap<>();
    private final Map<String, Map<String, RecipeModifier>> coreThreadSemiPermanentModifiers = new LinkedHashMap<>();

    /** MMCE's loaded config default (the field initializer before load was 20). */
    public static final int DEFAULT_FACTORY_MAX_THREADS = 10;

    /** @return how many factory runs one controller of this machine may hold at once */
    public int getMaxThreads() {
        return maxThreads;
    }

    /**
     * Sets the factory run ceiling.
     *
     * <p>A NEGATIVE VALUE IS REFUSED RATHER THAN STORED, which is upstream's own rule at the only place upstream
     * validates it: {@code MachineModifier.setMaxThreads} logs an error and returns for {@code maxThreads < 0}
     * (L73-U84). Storing one would be worse than an error message here -- {@code hasIdleThread} compares against
     * this number, so a negative ceiling silently disables the machine's factory mode with nothing reported.</p>
     */
    public void setMaxThreads(int maxThreads) {
        if (maxThreads < 0) {
            return;
        }
        this.maxThreads = maxThreads;
    }

    /** Adds or replaces one named factory thread preset. An empty set means all recipes with that thread name. */
    public void addCoreThreadPreset(String threadName, Collection<String> recipeNames) {
        addCoreThreadPreset(threadName, recipeNames, Map.of(), Map.of());
    }

    public void addCoreThreadPreset(String threadName, Collection<String> recipeNames,
                                    Map<String, RecipeModifier> permanent, Map<String, RecipeModifier> semiPermanent) {
        if (threadName == null || threadName.isBlank()) return;
        List<String> names = new ArrayList<>();
        if (recipeNames != null) {
            for (String recipe : recipeNames) {
                if (recipe != null && !recipe.isBlank() && !names.contains(recipe)) names.add(recipe);
            }
        }
        coreThreadPresets.put(threadName, List.copyOf(names));
        coreThreadPermanentModifiers.put(threadName, permanent == null ? Map.of() : Map.copyOf(permanent));
        coreThreadSemiPermanentModifiers.put(threadName, semiPermanent == null ? Map.of() : Map.copyOf(semiPermanent));
    }

    public void removeCoreThreadPreset(String threadName) {
        if (threadName != null) {
            coreThreadPresets.remove(threadName);
            coreThreadPermanentModifiers.remove(threadName);
            coreThreadSemiPermanentModifiers.remove(threadName);
        }
    }

    /** @return immutable factory thread name to recipe-name set mapping */
    public Map<String, List<String>> getCoreThreadPresets() {
        return Collections.unmodifiableMap(coreThreadPresets);
    }

    public Map<String, Map<String, RecipeModifier>> getCoreThreadPermanentModifiers() {
        return Collections.unmodifiableMap(coreThreadPermanentModifiers);
    }

    public Map<String, Map<String, RecipeModifier>> getCoreThreadSemiPermanentModifiers() {
        return Collections.unmodifiableMap(coreThreadSemiPermanentModifiers);
    }
}
