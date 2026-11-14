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
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.IOType;

/**
 * {@code modularmachinery:item} -- an item cost or product.
 *
 * <p>This is the body of one branch of {@code RecipeLoader}'s former {@code switch}, moved here by PLAN section 380 with
 * its reasoning intact:
 *
 * <pre>
 * case TYPE_ITEM -&gt; {
 *     String raw = string(requirement, "item", null);
 *     if (raw == null || raw.isEmpty()) {
 *         problems.add(path + ": item requirement has no 'item' key");
 *         yield null;
 *     }
 *     yield new RequirementItem(ioType, raw, clampedItemAmount(requirement), chanceOf(requirement));
 * }
 * </pre>
 *
 * <h2>Two things here are decisions, and both are easy to undo by accident</h2>
 *
 * <p><b>1. The raw string is passed through UNPARSED.</b> A legacy id such as {@code ore:ingotIron} (an ore-dictionary
 * name) or {@code minecraft:dye@3} (item plus metadata) is NOT a valid resource location. Rejecting it at this point
 * would discard the shims' chance to resolve it, and {@link RequirementItem} owns resolution and reports what it could
 * not map. <b>A "tidy-up" that parsed the id here would look like validation and would break every legacy recipe.</b>
 *
 * <p><b>2. {@code chance} IS read, and it was not always.</b> The port did not read it at all, so a recipe declaring
 * {@code "chance": 0.6} -- as {@code centrifuge_wash_redstone} does -- always produced its output. Silently giving more
 * than the pack asked for is the worst kind of divergence: <b>nothing fails and nothing warns.</b> That is why the chance
 * travels into the constructor here even though the requirement would still be valid without it.
 */
public final class ItemRequirementType implements RequirementType {

    public static final String NAME = "item";

    @Override
    public Requirement createRequirement(IOType type, JsonObject json) {
        return create(type, json, "");
    }

    /**
     * The loader's path is needed for the message; the interface does not carry one.
     *
     * @param path the recipe's path, prefixed onto the thrown message
     */
    public Requirement create(IOType type, JsonObject json, String path) {
        String raw = RequirementKeys.requiredString(path, json, "item", NAME);
        if (raw.isEmpty()) {
            // An EMPTY name is missing, not an item named "". The loader's original branch tested isEmpty() as well as
            // null, and dropping that test turns a rejected recipe into one that silently matches nothing.
            throw new RequirementParseException(path + ": item requirement has no 'item' key");
        }
        if (raw.equalsIgnoreCase("any:fuel")) return hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFuel.parse(type, json, false);
        RequirementItem requirement = new RequirementItem(type, raw, RequirementKeys.clampedItemAmount(json),
                RequirementKeys.chanceOf(json));

        requirement.withNbt(hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.read(json));

        if (json.has("consumeDurability")) {
            var value = json.get("consumeDurability");
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                throw new RequirementParseException("consumeDurability must be a nonnegative integer");
            }
            try {
                int damage = value.getAsBigDecimal().intValueExact();
                requirement.withConsumeDurability(damage);
            } catch (ArithmeticException | IllegalArgumentException invalid) {
                throw new RequirementParseException("consumeDurability must be a nonnegative integer and is input-only");
            }
        }
        return requirement;
    }
}
