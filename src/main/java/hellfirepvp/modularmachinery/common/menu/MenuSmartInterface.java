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
import hellfirepvp.modularmachinery.common.tiles.TileSmartInterface;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The smart interface menu.
 *
 * <h2>It carries no inventory slots of its own — by definition, not by omission</h2>
 *
 * <p>The jar's {@code ContainerSmartInterface} constructor is exactly:
 *
 * <pre>
 *   super(owner, opening);
 * </pre>
 *
 * <p>and nothing else. {@code ContainerBase}'s constructor adds the player's 3x9 inventory and hotbar, so
 * that is the whole of this screen's slot content. Upstream did not forget to add something; the smart
 * interface exposes no item inventory, so there is nothing to place.
 *
 * <p>This is worth stating because it is the opposite conclusion to every other container in this mod, and
 * the two are easy to confuse: {@code ContainerUpgradeBus} and {@code ContainerItemBus} earn their slots,
 * {@code ContainerEnergyHatch} and {@code ContainerFluidHatch} do not. Getting that distinction wrong either
 * way produces a screen that does not match the mod — one with a phantom grid, or one missing the player's.
 */
public class MenuSmartInterface extends AbstractContainerMenu {

    /** Panel size. Upstream's {@code setWidthHeight()} is empty, so the vanilla default applies. */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;

    private final BlockPos interfacePos;

    /** Client-side constructor, invoked from the menu type's factory. */
    public MenuSmartInterface(int windowId, Inventory playerInventory, BlockPos interfacePos) {
        super(RegistryMenus.SMART_INTERFACE.get(), windowId);
        this.interfacePos = interfacePos;
        addPlayerInventory(playerInventory);
    }

    public static MenuSmartInterface forInterface(int windowId, Inventory playerInventory,
                                                  TileSmartInterface smartInterface) {
        return new MenuSmartInterface(windowId, playerInventory, smartInterface.getBlockPos());
    }

    /**
     * Adds the player inventory, which is this screen's <b>entire</b> slot content.
     *
     * <p>Coordinates are {@code ContainerBase}'s: 3x9 at (8,84), hotbar at (8,142).
     */
    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 142));
        }
    }

    public BlockPos getInterfacePos() {
        return interfacePos;
    }

    /** {@inheritDoc} Every slot is a player slot, so shift-click only swaps inventory and hotbar. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < 27) {
            if (!moveItemStackTo(stack, 27, 36, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, 27, false)) {
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
        return player.level().getBlockEntity(interfacePos) instanceof TileSmartInterface
                && player.distanceToSqr(interfacePos.getX() + 0.5, interfacePos.getY() + 0.5,
                        interfacePos.getZ() + 0.5) <= 64.0;
    }
}
