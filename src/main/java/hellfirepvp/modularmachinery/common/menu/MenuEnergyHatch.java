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
import hellfirepvp.modularmachinery.common.tiles.base.TileEnergyHatch;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/**
 * The server-side half of an energy hatch GUI.
 *
 * <p>View-only, and deliberately so rather than for brevity: an energy hatch's contents are not
 * something a player manipulates by hand. There is no item to insert and no bucket to fill — the buffer
 * is filled by whatever cable the player runs to it and drained by the machine. Adding slots would
 * invent an interaction the mod does not have, and would need a rule for what happens when a player
 * pulls energy out mid-craft.
 *
 * <p>So this menu carries <b>only</b> synced data: stored and capacity, which the screen turns into a
 * gauge and a pair of numbers.
 */
public class MenuEnergyHatch extends AbstractContainerMenu {
    /**
     * Panel size, from the jar's GUI class. Asserted server-side by MachineSelfTest -- see §91: nine screens
     * carried numerical assertions against the jar and passed on a screen that was visibly broken, because
     * the panel size was treated as an established constant rather than a value to verify.
     */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;

    public static final int DATA_STORED = 0;
    public static final int DATA_CAPACITY = 1;
    public static final int DATA_INPUT = 2;
    public static final int DATA_SIZE = 3;

    private final BlockPos hatchPos;
    private final ContainerData data;

    public MenuEnergyHatch(int windowId, Inventory playerInventory, BlockPos hatchPos) {
        this(windowId, playerInventory, hatchPos, new SimpleContainerData(DATA_SIZE));
    }

    public MenuEnergyHatch(int windowId, Inventory playerInventory, BlockPos hatchPos,
                           ContainerData data) {
        super(RegistryMenus.ENERGY_HATCH.get(), windowId);
        this.hatchPos = hatchPos;
        this.data = data;
        // Vanilla sends signed shorts; split stored/capacity into words to retain up to Integer.MAX_VALUE.
        addDataSlots(new IntContainerData(data));
        addPlayerInventory(playerInventory);
    }


    /**
     * Adds the player inventory, matching upstream's {@code ContainerBase}.
     *
     * <p><b>These were missing entirely.</b> Upstream's {@code ContainerEnergyHatch} and
     * {@code ContainerFluidHatch} both call {@code super(owner, opening)}, and that base adds the player's
     * 3x9 inventory plus hotbar. Without them a player cannot move anything through the GUI at all — which
     * matters most for the fluid hatch, where putting a bucket in is the normal way to fill or drain it.
     * A hatch GUI with no player inventory is not a cosmetic difference; it is a screen you cannot use.
     */
    private void addPlayerInventory(Inventory playerInventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new net.minecraft.world.inventory.Slot(playerInventory,
                        column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new net.minecraft.world.inventory.Slot(playerInventory, column,
                    8 + column * 18, 142));
        }
    }
    public static MenuEnergyHatch forHatch(int windowId, Inventory playerInventory,
                                           TileEnergyHatch hatch) {
        return new MenuEnergyHatch(windowId, playerInventory, hatch.getBlockPos(), liveData(hatch));
    }

    public static ContainerData dataFor(TileEnergyHatch hatch) {
        return liveData(hatch);
    }

    /**
     * Reads the hatch on every access rather than snapshotting.
     *
     * <p>{@code AbstractContainerMenu} sends this data on a tick, so a snapshot taken when the window
     * opened would freeze the gauge at whatever the buffer held at that instant — and a frozen energy bar
     * is indistinguishable from a hatch that has stopped being charged.
     */
    private static ContainerData liveData(TileEnergyHatch hatch) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_STORED -> hatch.getStoredEnergy();
                    case DATA_CAPACITY -> hatch.getCapacity();
                    case DATA_INPUT -> hatch.getIOType() == hellfirepvp.modularmachinery.common.machine.IOType.INPUT ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Server-authoritative: a client write must not change what the hatch holds.
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    public BlockPos getHatchPos() {
        return hatchPos;
    }

    public int getStored() {
        return data.get(DATA_STORED);
    }

    public int getCapacity() {
        return data.get(DATA_CAPACITY);
    }

    public boolean isInput() {
        return data.get(DATA_INPUT) != 0;
    }

    /** @return fill fraction in {@code [0,1]}, or 0 for a zero-capacity hatch */
    public float getFillFraction() {
        int capacity = getCapacity();
        if (capacity <= 0) {
            return 0F;
        }
        return Math.min(1F, (float) getStored() / capacity);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // The hatch holds no slots of its own, so every slot here is a player slot and shift-clicking
        // simply moves within the player inventory -- which is what upstream's ContainerBase does when the
        // owner contributes no slots.
        net.minecraft.world.inventory.Slot slot = slots.get(index);
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
        return player.level().getBlockEntity(hatchPos) instanceof TileEnergyHatch
                && player.distanceToSqr(hatchPos.getX() + 0.5, hatchPos.getY() + 0.5,
                        hatchPos.getZ() + 0.5) <= 64.0;
    }
}
