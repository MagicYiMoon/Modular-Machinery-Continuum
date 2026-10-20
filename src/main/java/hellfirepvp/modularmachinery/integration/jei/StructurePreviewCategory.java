/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 * Derived from Modular Machinery CE; upstream HellFirePvP / KasumiNova attribution retained.
 */
package hellfirepvp.modularmachinery.integration.jei;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.preview.StructurePreviewHelper;
import hellfirepvp.modularmachinery.client.preview.BlueprintPreviewDetails;
import hellfirepvp.modularmachinery.client.preview.BlueprintPreviewOverlays;
import hellfirepvp.modularmachinery.client.preview.BlueprintLayerScrollbar;
import hellfirepvp.modularmachinery.common.item.ItemBlueprint;
import hellfirepvp.modularmachinery.common.machine.BlueprintPreviewState;
import hellfirepvp.modularmachinery.common.machine.BlueprintMachineInfo;
import hellfirepvp.modularmachinery.common.machine.BlueprintLayerRange;
import hellfirepvp.modularmachinery.common.machine.BlueprintRayPicker;
import hellfirepvp.modularmachinery.common.registry.RegistryBlocks;
import hellfirepvp.modularmachinery.common.registry.RegistryItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.inputs.IJeiGuiEventListener;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Source StructurePreviewWrapper/preview panel, expressed through JEI 15 extras. */
public final class StructurePreviewCategory implements IRecipeCategory<StructurePreviewEntry> {
    public static final RecipeType<StructurePreviewEntry> TYPE =
            RecipeType.create(Modularmachinery.MODID, "structure_preview", StructurePreviewEntry.class);
    private static final int WIDTH = 180, HEIGHT = 220, CONTROLS_Y = 145;
    private static final int UPGRADE_X = 150, UPGRADE_Y = 24, UPGRADE_ROWS = 5, UPGRADE_ROW_HEIGHT = 19;
    private enum HeaderControl {
        RECENTER(86, "C"), INFO(104, "i");
        final int x;
        final String label;
        HeaderControl(int x, String label) { this.x = x; this.label = label; }
        boolean contains(double x, double y) { return x >= this.x && x < this.x + 16 && y >= 2 && y < 19; }
    }
    private enum Control {
        FORMED(2, 26), CYCLE(30, 26), UPGRADES(58, 16), SHRINK(78, 16), GROW(162, 16);
        final int x, width;
        Control(int x, int width) { this.x = x; this.width = width; }
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= CONTROLS_Y && mouseY < CONTROLS_Y + 17;
        }
        boolean visible(StructurePreviewEntry entry) {
            return switch (this) {
                case FORMED, CYCLE -> true;
                case UPGRADES -> !entry.definition().upgrades().isEmpty();
                case SHRINK, GROW -> !entry.machine().getDynamicPatterns().isEmpty();
            };
        }
        boolean enabled(StructurePreviewEntry entry) {
            return visible(entry) && switch (this) {
                case SHRINK -> entry.controls().dynamicSize() > BlueprintPreviewState.minimumSize(entry.machine());
                case GROW -> entry.controls().dynamicSize() < BlueprintPreviewState.maximumSize(entry.machine());
                default -> true;
            };
        }
    }
    private final IDrawable icon;
    public StructurePreviewCategory(IGuiHelper helper) {
        icon = helper.createDrawableItemStack(new ItemStack(RegistryBlocks.blockController.get()));
    }
    @Override public RecipeType<StructurePreviewEntry> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.modularmachinery.structure_preview"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return WIDTH; }
    @Override public int getHeight() { return HEIGHT; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, StructurePreviewEntry entry, IFocusGroup focuses) {
        ItemStack blueprint = new ItemStack(RegistryItems.BLUEPRINT.get());
        ItemBlueprint.setAssociatedMachine(blueprint, entry.machine());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 124, 2).addItemStack(blueprint);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 142, 2)
                .addItemStack(hellfirepvp.modularmachinery.common.registry.DedicatedControllers.stackOrGeneric(entry.machine().getRegistryName(), false));
        builder.addSlot(RecipeIngredientRole.OUTPUT, 160, 2)
                .addItemStack(hellfirepvp.modularmachinery.common.registry.DedicatedControllers.stackOrGeneric(entry.machine().getRegistryName(), true));
        var materials = builder.addInvisibleIngredients(RecipeIngredientRole.INPUT);
        entry.ingredients().forEach(materials::addItemStack);
        StructureMaterialWidget.addSlots(builder);
    }
    @Override public void draw(StructurePreviewEntry entry, IRecipeSlotsView slots, GuiGraphics g,
                               double mouseX, double mouseY) {
        var mc = Minecraft.getInstance();
        // JEI does not forward release events outside the recipe bounds. Abandon that gesture on return.
        if (entry.interaction.pressed()) {
            entry.interaction.validateGesture(inScene(mouseX, mouseY),
                    GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), entry.interaction.pressedButton()) == GLFW.GLFW_PRESS);
        }
        Component name = Component.translatableWithFallback(entry.machine().getTranslationKey(),
                entry.machine().getLocalizedName());
        g.drawString(mc.font, mc.font.plainSubstrByWidth(name.getString(), 80), 2, 5, 0x404040, false);
        for (HeaderControl control : HeaderControl.values()) button(g, control.x, 2, 16, Component.literal(control.label));
        g.fill(1, 22, 179, 123, 0xFF30343A);
        drawStructure(g, entry, mouseX, mouseY);
        drawUpgrades(g, entry);
        layerScrollbar(entry).draw(g, mouseX, mouseY);
        Component mode = entry.singleLayer ? Component.translatable("jei.modularmachinery.structure_layer",
                entry.layer(), entry.definition().bounds().layerCount())
                : Component.translatable("jei.modularmachinery.structure_all");
        button(g, 2, 125, 88, mode);
        button(g, 92, 125, 86, Component.translatable("jei.modularmachinery.project"));
        drawControls(g, entry);
        Component hint = Component.translatable("jei.modularmachinery.structure_hint");
        g.pose().pushPose();
        g.pose().translate(2, 167, 0);
        float hintScale = Math.min(.7F, 176F / Math.max(1, mc.font.width(hint)));
        g.pose().scale(hintScale, hintScale, 1);
        g.drawString(mc.font, hint, 0, 0, 0x606060, false);
        g.pose().popPose();
    }
    private static void button(GuiGraphics g, int x, int y, int width, Component label) {
        button(g, x, y, width, label, true, false);
    }
    private static void button(GuiGraphics g, int x, int y, int width, Component label, boolean enabled, boolean selected) {
        g.fill(x, y, x + width, y + 17, !enabled ? 0xFF444444 : selected ? 0xFF335B40 : 0xFF777777);
        g.drawCenteredString(Minecraft.getInstance().font, label, x + width / 2, y + 4,
                !enabled ? 0x888888 : selected ? 0xA0FFAF : 0xFFFFFF);
    }
    private static void drawControls(GuiGraphics g, StructurePreviewEntry entry) {
        for (Control control : Control.values()) {
            if (!control.visible(entry)) continue;
            Component label = switch (control) {
                case FORMED -> Component.translatable("jei.modularmachinery.structure_formed");
                case CYCLE -> Component.translatable("jei.modularmachinery.structure_cycle");
                case UPGRADES -> Component.literal("U");
                case SHRINK -> Component.literal("−");
                case GROW -> Component.literal("+");
            };
            boolean selected = control == Control.FORMED && entry.controls().formed()
                    || control == Control.CYCLE && entry.controls().cycling()
                    || control == Control.UPGRADES && entry.showUpgrades;
            button(g, control.x, CONTROLS_Y, control.width, label, control.enabled(entry), selected);
        }
        if (!entry.machine().getDynamicPatterns().isEmpty()) {
            g.pose().pushPose();
            g.pose().translate(125, CONTROLS_Y + 5, 0);
            Component size = Component.translatable("gui.modularmachinery.blueprint.dynamic_size", entry.controls().dynamicSize());
            float scale = Math.min(0.75F, 60F / Math.max(1, Minecraft.getInstance().font.width(size)));
            g.pose().scale(scale, scale, 1);
            g.drawCenteredString(Minecraft.getInstance().font, size, 0, 0, 0x404040);
            g.pose().popPose();
        }
    }
    @Override public List<Component> getTooltipStrings(StructurePreviewEntry entry, IRecipeSlotsView slots, double x, double y) {
        if (x >= 92 && x < 178 && y >= 125 && y < 142) return List.of(
                Component.translatable("gui.modularmachinery.blueprint.project_world"),
                Component.translatable("gui.blueprint.popout.info"));
        if (layerScrollbar(entry).contains(x, y)) return layerScrollbar(entry).tooltip(x, y);
        for (HeaderControl control : HeaderControl.values()) if (control.contains(x, y)) {
            return control == HeaderControl.INFO ? BlueprintMachineInfo.tooltip(entry.machine(), entry.definition())
                    : List.of(Component.translatable("gui.preview.button.reset_center.tip"));
        }
        if (x >= 2 && x < 82 && y >= 2 && y < 19) return List.of(Component.translatableWithFallback(
                entry.machine().getTranslationKey(), entry.machine().getLocalizedName()));
        for (Control control : Control.values()) if (control.visible(entry) && control.contains(x, y)) {
            return List.of(switch (control) {
                case FORMED -> Component.translatable(entry.controls().formed()
                        ? "gui.preview.button.toggle_unformed.tip" : "gui.preview.button.toggle_formed.tip");
                case CYCLE -> Component.translatable(entry.controls().cycling()
                        ? "gui.preview.button.disable_cycle_replaceable_blocks.tip" : "gui.preview.button.enable_cycle_replaceable_blocks.tip.0");
                case UPGRADES -> Component.translatable(entry.showUpgrades
                        ? "gui.preview.button.toggle_upgrade_display.disable.tip" : "gui.preview.button.toggle_upgrade_display.enable.tip");
                case SHRINK -> Component.translatable("gui.preview.button.dynamic_pattern_subtract.tip", entry.controls().dynamicSize());
                case GROW -> Component.translatable("gui.preview.button.dynamic_pattern_plus.tip", entry.controls().dynamicSize());
            });
        }
        if (!entry.machine().getDynamicPatterns().isEmpty() && x >= 94 && x < 160 && y >= CONTROLS_Y && y < CONTROLS_Y + 17) {
            return List.of(Component.translatable("jei.modularmachinery.structure_size_range", entry.controls().dynamicSize(),
                    BlueprintPreviewState.minimumSize(entry.machine()), BlueprintPreviewState.maximumSize(entry.machine())));
        }
        int upgrade = upgradeAt(entry, x, y);
        if (upgrade >= 0) return BlueprintPreviewDetails.upgradeTooltip(entry.definition(), upgrade, entry.interaction.upgradeEnabled(upgrade));
        if (StructureMaterialWidget.overSummary(x, y)) return List.of(
                Component.translatable("jei.modularmachinery.structure_materials_hint"));
        if (StructureMaterialWidget.overSelected(entry, x, y)) return entry.materialTooltip(entry.interaction.selected());
        if (inScene(x, y) && !overScenePanel(entry, x, y) && !entry.interaction.pressed()) {
            var pos = entry.pick(x, y);
            if (pos != null) return entry.materialTooltip(pos);
        }
        return List.of();
    }

    private static boolean inScene(double x, double y) { return x >= 2 && x < 178 && y >= 23 && y < 122; }

    private static boolean overUpgradePanel(StructurePreviewEntry entry, double x, double y) {
        return entry.showUpgrades && !entry.definition().upgrades().isEmpty()
                && x >= UPGRADE_X && x < 178 && y >= UPGRADE_Y && y < 122;
    }

    private static boolean overScenePanel(StructurePreviewEntry entry, double x, double y) {
        return layerScrollbar(entry).contains(x, y) || overUpgradePanel(entry, x, y) || StructureMaterialWidget.overSelected(entry, x, y);
    }

    private static BlueprintLayerScrollbar layerScrollbar(StructurePreviewEntry entry) {
        boolean upgrades = entry.showUpgrades && !entry.definition().upgrades().isEmpty();
        entry.layerScrollbar.setBounds(upgrades ? 138 : 167, UPGRADE_Y, 98);
        return entry.layerScrollbar;
    }

    static int upgradeAt(StructurePreviewEntry entry, double x, double y) {
        if (!overUpgradePanel(entry, x, y) || y < UPGRADE_Y + 1) return -1;
        // The narrow scrollbar is not an ingredient; keyboard lookup must not target it.
        if (x >= 175) return -1;
        clampUpgradeScroll(entry);
        int row = (int) (y - UPGRADE_Y - 1) / UPGRADE_ROW_HEIGHT;
        int index = entry.upgradeScroll + row;
        return row < UPGRADE_ROWS && index < entry.definition().upgrades().size() ? index : -1;
    }

    private static void drawUpgrades(GuiGraphics g, StructurePreviewEntry entry) {
        if (!entry.showUpgrades || entry.definition().upgrades().isEmpty()) return;
        var upgrades = entry.definition().upgrades();
        clampUpgradeScroll(entry);
        g.fill(UPGRADE_X, UPGRADE_Y, 178, 122, 0xE0202328);
        for (int row = 0; row < UPGRADE_ROWS && row + entry.upgradeScroll < upgrades.size(); row++) {
            int index = row + entry.upgradeScroll, y = UPGRADE_Y + 1 + row * UPGRADE_ROW_HEIGHT;
            if (entry.interaction.upgradeEnabled(index)) g.fill(UPGRADE_X + 1, y, 175, y + 18, 0xAA27A345);
            ItemStack icon = BlueprintPreviewDetails.representative(upgrades.get(index));
            if (icon.isEmpty()) g.drawString(Minecraft.getInstance().font, "?", UPGRADE_X + 8, y + 5, 0xFFFFFF, false);
            else g.renderItem(icon, UPGRADE_X + 4, y + 1);
        }
        if (upgrades.size() > UPGRADE_ROWS) {
            int thumb = UPGRADE_Y + 1 + 76 * entry.upgradeScroll / (upgrades.size() - UPGRADE_ROWS);
            g.fill(175, thumb, 178, thumb + 18, 0xFFBBBBBB);
        }
    }

    private static void clampUpgradeScroll(StructurePreviewEntry entry) {
        entry.upgradeScroll = Math.max(0, Math.min(entry.upgradeScroll, entry.definition().upgrades().size() - UPGRADE_ROWS));
    }

    private static void drawStructure(GuiGraphics g, StructurePreviewEntry entry, double mouseX, double mouseY) {
        entry.hovered = null;
        var mc = Minecraft.getInstance();
        var scene = entry.scene();
        if (scene == null) return;
        entry.controls().updateClock(mc.level.getGameTime());
        scene.update(entry.controls().cycleTick(), entry.controls().formed());
        entry.updateMaterials();
        var bounds = entry.definition().bounds();
        float scale = entry.camera.scale(bounds);
        Vector3f center = entry.camera.center(bounds);
        g.flush();
        var pose = g.pose();
        Matrix4f recipeToGui = new Matrix4f(pose.last().pose());
        // GuiGraphics scissor coordinates are absolute; JEI has already translated the recipe pose.
        Vector3f topLeft = pose.last().pose().transformPosition(new Vector3f(2, 23, 0));
        Vector3f bottomRight = pose.last().pose().transformPosition(new Vector3f(178, 122, 0));
        g.enableScissor((int) Math.floor(Math.min(topLeft.x, bottomRight.x)),
                (int) Math.floor(Math.min(topLeft.y, bottomRight.y)),
                (int) Math.ceil(Math.max(topLeft.x, bottomRight.x)),
                (int) Math.ceil(Math.max(topLeft.y, bottomRight.y)));
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        var buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        try {
            pose.translate(90, 74, 100);
            // Flip both Y and depth so the exterior quad winding is preserved.
            pose.scale(scale, -scale, -scale);
            pose.mulPose(entry.camera.rotation());
            pose.translate(-center.x, -center.y, -center.z);
            // JEI input coordinates are recipe-local. Remove its outer translation/scale from the rendered matrix.
            entry.sceneToRecipe = BlueprintRayPicker.relativeTransform(pose.last().pose(), recipeToGui);
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
            scene.render(pose, buffers, !entry.singleLayer, entry.layer(), mc.getFrameTime());
            var visible = new HashSet<>(scene.visiblePositions(!entry.singleLayer, entry.layer()));
            if (inScene(mouseX, mouseY) && !overScenePanel(entry, mouseX, mouseY) && !entry.interaction.pressed()) {
                entry.hovered = entry.pick(mouseX, mouseY);
            }
            for (var pos : entry.interaction.highlightedPositions(entry.definition(), visible)) {
                BlueprintPreviewOverlays.fill(pose, buffers, pos, 0.15F, 0.95F, 0.3F, 0.28F);
            }
            var selected = entry.interaction.selected();
            if (selected != null && visible.contains(selected)) {
                BlueprintPreviewOverlays.fill(pose, buffers, selected, 1, 0.15F, 0.1F, 0.38F);
            }
            if (entry.hovered != null && !entry.hovered.equals(selected)) BlueprintPreviewOverlays.outline(pose, buffers, entry.hovered);
        } finally {
            buffers.endBatch();
            pose.popPose();
            // Clear only this viewport's depth, then restore the surrounding JEI drawing state.
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            g.disableScissor();
            RenderSystem.depthMask(depthWrite);
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            Lighting.setupFor3DItems();
        }
    }
    @Override public void createRecipeExtras(IRecipeExtrasBuilder extras, StructurePreviewEntry entry, IFocusGroup focuses) {
        var materialSlots = extras.getRecipeSlots().getSlots().stream()
                .filter(slot -> slot.getSlotName().orElse("").startsWith(StructureMaterialWidget.PREFIX)).toList();
        var materialWidget = new StructureMaterialWidget(entry, materialSlots);
        extras.addSlottedWidget(materialWidget, materialSlots);
        extras.addGuiEventListener(materialWidget);
        extras.addInputHandler(new StructureUpgradeInputHandler(entry));
        extras.addGuiEventListener(new IJeiGuiEventListener() {
            @Override public ScreenRectangle getArea() { return new ScreenRectangle(0, 0, WIDTH, HEIGHT); }
            @Override public boolean mouseScrolled(double x, double y, double delta) {
                if (delta == 0) return false;
                entry.interaction.cancel();
                if (layerScrollbar(entry).mouseScrolled(x, y, delta)) return true;
                if (overUpgradePanel(entry, x, y)) {
                    entry.upgradeScroll = Math.max(0, Math.min(entry.upgradeScroll + (delta > 0 ? -1 : 1),
                            entry.definition().upgrades().size() - UPGRADE_ROWS));
                    return true;
                }
                if (StructureMaterialWidget.overSelected(entry, x, y) || StructureMaterialWidget.overSummary(x, y)) return false;
                if (y < 22 || y > 142) return false;
                if (entry.singleLayer) entry.setLayer(new BlueprintLayerRange(entry.definition().bounds().minY(),
                        entry.definition().bounds().maxY()).step(entry.layer(), delta > 0 ? 1 : -1));
                else entry.camera.zoomBy(delta);
                return true;
            }
            @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
                if (layerScrollbar(entry).mouseDragged(y, button)) return true;
                if (!entry.interaction.pressed() || button != entry.interaction.pressedButton()) return false;
                entry.interaction.validateGesture(inScene(x, y), true);
                if (!entry.interaction.pressed()) return true;
                entry.interaction.drag(x, y);
                if (button == 0) entry.camera.rotate(dx, dy);
                else entry.camera.pan(dx, dy, entry.definition().bounds());
                return true;
            }
            @Override public boolean mouseClicked(double x, double y, int button) {
                if (button != 0 && button != 1) return false;
                if (entry.interaction.pressed()) return true;
                entry.interaction.cancel();
                if (layerScrollbar(entry).mouseClicked(x, y, button)) return true;
                for (HeaderControl control : HeaderControl.values()) if (control.contains(x, y)) {
                    if (button == 0 && control == HeaderControl.RECENTER) entry.resetCenter();
                    return true;
                }
                for (Control control : Control.values()) if (button == 0 && control.visible(entry) && control.contains(x, y)) {
                    if (!control.enabled(entry)) return true;
                    switch (control) {
                        case FORMED -> entry.controls().toggleFormed();
                        case UPGRADES -> { entry.showUpgrades = !entry.showUpgrades; entry.layerScrollbar.cancel(); }
                        case CYCLE -> {
                            var world = Minecraft.getInstance().level;
                            entry.controls().toggleCycle(world == null ? 0 : world.getGameTime());
                        }
                        case SHRINK -> entry.changeSize(-1);
                        case GROW -> entry.changeSize(1);
                    }
                    return true;
                }
                if (overUpgradePanel(entry, x, y)) {
                    entry.interaction.toggleUpgrade(upgradeAt(entry, x, y), entry.definition());
                    return true;
                }
                if (StructureMaterialWidget.overSelected(entry, x, y) || StructureMaterialWidget.overSummary(x, y)) return false;
                if (inScene(x, y)) { entry.interaction.press(x, y, button); return true; }
                if (button != 0) return false;
                if (y < 125 || y >= 142) return false;
                if (x >= 2 && x < 90) {
                    entry.singleLayer = !entry.singleLayer;
                    entry.layerScrollbar.cancel();
                    if (entry.singleLayer) entry.setLayer(entry.definition().bounds().minY());
                    return true;
                }
                if (x >= 92 && x < 178 && Minecraft.getInstance().player != null) {
                    var mc = Minecraft.getInstance();
                    StructurePreviewHelper.startPreview(entry.machine(), entry.controls().projectionSnapshot(
                            mc.level == null ? 0 : mc.level.getGameTime()));
                    Minecraft.getInstance().setScreen(null);
                    return true;
                }
                return false;
            }
            @Override public boolean mouseReleased(double x, double y, int button) {
                if (layerScrollbar(entry).mouseReleased(button)) return true;
                if (!entry.interaction.pressed() || button != entry.interaction.pressedButton()) return false;
                boolean click = entry.interaction.release(x, y, button);
                if (click && inScene(x, y) && !overScenePanel(entry, x, y)) {
                    entry.select(entry.pick(x, y));
                }
                return true;
            }
        });
    }
}
