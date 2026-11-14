/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import appeng.core.definitions.AEItems;
import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.estorage.EStorageCellSpec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;

/** Runs against native AE keys and storage cells in the opt-in isolated server suite. */
public final class EStorageCellRegressionChecks {
    private static final IActionSource SOURCE = IActionSource.empty();
    private EStorageCellRegressionChecks() { }
    public static void run() {
        for (var tier : EStorageCellSpec.Tier.values()) {
            checkCapacity(new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, tier), AEItemKey.of(Items.STONE));
            checkCapacity(new EStorageCellSpec(EStorageCellSpec.Kind.FLUID, tier), AEFluidKey.of(Fluids.WATER));
        }
        checkTypes(); checkPersistence(); checkCorruption(); checkNestedCells();
        Modularmachinery.LOG.info("[MMC][es-cell] PASS: native AE item/fluid keys, full six-tier capacity and >int transfers, 315/25 type limits, last-type reclamation, fractional bytes, simulation, exact NBT/fluid tags, deferred persistence, malformed/unknown data preserved and filled-cell nesting refused; core checks, real ME mounting verified separately");
    }

    private static void checkCapacity(EStorageCellSpec spec, AEKey key) {
        var data = new CompoundTag();
        var inventory = new EStorageCellInventory(spec, data, null);
        long capacity = spec.capacityForTypes(1, key.getAmountPerByte());
        require(inventory.getStatus() == CellState.EMPTY && inventory.canFitInsideCell(), "initial state");
        require(inventory.insert(key, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE) == capacity
                && data.isEmpty() && inventory.storedAmount() == 0, "insert simulation wrote data");
        require(inventory.insert(key, Long.MAX_VALUE, Actionable.MODULATE, SOURCE) == capacity, "capacity truncated");
        require(inventory.getStatus() == CellState.FULL && inventory.freeBytes() == 0
                && !inventory.canFitInsideCell(), "full/nesting state");
        require(inventory.insert(key, 1, Actionable.MODULATE, SOURCE) == 0, "overfill");
        require(inventory.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, SOURCE) == capacity
                && inventory.storedAmount() == capacity, "extract simulation modified stock");
        CompoundTag reloadedData = data.copy();
        inventory = new EStorageCellInventory(spec, reloadedData, null);
        require(inventory.storedAmount() == capacity, "reload truncated long amount");
        require(inventory.extract(key, capacity - 1, Actionable.MODULATE, SOURCE) == capacity - 1, "large extraction");
        require(inventory.usedBytes() == spec.bytesPerType() + 1 && inventory.storedAmount() == 1,
                "fractional byte rounded down");
        require(inventory.extract(key, 1, Actionable.MODULATE, SOURCE) == 1 && inventory.storedTypes() == 0
                && inventory.freeBytes() == spec.totalBytes(), "final type overhead not reclaimed");
        require(new EStorageCellInventory(spec, reloadedData.copy(), null).storedAmount() == 0
                && inventory.getAvailableStacks().isEmpty(), "fully drained state persisted stale resources");
        require(inventory.insert(key, -1, Actionable.MODULATE, SOURCE) == 0
                && inventory.extract(key, -1, Actionable.MODULATE, SOURCE) == 0, "negative transfer");
        AEKey wrong = key instanceof AEItemKey ? AEFluidKey.of(Fluids.WATER) : AEItemKey.of(Items.STONE);
        require(inventory.insert(wrong, 1, Actionable.MODULATE, SOURCE) == 0, "wrong resource channel");
    }

    private static void checkTypes() {
        for (var kind : new EStorageCellSpec.Kind[]{EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Kind.FLUID}) {
            var spec = new EStorageCellSpec(kind, EStorageCellSpec.Tier.A);
            var root = new CompoundTag();
            var inventory = new EStorageCellInventory(spec, root, null);
            AEKey first = null;
            for (int i = 0; i < spec.maxTypes(); i++) {
                AEKey key = tagged(kind, i);
                if (first == null) first = key;
                require(inventory.insert(key, 1, Actionable.MODULATE, SOURCE) == 1, "type slot " + i);
            }
            require(inventory.storedTypes() == spec.maxTypes() && inventory.getStatus() == CellState.TYPES_FULL,
                    "type exhaustion status");
            require(inventory.insert(tagged(kind, 999), 1, Actionable.MODULATE, SOURCE) == 0, "extra type accepted");
            require(inventory.insert(first, 1, Actionable.MODULATE, SOURCE) == 1, "existing type rejected at type limit");
            var reopened = new EStorageCellInventory(spec, root.copy(), null);
            require(reopened.storedTypes() == spec.maxTypes() && reopened.storedAmount() == spec.maxTypes() + 1,
                    "type/NBT persistence beyond native 63-type limit");
            require(reopened.extract(first, 2, Actionable.MODULATE, SOURCE) == 2
                    && reopened.insert(tagged(kind, 999), 1, Actionable.MODULATE, SOURCE) == 1, "type slot reclamation");
        }
    }

    private static AEKey tagged(EStorageCellSpec.Kind kind, int variant) {
        CompoundTag tag = new CompoundTag(); tag.putInt("Variant", variant);
        if (kind == EStorageCellSpec.Kind.FLUID) return AEFluidKey.of(Fluids.WATER, tag);
        ItemStack item = new ItemStack(Items.STONE); item.setTag(tag);
        return AEItemKey.of(item);
    }

    private static void checkPersistence() {
        var spec = new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.B);
        CompoundTag root = new CompoundTag(); root.putString("CustomName", "original");
        int[] saves = {0};
        var inventory = new EStorageCellInventory(spec, root, () -> saves[0]++);
        require(inventory.insert(tagged(spec.kind(), 42), 3_000_000_000L, Actionable.MODULATE, SOURCE) == 3_000_000_000L,
                "large tagged insert");
        require(saves[0] == 1 && !root.contains(EStorageCellInventory.DATA_TAG), "save provider contract");
        inventory.persist();
        require(root.getString("CustomName").equals("original"), "unrelated NBT overwritten");
        var reopened = new EStorageCellInventory(spec, root.copy(), null);
        require(reopened.extract(tagged(spec.kind(), 43), 1, Actionable.MODULATE, SOURCE) == 0
                && reopened.extract(tagged(spec.kind(), 42), Long.MAX_VALUE, Actionable.MODULATE, SOURCE) == 3_000_000_000L,
                "exact key identity");
        CompoundTag snapshot = root.copy(); inventory.persist();
        require(root.equals(snapshot) && saves[0] == 1, "persist changed clean snapshot");
    }

    private static void checkCorruption() {
        var spec = new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.A);
        CompoundTag good = new CompoundTag();
        new EStorageCellInventory(spec, good, null).insert(AEItemKey.of(Items.STONE), 1, Actionable.MODULATE, SOURCE);
        for (int mode = 0; mode < 9; mode++) {
            CompoundTag broken = good.copy(), content = broken.getCompound(EStorageCellInventory.DATA_TAG);
            var entries = content.getList("Entries", Tag.TAG_COMPOUND);
            switch (mode) {
                case 0 -> content.putInt("Version", 999);
                case 1 -> entries.getCompound(0).putLong("Amount", -1);
                case 2 -> entries.getCompound(0).putLong("Amount", Long.MAX_VALUE);
                case 3 -> entries.add(entries.getCompound(0).copy());
                case 4 -> entries.getCompound(0).put("Key", new CompoundTag());
                case 5 -> content.putString("Tier", "C");
                case 6 -> entries.getCompound(0).putInt("Amount", 1);
                case 7 -> entries.getCompound(0).put("Key", AEFluidKey.of(Fluids.WATER).toTagGeneric());
                case 8 -> content.putString("Entries", "invalid");
            }
            CompoundTag original = broken.copy(); boolean rejected = false;
            try { new EStorageCellInventory(spec, broken, null); }
            catch (IllegalArgumentException expected) { rejected = true; }
            require(rejected && original.equals(broken), "invalid snapshot deleted/modified data: " + mode);
        }
    }

    private static void checkNestedCells() {
        var spec = new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.A);
        var inventory = new EStorageCellInventory(spec, new CompoundTag(), null);
        ItemStack nativeCell = AEItems.ITEM_CELL_1K.stack();
        var nativeInventory = StorageCells.getCellInventory(nativeCell, null);
        require(nativeInventory != null, "native cell handler missing");
        nativeInventory.insert(AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE, SOURCE);
        nativeInventory.persist();
        require(inventory.insert(AEItemKey.of(nativeCell), 1, Actionable.MODULATE, SOURCE) == 0, "filled nested cell accepted");
        require(inventory.insert(AEItemKey.of(AEItems.ITEM_CELL_1K.stack()), 1, Actionable.MODULATE, SOURCE) == 1,
                "empty nested cell refused");
    }

    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
