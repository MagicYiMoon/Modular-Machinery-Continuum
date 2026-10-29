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
