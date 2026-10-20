/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.common.crafting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementType;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.RequirementTypeRegistry;
import hellfirepvp.modularmachinery.common.crafting.requirement.type.Requirements;
import hellfirepvp.modularmachinery.common.machine.IOType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Codec for resolved client display recipes; never executes adapters or accesses local files. */
public final class RecipeSnapshotCodec {
    private RecipeSnapshotCodec() {}

    public static JsonObject encode(MachineRecipe recipe, Map<Requirement, JsonObject> sources) {
        JsonObject json = new JsonObject();
        json.addProperty("machine", recipe.machine());
        json.addProperty("name", recipe.registryName());
        if (!recipe.displayName().isEmpty()) json.addProperty("displayName", recipe.displayName());
        json.addProperty("duration", recipe.recipeTime());
        json.addProperty("cancel", recipe.cancelOnPerTickFailure());
        json.addProperty("maxThreads", recipe.maxThreads());
        json.addProperty("threadName", recipe.threadName());
        json.addProperty("priority", recipe.priority());
        json.addProperty("parallelized", recipe.parallelized());
        json.addProperty("loadJEI", recipe.loadJEI());
        if (!recipe.tooltips().isEmpty()) {
            JsonArray tooltips = new JsonArray();
            recipe.tooltips().forEach(tooltips::add);
            json.add("tooltips", tooltips);
        }
        if (!recipe.commands().isEmpty()) json.add("commands", recipe.commands().toJson());
        if (recipe.adapter() != null) json.addProperty("adapter", recipe.adapter().toString());
        JsonArray requirements = new JsonArray();
        for (Requirement requirement : recipe.requirements()) {
            JsonObject source = sources.get(requirement);
            JsonObject entry = source == null ? new JsonObject() : source.deepCopy();
            entry.addProperty("type", requirement.typeId());
            entry.addProperty("io-type", requirement.getIOType() == IOType.INPUT ? "input" : "output");
            if (requirement instanceof RequirementFuel fuel) {
                entry = new JsonObject();
                entry.addProperty("type", fuel.typeId());
                entry.addProperty("io-type", "input");
                entry.addProperty("item", "any:fuel");
                entry.addProperty("time", fuel.getTime());
                entry.addProperty("chance", fuel.getChance());
                entry.addProperty("parallelizeUnaffected", fuel.isParallelizeUnaffected());
                fuel.getNbt().write(entry);
            } else if (requirement instanceof RequirementItem item) {
                entry.addProperty("item", item.getRawId());
                entry.addProperty("amount", item.getAmount());
                entry.addProperty("chance", item.getChance());
                item.getNbt().write(entry);
                entry.addProperty("consumeDurability", item.getConsumeDurability());
            } else if (requirement instanceof RequirementCatalyst catalyst) {
                entry.remove("amount");
                entry.remove("nbt");
                entry.remove("nbt-display");
                JsonArray items = new JsonArray();
                for (var alternative : catalyst.getCandidates()) {
                    var item = alternative.item();
                    JsonObject candidate = new JsonObject();
                    candidate.addProperty("item", item.getRawId());
                    candidate.addProperty("amount", item.getAmount());
                    candidate.addProperty("minAmount", alternative.minAmount());
                    candidate.addProperty("maxAmount", alternative.maxAmount());
                    candidate.addProperty("chance", alternative.weight());
                    item.getNbt().write(candidate);
                    items.add(candidate);
                }
                entry.add("items", items);
                entry.addProperty("chance", catalyst.getChance());
                entry.addProperty("parallelizeUnaffected", true);
                if (!catalyst.getTooltips().isEmpty()) {
                    JsonArray tips = new JsonArray(); catalyst.getTooltips().forEach(tips::add); entry.add("tooltips", tips);
                }
                if (!catalyst.getModifiers().isEmpty()) {
                    JsonArray mods = new JsonArray();
                    catalyst.getModifiers().forEach(mod -> mods.add(modifierJson(mod)));
                    entry.add("modifiers", mods);
                }
            } else if (requirement instanceof RequirementIngredientArray array) {
                entry.remove("amount");
                entry.remove("nbt");
                entry.remove("nbt-display");
                JsonArray items = new JsonArray();
                for (var alternative : array.getCandidates()) {
                    var item = alternative.item();
                    JsonObject candidate = new JsonObject();
                    candidate.addProperty("item", item.getRawId());
                    candidate.addProperty("amount", item.getAmount());
                    candidate.addProperty("minAmount", alternative.minAmount());
                    candidate.addProperty("maxAmount", alternative.maxAmount());
                    candidate.addProperty("chance", alternative.weight());
                    item.getNbt().write(candidate);
                    items.add(candidate);
                }
                entry.add("items", items);
                entry.addProperty("chance", array.getChance());
                entry.addProperty("parallelizeUnaffected", array.isParallelizeUnaffected());
            } else if (requirement instanceof RequirementFluid fluid) {
                entry.addProperty("fluid", fluid.getRawId());
                entry.addProperty("amount", fluid.getAmount());
                entry.addProperty("chance", fluid.getChance());
                fluid.getNbt().write(entry);
            } else if (requirement instanceof RequirementFluidPerTick rate) {
                entry.addProperty("fluid", rate.fluid().getRawId());
                entry.addProperty("amount", rate.fluid().getAmount());
                entry.remove("chance");
                rate.fluid().getNbt().write(entry);
            } else if (requirement instanceof RequirementEnergy energy) {
                entry.addProperty("energyPerTick", energy.getEnergyPerTick());
            } else if (requirement instanceof RequirementInterfaceNumInput number) {
                entry.addProperty("interface", number.getInterfaceType());
                entry.addProperty("min", number.getMinValue());
                entry.addProperty("max", number.getMaxValue());
            } else if ("modularmachinery:gas_pertick".equals(requirement.typeId())) {
                // Keep Mekanism out of this core codec's linkage path. The type is optional, but a server
                // snapshot still has to carry it when the optional mod is installed.
                try {
                    var cls = requirement.getClass();
                    entry.addProperty("gas", (String) cls.getMethod("getRawId").invoke(requirement));
                    entry.addProperty("amount", ((Number) cls.getMethod("getAmount").invoke(requirement)).intValue());
                    entry.remove("chance");
                    entry.addProperty("parallelizeUnaffected",
                            (Boolean) cls.getMethod("isParallelizeUnaffected").invoke(requirement));
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalArgumentException("Cannot encode gas_pertick snapshot", ex);
                }
            } else if (requirement instanceof BatchIORequirement batch) {
                batch.writeJson(entry);
            } else if (source == null) {
                throw new IllegalArgumentException("No network representation for " + requirement.typeId());
            }
            if (requirement.getSelectorTag() != null) entry.addProperty("selector-tag", requirement.getSelectorTag());
            else entry.remove("selector-tag");
            if (requirement instanceof Requirement.Parallelizable parallel)
                entry.addProperty("parallelizeUnaffected", parallel.isParallelizeUnaffected());
            if (requirement.getTriggerTime() > 0) entry.addProperty("triggerTime", requirement.getTriggerTime());
            if (requirement.isTriggerRepeatable()) entry.addProperty("triggerRepeatable", true);
            if (requirement.isIgnoreOutputCheck()) entry.addProperty("ignoreOutputCheck", true);
            requirements.add(entry);
        }
        json.add("requirements", requirements);
        return json;
    }

    public static MachineRecipe decode(JsonObject json) {
        Requirements.registerBuiltIns();
        var requirements = new ArrayList<Requirement>();
        for (JsonElement element : json.getAsJsonArray("requirements")) {
            JsonObject entry = element.getAsJsonObject();
            String type = entry.get("type").getAsString();
            IOType io = IOType.getByString(entry.get("io-type").getAsString());
            if (io == null) throw new IllegalArgumentException("Invalid requirement direction");
            Requirement requirement;
            // Values are already resolved by the server. Do not reapply JSON authoring limits
            // (e.g. the 64-item clamp), which would truncate scaled adapter outputs.
            if (type.equals("modularmachinery:item") && entry.get("item").getAsString().equalsIgnoreCase("any:fuel")) {
                requirement = RequirementFuel.parse(io, entry, true);
            } else if (type.equals("modularmachinery:item")) {
                RequirementItem item = new RequirementItem(io, entry.get("item").getAsString(),
                        entry.get("amount").getAsInt(), entry.get("chance").getAsFloat());
                item.withNbt(hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.read(entry));
                if (entry.has("consumeDurability")) item.withConsumeDurability(entry.get("consumeDurability").getAsInt());
                requirement = item;
            } else if (type.equals("modularmachinery:ingredient_array_input")) {
                var array = hellfirepvp.modularmachinery.common.crafting.requirement.type.IngredientArrayRequirementType.decode(io, entry, true);
                if (entry.has("parallelizeUnaffected")) array.setParallelizeUnaffected(entry.get("parallelizeUnaffected").getAsBoolean());
                requirement = array;
            } else if (type.equals("modularmachinery:catalyst")) {
                RequirementType registered = RequirementTypeRegistry.get(new ResourceLocation(type));
                if (registered == null) throw new IllegalArgumentException("Unknown server requirement: " + type);
                requirement = registered.createRequirement(io, entry);
            } else if (type.equals("modularmachinery:fluid")) {
                requirement = new RequirementFluid(io, entry.get("fluid").getAsString(),
                        entry.get("amount").getAsInt(), entry.get("chance").getAsFloat())
                        .withNbt(hellfirepvp.modularmachinery.common.util.nbt.RecipeNbt.read(entry));
            } else if (type.equals("modularmachinery:energy")) {
                requirement = new RequirementEnergy(io, entry.get("energyPerTick").getAsInt());
            } else {
                RequirementType registered = RequirementTypeRegistry.get(new ResourceLocation(type));
                if (registered == null) throw new IllegalArgumentException("Unknown server requirement: " + type);
                requirement = registered.createRequirement(io, entry);
            }
            if (requirement == null) throw new IllegalArgumentException("Invalid server requirement: " + type);
            if (requirement instanceof Requirement.Parallelizable parallel && entry.has("parallelizeUnaffected"))
                parallel.setParallelizeUnaffected(entry.get("parallelizeUnaffected").getAsBoolean());
            requirement.setSelectorTag(hellfirepvp.modularmachinery.common.machine.SelectorTags.read(entry));
            if (entry.has("triggerTime")) requirement.setTriggerTime(Math.max(0, entry.get("triggerTime").getAsInt()));
            if (entry.has("triggerRepeatable")) requirement.setTriggerRepeatable(entry.get("triggerRepeatable").getAsBoolean());
            if (entry.has("ignoreOutputCheck")) requirement.setIgnoreOutputCheck(entry.get("ignoreOutputCheck").getAsBoolean());
            requirements.add(requirement);
        }
        List<String> tooltips = new ArrayList<>();
        if (json.has("tooltips") && json.get("tooltips").isJsonArray()) {
            for (JsonElement tip : json.getAsJsonArray("tooltips")) {
                if (tip.isJsonPrimitive() && tip.getAsJsonPrimitive().isString()) tooltips.add(tip.getAsString());
            }
        }
        return new MachineRecipe(json.get("machine").getAsString(), json.get("name").getAsString(),
                json.get("duration").getAsInt(), requirements,
                json.has("adapter") ? new ResourceLocation(json.get("adapter").getAsString()) : null,
                json.get("cancel").getAsBoolean(), json.get("maxThreads").getAsInt(),
                json.get("threadName").getAsString(),
                json.has("priority") ? json.get("priority").getAsInt() : 0,
                json.has("commands") ? hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer
                        .parse(json.getAsJsonObject("commands"))
                        : hellfirepvp.modularmachinery.common.crafting.command.RecipeCommandContainer.empty(),
                tooltips, !json.has("parallelized") || json.get("parallelized").getAsBoolean(),
                !json.has("loadJEI") || json.get("loadJEI").getAsBoolean(),
                json.has("displayName") ? json.get("displayName").getAsString() : "");
    }

    private static JsonObject modifierJson(hellfirepvp.modularmachinery.common.modifier.RecipeModifier modifier) {
        JsonObject json = new JsonObject();
        json.addProperty("target", modifier.getTarget());
        json.addProperty("io", modifier.getIOTarget() == IOType.INPUT ? "input" : "output");
        json.addProperty("multiplier", modifier.getModifier());
        json.addProperty("operation", modifier.getOperation());
        json.addProperty("affectChance", modifier.isChance());
        return json;
    }
}
