/* SPDX-License-Identifier: GPL-3.0-only
 * MMC (Modular Machinery: Continuum), modified 2026-10. Derived from MMCE's inverted layer scrollbar.
 */
package hellfirepvp.modularmachinery.common.machine;

/** Relative structure Y coordinates: the top of the scrollbar selects the highest layer. */
public record BlueprintLayerRange(int min, int max) {
    public BlueprintLayerRange {
        if (max < min) throw new IllegalArgumentException("Inverted preview layer range");
    }

    public long count() { return (long) max - min + 1; }
    public int clamp(long layer) { return (int) Math.max(min, Math.min((long) max, layer)); }
    public int step(int layer, int delta) { return clamp((long) layer + delta); }
    public double fraction(int layer) {
        return min == max ? 0 : ((long) max - clamp(layer)) / (double) ((long) max - min);
    }
    public int atFraction(double fraction) {
        if (Double.isNaN(fraction)) return max;
        double bounded = Math.max(0, Math.min(1, fraction));
        return clamp((long) max - Math.round(bounded * ((long) max - min)));
    }
}
