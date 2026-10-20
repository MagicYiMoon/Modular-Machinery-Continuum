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
package hellfirepvp.modularmachinery.common.block.prop;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Locale;

/**
 * Item bus capacity tiers.
 *
 * <p>1.12.2 carried these through block metadata ({@code BlockVariants#getIdentifierForMeta}) and
 * per-metadata slot counts. On 1.20.1 the tier is a first-class blockstate property, and the
 * serialised names match the keys already present in {@code blockinputbus.json} / {@code blockoutputbus.json}
 * ({@code size=tiny} … {@code size=ludicrous}), so no asset change is needed.
 *
 * <h2>CORRECTION: this enum was wrong for thirty rounds</h2>
 *
 * <p>It originally modelled a bus as an <b>input/output row pair</b> — {@code TINY(1,1)}, {@code
 * NORMAL(3,3)} — and computed {@code getSlots()} as {@code max(rows) * 9}. Upstream has no such model:
 * each tier is a <b>flat slot count with its own grid geometry</b>, taken from
 * {@code ContainerItemBus#addInventorySlots} in the reference branch:
 *
 * <pre>
 *   TINY(1), SMALL(4), NORMAL(6), REINFORCED(9), BIG(12), HUGE(16), LUDICROUS(32)
 * </pre>
 *
 * <p>So a NORMAL bus held <b>27</b> stacks here where upstream holds <b>6</b> — a gameplay and balance
 * error, not a cosmetic one, and it went unnoticed because every test only ever asserted that the
 * <i>same</i> wrong number appeared on both sides of the menu. It was found by opening the GUI in a real
 * client and noticing the bus looked nothing like the original.
 *
 * <p>The geometry is carried here rather than in the menu because the <b>GUI texture</b> is authored
 * against it: {@code textures/gui/inventory_<tier>.png} draws each tier's frame at these exact
 * positions, so slots and frame have to come from one source or they will not line up.
 */
public enum ItemBusSize implements StringRepresentable, TierTipValue {

    //                 slots cols rows  originX originY
    TINY(1, 1, 1, 81, 30),
    SMALL(4, 2, 2, 70, 18),
    NORMAL(6, 3, 2, 61, 18),
    REINFORCED(9, 3, 3, 61, 13),
    BIG(12, 4, 3, 52, 18),
    HUGE(16, 4, 4, 53, 8),
    LUDICROUS(32, 8, 4, 17, 8);

    private final int slots;
    private final int columns;
    private final int rows;
    private final int originX;
    private final int originY;

    ItemBusSize(int slots, int columns, int rows, int originX, int originY) {
        this.slots = slots;
        this.columns = columns;
        this.rows = rows;
        this.originX = originX;
        this.originY = originY;
    }

    /** @return how many stacks this tier holds */
    public int getSlots() {
        return slots;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The slot count, which is this family's one number and the one upstream's
     * {@code tooltip.itembus.slot}/{@code tooltip.itembus.slots} line reports. Upstream spells the getter
     * {@code getSlotCount()}; this port uses {@code getSlots()}, and this method reads that field rather than
     * re-exporting it under a second name.</p>
     */
    @Override
    public int getTipValue() {
        return slots;
    }

    /**
     * The tier whose grid holds exactly {@code slotCount} slots.
     *
     * <p><b>Why the menu needs this.</b> {@code MenuItemBus.forBus} used to read the tier from the blockstate
     * and the handler from the tile -- two reads of one tile that CAN disagree, and did: the crash was
     * {@code Slot 6 not in valid range - [0,6)}, a menu built for a larger tier over a handler that had six
     * slots (PLAN section 274).
     *
     * <p>The menu needs the TIER rather than the count, because the tier carries the slot GEOMETRY and the count
     * alone would put the right number of slots in the wrong places. So the tier is recovered from the count,
     * which is the one quantity both sides already agree on, and geometry and count then come from a single
     * source.
     *
     * <p>The counts are distinct (1, 4, 6, 9, 12, 16, 32), so the lookup is unambiguous.
     *
     * @param slotCount the slot count of the handler the menu will wrap
     * @return the matching tier, or {@link #NORMAL} when no tier has that count
     */
    public static ItemBusSize forSlotCount(int slotCount) {
        for (ItemBusSize size : values()) {
            if (size.getSlots() == slotCount) {
                return size;
            }
        }
        return NORMAL;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    /** @return the GUI x of slot {@code index}, in 176x166 GUI space */
    public int getSlotX(int index) {
        return originX + (index % columns) * 18;
    }

    /** @return the GUI y of slot {@code index}, in 176x166 GUI space */
    public int getSlotY(int index) {
        return originY + (index / columns) * 18;
    }

    /**
     * @return the GUI texture for this tier, matching upstream's
     *         {@code textures/gui/inventory_<size>.png} naming
     */
    public String getTexturePath() {
        return "textures/gui/inventory_" + getSerializedName() + ".png";
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Creates the blockstate property for this variant.
     *
     * <p><b>Deliberately a factory, not a {@code public static final} field.</b> A block class reads
     * this while its own static initialiser runs. If the property lived in a static field of this
     * enum, and the enum's {@code <clinit>} had not completed, the block would receive {@code null}
     * and die at registration with
     * {@code Cannot invoke "Property.getName()" because "p_61100_" is null}. Calling
     * {@code EnumProperty.create} at the point of use removes the initialisation-order coupling
     * entirely. Note the enum constants themselves are always safe to reference, because they are
     * assigned before any other static field of the enum.
     */
    public static EnumProperty<ItemBusSize> createProperty() {
        return EnumProperty.create("size", ItemBusSize.class);
    }
}
