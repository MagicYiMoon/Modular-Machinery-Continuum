/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.JsonArray;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Existing requirement's independent selector editor; never switches screens or closes its menu. */
final class SelectorBindingPanel {
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private List<String> options = List.of();
    private String current = "", selected = "";
    private int scroll;
    private Font font;
    private EditBox search;
    private Button save;
    private boolean visible, busy;
    void init(Font font, int left, int top, Consumer<AbstractWidget> register, Consumer<String> apply, Runnable cancel) {
        String query = search == null ? "" : search.getValue();
        this.font = font; widgets.clear();
        search = new EditBox(font, left + 74, top + 84, 292, 18, Component.literal("搜索接口分组"));
        search.setMaxLength(256); search.setHint(Component.literal("搜索结构中的 selector-tag"));
        search.setResponder(value -> scroll = 0); search.setValue(query); widgets.add(search);
        widgets.add(Button.builder(Component.literal("不限接口"), b -> selected = "")
                .bounds(left + 74, top + 108, 84, 20)
                .tooltip(Tooltip.create(Component.literal("移除 selector-tag，允许使用整个成型结构的对应接口\n包括已标记分组和未标记分组的接口"))).build());
        save = Button.builder(Component.literal("保存"), b -> apply.accept(selected)).bounds(left + 228, top + 222, 66, 20).build();
        widgets.add(save);
        widgets.add(Button.builder(Component.literal("取消"), b -> cancel.run()).bounds(left + 300, top + 222, 66, 20).build());
        widgets.forEach(register); setVisible(visible, busy);
    }
    void open(JsonArray tags, String current) {
        options = tags.asList().stream().map(value -> value.getAsString()).distinct().sorted().toList();
        this.current = current; selected = current; scroll = 0;
        search.setValue(""); setVisible(true, false);
    }
    void setVisible(boolean show, boolean pending) {
        visible = show; busy = pending;
        for (var widget : widgets) { widget.visible = show; widget.active = show && !pending; }
        save.active = show && !pending && selected.length() <= 256 && selected.chars().noneMatch(Character::isISOControl);
        save.setMessage(Component.literal(pending ? "保存中…" : "保存"));
    }
    private List<String> filtered() {
        var values = new ArrayList<>(options);
        if (!current.isEmpty() && !values.contains(current)) values.add(0, current);
        String query = search.getValue().toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    void scroll(double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, filtered().size() - 3), scroll - (int) Math.signum(delta)));
    }
    boolean click(int x, int y) {
        if (x < 74 || x >= 366 || y < 134 || y >= 194) return false;
        var values = filtered(); int index = scroll + (y - 134) / 20;
        if (index < values.size()) selected = values.get(index);
        setVisible(visible, busy);
        return true;
    }
    void draw(GuiGraphics graphics, String status, boolean failed) {
        // A cleared oversized legacy selector must immediately re-enable save.
        setVisible(visible, busy);
        graphics.fill(0, 0, 440, 256, 0x99000000);
        graphics.fill(58, 18, 382, 252, 0xFF252A31);
        graphics.drawString(font, Component.literal("需求 · 接口分组"), 74, 29, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal(trim("按结构标签匹配，区分大小写；不是智能接口数值类型", 292)), 74, 46, 0xFFB8C0C8, false);
        graphics.drawString(font, Component.literal(trim("当前：" + (current.isEmpty() ? "不限接口" : current), 292)), 74, 64, 0xFF8ED6FF, false);
        graphics.drawString(font, Component.literal(trim("选择：" + (selected.isEmpty() ? "不限接口" : selected), 200)), 164, 114, 0xFFFFD36A, false);
        graphics.fill(72, 132, 368, 196, 0xFF30363D);
        var values = filtered();
        for (int row = 0; row < 3 && scroll + row < values.size(); row++) {
            String tag = values.get(scroll + row); int y = 134 + row * 20;
            if (tag.equals(selected)) graphics.fill(74, y, 366, y + 20, 0xFF5B4A28);
            graphics.drawString(font, Component.literal(trim(tag + (options.contains(tag) ? "" : "（现有值，结构未声明）"), 276)),
                    80, y + 6, tag.equals(selected) ? 0xFFFFD36A : 0xFFFFFF, false);
        }
        if (values.isEmpty()) graphics.drawString(font, Component.literal("没有匹配分组；需先在机器结构中声明"), 80, 143, 0xFFB8C0C8, false);
        String hint = status.isBlank() ? "结构声明含动态模板；实际接口以当前成型结构为准" : status;
        graphics.drawString(font, Component.literal(trim(hint, 292)), 74, 203, failed ? 0xFFFF7777 : 0xFFB8C0C8, false);
        if (values.size() > 3) graphics.drawString(font, Component.literal("↕"), 354, 183, 0xFF8ED6FF, false);
    }
    void tooltip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        if (!visible || x < 74 || x >= 366 || y < 134 || y >= 194) return;
        var values = filtered(); int index = scroll + (y - 134) / 20;
        if (index < values.size()) graphics.renderComponentTooltip(font, List.of(Component.literal(values.get(index)),
                Component.literal("单击选择后保存；缺少对应接口时不会回退到其他分组")), mouseX, mouseY);
    }
    private String trim(String text, int width) { return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "..."; }
}
