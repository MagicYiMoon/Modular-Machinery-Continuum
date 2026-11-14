/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.*;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.ChestBlockEntity;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import hellfirepvp.modularmachinery.common.estorage.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;

/** Real AE slots and storage handlers, only reached with AE installed. */
public final class EStorageHostNativeChecks {
    private static final IActionSource SOURCE = IActionSource.empty();
    private EStorageHostNativeChecks() { }
    private static AEKey key(EStorageCellSpec spec) {
        if (spec.kind() == EStorageCellSpec.Kind.GAS) return hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasChecks.key(0);
        return spec.kind() == EStorageCellSpec.Kind.ITEM ? AEItemKey.of(Items.DIAMOND) : AEFluidKey.of(Fluids.WATER);
    }
    private static long amount(EStorageCellSpec spec) { return spec.kind() == EStorageCellSpec.Kind.ITEM ? 12345 : 1001; }
    public static void seed(EStorageMatrixAccess access, EStorageCellSpec spec) {
        require(((EStorageCellInventory) access).insert(key(spec), amount(spec), Actionable.MODULATE, SOURCE) == amount(spec), "host seed");
    }
    public static void assertRevoked(EStorageMatrixAccess access, EStorageCellSpec spec) {
        var cell = (EStorageCellInventory) access;
        require(!cell.isOpen() && cell.insert(key(spec), 1, Actionable.MODULATE, SOURCE) == 0
                && cell.extract(key(spec), 1, Actionable.MODULATE, SOURCE) == 0 && cell.getAvailableStacks().isEmpty(), "stale ES reference remained writable/advertised");
    }
    public static void checkCount(EStorageMatrixAccess access, EStorageCellSpec spec) {
        require(access != null && access.storedAmount() == amount(spec) && access.storedTypes() == 1
                && ((EStorageCellInventory) access).extract(key(spec), Long.MAX_VALUE, Actionable.SIMULATE, SOURCE) == amount(spec), "host save/reload lost exact contents");
    }
    public static void checkNativeCells(ServerLevel level, BlockPos pos) {
        BlockPos chestPos = pos.east(2);
        require(level.getBlockState(pos).isAir() && level.getBlockState(chestPos).isAir(), "native ES fixture not empty");
        try {
            level.setBlockAndUpdate(pos, AEBlocks.DRIVE.block().defaultBlockState());
            level.setBlockAndUpdate(chestPos, AEBlocks.CHEST.block().defaultBlockState());
            var drive = (DriveBlockEntity) level.getBlockEntity(pos);
            var chest = (ChestBlockEntity) level.getBlockEntity(chestPos);
            for (var entry : EStorageRegistration.CELLS.entrySet()) {
                if (!entry.getKey().backendAvailable()) continue;
                ItemStack matrix = new ItemStack(entry.getValue().get());
                var writable = EStorageMatrixAccess.open(matrix, () -> { }, () -> true);
                seed(writable, entry.getKey()); writable.close();
                require(!StorageCells.isCellHandled(matrix), "ES accepted as native drive cell");
                require(StorageCells.getCellInventory(matrix, () -> { }) == null, "non-ES host mounted matrix");
                var metadata = StorageCells.getCellInventory(matrix, null);
                CompoundTag before = matrix.getTag().copy();
                require(metadata != null && !metadata.canFitInsideCell()
                        && metadata.insert(key(entry.getKey()), 1, Actionable.MODULATE, SOURCE) == 0
                        && metadata.extract(key(entry.getKey()), 1, Actionable.MODULATE, SOURCE) == 0, "metadata access writable or nestable");
                metadata.persist(); require(matrix.getTag().equals(before), "metadata changed original NBT");
                for (var inv : java.util.List.of(drive.getInternalInventory(), chest.getInternalInventory())) {
                    for (int slot = 0; slot < inv.size(); slot++) {
                        require(inv.insertItem(slot, matrix.copy(), false).getCount() == 1 && inv.getStackInSlot(slot).isEmpty(), "native ME slot accepted ES matrix");
                    }
                }
                var basic = StorageCells.getCellInventory(AEItems.ITEM_CELL_1K.stack(), null);
                require(basic != null && basic.insert(AEItemKey.of(matrix), 1, Actionable.MODULATE, SOURCE) == 0, "native cell nested filled ES matrix");
                var es = new EStorageCellInventory(new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.A), new CompoundTag(), null);
                require(es.insert(AEItemKey.of(matrix), 1, Actionable.MODULATE, SOURCE) == 0, "ES nested filled ES matrix");
                ItemStack empty = new ItemStack(entry.getValue().get());
                require(basic.insert(AEItemKey.of(empty), 1, Actionable.MODULATE, SOURCE) == 1, "native cell rejected empty ES matrix");
            }
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(chestPos, Blocks.AIR.defaultBlockState());
        }
    }
    private static void require(boolean condition, String reason) { if (!condition) throw new AssertionError(reason); }
}
