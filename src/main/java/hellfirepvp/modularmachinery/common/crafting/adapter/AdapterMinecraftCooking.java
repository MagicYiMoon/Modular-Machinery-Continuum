/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting.adapter;

import com.google.gson.JsonElement;
import hellfirepvp.modularmachinery.common.crafting.MachineRecipe;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Adapter for the three vanilla cooking recipe families added after 1.12.2.
 *
 * <p>The source mod had only the furnace adapter because blasting, smoking and campfire cooking did not
 * exist in 1.12.2. They use the same machine semantics as that adapter: one input, one output and an FE/tick
 * requirement, while preserving tag and OR ingredients instead of selecting the first item silently.</p>
 */
public final class AdapterMinecraftCooking<T extends AbstractCookingRecipe> extends RecipeAdapter {

    public static final ResourceLocation BLASTING = new ResourceLocation("minecraft", "blasting");
    public static final ResourceLocation SMOKING = new ResourceLocation("minecraft", "smoking");
    public static final ResourceLocation CAMPFIRE = new ResourceLocation("minecraft", "campfire_cooking");

    private final RecipeType<T> type;
    private final int defaultDuration;

    public AdapterMinecraftCooking(ResourceLocation name, RecipeType<T> type, int defaultDuration) {
        super(name);
        this.type = type;
        this.defaultDuration = defaultDuration;
    }

    @Override
    public java.util.Collection<MachineRecipe> createRecipesFor(String owningMachine,
                                                                 List<RecipeModifier> modifiers,
                                                                 List<Requirement> additionalRequirements) {
        RecipeManager manager = RecipeAdapters.recipeManager();
        if (manager == null) {
            RecipeAdapters.recordNotExpanded(getRegistryName(), "no recipe manager is available yet");
            return List.of();
        }
        RegistryAccess registries = RecipeAdapters.registryAccess();
        List<T> source = new ArrayList<>(manager.getAllRecipesFor(type));
        source.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
        Integer duration = durationWithModifiers(defaultDuration, modifiers);
        Integer energy = energyWithModifiers(AdapterMinecraftFurnace.DEFAULT_ENERGY_PER_TICK, modifiers);
        if (duration == null || energy == null) {
            RecipeAdapters.recordNotExpanded(getRegistryName(), "a recipe modifier produced a non-finite value");
            return List.of();
        }

        List<MachineRecipe> result = new ArrayList<>(source.size());
        for (T recipe : source) {
            InputDescriptor input = inputDescriptor(recipe);
            ItemStack output = recipe.getResultItem(registries);
            if (input == null || output == null || output.isEmpty()) continue;
            Integer inputAmount = scaledValue(input.amount, modifiers, ITEM_TARGET, IOType.INPUT);
            Integer outputAmount = scaledValue(Math.max(1, output.getCount()), modifiers, ITEM_TARGET,
                    IOType.OUTPUT);
            if (inputAmount == null || outputAmount == null) continue;

            List<Requirement> requirements = newRequirementList();
            if (inputAmount > 0) requirements.add(input.requirement(inputAmount));
            if (outputAmount > 0) requirements.add(new RequirementItem(IOType.OUTPUT, idOf(output), outputAmount));
            if (energy > 0) requirements.add(new RequirementEnergy(IOType.INPUT, energy));
            requirements.addAll(additionalRequirements);
            result.add(createRecipeShell(getRegistryName().getPath() + "_" + incId + "_"
                            + input.description() + "_" + idOf(output), owningMachine, duration, requirements));
        }
        incId++;
        return result;
    }

    @Nullable
    private static InputDescriptor inputDescriptor(AbstractCookingRecipe recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return null;
        Set<String> alternatives = new LinkedHashSet<>();
        collectIngredientIds(ingredients.get(0).toJson(), alternatives);
        if (alternatives.isEmpty()) {
            ItemStack[] items = ingredients.get(0).getItems();
            if (items.length == 1 && !items[0].isEmpty()) alternatives.add(idOf(items[0]));
        }
        return alternatives.isEmpty() ? null : new InputDescriptor(List.copyOf(alternatives), 1);
    }

    private static void collectIngredientIds(JsonElement element, Set<String> out) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) collectIngredientIds(child, out);
            return;
        }
        if (!element.isJsonObject()) return;
        var object = element.getAsJsonObject();
        if (object.has("tag") && object.get("tag").isJsonPrimitive()) {
            String tag = object.get("tag").getAsString();
            if (ResourceLocation.tryParse(tag) != null) out.add("tag:" + tag);
        } else if (object.has("item") && object.get("item").isJsonPrimitive()) {
            String item = object.get("item").getAsString();
            if (ResourceLocation.tryParse(item) != null) out.add(item);
        }
    }

    private static String idOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    private record InputDescriptor(List<String> ids, int amount) {
        Requirement requirement(int scaledAmount) {
            if (ids.size() == 1) return new RequirementItem(IOType.INPUT, ids.get(0), scaledAmount);
            return new RequirementIngredientArray(ids.stream()
                    .map(id -> new RequirementItem(IOType.INPUT, id, scaledAmount)).toList(), 1.0F);
        }

        String description() {
            return ids.size() == 1 ? ids.get(0) : "{" + String.join(", ", ids) + "}";
        }
    }
}
