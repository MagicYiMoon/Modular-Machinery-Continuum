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
package hellfirepvp.modularmachinery.common.machine;

/**
 * Input/output direction marker for machine components.
 *
 * <p>Ported from the 1.12.2 enum of the same name. The original exposed
 * {@code getByString(String)} and an {@code IStringSerializable}-style name; neither is needed on
 * 1.20.1, where blockstate values are serialised from the enum constant name by
 * {@code EnumProperty}. {@link #getByString(String)} is kept because the machine JSON deserialiser
 * reads {@code "input"}/{@code "output"} strings and must keep accepting the same spelling.
 */
public enum IOType {

    INPUT,
    OUTPUT;

    /**
     * Case-insensitive lookup used by the JSON loaders.
     *
     * @return the matching constant, or {@code null} if the string names neither (matches the
     *         original behaviour of returning {@code null} rather than throwing)
     */
    public static IOType getByString(String name) {
        if (name == null) {
            return null;
        }
        for (IOType type : values()) {
            if (type.name().equalsIgnoreCase(name)) {
                return type;
            }
        }
        return null;
    }
}
