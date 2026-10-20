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

/**
 * The ME pattern provider's work mode: <b>upstream's own enum, name for name and in upstream's own declaration
 * order</b> ({@code MEPatternProvider.WorkModeSetting}, upstream U784-U790).
 *
 * <h2>Why this is its own FILE rather than a nested enum on the tile, which is where upstream has it</h2>
 *
 * <p><b>A nested enum's static methods still name the outer class.</b> {@code Outer.Nested.values()} compiles to a
 * reference to {@code Outer$Nested}, whose own {@code InnerClasses} attribute points back at {@code Outer} -- so
 * loading the enum loads {@code TileMEPatternProvider}, and THAT class's signature implements
 * {@code appeng.helpers.patternprovider.PatternProviderLogicHost}. On a server without AE2 the load fails with
 * {@code NoClassDefFoundError: appeng/helpers/patternprovider/PatternProviderLogicHost}.</p>
 *
 * <p>That is not a hypothesis: this file exists because the first version of PLAN 737's work-mode check nested the
 * enum on the tile, and the standalone self-test died with exactly that exception, out of
 * {@code ServerSelfTestTrigger} -- a crash on the DEDICATED SERVER of a mod whose entire ME subsystem is optional. The
 * port's own boundary rules (§452 method level, §478 registry level, §481 client level, §712 self-test level) all say
 * the same thing, and this is the file-level instance of it: <b>a type the self-test must name has to be
 * AE2-free</b>.</p>
 *
 * <p>Upstream can nest it because upstream has no configuration without AE2. This port does, so the enum moves and the
 * tile holds a field of it -- which is the whole change, and it leaves every other decision upstream's.</p>
 *
 * <h2>The order is a SAVE FORMAT, not a preference</h2>
 *
 * <p>Upstream persists the mode as {@code (byte) workMode.ordinal()} (U587) and restores it as
 * {@code WorkModeSetting.values()[byte]} (U544). A reordered enum would silently reinterpret every saved provider on
 * the next load -- and would also move every work-mode icon, because the screen derives each sprite's U from the
 * ordinal ({@code 140 +- 18n}, G106-G113).</p>
 *
 * <h2>What each mode does, from the bodies rather than from the names</h2>
 *
 * <table>
 *   <caption>upstream's five modes and each one's effect</caption>
 *   <tr><th>constant</th><th>upstream</th><th>what its body does</th></tr>
 *   <tr><td>{@link #DEFAULT}</td><td>U785</td><td>no gate: {@code isBusy} is false for it (U307-U308) and
 *       {@code acceptsPattern} is true for it (U280), so pushed items accumulate without limit -- the user's
 *       "一次性全发配"</td></tr>
 *   <tr><td>{@link #BLOCKING_MODE}</td><td>U786</td><td>{@code isBusy} is TRUE while the container is non-empty
 *       (U308): the provider refuses the next push until the machine has consumed what it was given, which is how two
 *       recipes stop sharing one buffer -- the user's "阻挡模式 / 配方存储相互独立"</td></tr>
 *   <tr><td>{@link #CRAFTING_LOCK_MODE}</td><td>U787</td><td>{@code isBusy} is true while {@code !machineCompleted}
 *       (U307); {@code pushPattern} sets {@code machineCompleted = workMode != CRAFTING_LOCK_MODE} (U271), i.e. leaves
 *       it FALSE, and only a finished craft sets it back (U682-U690, U360) -- the user's
 *       "做完当前样板配方再发配下一样板配方物品"</td></tr>
 *   <tr><td>{@link #ENHANCED_BLOCKING_MODE}</td><td>U788</td><td>{@code isBusy} does NOT include it; instead
 *       {@code acceptsPattern} refuses a push while the container is non-empty UNLESS the incoming pattern equals
 *       {@code currentPattern} (U280), and {@code handleNewPattern} remembers that pattern while the mode is on
 *       (U283-U291). The recipe in progress keeps storing; a different one is refused.</td></tr>
 *   <tr><td>{@link #ISOLATION_INPUT}</td><td>U789</td><td>{@code provideComponent()} returns <b>null</b> (U180) and
 *       {@code provideComponents()} returns the <b>36 per-pattern containers</b> (U203-U204), each with its own group
 *       id (U128-U165); {@code pushPattern} routes the pushed stacks to the container of the matching pattern
 *       (U235-U242). Its description key says the same: "Set up independent containers for each pattern slot, and
 *       recipe checks are isolated from each other, group settings will be ignored".</td></tr>
 * </table>
 *
 * <h2>{@link #ISOLATION_INPUT} compatibility</h2>
 *
 * <p>In the source mod this mode hides the shared component and exposes 36 independent
 * {@code InfItemFluidHandler}s, one for each pattern slot.  The 1.20.1 port keeps that contract through the
 * AE2-free {@code PatternIsolationProvider} bridge: {@code provideComponent()} is represented by the normal
 * capability, while {@code provideComponents()} is represented by the 36 handlers returned to
 * {@code MachineContext}.  A pushed pattern is routed to the handler at the matching pattern index.</p>
 */
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
