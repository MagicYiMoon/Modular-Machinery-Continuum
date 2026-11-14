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

import net.minecraft.nbt.Tag;

/**
 * One node of a pattern tree: something that can decide whether a real NBT tag satisfies it.
 *
 * <h2>Why the port walks its own tree instead of subclassing the tags</h2>
 *
 * <p>Upstream's patterns ARE NBT trees: it subclasses {@code NBTTagString} and {@code NBTPrimitive} so that a pattern is an
 * ordinary tag in which some leaves have been replaced, and matching is <i>equals everywhere except at those leaves</i>.
 *
 * <p><b>That is not available on 1.20.1, and the compiler said so.</b> PLAN section 384.3 verified with {@code javap} that the
 * tag classes are not {@code final} and concluded the mechanism survived. It does not: {@code StringTag}'s only constructor is
 * <b>private</b>, and its public {@code valueOf(String)} is a static factory that a subclass cannot use to seed itself. The
 * first compile of the subclass failed with exactly that. <b>Modern NBT tags are immutable value objects built through
 * factories, which closes the subclass route for all of them.</b>
 *
 * <p>So the pattern is its own type, and {@link NbtMatchingHelper} walks it. The trade is explicit: the traversal of
 * compounds, lists and arrays moves from the vanilla library into the matcher -- <b>more code in ONE place, and no duplicated
 * pattern classes per numeric type.</b>
 *
 * <h2>What a node must decide</h2>
 *
 * <p>{@link #matches} receives the real tag found at that position in the stack. A node that recognises it returns true; a
 * node that does not, or that receives a tag of the wrong kind, returns false. <b>A wrong-kind tag is false rather than an
 * error</b>: a pattern is a filter, and a stack whose tag is a string where the pattern wanted a number simply does not match.
 */
public interface NbtPattern {

    /**
     * @param actual the real tag found in the stack being tested
     * @return whether it satisfies this node
     */
    boolean matches(Tag actual);
}
