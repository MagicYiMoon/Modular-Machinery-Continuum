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

/**
 * One smart interface TYPE a machine DECLARES — its name, its default value, and the texts the GUI,
 * the failure line and JEI show for it.
 *
 * <p><b>Not to be confused with {@link hellfirepvp.modularmachinery.common.block.prop.SmartInterfaceType},
 * which is a different class with the same simple name in a different package.</b> That one is the
 * BLOCK's variant property (upstream's {@code SmartInterfaceTypeEnum}: number/string); this one is
 * upstream's {@code common.util.SmartInterfaceType}, a per-machine data descriptor. Upstream has both
 * under the same simple name too, and the two are unrelated in both codebases.</p>
 *
 * <h2>Why this class is the missing half of the smart interface</h2>
 *
 * <p>Upstream's {@code TileSmartInterface} stores {@code SmartInterfaceData} entries whose TYPE is a
 * plain {@code String}, and whose VALUE is a float. Nothing in that tile knows what the type MEANS —
 * the meaning lives here, in the machine definition, and the machine is what hands the interface its
 * type and its initial value at formation time:
 *
 * <pre>
 * TileMultiblockMachineController U922 / U927
 *     smartInterface.addMachineData(getPos(), foundMachine.getRegistryName(),
 *                                  type.getType(), type.getDefaultValue(), true);
 * </pre>
 *
 * <p>So a port that skipped this class could not publish a single value: there would be no declared
 * types to publish, no default to publish, and {@code RequirementInterfaceNumInput}'s constructor
 * (upstream U28-U33 and U35-U40) would have nothing to take. It is listed here as a prerequisite of the
 * requirement, not as a display helper.</p>
 *
 * <h2>RULE A0 — what is kept and what is dropped</h2>
 *
 * <p><b>KEPT VERBATIM IN SHAPE AND NAME:</b> every field and every getter of upstream's
 * {@code common/util/SmartInterfaceType.java} (134 lines), including {@link #compareTo}, which is a
 * DESCENDING priority comparison ({@code another.priority - priority}, upstream U131-U133) and is what
 * decides which type a new interface receives when a machine declares more types than the structure has
 * interfaces.</p>
 *
 * <p><b>DROPPED, WITH THE REASON:</b> the CraftTweaker surface only — {@code @ZenRegister},
 * {@code @ZenClass("mods.modularmachinery.SmartInterfaceType")}, the {@code @ZenGetter}/{@code @ZenMethod}
 * annotations, and the static {@code create(String, float)} factory that exists solely to be called from
 * ZenScript. Upstream's constructors are public, so dropping the script factory costs the class nothing:
 * the one caller that made it exist does not exist in this port (PLAN 488.4 keeps a soft dependency out
 * of a core class, and PLAN 659.2(a) records that machine definitions reach this port through JSON, not
 * through a script API).</p>
 *
 * <p><b>AND THE FIELDS ARE NOT DEAD WEIGHT — each has a reader in this port:</b></p>
 *
 * <pre>
 * type              the key a bound datum is published and looked up under, and what a recipe's
 *                   interface requirement names                        (TileSmartInterface, requirement)
 * defaultValue      the value an interface is bound with at formation  (MachineFormation)
 * priority          an interface's type when types outnumber interfaces (MachineFormation)
 * notEqualMessage   the failure line when a value is out of range      (RequirementInterfaceNumInput)
 * jeiTooltip/args   the JEI page's value line                          (TooltipInterfaceNumberInput)
 * header/value/footer info  the smart interface screen's four lines     (ScreenSmartInterface)
 * </pre>
 *
 * <p>The three GUI texts are routed through {@code Component.translatable} exactly as upstream routes
 * them through {@code I18n.format} (upstream U83 / U88-U90 / U98), so an empty string still means
 * "use the default text" and a pack override still works.</p>
 */
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
