/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import java.util.*;

public final class IngredientArrayRequirementType implements RequirementType {
    public static final String NAME = "ingredient_array_input";
    @Override public Requirement createRequirement(IOType io, JsonObject json) {
        return decode(io, json, false);
    }
    public static RequirementIngredientArray decode(IOType io, JsonObject json, boolean snapshot) {
        if (io == null || !json.has("items") || !json.get("items").isJsonArray()
                || json.getAsJsonArray("items").isEmpty())
            throw new JsonParseException("ingredient_array_input requires valid IO and a nonempty items array");
        var items = new ArrayList<RequirementIngredientArray.Candidate>();
        for (var element : json.getAsJsonArray("items")) {
            if (!element.isJsonObject()) throw new JsonParseException("Each alternative must be an item object");
            var entry = element.getAsJsonObject().deepCopy();
            for (String key : List.of("amount", "nbt", "nbt-display"))
                if (!entry.has(key) && json.has(key)) entry.add(key, json.get(key).deepCopy());
            if (entry.has("consumeDurability"))
                throw new JsonParseException("Alternative durability is not supported");
            if (!entry.has("item") || !entry.get("item").isJsonPrimitive()
                    || !entry.getAsJsonPrimitive("item").isString()) throw new JsonParseException("Missing item ID");
            int amount = integer(entry, "amount", 1, snapshot);
            int min = integer(entry, "minAmount", amount, snapshot), max = integer(entry, "maxAmount", amount, snapshot);
            if (min > max) throw new JsonParseException("minAmount must not exceed maxAmount");
            float weight = entry.has("chance") ? entry.get("chance").getAsFloat() : 1F;
            if (!Float.isFinite(weight) || weight < 0) throw new JsonParseException("Candidate weight must be finite and nonnegative");
            var item = new RequirementItem(io, entry.get("item").getAsString(), amount).withNbt(RecipeNbt.read(entry));
            if (!item.isResolvable()) throw new JsonParseException("Unknown alternative item: " + item.getRawId());
            items.add(new RequirementIngredientArray.Candidate(item, min, max, weight));
        }
        return new RequirementIngredientArray(items, RequirementKeys.chanceOf(json), io);
    }
    private static int integer(JsonObject entry, String key, int fallback, boolean snapshot) {
        if (!entry.has(key)) return fallback;
        try {
            if (!entry.get(key).isJsonPrimitive() || !entry.getAsJsonPrimitive(key).isNumber()) throw new IllegalArgumentException();
            int value = entry.get(key).getAsBigDecimal().intValueExact();
            if (value < (snapshot ? 0 : 1)) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException e) { throw new JsonParseException(key + " must be a positive integer", e); }
    }
}
