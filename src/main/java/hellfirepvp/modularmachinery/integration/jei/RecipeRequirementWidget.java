/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.jei;

import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import java.util.*;

/** Bounded ingredient grids: all real slots remain indexed, including rows outside the viewport. */
final class RecipeRequirementWidget implements ISlottedRecipeWidget, IJeiGuiEventListener {
    static final int COLUMNS = 3, ROWS = 3, SLOT = 18, TOP = 16, LEFT = 4, RIGHT = 122;
    private final List<IRecipeSlotDrawable> inputs, outputs;
    private final int rows;
    private int inputScroll, outputScroll;

    RecipeRequirementWidget(List<IRecipeSlotDrawable> slots) {
        inputs = slots.stream().filter(s -> s.getRole() == RecipeIngredientRole.INPUT).toList();
        outputs = slots.stream().filter(s -> s.getRole() == RecipeIngredientRole.OUTPUT).toList();
        rows = visibleRows(Math.max(inputs.size(), outputs.size()));
        position();
    }

    static int visibleRows(int count) { return Math.max(1, Math.min(ROWS, (count + COLUMNS - 1) / COLUMNS)); }
    private static int maxScroll(List<?> slots) { return Math.max(0, (slots.size() + COLUMNS - 1) / COLUMNS - ROWS); }
    @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(0, TOP - 1, 180, rows * SLOT + 2); }

    private void position() {
        position(inputs, LEFT, inputScroll);
        position(outputs, RIGHT, outputScroll);
    }
    private static void position(List<IRecipeSlotDrawable> slots, int x, int scroll) {
        for (int i = 0; i < slots.size(); i++) {
            // Hidden slots have no drawing/input path, and stay inside the category for JEI validation.
            int row = i / COLUMNS - scroll;
            slots.get(i).setPosition(x + i % COLUMNS * SLOT, TOP + Math.floorMod(row, ROWS) * SLOT);
        }
    }
    @Override public void drawWidget(GuiGraphics g, double x, double y) {
        draw(g, inputs, LEFT, inputScroll);
        draw(g, outputs, RIGHT, outputScroll);
    }
    private void draw(GuiGraphics g, List<IRecipeSlotDrawable> slots, int x, int scroll) {
        for (int i = scroll * COLUMNS; i < Math.min(slots.size(), (scroll + rows) * COLUMNS); i++) slots.get(i).draw(g, false);
        int max = maxScroll(slots);
        if (max > 0) {
            int bottom = TOP + rows * SLOT;
            g.fill(x + 55, TOP, x + 58, bottom, 0xFF777777);
            int thumb = TOP + (bottom - TOP - 10) * scroll / max;
            g.fill(x + 55, thumb, x + 58, thumb + 10, 0xFFDDDDDD);
        }
    }
    @Override public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double x, double y) {
        Optional<RecipeSlotUnderMouse> slot = find(inputs, inputScroll, x, y);
        return slot.isPresent() ? slot : find(outputs, outputScroll, x, y);
    }
    private Optional<RecipeSlotUnderMouse> find(List<IRecipeSlotDrawable> slots, int scroll, double x, double y) {
        for (int i = scroll * COLUMNS; i < Math.min(slots.size(), (scroll + rows) * COLUMNS); i++) {
            if (slots.get(i).isMouseOver(x, y)) return Optional.of(new RecipeSlotUnderMouse(slots.get(i), getPosition()));
        }
        return Optional.empty();
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (delta == 0 || y < TOP - 1 || y >= TOP + rows * SLOT + 1) return false;
        int step = delta > 0 ? -1 : 1;
        if (x >= LEFT - 1 && x < LEFT + 59 && maxScroll(inputs) > 0)
            inputScroll = Math.max(0, Math.min(maxScroll(inputs), inputScroll + step));
        else if (x >= RIGHT - 1 && x < RIGHT + 59 && maxScroll(outputs) > 0)
            outputScroll = Math.max(0, Math.min(maxScroll(outputs), outputScroll + step));
        else return false;
        position();
        return true;
    }
}
