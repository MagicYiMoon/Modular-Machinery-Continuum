/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.pneumatic;

import hellfirepvp.modularmachinery.common.menu.IntContainerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;

/** Four upgrade slots and live, full-width integer synchronization; no air mutation packets. */
public final class MenuPressureHatch extends AbstractContainerMenu {
    public static final int WIDTH = 216, HEIGHT = 180;
    private final BlockPos pos;
    private final TilePressureHatch owner;
    private final ContainerData data;
    private final IItemHandler upgradeInventory;

    public MenuPressureHatch(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, null, new ItemStackHandler(4), new SimpleContainerData(7));
    }
    private MenuPressureHatch(int id, Inventory inventory, BlockPos pos, TilePressureHatch owner, IItemHandler upgrades, ContainerData data) {
        super(PressureRegistration.MENU.get(), id);
        this.pos = pos.immutable(); this.owner = owner; this.data = data; this.upgradeInventory = upgrades;
        for (int i = 0; i < 4; i++) addSlot(new SlotItemHandler(upgrades, i, 164 + i % 2 * 18, 28 + i / 2 * 18) {
            // Forge's default temporarily removes a stack to probe its limit. Removing Volume
            // Upgrades would vent air merely by querying the slot, even on a simulated insertion.
            @Override public int getMaxStackSize(ItemStack stack) { return Math.min(stack.getMaxStackSize(), TilePressureHatch.upgradeLimit(stack)); }
            @Override public int getMaxStackSize() { return hasItem() ? getMaxStackSize(getItem()) : 64; }
            @Override public boolean mayPlace(ItemStack stack) { return TilePressureHatch.upgradeLimit(stack) > 0 && super.mayPlace(stack); }
            @Override public void setChanged() { super.setChanged(); if (owner != null) owner.upgradesChanged(); }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 28 + col * 18, 98 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 28 + col * 18, 156));
        addDataSlots(new IntContainerData(data));
    }
    public static MenuPressureHatch forTile(int id, Inventory inventory, TilePressureHatch tile) {
        return new MenuPressureHatch(id, inventory, tile.getBlockPos(), tile, tile.upgrades(), dataFor(tile));
    }
    public static ContainerData dataFor(TilePressureHatch tile) {
        return new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> tile.air().getAir();
                    case 1 -> tile.air().getVolume();
                    case 2 -> Float.floatToIntBits(tile.air().getDangerPressure());
                    case 3 -> Float.floatToIntBits(tile.air().getCriticalPressure());
                    case 4 -> tile.upgradeCount("security");
                    case 5 -> tile.upgradeCount("volume");
                    case 6 -> tile.heat().getTemperatureAsInt();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) { }
            @Override public int getCount() { return 7; }
        };
    }
    public int air() { return data.get(0); }
    public int volume() { return data.get(1); }
    public float pressure() { return volume() <= 0 ? 0 : (float) air() / volume(); }
    public float danger() { return Float.intBitsToFloat(data.get(2)); }
    public float critical() { return Float.intBitsToFloat(data.get(3)); }
    public boolean safety() { return data.get(4) > 0; }
    public int temperature() { return data.get(6); }
    @Override public boolean stillValid(Player player) {
        var tile = player.level().getBlockEntity(pos);
        return tile instanceof TilePressureHatch && (owner == null || tile == owner && owner.live())
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem(), before = stack.copy();
        if (index < 4) {
            if (!moveItemStackTo(stack, 4, 40, true)) return ItemStack.EMPTY;
        } else {
            // Vanilla's empty-slot shift path uses the untyped slot limit. Use the handler's
            // real insertion so a two-item stack cannot overfill the single Security Upgrade.
            for (int i = 0; i < 4 && !stack.isEmpty(); i++) {
                ItemStack remainder = upgradeInventory.insertItem(i, stack, false);
                stack.setCount(remainder.getCount());
            }
            if (stack.getCount() == before.getCount()
                    && (index < 31 ? !moveItemStackTo(stack, 31, 40, false) : !moveItemStackTo(stack, 4, 31, false))) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack);
        return before;
    }
}
