/* SPDX-License-Identifier: GPL-3.0-only
 * Port of upstream RecipePrimer (HellFirePvP / Modular Machinery 2019), modified 2026-10.
 */
package hellfirepvp.modularmachinery.integration.crafttweaker;

import com.blamejared.crafttweaker.api.CraftTweakerAPI;
import com.blamejared.crafttweaker.api.action.base.IRuntimeAction;
import com.blamejared.crafttweaker.api.annotation.ZenRegister;
import com.blamejared.crafttweaker.api.ingredient.*;
import com.blamejared.crafttweaker.api.ingredient.type.TagIngredient;
import com.blamejared.crafttweaker.api.item.IItemStack;
import com.blamejared.crafttweaker.api.fluid.IFluidStack;
import com.blamejared.crafttweaker.api.data.IData;
import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementFluid;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.machine.MachineReloadListener;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.openzen.zencode.java.ZenCodeType;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.event.recipe.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Source-style chain builder. Each modifier applies only to the last appended requirement. */
@ZenRegister
@ZenCodeType.Name("mods.modularmachinery.RecipePrimer")
public final class RecipePrimer {
    private final JsonObject recipe = new JsonObject();
    private final JsonArray requirements = new JsonArray();
    private final Map<Class<?>, java.util.List<Consumer<?>>> eventHandlers = new LinkedHashMap<>();
    private final hellfirepvp.modularmachinery.common.crafting.ScriptRecipeCallbacks callbacks =
            new hellfirepvp.modularmachinery.common.crafting.ScriptRecipeCallbacks();
    private JsonObject last;
    RecipePrimer(String name, String machine, int ticks, int priority, boolean cancel) {
        recipe.addProperty("name", name); recipe.addProperty("machine", machine);
        recipe.addProperty("duration", ticks); recipe.addProperty("priority", priority);
        recipe.addProperty("cancel", cancel); recipe.addProperty("maxThreads", -1);
        recipe.addProperty("threadName", ""); recipe.add("requirements", requirements);
    }
    private JsonObject append(String type, boolean input) {
        last = new JsonObject(); last.addProperty("type", "modularmachinery:" + type);
        last.addProperty("io-type", input ? "input" : "output");
        requirements.add(last); return last;
    }
    private JsonObject last() {
        if (last == null) throw new IllegalStateException("Add a requirement before configuring it");
        return last;
    }
    private static int amount(long value) {
        if (value < 1 || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Amount must be 1..2147483647");
        return (int) value;
    }
    private static void nbt(JsonObject entry, CompoundTag tag) {
        if (tag != null) entry.addProperty("nbt", tag.toString());
    }
    private JsonObject itemEntry(ItemStack stack, int count) {
        if (stack.isEmpty()) throw new IllegalArgumentException("Empty item ingredient");
        JsonObject entry = new JsonObject();
        entry.addProperty("item", net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString());
        entry.addProperty("amount", amount(count)); nbt(entry, stack.getTag()); return entry;
    }
    private RecipePrimer addItem(IIngredient ingredient, int count, boolean input) {
        if (ingredient instanceof IItemStack stack) {
            JsonObject entry = append("item", input);
            itemEntry(stack.getInternal(), count).entrySet().forEach(e -> entry.add(e.getKey(), e.getValue()));
            entry.addProperty("chance", 1F);
        } else if (input && ingredient instanceof TagIngredient tag) {
            JsonObject entry = append("item", true);
            entry.addProperty("item", "tag:" + tag.key().location());
            entry.addProperty("amount", amount(count)); entry.addProperty("chance", 1F);
        } else if (input && ingredient instanceof com.blamejared.crafttweaker.api.ingredient.type.IIngredientList list) {
            // Concrete alternatives are supported; arbitrary predicates/transformers are not silently
            // broadened to their displayed item list.
            JsonArray alternatives = new JsonArray();
            for (IIngredient candidate : list.getIngredients()) {
                if (candidate instanceof IItemStack stack) alternatives.add(itemEntry(stack.getInternal(), count));
                else if (candidate instanceof TagIngredient tag) {
                    JsonObject alternative = new JsonObject();
                    alternative.addProperty("item", "tag:" + tag.key().location());
                    alternative.addProperty("amount", amount(count)); alternatives.add(alternative);
                } else throw new IllegalArgumentException("Alternative must be a plain item or item tag");
            }
            JsonObject entry = append("ingredient_array_input", true);
            entry.add("items", alternatives); entry.addProperty("chance", 1F);
        } else if (!input && ingredient instanceof TagIngredient tag) {
            // CraftTweaker 14 has no ore dictionary.  An item tag is its modern equivalent,
            // and the ingredient-array output path resolves a stable representative item at
            // runtime (the same role the 1.12 ore-dictionary output played).
            JsonObject candidate = new JsonObject();
            candidate.addProperty("item", "tag:" + tag.key().location());
            candidate.addProperty("amount", amount(count));
            JsonArray alternatives = new JsonArray(); alternatives.add(candidate);
            JsonObject entry = append("ingredient_array_input", false);
            entry.add("items", alternatives); entry.addProperty("chance", 1F);
        } else if (!input && ingredient instanceof com.blamejared.crafttweaker.api.ingredient.type.IIngredientList list) {
            JsonArray alternatives = new JsonArray();
            for (IIngredient candidate : list.getIngredients()) {
                if (candidate instanceof IItemStack stack) alternatives.add(itemEntry(stack.getInternal(), count));
                else if (candidate instanceof TagIngredient tag) {
                    JsonObject alternative = new JsonObject();
                    alternative.addProperty("item", "tag:" + tag.key().location());
                    alternative.addProperty("amount", amount(count)); alternatives.add(alternative);
                } else throw new IllegalArgumentException("Alternative must be a plain item or item tag");
            }
            if (alternatives.isEmpty()) throw new IllegalArgumentException("Empty item output alternatives");
            JsonObject entry = append("ingredient_array_input", false);
            entry.add("items", alternatives); entry.addProperty("chance", 1F);
        } else throw new IllegalArgumentException("Unsupported ingredient; use an item stack, item tag or plain alternatives");
        return this;
    }
    @ZenCodeType.Method public RecipePrimer addItemInput(IItemStack stack) { return addItem(stack, stack.getAmount(), true); }
    @ZenCodeType.Method public RecipePrimer addItemInput(IIngredient ingredient) { return addItem(ingredient, 1, true); }
    @ZenCodeType.Method public RecipePrimer addItemInput(IIngredientWithAmount ingredient) { return addItem(ingredient.getIngredient(), ingredient.getAmount(), true); }
    @ZenCodeType.Method public RecipePrimer addItemOutput(IItemStack stack) { return addItem(stack, stack.getAmount(), false); }
    @ZenCodeType.Method public RecipePrimer addItemOutput(IIngredient ingredient) { return addItem(ingredient, 1, false); }
    @ZenCodeType.Method public RecipePrimer addItemOutput(IIngredientWithAmount stack) { return addItem(stack.getIngredient(), stack.getAmount(), false); }
    /** Modern replacement for the removed 1.12 {@code addItemInput(oreDict, amount)} overload. */
    @ZenCodeType.Method public RecipePrimer addItemInput(IIngredient ingredient, int count) {
        return addItem(ingredient, count, true);
    }
    /** Modern replacement for the removed 1.12 {@code addItemOutput(oreDict, amount)} overload. */
    @ZenCodeType.Method public RecipePrimer addItemOutput(IIngredient ingredient, int count) {
        return addItem(ingredient, count, false);
    }
    private RecipePrimer array(IngredientArrayPrimer primer, boolean input) {
        if (primer == null) throw new IllegalArgumentException("Ingredient array cannot be null");
        var entry = append("ingredient_array_input", input); entry.add("items", primer.snapshot()); entry.addProperty("chance", 1F);
        int index = requirements.size() - 1;
        primer.checkers().forEach((candidate, checker) -> callbacks.setArrayChecker(index, candidate,
                (controller, stack) -> checker.isMatch(controller, com.blamejared.crafttweaker.api.item.IItemStack.of(stack))));
        primer.modifiers().forEach((candidate, values) -> values.forEach(modifier -> callbacks.addArrayModifier(index, candidate,
                (controller, stack) -> {
                    var result = modifier.apply(controller, com.blamejared.crafttweaker.api.item.IItemStack.of(stack));
                    return result == null ? net.minecraft.world.item.ItemStack.EMPTY : result.getImmutableInternal();
                })));
        return this;
    }
    @ZenCodeType.Method public RecipePrimer addIngredientArrayInput(IngredientArrayPrimer primer) { return array(primer, true); }
    @ZenCodeType.Method public RecipePrimer addRandomItemOutput(IngredientArrayPrimer primer) { return array(primer, false); }

    /** Adds the source mod's optional catalyst input and its conditional recipe modifiers. */
    @ZenCodeType.Method public RecipePrimer addCatalystInput(IItemStack stack, String[] tooltips, RecipeModifier[] modifiers) {
        if (stack == null || stack.getInternal().isEmpty()) throw new IllegalArgumentException("Empty catalyst item");
        JsonObject entry = append("catalyst", true);
        JsonArray items = new JsonArray(); items.add(itemEntry(stack.getInternal(), stack.getAmount())); entry.add("items", items);
        entry.addProperty("chance", 1F); catalystExtras(entry, tooltips, modifiers); return this;
    }
    @ZenCodeType.Method public RecipePrimer addCatalystInput(IIngredient ingredient, String[] tooltips, RecipeModifier[] modifiers) {
        if (ingredient instanceof IItemStack stack) return addCatalystInput(stack, tooltips, modifiers);
        if (ingredient instanceof TagIngredient tag) {
            JsonObject entry = append("catalyst", true); JsonArray items = new JsonArray();
            JsonObject candidate = new JsonObject(); candidate.addProperty("item", "tag:" + tag.key().location());
            candidate.addProperty("amount", 1); items.add(candidate); entry.add("items", items); entry.addProperty("chance", 1F);
            catalystExtras(entry, tooltips, modifiers); return this;
        }
        throw new IllegalArgumentException("Catalyst must be an item stack or item tag");
    }
    @ZenCodeType.Method public RecipePrimer addCatalystInput(IngredientArrayPrimer primer, String[] tooltips, RecipeModifier[] modifiers) {
        if (primer == null) throw new IllegalArgumentException("Catalyst ingredient array cannot be null");
        JsonObject entry = append("catalyst", true); entry.add("items", primer.snapshot()); entry.addProperty("chance", 1F);
        catalystExtras(entry, tooltips, modifiers); return this;
    }
    @ZenCodeType.Method public RecipePrimer addCatalystInput(IItemStack stack) { return addCatalystInput(stack, new String[0], new RecipeModifier[0]); }
    @ZenCodeType.Method public RecipePrimer addCatalystInput(IIngredient ingredient) {
        return addCatalystInput(ingredient, new String[0], new RecipeModifier[0]);
    }
    @ZenCodeType.Method public RecipePrimer addCatalystInput(IIngredientWithAmount ingredient) {
        return addCatalystInput(ingredient, new String[0], new RecipeModifier[0]);
    }
    @ZenCodeType.Method public RecipePrimer addCatalystInput(IngredientArrayPrimer primer) {
        return addCatalystInput(primer, new String[0], new RecipeModifier[0]);
    }
    @ZenCodeType.Method public RecipePrimer addCatalystInput(IIngredientWithAmount ingredient, String[] tooltips, RecipeModifier[] modifiers) {
        if (ingredient == null || ingredient.getIngredient() == null) throw new IllegalArgumentException("Empty catalyst ingredient");
        IIngredient base = ingredient.getIngredient();
        int count = amount(ingredient.getAmount());
        if (base instanceof IItemStack stack) {
            JsonObject entry = append("catalyst", true); JsonArray items = new JsonArray();
            items.add(itemEntry(stack.getInternal(), count)); entry.add("items", items); entry.addProperty("chance", 1F);
            catalystExtras(entry, tooltips, modifiers); return this;
        }
        if (base instanceof TagIngredient tag) {
            JsonObject entry = append("catalyst", true); JsonArray items = new JsonArray(); JsonObject candidate = new JsonObject();
            candidate.addProperty("item", "tag:" + tag.key().location()); candidate.addProperty("amount", count);
            items.add(candidate); entry.add("items", items); entry.addProperty("chance", 1F);
            catalystExtras(entry, tooltips, modifiers); return this;
        }
        throw new IllegalArgumentException("Catalyst must be an item stack or item tag");
    }

    private static void catalystExtras(JsonObject entry, String[] tooltips, RecipeModifier[] modifiers) {
        if (tooltips != null && tooltips.length > 0) {
            JsonArray tips = new JsonArray(); for (String tip : tooltips) if (tip != null && !tip.isEmpty()) tips.add(tip); entry.add("tooltips", tips);
        }
        if (modifiers != null && modifiers.length > 0) {
            JsonArray values = new JsonArray(); for (RecipeModifier modifier : modifiers) if (modifier != null) values.add(modifierJson(modifier)); entry.add("modifiers", values);
        }
    }
    private static JsonObject modifierJson(RecipeModifier modifier) {
        JsonObject json = new JsonObject(); json.addProperty("target", modifier.getTarget());
        json.addProperty("io", modifier.getIOTarget() == hellfirepvp.modularmachinery.common.machine.IOType.INPUT ? "input" : "output");
        json.addProperty("multiplier", modifier.getModifier()); json.addProperty("operation", modifier.getOperation());
        json.addProperty("affectChance", modifier.isChance()); return json;
    }
    @ZenCodeType.Method public RecipePrimer addInput(IItemStack stack) { return addItemInput(stack); }
    @ZenCodeType.Method public RecipePrimer addInput(IIngredient ingredient) { return addItemInput(ingredient); }
    @ZenCodeType.Method public RecipePrimer addInput(IIngredientWithAmount ingredient) { return addItemInput(ingredient); }
    @ZenCodeType.Method public RecipePrimer addInput(IFluidStack fluid) { return addFluidInput(fluid); }
    @ZenCodeType.Method public RecipePrimer addOutput(IItemStack stack) { return addItemOutput(stack); }
    @ZenCodeType.Method public RecipePrimer addOutput(IIngredient ingredient) { return addItemOutput(ingredient); }
    @ZenCodeType.Method public RecipePrimer addOutput(IIngredientWithAmount ingredient) { return addItemOutput(ingredient); }
    @ZenCodeType.Method public RecipePrimer addOutput(IFluidStack fluid) { return addFluidOutput(fluid); }
    @ZenCodeType.Method public RecipePrimer addItemInputs(IIngredient... ingredients) {
        for (var ingredient : ingredients) addItem(ingredient,
                ingredient instanceof IItemStack stack ? stack.getAmount() : 1, true);
        return this;
    }
    @ZenCodeType.Method public RecipePrimer addItemOutputs(IIngredient... ingredients) { for (var ingredient : ingredients) addItemOutput(ingredient); return this; }
    @ZenCodeType.Method public RecipePrimer addInputs(IIngredient... ingredients) { return addItemInputs(ingredients); }
    @ZenCodeType.Method public RecipePrimer addOutputs(IIngredient... ingredients) { return addItemOutputs(ingredients); }

    private RecipePrimer fluid(IFluidStack fluid, boolean input, boolean tick) {
        if (fluid.isEmpty()) throw new IllegalArgumentException("Empty fluid ingredient");
        JsonObject entry = append(tick ? "fluid_pertick" : "fluid", input);
        entry.addProperty("fluid", fluid.getRegistryName().toString());
        entry.addProperty("amount", amount(fluid.getAmount())); nbt(entry, fluid.getInternalTag());
        if (!tick) entry.addProperty("chance", 1F); return this;
    }
    @ZenCodeType.Method public RecipePrimer addFluidInput(IFluidStack fluid) { return fluid(fluid, true, false); }
    @ZenCodeType.Method public RecipePrimer addFluidOutput(IFluidStack fluid) { return fluid(fluid, false, false); }
    @ZenCodeType.Method public RecipePrimer addFluidPerTickInput(IFluidStack fluid) { return fluid(fluid, true, true); }
    @ZenCodeType.Method public RecipePrimer addFluidPerTickOutput(IFluidStack fluid) { return fluid(fluid, false, true); }
    @ZenCodeType.Method public RecipePrimer addFluidInputs(IFluidStack... fluids) { for (var f : fluids) addFluidInput(f); return this; }
    @ZenCodeType.Method public RecipePrimer addFluidOutputs(IFluidStack... fluids) { for (var f : fluids) addFluidOutput(f); return this; }
    @ZenCodeType.Method public RecipePrimer addFluidPerTickInputs(IFluidStack... fluids) { for (var f : fluids) addFluidPerTickInput(f); return this; }
    @ZenCodeType.Method public RecipePrimer addFluidPerTickOutputs(IFluidStack... fluids) { for (var f : fluids) addFluidPerTickOutput(f); return this; }
    private RecipePrimer gas(String gas, int count, boolean input, boolean tick) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("mekanism"))
            throw new IllegalStateException("Gas recipes require Mekanism");
        JsonObject entry = append(tick ? "gas_pertick" : "gas", input);
        entry.addProperty("gas", gas); entry.addProperty("amount", amount(count));
        if (!tick) entry.addProperty("chance", 1F); return this;
    }
    @ZenCodeType.Method public RecipePrimer addGasInput(String gas, int amount) { return gas(gas, amount, true, false); }
    @ZenCodeType.Method public RecipePrimer addGasOutput(String gas, int amount) { return gas(gas, amount, false, false); }
    @ZenCodeType.Method public RecipePrimer addGasPerTickInput(String gas, int amount) { return gas(gas, amount, true, true); }
    @ZenCodeType.Method public RecipePrimer addGasPerTickOutput(String gas, int amount) { return gas(gas, amount, false, true); }
    @ZenCodeType.Method public RecipePrimer addGasInputs(String... gases) {
        if (gases == null) throw new IllegalArgumentException("Gas list cannot be null");
        for (String gas : gases) addGasInput(gas, 1);
        return this;
    }
    @ZenCodeType.Method public RecipePrimer addGasOutputs(String... gases) {
        if (gases == null) throw new IllegalArgumentException("Gas list cannot be null");
        for (String gas : gases) addGasOutput(gas, 1);
        return this;
    }
    @ZenCodeType.Method public RecipePrimer addGasPerTickInputs(String... gases) {
        if (gases == null) throw new IllegalArgumentException("Gas list cannot be null");
        for (String gas : gases) addGasPerTickInput(gas, 1);
        return this;
    }
    @ZenCodeType.Method public RecipePrimer addGasPerTickOutputs(String... gases) {
        if (gases == null) throw new IllegalArgumentException("Gas list cannot be null");
        for (String gas : gases) addGasPerTickOutput(gas, 1);
        return this;
    }
    private RecipePrimer energy(long value, boolean input) {
        JsonObject entry = append("energy", input); entry.addProperty("energyPerTick", amount(value)); return this;
    }
    private RecipePrimer compressedAir(float pressure, int air, boolean input, boolean tick) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft")) throw new IllegalStateException("Compressed air recipes require PneumaticCraft");
        JsonObject fields = new JsonObject(); fields.addProperty("pressure", pressure); fields.addProperty("air", air); fields.addProperty("perTick", tick);
        new hellfirepvp.modularmachinery.integration.pneumatic.RequirementCompressedAir.Type(tick)
                .createRequirement(input ? hellfirepvp.modularmachinery.common.machine.IOType.INPUT : hellfirepvp.modularmachinery.common.machine.IOType.OUTPUT, fields);
        JsonObject entry = append("compressed_air", input); fields.entrySet().forEach(e -> entry.add(e.getKey(), e.getValue())); return this;
    }
    @ZenCodeType.Method public RecipePrimer addCompressedAirInput(float pressure, int air) { return compressedAir(pressure, air, true, false); }
    @ZenCodeType.Method public RecipePrimer addTemperatureInput(int minKelvin, int maxKelvin) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("pneumaticcraft")) throw new IllegalStateException("Temperature recipes require PneumaticCraft");
        var json = new JsonObject(); json.addProperty("min", minKelvin); json.addProperty("max", maxKelvin);
        new hellfirepvp.modularmachinery.integration.pneumatic.RequirementTemperature.Type().createRequirement(hellfirepvp.modularmachinery.common.machine.IOType.INPUT, json);
        var req = append("temperature", true); req.addProperty("min", minKelvin); req.addProperty("max", maxKelvin);
        return this;
    }
    @ZenCodeType.Method public RecipePrimer addEmberInput(double ember) {
        if (!net.minecraftforge.fml.ModList.get().isLoaded("embers")) throw new IllegalStateException("Ember recipes require Embers Rekindled");
        if (!Double.isFinite(ember) || ember < 0) throw new IllegalArgumentException("ember must be finite and non-negative");
        JsonObject entry = append("ember", true); entry.addProperty("ember", ember); return this;
    }
    @ZenCodeType.Method public RecipePrimer addCompressedAirOutput(int air) { return compressedAir(0, air, false, false); }
    @ZenCodeType.Method public RecipePrimer addCompressedAirPerTickInput(float pressure, int air) { return compressedAir(pressure, air, true, true); }
    @ZenCodeType.Method public RecipePrimer addCompressedAirPerTickOutput(int air) { return compressedAir(0, air, false, true); }
    @ZenCodeType.Method public RecipePrimer addEnergyPerTickInput(long value) { return energy(value, true); }
    @ZenCodeType.Method public RecipePrimer addEnergyPerTickOutput(long value) { return energy(value, false); }
    @ZenCodeType.Method public RecipePrimer addFuelItemInput(int ticks) {
        JsonObject entry = append("item", true); entry.addProperty("item", "any:fuel");
        entry.addProperty("time", amount(ticks)); entry.addProperty("chance", 1F); return this;
    }
    @ZenCodeType.Method public RecipePrimer addSmartInterfaceDataInput(String type, float min, float max) {
        JsonObject entry = append("interface_number_input", true); entry.addProperty("interface", type);
        entry.addProperty("min", min); entry.addProperty("max", max); return this;
    }
    @ZenCodeType.Method public RecipePrimer addSmartInterfaceDataInput(String type, float value) { return addSmartInterfaceDataInput(type, value, value); }
    private RecipePrimer environment(JsonObject fields) {
        new hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnvironment.Type()
                .createRequirement(hellfirepvp.modularmachinery.common.machine.IOType.INPUT, fields);
        JsonObject entry = append("environment", true);
        fields.entrySet().forEach(field -> entry.add(field.getKey(), field.getValue()));
        return this;
    }
    private RecipePrimer environmentRange(String condition, int min, int max) {
        var fields = new JsonObject(); fields.addProperty("condition", condition);
        fields.addProperty("min", min); fields.addProperty("max", max); return environment(fields);
    }
    private RecipePrimer environmentIds(String condition, String[] ids) {
        if (ids == null) throw new IllegalArgumentException("Environment ID list cannot be null");
        var fields = new JsonObject(); fields.addProperty("condition", condition);
        var values = new com.google.gson.JsonArray(); for (String id : ids) values.add(id);
        fields.add("values", values); return environment(fields);
    }
    @ZenCodeType.Method public RecipePrimer addEnvironmentAltitude(int min, int max) { return environmentRange("altitude", min, max); }
    @ZenCodeType.Method public RecipePrimer addEnvironmentTime(int min, int max) { return environmentRange("time", min, max); }
    @ZenCodeType.Method public RecipePrimer addEnvironmentDimensions(String... ids) { return environmentIds("dimensions", ids); }
    @ZenCodeType.Method public RecipePrimer addEnvironmentBiomes(String... ids) { return environmentIds("biomes", ids); }
    @ZenCodeType.Method public RecipePrimer addEnvironmentMoonPhases(int... phases) {
        if (phases == null) throw new IllegalArgumentException("Moon phases cannot be null");
        var fields = new JsonObject(); fields.addProperty("condition", "moonphase");
        var values = new com.google.gson.JsonArray(); for (int phase : phases) values.add(phase);
        fields.add("values", values); return environment(fields);
    }
    @ZenCodeType.Method public RecipePrimer addEnvironmentWeather(String weather) {
        var fields = new JsonObject(); fields.addProperty("condition", "weather");
        fields.addProperty("weather", weather); return environment(fields);
    }
    @ZenCodeType.Method public RecipePrimer setChance(float chance) {
        if (!last().has("chance")) throw new IllegalArgumentException("Last requirement does not support chance");
        if (!Float.isFinite(chance) || chance < 0 || chance > 1) throw new IllegalArgumentException("Chance must be 0..1");
        last.addProperty("chance", chance); return this;
    }
    @ZenCodeType.Method public RecipePrimer setTag(String tag) { last().addProperty("selector-tag", tag); return this; }
    @ZenCodeType.Method public RecipePrimer setParallelizeUnaffected(boolean value) { last().addProperty("parallelizeUnaffected", value); return this; }
    @ZenCodeType.Method public RecipePrimer setTriggerTime(int tick) {
        if (tick < 0) throw new IllegalArgumentException("Trigger time must not be negative");
        last().addProperty("triggerTime", tick); return this;
    }
    @ZenCodeType.Method public RecipePrimer setTriggerRepeatable(boolean value) { last().addProperty("triggerRepeatable", value); return this; }
    @ZenCodeType.Method public RecipePrimer setIgnoreOutputCheck(boolean value) {
        last().addProperty("ignoreOutputCheck", value); return this;
    }
    @ZenCodeType.Method public RecipePrimer setNBTChecker(AdvancedItemCheckerCT checker) {
        lastRequirement();
        if (checker == null) throw new IllegalArgumentException("Item checker cannot be null");
        if (!isPlainItemRequirement()) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.warn("[ModularMachinery] setNBTChecker(AdvancedItemNBTChecker checker) only can be applied to `Item`!");
            return this;
        }
        callbacks.setChecker(requirements.size() - 1,
                (controller, stack) -> checker.isMatch(controller, com.blamejared.crafttweaker.api.item.IItemStack.of(stack)));
        return this;
    }
    @ZenCodeType.Method public RecipePrimer addItemModifier(AdvancedItemModifierCT modifier) {
        lastRequirement();
        if (modifier == null) throw new IllegalArgumentException("Item modifier cannot be null");
        if (!isPlainItemRequirement()) {
            hellfirepvp.modularmachinery.Modularmachinery.LOG.warn("[ModularMachinery] addItemModifier(AdvancedItemModifier checker) only can be applied to `Item`!");
            return this;
        }
        callbacks.addModifier(requirements.size() - 1, (controller, stack) -> {
            var result = modifier.apply(controller, com.blamejared.crafttweaker.api.item.IItemStack.of(stack));
            return result == null ? net.minecraft.world.item.ItemStack.EMPTY : result.getImmutableInternal();
        });
        return this;
    }
    /** Adds one or more recipe-level lines displayed in the JEI recipe page. */
    @ZenCodeType.Method public RecipePrimer addRecipeTooltip(String... values) {
        JsonArray tooltips = recipe.has("tooltips") && recipe.get("tooltips").isJsonArray()
                ? recipe.getAsJsonArray("tooltips") : new JsonArray();
        for (String value : values) {
            if (value != null && !value.isEmpty()) tooltips.add(value);
        }
        recipe.add("tooltips", tooltips);
        return this;
    }
    /** Enables or disables recipe-level batch parallelism from parallel controllers. */
    @ZenCodeType.Method public RecipePrimer setParallelized(boolean value) {
        recipe.addProperty("parallelized", value); return this;
    }
    /** Prevents this recipe from being registered in JEI while keeping it runnable. */
    @ZenCodeType.Method public RecipePrimer setLoadJEI(boolean value) {
        recipe.addProperty("loadJEI", value); return this;
    }

    private <T> RecipePrimer handler(Class<T> type, Consumer<T> callback) {
        if (callback == null) throw new IllegalArgumentException("Recipe event callback cannot be null");
        eventHandlers.computeIfAbsent(type, ignored -> new java.util.ArrayList<>()).add(callback);
        return this;
    }
    @ZenCodeType.Method public RecipePrimer addPreCheckHandler(Consumer<RecipeCheckEvent> callback) {
        return handler(RecipeCheckEvent.class, event -> { if (event.phase == Phase.START) callback.accept(event); });
    }
    @ZenCodeType.Method public RecipePrimer addPostCheckHandler(Consumer<RecipeCheckEvent> callback) {
        return handler(RecipeCheckEvent.class, event -> { if (event.phase == Phase.END) callback.accept(event); });
    }
    @ZenCodeType.Method public RecipePrimer addCheckHandler(Consumer<RecipeCheckEvent> callback) {
        return handler(RecipeCheckEvent.class, callback);
    }
    @ZenCodeType.Method public RecipePrimer addStartHandler(Consumer<RecipeStartEvent> callback) {
        return handler(RecipeStartEvent.class, callback);
    }
    @ZenCodeType.Method public RecipePrimer addPreTickHandler(Consumer<RecipeTickEvent> callback) {
        return handler(RecipeTickEvent.class, event -> { if (event.phase == Phase.START) callback.accept(event); });
    }
    @ZenCodeType.Method public RecipePrimer addPostTickHandler(Consumer<RecipeTickEvent> callback) {
        return handler(RecipeTickEvent.class, event -> { if (event.phase == Phase.END) callback.accept(event); });
    }
    @ZenCodeType.Method public RecipePrimer addFailureHandler(Consumer<RecipeFailureEvent> callback) {
        return handler(RecipeFailureEvent.class, callback);
    }
    @ZenCodeType.Method public RecipePrimer addFinishHandler(Consumer<RecipeFinishEvent> callback) {
        return handler(RecipeFinishEvent.class, callback);
    }
    @ZenCodeType.Method public RecipePrimer addFactoryStartHandler(Consumer<FactoryRecipeStartEvent> callback) {
        return handler(FactoryRecipeStartEvent.class, callback);
    }
    @ZenCodeType.Method public RecipePrimer addFactoryPreTickHandler(Consumer<FactoryRecipeTickEvent> callback) {
        return handler(FactoryRecipeTickEvent.class, event -> { if (event.phase == Phase.START) callback.accept(event); });
    }
    @ZenCodeType.Method public RecipePrimer addFactoryPostTickHandler(Consumer<FactoryRecipeTickEvent> callback) {
        return handler(FactoryRecipeTickEvent.class, event -> { if (event.phase == Phase.END) callback.accept(event); });
    }
    @ZenCodeType.Method public RecipePrimer addFactoryFailureHandler(Consumer<FactoryRecipeFailureEvent> callback) {
        return handler(FactoryRecipeFailureEvent.class, callback);
    }
    @ZenCodeType.Method public RecipePrimer addFactoryFinishHandler(Consumer<FactoryRecipeFinishEvent> callback) {
        return handler(FactoryRecipeFinishEvent.class, callback);
    }
    @ZenCodeType.Method public RecipePrimer consumeDurability(int value) {
        if (!last().get("type").getAsString().equals("modularmachinery:item")
                || !last.get("io-type").getAsString().equals("input") || value < 0)
            throw new IllegalArgumentException("Durability applies only to item inputs");
        last.addProperty("consumeDurability", value); return this;
    }
    @ZenCodeType.Method public RecipePrimer setPreViewNBT(IData data) {
        if (!(data.getInternal() instanceof CompoundTag tag)) throw new IllegalArgumentException("Preview NBT must be a compound");
        last().addProperty("nbt-display", tag.toString()); return this;
    }
    @ZenCodeType.Method public RecipePrimer setMaxThreads(int value) {
        if (value < -1) throw new IllegalArgumentException("maxThreads must be -1 or nonnegative");
        recipe.addProperty("maxThreads", value); return this;
    }
    @ZenCodeType.Method public RecipePrimer setThreadName(String value) { recipe.addProperty("threadName", value); return this; }

    /** Frozen declaration: further mutation of this builder cannot change a recipe already built. */
    public JsonObject snapshot() {
        MachineRecipe decoded = RecipeSnapshotCodec.decode(recipe);
        for (Requirement requirement : decoded.requirements()) {
            if (requirement instanceof RequirementItem item && !item.isResolvable())
                throw new IllegalArgumentException("Unknown item: " + item.getRawId());
            if (requirement instanceof RequirementFluid fluid && !fluid.isResolvable())
                throw new IllegalArgumentException("Unknown fluid: " + fluid.getRawId());
        }
        return recipe.deepCopy();
    }
    @ZenCodeType.Method public void build() {
        JsonObject frozen = snapshot();
        CraftTweakerAPI.apply(new IRuntimeAction() {
            @Override public boolean shouldApplyOn(com.blamejared.crafttweaker.api.zencode.IScriptLoadSource source,
                                                   org.apache.logging.log4j.Logger logger) {
                return source.id().equals(com.blamejared.crafttweaker.api.CraftTweakerConstants.RELOAD_LISTENER_SOURCE_ID);
            }
            @Override public void apply() {
                ScriptRecipeRegistry.register(frozen);
                ScriptRecipeRegistry.registerCallbacks(frozen.get("name").getAsString(), callbacks);
                ScriptRecipeRegistry.registerHandlers(frozen.get("name").getAsString(), eventHandlers);
                var definitions = MachineReloadListener.serverDefinitions();
                definitions.recipes().installScriptRecipes(definitions.machines());
            }
            @Override public String describe() { return "Register Modular Machinery recipe " + frozen.get("name").getAsString(); }
            @Override public String systemName() { return "ModularMachinery"; }
        });
    }

    private JsonObject lastRequirement() {
        if (requirements.isEmpty() || last == null) throw new IllegalStateException("Add a requirement first");
        return last;
    }

    private boolean isPlainItemRequirement() {
        return "modularmachinery:item".equals(last().get("type").getAsString());
    }
}
