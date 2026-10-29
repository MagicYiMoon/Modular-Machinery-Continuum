/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import hellfirepvp.modularmachinery.common.menu.MenuMEItemInputBus;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;















































 
public class ScreenMEItemInputBus extends AbstractContainerScreen<MenuMEItemInputBus> {

     
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/meiteminputbus.png");

    /** The label rows, upstream U168-U170: the two grid captions share one row, the inventory label sits below the grids. */
    private static final int LABEL_CONFIG_Y = 24;
    private static final int LABEL_STORED_Y = 24;
    private static final int LABEL_STORED_X = 97;

    public ScreenMEItemInputBus(MenuMEItemInputBus menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Taken FROM the menu, per §193: a screen that states its own size can drift from the menu that placed its slots.
        this.imageWidth = MenuMEItemInputBus.PANEL_WIDTH;
        this.imageHeight = MenuMEItemInputBus.PANEL_HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    /**
     * The panel's text, from upstream's {@code drawFG} (U167-U170).
     *
     * <p>Drawn in the LABEL pass rather than in {@code renderBg} as upstream does: on 1.20.1 {@code renderLabels} is
     * where a container screen's text belongs, and the widget pass draws over the background.</p>
     *
     * <p><b>"Config" and "Stored Items" are this mod's own keys rather than AE2's {@code GuiText.Config} /
     * {@code GuiText.StoredItems} (upstream U168-U169)</b>, which is the same declination §665.5(e) recorded for the
     * fluid screen and for the same reason: an AE2 key renders as the raw key string in a language AE2 does not cover.
     * They are the keys the port already ships, added with the asset, and the item bus's screen is where they were
     * always meant to be read.</p>
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.meiteminputbus.title"), 8, 6, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.meiteminputbus.config"), 8, LABEL_CONFIG_Y, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.meiteminputbus.stored"), LABEL_STORED_X,
                LABEL_STORED_Y, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.modularmachinery.item_bus"), 8,
                imageHeight - 96 + 3, 0x404040, false);
    }

    





















 
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /** Port of upstream's wheel gesture, with the server deriving and validating the target stack again. */
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (delta == 0.0) {
            return super.mouseScrolled(mouseX, mouseY, delta);
        }
        for (Slot slot : menu.slots) {
            if (!(slot instanceof MenuMEItemInputBus.ConfigSlot config)
                    || mouseX < leftPos + slot.x || mouseX >= leftPos + slot.x + 16
                    || mouseY < topPos + slot.y || mouseY >= topPos + slot.y + 16) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                return true;
            }
            int current = stack.getCount();
            boolean up = delta > 0.0;
            boolean shift = hasShiftDown();
            boolean control = hasControlDown();
            long target;
            if (shift && control) {
                target = up ? Math.min((long) Integer.MAX_VALUE, current * 2L)
                        : Math.max(1L, current / 2L);
            } else {
                int step = control ? 100 : shift ? 10 : 1;
                target = up ? Math.min((long) Integer.MAX_VALUE, current + (long) step)
                        : Math.max(1L, current - (long) step);
            }
            ModularmachineryNetwork.PktMEItemInputBusCount.send(menu.getBusPos(), config.getGridIndex(), (int) target);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * Names the marked count under the cursor, which is the number this bus is asked to keep (upstream U187-U193).
     *
     * <p><b>Why the CONFIG grid needs a tooltip at all:</b> the marked stack's count is the whole meaning of the mark —
     * the bus tops the machine up to it and pushes the excess back (§399's rules 4 and 5) — and a 16x16 slot shows a
     * 64-stack and a 4096-stack identically. Upstream answers this with the same line plus its scroll-wheel hints; the
     * count is what survives here.</p>
     *
     * <p>The STORED grid's cache line is drawn for the same reason: the buffer's slots hold network quantities, so a
     * count of 832 is not readable off the icon either.</p>
     *
     * <p>An EMPTY config cell is answered too ({@code gui.meiteminputbus.nothing_marked}), because "this cell is not
     * configured" and "this cell is configured to nothing" are different states and the player needs to tell them
     * apart — the bus returns whatever it holds when a cell is cleared (§399's rule 1).</p>
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (Slot slot : menu.slots) {
            // The mouse is tested against the SLOT's own x/y -- the position the slot was drawn at -- rather than
            // against a hook that could hold a second copy of it.
            if (mouseX < leftPos + slot.x || mouseX >= leftPos + slot.x + 16
                    || mouseY < topPos + slot.y || mouseY >= topPos + slot.y + 16) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (slot instanceof MenuMEItemInputBus.ConfigSlot) {
                if (stack.isEmpty()) {
                    graphics.renderTooltip(font, Component.translatable("gui.meiteminputbus.nothing_marked"), mouseX, mouseY);
                } else {
                    List<Component> lines = List.of(
                            Component.translatable("gui.meiteminputbus.items_marked", String.format("%,d", stack.getCount())),
                            Component.translatable("gui.meiteminputbus.inv_action"),
                            actionHint());
                    graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
                }
                return;
            }
            if (slot instanceof MenuMEItemInputBus.StoredSlot && !stack.isEmpty()) {
                graphics.renderTooltip(font, Component.translatable("gui.meitembus.item_cached",
                        String.format("%,d", stack.getCount())), mouseX, mouseY);
                return;
            }
            // The player's own slots: vanilla's tooltip is correct and is drawn by the parent below.
            break;
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    private Component actionHint() {
        boolean shift = hasShiftDown();
        boolean control = hasControlDown();
        if (shift && control) {
            return Component.translatable("gui.meiteminputbus.inv_action.multiply", "SHIFT + CTRL");
        }
        if (control) {
            return Component.translatable("gui.meiteminputbus.inv_action.increase", "CONTROL", 100);
        }
        if (shift) {
            return Component.translatable("gui.meiteminputbus.inv_action.increase", "SHIFT", 10);
        }
        return Component.translatable("gui.meiteminputbus.inv_action.increase.normal");
    }
}
