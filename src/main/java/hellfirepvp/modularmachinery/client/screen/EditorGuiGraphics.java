/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Vanilla 1.20.1 scissor coordinates do not follow the pose stack. */
final class EditorGuiGraphics extends GuiGraphics {
    private final EditorViewport viewport;
    EditorGuiGraphics(Minecraft minecraft, GuiGraphics parent, EditorViewport viewport) {
        super(minecraft, parent.bufferSource());
        this.viewport = viewport;
        pose().mulPoseMatrix(parent.pose().last().pose());
        pose().translate(viewport.x(), viewport.y(), 0);
        pose().scale(viewport.scale(), viewport.scale(), 1);
    }
    @Override public int guiWidth() { return viewport.width(); }
    @Override public int guiHeight() { return viewport.height(); }
    @Override public void enableScissor(int left, int top, int right, int bottom) {
        super.enableScissor((int) Math.floor(viewport.screenX(left)), (int) Math.floor(viewport.screenY(top)),
                (int) Math.ceil(viewport.screenX(right)), (int) Math.ceil(viewport.screenY(bottom)));
    }
}
