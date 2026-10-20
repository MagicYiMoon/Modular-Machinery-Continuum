/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.function.Consumer;

/** Detached requirement-level text draft. The parent catalyst Save is the only server mutation. */
final class CatalystTooltipPanel {
    private final RecipeTooltipPanel lines = new RecipeTooltipPanel(false);
    private Font font;
    private Button apply, cancel;
    private boolean visible;

    void init(Font font, int left, int top, Consumer<AbstractWidget> register,
              Consumer<List<String>> accept, Runnable back) {
        this.font = font;
        apply = Button.builder(Component.literal("应用"), b -> accept.accept(lines.value()))
                .bounds(left + 246, top + 222, 66, 20)
                .tooltip(Tooltip.create(Component.literal("应用到催化剂草稿；返回后点击保存才写入服务器"))).build();
        cancel = Button.builder(Component.literal("取消"), b -> back.run()).bounds(left + 318, top + 222, 66, 20).build();
        register.accept(apply); register.accept(cancel);
        lines.init(font, left, top, register); setVisible(visible);
    }
    void open(List<String> source) { lines.open(source); setVisible(true); }
    void setVisible(boolean show) {
        visible = show; lines.setVisible(show);
        apply.visible = cancel.visible = show; cancel.active = show; apply.active = show && lines.error() == null;
    }
    boolean click(int x, int y) { return lines.click(x, y); }
    void scroll(int x, int y, double delta) { lines.scroll(x, y, delta); }
    boolean escape() { return lines.escape(); }
    void draw(GuiGraphics graphics) {
        String error = lines.error(); apply.active = visible && error == null;
        graphics.fill(0, 0, 440, 260, 0x99000000); graphics.fill(40, 8, 400, 252, 0xFF252A31);
        graphics.drawString(font, Component.literal("催化剂 · 提示文字"), 56, 18, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("显示于 JEI 催化剂悬停信息；不会改变催化效果"), 56, 40, 0xFFB8C0C8, false);
        lines.draw(graphics);
        graphics.drawString(font, Component.literal(error == null ? "应用只改草稿；催化剂窗口保存后才生效" : error),
                56, 202, error == null ? 0xFFB8C0C8 : 0xFFFF7777, false);
    }
}
