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

import org.jetbrains.annotations.Nullable;

/**
 * The comparison grammar behind a numeric NBT pattern, e.g. {@code ">=100"}.
 *
 * <h2>What the pattern language is</h2>
 *
 * <p>A numeric leaf in a pattern is written as an optional operator followed by a number. The operator is read from the
 * LEADING CHARACTERS, which is why {@code "<="} has to be tested before {@code "<"}: a parser that checked {@code "<"}
 * first would read {@code "<=100"} as <i>less than</i> the number {@code "=100"}, which is not a number at all.
 *
 * <table>
 *   <caption>Operators</caption>
 *   <tr><td>{@code <}</td><td>less than</td></tr>
 *   <tr><td>{@code <=}</td><td>less than or equal</td></tr>
 *   <tr><td>{@code =}</td><td>equal (also the default when no operator is present)</td></tr>
 *   <tr><td>{@code >=}</td><td>greater than or equal</td></tr>
 *   <tr><td>{@code >}</td><td>greater than</td></tr>
 * </table>
 *
 * <h2>Why this is one enum with one comparison, and upstream is not</h2>
 *
 * <p>Upstream's {@code NBTComparableNumber} carries this same five-case switch <b>six times</b> -- once in each of
 * {@code NBTComparableByte}, {@code Short}, {@code Integer}, {@code Long}, {@code Float} and {@code Double} -- with all five
 * cases textually identical in every copy.
 *
 * <p>That is the shape PLAN section 367 found in the item buses' neighbour walk: the same rules duplicated across classes,
 * where nothing keeps the copies in step and a later reader can change one and not the others. <b>The port compares through
 * ONE method over {@code double}, and the six leaf classes differ only in which vanilla tag they extend</b> -- which is the
 * only thing that genuinely differs between them.
 *
 * <p>{@code double} is wide enough for every numeric tag the format supports. It is not exact for a {@code long} above
 * 2^53, and that limit is accepted rather than hidden: a pattern comparing a {@code long} at that magnitude would compare
 * the same way a JSON reader already parses it, since the deserializer reads the pattern's number as a {@code double} too.
 */
public enum NbtComparisonMode {

    LESS("<"),
    LESS_EQUAL("<="),
    EQUAL("="),
    GREATER_EQUAL(">="),
    GREATER(">");

    /** Longest first, so {@code "<="} is matched before {@code "<"}. */
    private static final NbtComparisonMode[] BY_PREFIX_LENGTH = {GREATER_EQUAL, LESS_EQUAL, EQUAL, GREATER, LESS};

    private final String prefix;

    NbtComparisonMode(String prefix) {
        this.prefix = prefix;
    }

    public String prefix() {
        return prefix;
    }

    /**
     * Reads the mode from the front of a pattern string.
     *
     * @param pattern the pattern, e.g. {@code ">=100"} or {@code "100"}
     * @return the mode, or {@code null} when {@code pattern} is null
     */
    @Nullable
    public static NbtComparisonMode peekMode(@Nullable String pattern) {
        if (pattern == null) {
            return null;
        }
        String trimmed = pattern.trim();
        for (NbtComparisonMode mode : BY_PREFIX_LENGTH) {
            if (trimmed.startsWith(mode.prefix)) {
                return mode;
            }
        }
        // No operator means EQUAL, which is what upstream's own default does.
        return EQUAL;
    }

    /**
     * Strips the leading operator, leaving the number.
     *
     * @param pattern the pattern, e.g. {@code ">=100"}
     * @return the numeric text, e.g. {@code "100"}; never null for a non-null input
     */
    public static String stripMode(@Nullable String pattern) {
        if (pattern == null) {
            return "";
        }
        String trimmed = pattern.trim();
        for (NbtComparisonMode mode : BY_PREFIX_LENGTH) {
            if (trimmed.startsWith(mode.prefix)) {
                return trimmed.substring(mode.prefix.length()).trim();
            }
        }
        return trimmed;
    }

    /**
     * Tests one value against the pattern's value.
     *
     * @param patternValue the number the pattern named
     * @param actualValue the number found in the stack
     * @return whether the comparison holds
     */
    public boolean test(double patternValue, double actualValue) {
        return switch (this) {
            case LESS -> actualValue < patternValue;
            case LESS_EQUAL -> actualValue <= patternValue;
            case EQUAL -> actualValue == patternValue;
            case GREATER_EQUAL -> actualValue >= patternValue;
            case GREATER -> actualValue > patternValue;
        };
    }
}
