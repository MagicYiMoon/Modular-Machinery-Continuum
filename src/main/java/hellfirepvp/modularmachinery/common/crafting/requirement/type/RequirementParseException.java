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

/**
 * Thrown by a {@link RequirementType} when a required key is missing or malformed.
 *
 * <h2>Why this is a class rather than the null return the interface documents</h2>
 *
 * <p>{@link RequirementType#createRequirement} returning {@code null} means "reject this recipe", and for a type with
 * nothing to say that is enough. But the three built-in types had something to say and said it inside
 * {@code RecipeLoader}'s switch -- each branch naming the key it wanted, e.g. <i>"energy requirement has no
 * energyPerTick"</i>.
 *
 * <p>The port cannot keep those messages by returning null: the loader would know only that something failed, not which
 * key. And it cannot keep them by throwing the loader's own exception either, because the relevant class,
 * {@code RecipeLoader.RequiredKeyMissing}, is <b>private and nested</b> -- code outside that class cannot name it, let
 * alone throw it. That is what made "move each switch branch into an implementation" more than a mechanical move.
 *
 * <p>So the mechanism becomes public and lives with the types that need it. <b>The loader catches this and adds the message
 * to its problems list exactly as it did before the switch was removed</b>, which is why migrating a branch preserves its
 * reasoning rather than replacing a specific message with a generic one.
 *
 * <p>It extends {@link RuntimeException} rather than a checked type because a type implementation throwing it from inside
 * a switch expression or a lambda must not be forced to declare it; the loader's catch is unconditional.
 */
public class RequirementParseException extends RuntimeException {

    public RequirementParseException(String message) {
        super(message);
    }

    public RequirementParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
