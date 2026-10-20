/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's world-scene selection overlays.
 */
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

/** Overlay geometry shared by both preview hosts; callers filter it by visible scene positions. */
public final class BlueprintPreviewOverlays {
    private BlueprintPreviewOverlays() { }

    public static void fill(PoseStack pose, MultiBufferSource buffers, BlockPos pos, float r, float g, float b, float alpha) {
        AABB box = new AABB(pos).inflate(0.005);
        LevelRenderer.addChainedFilledBoxVertices(pose, buffers.getBuffer(RenderType.debugFilledBox()),
                box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, r, g, b, alpha);
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), box, r, g, b, 1);
    }

    public static void outline(PoseStack pose, MultiBufferSource buffers, BlockPos pos) {
        LevelRenderer.renderLineBox(pose, buffers.getBuffer(RenderType.lines()), new AABB(pos).inflate(0.004),
                0.55F, 0.8F, 1, 1);
    }
}
