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
 * Ordinary FE hatch tiers; names preserve the source blockstate variants.
 * Capacity defaults follow the user's 2026-10-20 balance settings. As of 2026-10-28,
 * automatic transfer is 10% of configured capacity, with optional explicit per-tier overrides.
 */
public enum EnergyHatchData implements StringRepresentable {
    TINY(1_000),
    SMALL(10_000),
    NORMAL(100_000),
    REINFORCED(1_000_000),
    BIG(10_000_000),
    HUGE(100_000_000),
    LUDICROUS(1_000_000_000),
    ULTIMATE(Integer.MAX_VALUE);

    private final int defaultMaxEnergy;

    EnergyHatchData(int maxEnergy) {
        this.defaultMaxEnergy = maxEnergy;
    }

    /** @return the Continuum default buffer for this tier, before configuration overrides */
    public int getDefaultMaxEnergy() {
        return defaultMaxEnergy;
    }

    /** @return the default transfer limit at this tier's default capacity */
    public int getDefaultMaxTransfer() {
        return Math.max(1, defaultMaxEnergy / 10);
    }

    /** Configured FE capacity, bounded by Forge Energy's signed 32-bit interface. */
    public int getMaxEnergy() {
        return hellfirepvp.modularmachinery.common.data.Config.energyHatchCapacity(this);
    }

    /** Configured transfer limit, or 10% of configured capacity when the setting is zero. */
    public int getMaxTransfer() {
        return hellfirepvp.modularmachinery.common.data.Config.energyHatchTransfer(this);
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** See ItemBusSize#createProperty for why this is a factory rather than a static field. */
    public static EnumProperty<EnergyHatchData> createProperty() {
        return EnumProperty.create("size", EnergyHatchData.class);
    }
}
