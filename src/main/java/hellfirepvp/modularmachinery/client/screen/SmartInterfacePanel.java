/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.*;
import hellfirepvp.modularmachinery.common.editor.SmartInterfaceEditor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Searchable declared types and a pair of inclusive float bounds, inside the existing server menu. */
final class SmartInterfacePanel {
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private List<String> options = List.of();
    private String selected = "";
    private int scroll;
    private Font font;
    private EditBox search, min, max;
    private Button save;
    private boolean visible, busy;

    void init(Font font, int left, int top, Consumer<AbstractWidget> register, Consumer<JsonObject> apply, Runnable cancel) {
        String query = search == null ? "" : search.getValue();
        String low = min == null ? "0" : min.getValue(), high = max == null ? "0" : max.getValue();
        this.font = font; widgets.clear();
        search = new EditBox(font, left + 74, top + 66, 292, 18, Component.literal("搜索智能接口类型"));
        search.setMaxLength(256); search.setHint(Component.literal("搜索机器声明的数值类型"));
        search.setResponder(value -> scroll = 0); search.setValue(query); widgets.add(search);
        min = new EditBox(font, left + 116, top + 164, 96, 18, Component.literal("最小值（包含）"));
        max = new EditBox(font, left + 270, top + 164, 96, 18, Component.literal("最大值（包含）"));
        for (var box : List.of(min, max)) {
            box.setMaxLength(64);
            box.setFilter(value -> value.chars().allMatch(c -> Character.isDigit(c) || ".eE+-".indexOf(c) >= 0));
            box.setTooltip(Tooltip.create(Component.literal("支持负数、小数和科学计数法；精度与源版 float 一致\n两端都包含；相同值表示精确匹配，不受批量倍率影响")));
            widgets.add(box);
        }
        min.setValue(low); max.setValue(high);
        save = Button.builder(Component.literal("保存"), b -> apply.accept(value()))
                .bounds(left + 228, top + 222, 66, 20).build(); widgets.add(save);
        widgets.add(Button.builder(Component.literal("取消"), b -> cancel.run()).bounds(left + 300, top + 222, 66, 20).build());
        widgets.forEach(register); setVisible(visible, busy);
    }
    void open(JsonArray types, JsonObject existing) {
        options = types.asList().stream().map(JsonElement::getAsString).distinct().sorted().toList();
        selected = existing == null ? options.stream().findFirst().orElse("") : existing.get("interface").getAsString();
        min.setValue(existing == null ? "0" : existing.get("min").getAsString());
        max.setValue(existing == null ? "0" : existing.has("max") ? existing.get("max").getAsString() : existing.get("min").getAsString());
        scroll = 0; search.setValue(""); setVisible(true, false);
    }
    private JsonObject value() { return SmartInterfaceEditor.create(selected, Float.parseFloat(min.getValue()), Float.parseFloat(max.getValue())); }
    private String error() {
        if (options.isEmpty()) return "没有共同类型；需先在机器中声明 smart-interface-types";
        if (!options.contains(selected)) return "请选择对应机器共同声明的类型";
        try { value(); return ""; } catch (RuntimeException invalid) { return "上下限须为有限数字，且最小值 ≤ 最大值"; }
    }
    void setVisible(boolean show, boolean pending) {
        visible = show; busy = pending;
        for (var widget : widgets) { widget.visible = show; widget.active = show && !pending; }
        save.active = show && !pending && error().isEmpty();
        save.setMessage(Component.literal(pending ? "保存中…" : "保存"));
    }
    private List<String> filtered() {
        String query = search.getValue().toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.toLowerCase(Locale.ROOT).contains(query)).toList();
    }
    void scroll(double delta) { scroll = Math.max(0, Math.min(Math.max(0, filtered().size() - 3), scroll - (int) Math.signum(delta))); }
    boolean click(int x, int y) {
        if (x < 74 || x >= 366 || y < 90 || y >= 150) return false;
        var values = filtered(); int index = scroll + (y - 90) / 20;
        if (index < values.size()) selected = values.get(index);
        setVisible(visible, busy); return true;
    }
    void draw(GuiGraphics graphics, String status, boolean failed) {
        setVisible(visible, busy);
        graphics.fill(0, 0, 440, 260, 0x99000000); graphics.fill(58, 18, 382, 252, 0xFF252A31);
        graphics.drawString(font, Component.literal("智能接口 · 数值条件（输入）"), 74, 29, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal(trim("选择：" + (selected.isEmpty() ? "未选择" : selected), 292)), 74, 48, 0xFFFFD36A, false);
        graphics.fill(72, 88, 368, 152, 0xFF30363D);
        var values = filtered();
        for (int row = 0; row < 3 && scroll + row < values.size(); row++) {
            String type = values.get(scroll + row); int y = 90 + row * 20;
            if (type.equals(selected)) graphics.fill(74, y, 366, y + 20, 0xFF5B4A28);
            graphics.drawString(font, Component.literal(trim(type, 268)), 80, y + 6, type.equals(selected) ? 0xFFFFD36A : 0xFFFFFF, false);
        }
        if (values.isEmpty()) graphics.drawString(font, Component.literal("没有匹配的已声明类型"), 80, 99, 0xFFB8C0C8, false);
        if (values.size() > 3) graphics.drawString(font, Component.literal("↕"), 354, 139, 0xFF8ED6FF, false);
        graphics.drawString(font, Component.literal("最小值"), 74, 169, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("最大值"), 226, 169, 0xFFFFFF, false);
        graphics.drawString(font, Component.literal("两端都包含；上下限相同表示精确匹配"), 74, 191, 0xFFB8C0C8, false);
        String error = error(), hint = status.isBlank() ? error.isEmpty() ? "仅检查数值，不消耗物品，也不设置概率" : error : status;
        graphics.drawString(font, Component.literal(trim(hint, 292)), 74, 207, failed || !error.isEmpty() ? 0xFFFF7777 : 0xFFB8C0C8, false);
    }
    void tooltip(GuiGraphics graphics, int mouseX, int mouseY, int x, int y) {
        if (x >= 74 && x < 366 && y >= 90 && y < 150) {
            var values = filtered(); int index = scroll + (y - 90) / 20;
            if (index < values.size()) graphics.renderComponentTooltip(font, List.of(Component.literal(values.get(index)),
                    Component.literal("单击选择类型；需要成型结构中的智能接口发布该类型的数值")), mouseX, mouseY);
        } else if (x >= 74 && x < 366 && y >= 44 && y < 62) {
            graphics.renderComponentTooltip(font, List.of(Component.literal("接口类型：" + selected)), mouseX, mouseY);
        } else if (x >= 74 && x < 366 && y >= 203 && y < 218 && !error().isEmpty()) {
            graphics.renderComponentTooltip(font, List.of(Component.literal(error())), mouseX, mouseY);
        }
    }
    private String trim(String text, int width) { return font.width(text) <= width ? text : font.plainSubstrByWidth(text, width - font.width("...")) + "..."; }
}
