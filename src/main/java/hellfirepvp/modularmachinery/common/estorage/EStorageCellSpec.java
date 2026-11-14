/* SPDX-License-Identifier: GPL-3.0-only
 * ES parameters ported from NovaEngineering-Core by KasumiNova and contributors.
 * Upstream: b37fbb53e4012d15baac2d95a8c1d4a32c62d6a8, modified for Forge 1.20.1.
 */
package hellfirepvp.modularmachinery.common.estorage;

/** Source ES matrix parameters. This class must remain usable without AE2 installed. */
public record EStorageCellSpec(Kind kind, Tier tier) {
    public enum Kind {
        ITEM(315), FLUID(25), GAS(25);
        private final int maxTypes;
        Kind(int maxTypes) { this.maxTypes = maxTypes; }
        public int maxTypes() { return maxTypes; }
    }

    public enum Tier {
        A(16, 4, 4), B(64, 16, 6), C(256, 64, 9);
        private final int millionBytes, multiplier, minimumHostLevel;
        Tier(int millionBytes, int multiplier, int minimumHostLevel) {
            this.millionBytes = millionBytes;
            this.multiplier = multiplier;
            this.minimumHostLevel = minimumHostLevel;
        }
        public int millionBytes() { return millionBytes; }
        public int multiplier() { return multiplier; }
        public int minimumHostLevel() { return minimumHostLevel; }
        public boolean supportedBy(int hostLevel) {
            return (hostLevel == 4 || hostLevel == 6 || hostLevel == 9) && hostLevel >= minimumHostLevel;
        }
    }

    public EStorageCellSpec {
        java.util.Objects.requireNonNull(kind, "kind");
        java.util.Objects.requireNonNull(tier, "tier");
    }

    // The source uses 1000 * 1024, not 1024 * 1024, for its named M-byte sizes.
    public long totalBytes() { return (long) tier.millionBytes() * 1000 * 1024; }
    public int bytesPerType() { return tier.multiplier() * 1024; }
    public int maxTypes() { return kind.maxTypes(); }
    public boolean backendAvailable() {
        return net.minecraftforge.fml.ModList.get().isLoaded("ae2")
                && (kind != Kind.GAS || net.minecraftforge.fml.ModList.get().isLoaded("appmek"));
    }
    public double idleDrain() { return totalBytes() / (1024.0 * 1024.0); }

    /** AE2's units per byte are supplied by the integration (items or mB), never hardcoded here. */
    public long unitsPerByte(int baseUnitsPerByte) {
        if (baseUnitsPerByte <= 0) throw new IllegalArgumentException("Non-positive units per byte");
        return Math.multiplyExact((long) baseUnitsPerByte, tier.multiplier());
    }

    public long capacityForTypes(int types, int baseUnitsPerByte) {
        if (types < 0 || types > maxTypes()) throw new IllegalArgumentException("Invalid type count: " + types);
        return Math.multiplyExact(totalBytes() - (long) types * bytesPerType(), unitsPerByte(baseUnitsPerByte));
    }
}
