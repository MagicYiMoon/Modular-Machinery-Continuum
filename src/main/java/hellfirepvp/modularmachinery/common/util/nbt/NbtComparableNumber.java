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

/**
 * The contract a numeric PATTERN leaf satisfies: it can decide whether a real numeric tag matches it.
 *
 * <h2>Why this is an interface and not a class</h2>
 *
 * <p>Upstream's {@code NBTComparableNumber} is an interface too, and the reason is structural rather than stylistic: the six
 * leaves each extend a DIFFERENT vanilla tag -- {@code ByteTag}, {@code ShortTag}, {@code IntTag}, {@code LongTag},
 * {@code FloatTag}, {@code DoubleTag} -- and Java's single inheritance leaves nothing to extend a shared parent with. <b>A
 * pattern leaf is a real NBT tag that also knows how to test itself</b>, which is exactly what an interface can express and a
 * class cannot.
 *
 * <h2>What the matcher does with it</h2>
 *
 * <p>The pattern is an ordinary NBT tree in which some leaves have been replaced by these. Matching walks the tree and, at
 * each leaf, asks whether the pattern leaf recognises the real tag -- see {@link NbtMatchingHelper}. So this interface is the
 * single point where "the pattern is a special leaf" becomes a decision instead of an equality test.
 */
public interface NbtComparableNumber {

    /**
     * @param actual the real tag found in the stack being tested
     * @return whether it satisfies this pattern leaf
     */
    boolean test(NumericTag actual);
}
