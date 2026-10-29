/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraftforge.client.model.pipeline.VertexConsumerWrapper;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Adapts terrain fluid vertices to the GUI's entity format and camera, without a chunk shader. */
final class PreviewFluidVertexConsumer extends VertexConsumerWrapper {
    private final Matrix4f transform;
    private final Matrix3f normals;

    static RenderType renderType(FluidState fluid) {
        // GUI previews always draw to the main target, including Fabulous graphics mode.
        return ItemBlockRenderTypes.getRenderLayer(fluid) == RenderType.translucent()
                ? RenderType.entityTranslucentCull(TextureAtlas.LOCATION_BLOCKS)
                : RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
    }

    PreviewFluidVertexConsumer(VertexConsumer parent, PoseStack.Pose pose) {
        super(parent);
        transform = new Matrix4f(pose.pose());
        normals = new Matrix3f(pose.normal());
    }

    @Override public VertexConsumer vertex(double x, double y, double z) {
        parent.vertex(transform, (float) x, (float) y, (float) z);
        return this;
    }

    @Override public VertexConsumer uv(float u, float v) {
        // LiquidBlockRenderer emits BLOCK vertices; NEW_ENTITY additionally requires the overlay field.
        parent.uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY);
        return this;
    }

    @Override public VertexConsumer uv2(int u, int v) {
        parent.uv2(LightTexture.FULL_BRIGHT);
        return this;
    }

    @Override public VertexConsumer normal(float x, float y, float z) {
        parent.normal(normals, x, y, z);
        return this;
    }
}
