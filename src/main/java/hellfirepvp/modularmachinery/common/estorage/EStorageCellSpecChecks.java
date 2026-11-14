/* SPDX-License-Identifier: GPL-3.0-only */
package hellfirepvp.modularmachinery.common.estorage;

import hellfirepvp.modularmachinery.Modularmachinery;

/** Source parameter oracle, also exercised on a server without AE2. */
public final class EStorageCellSpecChecks {
    private EStorageCellSpecChecks() { }
    public static void run() {
        long[] bytes = {16_384_000, 65_536_000, 262_144_000};
        int[] typeBytes = {4096, 16384, 65536};
        long[] singleItemCap = {524_156_928, 8_386_510_848L, 134_184_173_568L};
        for (var kind : EStorageCellSpec.Kind.values()) for (var tier : EStorageCellSpec.Tier.values()) {
            var spec = new EStorageCellSpec(kind, tier);
            require(spec.totalBytes() == bytes[tier.ordinal()], "M-byte size differs from upstream");
            require(spec.bytesPerType() == typeBytes[tier.ordinal()], "type overhead differs");
            require(spec.capacityForTypes(1, 8) == singleItemCap[tier.ordinal()], "single-type item capacity");
            require(spec.maxTypes() == (kind == EStorageCellSpec.Kind.ITEM ? 315 : 25), "type limit");
            require(spec.idleDrain() == bytes[tier.ordinal()] / 1048576.0, "idle drain");
            require(!tier.supportedBy(0) && !tier.supportedBy(5) && !tier.supportedBy(10), "invalid ES host");
            require(tier.supportedBy(4) == (tier == EStorageCellSpec.Tier.A), "S4 tier gate");
            require(tier.supportedBy(6) == (tier != EStorageCellSpec.Tier.C) && tier.supportedBy(9), "S6/S9 tier gate");
        }
        Modularmachinery.LOG.info("[MMC][es-spec] PASS: nine matrix specifications, source byte sizes/overheads, 315/25 types, 64-bit capacities and S4/S6/S9 host restrictions (no AE dependency)");
    }
    private static void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
}
