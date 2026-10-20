/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.common.editor.RecipeOptionsEditor.Options;
import hellfirepvp.modularmachinery.common.editor.RecipeOptionsEditor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** Local sub-draft. Apply returns to the parent properties; only the parent's Save writes the server file. */
final class RecipeOptionsPanel {
    private final List<AbstractWidget> widgets = new ArrayList<>();
    private Font font;
    private EditBox priority, threadName, maxThreads;
    private Button cancelPolicy, batchPolicy, jeiPolicy, generalTab, threadsTab, tooltipsTab, apply;
    private final RecipeTooltipPanel tooltips = new RecipeTooltipPanel();
    private boolean cancel, parallelized = true, loadJEI = true, visible;
    private int page;
    void init(Font font, int left, int top, Consumer<AbstractWidget> register, Consumer<Options> accept, Runnable back) {
        String draft = priority == null ? "0" : priority.getValue();
        String threadDraft = threadName == null ? "" : threadName.getValue();
        String limitDraft = maxThreads == null ? "-1" : maxThreads.getValue();
        this.font = font; widgets.clear();
        generalTab = Button.builder(Component.literal("加工与显示"), b -> { page = 0; update(); })
                .bounds(left + 56, top + 36, 104, 20).build(); widgets.add(generalTab);
        threadsTab = Button.builder(Component.literal("工厂线程"), b -> { page = 1; update(); })
                .bounds(left + 168, top + 36, 104, 20).build(); widgets.add(threadsTab);
        tooltipsTab = Button.builder(Component.literal("附加说明"), b -> { page = 2; update(); })
                .bounds(left + 280, top + 36, 104, 20).build(); widgets.add(tooltipsTab);
        priority = new EditBox(font, left + 202, top + 70, 182, 18, Component.literal("配方优先级"));
        priority.setMaxLength(11); priority.setFilter(value -> value.matches("-?[0-9]*")); priority.setValue(draft);
        priority.setTooltip(Tooltip.create(Component.literal("有符号 32 位整数，默认 0\n数值越小越先尝试，例如 -10 先于 0，0 先于 10\n同优先级仍按引擎原有规则排序"))); widgets.add(priority);
        threadName = new EditBox(font, left + 202, top + 70, 182, 18, Component.literal("工厂线程名称"));
        threadName.setMaxLength(Math.max(256, threadDraft.length())); threadName.setValue(threadDraft);
        threadName.setHint(Component.literal("留空：不限线程名称"));
        threadName.setTooltip(Tooltip.create(Component.literal("对应 threadName，仅限制工厂开工\n留空：不限名称；否则精确匹配核心线程，区分大小写和空格\n核心线程仍须先在机器脚本中声明，配方列表限制也继续生效\n这里只绑定配方，不会新建线程；普通控制器不受影响"))); widgets.add(threadName);
        maxThreads = new EditBox(font, left + 202, top + 102, 182, 18, Component.literal("配方线程上限"));
        maxThreads.setMaxLength(11); maxThreads.setFilter(value -> value.matches("-?[0-9]*")); maxThreads.setValue(limitDraft);
        maxThreads.setTooltip(Tooltip.create(Component.literal("对应配方 maxThreads，不是机器总线程数，也不是批量倍率\n-1：默认，不额外限制；0：禁止本配方在工厂开工（含核心线程）\n正数：上限；当前同一配方仍最多占一个线程，填大于 1 不会重复开工\n只影响新的开工，不中断已付款的加工；普通控制器不受影响"))); widgets.add(maxThreads);
        cancelPolicy = Button.builder(Component.empty(), b -> { cancel = !cancel; update(); })
                .bounds(left + 56, top + 102, 328, 20)
                .tooltip(Tooltip.create(Component.literal("对应源版 cancelIfPerTickFails\n开启：持续资源处理失败会取消本次加工，已消耗的输入不返还\n关闭：暂停等待供应或空间恢复；默认关闭"))).build(); widgets.add(cancelPolicy);
        batchPolicy = Button.builder(Component.empty(), b -> { parallelized = !parallelized; update(); })
                .bounds(left + 56, top + 130, 328, 20)
                .tooltip(Tooltip.create(Component.literal("对应源版 parallelized，默认开启\n开启：可使用机器与并行控制器的批量倍率，实际数量仍受资源限制\n关闭：此配方每次只加工一份，普通控制器和工厂线程均适用\n不改变机器倍率，也不影响其他配方；不改变线程数量"))).build(); widgets.add(batchPolicy);
        jeiPolicy = Button.builder(Component.empty(), b -> { loadJEI = !loadJEI; update(); })
                .bounds(left + 56, top + 158, 328, 20)
                .tooltip(Tooltip.create(Component.literal("对应源版 loadJEI，默认开启\n关闭后仅隐藏本配方的 JEI 页面，机器仍能加工，编辑器中仍可修改\n保存后同步刷新已连接客户端；未安装 JEI 时也可保存\n不隐藏机器蓝图或结构预览，共用文件会影响所有机器变体"))).build(); widgets.add(jeiPolicy);
        apply = Button.builder(Component.literal("应用"), b -> accept.accept(value())).bounds(left + 246, top + 222, 66, 20)
                .tooltip(Tooltip.create(Component.literal("应用到本地草稿，随后在配方窗口保存\n共用文件时会影响所有关联机器的配方"))).build(); widgets.add(apply);
        widgets.add(Button.builder(Component.literal("取消"), b -> back.run()).bounds(left + 318, top + 222, 66, 20).build());
        widgets.forEach(register); tooltips.init(font, left, top, register); setVisible(visible);
    }
    void open(Options options) {
        priority.setValue(Integer.toString(options.priority())); cancel = options.cancel();
        parallelized = options.parallelized(); loadJEI = options.loadJEI(); page = 0; tooltips.open(options.tooltips());
        threadName.setMaxLength(Math.max(256, options.threadName().length())); threadName.setValue(options.threadName());
        maxThreads.setValue(Integer.toString(options.maxThreads())); setVisible(true);
    }
    private Options value() {
        var result = new Options(Integer.parseInt(priority.getValue()), cancel, threadName.getValue(), Integer.parseInt(maxThreads.getValue()), parallelized, loadJEI, tooltips.value());
        RecipeOptionsEditor.validateThreads(result.threadName(), result.maxThreads()); return result;
    }
    private String error() {
        try { Integer.parseInt(priority.getValue()); } catch (NumberFormatException invalid) { return "优先级须为 32 位整数（默认 0）"; }
        try { value(); return tooltips.error(); }
        catch (NumberFormatException invalid) { return "线程上限须为 -1～2147483647 的整数"; }
        catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
    }
    private void update() {
        generalTab.active = visible && page != 0; threadsTab.active = visible && page != 1; tooltipsTab.active = visible && page != 2;
        for (var widget : List.of(priority, cancelPolicy, batchPolicy, jeiPolicy)) {
            widget.visible = visible && page == 0; widget.active = widget.visible;
        }
        for (var widget : List.of(threadName, maxThreads)) {
            widget.visible = visible && page == 1; widget.active = widget.visible;
        }
        tooltips.setVisible(visible && page == 2);
        cancelPolicy.setMessage(Component.literal("每 tick 处理失败时取消：" + (cancel ? "开启" : "关闭（暂停等待）")));
        batchPolicy.setMessage(Component.literal("允许批量加工：" + (parallelized ? "开启" : "关闭（每次一份）")));
        jeiPolicy.setMessage(Component.literal("在 JEI 中显示配方：" + (loadJEI ? "开启" : "关闭（仍可加工）")));
        apply.active = visible && error() == null;
    }
    void setVisible(boolean show) {
        visible = show;
        for (var widget : widgets) { widget.visible = show; widget.active = show; }
        update();
    }
    boolean click(int x, int y) { return page == 2 && tooltips.click(x, y); }
    void scroll(int x, int y, double delta) { if (page == 2) tooltips.scroll(x, y, delta); }
    boolean escape() { return page == 2 && tooltips.escape(); }
    void draw(GuiGraphics graphics) {
        update(); graphics.fill(0, 0, 440, 260, 0x99000000); graphics.fill(40, 8, 400, 252, 0xFF252A31);
        graphics.drawString(font, Component.literal("配方 · 高级设置（悬停查看说明）"), 56, 18, 0xFFFFFF, false);
        if (page == 2) tooltips.draw(graphics);
        else if (page == 1) {
            graphics.drawString(font, Component.literal("工厂线程名称"), 56, 75, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("配方线程上限"), 56, 107, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("仅工厂：-1 默认，0 禁用，正数为上限"), 56, 134, 0xFF8ED6FF, false);
            graphics.drawString(font, Component.literal("名称需精确匹配已声明的核心线程"), 56, 154, 0xFFB8C0C8, false);
            graphics.drawString(font, Component.literal("同配方仍限一个线程；批量开关在加工页"), 56, 174, 0xFFB8C0C8, false);
        } else {
            graphics.drawString(font, Component.literal("优先级（小值优先）"), 56, 75, 0xFFFFFF, false);
            graphics.drawString(font, Component.literal("以上选项仅作用于本配方"), 56, 186, 0xFF8ED6FF, false);
        }
        String error = error();
        graphics.drawString(font, Component.literal(error == null ? "应用后返回，配方窗口保存才写入文件" : error), 56, 202, error == null ? 0xFFB8C0C8 : 0xFFFF7777, false);
    }
}
