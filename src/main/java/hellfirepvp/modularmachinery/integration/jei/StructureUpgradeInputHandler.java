/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Adapted from MMCE SlotItemVirtualSelectableJEI.
 */
package hellfirepvp.modularmachinery.integration.jei;

import com.mojang.blaze3d.platform.InputConstants;
import hellfirepvp.modularmachinery.client.preview.BlueprintPreviewDetails;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.inputs.IJeiInputHandler;
import mezz.jei.api.gui.inputs.IJeiUserInput;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import java.util.List;

/** Keyboard-only ingredient lookup preserves the source upgrade slot's mouse highlight toggle. */
final class StructureUpgradeInputHandler implements IJeiInputHandler {
    private final StructurePreviewEntry entry;
    StructureUpgradeInputHandler(StructurePreviewEntry entry) { this.entry = entry; }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(0, 0, 180, 220); }
    @Override public boolean handleInput(double x, double y, IJeiUserInput input) {
        if (input.getKey().getType() == InputConstants.Type.MOUSE) return false;
        int index = StructurePreviewCategory.upgradeAt(entry, x, y);
        var runtime = JeiPluginModularMachinery.currentRuntime();
        if (index < 0 || runtime == null) return false;
        var keys = runtime.getKeyMappings();
        boolean recipe = input.is(keys.getShowRecipe());
        boolean uses = input.is(keys.getShowUses());
        boolean bookmark = input.is(keys.getBookmark());
        if (!recipe && !uses && !bookmark) return false;
        var stack = BlueprintPreviewDetails.representative(entry.definition().upgrades().get(index));
        if (stack.isEmpty()) return false;
        if (input.isSimulate()) return true;
        var snapshot = stack.copyWithCount(1);
        var client = Minecraft.getInstance();
        var screen = client.screen;
        // Finish JEI's input traversal before changing its active recipe layout.
        client.tell(() -> {
            if (client.screen != screen || JeiPluginModularMachinery.currentRuntime() != runtime) return;
            var factory = runtime.getJeiHelpers().getFocusFactory();
            if (recipe) runtime.getRecipesGui().show(factory.createFocus(RecipeIngredientRole.OUTPUT,
                    VanillaTypes.ITEM_STACK, snapshot));
            else if (uses) runtime.getRecipesGui().show(List.of(
                    factory.createFocus(RecipeIngredientRole.INPUT, VanillaTypes.ITEM_STACK, snapshot),
                    factory.createFocus(RecipeIngredientRole.CATALYST, VanillaTypes.ITEM_STACK, snapshot)));
            else runtime.getIngredientManager().createTypedIngredient(VanillaTypes.ITEM_STACK, snapshot)
                    .ifPresent(runtime.getBookmarkManager()::add);
        });
        return true;
    }
}
