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
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.tiles.me;































































 
public enum WorkModeSetting {

    /** Upstream U785. Push everything at once -- the container never refuses. */
    DEFAULT,
    /** Upstream U786. The container must be empty before the next push. */
    BLOCKING_MODE,
    /** Upstream U787. The next push waits for the current pattern's craft to finish. */
    CRAFTING_LOCK_MODE,
    /** Upstream U788. Only the pattern already in progress may keep storing. */
    ENHANCED_BLOCKING_MODE,
    /** Upstream U789. Independent input container for each pattern slot. */
    ISOLATION_INPUT;

    /**
     * Reads a persisted ordinal <b>without ever throwing</b>.
     *
     * <p>Upstream's {@code WorkModeSetting.values()[compound.getByte("workMode")]} (U544) throws
     * {@code ArrayIndexOutOfBoundsException} on a byte outside the range, out of {@code load()}, while the chunk that
     * holds the block is loading. A missing or damaged tag is a thing that happens to saved worlds, and the cost of the
     * guard is one comparison; the default is where a provider with no saved mode already starts.</p>
     *
     * @param ordinal the persisted ordinal, as a signed byte
     * @return the mode with that ordinal, or {@link #DEFAULT} when there is none
     */
    public static WorkModeSetting fromOrdinal(int ordinal) {
        WorkModeSetting[] all = values();
        return ordinal < 0 || ordinal >= all.length ? DEFAULT : all[ordinal];
    }

    /**
     * The ordinal after {@code currentOrdinal}, wrapping at the end.
     *
     * <p><b>Upstream has no such method and needs none:</b> its button is a {@code ButtonElements} that walks its own
     * registration order (G104-G155, G137-G154), and its five sprites are laid out around a fixed origin
     * ({@code 140 +- 18n}, G106-G113). This is the port's equivalent of that order, and <b>it is derived from
     * {@code values().length} rather than from a literal 5</b> -- the difference between "five modes, because the enum
     * says so" and "five modes, because someone counted once".</p>
     *
     * <p>It takes an ORDINAL rather than a constant because the client's copy of the mode arrives as one integer in a
     * data slot, and a caller that had to turn that int into a constant first in order to ask for the next one would
     * have to write the out-of-range case twice.</p>
     *
     * @param currentOrdinal the ordinal to advance from; out of range reads as {@link #DEFAULT}, the same reading
     *                       {@link #fromOrdinal(int)} gives it
     * @return the next ordinal, never out of range
     */
    public static int nextOrdinal(int currentOrdinal) {
        return (fromOrdinal(currentOrdinal).ordinal() + 1) % values().length;
    }
}
