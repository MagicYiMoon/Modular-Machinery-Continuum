/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 * Derived from Modular Machinery CE; upstream HellFirePvP / KasumiNova attribution retained.
 */
package hellfirepvp.modularmachinery.integration.jei;

import hellfirepvp.modularmachinery.client.preview.BlueprintPreviewScene;
import hellfirepvp.modularmachinery.client.preview.BlueprintPreviewDetails;
import hellfirepvp.modularmachinery.client.preview.BlueprintLayerScrollbar;
import hellfirepvp.modularmachinery.common.machine.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import java.lang.ref.WeakReference;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/** Per-machine JEI preview state; rebuilt when the server synchronises machine definitions. */
public final class StructurePreviewEntry implements AutoCloseable {
    private final DynamicMachine machine;
    private final BlueprintPreviewState controls;
    private final List<ItemStack> ingredients;
    private BlueprintPreviewScene scene;
    private WeakReference<ClientLevel> sceneWorld = new WeakReference<>(null);
    final BlueprintPreviewInteraction interaction = new BlueprintPreviewInteraction();
    final BlueprintPreviewCamera camera = new BlueprintPreviewCamera(StructurePreviewLayout.CAMERA_SPAN);
    final BlueprintMaterialState materials = new BlueprintMaterialState(
            StructurePreviewLayout.SUMMARY_ROWS, StructurePreviewLayout.ALTERNATIVE_ROWS);
    Matrix4f sceneToRecipe;
    BlockPos hovered;
    boolean showUpgrades = true;
    int upgradeScroll;
    boolean singleLayer;
    final BlueprintLayerScrollbar layerScrollbar = new BlueprintLayerScrollbar(() -> singleLayer,
            () -> definition().bounds().minY(), () -> definition().bounds().maxY(), this::layer, this::setLayer);
    public StructurePreviewEntry(DynamicMachine machine) {
        this.machine = machine;
        var loader = MachineReloadListener.getLoader();
        this.controls = new BlueprintPreviewState(machine, loader.getVariables(), loader.getResolver());
        this.ingredients = BlueprintSceneDefinition.ingredientStacks(machine, loader.getVariables(), loader.getResolver());
    }
    public DynamicMachine machine() { return machine; }
    public BlueprintSceneDefinition definition() { return controls.definition(); }
    public BlueprintPreviewState controls() { return controls; }
    public List<ItemStack> ingredients() { return ingredients.stream().map(ItemStack::copy).toList(); }

    public void resetCenter() {
        camera.resetCenter();
        interaction.cancel();
        sceneToRecipe = null;
        hovered = null;
    }

    public void changeSize(int delta) {
        if (controls.changeSize(delta)) {
            interaction.retain(definition());
            materials.retain(definition());
            close();
        }
    }

    public BlockPos pick(double x, double y) {
        if (scene == null || sceneToRecipe == null) return null;
        return BlueprintRayPicker.pick(sceneToRecipe, x, y, scene, scene.visiblePositions(!singleLayer, layer()));
    }

    public List<Component> materialTooltip(BlockPos pos) {
        return scene == null ? List.of() : BlueprintPreviewDetails.materialTooltip(scene, pos);
    }

    public void select(BlockPos pos) {
        interaction.select(pos, definition());
        materials.select(scene, interaction.selected());
    }

    public void updateMaterials() {
        if (scene != null) materials.update(scene);
    }

    /** JEI builds its index before rendering is available; allocate renderers and the private Level lazily. */
    public BlueprintPreviewScene scene() {
        ClientLevel world = Minecraft.getInstance().level;
        if (world == null || sceneWorld.get() != world) close();
        if (world == null) return null;
        if (scene == null) {
            scene = new BlueprintPreviewScene(machine, definition());
            sceneWorld = new WeakReference<>(world);
        }
        return scene;
    }

    public int layer() { return controls.layer(); }
    public void setLayer(int layer) {
        controls.setLayer(layer);
        interaction.cancel();
        hovered = null;
    }

    @Override public void close() {
        layerScrollbar.cancel();
        if (scene != null) scene.close();
        scene = null;
        sceneWorld.clear();
        sceneToRecipe = null;
        hovered = null;
        materials.invalidate();
        interaction.cancel();
    }
}
