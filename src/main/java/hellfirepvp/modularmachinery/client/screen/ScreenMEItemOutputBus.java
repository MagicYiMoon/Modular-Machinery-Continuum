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
import hellfirepvp.modularmachinery.common.menu.MenuMEItemOutputBus;
import appeng.client.gui.MathExpressionParser;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Optional;

/**
 * The ME ITEM OUTPUT bus screen — port of upstream's {@code GuiMEItemOutputBus} (77 lines) together with the eight
 * buttons of {@code GuiMEItemOutputBusStackSize} (317 lines), and <b>the direction that had no screen at all</b>
 * (AUDIT_COMPONENTS row 11).
 *
 * <h2>What upstream's two screens are, read at the body (rule A)</h2>
 *
 * <pre>
 *   GuiMEItemOutputBus (U20-U77)
 *     U21  TEXTURES_OUTPUT_BUS = appliedenergistics2:textures/guis/skychest.png
 *     U29  this.ySize = 195
 *     U36-U40  a wrench button at (guiLeft - 18, guiTop + 8) whose tooltip is gui.meitembus.stack_size.config
 *     U48-U51  its click sends PktSwitchGuiMEOutputBus(pos, 1)   -> the SECOND screen
 *     U56  the title gui.meitemoutputbus.title at (8, 8) and the inventory label at (8, ySize - 96 + 2)
 *
 *   GuiMEItemOutputBusStackSize (U40-U317)
 *     U25  TEXTURES = modularmachinery:textures/gui/stacksize.png        <- THE PORT'S OWN ASSET, already shipped
 *     U43  this.ySize = 105
 *     U51-U59  eight buttons: +1/+10/+100/+1000 at (20/48/82/120, 32) and -1/-10/-100/-1000 at the same x, y=69
 *     U74  a text field at (62, 57), 75 wide    U277  the label gui.meitembus.stack_size.name at (8, 6)
 *     U146-U150 every button sends PktMEOutputBusStackSizeChange(pos, newValue) -- the ABSOLUTE value
 * </pre>
 *
 * <h2>RULE A0 — why this is ONE screen, and what that changes (three parts)</h2>
 *
 * <p><b>(1) Upstream's method:</b> two GUIs, switched by the wrench button's packet; the eight buttons adjust a text
 * field whose contents are parsed (an AE2 expression parser, for input like {@code 4*64}) and sent as an absolute value;
 * the container writes it back on close. The chrome is AE2's ({@code GuiImgButton} for the wrench, a
 * {@code GuiTabButton} back, {@code AEBaseGui} for the screen itself).</p>
 *
 * <p><b>(2) Evidence it cannot be followed on 1.20.1 / AE2 15.4.11</b> — measured by entry name against the jar this
 * project compiles against: {@code appeng/client/gui/AEBaseGui.class}, {@code appeng/client/gui/widgets/GuiImgButton}
 * and {@code GuiTabButton} have ZERO entries; {@code appeng/container/} (AEBaseContainer, the 1.12.2 chrome) is absent.
 * {@code appeng/client/gui/MathExpressionParser} DOES exist — so the typed-expression half is the one piece that could
 * have been taken, and it is not: see the menu's rule A0 block.</p>
 *
 * <p><b>(3) What the port does instead:</b> one screen on the input bus's panel, the eight buttons upstream's second
 * screen draws (drawn on this one, at the same semantics), the value shown through the MENU's data slot rather than a
 * {@code @GuiSync} field, and each click sending a DELTA the server clamps and applies
 * ({@code PktMEItemOutputBusStackSize}). <b>The wrench button and the switch packet are gone with the second GUI</b>:
 * there is nothing left to switch to, and the button that edits the value is on the same panel as the value.</p>
 *
 * <h2>What the panel is, and the one place it is narrower than upstream</h2>
 *
 * <p>The panel is {@code meiteminputbus.png} — the port's own art, the same file the input bus's screen blits — and the
 * 36 read-only slots land on the 9x4 well band it draws at (8 + 18x, 123 + 18y). Upstream's art is AE2's 1.12.2
 * {@code skychest.png}, which AE2 15.4.11 does not have (measured: {@code ae2:textures/guis/chest.png} draws ONE
 * non-player well at (79, 39), and its four 18px rows are the player inventory's). <b>The player's own rows are
 * therefore not drawn</b> — see {@code MenuMEItemOutputBus}'s javadoc for why that changes nothing a player could do
 * here (upstream's 36 slots are disabled too) and why it is recorded rather than hidden.</p>
 *
 * <p><b>AND THIS FILE HAS NEVER BEEN RENDERED.</b> This harness is a dedicated server and cannot draw. What IS verified
 * is that it compiles, that its panel size and slot grid are the menu's own constants, and that its
 * {@code render} reaches {@code renderTooltip} (the defect row 45 is about). The pixels are the user's to judge.</p>
 */
public class ScreenMEItemOutputBus extends AbstractContainerScreen<MenuMEItemOutputBus> {

    /** The port's own panel, the same file {@code ScreenMEItemInputBus} blits — see the class javadoc. */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/meiteminputbus.png");

    /** The title row, upstream U56: the title at (8, 8). */
    private static final int TITLE_Y = 6;

    /**
     * The stack-size control, on the two 4x4 bands the art draws above the well band.
     *
     * <p>Eight buttons, 20x20, two rows of four at {@code x = 8 + 22i} and {@code y = 38 / 62} — <b>upstream's own
     * eight and upstream's own order</b> (+1/+10/+100/+1000 on the first row of its second screen, the minus row below
     * it), placed where this panel has room rather than at upstream's (20/48/82/120, 32/69), which belong to a 176x105
     * panel that is not on this screen.</p>
     */
    private static final int BUTTON_SIZE = 20;
    private static final int BUTTON_X = 8;
    private static final int BUTTON_ROW_ONE_Y = 38;
    private static final int BUTTON_ROW_TWO_Y = 62;
    private static final int BUTTON_PITCH = 22;

    /** The steps, in the order they are drawn: the first row adds, the second subtracts. Upstream's own eight. */
    private static final int[] BUTTON_STEPS = {1, 10, 100, 1000, -1, -10, -100, -1000};

    /** Where the cap's name and its current value go: one row under the title, above the buttons. */
    private static final int STACK_SIZE_LABEL_X = 8;
    private static final int STACK_SIZE_LABEL_Y = 22;

    /** The gap between the label and the number on that row. */
    private static final int VALUE_GAP = 6;

    /** Source parity: a typed number or arithmetic expression can set the cap directly. */
    private static final int FIELD_X = 8;
    private static final int FIELD_Y = 88;
    private static final int FIELD_WIDTH = 160;
    private static final int FIELD_HEIGHT = 18;

    private EditBox stackSizeField;

    public ScreenMEItemOutputBus(MenuMEItemOutputBus menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Taken FROM the menu, per §193: a screen that states its own size can drift from the menu that placed its slots.
        this.imageWidth = MenuMEItemOutputBus.PANEL_WIDTH;
        this.imageHeight = MenuMEItemOutputBus.PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        stackSizeField = new EditBox(font, leftPos + FIELD_X, topPos + FIELD_Y, FIELD_WIDTH, FIELD_HEIGHT,
                Component.translatable("gui.meitembus.stack_size.name"));
        stackSizeField.setMaxLength(32);
        stackSizeField.setValue(Integer.toString(menu.getConfiguredStackSize()));
        addRenderableWidget(stackSizeField);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (stackSizeField != null && stackSizeField.isFocused() && (keyCode == 257 || keyCode == 335)) {
            sendTypedValue();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (stackSizeField != null && !stackSizeField.isFocused()) {
            String authoritative = Integer.toString(menu.getConfiguredStackSize());
            if (!authoritative.equals(stackSizeField.getValue())) {
                stackSizeField.setValue(authoritative);
            }
        }
    }

    private void sendTypedValue() {
        Integer value = parseStackSize(stackSizeField.getValue());
        if (value == null) {
            return;
        }
        stackSizeField.setValue(Integer.toString(value));
        hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktMEItemOutputBusStackSizeSet
                .send(menu.getBusPos(), value);
    }

    /** Parses the operators accepted by AE2's source stack-size screen and applies its 1..Integer.MAX_VALUE clamp. */
    private static Integer parseStackSize(String text) {
        String expression = text == null ? "" : text.trim();
        if (expression.isEmpty()) {
            return null;
        }
        try {
            // The 1.12 screen allowed scientific notation in addition to AE2's operators. AE2's 1.20 parser
            // intentionally has no E token, so normalize the same notation to its supported power operator first.
            expression = expression.replaceAll("(?i)(\\d+(?:\\.\\d+)?)[e]([+-]?\\d+)", "($1*10^$2)");
            DecimalFormat format = new DecimalFormat("0.############################");
            format.setParseBigDecimal(true);
            Optional<BigDecimal> parsed = MathExpressionParser.parse(expression, format);
            if (parsed.isEmpty()) {
                return null;
            }
            BigDecimal value = parsed.get().setScale(0, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.ONE) < 0) {
                return 1;
            }
            BigDecimal max = BigDecimal.valueOf(Integer.MAX_VALUE);
            return value.compareTo(max) > 0 ? Integer.MAX_VALUE : value.intValue();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);
        for (int index = 0; index < BUTTON_STEPS.length; index++) {
            int x = leftPos + buttonX(index);
            int y = topPos + buttonY(index);
            boolean hovered = inside(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE);
            // THE PLATE IS DRAWN WITH `fill`, NOT BLITTED FROM THE PANEL, AND THE REASON IS A MEASUREMENT: the pixel
            // this used to blit -- (144, 216) of meiteminputbus.png, outside the art's 176x204 panel -- is FULLY
            // TRANSPARENT (alpha 0), so a button drawn from it would have been text floating over the background with
            // no plate at all. The art's own wells are not free either: the 36 slots sit on the lower band and the two
            // upper bands are where these buttons are. No widget texture is invented; a vanilla-looking plate is
            // filled, which is the one thing that cannot be wrong about its own pixels.
            graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, hovered ? 0xFF7B7B7B : 0xFF8B8B8B);
            graphics.fill(x, y, x + BUTTON_SIZE, y + 1, 0xFF373737);
            graphics.fill(x, y, x + 1, y + BUTTON_SIZE, 0xFF373737);
            graphics.fill(x + BUTTON_SIZE - 1, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFFFFFFFF);
            graphics.fill(x, y + BUTTON_SIZE - 1, x + BUTTON_SIZE, y + BUTTON_SIZE, 0xFFFFFFFF);
            String label = buttonLabel(index);
            graphics.drawString(font, label, x + (BUTTON_SIZE - font.width(label)) / 2, y + 6,
                    hovered ? 0xFFFFFF : 0x080808, false);
        }
    }

    /** @return the x of one button, from the row it is in; one statement so the hitbox and the drawing cannot drift */
    private static int buttonX(int index) {
        return BUTTON_X + BUTTON_PITCH * (index % 4);
    }

    /** @return the y of one button */
    private static int buttonY(int index) {
        return index < 4 ? BUTTON_ROW_ONE_Y : BUTTON_ROW_TWO_Y;
    }

    /** @return the text on one button, from the step it sends -- the number is NOT a second copy of the step */
    private static String buttonLabel(int index) {
        int step = BUTTON_STEPS[index];
        return (step > 0 ? "+" : "-") + (step == Integer.MIN_VALUE ? "" : Math.abs(step));
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.meitemoutputbus.title"), 8, TITLE_Y, 0x404040, false);
        // THE ONE LABEL UPSTREAM'S SCREEN DOES NOT HAVE ON THE SAME PANEL, and the reason it is here: upstream's
        // value lives in a second GUI with its own title ("Max Stack Size", gui.meitembus.stack_size.name, G277).
        // With the two screens collapsed into one, this is the label that says what the eight buttons edit -- and the
        // number is placed FROM the label's own measured width rather than at a second hardcoded x, so the two are
        // never drawn on top of each other in a language whose translation is longer than English's.
        Component label = Component.translatable("gui.meitembus.stack_size.name");
        graphics.drawString(font, label, STACK_SIZE_LABEL_X, STACK_SIZE_LABEL_Y, 0x404040, false);
        graphics.drawString(font, String.format("%,d", menu.getConfiguredStackSize()),
                STACK_SIZE_LABEL_X + font.width(label) + VALUE_GAP, STACK_SIZE_LABEL_Y, 0x404040, false);
    }

    










 
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * The buttons' own lines, in the shape {@code ScreenMEPatternProvider}'s tooltip uses: the hovered button's meaning,
     * then the count the bus will keep.
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int index = 0; index < BUTTON_STEPS.length; index++) {
            int x = leftPos + buttonX(index);
            int y = topPos + buttonY(index);
            if (inside(mouseX, mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE)) {
                graphics.renderTooltip(font, Component.translatable("gui.meitembus.stack_size.config"), mouseX, mouseY);
                return;
            }
        }
        for (Slot slot : menu.slots) {
            // The mouse is tested against the SLOT's own x/y -- the position the slot was drawn at -- rather than
            // against a hook that could hold a second copy of it.
            if (mouseX < leftPos + slot.x || mouseX >= leftPos + slot.x + 16
                    || mouseY < topPos + slot.y || mouseY >= topPos + slot.y + 16) {
                continue;
            }
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                // The same line the input bus's STORED grid draws (upstream U181-U198), and it is needed for the same
                // reason: an output bus's slots hold network quantities, so a count of 832 is not readable off the icon.
                graphics.renderTooltip(font, Component.translatable("gui.meitembus.item_cached",
                        String.format("%,d", stack.getCount())), mouseX, mouseY);
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>{@code super.mouseClicked} is what routes a click to a SLOT</b>, so the only correct shape is "handle the
     * button, else fall through" — the same shape {@code ScreenMEPatternProvider.mouseClicked} uses, and the reason the
     * click is not handled in {@code renderBg} where the button is drawn.</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // The source second screen commits the text field when its GUI closes. In the collapsed 1.20 screen,
        // clicking another control is the equivalent focus transition, so commit before handling that click.
        if (stackSizeField != null && stackSizeField.isFocused()
                && !inside((int) mouseX, (int) mouseY, leftPos + FIELD_X, topPos + FIELD_Y,
                FIELD_WIDTH, FIELD_HEIGHT)) {
            sendTypedValue();
            stackSizeField.setFocused(false);
        }
        if (button == 0) {
            for (int index = 0; index < BUTTON_STEPS.length; index++) {
                int x = leftPos + buttonX(index);
                int y = topPos + buttonY(index);
                if (inside((int) mouseX, (int) mouseY, x, y, BUTTON_SIZE, BUTTON_SIZE)) {
                    // THE DELTA, not the value: the server computes the new cap from the cap it holds, so a client
                    // whose display is a tick stale cannot install a number the bus never agreed to
                    // (PktMEItemOutputBusStackSize's javadoc).
                    hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork.PktMEItemOutputBusStackSize
                            .send(menu.getBusPos(), BUTTON_STEPS[index]);
                    if (stackSizeField != null) {
                        long wanted = (long) menu.getConfiguredStackSize() + BUTTON_STEPS[index];
                        wanted = Math.max(1L, Math.min((long) Integer.MAX_VALUE, wanted));
                        stackSizeField.setValue(Long.toString(wanted));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        sendTypedValue();
        super.onClose();
    }

    /** @return whether a point is inside a rectangle, the same test {@code ScreenMEPatternProvider} makes */
    private static boolean inside(int pointX, int pointY, int x, int y, int width, int height) {
        return pointX >= x && pointX < x + width && pointY >= y && pointY < y + height;
    }
}
