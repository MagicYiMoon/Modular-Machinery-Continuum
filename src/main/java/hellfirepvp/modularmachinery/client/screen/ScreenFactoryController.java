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
 * <p>NOT PORTED, named rather than half-drawn: the scrollbar ({@code GuiScrollbar}, upstream U366-U372) and
 * the search-time/work-mode footer (U234-U242, which reads fields this port does not have). The list shows
 * the six elements the texture has room for, and the status area says how many runs the factory has, so a
 * factory with more than six runs says so instead of hiding them.</p>
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

        /** guifactory.png is 280x213; every other GUI texture in this mod is 256x256. */
    private static final int FACTORY_TEXTURE_WIDTH = 280;
    private static final int FACTORY_TEXTURE_HEIGHT = 213;

    /** The jar's own constants (§210): GuiFactoryController.FONT_SCALE and TEXT_DRAW_OFFSET_X/Y. */
    private static final double FONT_SCALE = 0.72D;
    private static final int TEXT_DRAW_OFFSET_X = 113;
    private static final int TEXT_DRAW_OFFSET_Y = 12;

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
        for (int i = 0; i < Math.min(MenuFactoryController.MAX_PAGE_ELEMENTS, threads.size()); i++) {
            // Upstream's own row expression (U107): offsetY starts at RECIPE_QUEUE_OFFSET_Y and advances by
            // FACTORY_ELEMENT_HEIGHT + 1 per element. RECIPE_ROW_PITCH is that sum, declared in the menu so the
            // suite can measure the six rows it produces against the art's six wells.
            drawRun(graphics, threads.get(i), i,
                    MenuFactoryController.RECIPE_QUEUE_Y + i * MenuFactoryController.RECIPE_ROW_PITCH);
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

    /**
     * Draws the machine name, the status, the progress PERCENTAGE and the run count into the texture's dark
     * preview area — the rectangle that was left blank, which is what the client reported as an empty black box.
     *
     * <p>Upstream's own sequence, shortened where this port has no data for a line: the blueprint machine
     * ({@code drawBlueprintInfo} U54-U74), the structure found (U313-U334), the search status (U248-U265), the
     * thread count (U267-U275) and the parallelism (U277-U311). The three it has that this port cannot fill
     * are named in the class comment.</p>
     *
     * <h2>§693 — THE AGGREGATE BAR IS GONE, AND WHY</h2>
     *
     * <p>This method used to draw a 135-pixel bar of its own (a dark track plus a green fill) at the text
     * column's x, between the status line and the ticks line. The client's report of a "long green bar over the
     * text near the top right" is that bar. It is removed, for three measured reasons rather than for taste:</p>
     *
     * <ol>
     *   <li><b>Upstream does not draw one.</b> Its {@code drawFactoryStatus} (U189-U246, read in full) is TEXT
     *       ONLY: a redstone line, the blueprint, the structure, the search status, the thread count, the
     *       parallelism and the work-mode footer, and its single early return for a redstone stop. There is no
     *       {@code fill} call of any kind — upstream's ONLY progress bars on this screen are the six per-element
     *       fills drawn by {@code drawRecipeInfo}, which this screen draws too. Rule A0's default is therefore
     *       "no aggregate bar", and the bar was a deviation that was never recorded as one.</li>
     *   <li><b>The art has no bar for it.</b> guifactory.png is 280x213 and every one of its pixels is
     *       greyscale (measured: zero pixels with a channel spread over 6), and its preview area 112..246 x
     *       8..122 is a flat 19-21 grey field — no trough, no track, no lighter line. So there was nowhere on
     *       this panel the bar was <i>supposed</i> to be, which is why it ended up inside a text block: it was
     *       laid out from the text's own arithmetic ({@code TEXT_DRAW_OFFSET_X / FONT_SCALE}), not from a place
     *       the art reserved.</li>
     *   <li><b>And it could not be a row's bar.</b> 135 pixels is 1.57 rows: the fill could not be drawn inside
     *       any of the six wells at that width. The bar that belongs on this screen is the per-run fill, whose
     *       width the menu pins and the self-test asserts against the art's own well border.</li>
     * </ol>
     *
     * <p>Nothing the bar carried is lost: the per-run percentage is drawn inside each run's own row, and the
     * status line below still names the state. If the aggregate bar is wanted back, it needs an art change
     * first — a trough in the preview area — because a bar drawn on a plain field is what the client has just
     * reported as misplaced.</p>
     *
     * <p>PLAN 710, on the same line: the argument it passes to {@code gui.controller.status.crafting.progress}
     * is a PERCENTAGE now rather than the tick pair this method used to spell, for the reason on
     * {@link MenuFactoryController#progressText} and in the class comment.</p>
     */
    private void drawFactoryStatus(GuiGraphics graphics) {
        TileFactoryController factory = clientTile();

        graphics.pose().pushPose();
        graphics.pose().scale((float) FONT_SCALE, (float) FONT_SCALE, 1.0F);
        int x = (int) (TEXT_DRAW_OFFSET_X / FONT_SCALE);
        int y = TEXT_DRAW_OFFSET_Y;

        // THE MACHINE NAME, from the client tile's own synced field. Upstream asks the tile too
        // (getBlueprintMachine / getFoundMachine, U212-U216).
        String machineName = factory == null ? null : factory.getMachineName();
        graphics.drawString(font, Component.translatable("gui.controller.blueprint",
                        machineName == null || machineName.isEmpty()
                                ? Component.translatable("gui.controller.blueprint.none")
                                : Component.literal(machineName)),
                x, y, COLOR_WHITE, true);
        y += 12;
        graphics.drawString(font, Component.translatable("gui.controller.structure",
                        menu.isFormed() && machineName != null && !machineName.isEmpty()
                                ? Component.literal(machineName)
                                : Component.translatable("gui.controller.structure.none")),
                x, y, COLOR_WHITE, true);
        y += 15;
        // Source GuiFactoryController invokes the same GUI event hook after the structure line. Keep
        // the callback on the factory screen as well as the ordinary controller screen.
        if (factory != null && machineName != null && !machineName.isEmpty()) {
            var event = new hellfirepvp.modularmachinery.common.event.machine.ControllerGUIRenderEvent(factory);
            event.postEvent();
            for (String line : event.getExtraInfo()) {
                if (line != null && !line.isEmpty()) {
                    graphics.drawString(font, line, x, y, COLOR_WHITE, true);
                    y += 12;
                }
            }
        }

        // THE STATUS, the same six states in the same causal order as ScreenMachineController, over the
        // menu's data slots (DATA_FORMED/DATA_REDSTONE/DATA_CHUNK/DATA_DURATION/DATA_PROGRESS).
        int duration = menu.getDuration();
        int progress = menu.getProgress();
        Component status;
        int statusColor;
        if (menu.getRedstoneStopped()) {
            status = Component.translatable("gui.controller.status.redstone_stopped");
            statusColor = COLOR_BAD;
        } else if (!menu.getChunkLoaded()) {
            status = Component.translatable("gui.controller.status.chunk_unloaded");
            statusColor = COLOR_BAD;
        } else if (!menu.isFormed()) {
            status = Component.translatable("gui.controller.status.missing_structure");
            statusColor = COLOR_BAD;
        } else if (duration <= 0) {
            status = Component.translatable("gui.controller.status.no_recipe");
            statusColor = COLOR_BAD;
        } else if (menu.getProgressFraction() <= 0F) {
            // THE GUARDED READING, not the raw slot (§693). It is the same predicate here — duration > 0 is
            // already established by the branch above, so the fraction is progress/duration either way — and it
            // keeps the menu's own accessor on the path a client takes, which is the only shape in which that
            // accessor is not a second reader of the same two ints with no caller (the defect class §649(b)
            // named from the other direction).
            status = Component.translatable("gui.controller.status.idle");
            statusColor = COLOR_WHITE;
        } else {
            status = Component.translatable("gui.controller.status.crafting");
            statusColor = COLOR_OK;
        }
        graphics.drawString(font, status, x, y, statusColor, true);
        y += 12;

        // NO BAR HERE (§693, see the javadoc): the line that used to hold one is gone, and the flow below uses
        // the same 12-pixel advance every other line of this block uses, so the two remaining lines sit in the
        // preview area the art gives them rather than being pushed down by a widget that is no longer drawn.
        //
        // THE TICK PAIR IS GONE (PLAN 710). This line used to pass `progress + " / " + duration + " t"` into
        // `gui.controller.status.crafting.progress`, whose ONE argument upstream fills with a PERCENTAGE
        // (GuiFactoryController U180-U182: `(activeRecipe.getTick() * 100) / activeRecipe.getTotalTick()` then
        // `progress + "%"`). Read as "300 / 60 t" that is a valid string for the placeholder and a wrong VALUE:
        // the client's screenshot had this line reading "进度: 300 / 60 t" while every row above it read
        // "进度: 100%", which is the report ("原mod没有显示运行刻，是显示的运行百分比"). The argument now comes
        // from `MenuFactoryController.progressText`, the one place the percentage is formed, so there is no
        // expression left here that could concatenate a tick pair into the key.
        if (duration > 0) {
            graphics.drawString(font, Component.translatable("gui.controller.status.crafting.progress",
                    MenuFactoryController.progressText(progress, duration)), x, y, COLOR_WHITE, true);
        }
        y += 12;

        // THE RUN COUNT, both numbers off the same object so they cannot disagree: running runs and the list
        // that so much as exists. Upstream's equivalent line also prints the machine's ceiling
        // (gui.factory.threads, U271-U272), which lives on the SERVER tile's mirrored field and is 0 on a
        // client copy -- so it is not printed here rather than printed as a wrong number.
        if (factory != null) {
            graphics.drawString(font, Component.translatable("gui.factory.runs",
                            factory.getRunningThreadCount(), factory.getThreadCount()),
                    x, y, COLOR_WHITE, true);
        }

        // AND THE PARALLELISM READOUT (PLAN 711's neighbour, upstream GuiFactoryController U277-U311, which
        // prints the same pair for the run it is describing). Upstream's factory footer also names a ceiling
        // that lives on the SERVER tile's mirrored field and is 0 on a client copy -- that number is NOT
        // printed here, and the run count above still is, which is the rule already stated for this block.
        //
        // The CONTENT comes from the tile for the same reason the parallelism lines do on the machine
        // controller screen, and through the SAME method: TileFactoryController overrides getParallelism()
        // / getMaxParallelism() to report its FIRST RUNNING RUN, so these are upstream's numbers reached
        // through the tile. This screen owns only where they go -- one line below the block above, at the
        // same 12-pixel advance every line of this block uses, inside the pose this method already pushed.
        drawParallelismReadout(graphics, y);

        graphics.pose().popPose();
    }

    /**
     * Draws the tile's parallelism readout under {@code aboveY}, one line per component, and nothing at all
     * when the readout is empty -- the {@code ScreenMachineController} method of the same name, in the text
     * colour and line stride this panel's status block uses.
     *
     * <p>It reads {@code aboveY + READOUT_LINE_STRIDE} for its first line rather than {@code aboveY}, so the
     * block starts where the line above it ended -- and it draws through the pose this method already
     * pushed, so it computes no origin of its own (the PLAN 708 rule this screen has already been fixed
     * for).</p>
     */
    private void drawParallelismReadout(GuiGraphics graphics, int aboveY) {
        TileFactoryController factory = clientTile();
        if (factory == null) {
            return;
        }
        int lineY = aboveY + hellfirepvp.modularmachinery.common.tiles.TileController.READOUT_LINE_STRIDE;
        for (Component line : factory.parallelismReadoutLines()) {
            graphics.drawString(font, line, (int) (TEXT_DRAW_OFFSET_X / FONT_SCALE), lineY, COLOR_WHITE, true);
            lineY += hellfirepvp.modularmachinery.common.tiles.TileController.READOUT_LINE_STRIDE;
        }
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
        var factory = clientTile();
        int x = mouseX - leftPos;
        int y = mouseY - topPos - MenuFactoryController.RECIPE_QUEUE_Y;
        if (factory == null || x < MenuFactoryController.RECIPE_QUEUE_X
                || x >= MenuFactoryController.RECIPE_QUEUE_X + MenuFactoryController.RECIPE_ELEMENT_WIDTH || y < 0) return;
        int row = y / MenuFactoryController.RECIPE_ROW_PITCH;
        var threads = factory.getFactoryRecipeThreadList();
        if (row >= Math.min(MenuFactoryController.MAX_PAGE_ELEMENTS, threads.size())
                || y % MenuFactoryController.RECIPE_ROW_PITCH >= MenuFactoryController.RECIPE_ELEMENT_HEIGHT) return;
        var recipe = hellfirepvp.modularmachinery.common.machine.MachineReloadListener.getRecipeLoader()
                .getRecipe(threads.get(row).getRecipeName());
        if (recipe != null && !recipe.displayName().isBlank())
            graphics.renderComponentTooltip(font, java.util.List.of(Component.literal(recipe.displayName()),
                    Component.literal("ID: " + recipe.registryName())), mouseX, mouseY);
    }
}
