/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO GuiEFabricatorPatternSearch and panels. */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import hellfirepvp.modularmachinery.common.efabricator.EFabricatorPatternView;
import hellfirepvp.modularmachinery.common.menu.MenuEFabricatorPatternSearch;
import hellfirepvp.modularmachinery.common.network.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import java.util.*;

public final class ScreenEFabricatorPatternSearch extends AbstractContainerScreen<MenuEFabricatorPatternSearch> {
    private static final ResourceLocation BG = texture("efabricator_pattern_search_bg_dark"), SEARCH = texture("efabricator_search"), INV = texture("efabricator_inventory"), E = texture("efabricator_elements");
    private EditBox input, output;
    private int debounce = -1;
    private EFabricatorPatternView.Query last = EFabricatorPatternView.Query.empty();
    private boolean truncated;
    private boolean draggingScroll;
    public ScreenEFabricatorPatternSearch(MenuEFabricatorPatternSearch menu, Inventory inv, Component title) { super(menu, inv, title); imageWidth = menu.WIDTH; imageHeight = menu.HEIGHT; }
    private static ResourceLocation texture(String name) { return new ResourceLocation("modularmachinery", "textures/gui/neco/" + name + ".png"); }
    public static void receive(EFabricatorPatternPackets.Update p) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof MenuEFabricatorPatternSearch menu && menu.containerId == p.container()) menu.accept(p.session(), p.data());
    }
    private Component tr(String key, Object... args) { return Component.translatable("gui.modularmachinery.ef." + key, args); }
    @Override protected void init() {
        String in = input == null ? "" : input.getValue(), out = output == null ? "" : output.getValue();
        super.init();
        input = box(leftPos + 30, tr("search_input"), in); output = box(leftPos + 142, tr("search_output"), out);
    }
    private EditBox box(int x, Component prompt, String value) {
        var box = new EditBox(font, x, topPos + 21, 88, 12, prompt); box.setBordered(false); box.setMaxLength(EFabricatorPatternView.MAX_QUERY);
        box.setTextColor(0xFFD6D6D6); box.setHint(SmoothUiFonts.compact(prompt)); box.setValue(value); box.setResponder(s -> debounce = 4);
        addRenderableWidget(box); return box;
    }
    @Override protected void containerTick() { super.containerTick(); if (debounce >= 0 && --debounce < 0) sendQuery(0, true); }
    private Set<ResourceLocation> localized(String query) {
        var ids = new LinkedHashSet<ResourceLocation>(); query = query.strip().toLowerCase(Locale.ROOT); if (query.isBlank()) return ids;
        boolean mod = query.startsWith("@"); String term = mod ? query.substring(1) : query;
        for (var item : ForgeRegistries.ITEMS.getValues()) {
            var id = ForgeRegistries.ITEMS.getKey(item);
            if (id != null && (mod ? id.getNamespace().contains(term) : new ItemStack(item).getHoverName().getString().toLowerCase(Locale.ROOT).contains(term))) {
                if (ids.size() >= EFabricatorPatternView.MAX_MATCHES) { truncated = true; break; } ids.add(id);
            }
        }
        for (var fluid : ForgeRegistries.FLUIDS.getValues()) {
            var id = ForgeRegistries.FLUIDS.getKey(fluid);
            if (id != null && (mod ? id.getNamespace().contains(term) : Component.translatable(fluid.getFluidType().getDescriptionId()).getString().toLowerCase(Locale.ROOT).contains(term))) {
                if (ids.size() >= EFabricatorPatternView.MAX_MATCHES) { truncated = true; break; } ids.add(id);
            }
        }
        return ids;
    }
    private void sendQuery(int page, boolean names) {
        if (names) { truncated = false; last = new EFabricatorPatternView.Query(input.getValue(), output.getValue(), localized(input.getValue()), localized(output.getValue()), page); }
        else last = new EFabricatorPatternView.Query(last.input(), last.output(), last.inputNames(), last.outputNames(), Math.max(0, page));
        ModularmachineryNetwork.CHANNEL.sendToServer(new EFabricatorPatternPackets.Query(menu.containerId, menu.session(), last));
    }
    private void action(int action, int row) { ModularmachineryNetwork.CHANNEL.sendToServer(new EFabricatorPatternPackets.Action(menu.containerId, menu.session(), action, menu.snapshot().token(), row)); }
    private boolean inside(double x, double y, int l, int t, int w, int h) { return x >= leftPos + l && x < leftPos + l + w && y >= topPos + t && y < topPos + t + h; }
    private void blit(GuiGraphics g, ResourceLocation texture, int x, int y, int u, int v, int w, int h) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend(); com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.blit(texture, leftPos + x, topPos + y, u, v, w, h, 256, 256); com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    private void text(GuiGraphics g, Component text, int x, int y, int width, int color) {
        var smooth = SmoothUiFonts.compact(text); if (font.width(smooth) > width) smooth = Component.literal(font.substrByWidth(smooth, Math.max(0, width - font.width("…"))).getString() + "…").withStyle(smooth.getStyle());
        g.drawString(font, smooth, leftPos + x, topPos + y, color, false);
    }
    @Override protected void renderBg(GuiGraphics g, float partial, int mx, int my) {
        var d = menu.snapshot();
        blit(g, BG, 0, 0, 0, 0, imageWidth, imageHeight); blit(g, SEARCH, 7, 15, 1, 1, 229, 21); blit(g, SEARCH, 7, 39, 1, 25, 229, 86);
        blit(g, INV, 17, 129, 1, 120, 162, 76);
        blit(g, E, 212, 1, 151, 97, 14, 9); blit(g, E, 227, 1, inside(mx, my, 227, 1, 15, 11) ? 167 : 151, 75, 15, 11);
        text(g, tr("pattern_search"), 7, 3, 200, 0xFFD6D6D6);
        for (int i = 0; i < d.entries().size(); i++) {
            var entry = d.entries().get(i); int x = 10 + i % 12 * 18, y = 45 + i / 12 * 18;
            blit(g, INV, x, y, 1, 120, 18, 18);
            var icon = entry.description().icon(); if (!icon.isEmpty()) g.renderItem(icon, leftPos + x + 1, topPos + y + 1);
            else text(g, Component.literal("?"), x + 6, y + 4, 10, 0xFFFFB999);
            if (!entry.description().valid()) g.fill(leftPos + x + 1, topPos + y + 15, leftPos + x + 17, topPos + y + 17, 0xFFD88456);
            if (inside(mx, my, x, y, 18, 18)) g.fill(leftPos + x + 1, topPos + y + 1, leftPos + x + 17, topPos + y + 17, 0x55FFFFFF);
        }
        int scroll = d.pages() == 1 ? 0 : (int)(65L * d.page() / (d.pages() - 1)); blit(g, SEARCH, 229, 42 + scroll, 233, 1, 5, 15);
        text(g, tr("pattern_count", d.total()), 184, 131, 51, 0xFFD6D6D6);
        text(g, Component.literal((d.page() + 1) + "/" + d.pages()), 184, 145, 51, 0xFFD6D6D6);
        text(g, Component.literal("<"), 187, 165, 12, 0xFFD6D6D6); text(g, Component.literal(">"), 218, 165, 12, 0xFFD6D6D6);
        text(g, tr(!d.formed() ? "unformed" : !d.readable() ? "pattern_unknown" : truncated ? "search_refine" : d.total() == 0 ? "search_empty" : "pattern_help"), 8, 205, 227, 0xFFFFB999);
    }
    @Override protected void renderLabels(GuiGraphics g, int mx, int my) { }
    @Override public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g); super.render(g, mx, my, partial); renderTooltip(g, mx, my);
        if (inside(mx, my, 212, 1, 14, 10)) { g.renderTooltip(font, tr("to_monitor"), mx, my); return; }
        if (!menu.getCarried().isEmpty() && inside(mx, my, 7, 39, 229, 86)) { g.renderTooltip(font, tr("pattern_put"), mx, my); return; }
        int row = row(mx, my); if (row < 0 || row >= menu.snapshot().entries().size()) return;
        var entry = menu.snapshot().entries().get(row); var d = entry.description(); var lines = new ArrayList<Component>();
        if (!d.icon().isEmpty()) lines.add(d.icon().getHoverName());
        lines.add(tr("pattern_location", entry.bus().getX(), entry.bus().getY(), entry.bus().getZ(), entry.slot() + 1));
        lines.add(tr(d.valid() ? "pattern_ready" : "pattern_invalid"));
        lines.add(tr("search_input")); d.inputs().stream().limit(6).forEach(i -> lines.add(Component.literal("  ").append(i.name())));
        lines.add(tr("search_output")); d.outputs().stream().limit(6).forEach(i -> lines.add(Component.literal("  ").append(i.name())));
        lines.add(tr("pattern_take")); g.renderComponentTooltip(font, lines, mx, my);
    }
    private int row(double x, double y) { return inside(x, y, 10, 45, 216, 72) ? ((int)y - topPos - 45) / 18 * 12 + ((int)x - leftPos - 10) / 18 : -1; }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            if (inside(x, y, 227, 1, 15, 11)) { onClose(); return true; }
            if (inside(x, y, 212, 1, 14, 10)) { action(2, -1); return true; }
            if (inside(x, y, 184, 160, 18, 18)) { sendQuery(menu.snapshot().page() - 1, false); return true; }
            if (inside(x, y, 212, 160, 18, 18)) { sendQuery(menu.snapshot().page() + 1, false); return true; }
            if (inside(x, y, 229, 42, 5, 80)) { draggingScroll = true; scrollTo(y); return true; }
            if (inside(x, y, 7, 39, 229, 86)) {
                if (!menu.getCarried().isEmpty()) action(1, -1);
                else { int row = row(x, y); if (row >= 0 && row < menu.snapshot().entries().size()) action(0, row); }
                return true;
            }
        }
        return super.mouseClicked(x, y, button);
    }
    private void scrollTo(double y) {
        int pages = menu.snapshot().pages();
        int page = (int)Math.round(Math.max(0, Math.min(1, (y - topPos - 42 - 7.5) / 65)) * (pages - 1));
        sendQuery(page, false);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (button == 0 && draggingScroll) { scrollTo(y); return true; }
        return super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (button == 0 && draggingScroll) { draggingScroll = false; return true; }
        return super.mouseReleased(x, y, button);
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (delta != 0 && inside(x, y, 7, 39, 229, 86)) { sendQuery(menu.snapshot().page() + (delta < 0 ? 1 : -1), false); return true; }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public boolean keyPressed(int key, int scan, int mods) {
        if (key == 256) return super.keyPressed(key, scan, mods);
        if (input.isFocused() || output.isFocused()) {
            if (key == 258) { boolean next = input.isFocused(); input.setFocused(!next); output.setFocused(next); setFocused(next ? output : input); }
            else (input.isFocused() ? input : output).keyPressed(key, scan, mods);
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
