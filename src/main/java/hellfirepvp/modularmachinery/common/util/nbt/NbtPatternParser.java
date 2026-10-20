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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a pattern's TEXT into an {@link NbtPattern} tree.
 *
 * <h2>Why this exists instead of a call into {@code TagParser}</h2>
 *
 * <p>PLAN section 388 corrected an earlier plan that said 1.20.1's {@code TagParser} could do the container half. <b>It
 * cannot, and the reason is the feature itself:</b> a pattern's leaves are deliberately NOT valid SNBT --
 * {@code {Name:>=100}} and {@code {Name:.*Sword.*}} are both rejected outright by a parser that requires legal NBT.
 * <b>{@code TagParser} is all-or-nothing, and a pattern needs per-leaf interception by construction.</b> That is why upstream's
 * deserializer is 306 lines rather than a borrowed container parser plus a small addition.
 *
 * <p>So this walks the text itself. It is smaller than upstream's because {@link NbtLeafParser} already decides leaf kinds, so
 * the byte/short/long suffix fidelity a general parser would provide is not duplicated here.
 *
 * <h2>The four shapes, and the one decision that is worth stating</h2>
 *
 * <pre>
 *   { key : value , ... }     compound
 *   [ value , ... ]           list
 *   "..."                     a quoted value
 *   bare token                a value up to a separator
 * </pre>
 *
 * <p><b>A QUOTED value and a BARE value both go to {@link NbtLeafParser}.</b> The quotes are not what makes something a literal:
 * they exist so the value may contain characters that would otherwise end it -- a comma, a brace, a space -- and so it may use
 * escapes. A pattern written {@code {Name:"Sword|Axe"}} is a REGEX, exactly as the unquoted form is. <b>Treating quoted values as
 * literals would make the two spellings mean different things</b>, which is the kind of split a pack author cannot debug.
 *
 * <h2>What is deliberately not supported, and why silence would be wrong</h2>
 *
 * <p>Unquoted values stop at a comma, brace, bracket or whitespace. Minecraft's own SNBT also allows an unquoted string to
 * contain spaces and to run to the end of its brace, and <b>this parser does not rely on that</b>: a quoted value is the
 * supported way to write anything unusual, and upstream's own tests and shipped recipes use quotes for exactly those cases.
 * Where the choice costs something, it costs a parse error rather than a wrong pattern -- see {@link #parse}.
 */
public final class NbtPatternParser {

    private final String text;
    private int cursor;

    private NbtPatternParser(String text) {
        this.text = text;
    }

    /**
     * Parses a pattern's text.
     *
     * @param text the pattern as written in the recipe file
     * @return the pattern tree
     * @throws NbtPatternSyntaxException when the text is malformed -- <b>thrown rather than returning null</b>, because the
     *         caller's contract is to record a problem against the FILE (PLAN section 385.3), and an exception carries the
     *         offset that makes the message actionable
     */
    public static NbtPattern parse(@Nullable String text) {
        if (text == null || text.isBlank()) {
            // An empty pattern constrains nothing. This is the VALUE form of the null rule in NbtMatchingHelper: a pattern
            // nobody wrote is not a constraint.
            return new NbtCompoundPattern(Map.of());
        }
        NbtPatternParser parser = new NbtPatternParser(text);
        parser.skipWhitespace();
        NbtPattern result = parser.readValue();
        parser.skipWhitespace();
        if (parser.cursor < parser.text.length()) {
            throw new NbtPatternSyntaxException("trailing text at offset " + parser.cursor + ": '"
                    + parser.text.substring(parser.cursor) + "'");
        }
        return result;
    }

    /** @return true when the text parses; used by the deserializer to report a problem without catching */
    public static boolean isValid(@Nullable String text) {
        try {
            parse(text);
            return true;
        } catch (NbtPatternSyntaxException malformed) {
            return false;
        }
    }

    private NbtPattern readValue() {
        skipWhitespace();
        char c = peek();
        if (c == '{') {
            return readCompound();
        }
        if (c == '[') {
            return readList();
        }
        // Quoted and bare both become LEAVES, and the leaf parser decides whether the content is numeric. See the class
        // javadoc: the quotes govern termination and escapes, not whether the value is a pattern.
        return NbtLeafParser.parseLeaf(readToken());
    }

    private NbtPattern readCompound() {
        expect('{');
        Map<String, NbtPattern> keys = new LinkedHashMap<>();
        skipWhitespace();
        if (peek() == '}') {
            pop();
            return new NbtCompoundPattern(keys);
        }
        while (true) {
            skipWhitespace();
            String key = readKey();
            skipWhitespace();
            expect(':');
            keys.put(key, readValue());
            skipWhitespace();
            char next = pop();
            if (next == '}') {
                return new NbtCompoundPattern(keys);
            }
            if (next != ',') {
                throw new NbtPatternSyntaxException("expected ',' or '}' at offset " + (cursor - 1) + ", found '" + next + "'");
            }
        }
    }

    private NbtPattern readList() {
        expect('[');
        List<NbtPattern> entries = new ArrayList<>();
        skipWhitespace();
        if (peek() == ']') {
            pop();
            return new NbtListPattern(entries);
        }
        while (true) {
            entries.add(readValue());
            skipWhitespace();
            char next = pop();
            if (next == ']') {
                return new NbtListPattern(entries);
            }
            if (next != ',') {
                throw new NbtPatternSyntaxException("expected ',' or ']' at offset " + (cursor - 1) + ", found '" + next + "'");
            }
        }
    }

    /** A key is anything up to the colon; quotes are honoured so a key may contain one. */
    private String readKey() {
        String raw = readToken();
        if (raw.isEmpty()) {
            throw new NbtPatternSyntaxException("empty key at offset " + cursor);
        }
        return raw;
    }

    /**
     * Reads one token: quoted or bare.
     *
     * <p>A quoted token is unescaped; a bare one stops at a separator. Both are returned WITHOUT their quotes, because the
     * leaf parser is what gives the content meaning.
     */
    private String readToken() {
        skipWhitespace();
        char c = peek();
        if (c == '"' || c == '\'') {
            return readQuoted(c);
        }
        int start = cursor;
        while (cursor < text.length()) {
            char current = text.charAt(cursor);
            if (current == ',' || current == '}' || current == ']' || current == ':'
                    || Character.isWhitespace(current)) {
                break;
            }
            cursor++;
        }
        return text.substring(start, cursor);
    }

    private String readQuoted(char quote) {
        pop();
        StringBuilder out = new StringBuilder();
        while (true) {
            if (cursor >= text.length()) {
                throw new NbtPatternSyntaxException("unterminated quoted value starting before offset " + cursor);
            }
            char c = pop();
            if (c == quote) {
                return out.toString();
            }
            if (c == '\\') {
                if (cursor >= text.length()) {
                    throw new NbtPatternSyntaxException("trailing backslash at offset " + cursor);
                }
                char escaped = pop();
                switch (escaped) {
                    // The escapes SNBT defines AND the ones a REGEX needs. A pattern is a regular expression, so its own
                    // metacharacters must survive: "\\d+" arrives as backslash-d-plus rather than as a bare "d".
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case '\\' -> out.append('\\');
                    case '"' -> out.append('"');
                    case '\'' -> out.append('\'');
                    default -> out.append(escaped);
                }
                continue;
            }
            out.append(c);
        }
    }

    private void skipWhitespace() {
        while (cursor < text.length() && Character.isWhitespace(text.charAt(cursor))) {
            cursor++;
        }
    }

    private char peek() {
        if (cursor >= text.length()) {
            throw new NbtPatternSyntaxException("unexpected end of pattern at offset " + cursor);
        }
        return text.charAt(cursor);
    }

    private char pop() {
        char c = peek();
        cursor++;
        return c;
    }

    private void expect(char expected) {
        skipWhitespace();
        char found = pop();
        if (found != expected) {
            throw new NbtPatternSyntaxException("expected '" + expected + "' at offset " + (cursor - 1) + ", found '" + found + "'");
        }
    }
}
