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
 * Capacity defaults follow the user's 2026-10-20 balance settings, while transfer
 * defaults retain the source values. Both remain configurable per tier.
 */
public enum EnergyHatchData implements StringRepresentable {
    TINY(1_000, 128),
    SMALL(10_000, 512),
    NORMAL(100_000, 512),
    REINFORCED(1_000_000, 2_048),
    BIG(10_000_000, 8_192),
    HUGE(100_000_000, 32_768),
    LUDICROUS(1_000_000_000, 131_072),
    ULTIMATE(Integer.MAX_VALUE, 131_072);

    private final int defaultMaxEnergy;
    private final int defaultMaxTransfer;

    EnergyHatchData(int maxEnergy, int maxTransfer) {
        this.defaultMaxEnergy = maxEnergy;
        this.defaultMaxTransfer = maxTransfer;
    }

    /** @return the Continuum default buffer for this tier, before configuration overrides */
    public int getDefaultMaxEnergy() {
        return defaultMaxEnergy;
    }

    /** @return the source default transfer limit for this tier */
    public int getDefaultMaxTransfer() {
        return defaultMaxTransfer;
    }

    /** Configured FE capacity, bounded by Forge Energy's signed 32-bit interface. */
    public int getMaxEnergy() {
        return hellfirepvp.modularmachinery.common.data.Config.energyHatchCapacity(this);
    }

    /** Configured transfer limit, independent of buffer capacity. */
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
