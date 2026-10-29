/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuSmartInterface;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import hellfirepvp.modularmachinery.common.tiles.TileSmartInterface;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;




































































 
public class ScreenSmartInterface extends AbstractContainerScreen<MenuSmartInterface> {

    /** Inherited from {@code GuiContainerBase}, which is where the jar's only reference to it lives. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/guismartinterface.png");

    private EditBox valueField;
    private Button prev;
    private Button next;

    /** Which of the interface's bound entries is shown. Upstream's {@code showing} (U33), client-local. */
    private int showing = 0;

    public ScreenSmartInterface(MenuSmartInterface menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = MenuSmartInterface.PANEL_WIDTH;
        this.imageHeight = MenuSmartInterface.PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // Upstream's widget order: the field first (U196), then prev (U198) and next (U200).
        this.valueField = new EditBox(this.font, this.leftPos + 98, this.topPos + 35, 70, 10,
                Component.translatable("gui.smartinterface.value"));
        this.valueField.setMaxLength(16);   // upstream U197
        // Upstream's own key filter (U188-U190: digits, '.', 'E' plus the textbox control keys). Negative
        // values are therefore NOT enterable here, exactly as upstream — a value below zero can only come from
        // the machine's declared default. Carried over rather than widened: the requirement's range is a
        // pack's to declare, and this screen's job is to reach the values a pack can actually use.
        this.valueField.setFilter(text -> text.chars().allMatch(c ->
                Character.isDigit(c) || c == '.' || c == 'E' || c == 'e'));
        addRenderableWidget(this.valueField);

        this.prev = addRenderableWidget(Button.builder(Component.translatable("gui.smartinterface.prev"),
                        b -> step(-1))
                .bounds(this.leftPos + 7, this.topPos + 58, 40, 20).build());
        this.next = addRenderableWidget(Button.builder(Component.translatable("gui.smartinterface.next"),
                        b -> step(1))
                .bounds(this.leftPos + 129, this.topPos + 58, 40, 20).build());
    }

    /**
     * The index walk, upstream U146-U163.
     *
     * <p>Upstream's two branches differ in a way worth keeping: {@code prev} only moves when there IS an
     * earlier entry, while {@code next} WRAPS to the last entry when it runs past the end. The port keeps both,
     * including the wrap, because that asymmetry is what the jar does.</p>
     */
    private void step(int direction) {
        int bound = boundSize();
        if (direction < 0) {
            if (showing >= 1) {
                showing--;
            }
            if (showing > bound) {
                showing = bound - 1;
            }
            return;
        }
        if (bound > showing + 1) {
            showing++;
        } else {
            showing = bound - 1;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Upstream U172-U186: ENTER sends the parsed value to the server and clears the field; a value that is
        // not a number is ignored silently, which is upstream's own empty catch. The screen does NOT update its
        // own copy: the server is the authority and its answer comes back through the update tag.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER
                || keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
            TileSmartInterface iface = interfaceOnClient();
            TileSmartInterface.Data data = currentData(iface);
            if (data != null) {
                try {
                    float value = Float.parseFloat(this.valueField.getValue().trim());
                    ModularmachineryNetwork.PktSmartInterfaceUpdate.send(
                            menu.getInterfacePos(), data.getKey(), value);
                    this.valueField.setValue("");
                } catch (NumberFormatException ignored) {
                    // Upstream's own `catch (NumberFormatException ignored) {}` (U183).
                }
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** @return the interface this screen is looking at, on the client, or {@code null} before the chunk loads */
    private TileSmartInterface interfaceOnClient() {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        return level != null && level.getBlockEntity(menu.getInterfacePos())
                instanceof TileSmartInterface iface ? iface : null;
    }

    private int boundSize() {
        TileSmartInterface iface = interfaceOnClient();
        return iface == null ? 0 : iface.provideComponent().getBoundSize();
    }

    /** @return the entry the index points at, or {@code null} when this interface publishes nothing yet */
    private TileSmartInterface.Data currentData(TileSmartInterface iface) {
        return iface == null ? null : iface.provideComponent().getMachineData(showing);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    

































 
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        int offsetX = 4;
        int offsetY = 4;
        graphics.drawString(font, Component.translatable("gui.smartinterface.title",
                boundSize(), showing + 1), offsetX, offsetY, 0xFFFFFF, false);

        TileSmartInterface iface = interfaceOnClient();
        TileSmartInterface.Data data = currentData(iface);
        if (data == null) {
            // Nothing is published under this interface: upstream U59-U63 shows the same line and stops.
            graphics.drawString(font, Component.translatable("gui.smartinterface.notfound"),
                    offsetX + 3, offsetY + 12, 0xFFFFFF, false);
            return;
        }

        offsetX += 3;
        offsetY += 12;
        net.minecraft.core.BlockPos controllerPos = iface == null ? null : iface.getControllerPos();
        String machineLine = data.machineLine(
                controllerPos == null ? null : controllerPos.toShortString());
        if (machineLine != null) {
            graphics.drawString(font, Component.literal(machineLine), offsetX, offsetY, 0xFFFFFF, false);
        }

        offsetY += 10;
        graphics.drawString(font, Component.translatable(data.getHeaderInfo() == null ? ""
                : data.getHeaderInfo()), offsetX, offsetY, 0xFFFFFF, false);

        offsetY += 10;
        graphics.drawString(font, Component.literal(data.valueLine()), offsetX, offsetY, 0xFFFFFF, false);

        offsetY += 10;
        graphics.drawString(font, Component.translatable(data.getFooterInfo() == null ? ""
                : data.getFooterInfo()), offsetX, offsetY, 0xFFFFFF, false);
    }
}
