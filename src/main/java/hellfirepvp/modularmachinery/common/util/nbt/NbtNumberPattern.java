/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED. Modifications made 2026-10.
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.util.nbt;

import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.Tag;

























 
public final class NbtNumberPattern implements NbtPattern {

    private final NbtComparisonMode mode;
    private final double value;
    private final String source;

    /**
     * @param mode the comparison to apply
     * @param value the number the pattern named
     * @param source the pattern as written, kept for error messages
     */
    public NbtNumberPattern(NbtComparisonMode mode, double value, String source) {
        this.mode = mode;
        this.value = value;
        this.source = source;
    }

    /**
     * Builds a leaf from a pattern string such as {@code ">=100"}.
     *
     * @param pattern the pattern text
     * @return the leaf, or {@code null} when the numeric part does not parse -- which the deserializer reports against the
     *         file rather than allowing a leaf that silently compares against zero
     */
    public static NbtNumberPattern parse(String pattern) {
        NbtComparisonMode mode = NbtComparisonMode.peekMode(pattern);
        String numeric = NbtComparisonMode.stripMode(pattern);
        try {
            return new NbtNumberPattern(mode, Double.parseDouble(numeric), pattern);
        } catch (NumberFormatException notANumber) {
            // Reported rather than defaulted. A leaf comparing against 0 would accept every negative tag and reject every
            // positive one, which looks like a working filter and is not.
            return null;
        }
    }

    public NbtComparisonMode mode() {
        return mode;
    }

    public double value() {
        return value;
    }

    @Override
    public boolean matches(Tag actual) {
        return actual instanceof NumericTag numeric && mode.test(value, numeric.getAsDouble());
    }

    @Override
    public String toString() {
        return source;
    }
}
