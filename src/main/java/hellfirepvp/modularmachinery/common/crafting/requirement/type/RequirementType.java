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
 * Upstream attribution is retained as GPL-3.0 requires.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.crafting.requirement.type;

import com.google.gson.JsonObject;
import hellfirepvp.modularmachinery.common.crafting.Requirement;
import hellfirepvp.modularmachinery.common.machine.IOType;
import org.jetbrains.annotations.Nullable;


































 
public interface RequirementType {

    /**
     * Builds a requirement from the parsed JSON of one entry in a recipe's {@code requirements} array.
     *
     * @param type the io direction, already validated by the loader as REQUIRED
     * @param json the requirement object itself
     * @return the requirement, or {@code null} to reject the whole recipe
     */
    Requirement createRequirement(IOType type, JsonObject json);

    /**
     * The mod id this type needs, or {@code null} when it needs none.
     *
     * <p>Upstream's {@code requiresModid()}, kept rather than dropped because the case it exists for is real in this port:
     * the {@code modularmagic} providers target other mods. Availability depends on a compatible modern API, including
     * the community Astral Sorcery port. A type that names its mod can be registered, resolved by name, and
     * still fail honestly at parse time instead of pretending.
     */
    @Nullable
    default String requiresModid() {
        return null;
    }
}
