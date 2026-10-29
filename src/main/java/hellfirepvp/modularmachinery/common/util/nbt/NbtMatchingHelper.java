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
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;




























 
public final class NbtMatchingHelper {

    private NbtMatchingHelper() {
    }

    /**
     * The entry point a requirement uses.
     *
     * @param pattern the requirement's NBT pattern, or {@code null} when it constrains nothing
     * @param stackTag the stack's compound tag, or {@code null} when the stack has none
     * @return whether the stack satisfies the pattern
     */
    public static boolean matchNbtCompound(@Nullable NbtPattern pattern, @Nullable CompoundTag stackTag) {
        // Rule 1: no pattern constrains nothing.
        if (pattern == null) {
            return true;
        }
        // Rule 2: no tag on the stack satisfies only an empty compound pattern -- NOT any pattern.
        if (stackTag == null) {
            return pattern instanceof NbtCompoundPattern compound && compound.isEmpty();
        }
        return pattern.matches(stackTag);
    }

    /**
     * Equality for the leaves a pattern does not specialise: byte arrays, int arrays and long arrays.
     *
     * <p>Upstream compares {@code NBTTagByteArray} and {@code NBTTagIntArray} with {@code Arrays.equals} and falls through to
     * {@code equals} for everything else. {@code LongArrayTag} is not in upstream's list because 1.12.2's
     * {@code NBTTagLongArray} does not exist there; it does on 1.20.1, so it is handled the same way rather than falling
     * through to an {@code equals} that would also be correct but would report a mismatch as an opaque false.
     */
    public static boolean equalArrays(Tag pattern, Tag actual) {
        if (pattern instanceof net.minecraft.nbt.ByteArrayTag p
                && actual instanceof net.minecraft.nbt.ByteArrayTag a) {
            return Arrays.equals(p.getAsByteArray(), a.getAsByteArray());
        }
        if (pattern instanceof net.minecraft.nbt.IntArrayTag p
                && actual instanceof net.minecraft.nbt.IntArrayTag a) {
            return Arrays.equals(p.getAsIntArray(), a.getAsIntArray());
        }
        if (pattern instanceof net.minecraft.nbt.LongArrayTag p
                && actual instanceof net.minecraft.nbt.LongArrayTag a) {
            return Arrays.equals(p.getAsLongArray(), a.getAsLongArray());
        }
        return pattern.equals(actual);
    }

    /**
     * Builds a leaf for a plain value that is not a container.
     *
     * @param tag the pattern value as parsed from JSON
     * @return a leaf that compares by equality
     */
    public static NbtPattern exact(Tag tag) {
        return actual -> {
            // Arrays need the content comparison; everything else is equals, which is what upstream falls through to for
            // every tag it does not specialise.
            if (tag instanceof net.minecraft.nbt.ByteArrayTag
                    || tag instanceof net.minecraft.nbt.IntArrayTag
                    || tag instanceof net.minecraft.nbt.LongArrayTag) {
                return equalArrays(tag, actual);
            }
            return tag.equals(actual);
        };
    }

    /** @return a leaf matching any of the given alternatives; empty alternatives match nothing */
    public static NbtPattern anyOf(List<NbtPattern> alternatives) {
        List<NbtPattern> copy = List.copyOf(alternatives);
        return actual -> {
            for (NbtPattern alternative : copy) {
                if (alternative.matches(actual)) {
                    return true;
                }
            }
            return false;
        };
    }
}
