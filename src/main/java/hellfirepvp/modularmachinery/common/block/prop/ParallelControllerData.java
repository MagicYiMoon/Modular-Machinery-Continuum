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
 * Parallel controller tiers: the maximum number of recipe copies a controller of this tier may run at once.
 *
 * <h2>Why this is a separate enum from the upgrade bus's</h2>
 *
 * <p>See {@link UpgradeBusData}. Both blocks use a {@code type=} property with the same five constant
 * names, and this port previously served both from one invented enum. The names coincide; the numbers do
 * not — parallelism runs <b>4/16/64/256/512</b> against the upgrade bus's 3/6/9/12/18 — so a shared enum
 * guaranteed at least one of them was wrong. Both were.
 *
 * <h2>Magnitude</h2>
 *
 * <p>ULTIMATE is <b>512</b>. The shared enum gave 16, so an ultimate parallel controller was running at
 * <b>1/32 of its intended throughput</b>. That is a large gameplay difference, not a rounding one, and it
 * is why these constants are transcribed rather than reasoned about.
 *
 * <p>Values read from the jar's static initialiser via {@code javap -c}.
 */
public enum ParallelControllerData implements StringRepresentable, TierTipValue {

    NORMAL(4),
    REINFORCED(16),
    ELITE(64),
    SUPER(256),
    ULTIMATE(512);

    /**
     * The jar's own value for this tier: the DEFAULT, and the fallback while the config is not loaded.
     *
     * <p>Upstream's field is NON-FINAL and {@code ParallelControllerData.loadFromConfig} (U23-U29) overwrites it
     * from {@code "parallel-controller.<tier>.max-parallelism"} at startup. The port held the default AS the
     * answer in a {@code final} field, so a server owner could not raise or lower a controller's ceiling -- the
     * same defect as the other three tier families (audit row 40).
     */
    private final int defaultMaxParallelism;

    ParallelControllerData(int maxParallelism) {
        this.defaultMaxParallelism = maxParallelism;
    }

    /** @return the jar's own default for this tier, i.e. what the tier table declares */
    public int getDefaultMaxParallelism() {
        return defaultMaxParallelism;
    }

    /**
     * The largest number of simultaneous recipe copies this tier permits, <b>as configured</b>.
     *
     * <p>Read at the point of use from {@code "parallel-controller.<tier>.max-parallelism"}, which is upstream's
     * own key (U25). Note the CASE: this family's key uses the lowercased constant name, unlike the two hatch
     * families, which uppercase it -- the difference is upstream's and it decides which key is read.
     */
    public int getMaxParallelism() {
        return hellfirepvp.modularmachinery.common.data.Config.parallelControllerMaxParallelism(this);
    }

    /**
     * {@inheritDoc}
     *
     * <p>The parallelism, which is the number upstream's {@code tile.modularmachinery.blockparallelcontroller
     * .tip} line reports ({@code data.getMaxParallelism()} at {@code BlockParallelController} L50). One
     * getter, not a second field: the tooltip cannot report a different figure from the one the controller
     * runs at.</p>
     */
    @Override
    public int getTipValue() {
        return getMaxParallelism();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Creates the blockstate property for this variant.
     *
     * <p>Named {@code type} with the same serialised constants as before, so blockstate JSON and language
     * keys continue to resolve across the split.
     */
    public static EnumProperty<ParallelControllerData> createProperty() {
        return EnumProperty.create("type", ParallelControllerData.class);
    }
}
