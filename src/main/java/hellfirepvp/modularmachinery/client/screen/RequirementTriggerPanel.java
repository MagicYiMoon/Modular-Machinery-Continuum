/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.editor.RequirementTriggerEditor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.*;

/** Standalone trigger draft; resize preserves values, cancellation never submits a mutation. */
final class RequirementTriggerPanel {
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private Font font;
    private EditBox tick;
    private Button repeatButton, save;
    private int repeat, duration;
    private boolean visible, busy;
    void init(Font font, int left, int top, Consumer<AbstractWidget> register, BiConsumer<Integer, Integer> accept, Runnable cancel) {
        String draft = tick == null ? "" : tick.getValue();
        this.font = font; widgets.clear();
        tick = new EditBox(font, left + 184, top + 64, 182, 18, Component.literal("触发 tick"));
        tick.setMaxLength(10); tick.setFilter(value -> value.matches("[0-9]*")); tick.setValue(draft);
        tick.setHint(Component.literal("留空恢复默认，0 为开工时")); widgets.add(tick);
        tick.setResponder(value -> update());
        repeatButton = Button.builder(Component.empty(), b -> { repeat = (repeat + 1) % 3; update(); })
                .bounds(left + 74, top + 96, 292, 20)
                .tooltip(Tooltip.create(Component.literal("只在同一触发 tick 再次处理时生效\n例如失败回退后再次经过该 tick；不是每隔 N tick 执行"))).build(); widgets.add(repeatButton);
        widgets.add(Button.builder(Component.literal("恢复默认"), b -> { repeat = 0; tick.setValue(""); update(); })
                .bounds(left + 74, top + 222, 90, 20).build());
        save = Button.builder(Component.literal("保存"), b -> accept.accept(value(), repeat)).bounds(left + 228, top + 222, 66, 20).build(); widgets.add(save);
        widgets.add(Button.builder(Component.literal("取消"), b -> cancel.run()).bounds(left + 300, top + 222, 66, 20).build());
        widgets.forEach(register); setVisible(visible, busy);
    }
    void open(JsonObject requirement, int duration) {
        this.duration = duration;
        repeat = !requirement.has("triggerRepeatable") ? 0 : requirement.get("triggerRepeatable").getAsBoolean() ? 2 : 1;
        tick.setValue(requirement.has("triggerTime") ? requirement.get("triggerTime").getAsString() : "");
        setVisible(true, false);
    }
    private int value() { return tick.getValue().isBlank() ? -1 : Integer.parseInt(tick.getValue()); }
    private String error() {
        try { RequirementTriggerEditor.merge(new JsonObject(), value(), repeat, duration); return null; }
        catch (NumberFormatException invalid) { return "触发 tick 须为 0～2147483647 的整数"; }
        catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
    }
    private void update() {
        if (save == null || repeatButton == null) return;
        save.active = visible && !busy && error() == null;
        repeatButton.setMessage(Component.literal("重复触发：" + (repeat == 0 ? "默认（关闭，省略字段）" : repeat == 1 ? "关闭（只触发一次）" : "开启（允许再次经过时触发）")));
        save.setMessage(Component.literal(busy ? "保存中…" : "保存"));
    }
    void setVisible(boolean show, boolean pending) {
        visible = show; busy = pending;
        for (var widget : widgets) { widget.visible = show; widget.active = show && !pending; }
        update();
    }
    void draw(GuiGraphics graphics, String status, boolean failed) {
        graphics.fill(0, 0, 440, 260, 0x99000000); graphics.fill(58, 18, 382, 252, 0xFF252A31);
        graphics.drawString(font, Component.literal("需求 · 触发设置"), 74, 29, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("只设置一次性输入；配方时长 " + duration + " tick"), 74, 48, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal("触发 tick"), 74, 69, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("正数：运行到该 tick 时扣料，缺料则等待"), 74, 132, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal("时间受时长修改器影响；0 或留空为开工扣料"), 74, 150, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal("重复仅指再次经过同一时刻，不是执行间隔"), 74, 168, 0xFFFFD36A, false);
        graphics.drawString(font, Component.literal("持续需求与输出不在此编辑；取消不改文件"), 74, 186, 0xFFB8C0C8, false);
        String message = error() != null ? error() : status.isBlank() ? "保存立即重载；其他需求条件保持原样" : status;
        if (font.width(message) > 292) message = font.plainSubstrByWidth(message, 280) + "...";
        graphics.drawString(font, Component.literal(message), 74, 204, failed || error() != null ? 0xFFFF7777 : 0xFF8ED6FF, false);
    }
}
