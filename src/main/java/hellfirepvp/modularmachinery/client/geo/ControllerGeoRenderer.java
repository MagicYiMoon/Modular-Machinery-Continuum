/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.client.geo;

import com.mojang.blaze3d.vertex.PoseStack;
import hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/** Block renderer shared by normal and factory controllers. */
public final class ControllerGeoRenderer implements BlockEntityRenderer<TileController> {
    private final GeoBlockRenderer<TileController> delegate = new GeoBlockRenderer<>(new ControllerGeoModel());

    public ControllerGeoRenderer() {
    }

    public void render(TileController controller, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        // All controller types use this renderer, but most machines have no GeckoLib model.  A
        // registered model is also only the formed-machine appearance: an unformed controller must
        // keep its ordinary block model while the player is assembling it.  The source renderer's
        // getCurrentModel() has the same formed-state gate; doing it here prevents GeckoLib from
        // baking and drawing a custom model for a controller whose structure is currently missing.
        // Returning before GeckoLib asks Minecraft to bake a resource also avoids a missing-resource
        // warning for every ordinary controller.
        if (controller == null || !controller.isFormed()
                || GeoMachineModelRegistry.getForController(controller) == null) return;
        delegate.render(controller, partialTick, poseStack, buffer, packedLight, packedOverlay);
    }
}
