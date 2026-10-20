/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Adapted from MMCE LayerRenderScrollbar / Scrollbar.
 */
package hellfirepvp.modularmachinery.client.preview;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.BlueprintLayerRange;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Shared source-style arrows and draggable thumb; geometry is in the owning panel's coordinates. */
public final class BlueprintLayerScrollbar {
    private static final ResourceLocation TEXTURE = new ResourceLocation(Modularmachinery.MODID,
            "textures/gui/guiblueprint_new.png");
    private static final int WIDTH = 9, ARROW = 9, THUMB = 15;
    private final BooleanSupplier active;
    private final IntSupplier minimum, maximum, layer;
    private final IntConsumer setLayer;
    private int x, y, height;
    private boolean dragging;
    private double grabOffset;

    public BlueprintLayerScrollbar(BooleanSupplier active, IntSupplier minimum, IntSupplier maximum,
                                   IntSupplier layer, IntConsumer setLayer) {
        this.active = active;
        this.minimum = minimum;
        this.maximum = maximum;
        this.layer = layer;
        this.setLayer = setLayer;
    }

    public void setBounds(int x, int y, int height) {
        if (this.x != x || this.y != y || this.height != height) cancel();
        this.x = x; this.y = y; this.height = height;
    }
    private BlueprintLayerRange range() { return new BlueprintLayerRange(minimum.getAsInt(), maximum.getAsInt()); }
    private int trackTop() { return y + ARROW + 2; }
    private int travel() { return Math.max(0, height - 2 * (ARROW + 2) - THUMB); }
    private int thumbTop() { return trackTop() + (int) Math.round(range().fraction(layer.getAsInt()) * travel()); }
    public boolean contains(double mouseX, double mouseY) {
        return active.getAsBoolean() && mouseX >= x && mouseX < x + WIDTH && mouseY >= y && mouseY < y + height;
    }
    public void cancel() { dragging = false; }
    public void draw(GuiGraphics g, double mouseX, double mouseY) {
        if (!active.getAsBoolean()) { cancel(); return; }
        if (GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_MOUSE_BUTTON_LEFT)
                != GLFW.GLFW_PRESS) cancel();
        var range = range();
        boolean over = contains(mouseX, mouseY);
        boolean up = layer.getAsInt() < range.max(), down = layer.getAsInt() > range.min();
        g.fill(x, trackTop(), x + WIDTH, y + height - ARROW - 2, 0xFF202328);
        int upState = !up ? 3 : over && mouseY < y + ARROW ? 1 : 0;
        int downState = !down ? 3 : over && mouseY >= y + height - ARROW ? 1 : 0;
        g.blit(TEXTURE, x, y, 184 + 11 * upState, 121, WIDTH, ARROW, 256, 256);
        g.blit(TEXTURE, x, y + height - ARROW, 184 + 11 * downState, 132, WIDTH, ARROW, 256, 256);
        int thumb = thumbTop();
        int state = range.min() == range.max() ? 3 : dragging ? 2 : over && mouseY >= thumb && mouseY < thumb + THUMB ? 1 : 0;
        g.blit(TEXTURE, x + 1, thumb, 184 + 9 * state, 158, 7, THUMB, 256, 256);
    }
    public List<Component> tooltip(double mouseX, double mouseY) {
        if (!contains(mouseX, mouseY)) return List.of();
        String key = mouseY < y + ARROW ? "up.tip" : mouseY >= y + height - ARROW ? "down.tip" : "tip";
        return List.of(Component.translatable("gui.preview.button.layer_render_scrollbar." + key),
                Component.translatable("gui.preview.button.layer_render_scrollbar.state.tip", layer.getAsInt(), range().count()));
    }
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!contains(mouseX, mouseY) || (button != 0 && button != 1)) return false;
        if (button != 0) return true;
        if (mouseY < y + ARROW) setLayer.accept(range().step(layer.getAsInt(), 1));
        else if (mouseY >= y + height - ARROW) setLayer.accept(range().step(layer.getAsInt(), -1));
        else if (range().min() != range().max() && mouseY >= thumbTop() && mouseY < thumbTop() + THUMB) {
            grabOffset = mouseY - thumbTop();
            dragging = true;
        }
        return true;
    }
    public boolean mouseDragged(double mouseY, int button) {
        if (button != 0 || !dragging || !active.getAsBoolean()) return false;
        setLayer.accept(range().atFraction((mouseY - trackTop() - grabOffset) / Math.max(1, travel())));
        return true;
    }
    public boolean mouseReleased(int button) {
        if (button != 0 || !dragging) return false;
        cancel();
        return true;
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0 || !contains(mouseX, mouseY)) return false;
        cancel();
        setLayer.accept(range().step(layer.getAsInt(), delta > 0 ? 1 : -1));
        return true;
    }
}
