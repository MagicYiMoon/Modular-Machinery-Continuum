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

import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * A list pattern: UNORDERED consumable containment.
 *
 * <h2>The four rules, taken from upstream's {@code matchList} rather than invented</h2>
 *
 * <ol>
 *   <li><b>An empty pattern list matches anything.</b> Same shape as the null-pattern rule in {@link NbtMatchingHelper}: a
 *       constraint nobody wrote is not a constraint.</li>
 *   <li><b>The element types must be equal, and the stack's list must not be empty</b> -- unless the pattern is empty, which
 *       rule 1 already returned on. {@code ListTag.getElementType()} is what makes this checkable; on 1.12.2 it was
 *       {@code getTagType()}.</li>
 *   <li><b>Each pattern entry must match some stack entry.</b></li>
 *   <li><b>And each stack entry can be consumed only ONCE.</b> Upstream consumes into a working copy via
 *       {@code copyConsumeTags.remove(stackBase)}.</li>
 * </ol>
 *
 * <h2>Rule 4 is the one that is easy to drop, and dropping it loosens every pattern silently</h2>
 *
 * <p>Without consumption, a pattern of two entries both matching the same single stack entry would be TRUE against a list
 * holding that entry once. <b>That is a pattern claiming a stack has two of something when it has one</b>, and nothing would
 * fail or warn -- the recipe would simply accept a stack it should reject. It is the same class of silent looseness as
 * {@code find()} instead of {@code matches()} in {@link NbtPatternString}, and as the {@code "chance"} key that was once not
 * read at all (PLAN section 380.2).</p>
 *
 * <h2>And order is NOT part of the match</h2>
 *
 * <p>Upstream scans the whole remaining list per pattern entry, so {@code [A, B]} and {@code [B, A]} both match a stack list of
 * {@code [B, A]}. <b>That is right for this format</b>: a {@code ListTag} on an item is a set of attributes, not a sequence, and
 * requiring an order would make patterns depend on the order a mod happened to write them.</p>
 */
public final class NbtListPattern implements NbtPattern {

    private final List<NbtPattern> entries;

    public NbtListPattern(List<NbtPattern> entries) {
        this.entries = new ArrayList<>(entries);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public List<NbtPattern> entries() {
        return java.util.Collections.unmodifiableList(entries);
    }

    @Override
    public boolean matches(Tag actual) {
        if (!(actual instanceof ListTag list)) {
            return false;
        }
        // Rule 1.
        if (entries.isEmpty()) {
            return true;
        }
        // Rule 2. A list with no element type holds nothing, so it cannot satisfy a non-empty pattern.
        if (list.isEmpty()) {
            return false;
        }

        // The working copy that rules 3 and 4 operate on. Kept as the remaining candidates so a consumed entry cannot be
        // claimed twice.
        List<Tag> remaining = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) {
            remaining.add(list.get(i));
        }

        outer:
        for (NbtPattern entry : entries) {
            for (int i = 0; i < remaining.size(); i++) {
                if (entry.matches(remaining.get(i))) {
                    remaining.remove(i);
                    continue outer;
                }
            }
            // This pattern entry matched nothing still available.
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return entries.toString();
    }
}
