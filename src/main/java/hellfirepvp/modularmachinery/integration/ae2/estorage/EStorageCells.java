/* SPDX-License-Identifier: GPL-3.0-only
 * NECO ES cells are restricted to their own hosts; native AE storage may inspect but not mount them.
 */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.api.storage.cells.ICellHandler;
import appeng.api.storage.cells.ISaveProvider;
import appeng.api.storage.cells.StorageCell;
import hellfirepvp.modularmachinery.common.estorage.ItemEStorageCell;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

public final class EStorageCells {
    private EStorageCells() { }
    public static void register(IEventBus bus) {
        bus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            StorageCells.addCellHandler(new MetadataHandler());
            for (var entry : hellfirepvp.modularmachinery.common.estorage.EStorageRegistration.CELLS.entrySet()) {
                var item = entry.getValue().get();
                appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.INVERTER_CARD, item, 1,
                        "storage.modularmachinery.estorage_matrix");
                if (entry.getKey().kind() == hellfirepvp.modularmachinery.common.estorage.EStorageCellSpec.Kind.ITEM)
                    appeng.api.upgrades.Upgrades.add(appeng.core.definitions.AEItems.FUZZY_CARD, item, 1,
                            "storage.modularmachinery.estorage_matrix");
            }
        }));
    }
    private static final class MetadataHandler implements ICellHandler {
        @Override public boolean isCell(ItemStack stack) { return stack.getItem() instanceof ItemEStorageCell; }
        @Override public StorageCell getCellInventory(ItemStack stack, ISaveProvider host) {
            if (!(stack.getItem() instanceof ItemEStorageCell cell) || host != null) return null;
            // Null-host access is used by AE's nested-cell checks. It must never expose writable
            // storage without an assembled ES host, nor mutate the caller's ItemStack during a check.
            EStorageCellInventory snapshot;
            try { snapshot = new EStorageCellInventory(cell.specification(), stack.hasTag() ? stack.getTag().copy() : new CompoundTag(), null); }
            catch (IllegalArgumentException invalid) { snapshot = null; }
            EStorageCellInventory data = snapshot;
            return new StorageCell() {
                @Override public void getAvailableStacks(KeyCounter out) { if (data != null) data.getAvailableStacks(out); }
                @Override public boolean canFitInsideCell() { return data != null && data.canFitInsideCell(); }
                @Override public CellState getStatus() { return data == null ? CellState.FULL : data.getStatus(); }
                @Override public double getIdleDrain() { return cell.specification().idleDrain(); }
                @Override public Component getDescription() { return stack.getHoverName(); }
                @Override public void persist() { }
            };
        }
    }
}
