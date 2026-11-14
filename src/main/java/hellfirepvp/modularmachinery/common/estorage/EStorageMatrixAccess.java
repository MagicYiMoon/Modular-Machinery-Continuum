/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.estorage;

import net.minecraft.world.item.ItemStack;

/** Optional AE boundary. Blocks and saved matrices must survive removing AE2. */
public interface EStorageMatrixAccess extends AutoCloseable {
    boolean isOpen();
    long storedAmount();
    int storedTypes();
    long usedBytes();
    void persist();
    @Override void close();
    static EStorageMatrixSummary inspect(ItemStack stack) {
        return stack.getItem() instanceof ItemEStorageCell cell && cell.specification().backendAvailable()
                ? hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageCellFactory.inspect(stack) : null;
    }

    static EStorageMatrixAccess open(ItemStack stack, Runnable save, java.util.function.BooleanSupplier allowed) {
        if (!(stack.getItem() instanceof ItemEStorageCell cell)
                || !cell.specification().backendAvailable()) return null;
        return new hellfirepvp.modularmachinery.integration.ae2.estorage.EStorageCellInventory(
                cell.specification(), stack.getOrCreateTag(), save::run, allowed);
    }
}
