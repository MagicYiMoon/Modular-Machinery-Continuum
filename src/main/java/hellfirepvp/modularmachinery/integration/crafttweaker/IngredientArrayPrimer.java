/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.*;
import com.blamejared.crafttweaker.api.ingredient.type.TagIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.google.gson.*;
import org.openzen.zencode.java.ZenCodeType;
import java.util.*;

/** Candidate list used by RecipePrimer.addIngredientArrayInput/addRandomItemOutput. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.IngredientArrayPrimer")
public final class IngredientArrayPrimer {
    private final JsonArray items = new JsonArray();
    private JsonObject last;
    private final Map<Integer, AdvancedItemCheckerCT> checkers = new LinkedHashMap<>();
    private final Map<Integer, List<AdvancedItemModifierCT>> modifiers = new LinkedHashMap<>();
    private int lastIndex = -1;
    @ZenCodeType.Method public IngredientArrayPrimer addIngredient(IIngredient ingredient) {
        return ingredient instanceof IIngredientWithAmount withAmount
                ? add(withAmount.getIngredient(), withAmount.getAmount()) : add(ingredient, 1);
    }
    @ZenCodeType.Method public IngredientArrayPrimer addIngredients(IIngredient... ingredients) { for (var i : ingredients) add(i, 1); return this; }
    @ZenCodeType.Method public IngredientArrayPrimer addChancedIngredient(IIngredient ingredient, float weight) { var result = add(ingredient, 1); setChance(weight); return result; }
    private IngredientArrayPrimer add(IIngredient ingredient, long count) {
        if (count < 1 || count > Integer.MAX_VALUE) throw new IllegalArgumentException("Amount must be 1..2147483647");
        if (ingredient instanceof IItemStack stack) last = entry(stack.getInternal(), (int)count);
        else if (ingredient instanceof TagIngredient tag) { last = new JsonObject(); last.addProperty("item", "tag:" + tag.key().location()); last.addProperty("amount", (int)count); }
        else throw new IllegalArgumentException("Candidate must be an item stack or item tag");
        items.add(last); lastIndex = items.size() - 1; return this;
    }
    private static JsonObject entry(net.minecraft.world.item.ItemStack stack, int count) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Empty item candidate");
        var e = new JsonObject(); e.addProperty("item", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        e.addProperty("amount", count); if (stack.getTag() != null) e.addProperty("nbt", stack.getTag().toString()); return e;
    }
    @ZenCodeType.Method public IngredientArrayPrimer setMinMaxAmount(int min, int max) {
        if (last == null || min < 1 || max < min) throw new IllegalArgumentException("Invalid min/max amount");
        last.addProperty("minAmount", min); last.addProperty("maxAmount", max); return this;
    }
    /** Candidate chance is a relative weight, matching the source mod. */
    @ZenCodeType.Method public IngredientArrayPrimer setChance(float weight) {
        if (last == null || !Float.isFinite(weight) || weight < 0) throw new IllegalArgumentException("Invalid candidate weight");
        last.addProperty("chance", weight); return this;
    }
    @ZenCodeType.Method public IngredientArrayPrimer setChecker(AdvancedItemCheckerCT checker) {
        if (lastIndex < 0 || checker == null) throw new IllegalStateException("Add an ingredient before setting a checker");
        checkers.put(lastIndex, checker); return this;
    }
    @ZenCodeType.Method public IngredientArrayPrimer addItemModifier(AdvancedItemModifierCT modifier) {
        if (lastIndex < 0 || modifier == null) throw new IllegalStateException("Add an ingredient before adding a modifier");
        modifiers.computeIfAbsent(lastIndex, ignored -> new ArrayList<>()).add(modifier); return this;
    }
    @ZenCodeType.Method public IngredientArrayPrimer build() { return this; }
    JsonArray snapshot() { if (items.isEmpty()) throw new IllegalStateException("Ingredient array is empty"); return items.deepCopy(); }
    Map<Integer, AdvancedItemCheckerCT> checkers() { return checkers; }
    Map<Integer, List<AdvancedItemModifierCT>> modifiers() { return modifiers; }
}
