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
 * Upstream attribution is retained as GPL-3.0 requires. The full licence text is in the
 * LICENSE file at the repository root.
 ******************************************************************************/
package hellfirepvp.modularmachinery.common.event.recipe;

import hellfirepvp.modularmachinery.common.crafting.helper.RecipeCraftingContext;
import hellfirepvp.modularmachinery.common.event.machine.Phase;
import hellfirepvp.modularmachinery.common.tiles.TileController;

/**
 * Fired around the recipe start check (upstream RecipeCheckEvent, 43 lines).
 *
 * <p>Upstream posts it twice: TileMultiblockMachineController U1022 with Phase.START and U1039 with Phase.END, then
 * READS THE RESULT BACK -- U1042 checks isFailure() and U1044 turns the reason into a crafting failure. THE EVENT IS
 * THEREFORE NOT MERELY OBSERVATIONAL: A HANDLER CAN REFUSE A CRAFT, and setFailed is how.</p>
 *
 * <p>setFailed SETS THE CANCEL FLAG AS WELL AS THE FAILURE FLAG (upstream U27). That pairing is load bearing: the
 * cancellation is what stops the remaining handlers, and the failure flag is what the machine reads afterwards. A
 * port that set only one of the two would either keep running handlers after a refusal, or fail without stopping.</p>
 *
 * <p><b>setParallelism IS NOT PORTED YET, AND THAT IS A DOCUMENTED GAP RATHER THAN AN OMISSION (PLAN 501.2, D1).</b>
 * Upstream clamps against ActiveMachineRecipe.getParallelism() -- the ceiling the RECIPE declares -- and that class
 * does not exist here. The port parallel controller DOES have a clamp (TileParallelController T65), but its ceiling
 * comes from a different source, so reproducing the method now would either reference a missing getter or silently
 * clamp against a different quantity. Adding it would make the port LOOK complete while behaving differently.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.RecipeCheckEvent")
public class RecipeCheckEvent extends RecipeEvent {

    public final Phase phase;
    private boolean isFailure = false;
    private String failureReason = null;

    public RecipeCheckEvent(TileController controller, RecipeCraftingContext context, Phase phase) {
        super(controller, context);
        this.phase = phase;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("phase")
    public Phase getRecipePhase() { return phase; }

    /** Refuses the craft. Sets the cancel flag too, exactly as upstream does. */
    @org.openzen.zencode.java.ZenCodeType.Method
    public void setFailed(String reason) {
        this.isFailure = true;
        this.failureReason = reason;
        setCanceled(true);
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("failure")
    public boolean isFailure() {
        return isFailure;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("failureReason")
    public String getFailureReason() {
        return failureReason;
    }
}
