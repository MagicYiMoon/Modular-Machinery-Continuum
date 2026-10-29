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

import hellfirepvp.modularmachinery.common.block.prop.ItemBusSize;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.tiles.base.TileItemBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * The server-side half of an item bus GUI, with real inventory slots.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream extended {@code ContainerBase} and populated itself from an
 * {@code IInventory}. On 1.20.1 the equivalent is {@link SlotItemHandler} wrapping an
 * {@code IItemHandler} — the same capability the machine's recipes use, so the GUI and the craft logic
 * see one inventory rather than two that must be kept in step.
 *
 * <h2>The client-side problem this class has to solve</h2>
 *
 * <p>{@link AbstractContainerMenu} is constructed on <b>both</b> sides. On the client the block entity
 * frequently does not exist yet: the open packet arrives before the chunk containing the bus is loaded,
 * or at all if the player moved away. Yet the slot list must have the <b>same length on both sides</b> —
 * {@code AbstractContainerMenu} matches slots by index when it synchronises, so a mismatch shifts every
 * stack and corrupts the view.
 *
 * <p>Hence the slot <b>count</b> travels in the open packet alongside the position: the client builds the
 * right number of slots backed by a throwaway handler, and the real contents arrive through the normal
 * menu synchronisation. Guessing the count from a block state the client may not have would be the
 * source of an intermittent desync that only appears on slow connections.
 */
public class MenuItemBus extends AbstractContainerMenu {

    /**
     * Panel size. The standard 176x166: every tier texture is 256x256 with the panel occupying its
     * top-left corner, which is the usual Minecraft GUI convention and what upstream's GuiContainerBase
     * used (its setWidthHeight() was left empty so the default applied).
     */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;

    /** Player inventory geometry, matching upstream ContainerBase exactly. */
    private static final int PLAYER_INV_Y = 84;
    private static final int PLAYER_HOTBAR_Y = 142;
    private static final int PLAYER_INV_X = 8;


    private final BlockPos busPos;
    private final int busSlots;
    private final ItemBusSize size;

    /**
     * Client-side constructor, invoked from the menu type's factory.
     *
     * @param slotCount the bus's slot count, carried in the open packet so both sides agree
     */
    /**
     * Client-side constructor, invoked from the menu type's factory.
     *
     * @param sizeOrdinal the bus's TIER, carried in the open packet. The tier is sent rather than the
     *                    slot count because it carries the count <b>and</b> the grid geometry: a bare
     *                    count would let the client build the right number of slots in the wrong
     *                    places, which misaligns every stack without producing any error.
     */
    public MenuItemBus(int windowId, Inventory playerInventory, BlockPos busPos, int sizeOrdinal) {
        this(windowId, playerInventory, busPos, sizeFromOrdinal(sizeOrdinal),
                new ItemStackHandler(sizeFromOrdinal(sizeOrdinal).getSlots()));
    }

    /**
     * Resolves a tier from its on-wire ordinal, clamping rather than throwing: a malformed packet must
     * not break screen construction on the client, and the server never trusts this value for anything.
     */
    private static ItemBusSize sizeFromOrdinal(int ordinal) {
        ItemBusSize[] values = ItemBusSize.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return ItemBusSize.TINY;
        }
        return values[ordinal];
    }

    public MenuItemBus(int windowId, Inventory playerInventory, BlockPos busPos, ItemBusSize size,
                       IItemHandler inventory) {
        super(RegistryMenus.ITEM_BUS.get(), windowId);
        this.busPos = busPos;
        this.size = size;
        this.busSlots = size.getSlots();

        // The bus's own slots, positioned by the TIER's geometry -- not a 9-wide chest grid.
        //
        // Each ItemBusSize has its own count and grid (TINY 1 slot at (81,30); NORMAL 6 at (61,18) in a
        // 3x2; LUDICROUS 32 at (17,8) in an 8x4), and the GUI texture textures/gui/inventory_<tier>.png
        // draws the frame at exactly those positions. Reading the geometry from the tier is what keeps
        // slots inside their frame. Upstream had a per-tier switch in ContainerItemBus#addInventorySlots
        // carrying the same numbers; they live on the enum here so the screen can share them.
        for (int index = 0; index < busSlots; index++) {
            addSlot(new SlotItemHandler(inventory, index, size.getSlotX(index), size.getSlotY(index)));
        }

        // The player's inventory, always present so the player can move items in and out of the bus. This
        // is also what makes shift-clicking meaningful -- see quickMoveStack.
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

    /**
     * Builds the server-side menu for a bus, using the bus's <b>real</b> inventory.
     *
     * @return the menu, or {@code null} when the position holds no item bus
     */
    public static MenuItemBus forBus(int windowId, Inventory playerInventory, TileItemBus bus) {
 
        //
        // This read the tier from the BLOCKSTATE and the handler from the TILE -- two reads of one tile that
        // can disagree, and did: `Slot 6 not in valid range - [0,6)`, a menu built for a larger tier over a
        // six-slot handler (section 274).
        //
        // The tier is still needed rather than the bare count, because the tier carries the slot GEOMETRY --
        // a count alone would place the right number of slots in the wrong places. Recovering the tier FROM the
        // count is what makes geometry and count come from one quantity instead of two.
        net.minecraftforge.items.IItemHandler handler = bus.getInventory();
        return new MenuItemBus(windowId, playerInventory, bus.getBlockPos(),
                ItemBusSize.forSlotCount(handler.getSlots()), handler);
    }

    /** @return this bus's tier, which carries both the slot count and the GUI geometry */
    public ItemBusSize getSize() {
        return size;
    }

    /** @return the number of slots this bus has, i.e. excluding the player inventory */
    public int getBusSlotCount() {
        return busSlots;
    }

    public BlockPos getBusPos() {
        return busPos;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Shift-click moves between the bus and the player inventory in whichever direction the clicked
     * slot belongs to. Implemented rather than returning empty — unlike the controller menu, which has no
     * slots at all — because a bus exists to move items through, and a player shift-clicking a stack into
     * it is the normal way to load a machine.
     */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int busSlots = getBusSlotCount();

        if (index < busSlots) {
            // Bus -> player.
            if (!moveItemStackTo(stack, busSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player -> bus. mergeItemStack returns false when nothing fit, which is the correct signal
            // to leave the stack alone rather than moving part of it.
            if (!moveItemStackTo(stack, 0, busSlots, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Bound to the live block entity, so breaking the bus or walking away closes the screen. Checked on
     * the server each tick, which is what stops a client from keeping a window open over a bus that no
     * longer exists and then writing stacks into nothing.
     */
    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(busPos) instanceof TileItemBus
                && player.distanceToSqr(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5)
                        <= 64.0;
    }
}
