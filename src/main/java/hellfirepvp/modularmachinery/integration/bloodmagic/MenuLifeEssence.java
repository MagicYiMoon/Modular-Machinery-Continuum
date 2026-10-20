/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.bloodmagic;

import hellfirepvp.modularmachinery.common.menu.IntContainerData;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.*;
import wayoftime.bloodmagic.common.item.IBloodOrb;

public final class MenuLifeEssence extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerData data;
    private final IItemHandler orbs;
    public MenuLifeEssence(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, pos, new ItemStackHandler(1), new SimpleContainerData(3));
    }
    private MenuLifeEssence(int id, Inventory inventory, BlockPos pos, IItemHandler orbs, ContainerData data) {
        super(BloodMagicRegistration.MENU.get(), id); this.pos = pos; this.data = data; this.orbs = orbs;
        addSlot(new SlotItemHandler(orbs, 0, 26, 35) {
            @Override public boolean mayPlace(ItemStack stack) { return stack.getItem() instanceof IBloodOrb; }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 84 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        addDataSlots(new IntContainerData(data));
    }
    public static MenuLifeEssence forTile(int id, Inventory inventory, TileLifeEssenceProvider tile) {
        return new MenuLifeEssence(id, inventory, tile.getBlockPos(), tile.orbInventory(), new ContainerData() {
            public int getCount() { return 3; }
            public void set(int index, int value) {}
            public int get(int index) { return switch (index) { case 0 -> tile.amount(); case 1 -> tile.capacity(); default -> tile.network() == null ? 0 : 1; }; }
        });
    }
    public int amount() { return data.get(0); }
    public int capacity() { return data.get(1); }
    public boolean bound() { return data.get(2) != 0; }
    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof TileLifeEssenceProvider tile
                && (player.level().isClientSide() || tile.orbInventory() == orbs)
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); var stack = slot.getItem(); if (stack.isEmpty()) return ItemStack.EMPTY;
        var old = stack.copy();
        if (!(index == 0 ? moveItemStackTo(stack, 1, 37, true) : moveItemStackTo(stack, 0, 1, false))) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return old;
    }
}
