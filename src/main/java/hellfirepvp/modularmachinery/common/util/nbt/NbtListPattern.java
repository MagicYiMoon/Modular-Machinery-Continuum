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
