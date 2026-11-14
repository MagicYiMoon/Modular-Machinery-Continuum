/*******************************************************************************
 * HellFirePvP / Modular Machinery 2019
 *
 * This project is licensed under GNU GENERAL PUBLIC LICENSE Version 3.
 * The source code is available on github: https://github.com/HellFirePvP/ModularMachinery
 * For further details, see the License file there.
 *******************************************************************************
 * PORTED TO MINECRAFT 1.20.1 (Forge 47.3.0)
 *
 * This file is part of a derivative work and has been MODIFIED: it was rewritten for the
 * 1.20.1 API (official/Mojang mappings, Forge 47.3.0, mods.toml metadata, and the 1.20.1
 * vanilla and Forge signatures), and therefore differs substantially from the upstream
 * original named above. Modifications made 2026-08 .. 2026-09.
 *
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.machine;

import hellfirepvp.modularmachinery.common.util.nbt.NbtPattern;
import java.util.ArrayList;
import java.util.List;

/**
 * A machine definition's block pattern, in controller-relative coordinates.
 *
 * <p>Upstream compressed this into a {@code BlockArray} of dense metadata ints and cached lookups in
 * {@code BlockArrayCache}. On 1.20.1 there is no metadata, so a pattern entry is a block identifier
 * plus an <b>optional blockstate</b>, and the structure is kept as a sparse list of parts rather than
 * a dense cube.
 *
 * <p><b>Why sparse.</b> A large multiblock can enclose a 30×30×30 volume while declaring only a shell
 * of a few hundred parts. The 1.12.2 dense array allocated the whole cube and filled the interior with
 * a sentinel; a sparse list costs only what the JSON declares, and every part carries the origin
 * coordinates needed to tell the player where a mismatch is.
 */
public final class MachinePattern {

    /**
     * One declared block of the pattern.
     *
     * @param x        offset from the controller along X
     * @param y        offset from the controller along Y
     * @param z        offset from the controller along Z
     * @param elements the unresolved element name from the JSON, e.g. {@code minecraft:iron_bars},
     *                 {@code modularmachinery:blockcasing}, or a variable such as
     *                 {@code casings_decorative}
     */
    public record Part(int x, int y, int z, String elements, String selectorTag, StructureNbt structureNbt) {
        public Part(int x, int y, int z, String elements) { this(x, y, z, elements, null, null); }
        public Part(int x, int y, int z, String elements, String selectorTag) {
            this(x, y, z, elements, selectorTag, null);
        }

        public Part {
            if (selectorTag != null && selectorTag.isEmpty()) selectorTag = null;
        }

        public NbtPattern nbtPattern() { return structureNbt == null ? null : structureNbt.matching(); }
        public net.minecraft.nbt.CompoundTag previewNbt() {
            return structureNbt == null ? null : structureNbt.preview();
        }
    }

    private final List<Part> parts = new ArrayList<>();

    /** Bounding box in controller-relative coordinates, inclusive. Used to size previews and scans. */
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private boolean boundsInitialised = false;

    public void addPart(Part part) {
        parts.add(part);
        if (!boundsInitialised) {
            minX = maxX = part.x();
            minY = maxY = part.y();
            minZ = maxZ = part.z();
            boundsInitialised = true;
        } else {
            minX = Math.min(minX, part.x());
            minY = Math.min(minY, part.y());
            minZ = Math.min(minZ, part.z());
            maxX = Math.max(maxX, part.x());
            maxY = Math.max(maxY, part.y());
            maxZ = Math.max(maxZ, part.z());
        }
    }

    public List<Part> getParts() {
        return parts;
    }

    /**
     * Adds a part <b>INSTEAD OF</b> whatever this pattern already declares at the same offset (PLAN §750).
     *
     * <p><b>THIS IS UPSTREAM'S SEMANTIC, AND IT IS WHAT MAKES THE DYNAMIC SECTIONS PART OF ONE PATTERN.</b>
     * {@code DynamicPattern.addPatternToBlockArray} (upstream {@code github.kasuminova.mmce.common.util.DynamicPattern}
     * L184/L203) writes each section's and the cap's blocks into the pattern with {@code toAdd.addBlock(...)}, and
     * {@code BlockArray.addBlock} indexes by {@link BlockPos} in a map — so a section cell REPLACES the machine's own
     * part at that exact offset. {@code Assembly Line} shows why that matters: the wall's part
     * {@code (0,-1,0)} is {@code blockcasing@5}, while the first section's template part at the same offset is
     * {@code casings_item_input}, i.e. a {@code blockinputbus}. Keeping both would ask the matcher for two block
     * states at ONE world position, which no structure can satisfy.
     *
     * <p>{@link #addPart} appends an already-resolved cell. The JSON loader first expands coordinate arrays and
     * resolves duplicate offsets in a position-indexed map. Dynamic merging must also replace static cells;
     * appending a section on top of one would demand two block states at a single world position.
     *
     * @param part the part that wins at its offset
     */
    public void addPartReplacing(Part part) {
        parts.removeIf(existing -> existing.x() == part.x() && existing.y() == part.y()
                && existing.z() == part.z());
        addPart(part);
    }

    public int size() {
        return parts.size();
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    /** @return volume of the bounding box, for diagnostics and preview allocation */
    public long getBoundsVolume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }
}
