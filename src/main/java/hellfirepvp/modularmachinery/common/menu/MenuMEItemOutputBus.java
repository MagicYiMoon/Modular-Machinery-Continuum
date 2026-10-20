/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 * GNU GENERAL PUBLIC LICENSE Version 3. https://github.com/HellFirePvP/ModularMachinery
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0). MODIFIED. Modifications made 2026-10.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.registry.RegistryMEMenus;
import hellfirepvp.modularmachinery.common.tiles.me.MEItemOutputBus;
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
import org.jetbrains.annotations.Nullable;

/**
 * The server-side half of the ME ITEM OUTPUT bus GUI — <b>the direction that had no screen at all</b>
 * (AUDIT_COMPONENTS row 11: "输出方向打不开任何界面").
 *
 * <h2>What upstream has, read at the body (rule A)</h2>
 *
 * <pre>
 *   ContainerMEItemOutputBus (29 lines)
 *     C16  bindPlayerInventory(getInventoryPlayer(), 0, 195 - 82)      -> the player's rows at y = 113
 *     C18  guiAccess = owner.getInternalInventory().asGUIAccess()
 *     C19-C23  4 rows x 9 of SlotDisabled(guiAccess, y*9+x, 8 + 18x, 24 + 18y)   -> THE 36, READ-ONLY
 *
 *   GuiMEItemOutputBus (77 lines)
 *     U29  this.ySize = 195
 *     U21  TEXTURES_OUTPUT_BUS = appliedenergistics2:textures/guis/skychest.png
 *     U36-U40  a wrench button at (guiLeft - 18, guiTop + 8) whose tooltip is gui.meitembus.stack_size.config,
 *              and the click sends PktSwitchGuiMEOutputBus(pos, 1)
 *     U56  the title gui.meitemoutputbus.title at (8, 8)
 *
 *   ContainerMEItemOutputBusStackSize (47 lines)  -- the SECOND menu, reached by that button
 *     C14-C21  @GuiSync(0) public int stackSize = outputBus.getConfiguredStackSize()
 *     C40-C46  onContainerClosed -> outputBus.setConfiguredStackSize(this.stackSize)
 *   GuiMEItemOutputBusStackSize (317 lines)  -- eight buttons (+/-1, 10, 100, 1000), a text field at (62, 57),
 *              a mouse-wheel gesture and a tab button back to the first GUI
 * </pre>
 *
 * <h2>RULE A0 — why this is ONE vanilla menu with a data slot, and what that changes (three parts)</h2>
 *
 * <p><b>(1) Upstream's method:</b> the 36 read-only slots are AE2's {@code SlotDisabled} over an
 * {@code IItemHandlerModifiable.asGUIAccess()} view; the stack size travels in a {@code @GuiSync} field on an
 * {@code AEBaseContainer}; the eight buttons live in a SECOND screen with a {@code GuiImgButton}/{@code GuiTabButton}
 * chrome and an AE2 expression parser for typed input.</p>
 *
 * <p><b>(2) Evidence it cannot be followed on 1.20.1 / AE2 15.4.11</b> — measured against the jar this project
 * compiles against, by entry name:</p>
 *
 * <pre>
 *   appeng/container/                           ABSENT   -- no AEBaseContainer, no SlotDisabled
 *   appeng/client/gui/AEBaseGui.class           ABSENT   -- the 1.12.2 screen base (the 15.x screens derive from
 *                                                         a different base, whose whole chrome stack would come with it)
 *   appeng/client/gui/widgets/GuiImgButton      ABSENT   -- and GuiTabButton: zero entries for either name
 *   appeng/menu/guisync/GuiSync.class           PRESENT  -- but it annotates a field of an AE2 MENU, and this port's
 *                                                         menus are vanilla AbstractContainerMenu
 *   appeng/client/gui/MathExpressionParser      PRESENT  -- so upstream's typed expression input IS portable, and is
 *                                                         NOT ported: see (3)
 * </pre>
 *
 * <p><b>(3) What the port does instead</b> — the port's own conventions, reused rather than reinvented (rule B):</p>
 *
 * <pre>
 *   SlotDisabled over asGUIAccess()  -> a SlotItemHandler whose mayPlace/mayPickup are false (the shape
 *                                       MenuMEItemInputBus.StoredSlot already uses for the same purpose)
 *   @GuiSync int stackSize           -> ContainerData + addDataSlots, this port's convention for a value the screen
 *                                       displays (MenuMEFluidInputBus's nine fluids, MenuParallelController's number)
 *   the second GUI + tab button      -> ONE screen: the eight +/-1/10/100/1000 buttons upstream's SECOND screen has
 *                                       are drawn on the first, and each click sends PktMEItemOutputBusStackSize.
 *                                       <b>The expression parser is NOT ported</b>: it is the one AE2 piece that does
 *                                       exist here, and taking it would make a client-only AE2 class a dependency of
 *                                       an integer field, while the buttons already reach every value upstream's
 *                                       field could (upstream's own buttons are the same eight).
 *   PktSwitchGuiMEOutputBus          -> not needed: there is no second GUI to switch to
 *   PktMEOutputBusStackSizeChange    -> PktMEItemOutputBusStackSize, which carries a DELTA the server clamps and
 *                                       applies, and whose authorization is the port's standard pair (the player's own
 *                                       open menu, re-derived; the 64-block range)
 * </pre>
 *
 * <h2>What the screen draws, and the one place this menu is narrower than upstream</h2>
 *
 * <p>The 36 slots are laid out as the 9x4 grid the port's own {@code meiteminputbus.png} draws at (8 + 18x,
 * 123 + 18y) — <b>the art's own wells, reused</b>. Upstream's art for this screen is AE2's 1.12.2
 * {@code skychest.png}, which does not exist in AE2 15.4.11 (the namespace moved to {@code ae2}, and the surviving
 * {@code ae2:textures/guis/chest.png} draws a SINGLE non-player well at (79, 39) — its four 18px rows are the player
 * inventory's), and no shipped modularmachinery texture has a 36-well area. <b>So the panel is the input bus's, its
 * lower well band carries the output bus's 36 slots, and the PLAYER's own rows are not drawn</b> — upstream binds them
 * (C16), this screen does not, and that is the one deviation rather than an oversight: the panel's remaining wells are
 * two 4x4 bands that cannot hold the player's 36 in a vanilla layout. The bus's slots are read-only in both versions, so
 * nothing a player could do in upstream's screen is impossible here.</p>
 */
public class MenuMEItemOutputBus extends AbstractContainerMenu {

    /**
     * Panel size. <b>176x204 is the art this screen blits</b> ({@code meiteminputbus.png}, measured by the suite's own
     * panel check), NOT upstream's 195: upstream's 195 belongs to the AE2 sky-chest art, which (2) above shows cannot be
     * fetched, and a panel whose size disagrees with the texture it draws would cut the art off — the exact defect §91
     * recorded for the machine controller.
     */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 204;

    /**
     * The slot grid: 9 columns x 4 rows = <b>36</b>, which is {@code MEItemOutputBus.SLOTS} and upstream's
     * {@code y*9+x} loop (C19-C23).
     *
     * <p>Public because the self-test reads them, and because the screen places its labels from the same numbers: a
     * private constant is a value no check can see (§677).</p>
     */
    public static final int SLOT_COLUMNS = 9;
    public static final int SLOT_ROWS = 4;
    public static final int SLOTS = SLOT_COLUMNS * SLOT_ROWS;

    /** The well band's origin, in {@code meiteminputbus.png}'s own wells: rows at 123/141/159/181, columns at 8+18x. */
    public static final int SLOT_X = 8;
    public static final int SLOT_Y = 123;

    /** ContainerData layout: one int, the cap this bus applies to every slot (upstream's {@code @GuiSync(0)}). */
    public static final int DATA_STACK_SIZE = 0;
    public static final int DATA_SIZE = 1;

    private final BlockPos busPos;
    private final ContainerData data;

    /**
     * Client-side constructor, invoked from the menu type's factory.
     *
     * <p>The handler here is a THROWAWAY one, the same shape {@code MenuMEItemInputBus} uses: on the client the tile
     * frequently does not exist yet when the open packet arrives, so the slots are built over storage that holds
     * nothing and the real contents arrive through the ordinary menu synchronisation. What makes that safe is that the
     * grid's length is a CONSTANT of this class on both sides.</p>
     */
    public MenuMEItemOutputBus(int windowId, Inventory playerInventory, BlockPos busPos) {
        this(windowId, playerInventory, busPos, new ItemStackHandler(SLOTS), new SimpleContainerData(DATA_SIZE));
    }

    /** Both-sides constructor: the server passes the tile's live handler, the client a throwaway one and null. */
    private MenuMEItemOutputBus(int windowId, Inventory playerInventory, BlockPos busPos, IItemHandler busInventory,
                                ContainerData data) {
        super(RegistryMEMenus.ME_ITEM_OUTPUT_BUS.get(), windowId);
        this.busPos = busPos;
        // A vanilla container data packet carries one signed short.  IntContainerData
        // splits the logical 32-bit value into two unsigned words, matching the fluid
        // menus and preserving capacities above 65535 (including Integer.MAX_VALUE).
        this.data = data;

        for (int index = 0; index < SLOTS; index++) {
            addSlot(new DisabledSlot(busInventory, index,
                    SLOT_X + 18 * (index % SLOT_COLUMNS), SLOT_Y + 18 * (index / SLOT_COLUMNS)));
        }
        // The cap's display value, and the reason this menu needs a data slot at all: the screen shows what the tile
        // holds, and the tile is the server's.
        addDataSlots(new IntContainerData(data));
    }

    /**
     * Builds the server-side menu for a real bus, over the bus's own buffer and its own cap.
     *
     * <p>The handler is taken FROM the tile rather than through a blockstate or a registry lookup, which is §275's rule:
     * one reader for one quantity. A menu built for a different handler than the tile uses is the defect that crashed on
     * {@code Slot 6 not in valid range - [0,6)}.</p>
     */
    public static MenuMEItemOutputBus forBus(int windowId, Inventory playerInventory, MEItemOutputBus bus) {
        return new MenuMEItemOutputBus(windowId, playerInventory, bus.getBlockPos(),
                bus.getInternalInventory(), dataFor(bus));
    }

    /**
     * The cap as the CLIENT reads it: a {@code ContainerData} over the tile, in the shape
     * {@code MenuMEFluidInputBus.dataFor} established.
     *
     * <p>{@code get} is called on the server every {@code broadcastChanges} and on the client when the update arrives;
     * {@code set} is only ever reached ON THE CLIENT (vanilla's {@code ClientboundContainerSetDataPacket} path), so it
     * writes nothing anywhere — the value it is handed is the server's, and the client's copy is this object.</p>
     */
    public static ContainerData dataFor(MEItemOutputBus bus) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return bus.getConfiguredStackSize();
            }

            @Override
            public void set(int index, int value) {
                // NO-OP ON PURPOSE: this object lives on the SERVER, where the value comes FROM the tile. A write here
                // would make the displayed number an input to the tile instead of a report of it.
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    /** @return the cap the tile applies to every slot, as the menu's own data slot reports it */
    public int getConfiguredStackSize() {
        return data.get(DATA_STACK_SIZE);
    }

    public BlockPos getBusPos() {
        return busPos;
    }

    /**
     * @return whether {@code menu} is an ME item output bus menu looking at {@code pos}
     *
     * <p>Present for the same reason {@code MenuMEFluidInputBus.isOpenFor} is: the question "is this the menu the player
     * actually has open" belongs in one place, and naming it here removes a packet's need to name the menu TYPE — a
     * reference that would pull an AE2-gated registry through a class that loads without AE2 (PLAN 478).</p>
     */
    public static boolean isOpenFor(@Nullable AbstractContainerMenu menu, BlockPos pos) {
        return menu instanceof MenuMEItemOutputBus busMenu && busMenu.getBusPos().equals(pos);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // NOTHING MOVES, and the two reasons are separate: the bus's own 36 slots refuse both placement and pickup
        // (DisabledSlot), and this menu carries no player slots for them to move into or out of — see the class javadoc.
        // Returning EMPTY is the honest answer rather than a half-implemented transfer.
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(busPos) instanceof MEItemOutputBus
                && player.distanceToSqr(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5) <= 64.0;
    }

    /**
     * One of the 36 buffer slots: <b>read-only, which is the whole content of the class</b>.
     *
     * <p>Upstream's {@code SlotDisabled}, and its reason is the same direction as {@code MenuMEItemInputBus.StoredSlot}'s:
     * the buffer is what the machine has already produced, and a player taking a stack out of it through a screen would
     * be removing an item the machine had promised to the network. <b>Note what this means for parity:</b> in upstream
     * these 36 slots are ALSO disabled, so the absence of the player's rows changes nothing about what a player can do
     * here.</p>
     */
    public static class DisabledSlot extends SlotItemHandler {

        public DisabledSlot(IItemHandler handler, int index, int x, int y) {
            super(handler, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public int getMaxStackSize() {
            // Zero, as the input bus's config cells return, so no vanilla path (drag, hotbar swap, shift-click,
            // creative pick) can put a stack into a slot whose contents the machine owns.
            return 0;
        }
    }

    /** A slot of this menu, for a reader that wants to know whether an index is the bus's or not (there are no others). */
    public boolean isBusSlot(int index) {
        Slot slot = index >= 0 && index < slots.size() ? slots.get(index) : null;
        return slot instanceof DisabledSlot;
    }
}
