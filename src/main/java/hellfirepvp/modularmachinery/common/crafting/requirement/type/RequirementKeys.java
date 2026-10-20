/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.JsonObject;

/**
 * Reads the keys shared by more than one requirement type.
 *
 * <h2>Why these moved out of {@code RecipeLoader}</h2>
 *
 * <p>They were private helpers of the loader, called from inside its three-case {@code switch}. PLAN section 377 recorded
 * why moving each branch into a {@link RequirementType} implementation could not leave them behind: an implementation
 * outside {@code RecipeLoader} cannot reach a private method, and the branch's specific message -- which key was required
 * and missing -- is exactly what §313 spent a round establishing the value of.
 *
 * <p><b>Each method keeps the reasoning comment it had</b>, because the reasoning is about the RULE and not about where it
 * is called from. A helper that arrives here stripped to its code would be a net loss even though it compiles.
 */
public final class RequirementKeys {

    private RequirementKeys() {
    }

    /**
     * An item requirement's amount.
     *
     * <p>Defaults to 1 and is clamped to 1..64. Unlike the fluid amount below, a missing value is NOT an error here --
     * upstream reads it with the same default, and an item requirement of one item is the case a pack author means when
     * they write nothing.
     */
    public static int clampedItemAmount(JsonObject requirement) {
        if (!requirement.has("amount") || !requirement.get("amount").isJsonPrimitive()) {
            return 1;
        }
        return Math.max(1, Math.min(64, requirement.get("amount").getAsInt()));
    }

    /**
     * A chance, applying upstream's rule: OUT OF RANGE IS IGNORED, not clamped.
     *
     * <p>Upstream applies the value only when it is between 0 and 1 and otherwise leaves the requirement at its default,
     * which is 1.0 -- always. The port CLAMPED, so {@code "chance": -1} meant NEVER here and ALWAYS in the mod: the same
     * inversion-instead-of-refusal that io-type had (section 311).
     */
    public static float chanceOf(JsonObject requirement) {
        if (!requirement.has("chance") || !requirement.get("chance").isJsonPrimitive()) {
            return 1.0F;
        }
        float chance = requirement.get("chance").getAsFloat();
        return (chance >= 0F && chance <= 1F) ? chance : 1.0F;
    }

    /**
     * A fluid requirement's amount, per upstream RequirementTypeFluid:38-48.
     *
     * <p>REQUIRED upstream -- a fluid requirement with no amount is a rejected file. The port defaulted it to 1000 mB,
     * which is a plausible-looking number that the author never wrote. A present value is floored at 0, which is
     * upstream's only modification of it.
     *
     * @param path the recipe's path, so the thrown message names the file as every other problem line does
     * @throws RequirementParseException when the key is absent or not a primitive
     */
    public static int requiredFluidAmount(String path, JsonObject requirement) {
        if (!requirement.has("amount") || !requirement.get("amount").isJsonPrimitive()) {
            throw new RequirementParseException(path + ": fluid requirement has no amount");
        }
        return Math.max(0, requirement.get("amount").getAsInt());
    }

    /**
     * A key that must be present and primitive, read as an int.
     *
     * @throws RequirementParseException naming the key and the file
     */
    public static int requiredInt(String path, JsonObject requirement, String key, String typeName) {
        if (!requirement.has(key) || !requirement.get(key).isJsonPrimitive()) {
            throw new RequirementParseException(path + ": " + typeName + " requirement has no " + key);
        }
        return requirement.get(key).getAsInt();
    }

    /**
     * A key that must be present and primitive, read as a string.
     *
     * @throws RequirementParseException naming the key and the file
     */
    public static String requiredString(String path, JsonObject requirement, String key, String typeName) {
        if (!requirement.has(key) || !requirement.get(key).isJsonPrimitive()) {
            throw new RequirementParseException(path + ": " + typeName + " requirement has no " + key);
        }
        return requirement.get(key).getAsString();
    }
}
