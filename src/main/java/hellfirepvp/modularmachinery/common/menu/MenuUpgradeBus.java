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

import hellfirepvp.modularmachinery.common.block.prop.UpgradeBusData;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import hellfirepvp.modularmachinery.common.tiles.TileUpgradeBus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;

/**
 * The upgrade bus GUI: a grid of upgrade slots plus the player's inventory.
 *
 * <h2>Geometry, read from the jar -- and why it is not the usual shape</h2>
 *
 * <p>Two things about this screen differ from every other container in the mod, and both were read out of
 * {@code ContainerUpgradeBus} rather than assumed:
 *
 * <ol>
 *   <li><b>The upgrade slots wrap every 3 columns.</b> From the jar's {@code addInventorySlots}:
 *       {@code x = 8; y = 17; ... x += 18; if ((index+1) % 3 == 0) { x = 8; y += 18; }}. So this is a
 *       <b>3-wide</b> grid, not the 9-wide chest layout used elsewhere. The largest tier, ULTIMATE with 18
 *       slots, becomes 6 rows ending at y=125.</li>
 *   <li><b>The player inventory is at y=131, not 84</b>, and the hotbar at <b>189</b>, not 142.</li>
 * </ol>
 *
 * <p>Those two facts explain each other: {@code ContainerUpgradeBus} <b>overrides</b>
 * {@code ContainerBase#addPlayerSlots} to push the player inventory down, because the upgrade grid
 * occupies the space above, and grows the panel to <b>213</b> to fit. 125 + 18 = 143, clear of 131; and
 * the hotbar at 189 plus its 18 rows plus the border lands on 213.
 *
 * <p><b>So {@code ContainerBase}'s 84/142 are a default, not a constant.</b> This port originally pressed
 * every container into that shape, which cannot render this GUI correctly at all.
 */
public class MenuUpgradeBus extends AbstractContainerMenu {

    /** Panel size, taken from the jar's {@code GuiContainerUpgradeBus}. */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 213;

    /** Upgrade grid origin and wrap width, from the jar's {@code addInventorySlots}. */
    private static final int SLOT_START_X = 8;
    private static final int SLOT_START_Y = 17;
    private static final int COLUMNS = 3;

    // Player inventory, from the jar's overridden {@code addPlayerSlots}. PUBLIC so the self-test can check them
    // against guiupgradebus.png: as private they threw IllegalAccessException inside the wells check, which then
    // skipped this screen and still printed PASS -- the run before that change sampled 2 of its 4 entries (§677).
    public static final int PLAYER_INV_X = 8;
    public static final int PLAYER_INV_Y = 131;
    public static final int PLAYER_HOTBAR_Y = 189;

    private final BlockPos busPos;
    private final UpgradeBusData tier;

    /** Client-side constructor, invoked from the menu type's factory. */
    public MenuUpgradeBus(int windowId, Inventory playerInventory, BlockPos busPos, int tierOrdinal) {
        this(windowId, playerInventory, busPos, tierFromOrdinal(tierOrdinal),
                new ItemStackHandler(tierFromOrdinal(tierOrdinal).getUpgradeSlots()));
    }

    /**
     * Resolves a tier from its on-wire ordinal, clamping rather than throwing.
     *
     * <p>A malformed packet must not break screen construction on the client, and the server never trusts
     * this value for anything that matters.
     */
    private static UpgradeBusData tierFromOrdinal(int ordinal) {
        UpgradeBusData[] values = UpgradeBusData.values();
        if (ordinal < 0 || ordinal >= values.length) {
            return UpgradeBusData.NORMAL;
        }
        return values[ordinal];
    }

    public MenuUpgradeBus(int windowId, Inventory playerInventory, BlockPos busPos, UpgradeBusData tier,
                          IItemHandler inventory) {
        super(RegistryMenus.UPGRADE_BUS.get(), windowId);
        this.busPos = busPos;
        this.tier = tier;

        // The upgrade grid: 3 columns, wrapping every third slot. See the class javadoc -- this is the
        // jar's layout, not a chest grid.
        int x = SLOT_START_X;
        int y = SLOT_START_Y;
        for (int index = 0; index < tier.getUpgradeSlots(); index++) {
            addSlot(new SlotItemHandler(inventory, index, x, y));
            x += 18;
            if ((index + 1) % COLUMNS == 0) {
                x = SLOT_START_X;
                y += 18;
            }
        }

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

    























 
    public static MenuUpgradeBus forBus(int windowId, Inventory playerInventory, TileUpgradeBus bus) {
        // ONE SOURCE FOR THE TIER: THE BLOCK, via the tile's own state-derived accessor. The handler is read
        // through the same accessor's repair, so "the menu exposes exactly the tier's count" and "the handler
        // has exactly that many slots" are the same statement rather than two that can drift.
        return new MenuUpgradeBus(windowId, playerInventory, bus.getBlockPos(), bus.getTier(),
                bus.getUpgradeInventory());
    }

    /**
     * Writes a bus into an open-screen payload, for {@code BlockUpgradeBus.use} and for the check that decodes
     * it back through this menu's own registered factory.
     *
     * <p>ONE WRITER, TWO CALLERS — the shape {@code RegistryCreativeTabs#variantStacks}/{@code tabVariantStacks}
     * already uses: a payload a check re-derives for itself would agree with the block only by luck, and the
     * client half of this family is exactly where the report came from.</p>
     */
    public static void writeOpenData(net.minecraft.network.FriendlyByteBuf buffer, TileUpgradeBus bus) {
        buffer.writeBlockPos(bus.getBlockPos());
        // The tier, not the slot count: it carries the count AND the 3-wide grid geometry.
        buffer.writeVarInt(bus.getTier().ordinal());
    }

    public UpgradeBusData getTier() {
        return tier;
    }

    /** @return the number of upgrade slots, i.e. excluding the player inventory */
    public int getUpgradeSlotCount() {
        return tier.getUpgradeSlots();
    }

    public BlockPos getBusPos() {
        return busPos;
    }

    /** {@inheritDoc} Moves stacks between the upgrade grid and the player inventory in either direction. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int upgradeSlots = getUpgradeSlotCount();

        if (index < upgradeSlots) {
            if (!moveItemStackTo(stack, upgradeSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, upgradeSlots, false)) {
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
        return player.level().getBlockEntity(busPos) instanceof TileUpgradeBus
                && player.distanceToSqr(busPos.getX() + 0.5, busPos.getY() + 0.5, busPos.getZ() + 0.5)
                        <= 64.0;
    }
}
