/* SPDX-License-Identifier: GPL-3.0-only
 * NECO ContainerEStorageController / PktEStorageGUIData, KasumiNova and contributors, b37fbb53.
 * Bounded statistics pages instead of broadcasting complete matrix data.
 */
package hellfirepvp.modularmachinery.common.menu;

import hellfirepvp.modularmachinery.common.estorage.*;
import hellfirepvp.modularmachinery.common.registry.RegistryMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import java.util.*;

public final class MenuEStorageController extends AbstractContainerMenu {
    public static final int WIDTH = 256, HEIGHT = 207, PAGE_SIZE = 6, ROW_BASE = 21, ROW_STRIDE = 4;
    public static final int FLAGS = 0, TIER = 1, DRIVES = 2, MATRICES = 3, PAGE = 4, PAGES = 5,
            ENERGY = 6, ENERGY_MAX = 7, DRAIN = 8, USED = 9, CAPACITY = 10,
            ITEM_TYPES = 11, ITEM_TYPES_MAX = 12, FLUID_TYPES = 13, FLUID_TYPES_MAX = 14,
            ITEM_BYTES = 15, FLUID_BYTES = 16, ROWS = 17, GAS_TYPES = 18, GAS_TYPES_MAX = 19, GAS_BYTES = 20;
    private final long[] values = new long[ROW_BASE + PAGE_SIZE * ROW_STRIDE];
    private final BlockPos pos;
    private final TileEStorageController owner;
    private int page;
    private long nextUpdate;
    public MenuEStorageController(int id, Inventory inventory, BlockPos pos) { this(id, inventory, pos, null); }
    private MenuEStorageController(int id, Inventory inventory, BlockPos pos, TileEStorageController owner) {
        super(RegistryMenus.ESTORAGE_CONTROLLER.get(), id); this.pos = pos.immutable(); this.owner = owner;
        addDataSlots(new ContainerData() {
            public int getCount() { return values.length * 4; }
            public int get(int index) { return (int) ((values[index / 4] >>> (index % 4 * 16)) & 0xFFFF); }
            public void set(int index, int value) {
                if (MenuEStorageController.this.owner != null || index < 0 || index >= getCount()) return;
                int shift = index % 4 * 16; int slot = index / 4;
                values[slot] = (values[slot] & ~(0xFFFFL << shift)) | ((value & 0xFFFFL) << shift);
            }
        });
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inventory, 9 + row * 9 + col, 8 + col * 18, 125 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 183));
        refresh();
    }
    public static MenuEStorageController forController(int id, Inventory inventory, TileEStorageController owner) {
        return new MenuEStorageController(id, inventory, owner.getBlockPos(), owner);
    }
    public long value(int index) { return index < 0 || index >= values.length ? 0 : values[index]; }
    public double energyValue(int index) {
        double result = Double.longBitsToDouble(value(index)); return Double.isFinite(result) ? Math.max(0, result) : 0;
    }
    public EStorageMatrixSummary row(int index) {
        if (index < 0 || index >= PAGE_SIZE || index >= value(ROWS)) return null;
        int base = ROW_BASE + index * ROW_STRIDE;
        int kind = (int) value(base), tier = (int) value(base + 1);
        if (kind < 0 || kind >= EStorageCellSpec.Kind.values().length || tier < 0 || tier >= EStorageCellSpec.Tier.values().length) return null;
        return new EStorageMatrixSummary(new EStorageCellSpec(EStorageCellSpec.Kind.values()[kind], EStorageCellSpec.Tier.values()[tier]),
                (int) value(base + 2), value(base + 3), 0);
    }
    private void refresh() {
        if (owner == null) return;
        var rows = new ArrayList<EStorageMatrixSummary>();
        if (owner.isAssembled() && !owner.isStructureCheckRequested()) for (var drive : owner.drives()) {
            if (drive.isTierSupported()) { var data = drive.summary(); if (data != null) rows.add(data); }
        }
        rows.sort(Comparator.comparingLong(EStorageMatrixSummary::usedBytes).reversed()
                .thenComparing(Comparator.comparingInt(EStorageMatrixSummary::usedTypes).reversed())
                .thenComparing(Comparator.comparingInt((EStorageMatrixSummary s) -> s.specification().tier().ordinal()).reversed()));
        Arrays.fill(values, 0);
        values[FLAGS] = (owner.isAssembled() ? 1 : 0) | (owner.isOperational() ? 2 : 0)
                | (owner.channel() != null && owner.channel().isNetworkActive() ? 4 : 0);
        values[TIER] = owner.tier(); values[DRIVES] = owner.driveCount(); values[MATRICES] = rows.size();
        values[PAGES] = Math.max(1, (rows.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        page = Math.max(0, Math.min(page, (int) values[PAGES] - 1)); values[PAGE] = page;
        values[ENERGY] = Double.doubleToRawLongBits(owner.storedEnergy());
        values[ENERGY_MAX] = Double.doubleToRawLongBits(owner.energyCapacity());
        values[DRAIN] = Double.doubleToRawLongBits(owner.storageIdleDrain());
        for (var row : rows) {
            var spec = row.specification(); values[USED] += row.usedBytes(); values[CAPACITY] += spec.totalBytes();
            int types = switch (spec.kind()) { case ITEM -> ITEM_TYPES; case FLUID -> FLUID_TYPES; case GAS -> GAS_TYPES; };
            int bytes = switch (spec.kind()) { case ITEM -> ITEM_BYTES; case FLUID -> FLUID_BYTES; case GAS -> GAS_BYTES; };
            values[types] += row.usedTypes(); values[types + 1] += spec.maxTypes(); values[bytes] += row.usedBytes();
        }
        int start = page * PAGE_SIZE;
        values[ROWS] = Math.max(0, Math.min(PAGE_SIZE, rows.size() - start));
        for (int i = 0; i < values[ROWS]; i++) {
            var row = rows.get(start + i); int base = ROW_BASE + i * ROW_STRIDE;
            values[base] = row.specification().kind().ordinal(); values[base + 1] = row.specification().tier().ordinal();
            values[base + 2] = row.usedTypes(); values[base + 3] = row.usedBytes();
        }
    }
    @Override public void broadcastChanges() {
        if (owner != null && owner.getLevel().getGameTime() >= nextUpdate) {
            nextUpdate = owner.getLevel().getGameTime() + 20; refresh();
        }
        super.broadcastChanges();
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (owner == null || !stillValid(player) || (id != 0 && id != 1)) return false;
        page += id == 0 ? -1 : 1; refresh(); broadcastChanges(); return true;
    }
    @Override public boolean stillValid(Player player) {
        return player.level().hasChunkAt(pos) && player.level().getBlockEntity(pos) instanceof TileEStorageController controller
                && (owner == null || controller == owner) && !controller.isRemoved()
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        var slot = slots.get(index); if (!slot.hasItem()) return ItemStack.EMPTY;
        var stack = slot.getItem(); var original = stack.copy();
        if (!moveItemStackTo(stack, index < 27 ? 27 : 0, index < 27 ? 36 : 27, false)) return ItemStack.EMPTY;
        if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        slot.onTake(player, stack); return original;
    }
}
