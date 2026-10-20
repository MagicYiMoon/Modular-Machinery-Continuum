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
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.tiles.TileFactoryController;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * The factory controller's menu: a machine controller with a blueprint slot further right.
 *
 * <h2>Why this is a separate menu and not a flag on the controller's</h2>
 *
 * <p>Both screens show a controller with one blueprint slot, so sharing a menu with a position parameter
 * looks tempting. The jar does not, and the reason is that <b>the panel size differs</b>:
 *
 * <pre>
 *   ContainerController          blueprint slot (151, 8)   panel 176 x 166
 *   ContainerFactoryController   blueprint slot (255, 8)   panel 280 x 213
 * </pre>
 *
 * <p>A slot at x=255 does not fit in a 176-wide panel. The x coordinate and the panel width are one piece
 * of data, so splitting them across two classes is what keeps them consistent — and the panel width is what
 * {@code GuiFactoryController} draws against.
 *
 * <h2>The blueprint slot is inherited, not re-declared</h2>
 *
 * <p>{@code TileFactoryController extends TileController}, so it already carries the one-stack blueprint
 * inventory and its {@code isItemValid} restriction (see §69.2 for why that rule lives on the inventory
 * rather than on the slot widget). This menu only has to <b>place</b> it.
 */
public class MenuFactoryController extends AbstractContainerMenu {

    /** Panel size, from the jar's {@code GuiFactoryController}. */
    public static final int PANEL_WIDTH = 280;
    public static final int PANEL_HEIGHT = 213;

    // The player-inventory rows, at UPSTREAM's coordinates rather than vanilla's (§192).
    //
    // Vanilla puts them at 84 and the hotbar at 142, for a 166-tall panel. A 213-tall panel cannot use
    // those: the jar's ContainerController has the rows at 131 and the hotbar at 189, and 189 + 18 + 6 = 213,
    // which is where the height comes from in the first place.
    //
    // MenuUpgradeBus already had these numbers and is the screen the client reported as correct, which is
    // what identified the defect: two 213-tall screens were using a 166-tall screen's layout.
    // 112, not 8 (§206). The jar's ContainerFactoryController has `bipush 112` in its own constructor --
    // read past the first time because §192 was generalising from the controller and the upgrade bus, both of
    // which do use 8. This screen does not: its panel is 280 wide and its player wells sit to the RIGHT of the
    // blueprint and preview area.
    //
    // MEASURED, not inferred: guifactory.png has nine 18-pixel well borders at y=131, at
    //   x = 111, 129, 147, 165, 183, 201, 219, 237, 255
    // which is 112 to within the border pixel. The bytecode and the texture agree, and PLAYER_INV_X was the
    // only one of the three numbers that disagreed with both.
    public static final int PLAYER_INV_X = 112;
    public static final int PLAYER_INV_Y = 131;
    public static final int PLAYER_HOTBAR_Y = 189;

    /** The blueprint slot, from {@code ContainerFactoryController}'s bytecode: iconst_0, sipush 255, bipush 8. */
    private static final int BLUEPRINT_SLOT_X = 255;
    private static final int BLUEPRINT_SLOT_Y = 8;

    // ------------------------------------------------------------------
    // The run list, and the bar each run draws (PLAN 693)
    // ------------------------------------------------------------------
    //
    // THESE ARE HERE RATHER THAN ON THE SCREEN, for the reason the panel size and the player rows above are:
    // a screen is a client-only class a dedicated server cannot load, so a number written into it is a number
    // the self-test cannot read. Declared here they are constant variables -- INLINED at every use, so
    // referencing them loads nothing (§677's note) -- and the suite asserts them against guifactory.png's own
    // pixels, which is the only authority on where this panel's art put its rows.
    //
    // THE VALUES ARE UPSTREAM'S, from GuiFactoryController's constructor-initialised constants (its U37-U42):
    // RECIPE_QUEUE_OFFSET_X/Y = 8/8, FACTORY_ELEMENT_WIDTH/HEIGHT = 86/32, MAX_PAGE_ELEMENTS = 6, and the
    // row advance is its own expression, `offsetY += FACTORY_ELEMENT_HEIGHT + 1`, i.e. a 33-pixel pitch.
    //
    // MEASURED AGAINST THE ART (not chosen): guifactory.png's left column is six wells whose top separators
    // are the rows at y = 7, 40, 73, 106, 139, 172 -- a 33-pixel pitch, six of them -- with a light 198-grey
    // interior from x=8 to x=92 bounded by 1-pixel 55-grey lines at x=7 and x=93. So a row drawn at
    // (8, 8 + 33i) with an 86x32 sprite starts on the first light row under its own separator and ends on the
    // well's last light row: the geometry below is the art's, and the check that reads this art says so.

    /** The run list's origin on the panel, and the size of one row -- upstream's four constants. */
    public static final int RECIPE_QUEUE_X = 8;
    public static final int RECIPE_QUEUE_Y = 8;
    public static final int RECIPE_ELEMENT_WIDTH = 86;
    public static final int RECIPE_ELEMENT_HEIGHT = 32;

    /** Upstream's row advance: {@code FACTORY_ELEMENT_HEIGHT + 1} (GuiFactoryController U107). */
    public static final int RECIPE_ROW_PITCH = RECIPE_ELEMENT_HEIGHT + 1;

    /** Upstream's {@code MAX_PAGE_ELEMENTS}: the six rows the art has room for (GuiFactoryController U36). */
    public static final int MAX_PAGE_ELEMENTS = 6;

    /**
     * The width, in panel pixels, of one run's progress fill — <b>upstream's arithmetic, and the only bar this
     * screen has</b>.
     *
     * <p>Upstream ({@code GuiFactoryController.drawRecipeInfo}, its U132-U135) draws the element sprite TWICE:
     * once whole as the row's frame, then again clipped to
     * {@code (int) (FACTORY_ELEMENT_WIDTH * tick / totalTick)}, tinted green while the run is crafting. Its GUI
     * art carries no bar trough anywhere — measured: guifactoryelements.png's only tile is a flat 219-grey plate
     * with a white top row and dark bottom row and one interior colour, and every pixel of guifactory.png is
     * greyscale — so that clipped, tinted second blit IS the bar, and its width is this method.</p>
     *
     * <p><b>Why it saturates at the element's width:</b> a fill wider than its own row would cross the well's
     * right border at x=93 and land in the scrollbar's column. The bar the client reported as misplaced in §693
     * was 135 pixels wide (the status text column's width) — 1.57 rows — which cannot be drawn inside a row at
     * all. The self-test asserts this bound against the art.</p>
     *
     * @param progress the run's progress; values outside {@code [0,1]} are clamped rather than trusted
     * @return the fill's width, {@code 0} when there is nothing to show and {@code RECIPE_ELEMENT_WIDTH} at 100%
     */
    public static int runFillWidth(double progress) {
        if (progress <= 0.0D) {
            return 0;
        }
        if (progress >= 1.0D) {
            return RECIPE_ELEMENT_WIDTH;
        }
        return (int) (RECIPE_ELEMENT_WIDTH * progress);
    }

    /**
     * The text a run's progress line prints — <b>upstream's PERCENTAGE, and the only place on the factory
     * screen a progress number becomes text</b>.
     *
     * <p>Upstream's own lines ({@code GuiFactoryController.drawRecipeStatus}, its U179-U183), and this is the
     * whole of it:</p>
     *
     * <pre>
     *   if (activeRecipe != null &amp;&amp; activeRecipe.getTotalTick() &gt; 0) {
     *       int progress = (activeRecipe.getTick() * 100) / activeRecipe.getTotalTick();
     *       fr.drawString(I18n.format("gui.controller.status.crafting.progress", progress + "%"), offsetX, offsetY, 0x222222);
     *   }
     * </pre>
     *
     * <p>An INTEGER division, then upstream's own {@code + "%"} concatenation. <b>Upstream's GUI has no
     * {@code tick / totalTick} form anywhere</b>: the key's single argument is a percentage, so a pair of raw
     * tick counts handed to the same key is a wrong VALUE even though it is a valid string for the
     * placeholder — which is exactly what the client saw (its status line read {@code 进度：300 / 60 t} while
     * every row beside it read {@code 进度：100%}). The percentage is formed in ONE place —
     * {@link #progressPercent} — and the tick pair has no path into the key at all.</p>
     *
     * <p><b>Why it saturates at 100%.</b> Upstream's percentage cannot exceed 100: {@code ActiveMachineRecipe
     * .tick} returns before its own {@code this.tick++} once {@code isCompleted()} (U83-U85, {@code tick >=
     * totalTick}). This port reaches the same bound from the same side — {@code FactoryRecipeThread
     * .advanceRecipe} clamps at its own duration (PLAN 708) — and the {@code min} here repeats that bound at the
     * display, so a counter that ever ran away again would draw {@code 100%} rather than {@code 160%}. A value
     * upstream cannot print is not a value a screen should be able to print either.</p>
     *
     * <p>Upstream's line is guarded by {@code activeRecipe != null &amp;&amp; getTotalTick() > 0}; the
     * equivalent guard stays at the call site, and this method answers {@code "0%"} for a zero duration rather
     * than dividing by it.</p>
     *
     * @param ticks    elapsed ticks, from the run or from the menu's own data slot
     * @param duration that run's duration in ticks
     * @return e.g. {@code "0%"}, {@code "50%"}, {@code "100%"}; {@code "0%"} when there is no duration
     */
    public static String progressText(int ticks, int duration) {
        return progressPercent(ticks, duration) + "%";
    }

    /**
     * The same percentage as an <b>integer in {@code [0, 100]}</b> — the form the OTHER consumer of this
     * arithmetic needs, and the reason {@link #progressText} is now a one-line delegate rather than a second
     * copy of the expression.
     *
     * <p><b>WHY A SECOND ENTRY POINT EXISTS AT ALL (PLAN 711, rule B).</b> A screen wants the TEXT
     * ({@code "50%"}), and TheOneProbe's overlay wants the NUMBER: TOP's {@code IProbeInfo.progress(int, int,
     * IProgressStyle)} is a value against a maximum, so upstream's own TOP provider hands it exactly
     * {@code ((int) progress, 100)} — {@code MMInfoProvider} U205 for a factory thread and U296 for a single
     * craft, over the {@code (tick * 100) / totalTick} it formed at U189/U274. Two callers, two types, ONE
     * arithmetic: this method is that arithmetic and {@link #progressText} adds upstream's own {@code "%"}
     * (U200/U291). A second expression would be the very thing this class was created to prevent, so there is
     * exactly one {@code (ticks * 100) / duration} in the port and it is the line below.</p>
     *
     * <p>The saturating {@code min} is upstream's bound, not a decoration: {@code ActiveMachineRecipe.tick}
     * returns before its own {@code this.tick++} once completed (U83-U85), so upstream can never compute a
     * percentage above 100 — see {@link #progressText} for the full argument and PLAN 708 for the clamp that
     * reaches the same bound on this port's side.</p>
     *
     * @param ticks    elapsed ticks, from a run or from the menu's own data slot
     * @param duration that run's duration in ticks
     * @return {@code 0} for a zero or negative duration rather than a division by it, otherwise upstream's
     *         integer percentage, saturated at {@code 100}
     */
    public static int progressPercent(int ticks, int duration) {
        if (duration <= 0) {
            return 0;
        }
        return Math.min(100, (ticks * 100) / duration);
    }

    /**
     * Upstream's two FILL tints: the colour a run's progress bar is blitted with, chosen by whether the run is
     * crafting — <b>and therefore the colour that turns RED when the factory cannot proceed</b>.
     *
     * <p>Upstream's lines ({@code GuiFactoryController.drawRecipeInfo}, its U125-U130), and this is the whole of
     * it:</p>
     *
     * <pre>
     *   // Thread Status Color
     *   if (status.isCrafting()) GlStateManager.color(0.6F, 1.0F, 0.75F, 1.0F);      // U127
     *   else                     GlStateManager.color(1.0F, 0.6F, 0.6F, 1.0F);        // U129 -- RED
     * </pre>
     *
     * <p>where {@code status} is {@code thread.getStatus()} (U112) and {@code CraftingStatus.isCrafting()} is
     * {@code status == Type.CRAFTING}. <b>The RED is not a separate warning signal</b>: both of upstream's
     * writers of a NON-crafting status are refusals — {@code ActiveMachineRecipe.tick} U88-U98 returns
     * {@code CraftingStatus.failure(...)} when {@code context.ioTick} refuses, and {@code RecipeThread
     * .onFinished} U73-U78 returns {@code CraftingStatus.failure(checkResult.getFirstErrorMessage(""))} when
     * {@code canFinishCrafting()} refuses, which is the output-room question
     * ({@code craftcheck.failure.item.output.space}). A player's "the bar turns red when there is not enough
     * space" IS that failure status.</p>
     *
     * <p>In this port the same predicate is {@code FactoryRecipeThread.isWorking()}
     * ({@code hasActiveRecipe() &amp;&amp; blockedReason == null}), whose {@code blockedReason} is written by
     * {@code FactoryTicker} at exactly those two points — {@code notWorking} for the per-tick refusal and the two
     * output-room refusals, with {@code MachineTicker.outputSpaceKey} / {@code outputAllocationFailure} for the
     * key.</p>
     *
     * <p><b>WHY THIS METHOD IS HERE RATHER THAN TWO TRIPLES OF CONSTANTS ON THE SCREEN (rule A0, placement).</b>
     * A {@code private static final float} is a compile-time constant: {@code javac} inlines it, so no class
     * file contains a read of it and a check that reads the screen's bytes cannot tell which of the two colours
     * a branch uses — only that both numbers appear somewhere in the method. The suite runs on a DEDICATED
     * SERVER, which cannot load {@code ScreenFactoryController} at all (§203), so a colour the check must see
     * has to be reachable from a server-loadable class. This is the port's own convention for exactly this
     * situation, established twice already: {@link #runFillWidth} pins the bar's arithmetic and
     * {@link #progressText} pins its text, both so that the suite asserts THE value the screen draws rather
     * than a transcription of it. The values are upstream's, unchanged, and the screen reads them from here.</p>
     *
     * <p>The returned array is a shared constant and <b>must only be read</b> — it is not copied per call,
     * because this is on a per-frame path.</p>
     *
     * @param blocked whether the run is held (its blocked reason is set) rather than crafting
     * @return {@code {r, g, b}} — {@code {1, 0.6, 0.6}} when blocked, {@code {0.6, 1, 0.75}} while crafting
     */
    public static float[] fillTint(boolean blocked) {
        return blocked ? TINT_BLOCKED : TINT_CRAFTING;
    }

    /** Upstream U127: {@code GlStateManager.color(0.6F, 1.0F, 0.75F, 1.0F)} while the run is crafting. */
    private static final float[] TINT_CRAFTING = {0.6F, 1.0F, 0.75F};
    /** Upstream U129: {@code GlStateManager.color(1.0F, 0.6F, 0.6F, 1.0F)} — the red the client remembers. */
    private static final float[] TINT_BLOCKED = {1.0F, 0.6F, 0.6F};

    private final BlockPos controllerPos;
    private final ContainerData data;
    private final IItemHandler blueprintInventory;

    /** Client-side constructor, invoked from the menu type's factory. */
    public MenuFactoryController(int windowId, Inventory playerInventory, BlockPos controllerPos) {
        this(windowId, playerInventory, controllerPos, new SimpleContainerData(MenuMachineController.DATA_SIZE),
                new ItemStackHandler(1));
    }

    public MenuFactoryController(int windowId, Inventory playerInventory, BlockPos controllerPos,
                                 ContainerData data, IItemHandler blueprintInventory) {
        super(RegistryMenus.FACTORY_CONTROLLER.get(), windowId);
        this.controllerPos = controllerPos;
        this.data = data;
        this.blueprintInventory = blueprintInventory;
        addDataSlots(data);

        // Same shape on both sides, so slot indices line up during synchronisation.
        addSlot(new SlotItemHandler(blueprintInventory, 0, BLUEPRINT_SLOT_X, BLUEPRINT_SLOT_Y));

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INV_X + column * 18, PLAYER_HOTBAR_Y));
        }
    }

    public static MenuFactoryController forController(int windowId, Inventory playerInventory,
                                                      TileFactoryController controller) {
        // Reuses the machine controller's data layout: a factory controller reports the same progress and
        // state, so a second container-data shape would be two things to keep in step for no gain.
        return new MenuFactoryController(windowId, playerInventory, controller.getBlockPos(),
                MenuMachineController.dataFor(controller), controller.getBlueprintInventory());
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    // ------------------------------------------------------------------
    // The status the screen draws (PLAN 664)
    // ------------------------------------------------------------------
    //
    // THE SAME SIX INDICES AND THE SAME NAMES MenuMachineController EXPOSES, over the same ContainerData
    // shape -- `forController` above is what wires it, and it is `MenuMachineController.dataFor(controller)`.
    // That is why these are delegations rather than a second data layout: the indices, the meanings and the
    // live-read rule are all the machine controller's, and the factory tile already overrides the underlying
    // reads to report its FIRST RUNNING RUN (TileFactoryController, upstream's activeRecipe ==
    // activeRecipes[0]).
    //
    // WITHOUT THEM THE SCREEN HAD NOTHING TO DRAW WITH: ScreenFactoryController shipped as a background blit
    // and nothing else, so the panel's status area stayed black and the run list stayed empty (PLAN 664).

    /** @return ticks elapsed on the first running run, as the menu's data slot reports them */
    public int getProgress() {
        return data.get(MenuMachineController.DATA_PROGRESS);
    }

    /** @return that run's duration in ticks, or 0 when nothing is running */
    public int getDuration() {
        return data.get(MenuMachineController.DATA_DURATION);
    }

    public boolean isFormed() {
        return data.get(MenuMachineController.DATA_FORMED) != 0;
    }

    /** @return how many components the last formation bound; 0 makes a formed machine look wired when it is not */
    public int getComponents() {
        return data.get(MenuMachineController.DATA_COMPONENTS);
    }

    /** @return true when a redstone signal is holding the factory stopped */
    public boolean getRedstoneStopped() {
        return data.get(MenuMachineController.DATA_REDSTONE) != 0;
    }

    /** @return true when the controller's own chunk is loaded (the weaker reading -- see TileController) */
    public boolean getChunkLoaded() {
        return data.get(MenuMachineController.DATA_CHUNK) != 0;
    }

    /**
     * @return the first running run's progress in {@code [0,1]}, or 0 when nothing is running
     *
     * <p>Guards the division: a malformed recipe with no duration would otherwise throw inside rendering,
     * which crashes the client rather than drawing a wrong bar.</p>
     */
    public float getProgressFraction() {
        int duration = getDuration();
        if (duration <= 0) {
            return 0F;
        }
        return Math.min(1F, (float) getProgress() / duration);
    }

    /** {@inheritDoc} Index 0 is the blueprint slot, so the player inventory starts at 1. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        if (index == 0) {
            if (!moveItemStackTo(stack, 1, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (index < 28) {
            // A blueprint goes to the slot; anything else swaps between inventory and hotbar.
            if (!moveItemStackTo(stack, 0, 1, false) && !moveItemStackTo(stack, 28, 37, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 1, 28, false)) {
            return ItemStack.EMPTY;
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(controllerPos) instanceof TileFactoryController
                && player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5,
                        controllerPos.getZ() + 0.5) <= 64.0;
    }
}
