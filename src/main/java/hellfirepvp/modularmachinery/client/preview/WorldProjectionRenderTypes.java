/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Source world projection blend/depth adapted to RenderType batches.
 */
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import java.util.IdentityHashMap;
import java.util.Map;

/** Preserve each model/BER's shader, texture and vertex format, then apply ghost state at flush time. */
final class WorldProjectionRenderTypes {
    private final Map<RenderType, RenderType> types = new IdentityHashMap<>();
    private final Runnable bindTarget;

    WorldProjectionRenderTypes() {
        this(() -> Minecraft.getInstance().getMainRenderTarget().bindWrite(false));
    }

    // The offscreen GL regression supplies its own framebuffer instead of constructing a game client.
    WorldProjectionRenderTypes(Runnable bindTarget) { this.bindTarget = bindTarget; }

    MultiBufferSource wrap(MultiBufferSource buffers) { return type -> buffers.getBuffer(type(type)); }

    RenderType type(RenderType original) {
        return types.computeIfAbsent(original, source -> new GhostType(source, bindTarget));
    }

    void clear() { types.clear(); }

    static void restoreWorldState() {
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static final class GhostType extends RenderType {
        GhostType(RenderType source, Runnable bindTarget) {
            // Keep the source type's crumbling flag.  It is normally false for block and entity
            // layers, but breaking overlays and a few third-party BERs intentionally mark their
            // type as crumbling; dropping that bit makes the projection disappear while a block
            // is being mined.  The upload-sort flag is deliberately not copied: the ghost batch
            // is submitted immediately in end(), where QUADS sorting is applied explicitly below.
            super("modularmachinery_projection_" + source, source.format(), source.mode(), source.bufferSize(),
                    source.affectsCrumbling(), false,
                    () -> {
                        source.setupRenderState();
                        // AFTER_LEVEL is after Fabulous compositing. A BER's original RenderType may still
                        // select an item/entity target; the ghost must blend against the completed scene.
                        bindTarget.run();
                        RenderSystem.disableDepthTest();
                        // Source disables depth testing. Explicitly prevent writes too, protecting later passes.
                        RenderSystem.depthMask(false);
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE_MINUS_DST_COLOR,
                                GlStateManager.DestFactor.DST_COLOR);
                    },
                    () -> {
                        try { source.clearRenderState(); }
                        finally {
                            try { bindTarget.run(); }
                            finally { restoreWorldState(); }
                        }
                    });
        }

        @Override public void end(BufferBuilder builder, VertexSorting sorting) {
            if (!builder.building()) return;
            // Every ghost is blended, including originally-solid geometry. Keep quad sorting stable
            // within its own batch without ever changing model-layer selection or vertex formats.
            if (mode() == VertexFormat.Mode.QUADS) builder.setQuadSorting(sorting);
            var rendered = builder.end();
            boolean submitted = false;
            try {
                setupRenderState();
                submitted = true;
                BufferUploader.drawWithShader(rendered);
            } finally {
                try { if (!submitted) rendered.release(); }
                finally { clearRenderState(); }
            }
        }
    }
}
