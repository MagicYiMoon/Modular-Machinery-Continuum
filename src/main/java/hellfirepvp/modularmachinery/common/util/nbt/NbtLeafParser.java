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

import java.util.regex.Pattern;

/**
 * Decides what ONE leaf of a pattern text means: a number to compare, or a regular expression to match.
 *
 * <h2>The whole rule, and it is one rule</h2>
 *
 * <p>Upstream's {@code NBTJsonDeserializer.type(String)} opens by reading a comparison operator off the front, then tries a
 * cascade of numeric shapes, and <b>falls through to {@code new NBTPatternString(stringIn)}</b> for anything else. So:</p>
 *
 * <ul>
 *   <li><b>Text that looks like a number becomes a numeric comparison.</b> The optional type suffixes are Minecraft's own --
 *       {@code 5b}, {@code 5s}, {@code 5l}, {@code 1.5f}, {@code 1.5d} -- and the operator is optional, defaulting to
 *       {@code =}.</li>
 *   <li><b>Everything else becomes a REGEX.</b> Not a literal string: a pattern's default leaf is a regular expression that
 *       has to match the whole value.</li>
 * </ul>
 *
 * <p><b>That fallback is the single most consequential line in the whole feature and it is easy to read past.</b> A pack
 * author writing {@code "Name": "Sword"} gets a regex, so {@code "Name": "Sword|Axe"} works and so does
 * {@code "Name": ".*Sword.*"} -- while a literal-minded port that made the fallback a string EQUALITY would reject both,
 * silently, with every recipe still loading.
 *
 * <h2>And why the numeric shapes are tested in that order</h2>
 *
 * <p>{@code 1.5f} must be read as a float, not as an int with trailing junk, and {@code 5} must be read as an int rather than
 * as a double that happens to be whole. Upstream's order is float, byte, long, short, int, double, then double-without-suffix;
 * the port keeps the ORDER because the SUFFIX LETTERS are what disambiguate, and a reordering would make {@code 5l} fall into
 * the int case and compare as an int.</p>
 *
 * <h2>Why this is a separate class from the JSON walk</h2>
 *
 * <p>Upstream's deserializer is 306 lines because it is a COMPLETE JSON-to-NBT parser -- the numeric patterns above are copied
 * from vanilla's own {@code JsonToNBT}. <b>1.20.1 ships {@code TagParser}, so the port does not rewrite the container half</b>
 * and only needs this leaf rule. That is the whole reason this class exists on its own.
 */
public final class NbtLeafParser {

    // The suffix-bearing shapes. Case-insensitive: upstream's copies from JsonToNBT use CASE_INSENSITIVE, and a pack author
    // writing "5L" means the same thing as "5l".
    private static final Pattern BYTE_PATTERN  = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG_PATTERN  = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOAT_PATTERN = Pattern.compile(
            "[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE_PATTERN_NOSUFFIX = Pattern.compile(
            "[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?");
    private static final Pattern DOUBLE_PATTERN = Pattern.compile(
            "[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", Pattern.CASE_INSENSITIVE);
    private static final Pattern INT_PATTERN = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)");

    private NbtLeafParser() {
    }

    /**
     * Reads one leaf.
     *
     * @param text the leaf as written in the file
     * @return the leaf; a {@link NbtPatternString} when the text is not numeric, and never null for non-null input
     */
    public static NbtPattern parseLeaf(String text) {
        String operator = operatorOf(text);
        String body = NbtComparisonMode.stripMode(text);

        // Order matters -- see the class javadoc. The operator is stripped first, so ">=5l" is read as the long 5.
        if ("true".equalsIgnoreCase(body) || "false".equalsIgnoreCase(body)) {
            return new NbtNumberPattern(NbtComparisonMode.EQUAL, Boolean.parseBoolean(body) ? 1 : 0, body);
        }
        if (DOUBLE_PATTERN.matcher(body).matches()) {
            return numberOrRegex(text, body, operator, 'd');
        }
        if (FLOAT_PATTERN.matcher(body).matches()) {
            return numberOrRegex(text, body, operator, 'f');
        }
        if (BYTE_PATTERN.matcher(body).matches()) {
            return numberOrRegex(text, body, operator, 'b');
        }
        if (LONG_PATTERN.matcher(body).matches()) {
            return numberOrRegex(text, body, operator, 'l');
        }
        if (SHORT_PATTERN.matcher(body).matches()) {
            return numberOrRegex(text, body, operator, 's');
        }
        if (INT_PATTERN.matcher(body).matches()) {
            return numberOrRegex(text, body, operator, '\0');
        }
        if (DOUBLE_PATTERN_NOSUFFIX.matcher(body).matches()) {
            return numberOrRegex(text, body, operator, 'd');
        }

        // THE FALLBACK: a regular expression, not a literal string.
        return new NbtPatternString(text);
    }

    /** @return true when the text describes a numeric comparison rather than a regular expression */
    public static boolean isNumeric(@Nullable String text) {
        return text != null && !(parseLeaf(text) instanceof NbtPatternString);
    }

    private static NbtPattern numberOrRegex(String original, String body, String operator, char suffix) {
        String numeric = body;
        if (suffix != '\0' && !numeric.isEmpty()) {
            char last = numeric.charAt(numeric.length() - 1);
            if (last == suffix || Character.toLowerCase(last) == Character.toLowerCase(suffix)) {
                numeric = numeric.substring(0, numeric.length() - 1);
            }
        }
        try {
            double value = Double.parseDouble(numeric);
            NbtComparisonMode mode = operator.isEmpty() ? NbtComparisonMode.EQUAL : modeOf(operator);
            return new NbtNumberPattern(mode, value, original);
        } catch (NumberFormatException notANumber) {
            // The pattern regex matched but Double.parseDouble disagreed -- an overflow like "1e999" is the realistic case.
            // Falling back to a regex leaf keeps the entry loadable, and the value is still a faithful pattern.
            return new NbtPatternString(original);
        }
    }

    /** @return the leading operator text, or an empty string when none is present */
    private static String operatorOf(String text) {
        NbtComparisonMode mode = NbtComparisonMode.peekMode(text);
        String trimmed = text.trim();
        return mode != null && trimmed.startsWith(mode.prefix()) ? mode.prefix() : "";
    }

    private static NbtComparisonMode modeOf(String operator) {
        for (NbtComparisonMode mode : NbtComparisonMode.values()) {
            if (mode.prefix().equals(operator)) {
                return mode;
            }
        }
        return NbtComparisonMode.EQUAL;
    }
}
