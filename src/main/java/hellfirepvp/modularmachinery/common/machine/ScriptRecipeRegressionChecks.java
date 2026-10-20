/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.crafting.*;
import hellfirepvp.modularmachinery.common.crafting.requirement.*;
import hellfirepvp.modularmachinery.common.event.recipe.RecipeStartEvent;
import hellfirepvp.modularmachinery.common.modifier.RecipeModifier;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

public final class ScriptRecipeRegressionChecks {
    private ScriptRecipeRegressionChecks() { }
    public static void run() {
        var saved = ScriptRecipeRegistry.snapshots();
        try {
            ScriptRecipeRegistry.clear();
            var input = new RequirementItem(IOType.INPUT, "tag:forge:ingots/iron", 128);
            input.setParallelizeUnaffected(true);
            input.setSelectorTag("input_line");
            var recipe = new MachineRecipe("alloy_furnace", "script_registry_regression", 23,
                    List.of(input), null, true, 1, "core", -10);
            var source = RecipeSnapshotCodec.encode(recipe, Map.of());
            ScriptRecipeRegistry.register(source);
            AtomicInteger starts = new AtomicInteger();
            ScriptRecipeRegistry.registerHandlers(recipe.registryName(), Map.of(
                    RecipeStartEvent.class,
                    List.of((java.util.function.Consumer<RecipeStartEvent>) event -> starts.incrementAndGet())));
            new RecipeStartEvent(null,
                    new hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext(recipe)).postEvent();
            require(starts.get() == 1, "recipe start callback was not dispatched");
            source.addProperty("duration", 999);
            var loader = new RecipeLoader();
            var machines = MachineReloadListener.serverDefinitions().machines();
            loader.load(machines);
            var loaded = loader.getRecipe(recipe.registryName());
            require(loaded != null && loaded.recipeTime() == 23, "script registry did not freeze/merge its declaration");
            require(loaded.requirements().get(0) instanceof RequirementItem item && item.getAmount() == 128
                    && item.getTag() != null && item.isParallelizeUnaffected()
                    && "input_line".equals(item.getSelectorTag()), "script tag/amount/metadata changed after decoding");
            require(loader.installScriptRecipes(machines) == 0, "repeated script merge duplicated a recipe");
            var count = loader.getRecipeNames().size();
            loader.load(machines);
            require(loader.getRecipeNames().size() == count, "script recipe duplicated during reload");
            var client = RecipeLoader.fromSnapshot(loader.snapshot(), machines);
            require(((RequirementItem) client.getRecipe(recipe.registryName()).requirements().get(0))
                    .isParallelizeUnaffected(), "script parallel exemption was lost in client snapshot");

            // Source-style optional catalyst survives the server snapshot and does not block a run when absent.
            var catalyst = new RequirementCatalyst(new RequirementItem(IOType.INPUT, "minecraft:diamond", 1));
            catalyst.addTooltip("catalyst effect");
            catalyst.addModifier(new RecipeModifier("modularmachinery:item", IOType.OUTPUT, 2F,
                    RecipeModifier.OPERATION_MULTIPLY, false));
            var catalystRecipe = new MachineRecipe("alloy_furnace", "script_catalyst_regression", 10,
                    List.of(catalyst), null, true, 1, "", 0);
            var catalystSnapshot = RecipeSnapshotCodec.encode(catalystRecipe, Map.of());
            var catalystDecoded = RecipeSnapshotCodec.decode(catalystSnapshot);
            require(catalystDecoded.requirements().get(0) instanceof RequirementCatalyst decoded
                    && decoded.getModifiers().size() == 1 && decoded.getTooltips().size() == 1
                    && decoded.canStart(null), "optional catalyst snapshot or absent-catalyst path failed");
            boolean duplicate = false;
            try { ScriptRecipeRegistry.register(RecipeSnapshotCodec.encode(recipe, Map.of())); }
            catch (IllegalArgumentException expected) { duplicate = true; }
            require(duplicate, "duplicate script name silently replaced an earlier declaration");
            ScriptRecipeRegistry.clear();
            loader.load(machines);
            require(loader.getRecipe(recipe.registryName()) == null, "deleted script recipe survived reload");
        } finally {
            ScriptRecipeRegistry.clear();
            for (var snapshot : saved) ScriptRecipeRegistry.register(snapshot);
        }
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
