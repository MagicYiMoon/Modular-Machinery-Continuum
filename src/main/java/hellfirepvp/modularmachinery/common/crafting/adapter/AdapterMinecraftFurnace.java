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
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementEnergy;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementIngredientArray;
import hellfirepvp.modularmachinery.common.crafting.requirement.RequirementItem;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;












































 
public final class AdapterMinecraftFurnace extends RecipeAdapter {

    /** Upstream's {@code new ResourceLocation("minecraft", "furnace")} (U43) -- the JSON's {@code "adapter"} value. */
    public static final ResourceLocation NAME = new ResourceLocation("minecraft", "furnace");

    /** Upstream's own default, {@code 120} ticks (U60), before any duration modifier. */
    public static final int DEFAULT_TICK_TIME = 120;

    /** Upstream's own figure, {@code 20} FE/tick (U83), before any energy modifier. */
    public static final int DEFAULT_ENERGY_PER_TICK = 20;

    public AdapterMinecraftFurnace() {
        super(NAME);
    }

    @Override
    public Collection<MachineRecipe> createRecipesFor(String owningMachine, List<RecipeModifier> modifiers,
                                                     List<Requirement> additionalRequirements) {
        RecipeManager manager = RecipeAdapters.recipeManager();
        if (manager == null) {
            RecipeAdapters.recordNotExpanded(NAME,
                    "no recipe manager is available yet, so the vanilla smelting list cannot be read");
            return List.of();
        }
        RegistryAccess registries = RecipeAdapters.registryAccess();

        // SORTED, AND THAT IS NOT COSMETIC. `getAllRecipesFor` returns the recipe manager's own ordering,
        // which is a HashMap's downstream of the datapack load -- so two runs of this adapter could hand the
        // recipe registry two different orderings, and a name collision between two generated recipes would
        // then resolve differently between runs. This port's loader already sorts the FILES it walks for
        // exactly that reason (RecipeLoader.discoverDirectory), so the rule is one rule.
        List<SmeltingRecipe> smelting = new ArrayList<>(manager.getAllRecipesFor(RecipeType.SMELTING));
        smelting.sort(Comparator.comparing(recipe -> recipe.getId().toString()));

        Integer tickTime = durationWithModifiers(DEFAULT_TICK_TIME, modifiers);
        if (tickTime == null) {
            RecipeAdapters.recordNotExpanded(NAME,
                    "a 'modularmachinery:duration' modifier produced a non-finite duration");
            return List.of();
        }
        Integer energyPerTick = energyWithModifiers(DEFAULT_ENERGY_PER_TICK, modifiers);
        if (energyPerTick == null) {
            RecipeAdapters.recordNotExpanded(NAME,
                    "a 'modularmachinery:energy' modifier produced a non-finite value");
            return List.of();
        }

        List<MachineRecipe> out = new ArrayList<>(smelting.size());
        Probe probe = new Probe(smelting.size(), tickTime, energyPerTick);
        for (SmeltingRecipe recipe : smelting) {
            InputDescriptor input = inputDescriptor(recipe);
            if (input == null) {
                probe.skippedNoSingleInput++;
                continue;
            }
            ItemStack output = recipe.getResultItem(registries);
            if (output == null || output.isEmpty()) {
                probe.skippedNoOutput++;
                continue;
            }
            String outputId = idOf(output);

            // EACH VALUE IS SCALED AT ITS OWN SITE, WHICH IS UPSTREAM'S SHAPE (U73-U86). Every requirement is
            // then added only when its scaled value is POSITIVE -- upstream's own guard, so a pack can scale
            // a requirement away with a zero multiplier instead of leaving an unsatisfiable one behind. The
            // modifiers are NOT handed to anything that would apply them a second time; see
            // RecipeAdapter.createRecipeShell for the bug that shape prevented.
            Integer inAmount = scaledValue(input.amount(), modifiers, RecipeAdapter.ITEM_TARGET,
                    IOType.INPUT);
            Integer outAmount = scaledValue(Math.max(1, output.getCount()), modifiers,
                    RecipeAdapter.ITEM_TARGET, IOType.OUTPUT);
            if (inAmount == null || outAmount == null) {
                probe.skippedNonFiniteModifier++;
                continue;
            }

            List<Requirement> requirements = newRequirementList();
            if (inAmount > 0) {
                requirements.add(input.requirement(inAmount));
            }
            if (outAmount > 0) {
                requirements.add(new RequirementItem(IOType.OUTPUT, outputId, outAmount));
            }
            if (energyPerTick > 0) {
                requirements.add(new RequirementEnergy(IOType.INPUT, energyPerTick));
            }
            // The template's own `requirements`, taken as the file states them: they are already-parsed
            // Requirement VALUES, and re-scaling a value the author wrote is not this method's business.
            requirements.addAll(additionalRequirements);

            // THE NAME IS UPSTREAM'S (U64): "minecraft:smelting_<incId>_<input>_<output>". Registry names
            // stand where upstream interpolated ItemStacks, which is the only substitution available: an
            // ItemStack's `toString` is not a name a recipe can be looked up by.
            out.add(createRecipeShell("smelting_" + incId + "_" + input.description() + "_" + outputId,
                    owningMachine, tickTime, requirements));
            probe.converted++;
            if (probe.firstInputId == null) {
                probe.firstRecipeId = recipe.getId().toString();
                probe.firstInputId = input.description();
                probe.firstOutputId = outputId;
                probe.firstInputAmount = inAmount;
                probe.firstOutputAmount = outAmount;
                probe.firstExperience = recipe.getExperience();
            }
        }
        incId++;

        RecipeAdapters.recordProbe(NAME, probe);
        return out;
    }

    /**
     * Reads the first furnace ingredient without collapsing a tag or an OR ingredient to its first item.
     * Vanilla's JSON representation is stable across Forge 1.20.1: an object has either {@code item} or
     * {@code tag}, while an array is an OR of those objects.  Forge-added ingredient serializers that cannot
     * be represented in the portable requirement model are still reported as skipped. This is the 1.20.1
     * counterpart of upstream's single-stack furnace map: tags stay tags and alternatives stay alternatives.
     */
    @Nullable
    private static InputDescriptor inputDescriptor(SmeltingRecipe recipe) {
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        if (ingredients.isEmpty()) return null;
        Ingredient ingredient = ingredients.get(0);
        Set<String> alternatives = new LinkedHashSet<>();
        collectIngredientIds(ingredient.toJson(), alternatives);
        if (alternatives.isEmpty()) {
            // A custom ingredient may not expose a useful JSON form.  Preserve exact singleton items as a
            // last-resort conversion; an arbitrary multi-item list would silently change OR semantics.
            ItemStack[] items = ingredient.getItems();
            if (items.length == 1 && !items[0].isEmpty()) alternatives.add(idOf(items[0]));
        }
        if (alternatives.isEmpty()) return null;
        return new InputDescriptor(List.copyOf(alternatives), 1);
    }

    private static void collectIngredientIds(com.google.gson.JsonElement element, Set<String> out) {
        if (element == null || element.isJsonNull()) return;
        if (element.isJsonArray()) {
            for (com.google.gson.JsonElement child : element.getAsJsonArray()) collectIngredientIds(child, out);
            return;
        }
        if (!element.isJsonObject()) return;
        var object = element.getAsJsonObject();
        if (object.has("tag") && object.get("tag").isJsonPrimitive()) {
            String tag = object.get("tag").getAsString();
            if (net.minecraft.resources.ResourceLocation.tryParse(tag) != null) out.add("tag:" + tag);
        } else if (object.has("item") && object.get("item").isJsonPrimitive()) {
            String item = object.get("item").getAsString();
            if (net.minecraft.resources.ResourceLocation.tryParse(item) != null) out.add(item);
        }
    }

    private record InputDescriptor(List<String> ids, int amount) {
        Requirement requirement(int scaledAmount) {
            if (ids.size() == 1) return new RequirementItem(IOType.INPUT, ids.get(0), scaledAmount);
            List<RequirementItem> candidates = ids.stream()
                    .map(id -> new RequirementItem(IOType.INPUT, id, scaledAmount)).toList();
            return new RequirementIngredientArray(candidates, 1.0F);
        }

        String description() {
            return ids.size() == 1 ? ids.get(0) : "{" + String.join(", ", ids) + "}";
        }
    }

    private static String idOf(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? "minecraft:air" : id.toString();
    }

    /**
     * What one expansion did, kept for the load-time report and for the self-test.
     *
     * <p>It exists because the two failure modes here are SILENT by nature: a vanilla recipe whose input is a
     * tag converts into nothing, and "the adapter produced fewer recipes than the furnace has" is invisible
     * in the recipe registry -- there is no missing recipe to notice. The counts turn both into numbers that
     * can be asserted.</p>
     */
    public static final class Probe {
        /** How many smelting recipes the recipe manager returned. */
        public final int candidates;
        /** The duration in ticks every generated recipe got, after the duration modifiers. */
        public final int tickTime;
        /** The FE/tick every generated recipe got, after the energy modifiers. */
        public final int energyPerTick;
        /** How many became machine recipes. */
        public int converted;
        /** How many were skipped because their custom ingredient had no portable item/tag representation. */
        public int skippedNoSingleInput;
        /** How many were skipped because their output stack was empty. */
        public int skippedNoOutput;
        /** How many were skipped because a modifier produced a non-finite value. */
        public int skippedNonFiniteModifier;

        /** The first converted recipe's parts, so a check can assert what the conversion produced. */
        public String firstRecipeId;
        public String firstInputId;
        public String firstOutputId;
        public int firstInputAmount;
        public int firstOutputAmount;
        public float firstExperience;

        Probe(int candidates, int tickTime, int energyPerTick) {
            this.candidates = candidates;
            this.tickTime = tickTime;
            this.energyPerTick = energyPerTick;
        }

        @Override
        public String toString() {
            return converted + "/" + candidates + " converted, " + skippedNoSingleInput
                    + " skipped (input is a tag or a multi-item ingredient), " + skippedNoOutput
                    + " skipped (empty output), " + skippedNonFiniteModifier
                    + " skipped (non-finite modifier); duration=" + tickTime + "t, energy="
                    + energyPerTick + " FE/t";
        }
    }
}
