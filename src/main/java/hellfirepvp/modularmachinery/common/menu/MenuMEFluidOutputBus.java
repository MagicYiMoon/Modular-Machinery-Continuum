/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.registry.RegistryMEMenus;
import hellfirepvp.modularmachinery.common.tiles.me.MEFluidBus;
import hellfirepvp.modularmachinery.common.tiles.me.MEFluidOutputBus;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

/** Read-only 1.20.1 menu for the source mod's ME fluid output bus. */
public class MenuMEFluidOutputBus extends AbstractContainerMenu {
    public static final int PANEL_WIDTH = 176;
    public static final int PANEL_HEIGHT = 204;
    public static final int TANK_SLOTS = MEFluidBus.TANK_SLOT_AMOUNT;
    public static final int UPGRADE_SLOTS = 5;
    public static final int UPGRADE_X = 187;
    public static final int UPGRADE_Y = 8;
    public static final int DATA_SIZE = TANK_SLOTS * 2;
    public static final int FLUID_NONE = -1;
    public static final int PLAYER_INV_X = 8;
    // ContainerMEFluidOutputBus binds from 195 - 83, matching the shipped texture's wells.
    public static final int PLAYER_INV_Y = 112;
    public static final int PLAYER_HOTBAR_Y = 170;

    private final BlockPos busPos;
    private final ContainerData data;
    private final IUpgradeInventory upgrades;

    public MenuMEFluidOutputBus(int windowId, Inventory inventory, BlockPos busPos) {
        this(windowId, inventory, busPos, new SimpleContainerData(DATA_SIZE), clientUpgrades());
    }

    public MenuMEFluidOutputBus(int windowId, Inventory inventory, BlockPos busPos, ContainerData data) {
        this(windowId, inventory, busPos, data, clientUpgrades());
    }

    private MenuMEFluidOutputBus(int windowId, Inventory inventory, BlockPos busPos, ContainerData data,
                                 IUpgradeInventory upgrades) {
        super(RegistryMEMenus.ME_FLUID_OUTPUT_BUS.get(), windowId);
        this.busPos = busPos;
        this.data = data;
        this.upgrades = upgrades;
        for (int i = 0; i < TANK_SLOTS; i++) {
            addSlot(new FluidDisplaySlot(new EmptyContainer(), i, 8 + i * 18, 26));
        }
        addPlayerInventory(inventory);
        IItemHandler upgradeHandler = upgrades.toItemHandler();
        for (int i = 0; i < UPGRADE_SLOTS; i++) {
            addSlot(new MenuMEFluidInputBus.UpgradeSlot(upgradeHandler, upgrades, i,
                    UPGRADE_X, UPGRADE_Y + 18 * i));
        }
        addDataSlots(new IntContainerData(data));
    }

    public static MenuMEFluidOutputBus forBus(int windowId, Inventory inventory, MEFluidOutputBus bus) {
        return new MenuMEFluidOutputBus(windowId, inventory, bus.getBlockPos(), dataFor(bus), bus.getUpgrades());
    }

    private static IUpgradeInventory clientUpgrades() {
        net.minecraft.world.item.Item machine = BuiltInRegistries.ITEM
                .get(new net.minecraft.resources.ResourceLocation("modularmachinery", "blockmefluidoutputbus"));
        return UpgradeInventories.forMachine(machine, UPGRADE_SLOTS, () -> { });
    }

    private int upgradeStartIndex() {
        return TANK_SLOTS + 36;
    }

    public int capacityForDisplay() {
        int installed = upgrades.getInstalledUpgrades(AEItems.CAPACITY_CARD);
        long capacity = (long) MEFluidBus.TANK_DEFAULT_CAPACITY * (1L << Math.min(installed * 2, 30));
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    public static ContainerData dataFor(MEFluidOutputBus bus) {
        return new ContainerData() {
            @Override public int get(int index) {
                int slot = index / 2;
                if (slot < 0 || slot >= TANK_SLOTS) return 0;
                FluidStack stack = bus.getFluidInSlot(slot);
                if (index % 2 == 0) return stack.isEmpty() ? FLUID_NONE : BuiltInRegistries.FLUID.getId(stack.getFluid());
                return stack.getAmount();
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return DATA_SIZE; }
        };
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        PLAYER_INV_X + column * 18, PLAYER_INV_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, PLAYER_INV_X + column * 18, PLAYER_HOTBAR_Y));
        }
    }

    public BlockPos getBusPos() { return busPos; }

    public FluidStack fluidFor(int slot) {
        if (slot < 0 || slot >= TANK_SLOTS) return FluidStack.EMPTY;
        int id = data.get(slot * 2);
        if (id == FLUID_NONE) return FluidStack.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.byId(id);
        if (fluid == null || fluid == Fluids.EMPTY) return FluidStack.EMPTY;
        return new FluidStack(fluid, Math.max(0, data.get(slot * 2 + 1)));
    }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < TANK_SLOTS || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int playerMainStart = TANK_SLOTS;
        int playerHotbarStart = TANK_SLOTS + 27;
        int upgradeStart = upgradeStartIndex();
        if (index >= upgradeStart) {
            if (!moveItemStackTo(stack, playerMainStart, upgradeStart, false)) return ItemStack.EMPTY;
        } else if (index < playerHotbarStart) {
            if (!moveItemStackTo(stack, playerHotbarStart, upgradeStart, false)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, playerMainStart, playerHotbarStart, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, stack);
        return original;
    }

    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(busPos) instanceof MEFluidOutputBus
                && player.distanceToSqr(busPos.getX() + .5, busPos.getY() + .5, busPos.getZ() + .5) <= 64.0;
    }

    public static class FluidDisplaySlot extends Slot {
        public FluidDisplaySlot(Container container, int index, int x, int y) { super(container, index, x, y); }
        @Override public boolean mayPlace(ItemStack stack) { return false; }
        @Override public boolean mayPickup(Player player) { return false; }
        @Override public int getMaxStackSize() { return 0; }
    }

    private static final class EmptyContainer implements Container {
        @Override public int getContainerSize() { return TANK_SLOTS; }
        @Override public boolean isEmpty() { return true; }
        @Override public ItemStack getItem(int slot) { return ItemStack.EMPTY; }
        @Override public ItemStack removeItem(int slot, int amount) { return ItemStack.EMPTY; }
        @Override public ItemStack removeItemNoUpdate(int slot) { return ItemStack.EMPTY; }
        @Override public void setItem(int slot, ItemStack stack) { }
        @Override public void setChanged() { }
        @Override public boolean stillValid(Player player) { return true; }
        @Override public void clearContent() { }
    }
}
