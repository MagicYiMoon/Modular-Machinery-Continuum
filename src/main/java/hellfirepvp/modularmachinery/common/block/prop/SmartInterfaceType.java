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
 * The two data kinds a smart interface can carry.
 *
 * <p>Serialised names match {@code blocksmartinterface.json} ({@code type=number}).
 * Upstream selected this with block metadata and a per-metadata value class; on 1.20.1 it is an
 * ordinary blockstate property, and the "which value class" question is answered by this enum rather
 * than by a metadata lookup.
 */
public enum SmartInterfaceType implements StringRepresentable {

    // NUMBER and STRING -- two constants, even though the jar's SmartInterfaceTypeEnum has ONE.
    //
    // This is a 1.12.2 -> 1.20.1 API constraint, not an invention. Upstream selected the value class with
    // block METADATA, where a single used entry was fine. On 1.20.1 the type is an EnumProperty, and
    // Minecraft REQUIRES a blockstate property to have at least two possible values. Removing STRING makes
    // the property single-valued and the block fails to register:
    //
    //   Block{minecraft:air} attempted use property type with <= 1 possible values
    //   Caused by: NullPointerException: Registry Object not present: modularmachinery:blocksmartinterface
    //
    // So the second constant is REQUIRED to express a one-type block in this API. The faithful reading of
    // upstream's one-constant enum on 1.20.1 is not "one constant" -- it is "this block has one type", which
    // 1.20.1 cannot express as a property at all. The alternatives are a plain block with no property
    // (a larger refactor of BlockSmartInterface/TileSmartInterface) or this pair.
    NUMBER,

    /**
     * A filler constant, not a second type.
     *
     * <p>Evidence, gathered in §93-§94: it is referenced from Java <b>nowhere</b> (only {@code NUMBER} is,
     * once, as the default state); and its block model originally pointed at
     * {@code overlay_smartinterface_string}, a texture <b>upstream never shipped</b> — the assets contain
     * {@code overlay_smartinterface_number} and nothing else. So it was never a rendered variant.
     *
     * <p>It exists because 1.20.1's {@code EnumProperty} requires at least two values (§79.4): upstream chose
     * the smart interface's value class from block metadata, where one used entry is fine, and a single-valued
     * property fails registration outright.
     *
     * <p>Its model now reuses the {@code number} overlay, so no variant renders missing art.
     */
    STRING;

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** See {@link ItemBusSize#createProperty()} for why this is a factory, not a static field. */
    public static EnumProperty<SmartInterfaceType> createProperty() {
        return EnumProperty.create("type", SmartInterfaceType.class);
    }
}
