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
