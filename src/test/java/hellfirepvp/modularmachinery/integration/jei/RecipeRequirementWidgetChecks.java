/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.recipe.RecipeIngredientRole;
import java.lang.reflect.Proxy;
import java.util.*;

/** Exercises the actual widget input path: scrolling must expose every ingredient without hidden hit targets. */
public final class RecipeRequirementWidgetChecks {
    private static int assertions;
    public static void main(String[] args) {
        net.minecraft.SharedConstants.tryDetectVersion();
        net.minecraft.server.Bootstrap.bootStrap();
        checkPreviewLayout();
        for (int count : new int[]{0, 1, 3, 9, 10, 27, 101}) {
            List<IRecipeSlotDrawable> slots = new ArrayList<>();
            for (int i = 0; i < count; i++) slots.add(slot(RecipeIngredientRole.INPUT));
            for (int i = 0; i < 10; i++) slots.add(slot(RecipeIngredientRole.OUTPUT));
            var widget = new RecipeRequirementWidget(slots);
            Set<IRecipeSlotDrawable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int scroll = 0; scroll < 40; scroll++) {
                for (int row = 0; row < 3; row++) for (int col = 0; col < 3; col++) {
                    var hit = widget.getSlotUnderMouse(5 + col * 18, 17 + row * 18);
                    if (hit.isPresent()) {
                        var candidate = hit.get().slot();
                        require(slots.indexOf(candidate) < count, "output slot leaked into input grid");
                        seen.add(candidate);
                    }
                }
                widget.mouseScrolled(8, 24, -1);
            }
            require(seen.size() == count, "scrolling lost ingredients: " + count + "/" + seen.size());
            require(widget.getSlotUnderMouse(86, 25).isEmpty(), "arrow acquired an ingredient hit target");
            var outputBefore = widget.getSlotUnderMouse(123, 17).orElseThrow().slot();
            require(outputBefore == slots.get(count), "input scrolling changed output viewport");
            require(widget.mouseScrolled(130, 24, -1), "output overflow cannot scroll");
            require(widget.getSlotUnderMouse(123, 53).orElseThrow().slot() == slots.get(count + 9), "last output inaccessible");
            for (int i = 0; i < 40; i++) widget.mouseScrolled(8, 24, 1);
            if (count > 0) require(widget.getSlotUnderMouse(5, 17).orElseThrow().slot() == slots.get(0), "cannot scroll back to first input");
            require(!widget.mouseScrolled(8, 100, -1), "details area consumed grid scrolling");
        }
        require(RecipeRequirementWidget.visibleRows(0) == 1 && RecipeRequirementWidget.visibleRows(1) == 1
                && RecipeRequirementWidget.visibleRows(3) == 1 && RecipeRequirementWidget.visibleRows(4) == 2
                && RecipeRequirementWidget.visibleRows(9) == 3 && RecipeRequirementWidget.visibleRows(100) == 3,
                "small recipes must be compact and large recipes bounded");
        System.out.println("[MMC][jei-layout] PASS: " + assertions + " assertions; empty/small/mixed/101-slot grids, hit targets and independent scrolling");
    }
    private static void checkPreviewLayout() {
        // JEI 15's minimum 175-high frame reserves a 32-pixel header, 8 pixels of padding
        // and a 2-pixel recipe border. Reproduce the screenshot with GUI height 250 as well.
        for (int screenHeight : new int[]{240, 250, 270, 360, 540, 1080}) {
            for (boolean centerSearch : new boolean[]{false, true}) {
                int frame = Math.max(175, screenHeight - (centerSearch ? 76 : 58));
                require(StructurePreviewLayout.HEIGHT + 2 <= frame - 32 - 8,
                        "structure preview exceeds JEI's recipe area at height " + screenHeight);
            }
        }
        List<int[]> slots = new ArrayList<>();
        var builder = (mezz.jei.api.gui.builder.IRecipeLayoutBuilder) Proxy.newProxyInstance(
                RecipeRequirementWidgetChecks.class.getClassLoader(),
                new Class<?>[]{mezz.jei.api.gui.builder.IRecipeLayoutBuilder.class}, (proxy, method, args) -> {
                    if (!method.getName().equals("addSlot")) throw new UnsupportedOperationException(method.getName());
                    slots.add(new int[]{(int) args[1], (int) args[2]});
                    return Proxy.newProxyInstance(RecipeRequirementWidgetChecks.class.getClassLoader(),
                            new Class<?>[]{mezz.jei.api.gui.builder.IRecipeSlotBuilder.class},
                            (slot, call, values) -> slot);
                });
        StructureMaterialWidget.addSlots(builder);
        require(slots.size() == 9 + 1 + 2, "compact preview lost its material/selection slots");
        for (int[] slot : slots) {
            require(slot[0] >= 0 && slot[0] + 16 <= StructurePreviewLayout.WIDTH
                            && slot[1] >= 0 && slot[1] + 16 <= StructurePreviewLayout.HEIGHT,
                    "material icon extends beyond the preview bounds");
        }
        require(StructurePreviewLayout.SCENE_BOTTOM < StructurePreviewLayout.ACTIONS_Y
                        && StructurePreviewLayout.ACTIONS_Y + StructurePreviewLayout.BUTTON_HEIGHT <= StructurePreviewLayout.CONTROLS_Y
                        && StructurePreviewLayout.CONTROLS_Y + StructurePreviewLayout.BUTTON_HEIGHT <= StructurePreviewLayout.SUMMARY_Y,
                "preview controls overlap the scene/material grid");
        require(StructureMaterialWidget.overSummary(10, StructurePreviewLayout.SUMMARY_Y + 1)
                        && !StructureMaterialWidget.overSummary(10, StructurePreviewLayout.SUMMARY_Y + 18)
                        && !StructurePreviewLayout.inScene(90, StructurePreviewLayout.ACTIONS_Y),
                "compact material/scene hit areas still use old coordinates");
    }
    private static IRecipeSlotDrawable slot(RecipeIngredientRole role) {
        int[] pos = new int[2];
        return (IRecipeSlotDrawable) Proxy.newProxyInstance(RecipeRequirementWidgetChecks.class.getClassLoader(),
                new Class<?>[]{IRecipeSlotDrawable.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getRole" -> role;
                    case "setPosition" -> { pos[0] = (int) args[0]; pos[1] = (int) args[1]; yield null; }
                    case "isMouseOver" -> (double) args[0] >= pos[0] && (double) args[0] < pos[0] + 16
                            && (double) args[1] >= pos[1] && (double) args[1] < pos[1] + 16;
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> role.toString();
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
    private static void require(boolean condition, String message) {
        assertions++;
        if (!condition) throw new IllegalStateException(message);
    }
}
