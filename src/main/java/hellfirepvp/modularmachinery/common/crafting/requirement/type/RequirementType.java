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

/**
 * One kind of recipe requirement, and the extension point a pack or an addon uses to add its own.
 *
 * <h2>Why this exists: three cases became a switch, and a switch cannot be extended</h2>
 *
 * <p>Upstream has this class. The port did not -- PLAN section 374 established that, and the proof was the port's own
 * behaviour rather than a diff: {@code RecipeLoader} kept a list called {@code unknownRequirementTypes}, collected the
 * types it could not load, and reported them by name. <b>A loader that reports the types it cannot load KNOWS there are
 * more of them</b>, and the honest reading of that list is "the author implemented the reporting instead of the way to
 * implement the types".
 *
 * <h2>And why this is NOT upstream's shape, which is a deliberate departure</h2>
 *
 * <p>Upstream's version is an abstract class extending {@code IForgeRegistryEntry.Impl}, and its single method returns
 * {@code ComponentRequirement}. Neither is usable here, and both for reasons that are not preference:
 *
 * <ul>
 *   <li>{@code IForgeRegistryEntry} does not exist on 1.20.1 -- Forge's registries are built from {@code DeferredRegister}
 *       or plain maps now, so "an abstract class extending {@code IForgeRegistryEntry.Impl}" is not even expressible.</li>
 *   <li>{@code ComponentRequirement} does not exist in this port. PLAN section 353.3 measured its absence along with
 *       {@code ActiveMachineRecipe}/{@code RecipeRegistry}/{@code MachineComponent}, and the port's
 *       {@code RecipeCraftingContext} is 177 lines against upstream's 826. <b>Requiring it would put the extension point
 *       behind the largest piece of unfinished engine work in the port.</b></li>
 * </ul>
 *
 * <p>So this returns THE PORT'S OWN {@link Requirement}, which the three built-in requirement kinds already implement.
 * The extension point costs what it should cost, and the type erasure upstream carries is not needed for that.
 *
 * <h2>The contract, and the one thing a caller must know</h2>
 *
 * <p>{@link #createRequirement} returning {@code null} means <b>reject the recipe</b>, not "skip this requirement". That is
 * the loader's existing rule (PLAN section 313: a null requirement aborts the recipe), and a type that returns null on a
 * malformed entry is doing the right thing.
 */
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
