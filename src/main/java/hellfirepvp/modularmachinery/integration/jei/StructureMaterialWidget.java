/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE preview ingredient lists and JEI slots.
 */
package hellfirepvp.modularmachinery.integration.jei;

import hellfirepvp.modularmachinery.client.preview.BlueprintMaterialRenderer;
import hellfirepvp.modularmachinery.common.machine.BlueprintMaterialState;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotDrawable;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.inputs.RecipeSlotUnderMouse;
import mezz.jei.api.gui.widgets.ISlottedRecipeWidget;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenPosition;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import static hellfirepvp.modularmachinery.integration.jei.StructurePreviewLayout.*;

/** Native JEI slots provide configured recipe/use/bookmark input for a changing material view. */
final class StructureMaterialWidget implements ISlottedRecipeWidget, IJeiGuiEventListener {
    static final String PREFIX = "preview_material_";
    private static final int SELECTED_X = 5;
    private static final int SUMMARY_SLOTS = BlueprintMaterialState.COLUMNS * SUMMARY_ROWS;
    private static final int SUMMARY_HEIGHT = 18 * SUMMARY_ROWS, SUMMARY_THUMB = 9;
    private static final int SUMMARY_TRAVEL = SUMMARY_HEIGHT - SUMMARY_THUMB;
    private final StructurePreviewEntry entry;
    private final List<IRecipeSlotDrawable> slots;
    private final List<ItemStack> displayed = new ArrayList<>();
    private boolean draggingScrollbar;

    StructureMaterialWidget(StructurePreviewEntry entry, List<IRecipeSlotDrawable> slots) {
        this.entry = entry;
        // Slot names, rather than builder iteration order, define summary/primary/alternative indices.
        this.slots = slots.stream().sorted(Comparator.comparingInt(slot -> Integer.parseInt(
                slot.getSlotName().orElseThrow().substring(PREFIX.length())))).toList();
        for (int i = 0; i < this.slots.size(); i++) displayed.add(null);
    }

    static void addSlots(IRecipeLayoutBuilder builder) {
        var renderer = new MaterialRenderer();
        for (int i = 0; i < SUMMARY_SLOTS + 1 + ALTERNATIVE_ROWS; i++) {
            int x = i < SUMMARY_SLOTS ? 5 + i % BlueprintMaterialState.COLUMNS * 18 : SELECTED_X;
            int y = i < SUMMARY_SLOTS ? SUMMARY_Y + i / BlueprintMaterialState.COLUMNS * 18
                    : i == SUMMARY_SLOTS ? PRIMARY_Y : ALTERNATIVE_Y + (i - SUMMARY_SLOTS - 1) * 18;
            // Display-only slots never replace the complete, size-independent invisible INPUT index.
            builder.addSlot(RecipeIngredientRole.RENDER_ONLY, x + 1, y + 1).setSlotName(PREFIX + i)
                    .setCustomRenderer(VanillaTypes.ITEM_STACK, renderer);
        }
    }

    @Override public ScreenPosition getPosition() { return new ScreenPosition(0, 0); }
    @Override public ScreenRectangle getArea() { return new ScreenRectangle(0, 0, WIDTH, HEIGHT); }

    static boolean overSummary(double x, double y) { return x >= 5 && x < 178 && y >= SUMMARY_Y && y < SUMMARY_Y + SUMMARY_HEIGHT; }
    static boolean overSelected(StructurePreviewEntry entry, double x, double y) {
        return entry.interaction.selected() != null && x >= 3 && x < 30 && y >= UPGRADE_Y && y < SCENE_BOTTOM;
    }

    private void sync() {
        for (int i = 0; i < slots.size(); i++) {
            ItemStack stack = i < SUMMARY_SLOTS ? entry.materials.material(i)
                    : i == SUMMARY_SLOTS ? entry.materials.primary() : entry.materials.alternative(i - SUMMARY_SLOTS - 1);
            if (displayed.get(i) != null && ItemStack.matches(stack, displayed.get(i))) continue;
            var slot = slots.get(i);
            slot.clearDisplayOverrides();
            var override = slot.createDisplayOverrides();
            if (!stack.isEmpty()) override.addItemStack(stack.copy());
            displayed.set(i, stack.copy());
        }
    }

    @Override public void drawWidget(GuiGraphics g, double mouseX, double mouseY) {
        if (draggingScrollbar && GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(),
                GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) draggingScrollbar = false;
        sync();
        var selected = entry.interaction.selected();
        if (selected != null) {
            g.fill(3, UPGRADE_Y, 30, SCENE_BOTTOM, 0xE0202328);
            if (entry.materials.maxAlternativeScroll() > 0) {
                int thumb = ALTERNATIVE_Y + 18 * entry.materials.alternativeScroll() / entry.materials.maxAlternativeScroll();
                g.fill(25, thumb, 28, thumb + 18, 0xFFBBBBBB);
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            if (i >= SUMMARY_SLOTS && selected == null) continue;
            var rect = slots.get(i).getAreaIncludingBackground();
            g.fill(rect.getX() - 1, rect.getY() - 1, rect.getX() + 17, rect.getY() + 17, 0x80404040);
            slots.get(i).draw(g, false);
        }
        g.fill(169, SUMMARY_Y, 177, SUMMARY_Y + SUMMARY_HEIGHT, 0xFF555555);
        int max = entry.materials.maxMaterialScroll();
        int thumb = SUMMARY_Y + (max == 0 ? 0 : SUMMARY_TRAVEL * entry.materials.materialScroll() / max);
        g.fill(169, thumb, 177, thumb + SUMMARY_THUMB, max == 0 ? 0xFF777777 : 0xFFBBBBBB);
    }

    @Override public Optional<RecipeSlotUnderMouse> getSlotUnderMouse(double x, double y) {
        sync();
        for (int i = 0; i < slots.size(); i++) {
            if (i >= SUMMARY_SLOTS && entry.interaction.selected() == null) continue;
            if (!displayed.get(i).isEmpty() && slots.get(i).isMouseOver(x, y)) {
                return Optional.of(new RecipeSlotUnderMouse(slots.get(i), getPosition()));
            }
        }
        return Optional.empty();
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (delta == 0) return false;
        if (overSummary(x, y)) entry.materials.scrollMaterials(delta > 0 ? -1 : 1);
        else if (overSelected(entry, x, y)) entry.materials.scrollAlternatives(delta > 0 ? -1 : 1);
        else return false;
        entry.interaction.cancel();
        sync();
        return true;
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button != 0 || !overSummary(x, y) || x < 169) return false;
        entry.interaction.cancel();
        draggingScrollbar = true;
        scrollTo(y);
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button != 0 || !draggingScrollbar) return false;
        scrollTo(y);
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button != 0 || !draggingScrollbar) return false;
        draggingScrollbar = false;
        return true;
    }
    private void scrollTo(double y) {
        entry.materials.scrollMaterialTo((y - SUMMARY_Y - SUMMARY_THUMB / 2.0) / SUMMARY_TRAVEL);
        sync();
    }

    private static final class MaterialRenderer implements IIngredientRenderer<ItemStack> {
        @Override public void render(GuiGraphics g, ItemStack stack) { BlueprintMaterialRenderer.draw(g, stack, 0, 0); }
        @Override public List<Component> getTooltip(ItemStack stack, TooltipFlag flag) {
            List<Component> lines = new ArrayList<>(stack.getTooltipLines(Minecraft.getInstance().player, flag));
            if (stack.getCount() > 1) lines.add(Component.translatable("jei.modularmachinery.structure_material_count",
                    String.format(Locale.ROOT, "%,d", stack.getCount())));
            return lines;
        }
    }
}
