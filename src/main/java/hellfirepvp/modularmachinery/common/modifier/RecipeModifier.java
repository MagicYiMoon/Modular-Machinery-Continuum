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
package hellfirepvp.modularmachinery.common.modifier;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;

import java.util.List;
import net.minecraft.nbt.CompoundTag;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import org.openzen.zencode.java.ZenCodeType;

/**
 * One scaling rule a machine modifier applies to a recipe's requirements.
 *
 * <h2>The JSON this decodes, field for field</h2>
 *
 * <pre>
 * "modifier": { "target": "modularmachinery:item", "io": "output", "operation": 1, "multiplier": 2.0 }
 * </pre>
 *
 * <p>Upstream declares exactly these and two more:
 *
 * <pre>
 * public static final String IO_INPUT  = "input";
 * public static final String IO_OUTPUT = "output";
 * public static final int OPERATION_ADD      = 0;
 * public static final int OPERATION_MULTIPLY = 1;
 * protected final RequirementType&lt;?, ?&gt; target;
 * protected final IOType ioTarget;
 * protected final float modifier;
 * protected final int operation;
 * protected final boolean chance;
 * </pre>
 *
 * <p><b>The numbering is kept identical</b> — {@code "operation": 1} is MULTIPLY because that is what upstream's
 * {@code OPERATION_MULTIPLY} declares — so the shipped JSON means what upstream says it means.
 *
 * <h2>The one deliberate difference</h2>
 *
 * <p>Upstream's {@code target} is a {@code RequirementType} looked up in a registry. <b>This port's is the type
 * ID as a string</b>, matched against {@link Requirement#typeId()}, which stage 1 added for precisely this
 * purpose. The registry exists upstream to key the JSON deserialiser; the port resolves requirements another way,
 * and 1.20.1 removes {@code IForgeRegistryEntry} anyway, so a registry here would be structure without a job.
 */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.RecipeModifier")
public final class RecipeModifier {

    /** Upstream's {@code IO_INPUT}. */
    public static final String IO_INPUT = "input";

    /** Upstream's {@code IO_OUTPUT}. */
    public static final String IO_OUTPUT = "output";

    /** Upstream's {@code OPERATION_ADD}. */
    public static final int OPERATION_ADD = 0;

    /** Upstream's {@code OPERATION_MULTIPLY}. */
    public static final int OPERATION_MULTIPLY = 1;

    private final String target;
    private final IOType ioTarget;
    private final float modifier;
    private final int operation;
    private final boolean chance;

    public RecipeModifier(String target, IOType ioTarget, float modifier, int operation, boolean chance) {
        this.target = target;
        this.ioTarget = ioTarget;
        this.modifier = modifier;
        this.operation = operation;
        this.chance = chance;
    }

    /**
     * Reads one entry of a machine's {@code modifiers} array.
     *
     * @param entry the modifier object itself, NOT the surrounding replacement entry
     * @return the parsed modifier, or {@code null} when a field cannot be read
     */
    public static RecipeModifier deserialize(JsonObject entry) {
         
        // RecipeModifier.java:229-266. Each was OPTIONAL in the port with a default:
        //
        //   String ioTarget = part.getAsJsonPrimitive("io").getAsString();   // read unconditionally
        //   if (!part.has("target") || ...) throw ...;
        //   if (!part.has("multiplier") || ...) throw ...;
        //   if (!part.has("operation") || ...) throw ...;
        //   if (operation < 0 || operation > 1) throw ...;
        //   if (part.has("affectChance")) { ... getAsBoolean() ... }
        //
        // The last is the quietest and the worst: a modifier written upstream's way declares
        // "affectChance", the port read "chance", found nothing, and applied the modifier to the AMOUNT
        // instead of the probability -- so "twice as likely" became "twice as much".
        //
        // A null return still means "skip this modifier", which is the caller's existing contract. What
        // changed is that an INCOMPLETE modifier now reaches that path instead of being silently completed
        // with a default that was never in the file.
        JsonElement target = entry.get("target");
        if (target == null || target.isJsonNull()) {
            return null;
        }
        // `io` IS REQUIRED. Upstream reads it unconditionally, so a modifier without one is a broken file
        // rather than one that applies to both directions. The port's "null means both" was its own
        // invention, and it made a typo in the key silently double a modifier's reach.
        JsonElement ioElement = entry.get("io");
        if (ioElement == null || ioElement.isJsonNull()) {
            return null;
        }
        IOType io = null;
        String ioName = ioElement.getAsString();
        if (IO_INPUT.equals(ioName)) {
            io = IOType.INPUT;
        } else if (IO_OUTPUT.equals(ioName)) {
            io = IOType.OUTPUT;
        }
        if (io == null) {
            return null;
        }
        // `multiplier` IS REQUIRED, and the `modifier` alias is gone: upstream has no alias, and a
        // fallback default of 1.0 produced a modifier that scaled nothing while looking like it did.
        JsonElement value = entry.get("multiplier");
        if (value == null || value.isJsonNull()) {
            return null;
        }
        float amount = value.getAsFloat();
        // `operation` IS REQUIRED AND RANGED. Upstream rejects anything outside 0..1; the port defaulted to
        // MULTIPLY and accepted every other value as if it were that too, so "operation": 7 was a
        // multiplication with nothing anywhere to say the number had been ignored.
        JsonElement operation = entry.get("operation");
        if (operation == null || operation.isJsonNull()) {
            return null;
        }
        int op = operation.getAsInt();
        if (op < OPERATION_ADD || op > OPERATION_MULTIPLY) {
            return null;
        }
        // THE KEY IS `affectChance`, which is what upstream reads. `chance` is not a key upstream knows,
        // and reading it instead meant every upstream-style modifier lost this flag silently.
        boolean chanceFlag = false;
        JsonElement affectChance = entry.get("affectChance");
        if (affectChance != null && !affectChance.isJsonNull() && affectChance.isJsonPrimitive()
                && affectChance.getAsJsonPrimitive().isBoolean()) {
            chanceFlag = affectChance.getAsBoolean();
        }
        return new RecipeModifier(target.getAsString(), io, amount, op, chanceFlag);
    }

    public String getTarget() {
        return target;
    }

    public IOType getIOTarget() {
        return ioTarget;
    }

    public float getModifier() {
        return modifier;
    }

    public int getOperation() {
        return operation;
    }

    public boolean isChance() {
        return chance;
    }

    /** Stable NBT form used by controller and factory-thread runtime state. */
    public CompoundTag serialize() {
        CompoundTag tag = new CompoundTag();
        tag.putString("target", target);
        tag.putString("io", ioTarget == IOType.INPUT ? IO_INPUT : IO_OUTPUT);
        tag.putFloat("multiplier", modifier);
        tag.putInt("operation", operation);
        tag.putBoolean("affectChance", chance);
        return tag;
    }

    /** Reads the NBT form written by {@link #serialize()}, returning null for malformed data. */
    public static RecipeModifier deserialize(CompoundTag tag) {
        if (tag == null || !tag.contains("target") || !tag.contains("io")
                || !tag.contains("multiplier") || !tag.contains("operation")) {
            return null;
        }
        IOType io;
        String ioName = tag.getString("io");
        if (IO_INPUT.equals(ioName)) io = IOType.INPUT;
        else if (IO_OUTPUT.equals(ioName)) io = IOType.OUTPUT;
        else return null;
        int operation = tag.getInt("operation");
        if (operation < OPERATION_ADD || operation > OPERATION_MULTIPLY) return null;
        return new RecipeModifier(tag.getString("target"), io, tag.getFloat("multiplier"),
                operation, tag.getBoolean("affectChance"));
    }

    // ================================================================================================
     
    //
    // `"target": "modularmachinery:duration"` NAMES A REQUIREMENT TYPE THAT CANNOT BE INSTANTIATED. Upstream
    // registers it (RegistryRequirementTypes:74 -> `register(new RequirementDuration(), KEY_REQUIREMENT_DURATION)`)
    // and its own source says what it is, twice:
    //
    //   RequirementTypesMM:41     //Helper type as target for duration-type recipe modifiers
    //   RequirementDuration:22    //This is actually only a helper type for recipe modifiers
    //   RequirementDuration:27    throw new UnsupportedOperationException("Cannot instantiate requirement for duration!");
    //
    // So it is NOT a requirement a recipe can declare, and a modifier naming it therefore cannot match a
    // REQUIREMENT -- it matches the RECIPE'S OWN LENGTH. Upstream applies it to the recipe's tick time at the
    // recipe-copy (MachineRecipe:250, `Math.round(applyModifiers(modifiers, REQUIREMENT_DURATION, null,
    // this.tickTime, false))`) and to the running craft's total at ActiveMachineRecipe:80/133/148
    // (`applyModifiers(context, REQUIREMENT_DURATION, null, getRecipeTotalTickTime(), false)`), and derives the
    // per-tick I/O multiplier from it at RecipeCraftingContext:196-203 (`dur / result`).
    //
    // THE PORT HAD NONE OF THE THREE, AND IT WAS SILENT: `appliesTo(Requirement)` could never match the string
    // and nothing reported it, so a pack's duration modifier loaded, parsed, and scaled nothing. It was reachable
    // from shipped content (`assets/modularmachinery/default_recipes/alloy_smelter/
    // alloy_smelter_furnaces.adapter.json:18`) and it is NOT adapter-gated: the two upstream sites above are the
    // general recipe path, and MachineRecipe:250 runs for every machine's copy of every recipe.
    // ================================================================================================

    /**
     * Upstream's {@code RequirementTypesMM.KEY_REQUIREMENT_DURATION} — a modifier target, never a requirement.
     *
     * <p>A modifier naming this target scales the RECIPE'S DURATION, at every io direction: upstream's
     * recipe-level call passes {@code ioType == null} ({@code MachineRecipe:250}, {@code ActiveMachineRecipe:80}),
     * and {@code applyModifiers} skips its io test entirely for a null query ({@code ioType != null &&
     * mod.ioTarget != ioType} at U147). The shipped adapter file's {@code "io": "input"} therefore APPLIES.</p>
     */
    public static final String DURATION_TARGET = "modularmachinery:duration";

    /**
     * The requirement type ids this port actually has, read off the four {@code typeId()} implementations:
     * {@code RequirementItem} F474-F476, {@code RequirementFluid} F557-F559,
     * {@code RequirementEnergy} F186-F188 and {@code RequirementInterfaceNumInput} F238-F240.
     *
     * <p>{@code interface_number_input} IS LISTED EVEN THOUGH IT SCALES NOTHING: upstream's
     * {@code deepCopyModified} (U98-U100) ignores its modifiers and returns an unmodified copy, so it is a KNOWN
     * target with no effect BY UPSTREAM'S OWN DESIGN, which is a different thing from a target nobody recognises.
     * It is named here so the diagnostic below keeps saying which of the two it is looking at.</p>
     */
    public static final List<String> REQUIREMENT_TARGETS = List.of(
            "modularmachinery:item",
            "modularmachinery:fluid",
            "modularmachinery:fluid_pertick",
            "modularmachinery:ingredient_array_input",
            "modularmachinery:energy",
            "modularmachinery:interface_number_input");

    /** @return whether this port has an application path for {@code target} (see the two constants above) */
    public static boolean isKnownTarget(String target) {
        return DURATION_TARGET.equals(target) || REQUIREMENT_TARGETS.contains(target);
    }

    /** @return whether this modifier scales the recipe's DURATION rather than a requirement */
    public boolean targetsDuration() {
        return DURATION_TARGET.equals(target);
    }

    /**
     * The first target in {@code modifiers} that this port cannot apply, or {@code null} when every one of them
     * has an application path.
     *
     * <p><b>THIS IS THE "NO LONGER SILENT" HALF OF THE FIX, AND IT IS A LOAD-TIME DIAGNOSTIC RATHER THAN A TEST
     * ASSERTION:</b> a modifier that matches nothing is the defect, and the smallest honest treatment of an
     * unmatched target is that the load says so. {@code MachineLoader} adds the result to its problems, which the
     * loader already logs as warnings (F234-F236) and exposes through {@code getProblems()} (F173-F175).</p>
     *
     * @return e.g. {@code "modularmachinery:not_a_type"}, or {@code null} when all of them are known
     */
    public static String unmatchedTarget(Iterable<RecipeModifier> modifiers) {
        if (modifiers == null) {
            return null;
        }
        for (RecipeModifier modifier : modifiers) {
            if (modifier != null && !isKnownTarget(modifier.target)) {
                return modifier.target;
            }
        }
        return null;
    }

    /**
     * Upstream's duration arithmetic, transcribed from {@code applyModifiers} (U132-U161) for the one target whose
     * value is not a requirement's.
     *
     * <p><b>RULE A0 — THE THREE DIFFERENCES FROM {@link #apply(Requirement)}, AND NONE OF THEM IS A DEVIATION:</b></p>
     *
     * <ol>
     *   <li><b>upstream's method</b> — {@code RecipeModifier.applyModifiers(modifiers, RequirementType target,
     *       IOType ioType, double value, boolean isChance)} composes EVERY matching modifier in ONE pass:
     *       {@code add += mod.modifier} for OPERATION_ADD, {@code mul *= mod.modifier} for OPERATION_MULTIPLY, and
     *       returns {@code (value + add) * mul}. The port's requirement path cannot do that (it hands one modifier
     *       at a time to {@code Requirement#scaled}, and one requirement may not be scaled twice), but the duration
     *       has no requirement object to hand anything to, so the ONE-PASS form is available here and is used.</li>
     *   <li><b>why the io test is dropped</b> — upstream's two recipe-level call sites pass {@code ioType = null}
     *       ({@code MachineRecipe:250}, {@code ActiveMachineRecipe:80/133/148}), which makes U147's
     *       {@code ioType != null && mod.ioTarget != ioType} skip the test for every modifier. The port does the
     *       same by not testing io at all: the shipped file writes {@code "io": "input"} and upstream applies it.</li>
     *   <li><b>why {@code affectChance} is checked</b> — upstream passes {@code isChance = false} at all four
     *       sites, so U150 drops any modifier whose {@code affectChance} is true. A "make this twice as likely"
     *       modifier is not a "make this twice as long" modifier and the port keeps that distinction.</li>
     * </ol>
     *
     * @param baseTime the recipe's own duration in ticks, as the recipe file declares it
     * @return the scaled duration, unrounded and unclamped (see {@link #effectiveDuration})
     */
    public static float applyDurationModifiers(Iterable<RecipeModifier> modifiers, float baseTime) {
        if (modifiers == null) {
            return baseTime;
        }
        float add = 0F;
        float mul = 1F;
        for (RecipeModifier modifier : modifiers) {
            if (modifier == null || !modifier.targetsDuration() || modifier.chance) {
                continue;
            }
            switch (modifier.operation) {
                case OPERATION_ADD -> add += modifier.modifier;
                case OPERATION_MULTIPLY -> mul *= modifier.modifier;
                default -> {
                    // U157 throws for an unknown operation; this port's reader rejects the value at parse time
                    // (deserialize F146-F148), so an unreadable operation cannot reach here.
                }
            }
        }
        return (baseTime + add) * mul;
    }

    













 
    public static int effectiveDuration(Iterable<RecipeModifier> modifiers, int baseTime) {
        return (int) Math.max(1, Math.round(applyDurationModifiers(modifiers, baseTime)));
    }

    /**
     * Whether this modifier acts on the given requirement.
     *
     * <p>Two conditions, both from upstream: the requirement's TYPE must be the modifier's target, and — when the
     * modifier names a direction — the requirement must have that direction. <b>A modifier with no {@code io} in
     * the JSON applies to both directions</b>, which is why {@code ioTarget} may be null.
     *
     * <p><b>THE DURATION TARGET IS EXCLUDED EXPLICITLY, AND THAT IS NOT A SPECIAL CASE INVENTED HERE.</b> It
     * cannot match a requirement in upstream either: {@code RequirementDuration.createRequirement} THROWS
     * ("Cannot instantiate requirement for duration!", U27), so no requirement instance ever carries that
     * {@code requirementType} and upstream's {@code mod.target.equals(target)} (U144) is never satisfied by one.
     * Before this line the port returned false for it by a STRING MISMATCH that nothing reported — a modifier that
     * matched nothing, silently. It is now an explicit statement with a consumer: {@link #applyDurationModifiers}
     * is the path that acts on it, and {@link #unmatchedTarget} is the path that reports anything still unmatched.
     */
    public boolean appliesTo(Requirement requirement) {
        if (targetsDuration()) {
            // NOT a requirement modifier: `modularmachinery:duration` scales the recipe's LENGTH, and that is
            // applied at the recipe start site (MachineTicker.effectiveRecipeDuration), not to a requirement.
            return false;
        }
        if (!target.equals(requirement.typeId()) && !(target.equals("modularmachinery:fluid")
                && requirement instanceof hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluidPerTick)
                && !(target.equals("modularmachinery:item") && requirement instanceof
                hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray)) {
            return false;
        }
        return ioTarget == null || ioTarget == requirement.getIOType();
    }

    /**
     * This requirement with the modifier applied, or the requirement unchanged when it does not apply.
     *
     * <p>Delegates to {@link Requirement#scaled(float, int, IOType, boolean)} — stage 1's contract, added so that
     * this class has something to call rather than needing to know each requirement's shape.
     */
    public Requirement apply(Requirement requirement) {
        return appliesTo(requirement) ? requirement.scaled(modifier, operation, ioTarget, chance) : requirement;
    }
}
