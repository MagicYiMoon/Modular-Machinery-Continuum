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

/**
 * A numeric pattern leaf: a comparison against whatever number the stack's tag holds.
 *
 * <h2>One class where upstream has six</h2>
 *
 * <p>Upstream builds six classes -- {@code NBTComparableByte}, {@code Short}, {@code Integer}, {@code Long}, {@code Float},
 * {@code Double} -- each extending a different vanilla numeric tag, each carrying an identical copy of the five-case
 * comparison (PLAN section 384.4 counted them: lines 62-66, 72-76, 82-86, 92-96, 102-...).
 *
 * <p>The port needs one, because the design no longer subclasses the tags (section 385: their constructors are private, so it
 * cannot) and because {@link NbtComparisonMode} already owns the comparison. <b>The six upstream classes existed to satisfy
 * single inheritance, not because the behaviour differs.</b>
 *
 * <h2>Why the comparison widens to {@code double}</h2>
 *
 * <p>{@code NumericTag.getAsDouble()} reads any of the six numeric kinds, so one leaf can be tested against a byte, a short,
 * an int, a long, a float or a double without knowing which it will be. <b>The pattern's own value is also read as a
 * {@code double}</b>, so both sides widen the same way and a comparison between them is exact for every magnitude where a
 * double is exact.</p>
 *
 * <p>The limit that leaves is worth stating rather than hiding: a {@code long} above 2^53 cannot be distinguished by this,
 * and a pattern written as {@code "=9007199254740993"} would compare equal to {@code 9007199254740992}. <b>Upstream has the same
 * limit for the same reason</b> -- its comparison also widens -- so this is inherited behaviour, not a porting regression, but
 * it is recorded because nothing in the code says it.</p>
 */
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
