/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.preview;

import com.mojang.blaze3d.vertex.PoseStack;

/** Copies the world view before compositing; AFTER_LEVEL supplies a different, non-world stack in Forge 47. */
final class ProjectionViewFrame {
    private PoseStack worldView;

    void capture(PoseStack source) {
        worldView = new PoseStack();
        worldView.last().pose().set(source.last().pose());
        worldView.last().normal().set(source.last().normal());
    }

    PoseStack take() {
        PoseStack captured = worldView;
        worldView = null;
        return captured;
    }

    void clear() { worldView = null; }
}
