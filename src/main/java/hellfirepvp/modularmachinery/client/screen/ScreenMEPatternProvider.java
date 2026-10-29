/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.menu.MenuMEPatternProvider;
import hellfirepvp.modularmachinery.common.network.ModularmachineryNetwork;
import hellfirepvp.modularmachinery.common.tiles.me.TileMEPatternProvider;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;








































































 
public class ScreenMEPatternProvider extends AbstractContainerScreen<MenuMEPatternProvider> {

     
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/mepatternprovider.png");

    /** Upstream's label anchors (G61, G67, G73, G79), panel-relative. */
    private static final int TITLE_X = 7;
    private static final int TITLE_Y = 11;
    private static final int CACHED_X = 180;
    private static final int CACHED_Y = 11;
    private static final int INVENTORY_X = 7;
    private static final int INVENTORY_Y = 101;
    private static final int SINGLE_INV_X = 180;
    private static final int SINGLE_INV_Y = 158;

    /** Upstream's "?" tip button (G161-G169): 9 x 11 at (240, 157), art at (230, 214), hovered at (241, 214). */
    private static final int TIP_X = 240;
    private static final int TIP_Y = 157;
    private static final int TIP_WIDTH = 9;
    private static final int TIP_HEIGHT = 11;
    private static final int TIP_U = 230;
    private static final int TIP_V = 214;
    private static final int TIP_HOVERED_U = 230 + 11;

    /**
     * The two top-left action buttons, of which §729 drew NOTHING and recorded why.
     *
     * <p>Upstream builds them as one {@code Row} at {@code absXY(215, 7)} (G172-G173) holding the return-items button
     * first and the work-mode button second, and the two cells are 16 x 16 with a 2-pixel right margin (G101, G155,
     * G173).</p>
     *
     * <p><b>DRAWN NOW, AND THE TEST FOR DRAWING THEM IS NOT THAT UPSTREAM HAS THEM.</b> §729's own sentence is the
     * test: "a button drawn with no consumer is worse than a missing button, and this project has a name for the
     * class". Both now have a consumer -- {@code PktMEPatternProviderAction} carries their two actions, the tile
     * applies them, and the features behind them (the shared independent storage of
     * {@code InfItemFluidHandler.machineVisibleItems}, and the work-mode enum of
     * {@code WorkModeSetting}) exist and are asserted. Recorded this way round because the
     * widgets were once deliberately absent and a reader will ask what changed.</p>
     */
    private static final int RETURN_U = 176;
    private static final int RETURN_V = 214;
    private static final int RETURN_HOVERED_U = 176 + 18;
    private static final int RETURN_DOWN_U = 176 + 18 + 18;
    private static final int BUTTON_SIZE = 16;

    /** The button row's origin, upstream G173's {@code absXY(215, 7)}, and its 2-pixel right margin. */
    private static final int BUTTON_ROW_X = 215;
    private static final int BUTTON_ROW_Y = 7;
    private static final int BUTTON_MARGIN_RIGHT = 2;

    /**
     * The work-mode button's art: one 16 x 16 sprite per mode, laid out with the DEFAULT at 140 and the offsets
     * upstream chose (G106-G113) -- {@code 140 +- 18n} -- plus the three generic states at {@code 140 + 18 * 3, 4, 5}
     * (G115-G119).
     *
     * <pre>
     *   DEFAULT                 (140, 196)        G106
     *   BLOCKING_MODE           (158, 196)  +18   G108
     *   CRAFTING_LOCK_MODE      (176, 196)  +36   G110
     *   ENHANCED_BLOCKING_MODE  (122, 196)  -18   G112
     *   ISOLATION_INPUT         (104, 196)  -36   G113
     *   the button's own plate  (194, 196)  +54   G119   (setTexture)
     *   hovered                 (212, 196)  +72   G117
     *   pressed                 (230, 196)  +90   G115
     * </pre>
     *
     * <p><b>The per-mode U is DERIVED from the mode's ordinal rather than looked up in a switch</b>, and that
     * derivation is monotone only because upstream's enum order and its sprite order happen to agree. That is exactly
     * why the table above exists and why {@code WorkModeSetting}'s own javadoc records that the order is a SAVE FORMAT
     * -- a reordered enum would silently reinterpret every saved provider AND move every icon here.</p>
     */
    private static final int WORK_MODE_U_BASE = 140;
    private static final int WORK_MODE_V = 196;
    private static final int WORK_MODE_TEXTURE_U = WORK_MODE_U_BASE + 18 * 3;
    private static final int WORK_MODE_HOVERED_U = WORK_MODE_U_BASE + 18 * 4;
    private static final int WORK_MODE_DOWN_U = WORK_MODE_U_BASE + 18 * 5;

    /**
     * The two buttons' tooltip text <b>is not written here</b>, and that is a consequence of what the self-test has to
     * be able to reach: this class is a CLIENT class, and the harness is a dedicated server that can only read its
     * bytes (§481/§708). {@code MEPatternProviderTooltips} holds the keys, the marker and both line lists, so the
     * screen and the check build the SAME lines -- a copy here would have let the check pass on a screen that asked
     * for something else.
     *
     * <p>What used to be here was this screen's own private array of five keys plus the marker constant. It was
     * correct and had NO READER: the only method that used it was {@code renderTooltip}, which nothing called (see
     * the {@code render} override below).</p>
     */
    private boolean tipHovered;
    private boolean returnHovered;
    private boolean workModeHovered;
    private int cacheScroll;
    private int hoveredCache = -1;

    private sealed interface CacheEntry permits ItemCacheEntry, FluidCacheEntry {
    }

    private record ItemCacheEntry(ItemStack stack) implements CacheEntry {
    }

    private record FluidCacheEntry(FluidStack stack) implements CacheEntry {
    }

    public ScreenMEPatternProvider(MenuMEPatternProvider menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        // Taken FROM the menu, per §193: a screen that states its own size can drift from the menu that placed
        // its slots. Upstream's own numbers (G49-G50) are the menu's constants now.
        this.imageWidth = MenuMEPatternProvider.PANEL_WIDTH;
        this.imageHeight = MenuMEPatternProvider.PANEL_HEIGHT;
    }

    /** One rectangle test, so a button's hitbox and its drawing cannot drift apart. */
    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    /** @return the work-mode button's x, from the row origin, so the two buttons cannot overlap after an edit */
    private static int workModeButtonX() {
        return BUTTON_ROW_X + BUTTON_SIZE + BUTTON_MARGIN_RIGHT;
    }

    


























 
    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        // THE INDEPENDENT STORAGE'S FLUID BARREL, at upstream's own cell (G187-G189: 232, 172, 16 x 16).
        //
        // WHAT UPSTREAM DRAWS THERE is GuiFullCapFluidTank, an AE2 GuiFluidTank subclass. WHAT CANNOT BE FOLLOWED:
         
        // widget does not exist and neither does the IGuiFluidStackHandler it draws through. WHAT IS DONE INSTEAD: the
        // tank's contents are drawn directly in the cell the art already frames -- the art cuts the well and the "F",
        // so this adds the fluid and nothing else.
        drawBarrel(graphics);
        drawCachedStacks(graphics, mouseX, mouseY);

        // The "?" tip (upstream G158-G169). The hovered art is chosen locally, exactly as Button does upstream.
        boolean hovered = inside(mouseX, mouseY, leftPos + TIP_X, topPos + TIP_Y, TIP_WIDTH, TIP_HEIGHT);
        tipHovered = hovered;
        graphics.blit(TEXTURE, leftPos + TIP_X, topPos + TIP_Y, hovered ? TIP_HOVERED_U : TIP_U, TIP_V,
                TIP_WIDTH, TIP_HEIGHT);

        // The two action buttons: the plate first, then the mode's own icon on top of it, which is what upstream's
        // ButtonElements does with its ButtonTexture 4 backing the five per-mode sprites (G106-G119).
        returnHovered = inside(mouseX, mouseY, leftPos + BUTTON_ROW_X, topPos + BUTTON_ROW_Y, BUTTON_SIZE,
                BUTTON_SIZE);
        workModeHovered = inside(mouseX, mouseY, leftPos + workModeButtonX(), topPos + BUTTON_ROW_Y, BUTTON_SIZE,
                BUTTON_SIZE);

        graphics.blit(TEXTURE, leftPos + BUTTON_ROW_X, topPos + BUTTON_ROW_Y,
                returnHovered ? RETURN_HOVERED_U : RETURN_U, RETURN_V, BUTTON_SIZE, BUTTON_SIZE);

        int modeOrdinal = currentModeOrdinal();
        graphics.blit(TEXTURE, leftPos + workModeButtonX(), topPos + BUTTON_ROW_Y,
                workModeHovered ? WORK_MODE_HOVERED_U : WORK_MODE_TEXTURE_U, WORK_MODE_V, BUTTON_SIZE, BUTTON_SIZE);
        graphics.blit(TEXTURE, leftPos + workModeButtonX(), topPos + BUTTON_ROW_Y,
                WORK_MODE_U_BASE + 18 * modeOrdinal, WORK_MODE_V, BUTTON_SIZE, BUTTON_SIZE);
    }

    /**
     * The mode the provider is in, as the MENU's data slot reports it, <b>as the constant itself</b>.
     *
     * <p><b>Read from the menu and not from the tile, and that is what the data slot is for:</b> the client tile's
     * field is whatever the last update tag carried, while the data slot is refreshed by
     * {@code AbstractContainerMenu#broadcastChanges} every tick. Upstream reads {@code owner.getWorkMode()} (G245)
     * because its GUI holds the tile directly; this port's convention is the one {@code ScreenMEFluidInputBus} uses,
     * and the menu exposes exactly one door for this number.</p>
     *
     * <p><b>AND A NUMBER THE SCREEN DOES NOT KNOW BECOMES {@code DEFAULT} RATHER THAN AN INDEX.</b> A menu whose data
     * slot has not been filled yet reads 0, and a future sixth mode would read 5 -- either would step off the sprite
     * sheet. {@code WorkModeSetting.fromOrdinal} is that clamp, and it is the enum's own reader rather than a second
     * copy of the range test here; {@code mouseClicked} resolves the next mode through the same reader, so a value
     * outside the range advances from DEFAULT rather than from a fictional mode.</p>
     *
     * <p><b>ONE READER, TWO USES:</b> the button's own sprite and the marker on the tooltip's line are both derived
     * from this method, so a marker that disagreed with the icon could not be written without deleting one of them.</p>
     */
    private hellfirepvp.modularmachinery.common.tiles.me.WorkModeSetting currentMode() {
        return hellfirepvp.modularmachinery.common.tiles.me.WorkModeSetting.fromOrdinal(menu.workModeOrdinal());
    }

    /** The same mode as an ordinal, for the sprite table (which is indexed by ordinal for upstream's reason). */
    private int currentModeOrdinal() {
        return currentMode().ordinal();
    }

    /**
     * Draws the independent storage's barrel, and NOTHING when it is empty.
     *
     * <p><b>REUSES THE PORT'S OWN FLUID DRAWING rather than inventing a second one</b> (rule B): the still sprite is
     * resolved through the block atlas by {@code IClientFluidTypeExtensions.of(fluid).getStillTexture()} and blitted
     * with that extension's own tint, which is what {@code ScreenFluidHatch} does (L101-L137) and what
     * {@code ScreenMEFluidInputBus} does (L102-L114). The alternative the port already paid for is binding the still
     * texture as a texture, which renders vanilla's purple-and-black missing-texture checkerboard -- a still texture is
     * a sprite, not a bindable texture.</p>
     *
     * <p>The cell is upstream's (232, 172, 16 x 16) and the fluid is inset one pixel on each side so the art's own
     * border stays visible around it.</p>
     */
    private void drawBarrel(GuiGraphics graphics) {
        TileMEPatternProvider tile = clientTile();
        if (tile == null) {
            return;
        }
        var fluid = tile.getSubFluidHandler().getFluid();
        if (fluid.isEmpty()) {
            return;
        }
        var extensions = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions
                .of(fluid.getFluid());
        var still = extensions.getStillTexture();
        if (still == null) {
            return;
        }
        var sprite = minecraft.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS)
                .apply(still);
        int tint = extensions.getTintColor();
        int size = MenuMEPatternProvider.SUB_FLUID_SIZE - 2;
        graphics.blit(leftPos + MenuMEPatternProvider.SUB_FLUID_X + 1, topPos + MenuMEPatternProvider.SUB_FLUID_Y + 1,
                0, size, size, sprite,
                ((tint >> 16) & 0xFF) / 255.0F,
                ((tint >> 8) & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F,
                1.0F);
    }

    /** Draws the cached item/fluid list in the source GUI's 3-column, 7-row panel. */
    private void drawCachedStacks(GuiGraphics graphics, int mouseX, int mouseY) {
        hoveredCache = -1;
        List<CacheEntry> entries = cacheEntries();
        int maxScroll = Math.max(0, entries.size() - 21);
        cacheScroll = Math.max(0, Math.min(cacheScroll, maxScroll));
        int left = leftPos + 180;
        int top = topPos + 27;
        for (int visible = 0; visible < 21; visible++) {
            int index = cacheScroll + visible;
            if (index >= entries.size()) break;
            int x = left + (visible % 3) * 18;
            int y = top + (visible / 3) * 18;
            CacheEntry entry = entries.get(index);
            if (entry instanceof ItemCacheEntry item) {
                ItemStack stack = item.stack();
                graphics.renderItem(stack, x, y);
                graphics.renderItemDecorations(font, stack, x, y);
            } else if (entry instanceof FluidCacheEntry fluid) {
                drawFluidIcon(graphics, fluid.stack(), x, y);
            }
            if (inside(mouseX, mouseY, x, y, 16, 16)) {
                hoveredCache = index;
            }
        }
    }

    private List<CacheEntry> cacheEntries() {
        TileMEPatternProvider tile = clientTile();
        if (tile == null) return List.of();
        List<CacheEntry> entries = new ArrayList<>();
        // The source GUI combines the shared cache with all per-pattern combination components.  The fixed
        // two-slot item inventory and its fluid barrel stay out of this list and are drawn in their own cells.
        for (hellfirepvp.modularmachinery.common.util.InfItemFluidHandler handler
                : tile.getGuiCacheHandlers()) {
            for (ItemStack stack : handler.getItemStackList()) {
                if (stack != null && !stack.isEmpty()) entries.add(new ItemCacheEntry(stack));
            }
            for (FluidStack stack : handler.getFluidStackList()) {
                if (stack != null && !stack.isEmpty()) entries.add(new FluidCacheEntry(stack));
            }
        }
        return entries;
    }

    private void drawFluidIcon(GuiGraphics graphics, FluidStack fluid, int x, int y) {
        var extensions = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid.getFluid());
        var still = extensions.getStillTexture();
        if (still == null) return;
        var sprite = minecraft.getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(still);
        int tint = extensions.getTintColor();
        graphics.blit(x, y, 0, 16, 16, sprite,
                ((tint >> 16) & 0xFF) / 255.0F,
                ((tint >> 8) & 0xFF) / 255.0F,
                (tint & 0xFF) / 255.0F, 1.0F);
        String amount = Integer.toString(fluid.getAmount());
        graphics.drawString(font, amount, x + 17 - font.width(amount), y + 8, 0xFFFFFFFF, true);
    }

    /**
     * @return the client-side tile this screen is over, or {@code null} when its chunk has not arrived
     *
     * <p>Resolved from the world rather than held, because the menu carries a position and the block entity is the
     * client's own copy -- the same resolution {@code ScreenMachineController} uses for its {@code holding} tile.</p>
     */
    @Nullable
    private TileMEPatternProvider clientTile() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getBlockEntity(menu.getProviderPos()) instanceof TileMEPatternProvider provider
                ? provider : null;
    }

    /**
     * A click on either of the two buttons, and everything else passed through.
     *
     * <p><b>{@code super.mouseClicked} is what routes a click to a SLOT</b>, so the only correct shape is "handle the
     * buttons, then call super" -- a version that returned early on every click would make all 72 slots dead. This is
     * upstream's own structure too: {@code AEBaseGuiContainerDynamic.mouseClicked} offers the click to its widget
     * controller and falls through to the vanilla path (G203-G214 is the same shape around the AE2 fluid widget).</p>
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inside(mouseX, mouseY, leftPos + BUTTON_ROW_X, topPos + BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            ModularmachineryNetwork.PktMEPatternProviderAction.sendReturnItems(menu.getProviderPos());
            return true;
        }
        if (inside(mouseX, mouseY, leftPos + workModeButtonX(), topPos + BUTTON_ROW_Y, BUTTON_SIZE, BUTTON_SIZE)) {
            // THE REQUEST CARRIES THE MODE, not "advance by one". Upstream's onClicked does the same in the other
            // direction: it reads the ButtonElements' current selection and sends the matching ENABLE_* action
            // (G137-G154). Naming the target lets the server re-derive and answer with a mode it actually has.
            //
            // THE NEXT MODE COMES FROM THE ENUM'S OWN WALK, not from a list kept here: the screen reads the mode the
            // PLAYER CAN SEE (the menu's synchronised ordinal) and asks the enum for the ordinal after it.
            ModularmachineryNetwork.PktMEPatternProviderAction.sendWorkMode(menu.getProviderPos(),
                    hellfirepvp.modularmachinery.common.tiles.me.WorkModeSetting.nextOrdinal(menu.workModeOrdinal()));
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inside(mouseX, mouseY, leftPos + 180, topPos + 27, 69, 126)) {
            int max = Math.max(0, cacheEntries().size() - 21);
            cacheScroll = Math.max(0, Math.min(max, cacheScroll - (delta > 0 ? 1 : -1) * 3));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    /**
     * The panel's four labels, from upstream's own anchors and keys (G57-G80).
     *
     * <p>Drawn in the LABEL pass, and {@code super} is deliberately NOT called: vanilla's default prints the
     * open-packet title at (8, 6) plus {@code container.inventory}, which would put a SECOND title eight pixels
     * above upstream's and a second inventory label where upstream has one. Upstream draws four lines and no
     * more.</p>
     */
    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, Component.translatable("gui.mepatternprovider.title"),
                TITLE_X, TITLE_Y, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.mepatternprovider.cached"),
                CACHED_X, CACHED_Y, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.mepatternprovider.inventory"),
                INVENTORY_X, INVENTORY_Y, 0x404040, false);
        graphics.drawString(font, Component.translatable("gui.mepatternprovider.single_inv"),
                SINGLE_INV_X, SINGLE_INV_Y, 0x404040, false);
    }

    /**
     * The three tooltips this screen owns: the "?" tip (upstream G163-G167), the return-items button (G94-G99) and the
     * work-mode button (G121-G136).
     *
     * <p><b>The mode's tooltip is upstream's own list, in upstream's own order, with upstream's own "currently
     * enabled" marker</b> -- and the marker is put on the mode the MENU reports rather than on a field this screen
     * keeps. Upstream does the identical thing against {@code workModeSetting.getCurrentSelection()} (G122), which
     * {@code updateGUIState} fills from {@code owner.getWorkMode()} (G245). Both lists come from
     * {@code MEPatternProviderTooltips}, which is the class the self-test can also reach.</p>
     *
     * <p><b>THIS METHOD IS ONLY REACHED BECAUSE {@code render} CALLS IT</b> -- see that override for the measurement
     * that made this the round's whole defect on 1.20.1.</p>
     *
     * <p><b>And the "?" explanation is the one widget here that has no server side at all</b>: its whole function is to
     * explain the label beside it, and its key resolves in both language files. §729 drew it for that reason and this
     * round leaves it exactly as it was.</p>
     *
     * <p>{@code protected} rather than {@code public}: the method being overridden is
     * {@code AbstractContainerScreen.renderTooltip}, which is {@code protected}, and this is the visibility the port's
     * other two screens use ({@code ScreenMEItemInputBus} L133, {@code ScreenMEFluidInputBus} L158).</p>
     */
    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (tipHovered) {
            graphics.renderTooltip(font, Component.translatable("gui.mepatternprovider.single_inv.desc"),
                    mouseX, mouseY);
            return;
        }
        if (returnHovered) {
            graphics.renderTooltip(font, hellfirepvp.modularmachinery.common.tiles.me.MEPatternProviderTooltips
                    .returnItemsLines(), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (workModeHovered) {
            graphics.renderTooltip(font, hellfirepvp.modularmachinery.common.tiles.me.MEPatternProviderTooltips
                    .workModeLines(currentMode()), java.util.Optional.empty(), mouseX, mouseY);
            return;
        }
        if (hoveredCache >= 0) {
            List<CacheEntry> entries = cacheEntries();
            if (hoveredCache < entries.size()) {
                CacheEntry entry = entries.get(hoveredCache);
                if (entry instanceof ItemCacheEntry item) {
                    graphics.renderTooltip(font, item.stack(), mouseX, mouseY);
                } else if (entry instanceof FluidCacheEntry fluid) {
                    graphics.renderTooltip(font, fluid.stack().getDisplayName(), mouseX, mouseY);
                }
                return;
            }
        }
        super.renderTooltip(graphics, mouseX, mouseY);
    }
}
