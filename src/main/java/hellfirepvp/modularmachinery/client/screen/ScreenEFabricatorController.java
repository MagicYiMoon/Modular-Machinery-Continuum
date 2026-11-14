/* SPDX-License-Identifier: GPL-3.0-only
 * NECO GuiEFabricatorController and its status/control/heat panels, KasumiNova and contributors, b37fbb53.
 */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import hellfirepvp.modularmachinery.common.efabricator.*;
import hellfirepvp.modularmachinery.common.menu.MenuEFabricatorController;
import hellfirepvp.modularmachinery.common.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

public final class ScreenEFabricatorController extends AbstractContainerScreen<MenuEFabricatorController> {
    private static final ResourceLocation BG = texture("efabricator_dark"), E = texture("efabricator_elements"), INV = texture("efabricator_inventory");
    private static final ResourceLocation MODES = texture("efabricator_modes");
    private int pressedMode = -1;
    public ScreenEFabricatorController(MenuEFabricatorController menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = MenuEFabricatorController.WIDTH; imageHeight = MenuEFabricatorController.HEIGHT;
    }
    private static ResourceLocation texture(String name) { return new ResourceLocation("modularmachinery", "textures/gui/neco/" + name + ".png"); }
    public static void receive(EFabricatorMonitorPackets.Update p) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof MenuEFabricatorController menu && menu.containerId == p.container()) menu.accept(p.session(), p.data());
    }
    private Component tr(String key, Object... args) { return Component.translatable("gui.modularmachinery.ef." + key, args); }
    private String n(double n) {
        if (n < 1000) return Long.toString((long)n);
        double unit = n >= 1e12 ? 1e12 : n >= 1e9 ? 1e9 : n >= 1e6 ? 1e6 : 1e3;
        return String.format(Locale.ROOT, "%.1f%s", n / unit, unit == 1e12 ? "T" : unit == 1e9 ? "G" : unit == 1e6 ? "M" : "k");
    }
    private String exact(long n) { return String.format(Locale.ROOT, "%,d", n); }
    private String rate() { double rate = menu.craftsPerTick(); return rate <= .1 ? "<0.1" : String.format(Locale.ROOT, "%.1f", rate); }
    private void text(GuiGraphics g, Component value, int x, int y, int width, int color) {
        value = SmoothUiFonts.compact(value);
        if (font.width(value) > width) {
            var style = value.getStyle(); int max = Math.max(0, width - font.width(Component.literal("…").withStyle(style)));
            value = Component.literal(font.substrByWidth(value, max).getString() + "…").withStyle(style);
        }
        g.drawString(font, value, leftPos + x, topPos + y, color, false);
    }
    private void text(GuiGraphics g, String value, int x, int y, int width) { text(g, Component.literal(value), x, y, width, 0xFFD6D6D6); }
    private void blit(GuiGraphics g, ResourceLocation tex, int x, int y, int u, int v, int w, int h) {
        // Original sheets contain translucent borders, overlays and slot frames. Vanilla blit alone
        // does not enable blending; drawing these as opaque quads destroys the source appearance.
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(tex, leftPos + x, topPos + y, u, v, w, h, 256, 256);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    private boolean inside(double x, double y, int l, int t, int w, int h) { return x >= leftPos + l && x < leftPos + l + w && y >= topPos + t && y < topPos + t + h; }
    private int fill(double amount, double capacity, int size) { return capacity <= 0 ? 0 : (int)Math.max(0, Math.min(size, Math.floor(size * amount / capacity))); }
    private void vertical(GuiGraphics g, int x, int y, int u, int v, int w, int h, double amount, double capacity) {
        int f = fill(amount, capacity, h); if (f > 0) blit(g, E, x, y + h - f, u, v + h - f, w, f);
    }
    private void processor(GuiGraphics g, int x, int y, int tier) { blit(g, E, x, y, tier == 0 ? 58 : tier == 4 ? 1 : tier == 6 ? 20 : 39, tier == 0 ? 126 : 145, 18, 18); }
    private String status(EFabricatorMonitor.Snapshot d) { return (d.flags() & 1) == 0 ? "no_ae" : (d.flags() & 2) == 0 ? "unformed" : !d.readable() ? "unknown" : (d.flags() & 4) == 0 ? "stopped" : (d.flags() & 8) == 0 ? "offline" : "online"; }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        var d = menu.snapshot(); boolean known = d.readable();
        blit(g, BG, 0, 0, 0, 0, imageWidth, imageHeight);
        text(g, known && menu.craftsPerTick() > 0 ? tr("crafted_rate", n(d.crafted()), rate())
                : tr("crafted", known ? n(d.crafted()) : "?"), 5, 3, 160, 0xFFD6D6D6);
        text(g, Component.literal("< " + (d.page() + 1) + "/" + d.pages() + " >"), 165, 3, 45, 0xFFD6D6D6);
        blit(g, E, 212, 1, 151, 87, 14, 9);
        blit(g, E, 227, 1, inside(mx, my, 227, 1, 15, 11) ? 167 : 151, 75, 15, 11);
        blit(g, INV, 7, 15, 1, 197, 229, 58);
        for (int i = 0; i < 12; i++) {
            int x = 8 + i * 19; var w = i < d.workers().size() ? d.workers().get(i) : null;
            // Source CraftingStatusPanel uses the host tier on both rows for every available core.
            processor(g, x, 16, w == null ? 0 : d.tier()); processor(g, x, 54, w == null ? 0 : d.tier());
            blit(g, E, x, 35, w == null || !w.readable() ? 58 : w.queued() > 0 ? 20 : 39, 126, 18, 18);
            if (w != null && w.readable()) {
                if (!w.icon().isEmpty()) {
                    // Source CraftingStatus: slot margins (left=6, top=2), then Small adds
                    // half a pixel and SlotItemVirtual adds another half after its 0.5 scale.
                    // The resulting item origin is (7,3), not the whole-cell center (5,5).
                    g.pose().pushPose(); g.pose().translate(leftPos + x + 7, topPos + 35 + 3, 0); g.pose().scale(.5f, .5f, 1);
                    g.renderItem(w.icon(), 0, 0); g.pose().popPose();
                }
                int f = fill(w.queued(), w.capacity(), 14); if (f > 0) blit(g, E, x + 2, 49, 77, 126, f, 3);
            }
        }
        blit(g, MODES, 7, 77, pressedMode == 0 ? 75 : 1, d.overclock() ? 1 : 38, 36, 36);
        blit(g, MODES, 47, 77, pressedMode == 1 ? 112 : 38, d.cooling() ? 1 : 38, 36, 36);
        blit(g, E, 87, 77, 1, 219, 148, 36);
        blit(g, E, 89, 79, 1, 186, 40, 32); blit(g, E, 130, 79, 42, 186, 51, 32); blit(g, E, 182, 79, 94, 186, 51, 32);
        blit(g, E, 92, 80, 186, 1, 4, 9); blit(g, E, 97, 80, d.tier() == 4 ? 197 : d.tier() == 6 ? 203 : d.tier() == 9 ? 209 : 191, 1, 5, 9);
        text(g, tr("buses", d.buses()), 92, 89, 34, 0xFFD6D6D6);
        text(g, tr("processors", d.processors()), 92, 97, 34, 0xFFD6D6D6);
        text(g, tr("workers", d.workerCount()), 92, 105, 34, 0xFFD6D6D6);
        text(g, tr("queue"), 133, 81, 33, 0xFFD6D6D6); text(g, known ? n(d.queued()) : "?", 133, 91, 33); text(g, "/" + n(d.queueCapacity()), 133, 101, 33);
        blit(g, E, 169, 81, 146, 190, 9, 28); if (known) vertical(g, 169, 81, 150, 227, 9, 28, d.queued(), d.queueCapacity());
        text(g, tr("parallel"), 185, 81, 33, 0xFFD6D6D6); text(g, n(d.parallelism()), 185, 91, 33); text(g, d.delay() + " t", 185, 101, 33);
        blit(g, E, 221, 81, 156, 190, 9, 28); vertical(g, 221, 81, 160, 227, 9, 28, Math.min(d.parallelism(), d.queueCapacity()), d.parallelism());
        blit(g, INV, 17, 118, 1, 120, 162, 76);
        blit(g, E, 184, 117, 204, 24, 51, 77);
        blit(g, E, 187, 120, 210, 102, 45, 9); blit(g, E, 187, 132, 210, 122, 45, 9);
        if (known) { int f = fill(d.energy(), d.energyCapacity(), 45); if (f > 0) blit(g, E, 187, 132, 210, 112, f, 9); }
        text(g, tr("energy_short", known ? n(d.energy()) : "?"), 188, 121, 43, 0xFFD6D6D6);
        blit(g, E, 187, 144, 210, 102, 45, 9);
        text(g, tr("coolant_short", known ? n(d.coolant()) : "?"), 188, 145, 43, 0xFFD6D6D6);
        blit(g, E, 187, 156, 212, 134, 21, 36); blit(g, E, 210, 156, 234, 134, 21, 36);
        vertical(g, 187, 156, 108, 149, 21, 36, d.input(), d.inputCapacity()); vertical(g, 210, 156, 130, 149, 21, 36, d.output(), d.outputCapacity());
        text(g, tr(status(d)), 8, 194, 226, (d.flags() & 8) == 0 || !known ? 0xFFFFB999 : 0xFFB6D8B9);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) { }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g); super.render(g, mx, my, partial); renderTooltip(g, mx, my);
        var d = menu.snapshot(); List<Component> tip = null;
        if (inside(mx, my, 7, 77, 36, 36)) tip = List.of(tr("overclock", tr(d.overclock() ? "on" : "off")), tr("overclock_help"));
        else if (inside(mx, my, 47, 77, 36, 36)) tip = List.of(tr("cooling", tr(d.cooling() ? "on" : "off")), tr("cooling_help"), tr("coolant", d.readable() ? exact(d.coolant()) : "?"));
        else if (inside(mx, my, 5, 0, 160, 14)) tip = List.of(title, tr("crafted", d.readable() ? exact(d.crafted()) : "?"),
                tr("throughput", d.readable() ? String.format(Locale.ROOT, "%.3f", menu.craftsPerTick()) : "?"), tr("throughput_help"), tr("pending", d.readable() ? exact(d.pending()) : "?"));
        else if (inside(mx, my, 212, 1, 14, 10)) tip = List.of(tr("pattern_search"));
        else if (inside(mx, my, 165, 0, 45, 14)) tip = List.of(tr("pages", d.page() + 1, d.pages()), tr("page_help"));
        else if (inside(mx, my, 130, 79, 51, 32)) tip = List.of(tr("queue_full", d.readable() ? exact(d.queued()) : "?", exact(d.queueCapacity())));
        else if (inside(mx, my, 182, 79, 51, 32)) tip = List.of(tr("parallel_full", exact(d.parallelism())), tr("delay", d.delay(), d.untilWork()), tr("cost", EFabricatorSpecs.Tier.of(Math.max(4, d.tier())).energyPerCraft(d.overclock(), d.cooling())));
        else if (inside(mx, my, 184, 117, 51, 27)) tip = List.of(tr("energy", d.readable() ? String.format(Locale.ROOT, "%,.2f", d.energy()) : "?", exact(d.energyCapacity())), tr("energy_help"));
        else if (inside(mx, my, 184, 144, 51, 11)) tip = List.of(tr("coolant", d.readable() ? exact(d.coolant()) : "?"), tr("cooling_help"));
        else if (inside(mx, my, 187, 156, 21, 36)) tip = List.of(tr("fluid_in", exact(d.input()), exact(d.inputCapacity())), tr("fluid_help"));
        else if (inside(mx, my, 210, 156, 21, 36)) tip = List.of(tr("fluid_out", exact(d.output()), exact(d.outputCapacity())), tr("fluid_help"));
        else if (inside(mx, my, 8, 16, 227, 56)) {
            int i = (mx - leftPos - 8) / 19;
            if (i < d.workers().size()) {
                var w = d.workers().get(i); int row = my - topPos;
                if (row >= 35 && row < 53) tip = List.of(tr("worker_at", w.pos().getX(), w.pos().getY(), w.pos().getZ()),
                        w.readable() ? (w.icon().isEmpty() ? tr("idle") : w.icon().getHoverName()) : tr("unknown"),
                        tr("queue_full", w.readable() ? w.queued() : "?", w.capacity()), tr("energy", w.readable() ? n(w.energy()) : "?", exact(EFabricatorSpecs.ENERGY_CACHE)));
                else tip = List.of(tr("processor_tier", d.tier()), tr("processor_help"));
            }
        }
        if (tip != null) g.renderComponentTooltip(font, tip, mx, my);
    }
    private void action(int id, boolean enabled) { ModularmachineryNetwork.CHANNEL.sendToServer(new EFabricatorMonitorPackets.Action(menu.containerId, menu.session(), id, enabled)); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            if (inside(x, y, 7, 77, 36, 36)) { pressedMode = 0; return true; }
            if (inside(x, y, 47, 77, 36, 36)) { pressedMode = 1; return true; }
            if (inside(x, y, 165, 0, 14, 14)) { action(2, false); return true; }
            if (inside(x, y, 196, 0, 14, 14)) { action(3, false); return true; }
            if (inside(x, y, 212, 1, 14, 10)) { action(4, false); return true; }
            if (inside(x, y, 227, 1, 15, 11)) { onClose(); return true; }
        }
        return super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && pressedMode >= 0) {
            int mode = pressedMode; pressedMode = -1;
            if (inside(x, y, mode == 0 ? 7 : 47, 77, 36, 36)) action(mode, mode == 0 ? !menu.snapshot().overclock() : !menu.snapshot().cooling());
            return true;
        }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (delta != 0 && inside(x, y, 7, 0, 229, 73)) { action(delta < 0 ? 3 : 2, false); return true; }
        return super.mouseScrolled(x, y, delta);
    }
}
