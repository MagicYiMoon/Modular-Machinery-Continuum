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

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A compound pattern: every key it names must be present in the stack's compound and must itself match.
 *
 * <h2>SUBSET semantics, and they are not symmetric</h2>
 *
 * <p>A pattern listing one key matches a stack tag holding ten. <b>Keys present only in the STACK are ignored</b>, which is the
 * point: a pack author constrains the keys they care about without having to reproduce every key a mod happens to write into
 * an item's tag.
 *
 * <p>Getting this backwards -- requiring the two compounds to be equal -- would make almost every pattern fail, and it would
 * fail silently: the recipe would simply never run, with nothing in the log to say why. That is the failure mode §§311, 313
 * and 373 each dealt with in a different place, and it is why this rule is written down here rather than left to the code.
 *
 * <h2>Order is preserved, and it is deliberate</h2>
 *
 * <p>{@link LinkedHashMap} rather than a plain one, so an error message naming the keys reports them in the order a pack author
 * wrote them. The deserializer reports keys it could not build a leaf for, and a stable order means that message does not
 * change between runs for reasons unrelated to their file.
 */
public final class NbtCompoundPattern implements NbtPattern {

    private final Map<String, NbtPattern> keys;

    public NbtCompoundPattern(Map<String, NbtPattern> keys) {
        this.keys = new LinkedHashMap<>(keys);
    }

    /** @return the key-to-leaf map, unmodifiable; the deserializer walks it to report unbuildable leaves */
    public Map<String, NbtPattern> keys() {
        return java.util.Collections.unmodifiableMap(keys);
    }

    public boolean isEmpty() {
        return keys.isEmpty();
    }

    @Override
    public boolean matches(Tag actual) {
        if (!(actual instanceof CompoundTag compound)) {
            // A pattern describing a compound does not match a tag that is not one. Not an error: a filter that wanted a
            // number and found a string simply does not match.
            return false;
        }
        for (Map.Entry<String, NbtPattern> entry : keys.entrySet()) {
            if (!compound.contains(entry.getKey())) {
                return false;
            }
            Tag found = compound.get(entry.getKey());
            if (found == null || !entry.getValue().matches(found)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return keys.toString();
    }
}
