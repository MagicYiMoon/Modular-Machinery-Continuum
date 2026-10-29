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

import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * One generator of machine recipes from a foreign recipe set.
 *
 * <p><b>Upstream shape, read from the bodies rather than the signatures.</b> Upstream's
 * {@code RecipeAdapter} is an {@code IForgeRegistryEntry<RecipeAdapter>} with
 * {@code createRecipesFor(ResourceLocation owningMachineName, List<RecipeModifier>, List<ComponentRequirement>,
 * Map<Class<?>, List<IEventHandler<RecipeEvent>>>, List<String>)} and a protected {@code incId} counter that
 * {@code AdapterMinecraftFurnace} (U90) and {@code DynamicMachineRecipeAdapter} (U60) both increment once per
 * call, to keep a generator's recipe names unique across reloads. {@code AdapterMinecraftFurnace} names each
 * recipe {@code minecraft:smelting_<incId>_<input>_<output>} (U64) and every recipe it produces carries three
 * requirements: the input item, the output item, and 20 FE/tick (U73-U86).</p>
 *
 * <h2>Three deviations, and each one's reason</h2>
 *
 * <p><b>1. {@code IForgeRegistryEntry} is gone.</b> 1.20.1 removed it, and with it Forge-assigned registry
 * names -- the same era fact {@code RequirementTypeRegistry} records for requirement types. The name is a
 * constructor argument here, which is what {@code RequirementTypeRegistry} does with its map key. The
 * registry that replaces upstream's {@code RegistriesMM.ADAPTER_REGISTRY} is
 * {@link RecipeAdapterRegistry}.</p>
 *
 * <p><b>2. Script metadata is outside this generator signature.</b> The current port has a
 * CraftTweaker builder and per-recipe event callbacks, held in {@code ScriptRecipeRegistry}; they are
 * not parameters of this Java adapter interface. Template additional requirements are appended to
 * each generated recipe as configured. Source-derived amounts and time are scaled once by the adapter.
 * This does not imply that every upstream adapter-specific event is already implemented.</p>
 *
 * <p><b>3. {@code owningMachineName} is the machine's {@code registryName} STRING, not a
 * {@code ResourceLocation}.</b> This port keys its machines by the bare name a controller stores
 * ({@code MachineLoader.findByName}, {@code MachineRecipe.machine()}); an adapter handed a
 * {@code ResourceLocation} would have to unqualify it again to produce a recipe the ticker can find.</p>
 */
public abstract class RecipeAdapter {

    private final ResourceLocation registryName;

    /**
     * Upstream's {@code protected int incId = 0} (U35), incremented once per {@link #createRecipesFor} call.
     *
     * <p>Legacy adapters use this to distinguish template invocations. The loader clears its recipe map
     * on reload, so this counter does not provide stable reload identities. Existing legacy naming is
     * retained; new foreign adapters use the explicit template scope overload instead.</p>
     */
    protected int incId = 0;

    protected RecipeAdapter(ResourceLocation registryName) {
        this.registryName = registryName;
    }

    /** @return the name a recipe's {@code "adapter"} key names, e.g. {@code minecraft:furnace} */
    public final ResourceLocation getRegistryName() {
        return registryName;
    }

    /**
     * Generates the recipes this adapter contributes to one machine.
     *
     * @param owningMachine            the {@code registryName} of the machine the recipes belong to
     * @param modifiers                the {@code modifiers} array of the template that named this adapter
     * @param additionalRequirements   the template's own {@code requirements}, already parsed; applied to
     *                                 every generated recipe and to nothing else
     * @return the generated recipes; never null, and empty when the adapter found nothing to convert
     */
    public abstract Collection<MachineRecipe> createRecipesFor(String owningMachine,
                                                              List<RecipeModifier> modifiers,
                                                              List<Requirement> additionalRequirements);

    /** Stable template identity supplied by the loader; legacy adapters retain their existing naming. */
    public Collection<MachineRecipe> createRecipesFor(String templateScope, String owningMachine,
                                                     List<RecipeModifier> modifiers,
                                                     List<Requirement> additionalRequirements) {
        return createRecipesFor(owningMachine, modifiers, additionalRequirements);
    }

    /**
     * An empty requirement list for {@link #createRecipeShell} to add to.
     *
     * <p><b>It exists because {@code MachineRecipe}'s constructor calls {@code List.copyOf}, which makes an
     * immutable copy of the VIEW but not of the underlying list</b> -- so a caller that passes
     * {@code recipe.requirements()} and then adds to it throws
     * {@code UnsupportedOperationException} on {@code List.copyOf}'s result. Building the list here and
     * passing it once is the only order that works.</p>
     */
    protected static List<Requirement> newRequirementList() {
        return new ArrayList<>();
    }

    /**
     * The recipe shell every adapter builds on -- upstream {@code RecipeAdapter.createRecipeShell} (U72-U75).
     *
     * <p>Upstream's {@code recipeFilePath} for a generated recipe is
     * {@code "internal/adapter/" + registryName.getNamespace() + "/" + registryName.getPath()}. The port
     * keeps that exact spelling: {@code RecipeLoader} reports a collision with the two offending files, and
     * "internal/adapter/minecraft/furnace" is the only thing that tells a reader the collision is between two
     * generated recipes rather than two files a pack author wrote.</p>
     *
     * <p><b>THE REQUIREMENTS ARRIVE ALREADY SCALED, AND UPSTREAM'S SIGNATURE IS WHY.</b> Upstream's
     * {@code createRecipeShell} takes no modifier list -- it takes {@code tickTime} and returns a recipe to
     * which the adapter then adds requirements ONE AT A TIME, each computed from a scaled value
     * ({@code AdapterMinecraftFurnace} U60-U86: {@code Math.round(applyModifiers(modifiers,
     * REQUIREMENT_DURATION, ...))}, then the same shape per item and per energy, each gated on the result
     * being positive). <b>An earlier version of this method took the modifier list and re-applied it to
     * everything -- which double-applied the duration and energy modifiers, because the adapter had already
     * scaled those into the values it passed in.</b> The shipped template hides that (it has no energy
     * modifier), and a pack that added one would have got 20 × mod twice over. The signature now matches
     * upstream's shape: one scale site per value.</p>
     *
     * <p><b>The requirements list must be one nothing else holds.</b> {@code MachineRecipe}'s compact
     * constructor calls {@code List.copyOf}, so a caller that passes {@code recipe.requirements()} and then
     * adds to it throws {@code UnsupportedOperationException} on the copy's result. Build the list with
     * {@link #newRequirementList} and pass it once.</p>
     */
    protected MachineRecipe createRecipeShell(String recipeName, String owningMachine, int tickTime,
                                             List<Requirement> requirements) {
        return new MachineRecipe(owningMachine, recipeName, tickTime, requirements, null, false, -1, "");
    }

    /**
     * The value an adapter passes to a requirement, after the template's modifiers for {@code target}.
     *
     * <p>Upstream computes a generated value inline at each site: {@code Math.round(applyModifiers(modifiers,
     * RequirementTypesMM.REQUIREMENT_ITEM, IOType.INPUT, input.getCount(), false))}
     * ({@code AdapterMinecraftFurnace} U73-U80) and the same for energy (U83-U86) and duration (U60). This is
     * that same computation, in one place, because three copies of "apply the matching modifiers, in order,
     * then round" is three chances for them to disagree about rounding.</p>
     *
     * <p>Upstream adds a requirement only when the scaled value is POSITIVE
     * ({@code if (inAmount > 0)}, {@code if (inEnergy > 0)}), so a pack can scale a requirement away with a
     * zero multiplier instead of leaving an unsatisfiable one behind. That is the caller's check to make,
     * because only the caller knows which requirement the value belongs to.</p>
     *
     * <h3>The three deviations, none of which is about the arithmetic</h3>
     *
     * <ol>
     *   <li><b>What the JSON calls the target.</b> Upstream passes a {@code RequirementType} object resolved
     *       from its Forge registry ({@code RequirementTypesMM.REQUIREMENT_ITEM}); this port's
     *       {@code RecipeModifier} carries the type ID as a string ({@link RecipeModifier#getTarget()}), so
     *       the target arrives as {@code "modularmachinery:item"} instead. Same key, resolved one lookup
     *       earlier.</li>
     *   <li><b>What {@code io} means.</b> Upstream passes the {@code IOType} being computed
     *       ({@code IOType.INPUT} for the input amount) and {@code applyModifiers} skips a modifier whose own
     *       {@code io} disagrees. This port has no {@code applyModifiers} to pass it to, so the direction is
     *       the caller's filter -- passing {@code null} means "every modifier naming this target", which is
     *       what the duration site wants (upstream passes {@code null} there for exactly that reason).</li>
     *   <li><b>{@code chance}.</b> Upstream's last {@code false} argument is {@code affectChance}: it decides
     *       whether the modifier targets a probability rather than a magnitude. A generated requirement's
     *       magnitude is never a probability, so this port takes the same {@code false} and applies it
     *       unconditionally -- a modifier declaring {@code affectChance: true} still reaches a generated
     *       requirement's magnitude here, which is the one place this method is LESS strict than upstream.
     *       Recorded rather than papered over: no shipped template sets the flag, and honouring it needs the
     *       requirement instance, which does not exist yet at this point.</li>
     * </ol>
     *
     * @param base      upstream's own base value for this site: the adapter's default
     * @param modifiers the template's modifiers; every one naming {@code target} applies, in order
     * @param target    the requirement type the JSON names, e.g. {@code modularmachinery:item}
     * @param io        the direction being computed, or {@code null} for "both"
     * @return the scaled value rounded to the nearest integer, floored at zero, or {@code null} when the
     *         result is not finite
     */
    @Nullable
    public static Integer scaledValue(int base, List<RecipeModifier> modifiers, String target,
                                      @Nullable IOType io) {
        float value = base;
        for (RecipeModifier modifier : modifiers) {
            if (modifier == null || !target.equals(modifier.getTarget())) {
                continue;
            }
            if (io != null && modifier.getIOTarget() != null && modifier.getIOTarget() != io) {
                continue;
            }
            value = Requirement.apply(value, modifier.getModifier(), modifier.getOperation());
        }
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return null;
        }
        return Math.max(0, Math.round(value));
    }

    


















 
    @Nullable
    protected static Integer durationWithModifiers(int base, List<RecipeModifier> modifiers) {
        Integer ticks = scaledValue(base, modifiers, DURATION_TARGET, null);
        return ticks == null ? null : Math.max(1, ticks);
    }

    /**
     * The FE/tick a generated recipe costs after the template's energy modifiers.
     *
     * <p>Upstream scales this value and then constructs the requirement with the result
     * ({@code AdapterMinecraftFurnace} U83-U86), so the energy requirement is NEVER handed a modifier list --
     * which is why this is computed here.</p>
     *
     * @param base      the adapter's own figure, 20 for the furnace
     * @param modifiers the template's modifiers
     * @return zero or more FE/tick; zero means "no energy requirement at all", which is upstream's own
     *         behaviour when the scaled amount is not positive
     */
    @Nullable
    protected static Integer energyWithModifiers(int base, List<RecipeModifier> modifiers) {
        return scaledValue(base, modifiers, ENERGY_TARGET, IOType.INPUT);
    }

    /** {@code modularmachinery:duration} -- upstream's {@code RequirementTypesMM.REQUIREMENT_DURATION}. */
    private static final String DURATION_TARGET = "modularmachinery:duration";

    /** {@code modularmachinery:energy} -- upstream's {@code RequirementTypesMM.REQUIREMENT_ENERGY}. */
    private static final String ENERGY_TARGET = "modularmachinery:energy";

    /** {@code modularmachinery:item} -- upstream's {@code RequirementTypesMM.REQUIREMENT_ITEM}. */
    public static final String ITEM_TARGET = "modularmachinery:item";

    /** Upstream's {@code RecipeAdapter.resetIncId} (U77-U79). Present so a future script layer has it. */
    public void resetIncId() {
        incId = 0;
    }
}
