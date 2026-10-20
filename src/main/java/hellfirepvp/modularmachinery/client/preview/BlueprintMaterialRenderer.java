/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's ingredient count display.
 */
package hellfirepvp.modularmachinery.client.preview;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

/** Draws a material inside a 16-pixel slot, including counts exceeding a normal stack. */
public final class BlueprintMaterialRenderer {
    private BlueprintMaterialRenderer() { }

    public static void draw(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) return;
        var font = Minecraft.getInstance().font;
        graphics.renderItem(stack, x, y);
        if (stack.getCount() < 1000) graphics.renderItemDecorations(font, stack, x, y);
        else {
            graphics.renderItemDecorations(font, stack, x, y, "");
            int count = stack.getCount();
            String text = Screen.hasShiftDown() ? Integer.toString(count) : count < 1_000_000 ? count / 1000 + "K"
                    : count < 1_000_000_000 ? count / 1_000_000 + "M" : count / 1_000_000_000 + "G";
            float scale = Math.min(.5F, 17F / font.width(text));
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(x + 16, y + 12, 200);
            pose.scale(scale, scale, 1);
            graphics.drawString(font, text, -font.width(text), 0, 0xFFFFFF, true);
            pose.popPose();
        }
    }
}
