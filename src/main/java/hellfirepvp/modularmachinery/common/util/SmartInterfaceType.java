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
package hellfirepvp.modularmachinery.common.util;




























































 
public final class SmartInterfaceType implements Comparable<SmartInterfaceType> {

    /** The type's name, unique within one machine. Upstream U12. */
    private final String type;

    /** The value an interface bound to this type starts with. Upstream U14 and the 4-arg data ctor. */
    private final float defaultValue;

    /** GUI line above the value; empty means the default text. Upstream U16. */
    private String headerInfo = "";

    /** GUI format for the value itself; empty means the built-in one. Upstream U18. */
    private String valueInfo = "";

    /** GUI line below the value; empty means the default text. Upstream U20. */
    private String footerInfo = "";

    /** Shown instead of {@code craftcheck.failure.interface.number.notequal} when a value is out of range. Upstream U22. */
    private String notEqualMessage = "";

    /** A custom JEI line; empty means the built-in one. Upstream U24. */
    private String jeiTooltip = "";

    /**
     * How many format arguments {@link #jeiTooltip} takes.
     *
     * <p>Upstream's javadoc (U25-U30) is emphatic that this must match the tooltip's own format string, and
     * the DEFAULT IS 2 because every shipped instruction to a pack author shows two. The port keeps both the
     * number and the warning: {@code TooltipInterfaceNumberInput} reads it to choose between one and two
     * arguments, and any other value falls through to the built-in line rather than throwing.</p>
     */
    private int jeiTooltipArgsCount = 2;

    /**
     * Which type a NEW interface receives.
     *
     * <p>Upstream U31-U36: higher wins, and it only matters when a structure holds more interfaces than the
     * machine declares types — in that case the extra interfaces reuse the highest-priority type (upstream
     * {@code getFirstSmartInterfaceType}).</p>
     */
    private int priority = 0;

    public SmartInterfaceType(String type, float defaultValue) {
        this.type = type;
        this.defaultValue = defaultValue;
    }

    /** @return the name a recipe's interface requirement and this machine's declared map share */
    public String getType() {
        return type;
    }

    public float getDefaultValue() {
        return defaultValue;
    }

    public String getHeaderInfo() {
        return headerInfo;
    }

    public SmartInterfaceType setHeaderInfo(String headerInfo) {
        this.headerInfo = headerInfo;
        return this;
    }

    public String getValueInfo() {
        return valueInfo;
    }

    public SmartInterfaceType setValueInfo(String valueInfo) {
        this.valueInfo = valueInfo;
        return this;
    }

    public String getFooterInfo() {
        return footerInfo;
    }

    public SmartInterfaceType setFooterInfo(String footerInfo) {
        this.footerInfo = footerInfo;
        return this;
    }

    public String getNotEqualMessage() {
        return notEqualMessage;
    }

    public SmartInterfaceType setNotEqualMessage(String notEqualMessage) {
        this.notEqualMessage = notEqualMessage;
        return this;
    }

    public String getJeiTooltip() {
        return jeiTooltip;
    }

    public int getJeiTooltipArgsCount() {
        return jeiTooltipArgsCount;
    }

    public int getPriority() {
        return priority;
    }

    public SmartInterfaceType setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public SmartInterfaceType setJeiTooltip(String jeiTooltip, int argsCount) {
        this.jeiTooltip = jeiTooltip;
        this.jeiTooltipArgsCount = argsCount;
        return this;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>DESCENDING, AND THAT IS THE WHOLE MECHANISM:</b> upstream U131-U133 is
     * {@code another.priority - priority}, and the one caller sorts and takes the first
     * ({@code MachineFormation}'s port of upstream {@code TileMultiblockMachineController U926}). Reversing
     * this comparison would silently hand every new interface the LOWEST-priority type, with nothing
     * failing anywhere.</p>
     */
    @Override
    public int compareTo(SmartInterfaceType another) {
        return another.priority - priority;
    }

    @Override
    public String toString() {
        return "SmartInterfaceType[" + type + ", default=" + defaultValue
                + ", priority=" + priority + "]";
    }
}
