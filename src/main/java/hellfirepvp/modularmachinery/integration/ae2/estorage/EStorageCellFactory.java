/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import hellfirepvp.modularmachinery.common.estorage.EStorageCellSpec;
import hellfirepvp.modularmachinery.common.estorage.ItemEStorageCell;

/** Delayed boundary: the common registry must not verify an optional AE interface. */
public final class EStorageCellFactory {
    private EStorageCellFactory() { }
    public static ItemEStorageCell create(EStorageCellSpec spec) { return new EStorageWorkbenchCell(spec); }
    public static hellfirepvp.modularmachinery.common.estorage.EStorageMatrixSummary inspect(net.minecraft.world.item.ItemStack stack) {
        var spec = ((ItemEStorageCell) stack.getItem()).specification();
        var data = new EStorageCellInventory(spec, stack.hasTag() ? stack.getTag() : new net.minecraft.nbt.CompoundTag(), null);
        return new hellfirepvp.modularmachinery.common.estorage.EStorageMatrixSummary(spec, data.storedTypes(), data.usedBytes(), data.storedAmount());
    }
}
