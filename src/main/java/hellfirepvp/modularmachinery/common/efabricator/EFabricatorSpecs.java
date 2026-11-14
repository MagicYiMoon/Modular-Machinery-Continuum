/* SPDX-License-Identifier: GPL-3.0-only
 * Adapted from NovaEngineering Core b37fbb53, KasumiNova and contributors.
 */
package hellfirepvp.modularmachinery.common.efabricator;

import java.util.Collection;

/** Source physical specifications; these are not MMC recipe-thread multipliers. */
public final class EFabricatorSpecs {
    public static final int BASE_QUEUE = 32, MAX_QUEUE = 512, ENERGY_CACHE = 500_000,
            ENERGY_PER_CRAFT = 100, COOLANT_PER_CRAFT = 5, COOLANT_CACHE = 100_000,
            WORK_DELAY = 20, PATTERN_SLOTS = 72;
    private EFabricatorSpecs() { }
    public enum Kind { WORKER, PATTERN_BUS, CHANNEL, PARALLEL }
    public enum Tier {
        L4(4, 4, 24, 32, .99), L6(6, 8, 72, 96, .98), L9(9, 16, 256, 384, .97);
        public final int level, overclockMultiplier, parallel, extraParallel;
        public final double penalty;
        Tier(int level, int multiplier, int parallel, int extra, double penalty) {
            this.level = level; overclockMultiplier = multiplier; this.parallel = parallel; extraParallel = extra; this.penalty = penalty;
        }
        public static Tier of(int level) {
            for (var tier : values()) if (tier.level == level) return tier;
            throw new IllegalArgumentException("Unsupported EF tier: " + level);
        }
        public int queueDepth(boolean overclock) { return BASE_QUEUE * (overclock ? overclockMultiplier : 1); }
        public int energyPerCraft(boolean overclock, boolean cooling) { return ENERGY_PER_CRAFT * (overclock && !cooling ? overclockMultiplier : 1); }
    }
    public record Part(Kind kind, Tier tier) {
        public Part { if (kind == null || (kind == Kind.PARALLEL) != (tier != null)) throw new IllegalArgumentException("Invalid EF part"); }
    }
    public static int parallelism(Collection<Tier> processors, boolean overclock, boolean cooling) {
        double value = 0;
        for (var tier : processors) value += tier.parallel + (overclock ? tier.extraParallel : 0);
        // Source applies all additions before debuffs; cooling removes only the latter.
        if (overclock && !cooling) for (var tier : processors) value *= tier.penalty;
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0, Math.round(value)));
    }
    public static int workDelay(int workers, boolean cooling) {
        // Large custom structures must not produce a zero/negative tick interval.
        return cooling ? Math.max(1, WORK_DELAY - Math.max(0, workers)) : WORK_DELAY;
    }
    public record Acceleration(int ticks, int units) { }
    public static Acceleration coolingAcceleration(int parallelism, int unused, int delay, int coolant) {
        if (parallelism <= 0 || unused <= 0 || delay <= 1 || coolant <= 0) return new Acceleration(0, 0);
        float ratio = (float)parallelism / Math.min(unused, parallelism);
        double perTick = parallelism * .04;
        int speedup = Math.min(Math.round(ratio / .05f), delay - 1);
        speedup = Math.min(speedup, (int)(coolant / perTick));
        return new Acceleration(speedup, (int)Math.round(speedup * perTick));
    }
}
