/* SPDX-License-Identifier: GPL-3.0-only; adapted from NECO ContainerEFabricatorPatternBus. */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.efabricator.*;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;

public final class MenuEFabricatorPatternBus extends AbstractContainerMenu {
    public static final int WIDTH = 230, HEIGHT = 232, PATTERNS = 72;
    private final BlockPos pos;
    private final TileEFabricatorPatternBus owner;
    private final SimpleContainerData status = new SimpleContainerData(1);
    public MenuEFabricatorPatternBus(int id, Inventory inv, BlockPos pos) { this(id, inv, pos, null); }
    private MenuEFabricatorPatternBus(int id, Inventory inv, BlockPos pos, TileEFabricatorPatternBus owner) {
        super(RegistryMenus.EFABRICATOR_PATTERN_BUS.get(), id); this.pos = pos.immutable(); this.owner = owner;
        status.set(0, owner == null || owner.readable() ? 1 : 0); addDataSlots(status);
        Container patterns = owner == null ? new SimpleContainer(PATTERNS) : owner.menuInventory();
        for (int row = 0; row < 6; row++) for (int col = 0; col < 12; col++) addSlot(new Slot(patterns, row * 12 + col, 8 + col * 18, 28 + row * 18) {
            @Override public boolean mayPlace(ItemStack stack) { return patterns.canPlaceItem(getContainerSlot(), stack); }
            @Override public int getMaxStackSize() { return 1; }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++) addSlot(new Slot(inv, 9 + row * 9 + col, 35 + col * 18, 150 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 35 + col * 18, 208));
    }
    public static MenuEFabricatorPatternBus forBus(int id, Inventory inv, TileEFabricatorPatternBus owner) { return new MenuEFabricatorPatternBus(id, inv, owner.getBlockPos(), owner); }
    public boolean readable() { return status.get(0) == 1; }
    @Override public void broadcastChanges() { if (owner != null) status.set(0, owner.readable() ? 1 : 0); super.broadcastChanges(); }
    @Override public boolean stillValid(Player player) {
        return player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) instanceof TileEFabricatorPatternBus bus
                && !bus.isRemoved() && (owner == null || owner == bus) && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public void clicked(int index, int button, ClickType type, Player player) { if (stillValid(player)) super.clicked(index, button, type, player); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player) || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var copy = stack.copy();
        if (!moveItemStackTo(stack, index < PATTERNS ? PATTERNS : 0, index < PATTERNS ? slots.size() : PATTERNS, index < PATTERNS)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return copy;
    }
}
