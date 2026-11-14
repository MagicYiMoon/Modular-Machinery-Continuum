/* SPDX-License-Identifier: GPL-3.0-only
 * NECO GuiECalculatorController / StorageBar / CPUStatusPanel / MonitorPanel,
 * KasumiNova and contributors, b37fbb53. Uses the original three-panel artwork.
 */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import hellfirepvp.modularmachinery.common.ecalculator.ECalculatorMonitor.*;
import hellfirepvp.modularmachinery.common.menu.MenuECalculatorController;
import hellfirepvp.modularmachinery.common.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

public final class ScreenECalculatorController extends AbstractContainerScreen<MenuECalculatorController> {
    private static final ResourceLocation BG = texture("ecalculator_background"), E1 = texture("ecalculator_gui_1"), E2 = texture("ecalculator_gui_2");
    private UUID armedTask;
    private long armedUntil;
    public ScreenECalculatorController(MenuECalculatorController menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = MenuECalculatorController.WIDTH; imageHeight = MenuECalculatorController.HEIGHT;
    }
    private static ResourceLocation texture(String name) { return new ResourceLocation("modularmachinery", "textures/gui/neco/" + name + ".png"); }
    public static void receive(ECalculatorMonitorPackets.Update packet) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof MenuECalculatorController menu && menu.containerId == packet.container())
            menu.accept(packet.session(), packet.data());
    }
    public static void receive(ECalculatorMonitorPackets.Statistics packet) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof MenuECalculatorController menu && menu.containerId == packet.container())
            menu.acceptStatistics(packet.session(), packet.data());
    }
    private String decimal(double n) { return String.format(Locale.ROOT, "%.2f", n); }
    private List<Component> statisticsTooltip(boolean ready, long nanos, double ops) {
        if (!ready) return List.of(tr("statistics_unknown"));
        return List.of(tr("cpu_time", decimal(nanos / 1000.0)), tr("operations", decimal(ops)), tr("statistics_help"));
    }
    private Component tr(String key, Object... args) { return Component.translatable("gui.modularmachinery.ec." + key, args); }
    private String number(long n) {
        if (n < 1000) return Long.toString(n);
        double unit = n >= 1e12 ? 1e12 : n >= 1e9 ? 1e9 : n >= 1e6 ? 1e6 : 1e3;
        return String.format(Locale.ROOT, "%.1f%s", n / unit, unit == 1e12 ? "T" : unit == 1e9 ? "G" : unit == 1e6 ? "M" : "k");
    }
    private String exact(long n) { return String.format(Locale.ROOT, "%,d", n); }
    private void text(GuiGraphics g, Component label, int x, int y, int width, int color, boolean compact) {
        Component value = compact ? SmoothUiFonts.compact(label) : label;
        if (font.width(value) > width) {
            var style = value.getStyle();
            int max = Math.max(0, width - font.width(Component.literal("…").withStyle(style)));
            value = Component.literal(font.substrByWidth(value, max).getString() + "…").withStyle(style);
        }
        g.drawString(font, value, leftPos + x, topPos + y, color, false);
    }
    private boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= leftPos + left && x < leftPos + left + width && y >= topPos + top && y < topPos + top + height;
    }
    private void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        g.blit(texture, leftPos + x, topPos + y, u, v, w, h, 256, 256);
    }
    private String status(Snapshot data) {
        return (data.flags() & 1) == 0 ? "no_ae" : (data.flags() & 2) == 0 ? "unformed"
                : (data.flags() & 4) == 0 ? "stopped" : (data.flags() & 8) == 0 ? "offline" : "online";
    }
    private boolean armed(Task task) { return task.id().equals(armedTask) && System.currentTimeMillis() < armedUntil; }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        var data = menu.snapshot();
        blit(g, BG, 0, 0, 0, 0, imageWidth, imageHeight);
        blit(g, E1, 7, 7, 0, 0, 241, 50); blit(g, E1, 7, 58, 0, 51, 241, 77); blit(g, E1, 7, 137, 0, 129, 241, 77);
        text(g, title, 12, 11, 168, 0xFF303030, false);
        text(g, tr(status(data)), 184, 12, 59, (data.flags() & 8) == 0 ? 0xFF804040 : 0xFF205C37, true);
        int barX = leftPos + 11, barY = topPos + 26;
        g.fill(barX, barY, barX + 233, barY + 14, 0xFF707070);
        long cumulative = 0; int previous = 0, segmentIndex = 0;
        for (long bytes : data.segments()) {
            cumulative = hellfirepvp.modularmachinery.common.ecalculator.ECalculatorMonitor.add(cumulative, bytes);
            int edge = data.capacity() == 0 ? 0 : (int)Math.min(233, Math.round(233.0 * cumulative / data.capacity()));
            if (edge > previous) g.fill(barX + previous, barY, barX + edge, barY + 14, segmentIndex % 2 == 0 ? 0xFF3B96D6 : 0xFF245C98);
            previous = edge; segmentIndex++;
        }
        int usedEdge = data.capacity() == 0 ? 0 : (int)Math.min(233, Math.round(233.0 * data.used() / data.capacity()));
        if (usedEdge > previous) g.fill(barX + previous, barY, barX + usedEdge, barY + 14, 0xFF416C91);
        boolean complete = (data.flags() & 16) == 0;
        text(g, Component.literal((complete ? number(data.used()) : "?") + " / " + number(data.capacity()) + " B"), 14, 28, 225, 0xFFFFFFFF, true);
        text(g, tr("cores_page", data.corePage() + 1, data.corePages()), 12, 44, 88, 0xFF303030, true);
        var stats = menu.statistics();
        text(g, Component.literal((stats.ready() ? decimal(stats.nanosPerTick() / 1000.0) : "?") + " µs/t"), 104, 44, 93, 0xFF303030, true);
        text(g, Component.literal("◀       ▶"), 206, 43, 37, 0xFF303030, true);
        for (int i = 0; i < data.cores().size(); i++) {
            Core c = data.cores().get(i); int x = 11 + i % 6 * 39, y = 62 + i / 6 * 35;
            blit(g, E2, x, y, 70, 0, 38, 34);
            blit(g, E2, x, y, c.maxHyper() > 0 ? 109 : 70, 35, 38, 34);
            blit(g, texture("ec_core_l" + c.tier() + (c.maxHyper() > 0 ? "_hyper" : "")), x + 5, y + 5, 0, 0, 16, 16);
            int maximum = c.maxNormal() + c.maxHyper(), used = c.normal() + c.hyper();
            int fill = maximum == 0 ? 0 : Math.min(28, (int)(28L * used / maximum));
            if (fill > 0) blit(g, E2, x + 28, y + 3 + 28 - fill, 148, 28 - fill, 7, fill);
            text(g, Component.literal((c.readable() ? used : "?") + "/" + maximum), x + 5, y + 24, 22, c.readable() ? 0xFF303030 : 0xFFB00000, true);
        }
        text(g, tr("memory", number(data.capacity())), 13, 143, 72, 0xFF303030, true);
        text(g, tr("used", complete ? number(data.used()) : "?"), 13, 153, 72, 0xFF303030, true);
        text(g, tr("hyper_memory", complete ? number(data.hyperMemory()) : "?"), 13, 163, 72, 0xFF303030, true);
        text(g, tr("normal", complete ? data.normal() : "?", data.maxNormal()), 13, 176, 72, 0xFF303030, true);
        text(g, tr("hyper", complete ? data.hyper() : "?", data.maxHyper()), 13, 188, 72, 0xFF303030, true);
        text(g, tr("parallel", number(data.parallelism())), 13, 201, 72, 0xFF303030, true);
        for (int i = 0; i < data.tasks().size(); i++) {
            var task = data.tasks().get(i); int x = 98 + i % 2 * 69, y = 142 + i / 2 * 28;
            blit(g, E1, x, y, 1, 208, 65, 26);
            if (!task.icon().isEmpty()) g.renderItem(task.icon(), leftPos + x + 3, topPos + y + 4);
            else text(g, tr(task.running() ? "generic_icon" : "return_icon"), x + 3, y + 8, 16, 0xFF205D85, true);
            text(g, Component.literal(number(task.bytes())), x + 22, y + 3, 31, 0xFF303030, true);
            text(g, tr(!task.running() ? "return_short" : task.hyper() ? "hyper_short" : "normal_short"), x + 22, y + 12, 31, 0xFF303030, true);
            if (task.running()) text(g, Component.literal(armed(task) ? "?" : "×"), x + 54, y + 1, 10, 0xFFB02020, false);
            if (!task.online()) g.fill(leftPos + x + 2, topPos + y + 22, leftPos + x + 63, topPos + y + 24, 0xFF956A43);
        }
        if (data.tasks().isEmpty()) text(g, tr("no_tasks"), 103, 166, 128, 0xFF505050, false);
        text(g, Component.literal("◀  " + (data.taskPage() + 1) + " / " + data.taskPages() + "  ▶"), 122, 202, 91, 0xFF303030, true);
        // Preserve the source scroll rail; pages are bounded on the wire even for many cores/tasks.
        if (data.taskPages() > 1) {
            int offset = (int)(50L * data.taskPage() / (data.taskPages() - 1));
            blit(g, E1, 239, 142 + offset, 207, 208, 5, 15);
        }
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) { }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g); super.render(g, mouseX, mouseY, partialTick);
        var data = menu.snapshot();
        if (inside(mouseX, mouseY, 7, 7, 241, 36)) g.renderComponentTooltip(font, List.of(title, tr(status(data)),
                tr("memory", exact(data.capacity())), tr("used", (data.flags() & 16) == 0 ? exact(data.used()) : "?"), tr("hyper_memory", (data.flags() & 16) == 0 ? exact(data.hyperMemory()) : "?"),
                tr("task_count", (data.flags() & 16) == 0 ? data.taskCount() : "?")), mouseX, mouseY);
        if (inside(mouseX, mouseY, 11, 44, 89, 11) || inside(mouseX, mouseY, 202, 42, 43, 14)) g.renderTooltip(font, tr("core_paging"), mouseX, mouseY);
        if (inside(mouseX, mouseY, 101, 42, 98, 14)) {
            var stats = menu.statistics();
            g.renderComponentTooltip(font, statisticsTooltip(stats.ready(), stats.nanosPerTick(), stats.operationsPerTick()), mouseX, mouseY);
        }
        for (int i = 0; i < data.cores().size(); i++) {
            var c = data.cores().get(i);
            if (!inside(mouseX, mouseY, 11 + i % 6 * 39, 62 + i / 6 * 35, 38, 34)) continue;
            var lines = new ArrayList<Component>(); lines.add(tr("core", c.tier(), c.pos().toShortString()));
            lines.add(tr("normal", c.readable() ? c.normal() : "?", c.maxNormal())); lines.add(tr("hyper", c.readable() ? c.hyper() : "?", c.maxHyper()));
            if (!c.readable()) lines.add(tr("unreadable"));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
        if (inside(mouseX, mouseY, 10, 140, 76, 72)) g.renderComponentTooltip(font, (data.flags() & 16) != 0
                ? List.of(tr("memory", exact(data.capacity())), tr("unreadable_totals"), tr("parallel", exact(data.parallelism())))
                : List.of(tr("memory", exact(data.capacity())), tr("used", exact(data.used())), tr("hyper_memory", exact(data.hyperMemory())),
                        tr("normal", data.normal(), data.maxNormal()), tr("hyper", data.hyper(), data.maxHyper()), tr("parallel", exact(data.parallelism()))), mouseX, mouseY);
        for (int i = 0; i < data.tasks().size(); i++) {
            var task = data.tasks().get(i); int x = 98 + i % 2 * 69, y = 142 + i / 2 * 28;
            if (!inside(mouseX, mouseY, x, y, 65, 26)) continue;
            var lines = new ArrayList<Component>();
            lines.add(task.running() ? task.name() : tr("returning"));
            if (task.running()) lines.add(tr("requested", exact(task.amount())));
            lines.add(tr("used", exact(task.bytes()))); lines.add(tr(task.hyper() ? "hyper_task" : "normal_task"));
            var measurement = menu.statistics().task(task.id());
            lines.addAll(statisticsTooltip(measurement.ready(), measurement.nanosPerTick(), measurement.operationsPerTick()));
            if (measurement.ready()) lines.add(tr("samples", measurement.samples()));
            lines.add(tr(task.running() ? task.online() ? "running" : "paused" : "returning_help"));
            if (task.running() && inside(mouseX, mouseY, x + 53, y, 12, 12)) lines.add(tr(armed(task) ? "confirm_cancel" : "cancel"));
            g.renderComponentTooltip(font, lines, mouseX, mouseY);
        }
    }
    private void page(int id) {
        armedTask = null;
        if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            if (inside(x, y, 202, 42, 43, 14)) { page(x < leftPos + 224 ? 0 : 1); return true; }
            if (inside(x, y, 119, 199, 98, 14)) { page(x < leftPos + 168 ? 2 : 3); return true; }
            var tasks = menu.snapshot().tasks();
            for (int i = 0; i < tasks.size(); i++) {
                var task = tasks.get(i);
                if (!task.running() || !inside(x, y, 151 + i % 2 * 69, 142 + i / 2 * 28, 12, 12)) continue;
                if (armed(task)) {
                    ModularmachineryNetwork.CHANNEL.sendToServer(new ECalculatorMonitorPackets.Cancel(menu.containerId, menu.session(), task.id()));
                    armedTask = null;
                } else { armedTask = task.id(); armedUntil = System.currentTimeMillis() + 5000; }
                return true;
            }
            armedTask = null;
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (delta != 0 && inside(x, y, 7, 58, 241, 77)) { page(delta > 0 ? 0 : 1); return true; }
        if (delta != 0 && inside(x, y, 93, 137, 155, 77)) { page(delta > 0 ? 2 : 3); return true; }
        return super.mouseScrolled(x, y, delta);
    }
}
