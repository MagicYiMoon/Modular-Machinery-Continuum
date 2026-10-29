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
