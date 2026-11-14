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
package hellfirepvp.modularmachinery.common.menu;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.util.inv.AppEngInternalInventory;
import hellfirepvp.modularmachinery.common.registry.RegistryMEMenus;
import hellfirepvp.modularmachinery.common.tiles.me.TileMEPatternProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The pattern provider own screen, AE2 container half (PLAN sections 460, 473-478, and 712).
 *
 * <h2>What upstream's container is, read at the body (rule A)</h2>
 *
 * <pre>
 *   ContainerMEPatternProvider extends AEBaseContainer                      (upstream C29)
 *     C44      bindPlayerInventory(getInventoryPlayer(), 0, 114)            -&gt; the player's rows at y = 114
 *                                                                              and the hotbar at y = 172
 *     C46-C51  for row 0..3, for col 0..8:
 *                  addSlotToContainer(new SlotRestrictedInput(ENCODED_PATTERN, patterns,
 *                      (row * 9) + col, 8 + (col * 18), 28 + (row * 18), getInventoryPlayer()))
 *                                                                          -&gt; 36 pattern slots, 9 COLUMNS x 4 ROWS
 *     C53-C56  for i in 0..1: addSlotToContainer(new SlotNormal(subItemHandler, i, 181 + (i * 18), 172))
 *                                                                          -&gt; the two "single inv" slots
 * </pre>
 *
 * <p><b>So upstream's grid is 36 = 9 x 4 at (8 + 18c, 28 + 18r), and the number is not taken from a screenshot: it is
 * the two loop bounds at C47-C48.</b> The art agrees -- 36 wells, whose top borders measure 18/19 dark pixels at rows
 * 28/46/64/82 for all nine columns at {@code 8 + 18c} -- and the suite's own check re-measures that from
 * {@code mepatternprovider.png} rather than trusting this comment.</p>
 *
 * <h2>RULE A0 EVIDENCE FOR DEPARTING FROM UPSTREAM, in the three parts A0 requires</h2>
 *
 * <p><b>(1) UPSTREAM METHOD:</b> as above — {@code AEBaseContainer} with {@code SlotRestrictedInput} for the pattern
 * slots and {@code SlotNormal} for the sub-handler, placed by EXPLICIT coordinates (C46-C56).</p>
 *
 * <p><b>(2) WHY IT CANNOT BE FOLLOWED:</b> {@code AEBaseContainer}, {@code SlotNormal},
 * {@code SlotRestrictedInput} and {@code AppEngInternalInventory} (the {@code appeng.container} package),
 * {@code IFluidSyncContainer}, {@code SlotPlayerHotBar} and {@code PacketInventoryAction} are all ABSENT from AE2
 * 15.4.11 — verified by listing the jar.</p>
 *
 * <p><b>(3) THE ALTERNATIVE, and the two MEASURED facts that decided it.</b> Upstream's coordinates are kept
 * verbatim; WHAT CHANGED IS WHERE A POSITION CAN COME FROM, and that took two measurements rather than a
 * preference:</p>
 *
 * <pre>
 *   AE2 15.x POSITIONS NOTHING IN THE MENU. AppEngSlot's constructor is Slot(EMPTY_INVENTORY, index, 0, 0);
 *     AEBaseMenu.createPlayerInventorySlots builds Slot(inventory, i, 0, 0); and AEBaseMenu's bytecode contains NO
 *     `putfield net/minecraft/world/inventory/Slot.x` at all. The assignment lives in AEBaseScreen.repositionSlots,
 *     which reads the SCREEN's ScreenStyle JSON -- and this port's screen is a vanilla AbstractContainerScreen with
 *     no style, so before this change every slot of this menu sat at (0, 0).
 *   SLOT.x AND SLOT.y ARE FINAL on 1.20.1. The SRG-to-official mapping (build/createMcpToSrg/output.tsrg) names
 *     `x f_40220_` and `y f_40221_`, and both are `public final int` in the joined jar -- so a slot can only be
 *     given a position by its CONSTRUCTOR, and moving it afterwards needs reflection (upstream's own
 *     GuiMEPatternProvider U196-U197 reaches into AE2's private final x/y that way, for an AE2-EL-only branch).
 * </pre>
 *
 * <p><b>So the slots are built at their coordinates, which is this port's own convention for every menu that places
 * anything</b> ({@code MenuMEItemInputBus.addPlayerInventory}, {@code MenuMEFluidInputBus}):</p>
 *
 * <pre>
 *   AEBaseContainer            -&gt; AEBaseMenu
 *   SlotRestrictedInput        -&gt; a SlotItemHandler over AE2's PUBLIC bridge InternalInventory.toItemHandler(),
 *                                 with mayPlace = PatternDetailsHelper.isEncodedPattern -- AE2's own public
 *                                 predicate, which is the rule the ENCODED_PATTERN slot type enforces
 *   SlotNormal                 -&gt; NOT PORTED, see below
 *   explicit coordinates       -&gt; kept verbatim, passed to the slot CONSTRUCTORS
 *   bindPlayerInventory        -&gt; vanilla Slots over the player's Inventory, added with AE2's own semantics so that
 *                                 AEBaseMenu's semantic bookkeeping (and isPlayerSideSlot, which compares the slot's
 *                                 container with the player's inventory) sees the same thing it saw before
 * </pre>
 *
 * <p><b>AND THE 36 SLOTS THEMSELVES:</b> AE2 15.4.11's {@code PatternProviderLogic(node, host)} passes <b>9</b> to
 * its three-argument form, which hands that int straight to {@code new AppEngInternalInventory(host, size)}. So this
 * menu used to add <b>nine</b> pattern slots where upstream's container adds thirty-six, and
 * {@code TileMEPatternProvider.PATTERNS} (36) was a constant nothing read. It is now the size the logic is built
 * with, which is what makes the four rows of wells below reachable.</p>
 *
 * <h2>What is deliberately NOT here, each with the line that makes it unreachable</h2>
 *
 * <p><b>The two "single inv" slots (upstream C53-C56) are NOT added.</b> Upstream backs them with
 * {@code subItemHandler} (U93, 2 slots) which is INSIDE the machine-visible container — its own {@code handler} is
 * {@code new InfItemFluidHandler(subItemHandler, subFluidHandler)} (U107), so the machine draws from the single inv
 * as well as from the cache. This port's {@link hellfirepvp.modularmachinery.common.util.InfItemFluidHandler}
 * records the opposite property of itself (its own javadoc: the sub-handler is "not consulted by the methods
 * below"), so two slots here would hold items <b>nothing reads</b> — the defect class this project calls a value
 * with no producer/consumer. Recorded rather than faked; the label and the "?" button that DESCRIBE them are drawn
 * by the screen, with their keys resolving in both language files.</p>
 */
public class MenuMEPatternProvider extends AEBaseMenu {

    /**
     * The panel, from upstream's own constructor: {@code GuiMEPatternProvider} sets {@code xSize = 256} and
     * {@code ySize = 196} (upstream U49-U50). MEASURED against the shipped art rather than trusted: the opaque
     * region of {@code mepatternprovider.png} is 256 wide and 196 tall (its own columns are opaque on rows 0..195
     * and transparent from 196 down, outside the sprite sheet), which is what {@code ScreenMEPatternProvider} blits.
     */
    public static final int PANEL_WIDTH = 256;
    public static final int PANEL_HEIGHT = 196;

    /** Vanilla's slot pitch, and the art's: 18. */
    public static final int SLOT_PITCH = 18;

    /**
     * The pattern grid: upstream C47-C48's loop bounds, i.e. <b>9 columns x 4 rows = 36</b>.
     *
     * <p>{@code PATTERN_X}/{@code PATTERN_Y} are upstream C49's {@code 8 + (col * 18)} / {@code 28 + (row * 18)},
     * and the art's 36 wells sit at exactly those cells.</p>
     *
     * <p>PUBLIC, and not for decoration: a constant variable is INLINED at its reference site, so the suite reads
     * these numbers without loading this class — which matters, because this class names AE2 types and a server
     * without AE2 must still be able to check the screen's geometry (§677's {@code MenuUpgradeBus} lesson).</p>
     */
    public static final int PATTERN_COLUMNS = 9;
    public static final int PATTERN_ROWS = 4;
    public static final int PATTERN_SLOTS = PATTERN_COLUMNS * PATTERN_ROWS;
    public static final int PATTERN_X = 8;
    public static final int PATTERN_Y = 28;

    /**
     * The player's rows, from upstream C44's {@code bindPlayerInventory(getInventoryPlayer(), 0, 114)}: three rows at
     * 114 and the hotbar at 172 (AE2's own binding adds 58 to the offset for the hotbar).
     *
     * <p><b>Both are written as the subtraction for this panel's height, and the art is what decided the exact
     * numbers:</b> {@code mepatternprovider.png}'s player wells have their top borders at rows 113/131/149 and the
     * hotbar's at 171, i.e. the cells are at 114/132/150 and 172 — and {@code 196 - 82} / {@code 196 - 24} are the
     * same two numbers, which is also the relation {@code MenuMEFluidInputBus} uses for its 231-tall panel.</p>
     */
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = PANEL_HEIGHT - 82;
    public static final int PLAYER_HOTBAR_Y = PANEL_HEIGHT - 24;

    /** Columns in one player row; vanilla's own, and the art's. */
    public static final int PLAYER_COLUMNS = 9;

    /**
     * The independent storage's two wells, from upstream C53-C56 verbatim:
     * {@code new SlotNormal(subItemHandler, i, 181 + (i * 18), 172)}.
     *
     * <p><b>These are the slots §729 refused, and the refusal was correct at the time.</b> Its own sentence is the
     * record: "upstream backs them with {@code subItemHandler}, which is INSIDE the machine-visible container ... this
     * port's {@code InfItemFluidHandler} says of itself that the sub-handler is 'not consulted by the methods below',
     * so two slots there would hold items NOTHING reads". The measurement has not changed; what changed is that the
     * container can now present the sub-handler AS PART OF ONE COMPOSITE ({@code machineVisibleItems}), and the tile
     * exposes that composite through {@code ITEM_HANDLER} -- so the same two slots that would have written to a dead
     * inventory now write to the storage the machine counts and draws from.</p>
     *
     * <p><b>AND IT IS THE STORAGE THE USER NAMED.</b> {@code gui.mepatternprovider.single_inv} is "独立存储" /
     * "Single Inv", the label §729 already drew at (180, 158) above exactly these two cells, and the user's own
     * sentence for it -- "下方独立存储应为所有配方共同识别的槽位" -- is what {@code machineVisibleItems}' one shared
     * object delivers.</p>
     */
    public static final int SUB_ITEM_X = 181;
    public static final int SUB_ITEM_Y = 172;

    /**
     * The fluid barrel beside them, at upstream's own coordinate:
     * {@code new GuiFullCapFluidTank(owner.getSubFluidHandler(), 0, 0, 232, 172, 16, 16)}
     * ({@code GuiMEPatternProvider} G187-G189).
     *
     * <p><b>NOT A MENU SLOT, AND NOT DRAWN BY THIS FILE.</b> Upstream's widget is an AE2 {@code GuiFluidTank}, and
     * {@code appeng/fluids/} has ZERO entries in AE2 15.4.11 (re-measured this round: 5156 jar entries, no match) --
     * so there is no AE2 widget to instantiate and no {@code IGuiFluidStackHandler} to hand it. The 16 x 16 cell is
     * still the right cell and the value here is the anchor {@code ScreenMEPatternProvider} draws its own barrel
     * frame in, which also keeps the coordinate in ONE place instead of two.</p>
     */
    public static final int SUB_FLUID_X = 232;
    public static final int SUB_FLUID_Y = 172;
    public static final int SUB_FLUID_SIZE = 16;

    /** How many wells the independent storage has, taken from the tile's own constant (upstream U91). */
    public static final int SUB_ITEM_SLOTS = TileMEPatternProvider.SUB_ITEM_HANDLER_SLOTS;

    /** The work mode, as ONE int in the menu's data slots. See {@link #DATA_SIZE} and {@link #workModeOrdinal()}. */
    public static final int DATA_WORK_MODE = 0;

    /**
     * The menu's data-slot count: the work mode and nothing else.
     *
     * <p><b>Why a data slot at all, when the tile's mode already reaches the client on its update tag:</b> because
     * the two paths answer different questions and the screen needs the one that is LIVE. The update tag carries the
     * mode when the block entity is (re)sent -- which happens when the WINDOW OPENS, and not again until the mode
     * moves. A data slot is re-read every tick by {@code AbstractContainerMenu#broadcastChanges}, so a mode changed by
     * another source (a second player, a future automation hook) reaches an already-open screen. §735's defect A was
     * exactly this: the tile held the number, the screen showed what the open packet had carried.</p>
     */
    public static final int DATA_SIZE = 1;

    /** @return a live view of the provider's mode, as ONE ordinal */
    public static ContainerData dataFor(TileMEPatternProvider provider) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return index == DATA_WORK_MODE ? provider.getWorkMode().ordinal() : 0;
            }

            @Override
            public void set(int index, int value) {
                // Server-authoritative, exactly like MenuFluidHatch's and MenuMEFluidInputBus's: the client writes
                // nothing here, and the mode's write path is the packet.
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    private final BlockPos providerPos;
    private final ContainerData data;

    /**
     * Builds the menu over the block at {@code pos}, on EITHER side.
     *
     * <p><b>One constructor for both sides, unlike {@code MenuMEItemInputBus} and {@code MenuMEFluidInputBus},
     * because the question they split on has a single answer here.</b> Both of those take a live handler from the
     * tile/server and a throwaway from a client constructor; this menu can ask the world directly, because the pattern
     * provider is a block entity whose <i>client</i> copy is a real {@code TileMEPatternProvider} -- built by the
     * block entity type's own factory from the update tag -- and whose own constructor creates the inventories. So
     * {@code getBlockEntity(pos)} gives the real handler on both sides, and a client that reaches this menu before its
     * chunk has arrived gets a throwaway of the SAME LENGTH rather than a shorter slot list (the comment at
     * {@code patterns} below is the reason that matters).</p>
     */
    public MenuMEPatternProvider(int windowId, Inventory playerInventory, BlockPos pos) {
        super(RegistryMEMenus.PATTERN_PROVIDER.get(), windowId, playerInventory,
                playerInventory.player.level().getBlockEntity(pos));

        this.providerPos = pos;
        TileMEPatternProvider tile = playerInventory.player.level().getBlockEntity(pos)
                instanceof TileMEPatternProvider provider ? provider : null;
        // ⚠ AN IF/ELSE AND NOT A TERNARY, AND THE REASON IS A HARD-TO-READ CRASH RATHER THAN STYLE.
        //
        // This was `this.data = tile == null ? new SimpleContainerData(DATA_SIZE) : dataFor(tile);` -- the obvious
        // shape, and it compiles. What it also does is put BOTH branches' types into ONE expression, and the JVM's
        // bytecode verifier must then compute their common supertype. `dataFor` returns an ANONYMOUS ContainerData,
        // so the two join at `ContainerData`... except that the frame ALSO has to merge the value this constructor
        // holds, and this class's own supertype chain reaches AE2. ModLauncher's TransformerClassWriter therefore
        // asked its class loader for appeng/menu/AEBaseMenu while TRANSFORMING the self-test's call path, and on a
        // server without AE2 that is fatal:
        //
        //     java.lang.RuntimeException: Cannot find class appeng/menu/AEBaseMenu
        //       at cpw.mods.modlauncher.TransformerClassWriter.computeHierarchyFromFile(TransformerClassWriter.java:161)
        //       at ... computeHierarchy -> SuperCollectingVisitor.visit -> getCommonSuperClass
        //       ... at ServerSelfTestTrigger.onServerStarted(ServerSelfTestTrigger.java:73)
        //
        // Two separate statements keep the two values in different frames, and ModLauncher never has to answer the
        // question. THE RULE THIS ADDS TO THIS PORT'S LIST OF CLASS-LOAD BOUNDARIES: it is not enough for a type to be
        // named only in a guarded METHOD -- a ternary, a `?:` in an argument list, or any other place where two types
        // are merged by the verifier can pull an AE2 type into the frame of a class the standalone server loads.
        // (PLAN 737 records the full trace; the guard around this round's checks was moved to the CALL SITE for the
        // same family of reason.)
        if (tile == null) {
            this.data = new SimpleContainerData(DATA_SIZE);
        } else {
            this.data = dataFor(tile);
        }

        // The pattern slots. Upstream C46-C51, one per pattern, at upstream's own coordinates, semantically marked
        // so AE2 and its integrations recognise them as encoded-pattern slots.
        //
        // THE INVENTORY IS TAKEN FROM THE TILE WHEN THERE IS ONE, and a THROWAWAY of the same length is built when
        // there is not -- which is the client, where the block entity is frequently not loaded yet at the moment the
        // open packet arrives. This is MenuMEItemInputBus's shape and its reason: the grid's LENGTH is a constant of
        // this class on both sides, so the two sides' slot lists cannot differ in length and shift every
        // player-inventory index that follows.
        InternalInventory patterns;
        IItemHandlerModifiable subItems;
        if (tile != null) {
            patterns = tile.getLogic().getPatternInv();
            subItems = tile.getSubItemHandler();
        } else {
            patterns = new AppEngInternalInventory(TileMEPatternProvider.PATTERNS);
            // THE THROWAWAY IS THE SAME SHAPE AS THE REAL ONE, deliberately: a client without its tile gets two wells
            // over an empty 2-slot handler, which is what an empty independent storage looks like anyway. A shorter or
            // absent placeholder would put the player's inventory at a different index on one side than the other.
            subItems = new ItemStackHandler(SUB_ITEM_SLOTS);
        }
        // AE2's PUBLIC bridge from its own inventory type to Forge's, which is what SlotItemHandler takes --
        // AppEngSlot(InternalInventory, index) cannot be used here because its constructor fixes x = y = 0 and
        // Slot.x/y are final. The bridge writes through AppEngInternalInventory.setItemDirect, which notifies the
        // inventory's HOST (measured: setItemDirect -> notifyContentsChanged -> onContentsChanged), so a pattern the
        // player places still reaches PatternProviderLogic.onChangeInventory and the grid.
        for (int i = 0; i < patterns.size(); i++) {
            addSlot(new PatternSlot(patterns, i,
                            PATTERN_X + (i % PATTERN_COLUMNS) * SLOT_PITCH,
                            PATTERN_Y + (i / PATTERN_COLUMNS) * SLOT_PITCH),
                    SlotSemantics.ENCODED_PATTERN);
        }

        // THE INDEPENDENT STORAGE. Upstream C53-C56's two slots at upstream's own coordinates, over the SAME handler
        // object the tile exposes through ITEM_HANDLER -- so what a player drops here is what a recipe counts and
        // draws, and what the first button returns to the grid. That identity is the whole feature: it is the
        // difference between a slot and a well that holds items nothing reads.
        for (int i = 0; i < SUB_ITEM_SLOTS && i < subItems.getSlots(); i++) {
            addSlot(new SlotItemHandler(subItems, i, SUB_ITEM_X + i * SLOT_PITCH, SUB_ITEM_Y),
                    SlotSemantics.STORAGE);
        }

        // The player's rows: vanilla Slots built at their coordinates (the only way, x/y being final), added with
        // AE2's own semantics so AEBaseMenu's bookkeeping is unchanged. Upstream's offset (C44) is the number here.
        for (int i = 0; i < 27; i++) {
            addSlot(new Slot(playerInventory, i + 9,
                            PLAYER_INV_X + (i % PLAYER_COLUMNS) * SLOT_PITCH,
                            PLAYER_INV_Y + (i / PLAYER_COLUMNS) * SLOT_PITCH),
                    SlotSemantics.PLAYER_INVENTORY);
        }
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(playerInventory, i, PLAYER_INV_X + i * SLOT_PITCH, PLAYER_HOTBAR_Y),
                    SlotSemantics.PLAYER_HOTBAR);
        }

        // The slots must be added BEFORE the data slots, because a click resolves against the slot list.
        addDataSlots(data);
    }

    /** @return how many encoded-pattern slots this menu carries */
    public int patternSlotCount() {
        return getSlots(SlotSemantics.ENCODED_PATTERN).size();
    }

    /** @return how many independent-storage slots this menu carries */
    public int storageSlotCount() {
        return getSlots(SlotSemantics.STORAGE).size();
    }

    /** @return the block this menu is over, so a packet can re-derive its target without trusting a payload */
    public BlockPos getProviderPos() {
        return providerPos;
    }

    /**
     * @return the provider's work mode ordinal, as the SYNCHRONISED data reports it
     *
     * <p><b>The screen must call this and never {@code tile.getWorkMode()}:</b> on the client the tile's own field is
     * whatever the last update tag carried, while this reads the data slot the server refreshes every tick. Two readers
     * for one quantity is the drift this project keeps paying for, so the screen is given exactly one door.</p>
     */
    public int workModeOrdinal() {
        return data.get(DATA_WORK_MODE);
    }

    /**
     * @return whether {@code menu} is a pattern provider menu looking at {@code pos}
     *
     * <p>Present for the reason {@code MenuMEFluidInputBus.isOpenFor}'s javadoc states: the question "is this the menu
     * the player actually has open" belongs in one place, and naming it here removes the packet's need to name the menu
     * TYPE. It is also this packet's AUTHORIZATION rule -- without it any client could set any provider's mode or empty
     * it into the grid from anywhere in the world.</p>
     */
    public static boolean isOpenFor(@Nullable AbstractContainerMenu menu, BlockPos pos) {
        return menu instanceof MenuMEPatternProvider providerMenu
                && providerMenu.getProviderPos().equals(pos);
    }

    /**
     * One encoded-pattern slot: upstream's {@code SlotRestrictedInput(ENCODED_PATTERN, ...)} (C49), over AE2's own
     * inventory rather than over a vanilly container.
     *
     * <p><b>Why a Forge {@code SlotItemHandler} and not AE2's {@code AppEngSlot}:</b> the latter's only constructor
     * is {@code AppEngSlot(InternalInventory, int)}, which fixes the position at (0, 0), and {@code Slot.x}/{@code
     * Slot.y} are final — so an {@code AppEngSlot} cannot be placed on this screen's grid at all. The two AE2-side
     * behaviours this class would otherwise lose are re-stated here: the ENCODED_PATTERN rule and the one-pattern
     * stack limit (upstream's own inventory is {@code new AppEngInternalInventory(this, PATTERNS, 1,
     * PatternItemFilter.INSTANCE)}, U95, whose third argument is the stack limit).</p>
     */
    public static class PatternSlot extends SlotItemHandler {

        private final InternalInventory inventory;

        PatternSlot(InternalInventory inventory, int index, int x, int y) {
            super(inventory.toItemHandler(), index, x, y);
            this.inventory = inventory;
        }

        /** @return AE2's own inventory, for a caller that must not re-derive the index from the bridge */
        public InternalInventory getPatternInventory() {
            return inventory;
        }

        /** AE2's own public predicate — the rule {@code PlacableItemType.ENCODED_PATTERN} enforces on its slots. */
        @Override
        public boolean mayPlace(ItemStack stack) {
            return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
        }

        /** One pattern per slot, which is what upstream's inventory limit of 1 means. */
        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
