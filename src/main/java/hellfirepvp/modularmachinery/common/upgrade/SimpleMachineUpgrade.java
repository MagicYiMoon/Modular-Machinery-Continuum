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
 * original named above. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.upgrade;

import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.world.item.ItemStack;

/**
 * A declarative upgrade: a fixed list of requirement targets, a fixed effect, and an optional duration factor.
 *
 * <h2>Rule A0 — what this is, and what it replaces</h2>
 *
 * <p>Upstream's counterpart is {@code github.kasuminova.mmce.common.upgrade.SimpleMachineUpgrade} (90 lines), whose
 * payload is a {@code List<String> descriptions}, a {@code IFunction<SimpleMachineUpgrade, String[]>
 * busGuiDescriptionHandler} and a {@code NBTTagCompound customData}; its <b>behaviour</b> lives entirely in
 * CraftTweaker closures attached to event classes by {@code MachineUpgradeBuilder} (333 lines of
 * {@code addRecipeCheckHandler}, {@code addRecipeTickHandler}, …). This port has no script engine, so what upstream
 * expressed as closures is expressed here as DATA: for each requirement type the upgrade names, an operation and a
 * multiplier — which is precisely upstream's own {@code MachineUpgradeBuilder.addModifier(boolean stackAble, String
 * modifierKey, RecipeModifier modifier)} (U139-U159) reduced to the part that can be written in Java.
 *
 * <p><b>THIS IS THE CLASS A PLUGIN AUTHOR USES</b>, because it needs no lambda and no CraftTweaker:
 *
 * <pre>
 *   SimpleMachineUpgrade speed = SimpleMachineUpgrade.of(SpeedUpgrade.TYPE)
 *           .scale(RequirementItem.TYPE_ID, IOType.INPUT, RecipeModifier.OPERATION_MULTIPLY, 2.0F)
 *           .stackable()          // the effect multiplies by stackSize
 *           .duration(0.5F)       // and the craft takes half as long
 *           .describe("Speed: 2x input cost, half the time");
 * </pre>
 *
 * <p><b>{@code stackAble} is upstream's flag, kept with upstream's exact semantics</b> (U148-U156):
 * <ul>
 *   <li>{@code false} — the modifier is applied at its declared value however many items are stacked;</li>
 *   <li>{@code true} — the modifier is raised to the power of {@code stackSize}, which is what upstream's
 *       {@code for (int i = 0; i &lt; stackSize; i++) multiply = multiply.multiply(modifier.getModifier())} computes.
 *       Two speed upgrades are then 4x input cost, not 2x.</li>
 * </ul>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.SimpleMachineUpgrade")
public class SimpleMachineUpgrade extends MachineUpgrade {

    private final List<String> descriptions = new ArrayList<>();
    private final List<String> busGuiDescriptions = new ArrayList<>();
    /** Upstream's optional dynamic bus detail callback. */
    private Function<SimpleMachineUpgrade, String[]> busGuiDescriptionHandler;
    private final List<Effect> effects = new ArrayList<>();
    /** Effects declared through the upstream builder's modifierKey API. */
    private final Map<String, Effect> keyedEffects = new LinkedHashMap<>();
    private float durationMultiplier = 1.0F;

    /**
     * One named effect: "apply {@code value} with {@code operation} to every requirement of type
     * {@code requirementTypeId} in direction {@code io}".
     *
     * @param requirementTypeId which requirements this acts on, as {@link Requirement#typeId()} spells it
     * @param io                the direction it acts on, or {@code null} to act on both
     * @param operation         {@link RecipeModifier#OPERATION_ADD} or {@link RecipeModifier#OPERATION_MULTIPLY}
     * @param value             the addend or factor
     * @param chance            whether it targets a probability rather than a magnitude (upstream's {@code
     *                          affectChance})
     * @param stackAble         whether {@code value} is raised to {@code stackSize}, per upstream U148-U156
     */
    private record Effect(String requirementTypeId, IOType io, int operation, float value, boolean chance,
                          boolean stackAble) {

        /** @return this effect's multiplier for a given stack size, i.e. upstream's repeated multiply */
        float valueFor(int stackSize) {
            if (!stackAble || stackSize <= 1) {
                return value;
            }
            float raised = value;
            for (int i = 1; i < stackSize; i++) {
                raised *= value;
            }
            return raised;
        }
    }

    public SimpleMachineUpgrade(final UpgradeType type) {
        super(type);
    }

    /** @return a new upgrade of the given type, for a declarative registration */
    public static SimpleMachineUpgrade of(UpgradeType type) {
        return new SimpleMachineUpgrade(type);
    }

    /**
     * Adds one effect, at its declared value however many are stacked.
     *
     * @param requirementTypeId {@code RequirementItem.TYPE_ID}, {@code RequirementFluid.TYPE_ID},
     *                          {@code RequirementEnergy.TYPE_ID} or {@code RequirementInterfaceNumInput.TYPE_ID}
     */
    public SimpleMachineUpgrade scale(String requirementTypeId, IOType io, int operation, float value) {
        return scale(requirementTypeId, io, operation, value, false, false);
    }

    /**
     * Adds one effect.
     *
     * @param stackAble whether the multiplier is raised to {@code stackSize}; see the class javadoc
     */
    public SimpleMachineUpgrade scale(String requirementTypeId, IOType io, int operation, float value,
                                      boolean chance, boolean stackAble) {
        Objects.requireNonNull(requirementTypeId, "an effect must name a requirement type");
        effects.add(new Effect(requirementTypeId, io, operation, value, chance, stackAble));
        return this;
    }

    /** Adds an upstream {@code addModifier} effect with its controller-wide de-duplication key. */
    public SimpleMachineUpgrade scale(String modifierKey, String requirementTypeId, IOType io, int operation,
                                      float value, boolean chance, boolean stackAble) {
        if (modifierKey == null || modifierKey.isBlank()) {
            throw new IllegalArgumentException("modifier key cannot be blank");
        }
        Objects.requireNonNull(requirementTypeId, "an effect must name a requirement type");
        keyedEffects.putIfAbsent(modifierKey,
                new Effect(requirementTypeId, io, operation, value, chance, stackAble));
        return this;
    }

    /** Unkeyed effects are applied directly; keyed effects are resolved by MachineTicker's key set. */
    public List<RecipeModifier> unkeyedRequirementsModifiers() {
        return effectModifiers(effects);
    }

    /** Returns keyed effects in declaration order, with stack scaling already applied. */
    public Map<String, RecipeModifier> keyedRequirementsModifiers() {
        Map<String, RecipeModifier> result = new LinkedHashMap<>();
        keyedEffects.forEach((key, effect) -> result.put(key, effectModifier(effect)));
        return Collections.unmodifiableMap(result);
    }

    /**
     * Scales every INPUT requirement this upgrade names, by {@code factor}, multiplicatively.
     *
     * <p>The convenience for the common case: an upgrade that multiplies what a craft consumes. Direction matters
     * and is not cosmetic — a factor applied to outputs as well would make a speed upgrade also increase yield,
     * which is a different upgrade.
     */
    public SimpleMachineUpgrade scaleInput(String requirementTypeId, float factor, boolean stackAble) {
        return scale(requirementTypeId, IOType.INPUT, RecipeModifier.OPERATION_MULTIPLY, factor, false, stackAble);
    }

    /** Scales every OUTPUT requirement this upgrade names. See {@link #scaleInput}. */
    public SimpleMachineUpgrade scaleOutput(String requirementTypeId, float factor, boolean stackAble) {
        return scale(requirementTypeId, IOType.OUTPUT, RecipeModifier.OPERATION_MULTIPLY, factor, false, stackAble);
    }

    /** @param factor the duration multiplier this upgrade contributes; see {@link MachineUpgrade#durationMultiplier()} */
    public SimpleMachineUpgrade duration(float factor) {
        this.durationMultiplier = factor;
        return this;
    }

    /** Adds a tooltip line. Upstream U38-U40. */
    public SimpleMachineUpgrade describe(String description) {
        descriptions.add(description);
        return this;
    }

    /** Source-compatible name used by {@code SimpleMachineUpgrade} scripts. */
    @org.openzen.zencode.java.ZenCodeType.Method
    public void addDescription(String description) {
        describe(description);
    }

    /** Adds a fixed line for a bus detail view, matching the source builder's bus GUI description channel. */
    public SimpleMachineUpgrade describeBus(String description) {
        if (description != null) busGuiDescriptions.add(description);
        return this;
    }

    /** Sets the source-compatible dynamic description shown beside this upgrade in the bus screen. */
    public SimpleMachineUpgrade setBusGUIDescriptionHandler(
            Function<SimpleMachineUpgrade, String[]> handler) {
        this.busGuiDescriptionHandler = handler;
        return this;
    }

    /** CraftTweaker-facing IData view of the bus-owned custom data. */
    @org.openzen.zencode.java.ZenCodeType.Getter("customData")
    public com.blamejared.crafttweaker.api.data.IData getCustomDataValue() {
        return scriptCustomData();
    }

    /** CraftTweaker-facing setter for the bus-owned custom data. */
    @org.openzen.zencode.java.ZenCodeType.Setter("customData")
    public void setCustomDataValue(com.blamejared.crafttweaker.api.data.IData data) {
        if (data == null || !(data.getInternal() instanceof net.minecraft.nbt.CompoundTag tag)) {
            throw new IllegalArgumentException("customData must be a compound IData value");
        }
        setCustomData(tag);
    }

    @Override
    public List<RecipeModifier> requirementsModifiers() {
        if (effects.isEmpty() && keyedEffects.isEmpty()) {
            return Collections.emptyList();
        }
        List<RecipeModifier> modifiers = new ArrayList<>(effects.size() + keyedEffects.size());
        modifiers.addAll(effectModifiers(effects));
        keyedEffects.values().forEach(effect -> modifiers.add(effectModifier(effect)));
        return modifiers;
    }

    private List<RecipeModifier> effectModifiers(List<Effect> source) {
        List<RecipeModifier> modifiers = new ArrayList<>(source.size());
        for (Effect effect : source) modifiers.add(effectModifier(effect));
        return modifiers;
    }

    private RecipeModifier effectModifier(Effect effect) {
            // THE ARGUMENT ORDER IS RecipeModifier'S, WHICH IS (target, io, VALUE, OPERATION, chance) -- value
            // before operation, the opposite of this record's own field order. Written out because getting it the
            // other way round compiles as far as the float/int mismatch and would, with an int-typed value, have
            // passed the multiplier where the operation goes: an upgrade of "2.0x" silently applying operation 2,
            // which RecipeModifier's own deserialiser REJECTS as out of range.
            return new RecipeModifier(effect.requirementTypeId(), effect.io(),
                    effect.valueFor(getStackSize()), effect.operation(), effect.chance());
    }

    @Override
    public float durationMultiplier() {
        // A STACKABLE duration is intentionally not offered: two "half time" upgrades reaching 0.25x would be
        // reasonable, but a third reaching 0.125x and a tenth reaching 0.001x is a machine that finishes in the
        // same tick it starts, and upstream's own stack rule was written for requirement multipliers (U152-U156).
        // Keeping this a plain factor means a registration cannot accidentally produce a zero-tick recipe.
        return durationMultiplier;
    }

    @Override
    public List<String> getDescriptions() {
        return Collections.unmodifiableList(descriptions);
    }

    @Override
    public List<String> getBusGUIDescriptions() {
        if (busGuiDescriptionHandler == null) {
            return Collections.unmodifiableList(busGuiDescriptions);
        }
        try {
            String[] values = busGuiDescriptionHandler.apply(this);
            if (values == null) return List.of();
            List<String> result = new ArrayList<>(values.length);
            for (String value : values) if (value != null) result.add(value);
            return Collections.unmodifiableList(result);
        } catch (Throwable failure) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.warn(
                    "[MMCE] Machine upgrade bus description handler failed for {}", getType().getName(), failure);
            return List.of();
        }
    }

    @Override
    public MachineUpgrade copy() {
        SimpleMachineUpgrade copy = new SimpleMachineUpgrade(getType());
        copy.descriptions.addAll(descriptions);
        copy.busGuiDescriptions.addAll(busGuiDescriptions);
        copy.busGuiDescriptionHandler = busGuiDescriptionHandler;
        copy.effects.addAll(effects);
        copy.keyedEffects.putAll(keyedEffects);
        copy.durationMultiplier = durationMultiplier;
        copy.stackSize = stackSize;
        copy.customData = customData.copy();
        copyEventHandlersTo(copy);
        return copy;
    }

    @Override
    public SimpleMachineUpgrade copy(ItemStack owner) {
        return (SimpleMachineUpgrade) copy();
    }
}
