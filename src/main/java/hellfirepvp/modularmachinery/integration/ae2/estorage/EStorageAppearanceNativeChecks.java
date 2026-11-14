/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import hellfirepvp.modularmachinery.common.estorage.*;
import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.*;

public final class EStorageAppearanceNativeChecks {
    private EStorageAppearanceNativeChecks() { }
    public static void run(TileEStorageDrive drive, TileEStorageController owner) {
        var spec = new EStorageCellSpec(EStorageCellSpec.Kind.ITEM, EStorageCellSpec.Tier.A);
        drive.insertMatrix(new ItemStack(EStorageRegistration.CELLS.get(spec).get())); owner.refreshStructure();
        var data = (EStorageCellInventory) drive.storageAccess(); var source = IActionSource.empty();
        var diamond = AEItemKey.of(Items.DIAMOND);
        data.insert(diamond, 1, Actionable.SIMULATE, source);
        require(drive.appearance().status() == EStorageDriveAppearance.Status.IDLE, "simulation triggered writing LED");
        data.insert(diamond, 1, Actionable.MODULATE, source);
        require(drive.appearance().status() == EStorageDriveAppearance.Status.RUN, "real insert failed to trigger writing LED");
        data.extract(diamond, 1, Actionable.MODULATE, source);
        AEItemKey first = null;
        for (int i = 0; i < 315; i++) {
            var stack = new ItemStack(Items.DIAMOND); stack.getOrCreateTag().putInt("es-visual-key", i);
            var key = AEItemKey.of(stack); if (first == null) first = key;
            require(data.insert(key, 1, Actionable.MODULATE, source) == 1, "type-full fixture");
        }
        require(drive.appearance().capacity() == EStorageDriveAppearance.Capacity.TYPE_MAX, "type-full LED not orange");
        require(data.insert(first, Long.MAX_VALUE, Actionable.MODULATE, source) > 0
                && drive.appearance().capacity() == EStorageDriveAppearance.Capacity.FULL, "capacity-full LED not red");
        data.extract(first, spec.unitsPerByte(first.getType().getAmountPerByte()), Actionable.MODULATE, source);
        require(drive.appearance().capacity() == EStorageDriveAppearance.Capacity.TYPE_MAX, "freed byte did not restore orange LED");
        data.extract(first, Long.MAX_VALUE, Actionable.MODULATE, source);
        require(drive.appearance().capacity() == EStorageDriveAppearance.Capacity.EMPTY, "freed type did not restore green LED");
        var saved = drive.matrixSnapshot(); drive.tick();
        require(!owner.isStructureCheckRequested() && owner.isOperational(), "writing LED changed server structure");
        drive.takeMatrix(); require(drive.appearance().equals(EStorageDriveAppearance.EMPTY), "removed matrix left stale appearance");
        require(saved.hasTag(), "visual access discarded contents");
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
