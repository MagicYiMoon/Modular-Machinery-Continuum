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
 * Upgrade bus tiers: how many machine upgrades a bus of this tier can hold.
 *
 * <h2>Why this is a separate enum from the parallel controller's</h2>
 *
 * <p>Both blocks are keyed on a {@code type=} property with the <b>same five constant names</b>
 * ({@code normal} … {@code ultimate}), which made it look like one enum could serve both — and this port
 * did exactly that, with a single invented {@code MachineComponentTier}. <b>That was wrong.</b> The jar has
 * one data enum per component:
 *
 * <pre>
 *   UpgradeBusData   ParallelControllerData   EnergyHatchData
 *   FluidHatchSize   ItemBusSize              SmartInterfaceTypeEnum
 * </pre>
 *
 * <p>The names coincide; the <b>numbers do not</b>. Upgrade slots run 3/6/9/12/18 while parallelism runs
 * 4/16/64/256/512, so one shared enum forced one of the two components to be wrong — and both were, since
 * the shared value was 1/2/4/8/16.
 *
 * <h2>The values are read, not derived</h2>
 *
 * <p>Taken from the jar's static initialiser via {@code javap -c}. They are <b>not</b> a formula: there is
 * no clean geometric series here, and the earlier attempt to impose one is precisely what produced the
 * wrong numbers. Do not "tidy" them.
 */
public enum UpgradeBusData implements StringRepresentable, TierTipValue {

    NORMAL(3),
    REINFORCED(6),
    ELITE(9),
    SUPER(12),
    ULTIMATE(18);

    /**
     * The jar's own value for this tier: the DEFAULT, and the fallback while the config is not loaded.
     *
     * <p>Upstream's field is NON-FINAL and {@code UpgradeBusData.loadFromConfig} (U23-U29) overwrites it from
     * {@code "upgrade-bus.<tier>.max-upgrade_slot"} at startup -- note the underscore, it is upstream's. The
     * port held the default AS the answer, so the slot counts the tooltip, the tile and the menu all report
     * could not be changed by a server owner (audit row 40).
     */
    private final int defaultUpgradeSlots;

    UpgradeBusData(int upgradeSlots) {
        this.defaultUpgradeSlots = upgradeSlots;
    }

    /** @return the jar's own default for this tier, i.e. what the tier table declares */
    public int getDefaultUpgradeSlots() {
        return defaultUpgradeSlots;
    }

    /**
     * @return how many upgrade items a bus of this tier holds, <b>as configured</b>
     *
     * <p><b>THIS IS THE ONE PRODUCER OF THE SLOT COUNT, AND THAT IS WHY THE READ MOVED HERE RATHER THAN INTO THE
     * TILE.</b> {@code TileUpgradeBus.getSlots()} and {@code MenuUpgradeBus}'s grid both ask this method, and the
     * tile reconciles its {@code ItemStackHandler} to the answer, so a configured count reaches the menu, the
     * capability a hopper uses, and the tooltip from one place. Assigning the config into a field on the tile
     * would have introduced a second, cached copy that a config reload could not reach -- precisely the
     * "two readers of one rule disagreeing" shape defect B was reported as.</p>
     */
    public int getUpgradeSlots() {
        return hellfirepvp.modularmachinery.common.data.Config.upgradeBusSlots(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The upgrade slot count, which is the number upstream's {@code tile.modularmachinery.blockupgradebus
     * .tip} line reports ({@code data.getMaxUpgradeSlot()} at {@code BlockUpgradeBus} L53). One getter, not a
     * second field: the tooltip cannot report a different figure from the one the menu allocates.</p>
     */
    @Override
    public int getTipValue() {
        return getUpgradeSlots();
    }

    /**
     * The tier whose grid holds exactly {@code slotCount} slots.
     *
     * <p><b>IT HAS NO CALLER ANY MORE, AND THAT IS THE FIX RATHER THAN AN OVERSIGHT (defect B).</b> Its one
     * caller was {@code MenuUpgradeBus.forBus}, which used it to recover the tier from the HANDLER's slot count
     * (PLAN 275's shape, and the right one for {@code ItemBusSize} — see {@link #getUpgradeSlots()}). On this
     * family that direction is wrong: Forge's {@code ItemStackHandler.deserializeNBT} resizes a handler from
     * the SAVED {@code Size} (L188), so the handler's size is not a function of the tier, and a bus restored
     * from such a world exposed a grid the tier never declared — "升级总线默认全栏位可用，没有按说明给栏位". The
     * menu now takes the tier from the BLOCK ({@code TileUpgradeBus.getTier()}) and the tile reconciles its
     * inventory to it, so {@code handler -> tier} is no longer asked of anything. Kept, and kept public,
     * because the resolution itself is correct and a future caller whose only input really is a slot count
     * (another mod's handler, a saved inventory) still needs it; it must NOT be used to size this family's
     * menu again.</p>
     *
     * @param slotCount the slot count of a handler
     * @return the matching tier, or {@link #NORMAL} when no tier has that count
     */
    public static UpgradeBusData forSlotCount(int slotCount) {
        for (UpgradeBusData tier : values()) {
            if (tier.getUpgradeSlots() == slotCount) {
                return tier;
            }
        }
        return NORMAL;
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Creates the blockstate property for this variant.
     *
     * <p>The property is named {@code type} and the constants serialise as {@code normal} … {@code ultimate},
     * both unchanged, so existing blockstate JSON and language keys keep working across this split.
     *
     * <p><b>A factory, not a {@code public static final} field</b>, deliberately: a block class reads this
     * while its own static initialiser runs, and a field here could still be null at that point, killing
     * registration with {@code Cannot invoke "Property.getName()" because "p_61100_" is null}.
     */
    public static EnumProperty<UpgradeBusData> createProperty() {
        return EnumProperty.create("type", UpgradeBusData.class);
    }
}
