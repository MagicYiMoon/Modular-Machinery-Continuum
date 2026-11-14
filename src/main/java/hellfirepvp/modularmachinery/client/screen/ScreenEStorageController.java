/* SPDX-License-Identifier: GPL-3.0-only
 * NECO GuiEStorageController, KasumiNova and contributors, b37fbb53.
 * Preserves the 256x207 panel, 6-row matrix column and player slot positions.
 */
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.common.estorage.*;
import hellfirepvp.modularmachinery.client.SmoothUiFonts;
import hellfirepvp.modularmachinery.common.menu.MenuEStorageController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import java.util.*;

public final class ScreenEStorageController extends AbstractContainerScreen<MenuEStorageController> {
    private static final ResourceLocation BG = new ResourceLocation("modularmachinery", "textures/gui/neco/estorage_controller.png");
    private static final ResourceLocation ELEMENTS = new ResourceLocation("modularmachinery", "textures/gui/neco/estorage_controller_elements.png");
    private static final String[] GRAPHS = {"bytes", "item_types", "fluid_types", "energy", "gas_types", "item_bytes", "fluid_bytes", "gas_bytes", "energy_usage"};
    /** Source Graph coordinates, without the random per-open one/two-pixel jitter. */
    record Graph(int id, int x, int y, int width, int u, int v, boolean right) { }
    List<Graph> graphs() {
        boolean gas = net.minecraftforge.fml.ModList.get().isLoaded("appmek");
        var result = new ArrayList<Graph>();
        result.add(new Graph(6,10,32,60,6,225,false));
        result.add(new Graph(5,9,51,65,1,232,false));
        if(gas) result.add(new Graph(7,10,70,60,1,232,false));
        result.add(new Graph(0,8,gas?89:70,64,2,239,false));
        result.add(new Graph(1,82,29,59,1,197,true));
        result.add(new Graph(2,85,46,60,1,197,true));
        if(gas) result.add(new Graph(4,82,63,59,1,197,true));
        result.add(new Graph(8,83,gas?80:63,61,1,211,true));
        result.add(new Graph(3,83,gas?97:80,60,1,218,true));
        return result;
    }
    int focusedGraph() { return focus; }
    double graphCurrent(int graph) { return current(graph); }
    double graphMaximum(int graph) { return maximum(graph); }
    private String matrixName(EStorageCellSpec spec) { return spec.kind().name().toLowerCase(Locale.ROOT) + "_matrix"; }
    private int focus;
    public ScreenEStorageController(MenuEStorageController menu, Inventory inventory, Component title) {
        super(menu, inventory, title); imageWidth = MenuEStorageController.WIDTH; imageHeight = MenuEStorageController.HEIGHT;
        inventoryLabelX = 8; inventoryLabelY = 114;
    }
    private Component tr(String key, Object... args) { return Component.translatable("gui.modularmachinery.es." + key, args); }
    private String exact(double value) { return java.text.NumberFormat.getNumberInstance(java.util.Locale.ROOT).format(value); }
    private String shortNumber(double value) {
        if (value >= 1e12) return String.format(Locale.ROOT, "%.1fT", value / 1e12);
        if (value >= 1e9) return String.format(Locale.ROOT, "%.1fG", value / 1e9);
        if (value >= 1e6) return String.format(Locale.ROOT, "%.1fM", value / 1e6);
        if (value >= 1e3) return String.format(Locale.ROOT, "%.1fk", value / 1e3);
        return exact(value);
    }
    /** Keep the font on the native GUI pixel grid; fractional downscaling loses CJK strokes. */
    private void text(GuiGraphics g, Component text, int x, int y, int maxWidth, int color) {
        String value = text.getString();
        if (font.width(value) > maxWidth) value = font.plainSubstrByWidth(value, maxWidth - font.width("…")) + "…";
        g.drawString(font, value, x, y, color, false);
    }
    private String compactNumber(long value, int decimals) {
        if (value < 1000) return Long.toString(value);
        double unit = value >= 1e12 ? 1e12 : value >= 1e9 ? 1e9 : value >= 1e6 ? 1e6 : 1e3;
        double factor = Math.pow(10, decimals);
        if (unit < 1e12 && Math.round(value / unit * factor) / factor >= 1000) unit *= 1000;
        String suffix = unit == 1e12 ? "T" : unit == 1e9 ? "G" : unit == 1e6 ? "M" : "k";
        return String.format(Locale.ROOT, "%1$." + decimals + "f%2$s", value / unit, suffix);
    }
    Component byteUsage(long used, long capacity) {
        String result = compactNumber(used, 1) + "/" + compactNumber(capacity, 1);
        if (font.width(SmoothUiFonts.compact(Component.literal(result))) > 55) result = compactNumber(used, 0) + "/" + compactNumber(capacity, 0);
        return SmoothUiFonts.compact(Component.literal(result));
    }
    /** NECO EStorageGraphBar: source 32x92 cylinder at (63,22), including the rounded caps. */
    private void renderGauge(GuiGraphics g, double percentage) {
        if (percentage <= 0) return;
        int x = leftPos + 63, y = topPos + 22;
        int height = (int) Math.round(84 * percentage);
        // Source gradient: green, green, yellow, red; energy runs in the opposite direction.
        double position = (focus == 3 ? 1 - percentage : percentage) * 3;
        float red = (float) Math.max(0, Math.min(1, position - 1));
        float green = (float) Math.max(0, Math.min(1, 3 - position));
        g.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.setColor(red, green, 0, .75F);
        g.blit(ELEMENTS, x, y + 92 - height - 8, 1, 246, 32, 8, 256, 256);
        for (int offset = 92 - height - 3; offset < 89; offset++)
            g.blit(ELEMENTS, x, y + offset, 34, 250, 32, 4, 256, 256);
        g.blit(ELEMENTS, x, y + 84, 1, 246, 32, 8, 256, 256);
        g.flush();
        g.setColor(1, 1, 1, 1);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    private double current(int graph) {
        return graph == 0 ? menu.value(MenuEStorageController.USED) : graph == 1 ? menu.value(MenuEStorageController.ITEM_TYPES)
                : graph == 2 ? menu.value(MenuEStorageController.FLUID_TYPES) : graph == 4 ? menu.value(MenuEStorageController.GAS_TYPES) : graph == 5 ? menu.value(MenuEStorageController.ITEM_BYTES) : graph == 6 ? menu.value(MenuEStorageController.FLUID_BYTES)
                : graph == 7 ? menu.value(MenuEStorageController.GAS_BYTES) : graph == 8 ? menu.energyValue(MenuEStorageController.DRAIN) : menu.energyValue(MenuEStorageController.ENERGY);
    }
    private double maximum(int graph) {
        return graph == 0 ? menu.value(MenuEStorageController.CAPACITY) : graph == 1 ? menu.value(MenuEStorageController.ITEM_TYPES_MAX)
                : graph == 2 ? menu.value(MenuEStorageController.FLUID_TYPES_MAX) : graph == 4 ? menu.value(MenuEStorageController.GAS_TYPES_MAX) : graph == 8 ? menu.energyValue(MenuEStorageController.ENERGY)
                : graph >= 5 && graph <= 7 ? menu.value(MenuEStorageController.CAPACITY) : menu.energyValue(MenuEStorageController.ENERGY_MAX);
    }
    private boolean inside(double x, double y, int left, int top, int width, int height) {
        return x >= leftPos + left && x < leftPos + left + width && y >= topPos + top && y < topPos + top + height;
    }
    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        g.blit(BG, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        for (var slot : menu.slots) {
            int x = leftPos + slot.x - 1, y = topPos + slot.y - 1;
            g.fill(x, y, x + 18, y + 18, 0xFF777777); g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFE8E8E8);
            g.fill(x + 1, y + 1, x + 17, y + 17, 0xFFAAAAAA);
        }
        double max = maximum(focus), value = current(focus);
        renderGauge(g, max > 0 ? Math.min(1, Math.max(0, value / max)) : 0);
        for (var graph : graphs()) renderGraph(g, graph);
        for (int i = 0; i < MenuEStorageController.PAGE_SIZE; i++) {
            var row = menu.row(i); if (row == null) continue;
            var spec = row.specification(); int rowX = leftPos + 178, rowY = topPos + 22 + 28 * i;
            g.blit(ELEMENTS, rowX, rowY, 1, 1 + 27 * spec.tier().ordinal(), 67, 26, 256, 256);
            g.blit(ELEMENTS, rowX, rowY, switch (spec.kind()) { case ITEM -> 19; case FLUID -> 10; case GAS -> 28; }, 82, 8, 26, 256, 256);
            // The source guide lines are at y=7/14/21; use a compact TTF size, not a scaled pose.
            g.drawString(font, SmoothUiFonts.compact(tr(matrixName(spec), spec.tier().name())), rowX + 10, rowY + 1, 0xFF303030, false);
            g.drawString(font, SmoothUiFonts.compact(Component.literal(row.usedTypes() + " / " + spec.maxTypes())), rowX + 10, rowY + 8, 0xFF303030, false);
            g.drawString(font, byteUsage(row.usedBytes(), spec.totalBytes()), rowX + 10, rowY + 15, 0xFF303030, false);
        }
        text(g, Component.literal("◀  " + (menu.value(MenuEStorageController.PAGE) + 1) + " / " + Math.max(1, menu.value(MenuEStorageController.PAGES)) + "  ▶"),
                leftPos + 179, topPos + 194, 68, 0xFF303030);
    }
    private void renderGraph(GuiGraphics g, Graph graph) {
        double max=maximum(graph.id), value=current(graph.id);
        double percent=max>0?Math.min(1,Math.max(0,value/max)):0;
        Component label;
        if(graph.id==1||graph.id==2||graph.id==4)
            label=tr("graph_count",tr(GRAPHS[graph.id]),shortNumber(value),shortNumber(max));
        else if(graph.id==8) label=tr("graph_drain",shortNumber(value));
        else if(graph.id==3&&hasShiftDown()) label=tr("graph_energy",shortNumber(value),shortNumber(max));
        else label=tr("graph_percent",tr(GRAPHS[graph.id]),String.format(Locale.ROOT,"%.1f",percent*100));
        var compact=SmoothUiFonts.compact(label);
        int x=leftPos+graph.x, y=topPos+graph.y;
        // Match source right alignment while retaining native pixels; tooltip holds the full exact values.
        int limit=graph.right?75:67;
        if(font.width(compact)>limit) {
            String plain=label.getString();
            while(!plain.isEmpty()&&font.width(SmoothUiFonts.compact(Component.literal(plain+"…")))>limit)plain=plain.substring(0,plain.length()-1);
            compact=SmoothUiFonts.compact(Component.literal(plain+"…"));
        }
        g.drawString(font,compact,graph.right?x+graph.width-font.width(compact):x,y+1,0xFF303030,false);
        g.flush(); com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        g.setColor(1,1,1,focus==graph.id?1:.4F);
        g.blit(ELEMENTS,x,y+10,graph.u,graph.v,graph.width,6,256,256);
        g.flush();g.setColor(1,1,1,1);com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }
    @Override protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        text(g, title, 8, 7, 162, 0xFF303030);
        String status = (menu.value(MenuEStorageController.FLAGS) & 1) == 0 ? "unformed"
                : (menu.value(MenuEStorageController.FLAGS) & 4) != 0 ? "online" : "offline";
        text(g, tr(status), 179, 7, 67, 0xFF303030);
        g.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Source focus changes on label hover; clicks also select for keyboard/touch users.
        for (var graph : graphs()) if (inside(mouseX,mouseY,graph.x,graph.y,graph.width,10)) focus = graph.id;
        renderBackground(g); super.render(g, mouseX, mouseY, partialTick); renderTooltip(g, mouseX, mouseY);
        boolean overGraphLabel = false;
        for (var graph : graphs()) if (inside(mouseX,mouseY,graph.x,graph.y,graph.width,10)) {
            overGraphLabel = true;
            var lines = new ArrayList<Component>();
            lines.add(tr("ratio",tr(GRAPHS[graph.id]),exact(current(graph.id)),exact(maximum(graph.id))));
            if(graph.id >= 5 && graph.id <= 7) lines.add(tr("bytes_share_tip"));
            if(graph.id == 8) lines.add(tr("energy_usage_tip"));
            lines.add(tr("drain",exact(menu.energyValue(MenuEStorageController.DRAIN))));
            lines.add(tr("drives",menu.value(MenuEStorageController.DRIVES),menu.value(MenuEStorageController.MATRICES)));
            g.renderComponentTooltip(font,lines,mouseX,mouseY);
        }
        if (inside(mouseX, mouseY, 8, 5, 162, 12)) g.renderTooltip(font, title, mouseX, mouseY);
        if (!overGraphLabel && inside(mouseX, mouseY, 63, 22, 32, 92))
            g.renderComponentTooltip(font, List.of(tr("ratio", tr(GRAPHS[focus]), exact(current(focus)), exact(maximum(focus))),
                    tr("drain", exact(menu.energyValue(MenuEStorageController.DRAIN))),
                    tr("drives", menu.value(MenuEStorageController.DRIVES), menu.value(MenuEStorageController.MATRICES))), mouseX, mouseY);
        for (int i = 0; i < MenuEStorageController.PAGE_SIZE; i++) {
            var row = menu.row(i); if (row == null || !inside(mouseX, mouseY, 178, 22 + 28 * i, 67, 26)) continue;
            g.renderComponentTooltip(font, List.of(tr(matrixName(row.specification()), row.specification().tier().name()),
                    tr("ratio", tr("bytes"), exact(row.usedBytes()), exact(row.specification().totalBytes())),
                    tr("ratio", tr("types"), row.usedTypes(), row.specification().maxTypes())), mouseX, mouseY);
        }
    }
    private void changePage(int button) { if (minecraft != null && minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button); }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        if (inside(x, y, 176, 20, 72, 182) && delta != 0) { changePage(delta > 0 ? 0 : 1); return true; }
        return super.mouseScrolled(x, y, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0) {
            for (var graph : graphs()) if (inside(x,y,graph.x,graph.y,graph.width,10)) { focus = graph.id; return true; }
            if (inside(x, y, 178, 191, 68, 14)) { changePage(x < leftPos + 212 ? 0 : 1); return true; }
        }
        return super.mouseClicked(x, y, button);
    }
}
