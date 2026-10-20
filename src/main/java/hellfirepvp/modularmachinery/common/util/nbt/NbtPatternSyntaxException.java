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
package hellfirepvp.modularmachinery.common.util.nbt;

/**
 * A pattern text that cannot be parsed.
 *
 * <h2>Why this is thrown rather than returned as null</h2>
 *
 * <p>The recipe loader's contract is to record a problem against the FILE and continue (PLAN sections 311, 313). A null return
 * would force every caller to invent its own message, and would lose the offset -- which is the one piece of information that
 * makes a malformed pattern actionable. An exception carries both, and the loader's existing catch-then-report shape handles it.
 *
 * <p>It extends {@link RuntimeException} for the same reason {@link RequirementParseException} does: an implementation throwing
 * it from inside a lambda must not be forced to declare it, and the loader's catch is unconditional.
 */
public class NbtPatternSyntaxException extends RuntimeException {

    public NbtPatternSyntaxException(String message) {
        super(message);
    }
}
