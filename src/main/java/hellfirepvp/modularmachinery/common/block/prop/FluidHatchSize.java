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
 * Fluid hatch capacity tiers.
 *
 * <p>Serialised names match {@code blockfluidinputhatch.json} / {@code blockfluidoutputhatch.json}
 * ({@code size=tiny} … {@code size=vacuum}). Note the tier ladder differs from the item buses: the
 * top tier is {@code vacuum}, not {@code ludicrous}. Capacities are in millibuckets (mB), the unit
 * 1.20.1 fluid handlers use, unchanged from 1.12.2.
 */
public enum FluidHatchSize implements StringRepresentable, TierTipValue {

    // Values read out of the jar's FluidHatchSize static initialiser (javap -c), NOT derived.
    //
    // These were previously 1_000 / 4_000 / 16_000 / ... -- a clean x4 geometric series that looked like
    // what a tier list ought to be and was entirely invented. Upstream's scale is IRREGULAR
    // (x4, x2.5, x2, x2.25, x2, x2, x2), and the error grew with tier: TINY was 10x too large and VACUUM
    // 512x, holding 16384 buckets where the mod holds 32.
    //
    // A geometric series is exactly the kind of value that pattern-matches intuition, which is why it
    // survived every test: self-consistency cannot detect a wrong constant. Do not "tidy" these numbers.
    TINY(100),
    SMALL(400),
    NORMAL(1_000),
    REINFORCED(2_000),
    BIG(4_500),
    HUGE(8_000),
    LUDICROUS(16_000),
    VACUUM(32_000);

    /**
     * The jar's own value for this tier: the DEFAULT, and the fallback while the config is not loaded.
     *
     * <p>This is deliberately no longer the answer {@link #getCapacity()} gives. Upstream's field is
     * NON-FINAL and {@code FluidHatchSize.loadFromConfig} (U43-U47) overwrites it from
     * {@code "fluidhatch.<TIER>.size"} at startup, so the constructor value is upstream's fallback too. The port
     * could not do that -- a {@code final} field cannot be overwritten -- and held the default AS the answer,
     * which is why a server owner could not change a tank's size (audit row 40).
     */
    private final int defaultCapacity;

    FluidHatchSize(int capacity) {
        this.defaultCapacity = capacity;
    }

    /** @return the jar's own default for this tier, i.e. what the tier table declares */
    public int getDefaultCapacity() {
        return defaultCapacity;
    }

    /**
     * Tank capacity in mB, <b>as configured</b>.
     *
     * <p>The read happens here rather than into a field at startup, so the number this reports is the number in the
     * config file: upstream's own outcome, reached through 1.20.1's config API. See
     * {@link hellfirepvp.modularmachinery.common.data.Config} for the mechanism and for the one respect in which
     * this is more than upstream did (a reload is picked up without a restart).
     */
    public int getCapacity() {
        return hellfirepvp.modularmachinery.common.data.Config.fluidHatchCapacity(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The tank capacity, which is this family's one number and the one upstream's
     * {@code tooltip.fluidhatch.tank.info} line ("Can hold %s mb") reports.</p>
     */
    @Override
    public int getTipValue() {
        return getCapacity();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** See {@link ItemBusSize#createProperty()} for why this is a factory rather than a static field. */
    public static EnumProperty<FluidHatchSize> createProperty() {
        return EnumProperty.create("size", FluidHatchSize.class);
    }
}
