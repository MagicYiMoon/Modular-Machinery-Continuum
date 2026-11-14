/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NovaEngineering Core, KasumiNova and contributors (b37fbb53).
 */
package hellfirepvp.modularmachinery.common.ecalculator;

/** EC describes AE crafting CPUs, not MMC recipe threads or batch multipliers. */
public final class ECalculatorSpecs {
    private ECalculatorSpecs() { }
    public enum Memory {
        A(64, 4), B(1024, 6), C(16384, 9);
        private final int millions, minimumTier;
        Memory(int millions, int minimumTier) { this.millions = millions; this.minimumTier = minimumTier; }
        public int millionBytes() { return millions; }
        public int minimumTier() { return minimumTier; }
        public long bytes() { return millions * 1000L * 1024L; }
        public boolean supportedBy(int tier) { return tier >= minimumTier; }
    }
    public enum Kind { DRIVE, THREAD, HYPER_THREAD, PARALLEL, CHANNEL, TRANSMITTER, TAIL }
    public record Part(Kind kind, int tier) {
        public int normalSlots() { return kind == Kind.THREAD ? switch (tier) { case 4 -> 1; case 6 -> 2; case 9 -> 4; default -> 0; }
                : kind == Kind.HYPER_THREAD && tier == 9 ? 1 : 0; }
        public int hyperSlots() { return kind == Kind.HYPER_THREAD ? switch (tier) { case 4 -> 2; case 6 -> 4; case 9 -> 8; default -> 0; } : 0; }
        public int parallelism() { return kind == Kind.PARALLEL ? switch (tier) { case 4 -> 256; case 6 -> 2048; case 9 -> 16384; default -> 0; } : 0; }
    }
}
