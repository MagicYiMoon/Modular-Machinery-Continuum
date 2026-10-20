/* SPDX-License-Identifier: GPL-3.0-only
 * HellFirePvP / Modular Machinery 2019; KasumiNova / MMCE world-scene preview.
 * Modified for Forge 1.20.1, MMC (Modular Machinery: Continuum), 2026-10. Full license: LICENSE.
 */
package hellfirepvp.modularmachinery.client.screen;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.client.preview.BlueprintPreviewScene;
import hellfirepvp.modularmachinery.client.preview.BlueprintPreviewDetails;
import hellfirepvp.modularmachinery.client.preview.BlueprintPreviewOverlays;
import hellfirepvp.modularmachinery.client.preview.BlueprintMaterialRenderer;
import hellfirepvp.modularmachinery.client.preview.BlueprintIngredientLookup;
import hellfirepvp.modularmachinery.client.preview.BlueprintLayerScrollbar;
import hellfirepvp.modularmachinery.client.preview.StructurePreviewHelper;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;
import java.util.*;

/** Source-style blueprint panel: isolated scene, shape ray picking and optional upgrade overlays. */
public class ScreenBlueprint extends Screen {
    private static final int X_SIZE = 184, Y_SIZE = 220;
    private static final ResourceLocation GUI_TEXTURE = new ResourceLocation(Modularmachinery.MODID,
            "textures/gui/guiblueprint_new.png");
    private final DynamicMachine machine;
    private final BlueprintPreviewState controls;
    private final BlueprintMaterialState materials = new BlueprintMaterialState();
    private int left, top, upgradeScroll;
    private Button previousLayer, nextLayer, dynamicSubtract, dynamicPlus, layerMode, formedMode, upgradeMode, cycleMode;
    private BlueprintPreviewScene scene;
    private final BlueprintPreviewInteraction interaction = new BlueprintPreviewInteraction();
    private final BlueprintPreviewCamera camera = new BlueprintPreviewCamera(96);
    private BlockPos hovered;
    private Matrix4f sceneToGui;
    private boolean allLayers = true, showUpgrades = true;
    private boolean draggingMaterialScroll;
    private final BlueprintLayerScrollbar layerScrollbar = new BlueprintLayerScrollbar(() -> scene != null && !allLayers,
            this::minLayer, this::maxLayer, this::layer, this::setLayer);

    /** Shared hit areas for tooltips, mouse lookup and JEI's configured key bindings. */
    public record IngredientSlot(ItemStack stack, Rect2i area, boolean mouseLookup) { }
    public int getGuiLeft() { return left; }
    public int getGuiTop() { return top; }
    public int getGuiWidth() { return X_SIZE; }
    public int getGuiHeight() { return Y_SIZE; }

    public ScreenBlueprint(DynamicMachine machine) {
        super(Component.translatable("gui.modularmachinery.blueprint"));
        this.machine = machine;
        var loader = MachineReloadListener.getLoader();
        controls = machine == null ? null : new BlueprintPreviewState(machine, loader.getVariables(), loader.getResolver());
    }

    @Override protected void init() {
        cancelGestures();
        left = (width - X_SIZE) / 2;
        top = (height - Y_SIZE) / 2;
        if (scene == null && machine != null) rebuildScene();
        previousLayer = button("<", 8, 158, 16, () -> changeLayer(-1), "gui.preview.button.layer_render_scrollbar.down.tip");
        nextLayer = button(">", 26, 158, 16, () -> changeLayer(1), "gui.preview.button.layer_render_scrollbar.up.tip");
        layerMode = button("3D", 44, 158, 24, () -> {
            allLayers = !allLayers;
            layerScrollbar.cancel();
            if (!allLayers) setLayer(minLayer());
            updateButtons();
        },
                "gui.preview.button.toggle_layer_render.tip");
        formedMode = button("F", 70, 158, 16, this::toggleFormed,
                "gui.preview.button.toggle_formed.tip");
        upgradeMode = button("U", 88, 158, 16, () -> { showUpgrades = !showUpgrades; updateButtons(); },
                "gui.preview.button.toggle_upgrade_display.enable.tip");
        cycleMode = button("C", 106, 158, 16, this::toggleCycle,
                "gui.preview.button.disable_cycle_replaceable_blocks.tip");
        button("R", 124, 158, 16, this::resetView, "gui.preview.button.reset_center.tip");
        dynamicSubtract = button("−", 136, 28, 18, () -> changeSize(-1), "gui.preview.button.dynamic_pattern_subtract.tip");
        dynamicPlus = button("+", 156, 28, 18, () -> changeSize(1), "gui.preview.button.dynamic_pattern_plus.tip");
        Button project = button("P", 142, 158, 16, this::projectToWorld,
                "gui.modularmachinery.blueprint.project_world");
        project.setTooltip(Tooltip.create(Component.translatable("gui.modularmachinery.blueprint.project_world")
                .append("\n").append(Component.translatable("gui.blueprint.popout.info"))));
        project.active = machine != null;
        updateButtons();
    }

    private Button button(String label, int x, int y, int width, Runnable action, String tooltip) {
        Button button = Button.builder(Component.literal(label), b -> action.run())
                .bounds(left + x, top + y, width, 16).build();
        button.setTooltip(Tooltip.create(Component.translatable(tooltip)));
        addRenderableWidget(button);
        return button;
    }

    private void rebuildScene() {
        cancelGestures();
        if (scene != null) scene.close();
        scene = new BlueprintPreviewScene(machine, controls.definition());
        controls.updateClock(worldTick());
        scene.update(controls.cycleTick(), formed());
        interaction.retain(scene.definition());
        materials.retain(scene.definition());
        materials.update(scene);
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        long window = minecraft.getWindow().getWindow();
        if (interaction.pressed()) interaction.validateGesture(inScene(mouseX, mouseY),
                GLFW.glfwGetMouseButton(window, interaction.pressedButton()) == GLFW.GLFW_PRESS);
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) draggingMaterialScroll = false;
        renderBackground(graphics);
        graphics.blit(GUI_TEXTURE, left, top, 0, 0, X_SIZE, Y_SIZE, 256, 256);
        Component name = machine == null ? Component.translatable("gui.modularmachinery.blueprint.none")
                : Component.translatable(machine.getTranslationKey());
        graphics.drawString(font, font.plainSubstrByWidth(name.getString(), 168), left + 8, top + 8, 0x202020, false);
        hovered = null;
        if (scene != null) {
            controls.updateClock(worldTick());
            scene.update(controls.cycleTick(), formed());
            materials.update(scene);
            drawScene(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(font, allLayers ? Component.translatable("gui.modularmachinery.blueprint.all_layers")
                    : Component.literal("Y=" + layer() + " / " + maxLayer()), left + 10, top + 29, 0xEEEEEE, false);
            graphics.drawString(font, Component.translatable(formed() ? "gui.modularmachinery.blueprint.formed"
                    : "gui.modularmachinery.blueprint.unformed"), left + 10, top + 40, formed() ? 0x80E69B : 0xDDDDDD, false);
            if (!machine.getDynamicPatterns().isEmpty()) graphics.drawString(font,
                    Component.translatable("gui.modularmachinery.blueprint.dynamic_size", dynamicSize()),
                    left + 10, top + 143, 0xEEEEEE, false);
            if (showUpgrades && !scene.definition().upgrades().isEmpty()) drawUpgrades(graphics);
            layerScrollbar().draw(graphics, mouseX, mouseY);
            drawMaterials(graphics);
            graphics.drawCenteredString(font, "i", left + 168, top + 162, 0x404040);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (scene == null) return;
        if (layerScrollbar().contains(mouseX, mouseY)) {
            tooltip(graphics, layerScrollbar.tooltip(mouseX, mouseY), mouseX, mouseY);
            return;
        }
        int upgrade = upgradeAt(mouseX, mouseY);
        if (upgrade >= 0) tooltip(graphics, upgradeTooltip(upgrade), mouseX, mouseY);
        else if (showUpgrades && scene.definition().upgrades().isEmpty() && overUpgradePanel(mouseX, mouseY)) {
            tooltip(graphics, List.of(Component.translatable("gui.modularmachinery.blueprint.no_upgrades")), mouseX, mouseY);
        } else if (ingredientAt(mouseX, mouseY) != null) {
            ItemStack stack = ingredientAt(mouseX, mouseY).stack();
            if (stack.getCount() < 1000) graphics.renderTooltip(font, stack, mouseX, mouseY);
            else {
                List<Component> lines = new ArrayList<>(getTooltipFromItem(minecraft, stack));
                lines.add(Component.literal(String.format(Locale.ROOT, "%,d", stack.getCount())));
                graphics.renderTooltip(font, lines, stack.getTooltipImage(), mouseX, mouseY);
            }
        } else if (hovered != null && !interaction.pressed()) tooltip(graphics, materialTooltip(hovered), mouseX, mouseY);
        else if (mouseX >= left + 160 && mouseX < left + 176 && mouseY >= top + 158 && mouseY < top + 174) {
            tooltip(graphics, machineTooltip(), mouseX, mouseY);
        } else if (mouseX >= left + 8 && mouseX < left + 176 && mouseY >= top + 7 && mouseY < top + 20) {
            graphics.renderTooltip(font, name, mouseX, mouseY);
        }
    }

    private void drawScene(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.flush();
        graphics.fill(left + 6, top + 26, left + 178, top + 154, 0xFF30343A);
        graphics.flush();
        graphics.enableScissor(left + 7, top + 27, left + 177, top + 153);
        var pose = graphics.pose();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        var buffers = minecraft.renderBuffers().bufferSource();
        pose.pushPose();
        try {
            var bounds = scene.definition().bounds();
            float scale = camera.scale(bounds);
            var center = camera.center(bounds);
            pose.translate(left + 92, top + 92, 100);
            pose.scale(scale, -scale, -scale);
            pose.mulPose(camera.rotation());
            pose.translate(-center.x, -center.y, -center.z);
            sceneToGui = new Matrix4f(pose.last().pose());
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.enableDepthTest();
            Lighting.setupFor3DItems();
            scene.render(pose, buffers, allLayers, layer(), partialTick);
            Set<BlockPos> visible = new HashSet<>(scene.visiblePositions(allLayers, layer()));
            if (inScene(mouseX, mouseY) && !overSceneControl(mouseX, mouseY) && !interaction.pressed()) {
                hovered = BlueprintRayPicker.pick(sceneToGui, mouseX, mouseY, scene, visible);
            }
            for (BlockPos pos : interaction.highlightedPositions(scene.definition(), visible)) {
                overlay(graphics, pos, 0.15F, 0.95F, 0.3F, 0.28F);
            }
            var selected = interaction.selected();
            if (selected != null && visible.contains(selected)) overlay(graphics, selected, 1, 0.15F, 0.1F, 0.38F);
            if (hovered != null && !hovered.equals(selected)) {
                BlueprintPreviewOverlays.outline(pose, buffers, hovered);
            }
        } finally {
            buffers.endBatch();
            pose.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            graphics.disableScissor();
            RenderSystem.depthMask(depthWrite);
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            Lighting.setupFor3DItems();
        }
    }

    private void overlay(GuiGraphics graphics, BlockPos pos, float r, float g, float b, float alpha) {
        BlueprintPreviewOverlays.fill(graphics.pose(), minecraft.renderBuffers().bufferSource(), pos, r, g, b, alpha);
    }

    private void drawUpgrades(GuiGraphics graphics) {
        graphics.fill(left + 149, top + 50, left + 175, top + 151, 0xD0202328);
        List<BlueprintSceneDefinition.Upgrade> upgrades = scene.definition().upgrades();
        for (int row = 0; row < 5 && upgradeScroll + row < upgrades.size(); row++) {
            int index = upgradeScroll + row, y = top + 51 + row * 20;
            if (interaction.upgradeEnabled(index)) graphics.fill(left + 151, y, left + 173, y + 19, 0xAA27A345);
            ItemStack stack = representative(upgrades.get(index));
            if (stack.isEmpty()) graphics.drawString(font, "?", left + 158, y + 5, 0xFFFFFF, false);
            else graphics.renderItem(stack, left + 154, y + 1);
        }
        if (upgrades.isEmpty()) graphics.drawString(font, "—", left + 157, top + 54, 0xDDDDDD, false);
        if (upgrades.size() > 5) {
            int thumb = top + 51 + 80 * upgradeScroll / Math.max(1, upgrades.size() - 5);
            graphics.fill(left + 173, thumb, left + 175, thumb + 18, 0xFFBBBBBB);
        }
    }

    private void drawMaterials(GuiGraphics graphics) {
        var selected = interaction.selected();
        Component label = selected == null ? Component.translatable("gui.modularmachinery.blueprint.pick_hint")
                : Component.literal("(" + selected.getX() + ", " + selected.getY() + ", " + selected.getZ() + ")");
        graphics.drawString(font, font.plainSubstrByWidth(label.getString(), 128), left + 10, top + 51, 0xEEEEEE, false);
        if (selected != null) {
            graphics.fill(left + 9, top + 61, left + 35, top + 140, 0xD0202328);
            drawMaterial(graphics, materials.primary(), 10, 62);
            for (int row = 0; row < BlueprintMaterialState.ALTERNATIVE_ROWS; row++) {
                ItemStack stack = materials.alternative(row);
                if (!stack.isEmpty()) drawMaterial(graphics, stack, 10, 84 + row * 18);
            }
            if (materials.maxAlternativeScroll() > 0) {
                int thumb = top + 84 + 40 * materials.alternativeScroll() / materials.maxAlternativeScroll();
                graphics.fill(left + 31, thumb, left + 34, thumb + 12, 0xFFBBBBBB);
            }
        }
        for (int row = 0; row < 2; row++) for (int column = 0; column < 9; column++) {
            drawMaterial(graphics, materials.material(row * 9 + column),
                    5 + column * 18, 179 + row * 18);
        }
        graphics.fill(left + 168, top + 179, left + 176, top + 215, 0xFF555555);
        int thumb = top + 179 + (materials.maxMaterialScroll() == 0 ? 0 : 23 * materials.materialScroll() / materials.maxMaterialScroll());
        graphics.fill(left + 168, thumb, left + 176, thumb + 13,
                materials.maxMaterialScroll() == 0 ? 0xFF777777 : 0xFFBBBBBB);
    }

    private void drawMaterial(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.fill(left + x, top + y, left + x + 18, top + y + 18, 0x80404040);
        BlueprintMaterialRenderer.draw(graphics, stack, left + x + 1, top + y + 1);
    }

    private void selectBlock(BlockPos pos) {
        interaction.select(pos, scene.definition());
        materials.select(scene, interaction.selected());
    }

    private boolean overMaterials(double x, double y) {
        return x >= left + 5 && x < left + 177 && y >= top + 179 && y < top + 215;
    }
    private boolean overSelectedMaterials(double x, double y) {
        return interaction.selected() != null && x >= left + 9 && x < left + 35 && y >= top + 61 && y < top + 140;
    }
    public IngredientSlot ingredientAt(double x, double y) {
        if (scene == null) return null;
        if (overMaterials(x, y) && x < left + 167) {
            int column = (int) (x - left - 5) / 18, row = (int) (y - top - 179) / 18;
            ItemStack stack = materials.material(row * 9 + column);
            if (!stack.isEmpty()) return new IngredientSlot(stack,
                    new Rect2i(left + 5 + column * 18, top + 179 + row * 18, 18, 18), true);
        }
        if (overSelectedMaterials(x, y) && x >= left + 10 && x < left + 28) {
            ItemStack primary = materials.primary();
            if (y >= top + 62 && y < top + 80 && !primary.isEmpty()) return new IngredientSlot(primary,
                    new Rect2i(left + 10, top + 62, 18, 18), true);
            int row = (int) (y - top - 84) / 18;
            ItemStack alternative = materials.alternative(row);
            if (y >= top + 84 && y < top + 138 && !alternative.isEmpty()) {
                return new IngredientSlot(alternative,
                        new Rect2i(left + 10, top + 84 + row * 18, 18, 18), true);
            }
        }
        int upgrade = upgradeAt(x, y);
        if (upgrade >= 0) {
            ItemStack stack = representative(scene.definition().upgrades().get(upgrade));
            if (!stack.isEmpty()) return new IngredientSlot(stack,
                    new Rect2i(left + 149, top + 51 + (upgrade - upgradeScroll) * 20, 26, 19), false);
        }
        return null;
    }

    private List<Component> materialTooltip(BlockPos pos) {
        return BlueprintPreviewDetails.materialTooltip(scene, pos);
    }

    private ItemStack representative(BlueprintSceneDefinition.Upgrade upgrade) {
        return BlueprintPreviewDetails.representative(upgrade);
    }

    private List<Component> upgradeTooltip(int index) {
        return BlueprintPreviewDetails.upgradeTooltip(scene.definition(), index, interaction.upgradeEnabled(index));
    }

    private List<Component> machineTooltip() {
        List<Component> lines = new ArrayList<>(BlueprintMachineInfo.tooltip(machine, scene.definition()));
        lines.add(Component.translatable("gui.modularmachinery.blueprint.layer", layer(), new BlueprintLayerRange(minLayer(), maxLayer()).count()));
        lines.add(Component.translatable("gui.modularmachinery.blueprint.controls"));
        lines.add(Component.translatable("gui.modularmachinery.blueprint.materials_hint"));
        if (BlueprintIngredientLookup.available()) lines.add(Component.translatable("gui.modularmachinery.blueprint.jei_hint"));
        return lines;
    }

    private void tooltip(GuiGraphics graphics, List<Component> lines, int mouseX, int mouseY) {
        int maxWidth = Math.max(100, Math.min(280, width - 24));
        var wrapped = lines.stream().flatMap(line -> font.split(line, maxWidth).stream()).toList();
        graphics.renderTooltip(font, wrapped, mouseX, mouseY);
    }

    private void updateButtons() {
        if (previousLayer == null) return;
        previousLayer.active = machine != null && !allLayers && layer() > minLayer();
        nextLayer.active = machine != null && !allLayers && layer() < maxLayer();
        layerMode.active = formedMode.active = cycleMode.active = upgradeMode.active = machine != null;
        dynamicSubtract.visible = dynamicPlus.visible = machine != null && !machine.getDynamicPatterns().isEmpty();
        dynamicSubtract.active = dynamicSize() > minimumSize();
        dynamicPlus.active = dynamicSize() < maximumSize();
        dynamicPlus.setTooltip(Tooltip.create(Component.translatable("gui.preview.button.dynamic_pattern_plus.tip", dynamicSize())));
        dynamicSubtract.setTooltip(Tooltip.create(Component.translatable("gui.preview.button.dynamic_pattern_subtract.tip", dynamicSize())));
        layerMode.setMessage(Component.literal(allLayers ? "Y" : "3D"));
        layerMode.setTooltip(Tooltip.create(Component.translatable(allLayers ? "gui.preview.button.toggle_layer_render.tip"
                : "gui.preview.button.toggle_3d_render.tip")));
        formedMode.setMessage(Component.literal("F").withStyle(formed() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        formedMode.setTooltip(Tooltip.create(Component.translatable(formed() ? "gui.preview.button.toggle_unformed.tip"
                : "gui.preview.button.toggle_formed.tip")));
        upgradeMode.setTooltip(Tooltip.create(Component.translatable(showUpgrades
                ? "gui.preview.button.toggle_upgrade_display.disable.tip" : "gui.preview.button.toggle_upgrade_display.enable.tip")));
        upgradeMode.setMessage(Component.literal("U").withStyle(showUpgrades ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        cycleMode.setMessage(Component.literal("C").withStyle(cycling() ? ChatFormatting.GREEN : ChatFormatting.WHITE));
        cycleMode.setTooltip(Tooltip.create(Component.translatable(cycling() ? "gui.preview.button.disable_cycle_replaceable_blocks.tip"
                : "gui.preview.button.enable_cycle_replaceable_blocks.tip.0")));
    }

    private int maximumSize() {
        return BlueprintPreviewState.maximumSize(machine);
    }
    private int minimumSize() {
        return BlueprintPreviewState.minimumSize(machine);
    }
    private int minLayer() { return controls == null ? 0 : controls.definition().bounds().minY(); }
    private int maxLayer() { return controls == null ? 0 : controls.definition().bounds().maxY(); }
    private int layer() { return controls == null ? 0 : controls.layer(); }
    private int dynamicSize() { return controls == null ? 0 : controls.dynamicSize(); }
    private boolean formed() { return controls != null && controls.formed(); }
    private boolean cycling() { return controls == null || controls.cycling(); }
    private long worldTick() { return minecraft.level == null ? 0 : minecraft.level.getGameTime(); }
    private void toggleFormed() { if (controls != null) { controls.toggleFormed(); updateButtons(); } }
    private void toggleCycle() { if (controls != null) { controls.toggleCycle(worldTick()); updateButtons(); } }
    private void setLayer(int value) {
        if (controls != null) controls.setLayer(value);
        interaction.cancel();
        hovered = null;
        updateButtons();
    }
    private void changeLayer(int delta) { setLayer(new BlueprintLayerRange(minLayer(), maxLayer()).step(layer(), delta)); }
    private void changeSize(int delta) {
        if (controls != null && controls.changeSize(delta)) { rebuildScene(); updateButtons(); }
    }
    private void resetView() {
        camera.resetCenter();
        cancelGestures();
    }
    private void cancelGestures() {
        interaction.cancel();
        layerScrollbar.cancel();
        draggingMaterialScroll = false;
        hovered = null;
        sceneToGui = null;
        setDragging(false);
    }
    @Override public void removed() {
        cancelGestures();
        super.removed();
    }
    private void projectToWorld() {
        if (machine != null && minecraft.player != null) {
            StructurePreviewHelper.startPreview(machine, controls.projectionSnapshot(worldTick()));
            materials.invalidate();
            if (scene != null) { scene.close(); scene = null; }
            minecraft.setScreen(null);
        }
    }
    @Override public void onClose() {
        cancelGestures();
        materials.invalidate();
        if (scene != null) { scene.close(); scene = null; }
        super.onClose();
    }
    private boolean inScene(double x, double y) { return x >= left + 7 && x < left + 177 && y >= top + 27 && y < top + 153; }
    private boolean overUpgradePanel(double x, double y) {
        return showUpgrades && scene != null && !scene.definition().upgrades().isEmpty()
                && x >= left + 149 && x < left + 175 && y >= top + 50 && y < top + 151;
    }
    private boolean overSceneControl(double x, double y) {
        return layerScrollbar().contains(x, y) || overSelectedMaterials(x, y) || overUpgradePanel(x, y) || dynamicPlus != null && dynamicPlus.visible
                && x >= left + 136 && x < left + 174 && y >= top + 28 && y < top + 44;
    }
    private BlueprintLayerScrollbar layerScrollbar() {
        boolean upgrades = showUpgrades && scene != null && !scene.definition().upgrades().isEmpty();
        layerScrollbar.setBounds(left + (upgrades ? 137 : 165), top + 50, 101);
        return layerScrollbar;
    }
    private int upgradeAt(double x, double y) {
        if (!overUpgradePanel(x, y) || scene == null || y < top + 51) return -1;
        int row = (int) (y - top - 51) / 20, index = upgradeScroll + row;
        return row < 5 && index < scene.definition().upgrades().size() ? index : -1;
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (delta == 0) return false;
        interaction.cancel();
        if (layerScrollbar().mouseScrolled(x, y, delta)) return true;
        if (overMaterials(x, y)) {
            materials.scrollMaterials(delta > 0 ? -1 : 1);
            return true;
        }
        if (overSelectedMaterials(x, y)) {
            materials.scrollAlternatives(delta > 0 ? -1 : 1);
            return true;
        }
        if (scene != null && overUpgradePanel(x, y)) {
            upgradeScroll = Mth.clamp(upgradeScroll + (delta > 0 ? -1 : 1), 0,
                    Math.max(0, scene.definition().upgrades().size() - 5));
            return true;
        }
        if (scene != null && inScene(x, y) && !overSceneControl(x, y)) { camera.zoomBy(delta); return true; }
        if (x >= left && x < left + X_SIZE && y >= top && y < top + Y_SIZE && !allLayers) {
            changeLayer(delta > 0 ? 1 : -1); return true;
        }
        return super.mouseScrolled(x, y, delta);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (interaction.pressed() && (button == 0 || button == 1)) return true;
        if (layerScrollbar().mouseClicked(x, y, button)) { interaction.cancel(); return true; }
        if (button == 0 && overMaterials(x, y) && x >= left + 168) {
            draggingMaterialScroll = true;
            scrollMaterialTo(y);
            return true;
        }
        IngredientSlot ingredient = ingredientAt(x, y);
        if (ingredient != null && ingredient.mouseLookup() && (button == 0 || button == 1)) {
            BlueprintIngredientLookup.open(ingredient.stack(), button);
            return true;
        }
        if (scene != null && (button == 0 || button == 1) && overUpgradePanel(x, y)) {
            interaction.toggleUpgrade(upgradeAt(x, y), scene.definition());
            return true;
        }
        if (scene != null && (button == 0 || button == 1) && inScene(x, y) && !overSceneControl(x, y)) {
            interaction.press(x, y, button);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (layerScrollbar().mouseDragged(y, button)) return true;
        if (button == 0 && draggingMaterialScroll) { scrollMaterialTo(y); return true; }
        if (interaction.pressed() && button == interaction.pressedButton()) {
            interaction.validateGesture(inScene(x, y), true);
            if (!interaction.pressed()) return true;
            interaction.drag(x, y);
            if (button == 0) camera.rotate(dx, dy);
            else camera.pan(dx, dy, scene.definition().bounds());
            return true;
        }
        return super.mouseDragged(x, y, button, dx, dy);
    }

    @Override public boolean mouseReleased(double x, double y, int button) {
        if (layerScrollbar().mouseReleased(button)) return true;
        if (button == 0 && draggingMaterialScroll) { draggingMaterialScroll = false; return true; }
        if (interaction.pressed() && button == interaction.pressedButton()) {
            if (interaction.release(x, y, button) && inScene(x, y) && !overSceneControl(x, y) && sceneToGui != null) {
                selectBlock(BlueprintRayPicker.pick(sceneToGui, x, y, scene, scene.visiblePositions(allLayers, layer())));
            }
            return true;
        }
        return super.mouseReleased(x, y, button);
    }

    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        double mouseX = minecraft.mouseHandler.xpos() * width / minecraft.getWindow().getScreenWidth();
        double mouseY = minecraft.mouseHandler.ypos() * height / minecraft.getWindow().getScreenHeight();
        // Let JEI process its configured recipe/use/bookmark keys without R/U resetting this panel.
        if (BlueprintIngredientLookup.available() && ingredientAt(mouseX, mouseY) != null) {
            return super.keyPressed(key, scanCode, modifiers);
        }
        if (scene != null && !hasControlDown() && !hasAltDown()) {
            switch (key) {
                case org.lwjgl.glfw.GLFW.GLFW_KEY_F -> { toggleFormed(); return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_U -> { showUpgrades = !showUpgrades; updateButtons(); return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_C -> { toggleCycle(); return true; }
                case org.lwjgl.glfw.GLFW.GLFW_KEY_R -> { resetView(); return true; }
            }
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    private void scrollMaterialTo(double y) {
        materials.scrollMaterialTo((y - top - 179 - 6.5) / 23);
    }
}
