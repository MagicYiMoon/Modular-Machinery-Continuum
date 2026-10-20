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
package hellfirepvp.modularmachinery.common.block;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

import java.util.Locale;

/**
 * The six casing textures upstream selected with a block metadata value.
 *
 * <p>1.12.2 used {@code PropertyEnum<CasingType>} over {@code Block#getMetaFromState}; the metadata
 * concept is gone on 1.20.1 but the property survives essentially unchanged — the enum now implements
 * {@link StringRepresentable} ({@code IStringSerializable} was renamed) and its serialised names are
 * the lowercase constants, which is exactly what the shipped {@code blockcasing.json} already keys on
 * ({@code casing=plain}, {@code casing=vent}, …).
 */
public enum CasingType implements StringRepresentable {

    PLAIN,
    VENT,
    FIREBOX,
    GEARBOX,
    REINFORCED,
    CIRCUITRY;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** See {@link hellfirepvp.modularmachinery.common.block.prop.ItemBusSize#createProperty()}. */
    public static EnumProperty<CasingType> createProperty() {
        return EnumProperty.create("casing", CasingType.class);
    }
}
