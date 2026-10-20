/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.appmek;

import appeng.api.upgrades.*;
import hellfirepvp.modularmachinery.common.menu.IntContainerData;
import mekanism.api.MekanismAPI;
import mekanism.api.chemical.gas.GasStack;
import mekanism.common.capabilities.Capabilities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.SlotItemHandler;

/** Vanilla ghost-slot clicks configure gas identity; no gas or container item is transferred. */
public final class MenuMEGasBus extends AbstractContainerMenu {
    private static final int FIELDS = 6, SIZE = MEGasBus.SLOTS * FIELDS + 1;
    private final BlockPos pos;
    private final boolean input;
    private final ContainerData data;
    public MenuMEGasBus(int id, Inventory inventory, BlockPos pos, boolean input) {
        this(id, inventory, pos, input, new SimpleContainerData(SIZE),
                UpgradeInventories.forMachine(inventory.player.level().getBlockState(pos).getBlock().asItem(), 5, () -> {}));
    }
    private MenuMEGasBus(int id, Inventory inventory, BlockPos pos, boolean input, ContainerData data, IUpgradeInventory upgrades) {
        super(GasBusRegistration.MENU.get(), id); this.pos = pos; this.input = input; this.data = data;
        for (int i = 0; i < 9; i++) addSlot(new Slot(new SimpleContainer(9), i, 8 + 18 * i, input ? 35 : -1000) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, panelHeight() - 82 + row * 18));
        for (int i = 0; i < 9; i++) addSlot(new Slot(inventory, i, 8 + i * 18, panelHeight() - 24));
        for (int i = 0; i < 5; i++) addSlot(new SlotItemHandler(upgrades.toItemHandler(), i, 187, 8 + 18 * i) {
            @Override public int getMaxStackSize() { return 1; }
        });
        addDataSlots(new IntContainerData(data));
    }
    public static MenuMEGasBus forBus(int id, Inventory inventory, MEGasBus bus) {
        return new MenuMEGasBus(id, inventory, bus.getBlockPos(), bus.isInput(), dataFor(bus), bus.getUpgrades());
    }
    public static ContainerData dataFor(MEGasBus bus) {
        return new ContainerData() {
            public int getCount() { return SIZE; }
            public void set(int index, int value) {}
            public int get(int index) {
                if (index == SIZE - 1) return bus.getMainNode().isActive() ? 1 : 0;
                int slot = index / FIELDS; var gas = bus.stored(slot); var config = bus.configured(slot);
                return switch (index % FIELDS) {
                    case 0 -> config.isEmpty() ? -1 : GasIds.TYPES.indexOf(config.getType());
                    case 1 -> gas.isEmpty() ? -1 : GasIds.TYPES.indexOf(gas.getType());
                    case 2 -> (int) gas.getAmount();
                    case 3 -> (int) (gas.getAmount() >>> 32);
                    case 4 -> bus.capacity();
                    default -> 0;
                };
            }
        };
    }
    private static final class GasIds {
        static final java.util.List<mekanism.api.chemical.gas.Gas> TYPES = MekanismAPI.gasRegistry().getKeys().stream()
                .sorted(java.util.Comparator.comparing(Object::toString)).map(key -> MekanismAPI.gasRegistry().getValue(key)).toList();
    }
    public boolean isInput() { return input; }
    public int panelHeight() { return input ? 231 : 204; }
    public int tankY() { return input ? 53 : 26; }
    public boolean online() { return data.get(SIZE - 1) != 0; }
    public GasStack gas(int slot, boolean configured) {
        int id = data.get(slot * FIELDS + (configured ? 0 : 1));
        if (id < 0) return GasStack.EMPTY;
        var gas = id < GasIds.TYPES.size() ? GasIds.TYPES.get(id) : null;
        long amount = configured ? 1L : (Integer.toUnsignedLong(data.get(slot * FIELDS + 2)) | ((long) data.get(slot * FIELDS + 3) << 32));
        return gas == null || gas.isEmptyType() || amount <= 0 ? GasStack.EMPTY : new GasStack(gas, amount);
    }
    public long capacity(int slot) { return Math.max(0, data.get(slot * FIELDS + 4)); }
    @Override public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof MEGasBus bus && bus.isInput() == input
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public void clicked(int slot, int button, ClickType kind, Player player) {
        if (slot >= 0 && slot < 9) {
            if (!player.level().isClientSide() && stillValid(player) && input && kind == ClickType.PICKUP
                    && player.level().getBlockEntity(pos) instanceof MEGasBus bus) {
                if (button == 1) bus.configure(slot, GasStack.EMPTY);
                else if (button == 0) {
                    var carried = getCarried().getCapability(Capabilities.GAS_HANDLER).orElse(null);
                    if (carried != null) for (int tank = 0; tank < carried.getTanks(); tank++) {
                        var gas = carried.getChemicalInTank(tank);
                        if (!gas.isEmpty()) { bus.configure(slot, gas); break; }
                    }
                }
                broadcastChanges();
            }
            return;
        }
        super.clicked(slot, button, kind, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int slot) {
        if (slot < 9 || slot >= slots.size()) return ItemStack.EMPTY;
        var source = slots.get(slot); var stack = source.getItem(); if (stack.isEmpty()) return ItemStack.EMPTY;
        var old = stack.copy();
        boolean moved = slot >= 45 ? moveItemStackTo(stack, 9, 45, true) : moveItemStackTo(stack, 45, 50, false);
        if (!moved) return ItemStack.EMPTY;
        if (stack.isEmpty()) source.set(ItemStack.EMPTY); else source.setChanged();
        source.onTake(player, stack); return old;
    }
}
