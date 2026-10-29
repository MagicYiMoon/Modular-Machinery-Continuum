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
package hellfirepvp.modularmachinery.common.crafting;

import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;

/**
 * One requirement a machine recipe imposes on a machine.
 *
 * <p><b>1.12.2 → 1.20.1.</b> Upstream modelled this as a Gson-deserialised class hierarchy under
 * {@code common.crafting.requirement} (energy, item, fluid, plus a long tail of mod-specific types such
 * as {@code aspect}, {@code mana}, {@code starlight}). The shape survives; what changes is the
 * underlying game API each implementation talks to — {@code IItemHandler} for items,
 * {@code IFluidHandler} for fluids, and Forge Energy ({@code IEnergyStorage}) rather than the mod's own
 * long-typed {@code IEnergyHandler}.
 *
 * <h2>The design contract that matters</h2>
 *
 * <p>A requirement is <b>not</b> a recipe ingredient in the vanilla sense, and this mod deliberately
 * does not route it through {@code RecipeType}/{@code RecipeSerializer}. The difference is
 * <b>consumption semantics</b>:
 *
 * <ul>
 *   <li>a vanilla recipe consumes everything at once, atomically;</li>
 *   <li>a Modular Machinery recipe <b>runs over time</b> — energy is drawn per tick, inputs are drawn
 *       gradually, and outputs appear at the end. A recipe can therefore be interrupted mid-way, and
 *       partial consumption must be handled.</li>
 * </ul>
 *
 * <p>That is why {@link #canStart} and {@link #canFinish} are separate: a machine must be able to ask
 * "could this recipe run to completion given current contents?" <b>before stealing any input</b>,
 * because starting a recipe that cannot finish destroys the player's items.
 *
 * <p>Both take a {@link MachineContext} rather than a bare position: the context resolves the machine's
 * components once, so a requirement inspecting several busses does not re-resolve capabilities per
 * lookup, and never holds a handler that may since have been invalidated.
 */
public interface Requirement {

    int PRIORITY_WEIGHT_ENERGY = 50_000_000;
    int PRIORITY_WEIGHT_ITEM = 50_000;
    int PRIORITY_WEIGHT_FLUID = 100;

    /** Source ComponentRequirement contract. Wrappers do not inherit the wrapped requirement's weight. */
    default int getSortingWeight() { return 0; }

    /** Tick at which this requirement is triggered; zero means the normal recipe start/finish phase. */
    default int getTriggerTime() { return 0; }

    /** Whether a triggered requirement may be attempted more than once at its trigger tick. */
    default boolean isTriggerRepeatable() { return false; }

    /** Configures the delayed trigger; extension requirements may reject this feature explicitly. */
    default void setTriggerTime(int tick) {
        if (tick != 0) throw new IllegalArgumentException("triggerTime is unsupported by " + typeId());
    }

    default void setTriggerRepeatable(boolean repeatable) {
        if (repeatable) throw new IllegalArgumentException("triggerRepeatable is unsupported by " + typeId());
    }

    /** Whether an output may be placed best-effort without reserving output room first. */
    default boolean isIgnoreOutputCheck() { return false; }

    /** Configures upstream's {@code ignoreOutputCheck} recipe flag. */
    default void setIgnoreOutputCheck(boolean ignored) {
        if (ignored) throw new IllegalArgumentException("ignoreOutputCheck is unsupported by " + typeId());
    }

    /** Null means all eligible components; a name requires an exact structure selector match. */
    default String getSelectorTag() { return null; }

    /** Extension requirements must explicitly support selectors rather than silently ignore them. */
    default void setSelectorTag(String tag) {
        if (tag != null && !tag.isEmpty()) throw new IllegalArgumentException("Selector unsupported by " + typeId());
    }

    /** @return this requirement's {@code io-type} */
    IOType getIOType();

    /**
     * Whether the machine's current contents satisfy this requirement enough to <b>begin</b>.
     *
     * <p>For an input this means the full amount is present; for an output it means there is room.
     */
    boolean canStart(MachineContext context);

    /**
     * Whether this recipe could still complete if it were started now.
     *
     * <p>Separated from {@link #canStart} because the two genuinely differ: a machine may be able to
     * start but have nowhere to put the result, and discovering that only at completion time would mean
     * having already consumed the inputs.
     *
     * <p>The default delegates to {@link #canStart} for requirements where the two coincide (energy
     * drawn per tick, for instance, only has to be satisfiable at the start).
     */
    default boolean canFinish(MachineContext context) {
        return canStart(context);
    }

    // ================================================================================================
     
    //
    // `RecipeModifier` -- the machine-modifier system the shipped JSON already uses -- needs exactly two
    // things of a requirement, and until now this interface exposed neither:
    //
    //   * a TYPE IDENTITY, so `"target": "modularmachinery:item"` can name the requirements it applies to;
    //   * a VALUE it can be scaled by, so `{"operation": 1, "multiplier": 2.0}` can double it.
    //
    // That is all. Upstream's `RequirementType` hierarchy with its twelve per-shape deserialisers exists
    // because upstream needed a registry keyed by requirement shape and a deserialiser per shape -- needs
    // this port meets in its own loader, and which 1.20.1's registry API would force to be rewritten anyway
    // (`IForgeRegistryEntry.Impl` no longer exists). So the SHAPE upstream provides is kept and the twelve
    // subclasses are not: the type identity and the scaler are, because those are what the modifiers read.
    // ================================================================================================

    /** Add [value] to the requirement's magnitude. Upstream: RecipeModifier.OPERATION_ADD. */
    int OPERATION_ADD = 0;

    /**
     * Multiply the requirement's magnitude by [value]. Upstream: RecipeModifier.OPERATION_MULTIPLY.
     *
     * <p>These two are the whole of upstream's operation vocabulary, and the shipped JSON uses the raw ints
     * -- `"operation": 1` is MULTIPLY because that is what `RecipeModifier.OPERATION_MULTIPLY` declares.
     * Keeping the numbering identical means the JSON means what upstream says it means.
     */
    int OPERATION_MULTIPLY = 1;

    /**
     * This requirement's type identity, as the JSON names it.
     *
     * <p>Matches upstream's `RequirementType` registry names, so a modifier's `"target"` resolves to the same
     * requirements it would upstream: {@code modularmachinery:item}, {@code modularmachinery:fluid},
     * {@code modularmachinery:energy}.
     */
    String typeId();

    /**
     * This requirement with a modifier applied.
     *
     * @param value     the multiplier or addend, from the JSON's {@code multiplier}
     * @param operation {@link #OPERATION_ADD} or {@link #OPERATION_MULTIPLY}
     * @param io        the IO direction the modifier targets; a modifier only applies when this matches
     * @param chance    whether the modifier targets a probability rather than a magnitude. Only requirements
     *                  that HAVE a probability act on it; the others return themselves unchanged, which is
     *                  what upstream's chance-flag does by having no probability to scale.
     * @return a new requirement, or {@code this} when the modifier does not apply
     */
    default Requirement scaled(float value, int operation, IOType io, boolean chance) {
        return this;
    }

    /**
     * Applies {@code operation} to {@code base} with {@code value}. Shared so the three implementations
     * cannot disagree about what "operation 1" means.
     */
    static float apply(float base, float value, int operation) {
        return operation == OPERATION_MULTIPLY ? base * value : base + value;
    }

    /** @return a short human-readable description, for diagnostics and later the JEI page */
    default String describe() {
        return getClass().getSimpleName();
    }

    





















 
    default String describeFailure(MachineContext context) {
        return null;
    }

    








































 
    default boolean canEmit(MachineContext context, int parallelism) {
        if (getIOType() == IOType.OUTPUT && isIgnoreOutputCheck()) return true;
        return canFinish(context);
    }

    /**
     * Per-tick requirement lifecycle, corresponding to the source mod's
     * {@code ComponentRequirement.PerTick}.  The port does not expose the old
     * {@code ProcessingComponent} list; a {@link MachineContext} is the 1.20.1
     * capability view and keeps all world access on the server thread.
     *
     * <p>Implementations that need grouped/atomic access can return {@code true}
     * from {@link #isBatchPerTick()} and perform the operation in
     * {@link #doIOTick(MachineContext, int)}.  The built-in fluid-rate requirement
     * uses the shared {@code FluidTickIO} planner for that purpose.</p>
     */
    interface PerTick extends Requirement {
        default void startIOTick(MachineContext context, float durationMultiplier) {}

        /** Called before and after an IO tick; return a failure key from the post-call when needed. */
        default String resetIOTick(MachineContext context) { return null; }

        /** Performs this requirement's one-tick IO and returns a failure key, or {@code null}. */
        default String doIOTick(MachineContext context, int parallelism) { return null; }

        /** Grouped requirements are planned together and are not invoked through individual component loops. */
        default boolean isBatchPerTick() { return false; }
    }

    


































 
    interface Parallelizable extends Requirement {

        /**
         * @param maxParallelism the ceiling to stay within; the return value must not exceed it
         * @param siblings       the recipe's every requirement, so a requirement sharing its output room with
         *                       others can account for them; may be null or empty, which means "only me"
         * @return how many copies of this requirement the machine can actually supply
         */
        int getMaxParallelism(hellfirepvp.modularmachinery.common.machine.MachineContext context,
                              int maxParallelism, java.util.List<Requirement> siblings);

        int getParallelism();

        /**
         * Sets the multiplier. AN EXEMPT REQUIREMENT MUST IGNORE THIS -- see setParallelizeUnaffected, which is the
         * upstream mechanism at ComponentRequirement U471-U489 and U581-U599.
         */
        void setParallelism(int parallelism);

        /** @return true when this requirement refuses to be parallelized at all */
        boolean isParallelizeUnaffected();

        /**
         * Marks this requirement as exempt, AND RESETS ITS PARALLELISM TO ONE -- upstream does both in one method
         * (U472-U477), and the reset is what makes the exemption hold even if it is toggled after a value was set.
         */
        void setParallelizeUnaffected(boolean unaffected);
    }
}
