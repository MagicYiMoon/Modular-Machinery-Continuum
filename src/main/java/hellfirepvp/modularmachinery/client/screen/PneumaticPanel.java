/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.editor.PneumaticEditor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Distinct air quantities/pressure thresholds and inclusive integer Kelvin conditions. */
final class PneumaticPanel {
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private Font font;
    private EditBox air, pressure, min, max;
    private Button airTab, temperatureTab, mode, save;
    private boolean temperature, output, rate, editing, visible, pending;

    void init(Font font, int left, int top, Consumer<AbstractWidget> adder, Consumer<JsonObject> apply, Runnable cancel) {
        String a = air == null ? "100" : air.getValue(), p = pressure == null ? "2" : pressure.getValue();
        String low = min == null ? "0" : min.getValue(), high = max == null ? "2147483647" : max.getValue();
        this.font = font; widgets.clear();
        airTab = Button.builder(Component.literal("压缩空气"), btn -> { temperature = false; update(); })
                .bounds(left + 74, top + 46, 140, 20).build(); widgets.add(airTab);
        temperatureTab = Button.builder(Component.literal("持续温度条件"), btn -> { temperature = true; update(); })
                .bounds(left + 226, top + 46, 140, 20).build(); widgets.add(temperatureTab);
        mode = Button.builder(Component.literal(""), btn -> { rate = !rate; update(); })
                .bounds(left + 74, top + 76, 292, 20).build(); widgets.add(mode);
        mode.setTooltip(Tooltip.create(Component.literal("单次：开始时输入、结束时输出\n逐 tick：每个成功推进的配方 tick 处理；缺气或满仓暂停\n输入空气为 0 时，只检查压力；逐 tick 可持续检查压力")));
        air = box(font, left + 74, top + 116, "空气用量", a, false);
        pressure = box(font, left + 74, top + 154, "压力阈值 bar", p, true);
        pressure.setTooltip(Tooltip.create(Component.literal("-1～25 bar；支持小数\n正压力要求 ≥ 阈值；负压力（真空）要求 ≤ 阈值\n该阈值不会随批量倍率改变")));
        min = box(font, left + 74, top + 116, "最低温度 K", low, false);
        max = box(font, left + 74, top + 154, "最高温度 K", high, false);
        for (var box : List.of(min, max)) box.setTooltip(Tooltip.create(Component.literal("整数 Kelvin（K），0～2147483647，两端包含\n在开工和每个加工 tick 检查，不消耗温度，不受批量倍率影响")));
        save = Button.builder(Component.literal("保存"), btn -> { if (error().isEmpty()) apply.accept(value()); })
                .bounds(left + 228, top + 222, 66, 20).build(); widgets.add(save);
        widgets.add(Button.builder(Component.literal("取消"), btn -> cancel.run()).bounds(left + 300, top + 222, 66, 20).build());
        widgets.forEach(adder); update();
    }
    private EditBox box(Font font, int x, int y, String title, String value, boolean decimal) {
        var box = new EditBox(font, x, y, 292, 18, Component.literal(title));
        box.setMaxLength(32); box.setFilter(s -> s.chars().allMatch(c -> c >= '0' && c <= '9' || decimal && ".eE+-".indexOf(c) >= 0));
        box.setValue(value); box.setResponder(v -> update()); widgets.add(box); return box;
    }
    void open(JsonObject existing, boolean output) {
        this.output = output; editing = existing != null;
        temperature = editing && PneumaticEditor.isTemperature(existing);
        var clean = editing ? PneumaticEditor.validate(existing) : PneumaticEditor.air(output, 100, output ? 0 : 2, false);
        rate = !temperature && PneumaticEditor.perTick(clean);
        air.setValue(temperature ? "100" : clean.get("air").getAsString());
        pressure.setValue(temperature ? "2" : clean.get("pressure").getAsString());
        min.setValue(temperature ? clean.get("min").getAsString() : "0");
        max.setValue(temperature ? clean.get("max").getAsString() : "2147483647");
        setVisible(true, false);
    }
    private JsonObject value() {
        return temperature ? PneumaticEditor.temperature(Integer.parseInt(min.getValue()), Integer.parseInt(max.getValue()))
                : PneumaticEditor.air(output, Integer.parseInt(air.getValue()), Float.parseFloat(pressure.getValue()), rate);
    }
    private String error() {
        try { value(); return ""; }
        catch (NumberFormatException invalid) { return temperature ? "请填写有效的整数 Kelvin 温度" : "请填写有效的空气整数及压力数字"; }
        catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
    }
    void setVisible(boolean visible, boolean pending) { this.visible = visible; this.pending = pending; update(); }
    private void update() {
        for (var w : widgets) { w.visible = visible; w.active = visible && !pending; }
        if (save == null) return;
        airTab.active &= !editing && temperature;
        temperatureTab.visible = visible && !output;
        temperatureTab.active &= !editing && !temperature;
        mode.visible = visible && !temperature;
        mode.setMessage(Component.literal(rate ? "逐 tick：mL/t（单击切换）" : "单次：mL（单击切换）"));
        air.visible = visible && !temperature;
        pressure.visible = visible && !temperature && !output;
        min.visible = max.visible = visible && temperature;
        save.active &= error().isEmpty();
    }
    void draw(GuiGraphics g, String status, boolean failed) {
        update();
        g.fill(0, 0, 440, 260, 0xE0101216); g.fill(58, 18, 382, 252, 0xFF242A31);
        line(g, (editing ? "编辑" : "添加") + (output ? "输出" : "输入") + " · PneumaticCraft", 28, 0xFFFFFF);
        line(g, temperature ? "最低温度（K，包含）" : "空气用量（" + (rate ? "mL/t" : "mL") + "，可为 0）", 103, 0xDDDDDD);
        if (temperature) line(g, "最高温度（K，包含）", 141, 0xDDDDDD);
        else if (!output) line(g, "压力阈值（bar，负数表示真空）", 141, 0xDDDDDD);
        else { line(g, "输出不检查压力阈值", 143, 0xB5C5D5); line(g, "单次在结束产出；逐 tick 在加工中产出", 158, 0xB5C5D5); }
        line(g, temperature ? "持续检查温度范围，不消耗温度，不随批量缩放" : "批量倍率只放大空气用量；压力阈值不变", 181, 0xD9C98E);
        line(g, temperature ? "从所选气压仓读取温度，需要实际接入热源" : "空气为 0 时仍需匹配的气压仓；输入可仅检查压力", 194, 0xB5C5D5);
        String error = error(), text = error.isEmpty() ? status : error;
        line(g, text, 208, !error.isEmpty() || failed ? 0xFF8888 : 0x9BDEAA);
    }
    void tooltip(GuiGraphics g, int mouseX, int mouseY, int x, int y) {
        if (x >= 74 && x < 366 && y >= 205 && y < 220 && !error().isEmpty())
            g.renderComponentTooltip(font, List.of(Component.literal(error())), mouseX, mouseY);
    }
    private void line(GuiGraphics g, String s, int y, int color) { g.drawString(font, font.plainSubstrByWidth(s, 292), 74, y, color, false); }
}
