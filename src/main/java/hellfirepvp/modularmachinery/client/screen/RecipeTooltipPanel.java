/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.client.RecipeTooltipText;
import hellfirepvp.modularmachinery.common.editor.*;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.*;
import java.util.function.Consumer;

/** One selectable source entry per row, with a separately scrollable preview. */
final class RecipeTooltipPanel {
    private final boolean translate;
    RecipeTooltipPanel() { this(true); }
    RecipeTooltipPanel(boolean translate) { this.translate = translate; }
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private final List<String> draft = new ArrayList<>();
    private Font font;
    private EditBox text;
    private Button add, up, down, previewButton;
    private int index, scroll, previewScroll;
    private boolean visible, preview;
    void init(Font font, int left, int top, Consumer<AbstractWidget> register) {
        store(); this.font = font; widgets.clear();
        add = Button.builder(Component.literal("+ 行"), b -> {
            store(); if (draft.size() < RecipeTooltipEditor.MAX_LINES) { draft.add(""); index = draft.size() - 1; select(); }
        }).bounds(left + 56, top + 70, 52, 20).build(); widgets.add(add);
        up = Button.builder(Component.literal("↑"), b -> move(-1)).bounds(left + 114, top + 70, 26, 20).build(); widgets.add(up);
        down = Button.builder(Component.literal("↓"), b -> move(1)).bounds(left + 144, top + 70, 26, 20).build(); widgets.add(down);
        previewButton = Button.builder(Component.empty(), b -> { store(); preview = !preview; previewScroll = 0; update(); })
                .bounds(left + 280, top + 70, 104, 20).build(); widgets.add(previewButton);
        text = new EditBox(font, left + 56, top + 150, 328, 18, Component.literal("选中说明行的文字"));
        text.setTooltip(Tooltip.create(Component.literal((translate ? "点击列表中的一行，在此输入文字或语言翻译键" : "点击列表中的一行，在此输入催化剂提示原文")
                + "\n颜色 &a、粗体 &l、重置 &r；普通 & 字符保持原样\n每行最多 512 字，共 64 行／8192 字；空行可保留\n"
                + (translate ? "只修改配方附加说明，不修改催化剂自身提示" : "只修改当前催化剂提示，不修改配方附加说明或催化效果")))); widgets.add(text);
        widgets.forEach(register); select();
    }
    void open(List<String> source) {
        draft.clear(); draft.addAll(source); index = scroll = previewScroll = 0; preview = false; select();
    }
    private void store() {
        if (text != null && !draft.isEmpty()) draft.set(index, RecipeTooltipEditor.editedLine(draft.get(index), text.getValue()));
    }
    List<String> value() { store(); return List.copyOf(draft); }
    String error() { try { RecipeTooltipEditor.validate(value()); return null; } catch (IllegalArgumentException invalid) { return invalid.getMessage(); } }
    private void move(int delta) {
        store(); int next = index + delta;
        if (next >= 0 && next < draft.size()) { Collections.swap(draft, index, next); index = next; select(); }
    }
    private void select() {
        index = Math.max(0, Math.min(index, draft.size() - 1));
        scroll = Math.max(0, Math.min(scroll, Math.max(0, draft.size() - 3)));
        if (index < scroll) scroll = index;
        if (index >= scroll + 3) scroll = index - 2;
        String line = draft.isEmpty() ? "" : MachineNameFormatting.editable(draft.get(index));
        text.setMaxLength(Math.max(RecipeTooltipEditor.MAX_LINE_LENGTH, line.length())); text.setValue(line); update();
    }
    void setVisible(boolean show) { visible = show; update(); }
    private void update() {
        for (var widget : widgets) { widget.visible = visible; widget.active = visible; }
        text.visible = visible && !preview; text.active = text.visible && !draft.isEmpty();
        add.active = visible && !preview && draft.size() < RecipeTooltipEditor.MAX_LINES;
        up.active = visible && !preview && index > 0; down.active = visible && !preview && index + 1 < draft.size();
        previewButton.setMessage(Component.literal(preview ? "返回编辑" : "预览说明"));
    }
    boolean escape() { if (!preview) return false; preview = false; update(); return true; }
    boolean click(int x, int y) {
        if (!visible || preview || x < 56 || x >= 384 || y < 96 || y >= 144) return false;
        int clicked = scroll + (y - 96) / 16;
        if (clicked >= draft.size()) return true;
        store();
        if (x >= 364) { draft.remove(clicked); if (clicked < index) index--; }
        else index = clicked;
        select(); return true;
    }
    void scroll(int x, int y, double delta) {
        if (!visible || x < 56 || x >= 384 || y < 96 || y >= (preview ? 168 : 144)) return;
        if (preview) previewScroll = Math.max(0, Math.min(previewScroll - (int) Math.signum(delta), Math.max(0, previewLines().size() - 7)));
        else scroll = Math.max(0, Math.min(scroll - (int) Math.signum(delta), Math.max(0, draft.size() - 3)));
    }
    private List<FormattedCharSequence> previewLines() {
        var lines = new ArrayList<FormattedCharSequence>();
        for (var raw : draft) {
            var wrapped = font.split(translate ? RecipeTooltipText.resolve(raw) : Component.literal(raw), 316);
            if (wrapped.isEmpty()) lines.add(FormattedCharSequence.EMPTY); else lines.addAll(wrapped);
        }
        return lines;
    }
    void draw(GuiGraphics graphics) {
        update();
        graphics.drawString(font, Component.literal((draft.isEmpty() ? 0 : index + 1) + "/" + draft.size() + " 行"), 180, 76, 0xFFB8C0C8, false);
        graphics.fill(56, 96, 384, preview ? 168 : 144, 0xFF30363D);
        if (preview) {
            var lines = previewLines(); previewScroll = Math.min(previewScroll, Math.max(0, lines.size() - 7));
            for (int row = 0; row < 7 && previewScroll + row < lines.size(); row++) graphics.drawString(font, lines.get(previewScroll + row), 62, 97 + row * 10, 0xFFFFFFFF, false);
            graphics.drawString(font, Component.literal("预览自动换行；在预览区滚轮翻页"), 56, 176, 0xFF8ED6FF, false);
        } else {
            for (int row = 0; row < 3 && scroll + row < draft.size(); row++) {
                int i = scroll + row, y = 96 + row * 16;
                if (i == index) graphics.fill(57, y, 383, y + 16, 0xFF5B4A28);
                String line = (i + 1) + ". " + (draft.get(i).isEmpty() ? "（空行）" : MachineNameFormatting.editable(draft.get(i)));
                if (font.width(line) > 294) line = font.plainSubstrByWidth(line, 282) + "...";
                graphics.drawString(font, Component.literal(line), 62, y + 4, 0xFFFFFFFF, false);
                graphics.drawString(font, Component.literal("×"), 370, y + 4, 0xFFFF5555, false);
            }
            graphics.drawString(font, Component.literal("选中一行输入文字；颜色 &a  粗体 &l  重置 &r"), 56, 176, 0xFF8ED6FF, false);
        }
        if (draft.isEmpty()) graphics.drawString(font, Component.literal("暂无说明，点击 + 行添加"), 62, 100, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal(translate ? "有翻译键则翻译；无对应条目时显示原文" : "与源版一致：JEI 催化剂悬停提示按原文显示"), 56, 186, 0xFFB8C0C8, false);
    }
}
