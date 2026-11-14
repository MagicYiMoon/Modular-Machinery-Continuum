/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.integration.ae2.estorage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import hellfirepvp.modularmachinery.common.estorage.EStorageCellSpec;

/** Optional chemical API stays behind a factory returning only AE types. */
final class EStorageKeySupport {
    private EStorageKeySupport() { }
    static AEKeyType type(EStorageCellSpec spec) {
        if (!spec.backendAvailable()) throw new IllegalArgumentException("ES matrix backend is unavailable; original NBT preserved");
        return switch (spec.kind()) {
            case ITEM -> AEKeyType.items();
            case FLUID -> AEKeyType.fluids();
            case GAS -> hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasKeys.type();
        };
    }
    static boolean accepts(EStorageCellSpec spec, AEKey key) {
        if (key == null || key.getType() != type(spec)) return false;
        return spec.kind() != EStorageCellSpec.Kind.GAS
                || hellfirepvp.modularmachinery.integration.appmek.estorage.EStorageGasKeys.isGas(key);
    }
}
