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
 * original named above. Modifications made 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.crafting.helper;

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.ArrayList;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementCatalyst;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;






























 
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.RecipeCraftingContext")
public final class RecipeCraftingContext {

    /** Event-local last observation survives completion so the closing END event still identifies its craft. */
    private hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe observedRecipe;
    private hellfirepvp.modularmachinery.common.machine.RecipeThread runtimeThread;

    public void bindRuntimeThread(hellfirepvp.modularmachinery.common.machine.RecipeThread thread) { runtimeThread = thread; }
    public hellfirepvp.modularmachinery.common.machine.RecipeThread runtimeThread() { return runtimeThread; }

    public void observeRecipe(hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe recipe) { observedRecipe = recipe; }
    public hellfirepvp.modularmachinery.common.crafting.ActiveMachineRecipe observedRecipe() { return observedRecipe; }

    @org.openzen.zencode.java.ZenCodeType.Getter("recipeName")
    public String getRecipeName() { return recipe.registryName(); }

    @org.openzen.zencode.java.ZenCodeType.Getter("effectiveDuration")
    public int getEffectiveDuration() { return effectiveDuration(); }

    @org.openzen.zencode.java.ZenCodeType.Getter("requirementDescriptions")
    public String[] getRequirementDescriptions() {
        return requirements.stream().map(Requirement::describe).toArray(String[]::new);
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("inputDescriptions")
    public String[] getInputDescriptions() {
        return requirementsOf(IOType.INPUT).stream().map(Requirement::describe).toArray(String[]::new);
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("outputDescriptions")
    public String[] getOutputDescriptions() {
        return requirementsOf(IOType.OUTPUT).stream().map(Requirement::describe).toArray(String[]::new);
    }

    private final MachineRecipe recipe;

    /**
     * The requirements as they stand for this run.
     *
     * <p>Equal to the recipe's own list until a modifier is applied. Held as a field rather than recomputed so
     * that modifiers -- which arrive in stage 4 -- can replace it once and have every later evaluation agree.
     */
    private final List<Requirement> requirements;
    /** The source-style combination group chosen for this run, when isolation input is active. */
    private String selectedIsolationGroupKey;
    /**
     * The integer duration factor used by upstream's delayed-trigger path.
     *
     * <p>This is intentionally not {@code recipeTime / modifiedRecipeTime}.  The 1.12.2
     * implementation calls {@code applyModifiers(..., 1, false)} and rounds that value before
     * multiplying a requirement's trigger time.  In particular, an additive duration modifier
     * contributes to this factor itself; deriving it from the final recipe length changes the
     * meaning of both additive and sub-one modifiers.</p>
     */
    private final float triggerTimeScale;
    private final java.util.Set<Integer> activeCatalysts;
    private final List<RecipeModifier> activeModifiers;
    /** Legacy declarative upgrade speed is applied after duration modifiers, without changing trigger scale. */
    private final float upgradeDurationFactor;

    public RecipeCraftingContext(MachineRecipe recipe) {
        this(recipe, recipe.requirements());
    }

    /**
     * A run whose requirements have been scaled by the modifiers that are ACTIVE for this machine.
     *
     * <p><b>The caller must pass only the active ones.</b> The structure conditions live in
     * {@code ModifierReplacement}, and a modifier whose condition does not hold must not be here -- applying it
     * would grant the vent bonus whether or not the vent exists, which is a worse defect than not applying it at
     * all. That is why the condition was built before this constructor.
     *
     * <p>Each requirement is passed through every modifier in turn, so two modifiers on the same target compose      * in declaration order.
     */
    public static RecipeCraftingContext withModifiers(MachineRecipe recipe,
            java.util.List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> activeModifiers) {
        float scale = Math.round(hellfirepvp.modularmachinery.common.modifier.RecipeModifier
                .applyDurationModifiers(activeModifiers, 1F));
        return new RecipeCraftingContext(recipe, apply(recipe.requirements(), activeModifiers), scale, activeModifiers);
    }

    /** Resolves optional source-style catalysts and folds their modifiers into this run. */
    public static RecipeCraftingContext withModifiers(MachineRecipe recipe,
            java.util.List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> baseModifiers,
            MachineContext context) {
        return withModifiers(recipe, baseModifiers, context, java.util.Set.of());
    }

    public static RecipeCraftingContext withModifiers(MachineRecipe recipe,
            java.util.List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> baseModifiers,
            MachineContext context, java.util.Set<Integer> forcedCatalysts) {
        return withModifiers(recipe, baseModifiers, context, forcedCatalysts, 1F);
    }

    public static RecipeCraftingContext withModifiers(MachineRecipe recipe, List<RecipeModifier> baseModifiers,
            MachineContext context, java.util.Set<Integer> forcedCatalysts, float upgradeDurationFactor) {
        List<RecipeModifier> all = new ArrayList<>();
        if (baseModifiers != null) all.addAll(baseModifiers);
        List<Requirement> first = apply(recipe.requirements(), all);
        List<Boolean> catalystState = new ArrayList<>();
        int catalystIndex = 0;
        for (Requirement requirement : first) {
            if (requirement instanceof RequirementCatalyst catalyst) {
                boolean present = (forcedCatalysts != null && forcedCatalysts.contains(catalystIndex))
                        || (context != null && catalyst.detect(context));
                catalystState.add(present);
                if (present) all.addAll(catalyst.getModifiers());
                catalystIndex++;
            }
        }
        List<Requirement> resolved = apply(recipe.requirements(), all);
        int resolvedCatalystIndex = 0;
        for (Requirement requirement : resolved) {
            if (requirement instanceof RequirementCatalyst catalyst) {
                catalyst.setActive(resolvedCatalystIndex < catalystState.size() && catalystState.get(resolvedCatalystIndex++));
            }
        }
        float scale = Math.round(RecipeModifier.applyDurationModifiers(all, 1F));
        return new RecipeCraftingContext(recipe, resolved, scale, all, upgradeDurationFactor);
    }

    /** @return the requirements with every active modifier applied, in order */
    private static java.util.List<Requirement> apply(java.util.List<Requirement> requirements,
            java.util.List<hellfirepvp.modularmachinery.common.modifier.RecipeModifier> activeModifiers) {
        if (activeModifiers == null || activeModifiers.isEmpty()) {
            return requirements;
        }
        java.util.List<Requirement> scaled = new java.util.ArrayList<>(requirements.size());
        for (Requirement requirement : requirements) {
            Requirement current = requirement;
            for (hellfirepvp.modularmachinery.common.modifier.RecipeModifier modifier : activeModifiers) {
                current = modifier.apply(current);
            }
            scaled.add(current);
        }
        return scaled;
    }

    private RecipeCraftingContext(MachineRecipe recipe, List<Requirement> requirements) {
        this(recipe, requirements, 1F, List.of());
    }

    private RecipeCraftingContext(MachineRecipe recipe, List<Requirement> requirements, float triggerTimeScale) {
        this(recipe, requirements, triggerTimeScale, List.of());
    }

    private RecipeCraftingContext(MachineRecipe recipe, List<Requirement> requirements, float triggerTimeScale,
                                 List<RecipeModifier> activeModifiers) {
        this(recipe, requirements, triggerTimeScale, activeModifiers, 1F);
    }

    private RecipeCraftingContext(MachineRecipe recipe, List<Requirement> requirements, float triggerTimeScale,
                                 List<RecipeModifier> activeModifiers, float upgradeDurationFactor) {
        this.recipe = recipe;
        this.requirements = List.copyOf(requirements);
        this.triggerTimeScale = triggerTimeScale;
        this.activeModifiers = activeModifiers == null ? List.of() : List.copyOf(activeModifiers);
        this.upgradeDurationFactor = upgradeDurationFactor;
        java.util.LinkedHashSet<Integer> catalysts = new java.util.LinkedHashSet<>();
        int index = 0;
        for (Requirement requirement : this.requirements) {
            if (requirement instanceof RequirementCatalyst catalyst && catalyst.isActive()) catalysts.add(index);
            if (requirement instanceof RequirementCatalyst) index++;
        }
        this.activeCatalysts = java.util.Set.copyOf(catalysts);
    }

    public MachineRecipe getRecipe() {
        return recipe;
    }

    /** Effective recipe duration after duration-target modifiers active for this run. */
    public int effectiveDuration() {
        int base = RecipeModifier.effectiveDuration(activeModifiers, recipe.recipeTime());
        return upgradeDurationFactor == 1F ? base : Math.max(1, Math.round(base * upgradeDurationFactor));
    }

    public java.util.Set<Integer> activeCatalystIndices() { return activeCatalysts; }

    /** @return this run's requirements, possibly modified; immutable */
    public List<Requirement> getRequirements() {
        return requirements;
    }

    /** Effective trigger tick after upstream's rounded duration-modifier factor. */
    public int triggerTime(Requirement requirement) {
        if (requirement == null || requirement.getTriggerTime() <= 0) return requirement == null ? 0 : requirement.getTriggerTime();
        return Math.round(requirement.getTriggerTime() * triggerTimeScale);
    }

    /** @return this run's requirements with the given direction, in declaration order */
    public List<Requirement> requirementsOf(IOType ioType) {
        return requirements.stream().filter(r -> r.getIOType() == ioType).toList();
    }

    /**
     * Whether every requirement is satisfied well enough to BEGIN.
     *
     * <p>This is the loop {@code MachineTicker} used to run inline, moved here so that a thread can own it.
     * The semantics are unchanged and deliberately so: an input must be fully present, an output must have
     * room, and nothing is taken until both this and {@link #canFinish} agree.
     */
    public boolean canStart(MachineContext context) {
        if (selectedIsolationGroupKey != null) {
            context.selectIsolationGroup(selectedIsolationGroupKey);
            return canStartInSelectedGroup(context);
        }
        java.util.List<MachineContext.IsolationGroup> groups = context.isolationGroups();
        if (!groups.isEmpty()) {
            for (MachineContext.IsolationGroup group : groups) {
                context.selectIsolationGroup(group.key());
                if (canStartInSelectedGroup(context)) {
                    selectedIsolationGroupKey = group.key();
                    return true;
                }
            }
            context.selectIsolationGroup(null);
            return false;
        }
        context.selectIsolationGroup(null);
        return canStartInSelectedGroup(context);
    }

    private boolean canStartInSelectedGroup(MachineContext context) {
        for (Requirement requirement : requirements) {
            if (requirement.getTriggerTime() > 0) continue;
            if (requirement.getIOType() == hellfirepvp.modularmachinery.common.machine.IOType.OUTPUT
                    && requirement.isIgnoreOutputCheck()) continue;
            if (!requirement.canStart(context)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether this run could still COMPLETE if started now.
     *
     * <p>Separate from {@link #canStart} because the two genuinely differ: a machine may be able to start but
     * have nowhere to put the result, and discovering that at completion time would mean having already
     * consumed the inputs. {@code MachineTicker}'s javadoc records the same reasoning, and it is the reason
     * both are required before anything is taken.
     */
    public boolean canFinish(MachineContext context) {
        if (selectedIsolationGroupKey != null) {
            context.selectIsolationGroup(selectedIsolationGroupKey);
            return canFinishInSelectedGroup(context);
        }
        java.util.List<MachineContext.IsolationGroup> groups = context.isolationGroups();
        if (!groups.isEmpty()) {
            for (MachineContext.IsolationGroup group : groups) {
                context.selectIsolationGroup(group.key());
                if (canFinishInSelectedGroup(context)) {
                    selectedIsolationGroupKey = group.key();
                    return true;
                }
            }
            context.selectIsolationGroup(null);
            return false;
        }
        context.selectIsolationGroup(null);
        return canFinishInSelectedGroup(context);
    }

    private boolean canFinishInSelectedGroup(MachineContext context) {
        for (Requirement requirement : requirements) {
            if (requirement.getTriggerTime() > 0) continue;
            if (requirement.getIOType() == hellfirepvp.modularmachinery.common.machine.IOType.OUTPUT
                    && requirement.isIgnoreOutputCheck()) continue;
            if (!requirement.canStart(context) || !requirement.canFinish(context)) {
                return false;
            }
        }
        return true;
    }

    









 
    public String describeBlockers(MachineContext context) {
        if (selectedIsolationGroupKey != null) context.selectIsolationGroup(selectedIsolationGroupKey);
        StringBuilder blocked = new StringBuilder();
        for (Requirement requirement : requirements) {
            if (requirement.getIOType() == hellfirepvp.modularmachinery.common.machine.IOType.OUTPUT
                    && requirement.isIgnoreOutputCheck()) continue;
            if (!requirement.canStart(context) || !requirement.canFinish(context)) {
                if (blocked.length() > 0) {
                    blocked.append(", ");
                }
                String reason = requirement.describeFailure(context);
                blocked.append(reason == null || reason.isEmpty() ? requirement.describe() : reason);
            }
        }
        return blocked.length() == 0 ? Component.translatable("gui.controller.status.crafting").getString()
                : blocked.toString();
    }

    /** @return the group selected by the last successful start/finish check, if any */
    public String getSelectedIsolationGroupKey() {
        return selectedIsolationGroupKey;
    }

    /** Restores a persisted factory/controller group before this run is evaluated. */
    public RecipeCraftingContext setSelectedIsolationGroupKey(String key) {
        this.selectedIsolationGroupKey = key;
        return this;
    }
}
