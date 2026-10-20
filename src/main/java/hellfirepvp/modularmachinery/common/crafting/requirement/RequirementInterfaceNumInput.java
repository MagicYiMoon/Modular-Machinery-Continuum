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
package hellfirepvp.modularmachinery.common.crafting.requirement;

import hellfirepvp.modularmachinery.Modularmachinery;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.DynamicMachine;
import hellfirepvp.modularmachinery.common.machine.IOType;
import hellfirepvp.modularmachinery.common.machine.MachineContext;
import hellfirepvp.modularmachinery.common.tiles.TileSmartInterface;
import hellfirepvp.modularmachinery.common.util.SmartInterfaceType;
import net.minecraft.network.chat.Component;

/**
 * Requires a smart interface to be publishing a NUMBER within a range.
 *
 * <p>Upstream {@code RequirementInterfaceNumInput.java}, 112 lines, read in full. This is the consumer end of
 * the smart interface channel (PLAN 663, B4) and it is the reason the interface needed a provider, a
 * declared-type list and a publish path at all: without a reader the whole channel is data nobody looks at.</p>
 *
 * <h2>What it READS, and what it does when the interface is absent or the type is wrong</h2>
 *
 * <p>Upstream's {@code canStartCrafting} (U75-U90) is short and every branch of it is kept:</p>
 *
 * <pre>
 * provider.getMachineData(type.getType())   U77  -- by the DECLARED TYPE's name
 * if (data == null)                          U78  -- interface absent, or publishing another type
 *         CraftCheck.failure("component.missing.modularmachinery.interface.number")
 * float value = data.getValue();             U81
 * if (value &gt;= minValue &amp;&amp; value &lt;= maxValue)  U82  -- INCLUSIVE both ends
 *         CraftCheck.success()
 * otherwise                                  U86-U89
 *         the declared type's notEqualMessage, or "craftcheck.failure.interface.number.notequal"
 * </pre>
 *
 * <p><b>SO THE ABSENT CASE AND THE WRONG-TYPE CASE ARE THE SAME CASE, and that is upstream's design rather
 * than an omission:</b> the lookup is by name, and a machine that does not declare the name never publishes
 * it (the binding walk is gated on the machine's own declared list), so "no datum" is exactly "no interface
 * is publishing this". Upstream additionally rejects the requirement EARLIER when the machine does not
 * declare the type at all — {@code RecipePrimer.addSmartInterfaceDataInput} U273-U277 logs
 * {@code "SmartInterfaceType <x> Not Found!"} and appends nothing. This port reaches the same place from the
 * other side: the JSON type factory cannot see the recipe's machine (see below), so an undeclared name is
 * simply never published, and the requirement reports the missing-interface message for ever. Both messages
 * are the upstream lang strings, read from the mod's own lang files rather than retyped.</p>
 *
 * <h2>RULE A0 — THREE DEVIATIONS, EACH FORCED BY A SIGNATURE THAT DOES NOT EXIST HERE</h2>
 *
 * <p><b>1. THE DESCRIPTOR IS NOT RESOLVED AT CONSTRUCTION.</b> Upstream U28-U33 takes the machine name and
 * resolves the {@code SmartInterfaceType} out of {@code MachineRegistry} in the constructor. This port's
 * extension point is {@code RequirementType#createRequirement(IOType, JsonObject)} — the requirement's own
 * JSON and nothing else, with no machine name and no recipe path — so the descriptor is resolved where a
 * machine IS available: {@link #resolveType(DynamicMachine)}, called from
 * {@link #describeFailure(MachineContext)} through {@link MachineContext#getMachine()} and from the JEI
 * tooltip, which receives the recipe and therefore its machine's name. What this costs: a typo in the type
 * name is caught when the recipe runs rather than when the file loads. What it does not cost: any behaviour
 * at runtime, because an undeclared name could never be published in either version.</p>
 *
 * <p><b>2. THE FAILURE MESSAGE TRAVELS ON THE PORT'S OWN CHANNEL INSTEAD OF {@code CraftCheck}.</b>
 * {@code CraftCheck} does not exist here (PLAN 521 measured its absence, and the port's substitute is
 * {@code RecipeCraftingContext.describeBlockers}), and the port's {@code Requirement} contract is boolean —
 * a requirement cannot return a message from {@code canStart}. So this class implements
 * {@code Requirement#describeFailure(MachineContext)}, which the blocker builder consults before falling back
 * to {@code describe()}. Upstream's two messages are carried VERBATIM as the payload, followed by the
 * observed value and the range — a diagnostic suffix this port adds on purpose, because
 * {@code describeBlockers} is what a player reads when a machine refuses to start and "Different values for
 * Smart Data Interface inputs!" alone does not say which value.</p>
 *
 * <p><b>3. THE IO DIRECTION IS ALWAYS INPUT, AND A FILE THAT SAYS OTHERWISE IS REJECTED RATHER THAN
 * OBEYED.</b> Upstream's three constructors all pass {@code IOType.INPUT} (U29/U36/U43); there is no output
 * form of this requirement, because reading a published value is not producing anything. The JSON still has
 * to carry {@code io-type} — this port's loader requires it for every requirement (PLAN 311) — so the type
 * factory refuses a non-input one with a message instead of building a requirement whose direction the engine
 * would then treat as a product.</p>
 *
 * <h2>FACTS ONLY THE UPSTREAM BODIES REVEALED, KEPT BECAUSE THEY ARE BEHAVIOUR</h2>
 *
 * <ul>
 *   <li><b>IT IS IMMUNE TO RECIPE MODIFIERS.</b> {@code deepCopyModified(List<RecipeModifier>)} (U98-U100)
 *       ignores its argument and returns {@code deepCopy()}. A recipe modifier targeting this requirement
 *       changes nothing upstream, so {@link #scaled} keeps the interface default — returning {@code this} —
 *       and no modifier can widen the range a pack declared.</li>
 *   <li><b>IT HAS NO JEI COMPONENT.</b> {@code provideJEIComponent()} returns {@code null} (U108-U111), so
 *       upstream's JEI presence is entirely the tooltip produced by {@code TooltipInterfaceNumberInput}. The
 *       port has the same split: no slot is added for this requirement, only text lines.</li>
 *   <li><b>THE RANGE IS INCLUSIVE AT BOTH ENDS</b> (U82), which is why the boundary values are asserted in
 *       the self-test rather than assumed.</li>
 * </ul>
 */
public final class RequirementInterfaceNumInput extends hellfirepvp.modularmachinery.common.crafting.SelectorRequirement implements Requirement {

    /** Upstream's registry key, read from {@code RequirementTypesMM.KEY_INTERFACE_NUMBER_INPUT}. */
    public static final String TYPE_ID = Modularmachinery.MODID + ":interface_number_input";

    /**
     * The DECLARED type's name this requirement reads — upstream's {@code SmartInterfaceType.type}.
     *
     * <p>Held as the name rather than as the descriptor for the reason given in the class javadoc; see
     * {@link #resolveType(DynamicMachine)} for the descriptor lookup, which is where upstream did it too
     * (from the machine).</p>
     */
    private final String interfaceType;

    private final float minValue;
    private final float maxValue;

    public RequirementInterfaceNumInput(String interfaceType, float minValue, float maxValue) {
        this.interfaceType = interfaceType;
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    /**
     * Upstream's single-value constructor (U42-U47): the range collapses to one value.
     *
     * <p>Kept as a constructor rather than as a caller-side {@code (v, v)} because the JSON has the same two
     * shapes — {@code min} alone, or {@code min} and {@code max} — and {@code InterfaceNumberRequirementType}
     * routes them here, so "min only" and "min == max" cannot drift apart.</p>
     */
    public RequirementInterfaceNumInput(String interfaceType, float value) {
        this(interfaceType, value, value);
    }

    /** @return the declared type's name this requirement reads */
    public String getInterfaceType() {
        return interfaceType;
    }

    public float getMinValue() {
        return minValue;
    }

    public float getMaxValue() {
        return maxValue;
    }

    /**
     * The descriptor this requirement's type name refers to on {@code machine}.
     *
     * <p>Upstream resolves the same object in its constructor out of {@code MachineRegistry} (U30). Returns
     * {@code null} when the machine does not declare the name — see the class javadoc's second point, which
     * is also why the JEI tooltip has to handle that case rather than assume.</p>
     */
    public SmartInterfaceType resolveType(DynamicMachine machine) {
        return machine == null ? null : machine.getSmartInterfaceType(interfaceType);
    }

    /** {@inheritDoc} Always INPUT: upstream's three constructors all say so, and there is no output form. */
    @Override
    public IOType getIOType() {
        return IOType.INPUT;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The read, in upstream's order: ask each of the machine's interfaces for a datum published under this
     * requirement's type name; the FIRST one found is the subject (upstream receives exactly one provider per
     * requirement, chosen by {@code isValidComponent} U61-U71 which asks the same question). No datum anywhere
     * means the requirement is not satisfied, which is upstream's {@code data == null} branch.</p>
     *
     * <p>Once a datum is found the comparison is upstream U82 verbatim: inclusive at both ends.</p>
     */
    @Override
    public boolean canStart(MachineContext context) {
        TileSmartInterface.Data data = findData(context);
        if (data == null) {
            return false;
        }
        float value = (float) data.getNumber();
        return value >= minValue && value <= maxValue;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Upstream's two failure messages, in its own order (U78-U79 then U86-U89): the missing-interface
     * message when nothing publishes this type, and the declared type's custom message — falling back to the
     * built-in one — when a value is out of range.</p>
     */
    @Override
    public String describeFailure(MachineContext context) {
        TileSmartInterface.Data data = findData(context);
        if (data == null) {
            return Component.translatable("component.missing.modularmachinery.interface.number").getString()
                    + " (interface type '" + interfaceType + "' is not published by this machine)";
        }
        float value = (float) data.getNumber();
        if (value >= minValue && value <= maxValue) {
            return null;
        }
        SmartInterfaceType type = resolveType(context.getMachine());
        String customMessage = type == null ? "" : type.getNotEqualMessage();
        String message = customMessage.isEmpty()
                ? Component.translatable("craftcheck.failure.interface.number.notequal").getString()
                : customMessage;
        // The observed value and the declared range are appended rather than substituted: upstream's message
        // is a complete sentence a pack may have replaced, and rewriting it would lose the pack's wording.
        return message + " (interface '" + interfaceType + "' = " + value
                + ", expected " + minValue + ".." + maxValue + ")";
    }

    /**
     * The first datum published under this requirement's type name among the machine's smart interfaces.
     *
     * <p>Reads through {@code provideComponent()} — the provider upstream hands a requirement — rather than
     * through a second accessor on the tile, so the requirement and the screen cannot disagree about which
     * data a provider exposes.</p>
     *
     * @return the datum, or {@code null} when no interface of this machine publishes this type
     */
    private TileSmartInterface.Data findData(MachineContext context) {
        for (TileSmartInterface iface : context.forSelector(getSelectorTag()).smartInterfaces()) {
            TileSmartInterface.Data data = iface.provideComponent().getMachineData(interfaceType);
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    @Override
    public String describe() {
        return "interface '" + interfaceType + "' in [" + minValue + ", " + maxValue + "] (" + getIOType() + ")";
    }

    @Override
    public String typeId() {
        return TYPE_ID;
    }

    // scaled(...) is deliberately NOT overridden: upstream's deepCopyModified(U98-U100) ignores its modifiers
    // and returns an unmodified copy, so the interface default returns `this` unchanged. See the class javadoc.
}
