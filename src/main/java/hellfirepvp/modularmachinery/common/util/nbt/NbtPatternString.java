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

/**
 * A string pattern: a regular expression a stack's string tag must match.
 *
 * <h2>This is NOT a subclass of {@code StringTag}, and that is a forced departure from upstream</h2>
 *
 * <p>Upstream's {@code NBTPatternString extends NBTTagString}, and the whole of its pattern design rests on that: <b>a
 * pattern is an ordinary NBT tree in which some leaves have been replaced by subclasses</b>, so matching is <i>equals
 * everywhere except at those leaves</i>. It is an elegant design and the port set out to keep it.
 *
 * <p><b>It cannot be kept on 1.20.1, and the reason was found the right way -- by the compiler.</b> PLAN section 384.3
 * verified with {@code javap} that {@code StringTag} carries no {@code final} modifier, and concluded the mechanism was
 * available. That check was necessary and INSUFFICIENT: {@code StringTag}'s only constructor is <b>PRIVATE</b>, and
 * {@code valueOf(String)} is a static factory that cannot give a subclass its data. The first compile of the subclass
 * reported it exactly:
 *
 * <pre>
 * error: StringTag(String) is private access in StringTag
 * </pre>
 *
 * <p><b>The lesson is worth more than the fix.</b> §384.3 asked "can this class be extended" and got a true answer to a
 * question that was not the binding one. The binding question was "can a SUBCLASS get a constructed instance", and those are
 * different questions that only coincide when a class is open to direct construction. On modern Minecraft, NBT tag types are
 * built through static factories precisely so that they can be immutable value objects, and that closes the subclass route
 * for every one of them.
 *
 * <h2>What replaces it</h2>
 *
 * <p>A pattern is its own small tree -- this class is one leaf of it -- and the matcher walks that tree against a real
 * {@code Tag} rather than walking a fake tag against a real one. The traversal of compounds, lists and arrays is the
 * matcher's job instead of the vanilla NBT library's, which is more code in ONE place and no duplicated pattern classes.
 *
 * <h2>The two rules, both upstream's</h2>
 *
 * <ol>
 *   <li><b>FULL match, not partial.</b> {@code matches()} is anchored at both ends, so {@code "foo"} does not match
 *       {@code "foobar"}. {@code find()} here would make every pattern silently looser than written.</li>
 *   <li><b>Case-insensitive</b>, because a tag's capitalisation is not something a recipe should be able to fail on.</li>
 * </ol>
 *
 * <p>And one addition: an invalid regex becomes a pattern matching nothing plus {@link #hasValidPattern()} false, so the
 * deserializer can record a problem against the file. Upstream lets {@code PatternSyntaxException} escape the constructor,
 * which during a recipe reload would abort the load instead of reporting one bad entry -- the same principle as
 * {@code io-type} and {@code energyPerTick} being REQUIRED (PLAN sections 311, 313).
 */
public final class NbtPatternString implements NbtPattern {

    private final String source;
    private final java.util.regex.Pattern strPattern;

    public NbtPatternString(String source) {
        this.source = source;
        java.util.regex.Pattern compiled;
        try {
            compiled = java.util.regex.Pattern.compile(source, java.util.regex.Pattern.CASE_INSENSITIVE);
        } catch (java.util.regex.PatternSyntaxException invalid) {
            compiled = null;
        }
        this.strPattern = compiled;
    }

    /** @return whether the regular expression compiled; false means the JSON held an invalid pattern */
    public boolean hasValidPattern() {
        return strPattern != null;
    }

    /** @return the expression as written, for error messages */
    public String source() {
        return source;
    }

    /** @param toTest the real string tag's value; @return whether it matches FULLY and case-insensitively */
    public boolean testString(String toTest) {
        return strPattern != null && strPattern.matcher(toTest).matches();
    }

    @Override
    public boolean matches(net.minecraft.nbt.Tag actual) {
        return actual instanceof net.minecraft.nbt.StringTag str && testString(str.getAsString());
    }

    @Override
    public String toString() {
        return "~" + source;
    }
}
