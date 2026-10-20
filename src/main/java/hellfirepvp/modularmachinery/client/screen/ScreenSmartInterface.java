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

/**
 * The smart interface screen: the mod's own art, the player inventory, and the ONE thing that makes the
 * channel writable — a value field whose Enter sends {@code PktSmartInterfaceUpdate}.
 *
 * <h2>How the texture was found, since the obvious lookup returns nothing</h2>
 *
 * <p>{@code GuiContainerSmartInterface} contains <b>no texture path of its own</b> — reading its constant
 * pool for {@code textures/gui/*.png} comes back empty, and the natural conclusion is that the asset is
 * missing or the class was never given one.</p>
 *
 * <p>It is not missing. <b>{@code GuiContainerBase} declares {@code guismartinterface.png}</b> as its own
 * default, and this screen inherits it. So the base class carries a subclass's texture, which is unusual but
 * is what the jar does.</p>
 *
 * <p><b>A class with no texture string is not necessarily missing one — check the base before "fixing"
 * it.</b> Registering a different texture here would have replaced correct art with a guess.</p>
 *
 * <h2>PLAN 663: what was added, and the upstream body each part comes from</h2>
 *
 * <p>Until this round the screen drew <b>only</b> the background and the player inventory, so the interface's
 * whole purpose — publishing a value a recipe can read — had no human-facing path at all: the value published
 * at formation time was the only value that could ever exist. Every control below is upstream's
 * {@code GuiContainerSmartInterface} (203 lines) read in full:</p>
 *
 * <pre>
 * U54   the title: bound count and which entry is shown          gui.smartinterface.title
 * U58-U60  nothing bound -> "Not Found Bound Machine"            gui.smartinterface.notfound
 * U78-U98  the machine and its position, then the declared type's header / value / footer lines
 * U116-U120 the text field, drawn in the BACKGROUND pass because it is not a widget there
 * U129-U163 the prev/next buttons and the index they walk        gui.smartinterface.prev / .next
 * U166-U191 the key handling: Enter sends the parsed value, digits/'.'/'E' go to the field
 * </pre>
 *
 * <p><b>THE ONE BEHAVIOUR THAT MOVED AND WHY IT HAD TO:</b> the value's WIDGETS are created in
 * {@code init} and registered with {@code addRenderableWidget}, so 1.20.1 draws and ticks them itself — the
 * manual {@code drawButton}/{@code mousePressed}/{@code updateScreen} calls upstream makes (U117-U120,
 * U146-U163, U124-U127) have no counterpart in this API and are not translated. The BUTTONS' positions are
 * upstream's own numbers, converted from screen space to panel space: upstream places them at
 * {@code width / 2 - 81} and {@code width / 2 + 41} at {@code height / 2 - 25}, and
 * {@code width / 2 == leftPos + imageWidth / 2} for a 176-wide panel, so those are
 * {@code leftPos + 7} and {@code leftPos + 129} at {@code topPos + 58}. The text field is likewise
 * {@code width / 2 + 10, height / 2 - 48} → {@code leftPos + 98, topPos + 35}.</p>
 *
 * <h2>What this screen does NOT reach, stated rather than implied</h2>
 *
 * <p>The value shown comes from the CLIENT's copy of the interface, which the server pushes through the block
 * entity's update tag ({@code markForUpdate} on every write). That is this port's substitute for upstream's
 * two hand-written packets, and it is the same information over the same kind of connection — but the
 * server-to-client half is not separately observable in this project's headless harness (PLAN 663.6), so the
 * claim that a player sees the number they typed is reasoned from one serialisation path and not measured.</p>
 *
 * <h2>PLAN 674 — THE DISPLAY HALF NOW TRAVELS IN THAT SAME TAG, AND WHY IT HAD TO</h2>
 *
 * <p>PLAN 663's revision drew the machine name and the type's header/value/footer from a CLIENT-SIDE lookup
 * in the machine-definition loader ({@code owningMachine}), keyed by the controller's machine name. <b>That
 * reader had no producer on a client:</b> the definitions are loaded only through
 * {@code AddReloadListenerEvent}, which Forge 47.3.0 posts from
 * {@code ForgeEventFactory.onResourceReload(ReloadableServerResources, RegistryAccess)} — a server-side path,
 * so a client connected to a server has an EMPTY loader and the lookup returned {@code null}. The consequence
 * was not a missing label: {@code renderLabels} returned BEFORE THE VALUE LINE, so the number a player had
 * just typed was stored, persisted and synced — and never drawn, on every reopen.</p>
 *
 * <p>Every line this screen draws therefore comes out of the DATUM now (see
 * {@link TileSmartInterface.Data#setDisplay}), which is the object the value itself lives in and the tag the
 * value itself arrives in: one channel, one producer (the formation walk, which holds the parsed declaration),
 * one reader (the player). This screen no longer touches the definition loader at all, and
 * {@link #renderLabels}'s javadoc names the upstream lines each part reproduces.</p>
 */
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

    /**
     * The four text lines, upstream U45-U101 — <b>all four read out of the DATUM, which is the same object the
     * value lives in and the same tag the value arrived in.</b>
     *
     * <p><b>PLAN 674 — THE DEFECT THIS REWRITE FIXES, AND WHY THE OLD SHAPE COULD NOT DRAW ANYTHING.</b> Until
     * this round the machine name line and the declared type's header/value/footer came from
     * {@code owningMachine(...)}, a CLIENT-SIDE lookup in {@code MachineReloadListener}'s definition loader
     * keyed by the controller's machine name. That loader is filled by
     * {@code MachineReloadListener.onAddReloadListeners}, i.e. by {@code AddReloadListenerEvent} — which Forge
     * 47.3.0 posts exclusively from
     * {@code ForgeEventFactory.onResourceReload(ReloadableServerResources, RegistryAccess)}, a SERVER-side
     * path (verified against the mapped jar, PLAN 674). On a client connected to a server the loader is
     * therefore EMPTY, {@code owningMachine} returned {@code null}, and this method RETURNED BEFORE DRAWING
     * THE VALUE LINE. The number a player typed was stored, persisted and synced correctly — and never
     * visible, on every reopen. <b>Rendering the value must not depend on a definition the client does not
     * have.</b></p>
     *
     * <p>So the display half now travels WITH the value (see {@code TileSmartInterface.Data}'s
     * {@code setDisplay}), which is upstream's own carrier: upstream's datum held {@code parent} — the
     * declaring machine — for exactly this reason ({@code GuiContainerSmartInterface} U65), and upstream's
     * client could resolve the definition only because a 1.12.2 mod loads its config on both sides.
     * <b>The header and footer are LANG KEYS upstream passes through {@code I18n.format} (U83/U98)</b>, so
     * carrying the key — not the resolved text — is what keeps them translated in the PLAYER'S language,
     * which the server cannot know. The value format is the pack's {@code String.format} argument (U88).</p>
     *
     * <p><b>THE LAYOUT IS UNCHANGED, AND IT IS UPSTREAM'S:</b> title at (4, 4), then the machine line at
     * (7, 16), the header at (7, 26), the value at (7, 36) and the footer at (7, 46) — upstream's own
     * {@code offsetX += 3} / four {@code offsetY += 10} steps, which is also what the previous port revision
     * computed. The ONE line whose position is load-bearing is the value line, and it is now drawn whenever a
     * datum exists — it is no longer gated on anything the client might lack.</p>
     *
     * <p>A value format with the wrong conversion is a pack's typo and must not take the screen down:
     * {@code Data#valueLine()} keeps upstream's guard (U87-U94) and falls back to the mod's own
     * {@code gui.smartinterface.value} line, so the number is always on screen.</p>
     */
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
