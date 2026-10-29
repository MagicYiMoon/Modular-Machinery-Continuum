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
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.common.menu.MenuParallelController;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The parallel controller screen: six adjustment buttons and a text field.
 *
 * <h2>REWRITE: the previous version was an invented design, not a mistuned one</h2>
 *
 * <p>This screen used to draw a row of sixteen pips with the hint "click a pip to set", in a 150x76 panel.
 * Its javadoc explained that in-GUI adjustment was unimplemented "because this port has no network channel",
 * and a later round added the channel and made the pips clickable — which read as progress.
 *
 * <p>The jar's {@code GuiContainerParallelController} declares:
 *
 * <pre>
 *   GuiButton increment_1, increment_10, increment_100;
 *   GuiButton decrement_1, decrement_10, decrement_100;
 *   GuiTextField textField;
 * </pre>
 *
 * <p>Six buttons and a text field. The original is not a gauge to be read; it is a control panel. Every
 * decision above was built on a screen that does not exist, and the "no network channel" note was a
 * <i>justification for an invention</i> — it made a missing feature look like a considered porting choice.
 *
 * <h2>The controls, with every coordinate read from the jar</h2>
 *
 * <p>Upstream places them relative to the <b>screen centre</b> (176x166 panel, {@code setWidthHeight()}
 * empty). Converted to panel coordinates:
 *
 * <pre>
 *   -1  (7, 26)    -10  (72, 26)    -100  (139, 26)
 *   +1  (7, 60)    +10  (72, 60)    +100  (139, 60)
 *   text field (73, 48), 95 x 10, max length 10
 * </pre>
 *
 * <p>The rows sit at y=26..46 and y=60..80 with the field at y=48..58 — <b>exactly in the gap between
 * them</b>. That is a designed control panel, and it is why these numbers are transcribed rather than
 * reasoned about: nothing about "a panel showing a value" implies three equal columns, two rows, and a field
 * sized to span the middle two.
 *
 * <h2>Its background is inherited, not declared</h2>
 *
 * <p><b>This section used to claim the opposite</b> — that "the jar's class contains no texture path at all,
 * so this screen draws no custom art" — and the empty {@code renderBg} cited it. <b>Both were wrong, and the
 * bytecode says so plainly:</b>
 *
 * <pre>
 * GuiContainerParallelController.func_146976_a  ->  TextureManager.bind(TEXTURES_EMPTY_GUI)
 * </pre>
 *
 * <p>{@code TEXTURES_EMPTY_GUI} is declared on the upstream base class, {@code GuiContainerBase} — <b>the base
 * exists precisely to supply it</b> — and §159 already identified it as
 * {@code textures/gui/guismartinterface.png}. So the class declares no texture path <i>and blits one anyway</i>.
 *
 * <p><b>That is the whole reason §74 recorded this screen as having no art</b>: the conclusion was drawn from
 * the absence of a declaration in one class, without asking what that class inherits. The port enforced it with
 * an empty {@code renderBg} and a comment, and the client reported the result as "界面没了" — the buttons and
 * labels drew over the world with nothing behind them. */
public class ScreenParallelController extends AbstractContainerScreen<MenuParallelController> {

    // Sourced from the menu so there is ONE declaration per screen, and so the self-test can assert it --
    // the screen itself is client-side and unreachable from a dedicated server.
    public static final int PANEL_WIDTH = MenuParallelController.PANEL_WIDTH;
    public static final int PANEL_HEIGHT = MenuParallelController.PANEL_HEIGHT;

    /** Button geometry, all read from {@code initGui}. */
    private static final int BUTTON_WIDTH = 30;
    private static final int BUTTON_HEIGHT = 20;
    private static final int ROW_DECREMENT_Y = 26;
    private static final int ROW_INCREMENT_Y = 60;
    private static final int COLUMN_1_X = 7;
    private static final int COLUMN_10_X = 72;
    private static final int COLUMN_100_X = 139;

    /** Text field geometry. */
    private static final int FIELD_X = 73;
    private static final int FIELD_Y = 48;
    private static final int FIELD_WIDTH = 95;
    private static final int FIELD_HEIGHT = 10;

    private EditBox valueField;

    public ScreenParallelController(MenuParallelController menu, Inventory playerInventory,
                                    Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();

        // The decrement row.
        addDeltaButton(COLUMN_1_X, ROW_DECREMENT_Y, -1, "-1");
        addDeltaButton(COLUMN_10_X, ROW_DECREMENT_Y, -10, "-10");
        addDeltaButton(COLUMN_100_X, ROW_DECREMENT_Y, -100, "-100");

        // The increment row.
        addDeltaButton(COLUMN_1_X, ROW_INCREMENT_Y, 1, "+1");
        addDeltaButton(COLUMN_10_X, ROW_INCREMENT_Y, 10, "+10");
        addDeltaButton(COLUMN_100_X, ROW_INCREMENT_Y, 100, "+100");

        // The text field, for an exact value. maxLength 10 matches the jar's setMaxStringLength(10).
        valueField = new EditBox(font, leftPos + FIELD_X, topPos + FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("gui.parallelcontroller.current_value"));
        valueField.setMaxLength(10);
        valueField.setValue(Integer.toString(menu.getParallelism()));
        addRenderableWidget(valueField);
    }

    /**
     * Adds one adjustment button.
     *
     * <p>The labels are literal strings, matching the jar — upstream does not translate them either, which
     * is why the class carries no lang key for them.
     *
     * <p>Each button sends the <b>resulting absolute value</b> rather than the delta. The delta is a client
     * notion; the server has no business applying "add 10" to a value that may have changed in flight, and
     * sending the intended total means the server-side clamp (§58) acts on a number the player actually
     * asked for.
     */
    private void addDeltaButton(int x, int y, int delta, String label) {
        addRenderableWidget(Button.builder(Component.literal(label), button -> {
             
            //
            // This used to send and display the raw `menu.getParallelism() + delta` and set the field to
            // it, with a comment claiming "the server's value overwrites it on the next sync, and it
            // clamps, so nothing here can push the block out of range". That reasoning has a hole:
            //
            //   the server clamps 1 - 10 = -9 up to 1. Its value was ALREADY 1, so it does not change, so
            //   DataSlot sends no update, so the optimistic -9 is never overwritten. The client then shows
            //   -9 for a controller that is at 1.
            //
            // Which is exactly what the client reported: "-10 gives -9, -100 gives -99".
            //
            // Calling the server's own clamp keeps ONE definition of the rule, so the optimistic value and
            // the authoritative one cannot disagree.
            int target = ModularmachineryNetwork.PktSetParallelism.clampParallelism(
                    menu.getParallelism() + delta, menu.getMaxParallelism());
            ModularmachineryNetwork.PktSetParallelism.send(menu.getControllerPos(), target);
            valueField.setValue(Integer.toString(target));
        }).bounds(leftPos + x, topPos + y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
    }

    /**
     * {@inheritDoc}
     *
     * <p>Submits the typed value on Enter. Empty or unparseable input is ignored rather than sent as 0 —
     * clearing the field to retype a number should not silently zero the controller.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (valueField != null && valueField.isFocused()
                && (keyCode == 257 || keyCode == 335)) {   // Enter, keypad Enter
            String text = valueField.getValue().trim();
            try {
                // The same clamp on the typed path, so a typed -50 cannot sit in the field either.
                ModularmachineryNetwork.PktSetParallelism.send(menu.getControllerPos(),
                        ModularmachineryNetwork.PktSetParallelism.clampParallelism(
                                Integer.parseInt(text), menu.getMaxParallelism()));
            } catch (NumberFormatException ignored) {
                // Leave the field as typed; the player is mid-edit.
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** §159: the base class's {@code TEXTURES_EMPTY_GUI}, which is the smart interface's own file. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation("modularmachinery", "textures/gui/guismartinterface.png");

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // The jar's GuiContainerParallelController binds TEXTURES_EMPTY_GUI in its background method. See the
        // class javadoc: it declares no texture of its own and inherits one, and this screen used to draw
        // nothing because that inheritance was not looked for.
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
        // The maximum, as upstream shows it -- without it the current value has no scale to read against.
        //
        // §201: this was drawn at y=88, and the panel is 166 tall with the player's rows at 84, 102 and 120 --
        // so the line sat INSIDE the first inventory row. The client reported it as the text having gone below
        // the inventory.
        //
        // The vertical budget on a 166-tall panel is: title 6..15, decrement row 26..46, increment row 60..80,
        // player rows from 84. There is no free line below the buttons, so the only gap that fits is the one
        // between the title and the first row -- y=16, nine pixels tall, clearing 26.
        graphics.drawString(font,
                Component.translatable("gui.parallelcontroller.max_value", menu.getMaxParallelism()),
                8, 16, 0x707070, false);
    }
}