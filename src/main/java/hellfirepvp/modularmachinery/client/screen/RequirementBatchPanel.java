/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.common.editor.RequirementBatchEditor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Standalone field editor, including requirements whose NBT cannot be edited by the basic item picker. */
final class RequirementBatchPanel {
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<Button> policies = new ArrayList<>();
    private Font font;
    private Button save;
    private int selected;
    private boolean visible, busy;
    void init(Font font, int left, int top, Consumer<AbstractWidget> register, Consumer<Integer> accept, Runnable cancel) {
        this.font = font; widgets.clear(); policies.clear();
        for (int i = 0; i < 3; i++) {
            final int value = i;
            var button = Button.builder(Component.literal(switch (i) {
                case RequirementBatchEditor.DEFAULT -> "恢复默认：随批量放大（省略字段）";
                case RequirementBatchEditor.FOLLOW -> "随批量放大：每份加工各算一份";
                default -> "不随批量放大：整批只算一份";
            }), b -> { selected = value; setVisible(visible, busy); }).bounds(left + 74, top + 76 + i * 28, 292, 22).build();
            policies.add(button); widgets.add(button);
        }
        save = Button.builder(Component.literal("保存"), b -> accept.accept(selected)).bounds(left + 228, top + 222, 66, 20).build();
        widgets.add(save);
        widgets.add(Button.builder(Component.literal("取消"), b -> cancel.run()).bounds(left + 300, top + 222, 66, 20).build());
        widgets.forEach(register); setVisible(visible, busy);
    }
    void open(int policy) { selected = policy; setVisible(true, false); }
    void setVisible(boolean show, boolean pending) {
        visible = show; busy = pending;
        for (var widget : widgets) { widget.visible = show; widget.active = show && !pending; }
        for (int i = 0; i < policies.size(); i++) policies.get(i).active = show && !pending && selected != i;
        save.setMessage(Component.literal(pending ? "保存中…" : "保存"));
    }
    void draw(GuiGraphics graphics, String status, boolean failed) {
        graphics.fill(0, 0, 440, 260, 0x99000000); graphics.fill(58, 18, 382, 252, 0xFF252A31);
        graphics.drawString(font, Component.literal("需求 · 批量设置"), 74, 29, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("只影响选中需求；不改变机器倍率或线程数"), 74, 48, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal("当前选择：" + (selected == 0 ? "恢复默认" : selected == 1 ? "随批量放大" : "整批只算一份")), 74, 64, 0xFFFFD36A, false);
        graphics.drawString(font, Component.literal("例：需求 2 个、批量 4 → 消耗或产出 8 个"), 74, 164, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal("选择不放大时仍为 2 个；FE/t、mB/t 同理"), 74, 180, 0xFFB8C0C8, false);
        String hint = status.isBlank() ? "保存立即重载；取消不改文件，保留其他条件" : status;
        if (font.width(hint) > 292) hint = font.plainSubstrByWidth(hint, 280) + "...";
        graphics.drawString(font, Component.literal(hint), 74, 203, failed ? 0xFFFF7777 : 0xFF8ED6FF, false);
    }
}
