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
import hellfirepvp.modularmachinery.common.tiles.base.TileFluidTank;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/**
 * The server-side half of a fluid hatch GUI.
 *
 * <h2>Syncing a fluid through an int channel</h2>
 *
 * <p>{@link ContainerData} carries {@code int}s only, so the tank's fluid identity cannot travel as a
 * {@code FluidStack}. It is sent as the fluid's <b>registry id</b> instead — the same integer the
 * registry already uses for network and NBT references, and stable for the lifetime of a session, which
 * is all a display needs. The client resolves it back with {@code byId}.
 *
 * <p>The alternative would be a custom packet carrying the fluid, which means a second sync path that can
 * disagree with this one. Using the registry id keeps the fluid and its amount on <b>one</b> channel, so
 * they cannot desynchronise — a gauge showing the right amount of the wrong fluid is the kind of bug that
 * is easy to write and hard to notice.
 */
public class MenuFluidHatch extends AbstractContainerMenu {
    /**
     * Panel size, from the jar's GUI class. Asserted server-side by MachineSelfTest -- see §91: nine screens
     * carried numerical assertions against the jar and passed on a screen that was visibly broken, because
     * the panel size was treated as an established constant rather than a value to verify.
     */
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 166;

    public static final int DATA_AMOUNT = 0;
    public static final int DATA_CAPACITY = 1;
    /** Registry id of the tank's fluid, or {@link #FLUID_NONE} when empty. */
    public static final int DATA_FLUID_ID = 2;
    public static final int DATA_INPUT = 3;
    public static final int DATA_GAS = 4;
    public static final int DATA_SIZE = 5;

    /** Sentinel for "no fluid". Zero is a valid registry id, so zero cannot be used for absence. */
    public static final int FLUID_NONE = -1;

    private final BlockPos hatchPos;
    private final ContainerData data;

    public MenuFluidHatch(int windowId, Inventory playerInventory, BlockPos hatchPos) {
        this(windowId, playerInventory, hatchPos, new SimpleContainerData(DATA_SIZE));
    }

    public MenuFluidHatch(int windowId, Inventory playerInventory, BlockPos hatchPos,
                          ContainerData data) {
        super(RegistryMenus.FLUID_HATCH.get(), windowId);
        this.hatchPos = hatchPos;
        this.data = data;
        // A vanilla menu data packet transports one signed short per slot.  Amounts and registry ids can
        // exceed that range, so expose each logical int as two 16-bit words while keeping the logical
        // ContainerData field used by the screen unchanged.
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
    public static MenuFluidHatch forHatch(int windowId, Inventory playerInventory,
                                          TileFluidTank hatch) {
        return new MenuFluidHatch(windowId, playerInventory, hatch.getBlockPos(), liveData(hatch));
    }

    public static ContainerData dataFor(TileFluidTank hatch) {
        return liveData(hatch);
    }

    private static ContainerData liveData(TileFluidTank hatch) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                var tank = hatch.getTank();
                return switch (index) {
                    case DATA_AMOUNT -> hatch.hasGas() ? (int) Math.min(Integer.MAX_VALUE, hatch.getGasAmount()) : tank.getFluidAmount();
                    case DATA_CAPACITY -> tank.getCapacity();
                    case DATA_FLUID_ID -> {
                        if (hatch.hasGas()) yield FLUID_NONE;
                        Fluid fluid = tank.getFluid().getFluid();
                        // EMPTY means "nothing here", which must be distinguishable from a real fluid
                        // whose registry id happens to be 0 -- hence the sentinel rather than 0.
                        yield fluid == Fluids.EMPTY ? FLUID_NONE : BuiltInRegistries.FLUID.getId(fluid);
                    }
                    case DATA_INPUT -> hatch.getIOType()
                            == hellfirepvp.modularmachinery.common.machine.IOType.INPUT ? 1 : 0;
                    case DATA_GAS -> hatch.hasGas() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Server-authoritative.
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

    public int getAmount() {
        return data.get(DATA_AMOUNT);
    }

    public int getCapacity() {
        return data.get(DATA_CAPACITY);
    }

    public boolean isInput() {
        return data.get(DATA_INPUT) != 0;
    }

    /** @return the tank's fluid, or {@code Fluids.EMPTY} when it holds none */
    public Fluid getFluid() {
        int id = data.get(DATA_FLUID_ID);
        if (id == FLUID_NONE) {
            return Fluids.EMPTY;
        }
        Fluid fluid = BuiltInRegistries.FLUID.byId(id);
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    public boolean isGas() { return data.get(DATA_GAS) != 0; }

    /** @return fill fraction in {@code [0,1]}, or 0 for a zero-capacity tank */
    public float getFillFraction() {
        int capacity = getCapacity();
        if (capacity <= 0) {
            return 0F;
        }
        return Math.min(1F, (float) getAmount() / capacity);
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
        return player.level().getBlockEntity(hatchPos) instanceof TileFluidTank
                && player.distanceToSqr(hatchPos.getX() + 0.5, hatchPos.getY() + 0.5,
                        hatchPos.getZ() + 0.5) <= 64.0;
    }
}
