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



































 
public final class NbtPatternParser {

    private final String text;
    private int cursor;

    private NbtPatternParser(String text) {
        this.text = text;
    }

    







 
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
