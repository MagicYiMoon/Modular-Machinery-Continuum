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
import hellfirepvp.modularmachinery.common.tiles.me.MEItemInputBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;

/**
 * The server-side half of the ME item input bus GUI — the missing entrance §649(b) recorded and §665.5 explicitly did
 * NOT close: the block had no {@code use}, there was no menu type and no screen, so
 * {@code modularmachinery:blockmeiteminputbus} could not be opened by right-click at all.
 *
 * <h2>What upstream's two files are (rule A — read at the body, not the signature)</h2>
 *
 * <pre>
 *   common/container/ContainerMEItemInputBus (39 lines, read IN FULL at §686.2)
 *     C17   bindPlayerInventory(getInventoryPlayer(), 0, 195 - 72)   -> the player's rows at y = 123
 *     C19   config = owner.getConfigInventory().asGUIAccess()
 *     C20-C25  4x4 of SlotFake(config, y*4+x, 8 + 18x, 35 + 18y)     -> the CONFIG grid
 *     C27   internal = owner.getInternalInventory().asGUIAccess()
 *     C28-C33  4x4 of SlotDisabled(internal, y*4+x, 90 + 8 + 18x, 35 + 18y) -> the STORED grid, read-only
 *
 *   client/gui/GuiMEItemInputBus (262 lines)
 *     U43   TEXTURES_INPUT_BUS = modularmachinery:textures/gui/meiteminputbus.png
 *     U49   this.ySize = 204                                          -> THE PANEL HEIGHT
 *     U167-U170  drawFG: the title, Config, Stored Items and the inventory label
 *     U181-U202  renderToolTip: "Items marked: N" on a fake slot, "Items Cached: N" on a disabled one
 * </pre>
 *
 * <p><b>And the two grids are 16 slots, not 27 — and since the slot-count fix they are the tile's OWN two halves, not
 * a window onto them.</b> Upstream's tile is {@code IOInventory(this, slotIDs, new int[]{})} with {@code int size = 16}
 * for both the buffer and the config (upstream {@code MEItemInputBus} U36 and U57) — the 4x4 geometry only makes sense
 * at 16 — and the port's own {@code meiteminputbus.png} draws exactly four wells per row in two 4x4 blocks. The port
 * used to build a 27-slot buffer and a 27-slot config ({@code MEBusTiles.SLOTS}), so this grid showed the first
 * {@link #CONFIG_SLOTS} of a longer handler and eleven machine-facing buffer slots had no configuration cell at all:
 * nothing could refill them, and the capability exposed them anyway. <b>That is fixed at the tile:
 * {@code MEItemInputBus.STORAGE_SLOTS} is 16 and so is its {@code CONFIG_SLOTS}</b>, so this grid, both halves of the
 * tile and the capability now agree on one number, and a check asserts the four of them equal rather than a total.</p>
 *
 * <h2>RULE A0 — why this is a vanilla menu, and what that changes</h2>
 *
 * <p><b>(1) Upstream's method:</b> {@code ContainerMEItemInputBus extends AEBaseContainer}, and the two grids are AE2's
 * {@code SlotFake} and {@code SlotDisabled}; the count on a marked stack is changed by scroll wheel through
 * {@code PktMEInputBusInvAction}, and a config slot's item arrives from JEI through AE2's
 * {@code PacketInventoryAction(InventoryAction.PLACE_JEI_GHOST_ITEM, ...)} (upstream U246).</p>
 *
 * <p><b>(2) Evidence it cannot work on 1.20.1 / AE2 15.4.11</b> — {@code appeng/container/} is absent from the jar this
 * project compiles against: no {@code AEBaseContainer}, no {@code SlotFake}, no {@code SlotDisabled}, no
 * {@code InventoryAction}, no {@code PacketInventoryAction}. This is the same absence {@code MenuMEFluidInputBus}
 * recorded for {@code appeng/fluids/}, one package over, and it is why the port's own menu is the convention here.</p>
 *
 * <p><b>(3) What the port does instead:</b></p>
 *
 * <pre>
 *   AEBaseContainer            -> a vanilla AbstractContainerMenu (the §665 convention)
 *   SlotFake over IOInventory  -> a SlotItemHandler over the tile's REAL ItemStackHandler
 *   SlotDisabled               -> a SlotItemHandler whose mayPlace/mayPickup are false ("display only")
 *   PktMEInputBusInvAction     -> replaced by the port's validated {@code PktMEItemInputBusCount}; it updates the count
 *                                 without trusting a client supplied block or slot target
 *   JEI ghost drag             -> handled by the optional JEI plugin and a dedicated validated packet; the menu remains
 *                                 the server-side target check and single configuration writer
 *   setupUpgrades              -> NOT PORTED: needs AE2's UpgradeInventory/IUpgradeableHost, exactly as §665.5(c)
 *                                 recorded for the fluid bus
 * </pre>
 *
 * <h2>Why the config slot is a REAL slot here, where the fluid one was a ghost</h2>
 *
 * <p>The fluid bus's config slot had to be a ghost because a {@code Slot} holds an {@link ItemStack} and a fluid is not
 * one — that is why {@code MenuMEFluidInputBus} needed {@code EmptyContainer}, a {@code ContainerData} pair and
 * {@code PktMEFluidInputBusConfig}. <b>An item configuration IS an item stack</b> (the mark is literally "keep this
 * many of this item"), so the tile's own {@link ItemStackHandler} is the correct backing store, vanilla syncs a slot's
 * stack for free, and <b>no packet is needed for this screen at all</b>.</p>
 *
 * <p>What still has to be replaced is {@code SlotFake}'s BEHAVIOUR, because 1.20.1's {@code Slot} has no
 * {@code mayPlace}-driven click handler: {@code Slot}'s own {@code set}/{@code setChanged} are public and unprotected,
 * so vanilla's click path would treat this as an ordinary container slot and let a player pick a marked stack back out.
 * {@link #clicked} is therefore overridden, exactly as {@code MenuMEFluidInputBus.clicked} is and for the same reason
 * (see that class's javadoc): the click arrives at the MENU on both sides in 1.20.1.</p>
 */
public class MenuMEItemInputBus extends AbstractContainerMenu {

    /**
     * Panel size, from upstream's own numbers: {@code GuiMEItemInputBus} sets {@code ySize = 204} in its constructor
     * (upstream U49) and the container binds the player's rows from {@code 195 - 72 = 123} (upstream C17), which is
     * {@code 204 - 82} — this port's relation for a 204-tall panel. The width is the vanilla default 176.
     *
     * <p>Both numbers were then MEASURED against the shipped art rather than trusted:
     * {@code assets/modularmachinery/textures/gui/meiteminputbus.png} is 256x256 with its panel drawn to 176x204, its
     * player wells at rows 123/141/159/181 and its four config wells' top borders at rows 34/52/70/88. Asserted by
     * {@code MachineSelfTest}'s "panel sizes match the jar" and "every screen's player slots land on its texture's
     * wells".</p>
     */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 204;

    /**
     * The config grid's geometry, from upstream U20-U25: 4x4, at {@code 8 + 18x} / {@code 35 + 18y}.
     *
     * <p>{@code CONFIG_X}/{@code CONFIG_Y} are PUBLIC for the reason {@link #PLAYER_INV_X} and its neighbours are: a
     * constant variable is INLINED at its reference site, so the suite reads these numbers without loading this class —
     * which matters, because this class names an AE2-connected tile. A private constant is a value no check can see
     * (§677's {@code MenuUpgradeBus} lesson).</p>
     */
    public static final int CONFIG_COLUMNS = 4;
    public static final int CONFIG_ROWS = 4;
    public static final int CONFIG_SLOTS = CONFIG_COLUMNS * CONFIG_ROWS;
    public static final int CONFIG_X = 8;
    public static final int CONFIG_Y = 35;

    /** The STORED grid's origin, upstream U31: {@code 90 + 8 + 18 * x}, {@code 35 + 18 * y}. */
    public static final int STORED_X = 90 + 8;
    public static final int STORED_Y = 35;

    /**
     * The player's rows.
     *
     * <p><b>They are the WELL BODY rows, and the art is what decided the exact numbers:</b> {@code meiteminputbus.png}
     * draws the four player well rows' cells at y = 123/141/159/181, with each well's top border one pixel above
     * (122/140/158/180). Upstream's container binds them from {@code 195 - 72 = 123} (its C17), which is the same
     * number — and 123 is what landed on the wells when this screen was measured, while {@code 204 - 82 = 122} (the
     * relation the fluid screen uses for its 231-tall panel) landed on the border row instead and was caught by the
     * suite's wells check: four rows, 4/4 separators each, top border 0/18.</p>
     *
     * <p><b>So the subtraction here is {@code PANEL_HEIGHT - 81} / {@code - 23}, not the fluid screen's
     * {@code - 82} / {@code - 24}, and the difference is one pixel of panel rather than a different rule.</b> Written
     * as the subtraction for the same reason the fluid screen is: the numbers cannot drift from {@link #PANEL_HEIGHT}.
     * Public, so the suite's wells check samples them rather than transcribing them.</p>
     */
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = PANEL_HEIGHT - 81;
    public static final int PLAYER_HOTBAR_Y = PANEL_HEIGHT - 23;

    private final BlockPos busPos;

    /**
     * Client-side constructor, invoked from the menu type's factory.
     *
     * <p>The handler built here is a THROWAWAY one, the same shape {@code MenuItemBus} uses: on the client the tile
     * frequently does not exist yet when the open packet arrives, so the slot list is built over storage that holds
     * nothing and the real marks arrive through the ordinary menu synchronisation. What makes that safe is that the
     * grid's length is a CONSTANT of this class on both sides — see the constructor below.</p>
     */
    public MenuMEItemInputBus(int windowId, Inventory playerInventory, BlockPos busPos) {
        this(windowId, playerInventory, busPos, new ItemStackHandler(CONFIG_SLOTS), null);
    }

    /** Both-sides constructor: the server passes the tile's live handler, the client a throwaway one and null. */
    private MenuMEItemInputBus(int windowId, Inventory playerInventory, BlockPos busPos,
                               ItemStackHandler config, @Nullable IItemHandler stored) {
        super(RegistryMEMenus.ME_ITEM_INPUT_BUS.get(), windowId);
        this.busPos = busPos;

        for (int index = 0; index < CONFIG_SLOTS; index++) {
            addSlot(new ConfigSlot(this, config, index,
                    CONFIG_X + 18 * (index % CONFIG_COLUMNS), CONFIG_Y + 18 * (index / CONFIG_COLUMNS)));
        }
        for (int index = 0; index < CONFIG_SLOTS; index++) {
            int x = STORED_X + 18 * (index % CONFIG_COLUMNS);
            int y = STORED_Y + 18 * (index / CONFIG_COLUMNS);
            if (stored != null && index < stored.getSlots()) {
                // The real buffer half of the same 4x4 grid: what the bus currently holds for the machine.
                addSlot(new StoredSlot(stored, index, x, y));
            } else {
                // The client, and any index past the buffer's own size: a display-only slot over a handler that holds
                // nothing. It must still EXIST, or the two sides' slot lists differ in length and every
                // player-inventory index that follows shifts.
                addSlot(new StoredSlot(new ItemStackHandler(1), 0, x, y));
            }
        }
        addPlayerInventory(playerInventory);
    }

    /**
     * Builds the server-side menu for a real bus, over the bus's own two handlers.
     *
     * <p>The handlers are taken FROM the tile rather than through a blockstate or a registry lookup, which is §275's
     * rule: one reader for one quantity. A menu built for a different handler than the tile uses is the defect that
     * crashed on {@code Slot 6 not in valid range - [0,6)}.</p>
     */
    public static MenuMEItemInputBus forBus(int windowId, Inventory playerInventory, MEItemInputBus bus) {
        return new MenuMEItemInputBus(windowId, playerInventory, bus.getBlockPos(),
                bus.getConfigInventory(), bus.getInternalInventory());
    }

    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9,
                        PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, PLAYER_INV_X + column * 18, PLAYER_HOTBAR_Y));
        }
    }

    public BlockPos getBusPos() {
        return busPos;
    }

    /**
     * @return whether {@code menu} is an ME item input bus menu looking at {@code pos}
     *
     * <p>Present for the same reason {@code MenuMEFluidInputBus.isOpenFor} is: the question "is this the menu the
     * player actually has open" belongs in one place, and naming it here removes a packet's need to name the menu
     * TYPE — a reference that would pull an AE2-gated registry through a class that loads without AE2.</p>
     */
    public static boolean isOpenFor(@Nullable AbstractContainerMenu menu, BlockPos pos) {
        return menu instanceof MenuMEItemInputBus busMenu && busMenu.getBusPos().equals(pos);
    }

    /**
     * @return the config slot at {@code index}, or {@code null} when the index is not one of the 4x4 grid
     *
     * <p><b>Not {@code slots.get(i) instanceof ConfigSlot} at the call site</b>, for the reason §629 recorded: the
     * menu's own slot list is the only thing that knows where its config slots are, so a caller that recomputed
     * {@code index < CONFIG_SLOTS} would be a second copy of a layout rule.</p>
     */
    @Nullable
    public ConfigSlot configSlotAt(int index) {
        if (index < 0 || index >= CONFIG_SLOTS) {
            return null;
        }
        Slot slot = slots.get(index);
        return slot instanceof ConfigSlot config ? config : null;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>This is where a config click is handled, and it is the whole write path of this screen</b> — upstream's
     * {@code SlotFake} plus {@code PktMEInputBusInvAction} plus the JEI ghost packet, collapsed into one vanilla
     * method. The click arrives here on BOTH sides (the client sends
     * {@code ServerboundContainerClickPacket}, the server runs the same method on the same index), and the server's
     * own {@code clicked} call performs the write. The result then travels back through the ordinary slot sync, so
     * this screen needs no packet of its own.</p>
     *
     * <p>Every non-config slot falls through to vanilla, which is why this override names {@link ConfigSlot} rather
     * than reimplementing pickup/place for the 16 stored slots and the player's 36.</p>
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        ConfigSlot config = configSlotAt(slotId);
        if (config != null && !player.level().isClientSide()) {
            if (!player.containerMenu.equals(this)) {
                // A click for a menu the player no longer has open. The server already refuses a mismatched window id
                // before this method is reached, so this is the second, explicit reader of the same rule — and the
                // only one that also holds for the write path this menu will gain if a packet is added later.
                return;
            }
            config.setMarked(player.containerMenu.getCarried());
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The CONFIG grid is not a shift-click target, and that refusal is deliberate: a marked stack is a
     * configuration, and the gesture that produces one is "hold the stack, click the cell" ({@link ConfigSlot}). A
     * shift-click that silently marked sixteen cells with the same stack would produce a configuration the player
     * never stated.</p>
     *
     * <p>The STORED grid is not a shift-click SOURCE either — {@link StoredSlot#mayPickup} refuses — so items move
     * between the player's own rows, which is all this screen offers.</p>
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (configSlotAt(index) != null) {
            // See above: a marked stack is a configuration, not an item.
            return ItemStack.EMPTY;
        }
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int busSlots = CONFIG_SLOTS * 2;
        if (index < busSlots) {
            // Only the stored grid can hold a stack out of the range, and it refuses; move to the player's rows.
            if (!moveItemStackTo(stack, busSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, busSlots, slots.size(), false)) {
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
        return player.level().getBlockEntity(busPos) instanceof MEItemInputBus
                && player.distanceToSqr(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5) <= 64.0;
    }

    /**
     * One cell of the 4x4 CONFIG grid: the item and count this bus is asked to keep available.
     *
     * <p><b>Backed by the tile's real {@link ItemStackHandler}, because the mark IS an item stack</b> — see the class
     * javadoc for why this is not a ghost slot, unlike the fluid bus's.</p>
     *
     * <p><b>It extends {@link SlotItemHandler} and not vanilla's {@code Slot}, and that is a 1.20.1 fact rather than a
     * style choice:</b> {@code ItemStackHandler} implements Forge's {@code IItemHandler} and NOT vanilla's
     * {@code Container}, so it cannot be passed to {@code Slot}'s constructor at all — the compiler rejected the first
     * version of this class. {@code SlotItemHandler} bridges the two, which is what {@code MenuItemBus} already uses
     * for the ordinary bus's slots (rule B). The consequence is recorded on {@link #mayPlace}: the bridge's own
     * {@code set} writes through to the handler, so the class relies on {@link MenuMEItemInputBus#clicked} to keep
     * vanilla's place/pickup path away from it.</p>
     */
    public static class ConfigSlot extends SlotItemHandler {

        /** The menu, so a click can reach {@link #setMarked} through one named path rather than through {@code slots}. */
        private final MenuMEItemInputBus menu;

        /** Which grid cell this is. Carried explicitly: see {@link #getGridIndex()}. */
        private final int gridIndex;

        /** The tile's config handler — the one reader of the mark, on both sides. */
        private final ItemStackHandler config;

        ConfigSlot(MenuMEItemInputBus menu, ItemStackHandler config, int gridIndex, int x, int y) {
            super(config, gridIndex, x, y);
            this.menu = menu;
            this.config = config;
            this.gridIndex = gridIndex;
        }

        /**
         * @return the grid cell this configures
         *
         * <p><b>Not {@code Slot.getContainerSlot()}.</b> That accessor answers "which slot of the BACKING container",
         * and this slot's backing handler is the tile's config inventory — <b>which is exactly as long as this grid
         * since the slot-count fix</b> (16, {@code MEItemInputBus.CONFIG_SLOTS}), so the two agree by construction
         * rather than by luck. Naming the field is still what makes that a fact rather than an assumption, and it is
         * §275's {@code Slot 6 not in valid range} shape: a slot whose index came from a container that can be
         * resized — which is exactly what a {@code Size} mismatch in NBT used to do to this handler.</p>
         */
        public int getGridIndex() {
            return gridIndex;
        }

        /**
         * Writes {@code marked} into this cell, and this is the ONE producer of an ME item bus configuration.
         *
         * <h2>The gesture, and why the count is the carried stack's</h2>
         *
         * <p>Upstream's config slots are filled from JEI or by AE2's own inventory action, and the COUNT on the marked
         * stack is what the bus keeps available — a single input slot can legitimately hold thousands of an item
         * because the network hands them over at once (§399). Upstream adjusts that count with the scroll wheel, through
         * {@code PktMEInputBusInvAction}. The port sends the requested count through its own validated packet, while the
         * server re-reads the open menu's current stack before changing it.
         * What is ported is the same value expressed by the gesture every other screen in this mod already teaches:
         * <b>hold the stack you mean, click the cell</b> — the rule {@code MenuMEFluidInputBus.ConfigSlot} applies to
         * fluids.</p>
         *
         * <p><b>An empty hand CLEARS the cell</b>, which is how a configuration is removed and how the bus is returned
         * to rule 1 ("nothing configured, so give back whatever is held").</p>
         *
         * <p>The stack written is the one the SERVER sees in the player's carried slot, never a value from a payload:
         * this method is called from {@link MenuMEItemInputBus#clicked} on the server, and the client's own copy of the
         * carried stack is only a claim about what it clicked with.</p>
         *
         * @return true when the cell changed
         */
        public boolean setMarked(@Nullable ItemStack marked) {
            ItemStack value = marked == null ? ItemStack.EMPTY : marked.copy();
            ItemStack before = config.getStackInSlot(gridIndex);
            // An unchanged write is skipped so that doBusWork is not woken for a click that configured nothing --
            // a config change marks the slot dirty, and the bus would then re-examine it every tick.
            if (ItemStack.matches(before, value)) {
                return false;
            }
            // The handler is the single source of the mark: writing anywhere else would need a second sync path that
            // could disagree with the one doBusWork reads.
            config.setStackInSlot(gridIndex, value);
            return true;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            // Never through vanilla: a marked stack is written by setMarked, and letting vanilla place here would make
            // the grid an ordinary inventory a player can lose items in.
            return false;
        }

        @Override
        public boolean mayPickup(Player player) {
            // A marked stack is a CONFIGURATION. Taking it out would silently unconfigure the bus, which is what the
            // empty-hand click already expresses on purpose.
            return false;
        }

        @Override
        public int getMaxStackSize() {
            // Zero, as MenuMEFluidInputBus.ConfigSlot returns: no vanilla path (drag, hotbar swap, shift-click,
            // creative pick) may put a stack in this cell.
            return 0;
        }

        /** @return the menu this cell belongs to, for the screen's tooltip and for the self-test */
        public MenuMEItemInputBus getMenu() {
            return menu;
        }
    }

    /**
     * One cell of the 4x4 STORED grid: what the bus currently holds for the machine.
     *
     * <p>Upstream's {@code SlotDisabled}: <b>read-only, and the refusal is the whole content of the class.</b> The
     * buffer is the machine's input — a player taking a stack out of it through a screen would be removing an item the
     * machine had already been promised.</p>
     */
    public static class StoredSlot extends SlotItemHandler {

        public StoredSlot(IItemHandler handler, int index, int x, int y) {
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
    }
}
