/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-08 .. 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.client.screen;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.machine.factory.FactoryRecipeThread;
import hellfirepvp.modularmachinery.common.menu.MenuFactoryController;
import hellfirepvp.modularmachinery.common.menu.FactoryStatusViewport;
import java.util.ArrayList;
import java.util.List;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

/**
 * The factory controller screen.
 *
 * <p>Blits {@code guifactory.png}, the texture the jar's own {@code GuiFactoryController} names — read out
 * of its constant pool, not guessed. The same class also declares {@code guifactoryelements.png}, the second
 * sheet upstream draws its run list from, and THAT ONE IS DRAWN NOW (PLAN 664): the panel's left column has
 * six empty 86x32 boxes in the texture, and they were empty on screen because nothing drew into them.
 *
 * <p>The panel is <b>280x213</b>, distinctly wider than the machine controller's 176x166, because the
 * blueprint slot sits at x=255. Taking the controller's size here would put the slot outside the frame.
 *
 * <h2>PLAN 664 — what was wrong and what this draws now</h2>
 *
 * <p>Reported from the client: the factory screen is an EMPTY BLACK BOX — no machine name, no progress bar,
 * no run list. Both halves of that were true, and for two different reasons:</p>
 *
 * <ul>
 *   <li><b>THE SCREEN DREW NOTHING.</b> Before this round {@code renderLabels} called
 *       {@code super.renderLabels} and returned: no name, no status, no bar, no list. The black rectangle is
 *       the TEXTURE's own preview area — guifactory.png's flat dark field, re-measured in PLAN 693 as
 *       u=112..246, v=8..122 (the figure §664 quoted, u=111..252 v=8..131, counted the softer border pixels
 *       around it) — waiting for the text upstream draws over it.</li>
 *   <li><b>AND THE DATA IT NEEDS WAS ONLY SYNCED ON A TRANSITION.</b> The port pushed the tile's runs to
 *       clients on start/finish/blocked-change only, where upstream syncs every tick while the factory has
 *       threads ({@code markNoUpdateSync}, {@code TileFactoryController} U145-U148). A run list drawn from
 *       that would have shown every bar frozen at the value its run began with, so the ticker's cadence was
 *       fixed FIRST ({@code FactoryTicker.tick}, {@code TileEntityBase.markNoUpdateSync}).</li>
 * </ul>
 *
 * <h2>Where each number comes from, because two channels exist and neither may be guessed</h2>
 *
 * <pre>
 * what is drawn                        source                              why that one
 * blueprint / structure name           the CLIENT tile, getMachineName()   the name is not an int and the
 *                                                                          menu's data slots are int-only
 * the run list, its names, progress    the CLIENT tile, thread list        same reason, and the list is
 *   and blocked reasons                                                    the tile's own state
 * formed / redstone / chunk / the      the MENU's data slots               the port's existing status
 *   first run's ticks                                                      convention (the machine
 *                                                                          controller screen reads the
 *                                                                          same six indices)
 * </pre>
 *
 * <p>Upstream reads its TILE for all of it ({@code drawFactoryStatus} U189-U246 and {@code drawRecipeQueue}
 * U94-U109 both take the {@code TileFactoryController} its GUI was constructed with), and the client here has
 * a tile too — that is what the two name/list rows use. The menu's slots are not redundant: they are the
 * server-authoritative reading, updated by vanilla every tick, and they are what the OTHER controller screen
 * already uses for the same six states.</p>
 *
 * <h2>PLAN 693 — the progress bar was in the wrong place, and there were two of them</h2>
 *
 * <p>Reported from the client: the progress bar is drawn in the wrong place. Measured against the shipped art
 * rather than by eye, this screen drew TWO green things and neither was the bar the art implies:</p>
 *
 * <ul>
 *   <li><b>THE TINTS WERE ON THE WRONG BLITS.</b> {@code drawRun} tinted the row's FRAME with the crafting
 *       green and blitted the FILL with the colour reset to white, where upstream tints the frame plainly and
 *       the fill green (U117-U122 vs U126-U130). Every crafting run's whole 86x32 row therefore turned green —
 *       a green band over the run list — while the progress fill was invisible, because the element sheet is a
 *       flat plate and a white clip of a white frame changes nothing. The bar is the fill, inside its own row,
 *       at {@code RECIPE_ELEMENT_WIDTH * progress} pixels wide.</li>
 *   <li><b>AND A SECOND BAR WAS DRAWN WHERE THE ART HAS NOTHING.</b> {@code drawFactoryStatus} drew a 135-pixel
 *       aggregate bar inside the status text block. Upstream's factory has no such bar (its
 *       {@code drawFactoryStatus} is text only) and guifactory.png has no trough for one — it is greyscale
 *       from edge to edge and its preview area is a flat dark field. It is removed; the reasoning and the
 *       measurements are on that method.</li>
 * </ul>
 *
 * <p>The run list's geometry did NOT change: it was already upstream's, and the art agrees with it to the
 * pixel — six wells in the left column whose top separators are at y = 7, 40, 73, 106, 139, 172 (a 33-pixel
 * pitch) with a light interior from x=8 to x=92 between 1-pixel borders at x=7 and x=93, which is where
 * {@code RECIPE_QUEUE_X/Y} + {@code RECIPE_ROW_PITCH} put the six rows. The self-test now measures that
 * relation and the bar's width against those pixels.</p>
 *
 * <h2>PLAN 708 — the bars were drawn a WHOLE PANEL-ORIGIN away from their own wells</h2>
 *
 * <p>Reported again from the client after PLAN 693: the progress bars are still in the wrong place. 693
 * measured the ART correctly and fixed the tint assignment and the width, and its check passed — because that
 * check asserted the MENU's six row coordinates against the art and only asked the screen to <i>name</i> the
 * pinned width arithmetic. What it could not see was the one number the SCREEN added on top: {@code drawRun}
 * blitted both the frame and the fill at {@code leftPos + RECIPE_QUEUE_X}, {@code topPos + offsetY}, while
 * {@code renderLabels} already runs inside a pose translated by exactly that origin. The rows therefore landed
 * at {@code 2 * leftPos + 8}, i.e. in the status text area, and the row <i>text</i> — drawn inside a 0.72-scaled
 * pose, where the origin contributes only 9 pixels — stayed in the left column where it belongs. That split is
 * the whole of the report: text on the wells, green bars over the status lines.</p>
 *
 * <p>The arithmetic is upstream's now, and upstream's arithmetic is panel-relative (rule A0): a 1.12.2
 * {@code GuiContainer} translates by (guiLeft, guiTop) before {@code drawGuiContainerForegroundLayer}, so
 * {@code GuiFactoryController}'s {@code (RECIPE_QUEUE_OFFSET_X, offsetY)} blits are panel-relative on both
 * versions. The self-test's new check reads this class's own compiled bytes and asserts that
 * {@code drawRun} reads no panel origin field at all, that {@code renderBg} is the only method that does, and
 * that the row rectangle is the art's well rectangle.</p>
 *
 * <p>The six visible rows scroll through all core and dynamic threads. Factory-wide thread and batch
 * ceilings use the existing block-entity update tag. Performance timings use this open menu's own
 * data slots; the work mode reports the port's actual synchronous engine.</p>
 *
 * <h2>PLAN 710 — the status line printed RUNNING TICKS where upstream prints a PERCENTAGE</h2>
 *
 * <p>The client's report, verbatim: <i>"我好像记得原mod没有显示运行刻，是显示的运行百分比，如果空间不足进度条会变红提示"</i>
 * — the original mod showed a running PERCENTAGE rather than running ticks, and the progress bar turns RED
 * when there is not enough space. Both halves were checked against upstream's own bodies before anything was
 * changed, and both hold:</p>
 *
 * <ul>
 *   <li><b>THE PERCENTAGE (fixed here).</b> {@code GuiFactoryController.drawRecipeStatus} U179-U183 is the only
 *       place upstream forms this text, and it is
 *       {@code int progress = (activeRecipe.getTick() * 100) / activeRecipe.getTotalTick();} followed by
 *       {@code I18n.format("gui.controller.status.crafting.progress", progress + "%")}. This screen passed
 *       {@code progress + " / " + duration + " t"} into that same key — the <i>string</i> fit the placeholder,
 *       the <i>value</i> did not, and it is the line the client read
 *       ({@code 进度: 300 / 60 t} beside rows reading {@code 进度: 100%}). Both lines now take their argument
 *       from {@link MenuFactoryController#progressText}, which is upstream's integer arithmetic and upstream's
 *       {@code "%"} concatenation, and therefore the only expression on this screen that can reach the key.</li>
 *   <li><b>THE RED FILL (already correct, verified).</b> Upstream's predicate is
 *       {@code if (status.isCrafting())} at U126 — green — {@code else} U129 — {@code (1.0F, 0.6F, 0.6F)} —
 *       red, where {@code status} is {@code thread.getStatus()} (U112). Its two writers of a non-crafting
 *       status are both failures: {@code ActiveMachineRecipe.tick} U88-U98 returns
 *       {@code CraftingStatus.failure(...)} when {@code context.ioTick} refuses, and {@code RecipeThread
 *       .onFinished} U73-U78 returns {@code CraftingStatus.failure(checkResult.getFirstErrorMessage(""))} when
 *       {@code canFinishCrafting()} refuses — the output-room question, whose key family is
 *       {@code craftcheck.failure.item.output.space}. This screen's equivalent is {@code thread.isWorking()},
 *       which is {@code hasActiveRecipe() && blockedReason == null}, and the port's {@code FactoryTicker}
 *       writes that reason at exactly those two points ({@code notWorking} U526 for the per-tick refusal, and
 *       U479/U493 for the two output-room refusals, through {@code MachineTicker.outputSpaceKey} and
 *       {@code outputAllocationFailure}). No new signal was created; the reason that already existed is what
 *       selects upstream's red constant.</li>
 * </ul>
 */
public class ScreenFactoryController extends AbstractContainerScreen<MenuFactoryController> {
    private net.minecraft.client.gui.components.Button hypernetButton;
    @Override public void containerTick(){super.containerTick();HyperNetMonitorButton.update(hypernetButton,menu.getControllerPos());}
    private final FactoryStatusViewport statusViewport = new FactoryStatusViewport();
    private final hellfirepvp.modularmachinery.common.menu.FactoryThreadViewport threadViewport =
            new hellfirepvp.modularmachinery.common.menu.FactoryThreadViewport();

        /** guifactory.png is 280x213; every other GUI texture in this mod is 256x256. */
    private static final int FACTORY_TEXTURE_WIDTH = 280;
    private static final int FACTORY_TEXTURE_HEIGHT = 213;

    /** The jar's own constants (§210): GuiFactoryController.FONT_SCALE and TEXT_DRAW_OFFSET_X/Y. */
    private static final double FONT_SCALE = FactoryStatusViewport.SCALE;
    private static final int TEXT_DRAW_OFFSET_X = 113;

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/guifactory.png");
    /** The element sheet, upstream's own name (GuiFactoryController U32). */
    private static final ResourceLocation TEXTURE_ELEMENTS =
            new ResourceLocation(Modularmachinery.MODID, "textures/gui/guifactoryelements.png");

    // THE RUN LIST'S GEOMETRY IS THE MENU'S, NOT THIS CLASS'S (PLAN 693), for the reason the panel size and the
    // player rows are already there: a screen is a client-only class, and the dedicated server the self-test runs
    // on cannot load it, so a coordinate written here cannot be asserted against the art it draws on. The menu
    // declares RECIPE_QUEUE_X/Y, RECIPE_ELEMENT_WIDTH/HEIGHT, RECIPE_ROW_PITCH and MAX_PAGE_ELEMENTS; this screen
    // reads them and the suite measures them against guifactory.png's own pixels.

    // The same palette the machine controller screen uses, so the two screens agree on what a status colour
    // means without a second convention. NO BAR COLOURS HERE ANY MORE: the aggregate bar they served is gone
    // (see drawFactoryStatus).
    private static final int COLOR_TEXT = 0xFF404040;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final int COLOR_OK = 0xFF1E7A1E;
    private static final int COLOR_BAD = 0xFF9A2020;

    // Plain dynamic-thread frame (upstream GuiFactoryController U120-U122).
    // The upstream blue core-thread frame still needs a separate client rendering pass.
    private static final float ELEMENT_FRAME_R = 1.0F, ELEMENT_FRAME_G = 1.0F, ELEMENT_FRAME_B = 1.0F;
    private static final float CORE_FRAME_R = 0.7F, CORE_FRAME_G = 0.9F, CORE_FRAME_B = 1.0F;

    // THE FILL'S TWO TINTS ARE THE MENU'S, NOT THIS CLASS'S (PLAN 710), and the reason is the one the panel
    // size and the run-list geometry are already there for: a `private static final float` is INLINED by javac,
    // so nothing in this file's bytes records which of the two colours a branch uses -- and the suite runs on a
    // dedicated server, which cannot load this class at all (§203). `MenuFactoryController.fillTint(blocked)`
    // holds upstream's own numbers (U127 green / U129 red) where the check can call them.

    public ScreenFactoryController(MenuFactoryController menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = MenuFactoryController.PANEL_WIDTH;
        this.imageHeight = MenuFactoryController.PANEL_HEIGHT;
        // Vanilla would draw the title at (8,6) full size; the jar draws it at (12,12) at 0.72 (§208).
        // Parking the vanilla label off-screen and drawing our own is how both are achieved -- the inventory
        // label still comes from super.renderLabels, so only the title is moved.
        // NOTE: nothing may be assigned to titleLabelX/Y here -- AbstractContainerScreen.init() sets them
    }

    /**
     * Hides vanilla's title AFTER super.init has set it (§217).
     *
     * <p>{@code AbstractContainerScreen.init()} assigns {@code titleLabelX/Y} itself, so a value set in the
     * constructor is discarded -- and {@code renderLabels} calls {@code super.renderLabels}, which then draws
     * vanilla's own title at (8,6) while this screen draws its scaled copy at the jar's offset. The result was
     * two overlapping titles, reported from the client as the text being "misplaced".
     */
    @Override
    protected void init() {
        super.init();
        hypernetButton=addRenderableWidget(HyperNetMonitorButton.create(menu,leftPos+imageWidth-25,topPos+30));
        HyperNetMonitorButton.update(hypernetButton,menu.getControllerPos());
        this.titleLabelY = -1000;   // vanilla's; this screen draws its own below

        // §218: and the INVENTORY label is derived from where the slots actually are, not from vanilla's
        // arithmetic. Vanilla computes `imageHeight - 94`, which for a 213-tall panel is 119 -- but it rendered
        // at about 70, which is `166 - 94`, and the client reported the label sitting far above its grid.
        //
        // Rather than work out which constructor wrote imageHeight when, the value is taken from the thing it
        // labels: MenuFactoryController.PLAYER_INV_Y is where the first row is, and every other screen in this
        // port puts its label twelve pixels above that. Deriving it means the two cannot disagree.
        // And the same on X (§219): vanilla's inventoryLabelX is 8, which on a 280-wide panel whose player wells
        // start at 112 puts the word 物品栏 on the far left, beside the blueprint column, labelling a grid it
        // is nowhere near. The label belongs over the row it names, so both numbers come from PLAYER_INV_*.
        this.inventoryLabelX = MenuFactoryController.PLAYER_INV_X;
        this.inventoryLabelY = MenuFactoryController.PLAYER_INV_Y - 12;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // THE TEXTURE IS 280x213, NOT 256x256 (§200).
        //
        // The last two arguments are the texture's own dimensions; blit uses them to turn the u/v rect into UV
        // coordinates. Every other GUI texture in this mod is 256x256, so 256,256 was copied here by pattern --
        // but guifactory.png is 280 wide, and 280/256 = 1.09 pushes the right edge past u=1, where it WRAPS and
        // samples the texture's left edge again. The panel is 280 wide, so the last 24 pixels of every row were
        // drawn from the wrong part of the image, which is what the client reported.
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight,
                FACTORY_TEXTURE_WIDTH, FACTORY_TEXTURE_HEIGHT);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // super draws the inventory label at inventoryLabelY, derived from the slot rows above.
        super.renderLabels(graphics, mouseX, mouseY);
        // NO TITLE IS DRAWN, and that is upstream's shape rather than an omission (PLAN 245): the jar's
        // GuiFactoryController.func_146979_b calls drawRecipeQueue() and drawFactoryStatus() and NOTHING ELSE.
        // What used to be here instead was a title at TEXT_DRAW_OFFSET in dark grey INSIDE the panel's dark
        // preview area, which the client reported as unreadable -- the space belongs to the two calls below,
        // which is what PLAN 245 said was missing and what this round finally draws.
        drawRunList(graphics);
        drawFactoryStatus(graphics);
    }

    // ------------------------------------------------------------------
    // The run list: upstream GuiFactoryController.drawRecipeQueue / drawRecipeInfo (U94-U170)
    // ------------------------------------------------------------------

    /**
     * Draws the factory's runs into the texture's six left-column elements.
     *
     * <p>Upstream's order (U98-U108): core threads first, then the simple ones, {@code MAX_PAGE_ELEMENTS} of
     * them starting at the scrollbar's offset. {@link TileFactoryController} returns that same order.</p>
     */
    private void drawRunList(GuiGraphics graphics) {
        TileFactoryController factory = clientTile();
        if (factory == null) {
            return;
        }
        java.util.List<FactoryRecipeThread> threads = factory.getFactoryRecipeThreadList();
        threadViewport.updateCount(threads.size());
        for (int i = 0; i < threadViewport.visibleCount(); i++) {
            // Upstream's own row expression (U107): offsetY starts at RECIPE_QUEUE_OFFSET_Y and advances by
            // FACTORY_ELEMENT_HEIGHT + 1 per element. RECIPE_ROW_PITCH is that sum, declared in the menu so the
            // suite can measure the six rows it produces against the art's six wells.
            int index = threadViewport.index(i);
            drawRun(graphics, threads.get(index), index,
                    MenuFactoryController.RECIPE_QUEUE_Y + i * MenuFactoryController.RECIPE_ROW_PITCH);
        }
        if (threadViewport.maxOffset() > 0) {
            var viewport = threadViewport;
            int x = hellfirepvp.modularmachinery.common.menu.FactoryThreadViewport.SCROLL_X;
            int y = hellfirepvp.modularmachinery.common.menu.FactoryThreadViewport.SCROLL_Y;
            int height = hellfirepvp.modularmachinery.common.menu.FactoryThreadViewport.SCROLL_HEIGHT;
            graphics.fill(x, y, x + 6, y + height, 0xFF555555);
            graphics.fill(x + 1, viewport.thumbY(), x + 5, viewport.thumbY() + viewport.thumbHeight(), 0xFFBBBBBB);
        }
    }

    /**
     * One run element: the sheet at its full size as the row's frame, then the same sheet again clipped to the
     * run's progress as the row's bar.
     *
     * <p>Upstream draws exactly this pair (U123 for the frame and U132-U135 for the fill), with the fill's width
     * being the element's width times {@code activeRecipe.getTick() / getTotalTick()} — the per-run progress bar
     * — and the tint difference is upstream's too, <b>one tint per blit</b>: the FRAME is plain and the FILL is
     * green while the run is crafting and red otherwise (U117-U122 vs U126-U130).</p>
     *
     * <h2>§693 — what was wrong here, and why the whole row was green</h2>
     *
     * <p>The two tints were assigned to the WRONG BLITS: the frame carried the crafting tint and the fill was
     * blitted with the colour reset to white. Two visible consequences, and the client reported the first:</p>
     *
     * <ul>
     *   <li><b>Every crafting run's WHOLE 86x32 row turned green</b> — a green band under the row's own text,
     *       over the run list, regardless of how far the run had got. That is the "green progress bar in the
     *       wrong place": the bar drawn over the row instead of inside it at its progress width.</li>
     *   <li><b>And the fill could not be seen at all.</b> guifactoryelements.png's only tile is a FLAT plate —
     *       measured: one interior colour (219,219,219) over 2520 pixels, a white top row and a 55-grey bottom
     *       row — so a white-tinted clip of it over a plain frame is a no-op. The run's progress was invisible on
     *       top of that, which is why the row's percentage text was the only progress the player could read.</li>
     * </ul>
     *
     * <p>The geometry was never the problem: the rows were already drawn at the coordinates the art's six wells
     * sit at (menu constants, measured — see {@link MenuFactoryController#RECIPE_QUEUE_Y}). The <i>bar's</i>
     * place was the tint assignment and the width, and both now come from upstream's arithmetic, pinned in the
     * menu so the self-test can assert the width against the art.</p>
     */
    private void drawRun(GuiGraphics graphics, FactoryRecipeThread thread, int id, int offsetY) {
        double progress = runProgress(thread);
        boolean crafting = thread.isWorking();

        // THE FRAME, drawn untinted: the row's backdrop, not its bar (upstream U117-U123).
        //
        // PANEL-RELATIVE -- NO leftPos / topPos HERE (PLAN 708). `renderLabels` IS INVOKED INSIDE A POSE THAT
        // `AbstractContainerScreen.render` HAS ALREADY TRANSLATED BY (leftPos, topPos), so adding the panel
        // origin a second time draws every row one panel-origin away from where the art put its wells. The
        // authorising body is vanilla's own: `render` loads leftPos/topPos into locals, calls
        // `pose().pushPose()` and `pose().translate(leftPos, topPos, 0)`, and only then calls `renderLabels`
        // (verified on the mapped 1.20.1 jar's bytecode: renderBg at 18, the translate at 54-71, renderLabels
        // at 205, popPose at 513). THE FIRST TWO BLITS USED TO READ `leftPos + RECIPE_QUEUE_X` and
        // `topPos + offsetY`, which put the 86x32 rows at (2*leftPos + 8, 2*topPos + 8 + 33i): on a 1280x720
        // window at GUI scale 3 -- origin (73, 13) -- that is panel x 154..240, inside the status PREVIEW
        // FIELD and over the two status text lines, which is the "large green bar over the text, and a second
        // one below it with no text of its own" the client reported. The ROW TEXT was never affected because
        // it is drawn inside a 0.72-scaled pose, where the same origin lands 9.4 pixels from the panel edge
        // (the art's well) instead of 73 -- which is why the text was right in the left column while the bars
        // were not.
        //
        // UPSTREAM'S ARITHMETIC IS PANEL-RELATIVE TOO, so this is a restoration and not a deviation
        // (rule A0): 1.12.2's `GuiContainer.drawScreen` translates by (guiLeft, guiTop) before calling
        // `drawGuiContainerForegroundLayer` exactly as 1.20.1's does before `renderLabels`, and upstream's
        // `drawRecipeInfo` blits at `(RECIPE_QUEUE_OFFSET_X, offsetY)` with no origin added
        // (GuiFactoryController U123, U132-U135). Every other screen in this port already draws this way:
        // ScreenMachineController writes at 12/24/46 panel-relative, and this screen's own
        // `drawFactoryStatus` does too.
        if (thread.isPresetThread()) {
            graphics.setColor(CORE_FRAME_R, CORE_FRAME_G, CORE_FRAME_B, 1.0F);
        } else {
            graphics.setColor(ELEMENT_FRAME_R, ELEMENT_FRAME_G, ELEMENT_FRAME_B, 1.0F);
        }
        graphics.blit(TEXTURE_ELEMENTS, MenuFactoryController.RECIPE_QUEUE_X, offsetY,
                0, 0, MenuFactoryController.RECIPE_ELEMENT_WIDTH, MenuFactoryController.RECIPE_ELEMENT_HEIGHT,
                256, 256);

        // THE FILL — THE BAR — is the same sprite cut to this run's progress and tinted (upstream U126-U135).
        // Its width is the menu's arithmetic, so a bar can never be wider than the row it belongs to; a
        // zero-width blit would be a bug in some drivers, so nothing is drawn when there is no progress.
        int filled = MenuFactoryController.runFillWidth(progress);
        if (filled > 0) {
            // THE RED WARNING IS UPSTREAM'S OWN PREDICATE (PLAN 710): a run that is not working -- held with no
            // room for its output, or starved of energy -- takes U129's (1.0, 0.6, 0.6), and a run that is
            // crafting takes U127's (0.6, 1.0, 0.75). `crafting` is `thread.isWorking()`, which is
            // `hasActiveRecipe() && blockedReason == null`, and `blockedReason` is the field the ENGINE writes
            // through FactoryTicker.setBlockedReason with MachineTicker.outputSpaceKey /
            // outputAllocationFailure. NO SECOND SIGNAL: the reason that already reached this screen through the
            // update tag is what selects the colour.
            float[] tint = MenuFactoryController.fillTint(!crafting);
            graphics.setColor(tint[0], tint[1], tint[2], 1.0F);
            graphics.blit(TEXTURE_ELEMENTS, MenuFactoryController.RECIPE_QUEUE_X, offsetY,
                    0, 0, filled, MenuFactoryController.RECIPE_ELEMENT_HEIGHT, 256, 256);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // The text, at the jar's 0.72 scale and offsets (U142-U187).
        graphics.pose().pushPose();
        graphics.pose().scale((float) FONT_SCALE, (float) FONT_SCALE, 1.0F);
        int textX = (int) (MenuFactoryController.RECIPE_QUEUE_X / FONT_SCALE) + 2;
        int textY = (int) (offsetY / FONT_SCALE) + 2;
        String name = thread.getRecipeName();
        String subject;
        if (thread.isPresetThread()) {
            String threadName = thread.getThreadName();
            subject = I18n.exists(threadName) ? I18n.get(threadName) : threadName;
        } else {
            subject = name == null ? Component.translatable("gui.factory.thread", id).getString() : name;
            var recipe = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getRecipeLoader().getRecipe(name);
            if (recipe != null) subject = recipe.displayNameOrId();
        }
        subject = fitRunSubject(subject);
        graphics.drawString(font, subject, textX, textY, COLOR_TEXT, false);
        textY += 12;
        // The run's own state, in the port's words: its blocked reason when it has one (the port's substitute
        // for upstream's CraftingStatus -- PLAN 521), otherwise the same two the machine screen uses.
        //
        // THE REASON IS RENDERED AS A TRANSLATION KEY, NOT AS A LITERAL (PLAN 708). The hold for a full
        // output now carries `craftcheck.failure.<type>.output.space` -- the SAME key the single-craft path
        // puts on the machine's controller (PLAN 683, MachineTicker.outputSpaceKey) -- and a literal would
        // print that key to the player. `translatable` RESOLVES A KEY AND PASSES A NON-KEY THROUGH
        // UNCHANGED, which is vanilla's own behaviour for an unresolved key, so the diagnostics the other
        // writer of this field produces (RecipeCraftingContext.describeBlockers, a sentence) still render
        // exactly as they did.
        Component state = thread.getBlockedReason() != null
                ? Component.translatable(thread.getBlockedReason())
                : (thread.hasActiveRecipe()
                        ? Component.translatable("gui.controller.status.crafting")
                        : Component.translatable("gui.controller.status.idle"));
        for (var line : font.split(state,
                (int) ((MenuFactoryController.RECIPE_ELEMENT_WIDTH - 6) / FONT_SCALE))) {
            graphics.drawString(font, line, textX, textY, COLOR_TEXT, false);
            textY += 10;
        }
        // The percentage, from upstream's own arithmetic and its own '%' (U179-U183), through the ONE method
        // that forms it (PLAN 710). Both this line and the status area's line take their argument from there,
        // so the two cannot disagree about the unit — and neither can spell a tick pair.
        if (thread.hasActiveRecipe() && thread.getRecipeDuration() > 0) {
            graphics.drawString(font, Component.translatable("gui.controller.status.crafting.progress",
                    MenuFactoryController.progressText(thread.getRecipeTicks(), thread.getRecipeDuration())),
                    textX, textY, COLOR_TEXT, false);
        }
        graphics.pose().popPose();
    }

    /** Keep names within the row; the hover tooltip retains the complete label and ID. */
    private String fitRunSubject(String subject) {
        int labelWidth = (int) ((MenuFactoryController.RECIPE_ELEMENT_WIDTH - 6) / FONT_SCALE);
        if (font.width(subject) <= labelWidth) return subject;
        return font.plainSubstrByWidth(subject, labelWidth - font.width("...")) + "...";
    }

    /** @return this run's progress in {@code [0,1]}, or 0 when it has no recipe */
    private static double runProgress(FactoryRecipeThread thread) {
        if (!thread.hasActiveRecipe() || thread.getRecipeDuration() <= 0) {
            return 0.0D;
        }
        return Math.min(1.0D, (double) thread.getRecipeTicks() / thread.getRecipeDuration());
    }

    // ------------------------------------------------------------------
    // The status area: upstream GuiFactoryController.drawFactoryStatus (U189-U246)
    // ------------------------------------------------------------------

    /** A wrapped status row; performance diagnostics use a separate fixed footer. */
    private record StatusLine(Component text, int color) { }

    private void drawFactoryStatus(GuiGraphics graphics) {
        TileFactoryController factory = clientTile();
        String machineName = factory == null ? null : factory.getMachineName();
        boolean named = machineName != null && !machineName.isEmpty();
        var lines = new ArrayList<StatusLine>();
        lines.add(new StatusLine(Component.translatable("gui.controller.blueprint", named
                ? Component.literal(machineName) : Component.translatable("gui.controller.blueprint.none")), COLOR_WHITE));
        lines.add(new StatusLine(Component.translatable("gui.controller.structure", menu.isFormed() && named
                ? Component.literal(machineName) : Component.translatable("gui.controller.structure.none")), COLOR_WHITE));
        if (factory != null && named) {
            var event = new hellfirepvp.modularmachinery.common.event.machine.ControllerGUIRenderEvent(factory);
            event.postEvent();
            for (String line : event.getExtraInfo()) {
                if (line != null && !line.isEmpty()) lines.add(new StatusLine(Component.literal(line), COLOR_WHITE));
            }
        }
        int duration = menu.getDuration();
        int progress = menu.getProgress();
        String statusKey;
        int color = COLOR_BAD;
        if (menu.getRedstoneStopped()) statusKey = "gui.controller.status.redstone_stopped";
        else if (!menu.getChunkLoaded()) statusKey = "gui.controller.status.chunk_unloaded";
        else if (!menu.isFormed()) statusKey = "gui.controller.status.missing_structure";
        else if (duration <= 0) statusKey = "gui.controller.status.no_recipe";
        else if (menu.getProgressFraction() <= 0F) { statusKey = "gui.controller.status.idle"; color = COLOR_WHITE; }
        else { statusKey = "gui.controller.status.crafting"; color = COLOR_OK; }
        lines.add(new StatusLine(Component.translatable(statusKey), color));
        if (duration > 0) lines.add(new StatusLine(Component.translatable("gui.controller.status.crafting.progress",
                MenuFactoryController.progressText(progress, duration)), COLOR_WHITE));
        if (factory != null && factory.getMaxThreads() > 0) lines.add(new StatusLine(Component.translatable(
                "gui.factory.threads", factory.getDynamicThreadCount(), factory.getMaxThreads()), COLOR_WHITE));
        drawParallelismReadout(lines);

        // Wrap before measuring: long translated names and script-added lines remain accessible by scrolling.
        var rows = new ArrayList<java.util.Map.Entry<net.minecraft.util.FormattedCharSequence, Integer>>();
        for (StatusLine line : lines) for (var row : font.split(line.text(), FactoryStatusViewport.TEXT_WIDTH))
            rows.add(java.util.Map.entry(row, line.color()));
        statusViewport.updateHeight(rows.size() * 12);
        graphics.enableScissor(leftPos + FactoryStatusViewport.X, topPos + FactoryStatusViewport.Y,
                leftPos + FactoryStatusViewport.X + FactoryStatusViewport.WIDTH,
                topPos + FactoryStatusViewport.Y + FactoryStatusViewport.HEIGHT);
        graphics.pose().pushPose();
        graphics.pose().translate(TEXT_DRAW_OFFSET_X, FactoryStatusViewport.Y, 0);
        graphics.pose().scale((float) FONT_SCALE, (float) FONT_SCALE, 1F);
        int y = -statusViewport.offset();
        for (var row : rows) {
            if (y + font.lineHeight > 0 && y < FactoryStatusViewport.LOGICAL_HEIGHT)
                graphics.drawString(font, row.getKey(), 0, y, row.getValue(), true);
            y += 12;
        }
        graphics.pose().popPose();
        graphics.disableScissor();
        if (statusViewport.maxOffset() > 0) {
            graphics.fill(FactoryStatusViewport.SCROLL_X, FactoryStatusViewport.Y,
                    FactoryStatusViewport.SCROLL_X + FactoryStatusViewport.SCROLL_WIDTH,
                    FactoryStatusViewport.Y + FactoryStatusViewport.HEIGHT, 0xFF303030);
            graphics.fill(FactoryStatusViewport.SCROLL_X, statusViewport.thumbY(),
                    FactoryStatusViewport.SCROLL_X + FactoryStatusViewport.SCROLL_WIDTH,
                    statusViewport.thumbY() + statusViewport.thumbHeight(), 0xFFAAAAAA);
        }
        drawPerformanceFooter(graphics);
    }

    private void drawParallelismReadout(List<StatusLine> lines) {
        TileFactoryController factory = clientTile();
        if (factory != null) for (Component line : factory.factoryParallelismReadoutLines())
            lines.add(new StatusLine(line, COLOR_WHITE));
    }

    private List<Component> performanceLines() {
        return List.of(Component.translatable("gui.factory.performance.tick", menu.performanceTickText()),
                Component.translatable("gui.factory.performance.search", menu.performanceSearchText()),
                Component.translatable("gui.factory.performance.mode", Component.translatable(menu.workModeTranslationKey())));
    }

    private void drawPerformanceFooter(GuiGraphics graphics) {
        graphics.pose().pushPose();
        graphics.pose().translate(TEXT_DRAW_OFFSET_X, FactoryStatusViewport.FOOTER_Y, 0);
        graphics.pose().scale((float) FONT_SCALE, (float) FONT_SCALE, 1F);
        int row = 0;
        for (Component line : performanceLines()) {
            String text = line.getString();
            if (font.width(text) > FactoryStatusViewport.TEXT_WIDTH)
                text = font.plainSubstrByWidth(text, FactoryStatusViewport.TEXT_WIDTH - font.width("…")) + "…";
            graphics.drawString(font, text, 0, (int) (row++ * FactoryStatusViewport.FOOTER_STRIDE / FONT_SCALE), COLOR_WHITE, true);
        }
        graphics.pose().popPose();
    }

    // ------------------------------------------------------------------
    // The client-side tile
    // ------------------------------------------------------------------

    /**
     * @return the client's copy of this controller, or {@code null} when the chunk is not loaded yet
     *
     * <p>The screen was handed a {@link MenuFactoryController}, which carries the position rather than the
     * block entity, so the tile is resolved here — the 1.20.1 shape of what upstream's GUI gets for free by
     * being constructed with its {@code TileFactoryController} (U47-U52). The data is the update tag the
     * server sends (chunk send, and every tick while the factory has runs -- see
     * {@code TileEntityBase.markNoUpdateSync}).</p>
     */
    private TileFactoryController clientTile() {
        if (minecraft == null || minecraft.level == null) {
            return null;
        }
        return minecraft.level.getBlockEntity(menu.getControllerPos()) instanceof TileFactoryController factory
                ? factory
                : null;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + FactoryStatusViewport.X && mouseX < leftPos + FactoryStatusViewport.X + FactoryStatusViewport.WIDTH
                && mouseY >= topPos + FactoryStatusViewport.FOOTER_Y
                && mouseY < topPos + FactoryStatusViewport.FOOTER_Y + FactoryStatusViewport.FOOTER_ROWS * FactoryStatusViewport.FOOTER_STRIDE)
            graphics.renderComponentTooltip(font, performanceLines(), mouseX, mouseY);
        var factory = clientTile();
        if (factory == null) return;
        var threads = factory.getFactoryRecipeThreadList();
        threadViewport.updateCount(threads.size());
        int index = threadViewport.indexAt(mouseX - leftPos, mouseY - topPos);
        if (index < 0) return;
        var recipe = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getRecipeLoader()
                .getRecipe(threads.get(index).getRecipeName());
        if (recipe != null && !recipe.displayName().isBlank())
            graphics.renderComponentTooltip(font, java.util.List.of(Component.literal(recipe.displayName()),
                    Component.literal("ID: " + recipe.registryName())), mouseX, mouseY);
    }

    private void refreshThreadViewport() {
        var factory = clientTile();
        threadViewport.updateCount(factory == null ? 0 : factory.getThreadCount());
    }

    @Override public boolean mouseScrolled(double x, double y, double delta) {
        refreshThreadViewport();
        return statusViewport.scroll(x - leftPos, y - topPos, delta) || threadViewport.scroll(x - leftPos, y - topPos, delta) || super.mouseScrolled(x, y, delta);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        refreshThreadViewport();
        return statusViewport.click(x - leftPos, y - topPos, button) || threadViewport.click(x - leftPos, y - topPos, button) || super.mouseClicked(x, y, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        refreshThreadViewport();
        return statusViewport.drag(y - topPos, button) || threadViewport.drag(y - topPos, button) || super.mouseDragged(x, y, button, dx, dy);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return statusViewport.release(button) || threadViewport.release(button) || super.mouseReleased(x, y, button);
    }
}
