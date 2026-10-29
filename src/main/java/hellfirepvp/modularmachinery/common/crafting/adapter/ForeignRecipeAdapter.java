/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.crafting.adapter;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

/** Converts audited recipe families, rejecting unsupported semantics instead of silently dropping inputs. */
public abstract class ForeignRecipeAdapter extends RecipeAdapter {
    protected ForeignRecipeAdapter(ResourceLocation id) { super(id); }
    protected abstract boolean accepts(Recipe<?> recipe);
    protected abstract Converted convert(Recipe<?> recipe, List<RecipeModifier> modifiers);
    protected Requirement nativeWithModifiers(Requirement requirement, List<RecipeModifier> modifiers) { return withModifiers(requirement, modifiers); }
    public record Converted(int duration, int energy, List<Requirement> requirements,
                            List<List<FluidStack>> fluidInputs) {}
    @Override public final Collection<MachineRecipe> createRecipesFor(String machine, List<RecipeModifier> modifiers,
                                                                      List<Requirement> additional) {
        return createRecipesFor("direct/" + incId++, machine, modifiers, additional);
    }
    @Override public final Collection<MachineRecipe> createRecipesFor(String templateScope, String machine,
                                                                      List<RecipeModifier> modifiers,
                                                                      List<Requirement> additional) {
        String scope = UUID.nameUUIDFromBytes((machine + "\0" + templateScope)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        var manager = RecipeAdapters.recipeManager();
        if (manager == null) { RecipeAdapters.recordNotExpanded(getRegistryName(), "no recipe manager available"); return List.of(); }
        var result = new ArrayList<MachineRecipe>();
        int skipped = 0, accepted = 0;
        var reasons = new ArrayList<String>();
        for (var source : manager.getRecipes().stream().filter(this::accepts).sorted(Comparator.comparing(r -> r.getId().toString())).toList()) {
            accepted++;
            try {
                for (var modifier : modifiers) if (modifier != null && !Float.isFinite(modifier.getModifier()))
                    throw new IllegalArgumentException("non-finite modifier");
                Converted converted = convert(source, modifiers);
                if (converted.duration <= 0 || converted.energy < 0) throw new IllegalArgumentException("invalid duration/energy");
                List<List<Requirement>> variants = new ArrayList<>(); variants.add(new ArrayList<>(converted.requirements));
                for (var alternatives : converted.fluidInputs) {
                    if (alternatives.isEmpty()) throw new IllegalArgumentException("empty fluid ingredient");
                    if ((long) variants.size() * alternatives.size() > 256) throw new IllegalArgumentException("fluid alternatives exceed 256 variants");
                    var next = new ArrayList<List<Requirement>>();
                    for (var variant : variants) for (var fluid : alternatives) {
                        var copy = new ArrayList<>(variant); copy.add(fluid(IOType.INPUT, fluid)); next.add(copy);
                    }
                    variants = next;
                }
                var duration = durationWithModifiers(converted.duration, modifiers);
                if (duration == null) throw new IllegalArgumentException("non-finite duration modifier");
                // Assemble each source atomically; one unsupported ingredient cannot produce a partial recipe.
                var generated = new ArrayList<MachineRecipe>(); int variantId = 0;
                for (var requirements : variants) {
                    if (converted.energy > 0) requirements.add(new RequirementEnergy(IOType.INPUT, converted.energy));
                    var scaled = new ArrayList<Requirement>();
                    for (var requirement : requirements) scaled.add(nativeWithModifiers(requirement, modifiers));
                    // Template additions are already configured requirements, as in the source furnace adapter.
                    scaled.addAll(additional);
                    var recipe = createRecipeShell(getRegistryName() + "/" + scope + "/" + source.getId() + "/" + variantId++, machine, duration, scaled);
                    generated.add(recipe.withDisplayName(source.getId().toString()));
                }
                result.addAll(generated);
            } catch (IllegalArgumentException failure) {
                skipped++;
                if (reasons.size() < 8) reasons.add(source.getId() + ": " + failure.getMessage());
            }
        }
        hellfirepvp.modularmachinery.Modularmachinery.LOG.info("[MMC][foreign-adapter] {}: {} source, {} generated, {} skipped {}",
                getRegistryName(), accepted, result.size(), skipped, reasons);
        if (skipped > 0) hellfirepvp.modularmachinery.Modularmachinery.LOG.warn("[MMC][foreign-adapter] {}: {} unsupported source recipe(s) skipped: {}", getRegistryName(), skipped, reasons);
        return result;
    }
    private static Requirement withModifiers(Requirement requirement, List<RecipeModifier> modifiers) {
        Requirement result = requirement;
        for (var modifier : modifiers) {
            result = modifier.apply(result);
        }
        return result;
    }
    public static Requirement itemInput(Ingredient ingredient, int count) {
        if (ingredient.isEmpty() || count <= 0) throw new IllegalArgumentException("empty item ingredient");
        var ids = new LinkedHashSet<String>(); collect(ingredient.toJson(), ids);
        if (ids.isEmpty()) throw new IllegalArgumentException("unsupported custom item ingredient");
        var candidates = ids.stream().map(id -> new RequirementItem(IOType.INPUT, id, count)).toList();
        return candidates.size() == 1 ? candidates.get(0) : new RequirementIngredientArray(candidates, 1);
    }
    private static void collect(JsonElement json, Set<String> ids) {
        if (json.isJsonArray()) { for (var entry : json.getAsJsonArray()) collect(entry, ids); return; }
        if (!json.isJsonObject()) throw new IllegalArgumentException("invalid ingredient JSON");
        var obj = json.getAsJsonObject();
        if (obj.has("type") || obj.has("nbt")) throw new IllegalArgumentException("custom/NBT ingredient requires dedicated adapter");
        if (obj.has("tag")) ids.add("tag:" + obj.get("tag").getAsString());
        else if (obj.has("item")) ids.add(obj.get("item").getAsString());
        else throw new IllegalArgumentException("unrecognized ingredient");
    }
    public static RequirementItem item(IOType io, ItemStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("empty item output");
        return new RequirementItem(io, ForgeRegistries.ITEMS.getKey(stack.getItem()).toString(), stack.getCount()).withNbt(nbt(stack.getTag()));
    }
    public static RequirementFluid fluid(IOType io, FluidStack stack) {
        if (stack.isEmpty()) throw new IllegalArgumentException("empty fluid stack");
        return new RequirementFluid(io, ForgeRegistries.FLUIDS.getKey(stack.getFluid()).toString(), stack.getAmount()).withNbt(nbt(stack.getTag()));
    }
    private static RecipeNbt nbt(net.minecraft.nbt.CompoundTag tag) {
        if (tag == null) return RecipeNbt.EMPTY;
        var json = new JsonObject(); json.addProperty("nbt", tag.toString()); return RecipeNbt.read(json);
    }
    public static List<FluidStack> fluids(List<FluidStack> alternatives) {
        var result = new ArrayList<FluidStack>();
        for (var stack : alternatives) if (!stack.isEmpty() && result.stream().noneMatch(s -> s.isFluidEqual(stack) && s.getAmount() == stack.getAmount())) result.add(stack.copy());
        return result;
    }
}
