/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.editor.CatalystEditor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A subpanel in the same menu; cancelling discards its detached modifier draft. */
final class CatalystModifierPanel {
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private JsonArray draft = new JsonArray();
    private int index, scroll;
    private Font font;
    private EditBox value;
    private Button target, direction, operation, mode, add, up, down;
    private String status = "";
    private boolean visible;

    void init(Font font, int left, int top, Consumer<AbstractWidget> register,
              Consumer<JsonArray> apply, Runnable cancel) {
        String text = value == null ? null : value.getValue();
        this.font = font;
        widgets.clear();
        value = new EditBox(font, left + 118, top + 192, 88, 18, Component.literal("修改数值"));
        value.setMaxLength(48);
        value.setFilter(s -> s.chars().allMatch(c -> Character.isDigit(c) || ".eE+-".indexOf(c) >= 0));
        value.setTooltip(Tooltip.create(Component.literal("加法填写增减量，乘法填写倍率；支持负数和科学计数法\n运行时间的加法单位为 tick，能量为 FE/t\n修改概率时，0.1 表示 10 个百分点；乘法 2 表示翻倍\n时间先合并加法再乘法；其他目标按列表顺序应用")));
        widgets.add(value);
        target = Button.builder(Component.empty(), b -> change(0)).bounds(left + 70, top + 166, 112, 20).build();
        direction = Button.builder(Component.empty(), b -> change(1)).bounds(left + 188, top + 166, 72, 20).build();
        operation = Button.builder(Component.empty(), b -> change(2)).bounds(left + 266, top + 166, 98, 20).build();
        mode = Button.builder(Component.empty(), b -> change(3)).bounds(left + 218, top + 192, 146, 20).build();
        mode.setTooltip(Tooltip.create(Component.literal("数量／用量与概率分别修改；时间只修改时长\n物品目标同时作用于候选组，流体目标包含每 tick 流体需求")));
        widgets.addAll(List.of(target, direction, operation, mode));
        add = Button.builder(Component.literal("+ 修改器"), b -> {
            if (!store()) return;
            if (draft.size() >= CatalystEditor.MAX_MODIFIERS) { status = "修改器数量已达上限"; return; }
            draft.add(CatalystEditor.modifier()); index = draft.size() - 1; select();
        }).bounds(left + 292, top + 24, 74, 20).build();
        widgets.add(add);
        up = Button.builder(Component.literal("↑"), b -> move(-1)).bounds(left + 232, top + 24, 26, 20).build();
        down = Button.builder(Component.literal("↓"), b -> move(1)).bounds(left + 260, top + 24, 26, 20).build();
        widgets.addAll(List.of(up, down));
        widgets.add(Button.builder(Component.literal("应用"), b -> { if (store()) apply.accept(draft.deepCopy()); })
                .bounds(left + 228, top + 212, 66, 20)
                .tooltip(Tooltip.create(Component.literal("应用到催化剂草稿；回到催化剂窗口点击保存后写入服务器"))).build());
        widgets.add(Button.builder(Component.literal("取消"), b -> cancel.run()).bounds(left + 298, top + 212, 66, 20).build());
        widgets.forEach(register);
        select();
        if (text != null) value.setValue(text);
        setVisible(visible);
    }

    void open(JsonArray source) {
        draft = source.deepCopy(); index = scroll = 0; status = ""; select(); setVisible(true);
    }

    void setVisible(boolean show) {
        visible = show;
        for (var widget : widgets) { widget.visible = show; widget.active = show; }
        boolean selected = show && !draft.isEmpty();
        value.active = target.active = direction.active = operation.active = mode.active = selected;
        add.active = show && draft.size() < CatalystEditor.MAX_MODIFIERS;
        up.active = selected && index > 0;
        down.active = selected && index + 1 < draft.size();
        if (selected && duration()) direction.active = mode.active = false;
        if (selected && selected().get("target").getAsString().equals(CatalystEditor.TARGETS.get(1))) mode.active = false;
    }

    private boolean duration() { return selected().get("target").getAsString().equals(CatalystEditor.TARGETS.get(0)); }
    private JsonObject selected() { return draft.get(index).getAsJsonObject(); }

    private boolean store() {
        if (draft.isEmpty()) return true;
        try {
            float parsed = Float.parseFloat(value.getValue());
            if (!Float.isFinite(parsed)) throw new IllegalArgumentException();
            selected().addProperty("multiplier", parsed);
            CatalystEditor.validateModifier(selected());
            status = "";
            return true;
        } catch (RuntimeException invalid) { status = "请填写有效的有限数值"; return false; }
    }

    private void change(int field) {
        if (!store() || draft.isEmpty()) return;
        var mod = selected();
        switch (field) {
            case 0 -> {
                int next = (CatalystEditor.TARGETS.indexOf(mod.get("target").getAsString()) + 1) % CatalystEditor.TARGETS.size();
                mod.addProperty("target", CatalystEditor.TARGETS.get(next));
                if (next == 0) { mod.addProperty("affectChance", false); mod.addProperty("io", "input"); }
                if (next == 1) mod.addProperty("affectChance", false);
            }
            case 1 -> mod.addProperty("io", "input".equals(mod.get("io").getAsString()) ? "output" : "input");
            case 2 -> mod.addProperty("operation", 1 - mod.get("operation").getAsInt());
            case 3 -> mod.addProperty("affectChance", !mod.get("affectChance").getAsBoolean());
            default -> throw new IllegalArgumentException();
        }
        select();
    }

    private void move(int delta) {
        int next = index + delta;
        if (next < 0 || next >= draft.size() || !store()) return;
        var other = draft.get(next); draft.set(next, draft.get(index)); draft.set(index, other);
        index = next; select();
    }

    private void select() {
        index = Math.max(0, Math.min(index, draft.size() - 1));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, draft.size() - 4)));
        if (index < scroll) scroll = index;
        if (index >= scroll + 4) scroll = index - 3;
        if (!draft.isEmpty()) {
            var mod = selected();
            target.setMessage(Component.literal(CatalystEditor.targetLabel(mod.get("target").getAsString())));
            direction.setMessage(Component.literal(duration() ? "时长" : "input".equals(mod.get("io").getAsString()) ? "输入" : "输出"));
            operation.setMessage(Component.literal(mod.get("operation").getAsInt() == 0 ? "加法 ＋" : "乘法 ×"));
            mode.setMessage(Component.literal(duration() ? "修改时长（tick）" : mod.get("target").getAsString().equals(CatalystEditor.TARGETS.get(1))
                    ? "修改能量（FE/t）" : mod.get("affectChance").getAsBoolean() ? "修改概率" : "修改数量／用量"));
            value.setValue(mod.get("multiplier").getAsString());
        } else {
            target.setMessage(Component.literal("选择目标")); direction.setMessage(Component.literal("输入／输出"));
            operation.setMessage(Component.literal("加法／乘法")); mode.setMessage(Component.literal("修改数量／概率")); value.setValue("");
        }
        setVisible(visible);
    }

    boolean click(int x, int y) {
        if (x < 66 || x >= 374 || y < 64 || y >= 160) return false;
        int clicked = scroll + (y - 64) / 24;
        if (clicked >= draft.size()) return true;
        if (x >= 350) {
            if (clicked != index && !store()) return true;
            draft.remove(clicked); if (clicked < index) index--; select();
        } else if (clicked != index && store()) { index = clicked; select(); }
        return true;
    }

    void scroll(double delta) { scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), Math.max(0, draft.size() - 4))); }

    private String trim(String text, int width) {
        return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "...";
    }

    void draw(GuiGraphics graphics) {
        graphics.fill(0, 0, 440, 260, 0x99000000);
        graphics.fill(58, 18, 382, 248, 0xFF252A31);
        graphics.drawString(font, Component.literal("催化剂 · 修改器"), 70, 29, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("找到催化剂才生效；点击按钮切换属性"), 70, 50, 0xFFB8C0C8, false);
        graphics.fill(66, 62, 374, 160, 0xFF30363D);
        for (int row = 0; row < 4 && scroll + row < draft.size(); row++) {
            int i = scroll + row, y = 64 + row * 24;
            var mod = draft.get(i).getAsJsonObject();
            if (i == index) graphics.fill(68, y, 372, y + 24, 0xFF5B4A28);
            String label = CatalystEditor.targetLabel(mod.get("target").getAsString());
            if (!mod.get("target").getAsString().equals(CatalystEditor.TARGETS.get(0)))
                label += " · " + ("input".equals(mod.get("io").getAsString()) ? "输入" : "输出")
                        + (mod.get("affectChance").getAsBoolean() ? "概率" : "用量");
            graphics.drawString(font, Component.literal(trim((i + 1) + ". " + label, 262)), 76, y + 2, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal(trim((mod.get("operation").getAsInt() == 0 ? "加上 " : "乘以 ")
                    + mod.get("multiplier").getAsString(), 262)), 76, y + 13, 0xFFB8C0C8, false);
            graphics.drawString(font, Component.literal("×"), 355, y + 8, 0xFFFF5555, false);
        }
        if (draft.isEmpty()) graphics.drawString(font, Component.literal("点击 + 修改器添加效果；缺少催化剂仍可加工"), 74, 88, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal("数值"), 70, 197, 0xFFFFFF, false);
        String hint = status.isEmpty() ? "应用只改草稿；返回后保存催化剂才生效" : status;
        graphics.drawString(font, Component.literal(trim(hint, 296)), 70, 234, status.isEmpty() ? 0xFFB8C0C8 : 0xFFFF7777, false);
    }
}
