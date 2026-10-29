/* SPDX-License-Identifier: GPL-3.0-only
 * MMCE Forge 1.20.1 port, modified 2026-10.
 */
package hellfirepvp.modularmachinery.client.geo;

import hellfirepvp.modularmachinery.common.geo.GeoMachineModelRegistry;
import hellfirepvp.modularmachinery.common.geo.MachineGeoModel;
import hellfirepvp.modularmachinery.common.tiles.TileController;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/** Resolves a controller's script-defined GeckoLib resources at render time. */
public final class ControllerGeoModel extends GeoModel<TileController> {
    private static final ResourceLocation FALLBACK = new ResourceLocation("minecraft", "missingno");

    /**
     * Resolves the model selection event once for each resource lookup.  The source renderer's
     * current model is a single object containing the geometry, texture and animation file.  The
     * old port applied {@code ControllerModelGetEvent} only in {@code getModelResource}, so a
     * callback that selected an alternate model rendered alternate geometry with the base texture
     * and animation file.  Keeping this helper as the one selection path makes all three resources
     * describe the same model.
     */
    private MachineGeoModel selectedDefinition(TileController controller) {
        return GeoMachineModelRegistry.selectedForController(controller);
    }

    @Override
    public ResourceLocation getModelResource(TileController controller) {
        MachineGeoModel definition = selectedDefinition(controller);
        return definition == null ? FALLBACK : definition.getModel();
    }

    @Override
    public ResourceLocation getTextureResource(TileController controller) {
        MachineGeoModel definition = selectedDefinition(controller);
        return definition == null ? new ResourceLocation("minecraft", "textures/misc/unknown_server.png")
                : definition.getTexture();
    }

    @Override
    public ResourceLocation getAnimationResource(TileController controller) {
        MachineGeoModel definition = selectedDefinition(controller);
        return definition == null ? FALLBACK : definition.getAnimation();
    }

    @Override
    public RenderType getRenderType(TileController controller, ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
