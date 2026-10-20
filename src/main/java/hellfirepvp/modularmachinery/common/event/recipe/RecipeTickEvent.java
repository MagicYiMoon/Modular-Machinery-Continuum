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
 * Fired around each tick of a running recipe (upstream RecipeTickEvent, 83 lines).
 *
 * <p><b>THIS IS THE CLASS THAT WAS HELD BACK LONGEST, AND THE REASON IS WORTH KEEPING (PLAN 505.2, 542).</b> Its
 * upstream postEvent override IS its behaviour: preventProgressing rewinds the recipe own tick counter so the craft
 * does not advance, and destructRecipe tears the running recipe down. A port that kept the FLAGS and dropped the
 * OVERRIDE would produce an event where a handler sets a flag and NOTHING HAPPENS -- the scaffolding-without-behaviour
 * pattern section 420 rejected.</p>
 *
 * <p>THE TWO THINGS THE OVERRIDE NEEDED TURNED OUT TO BE ALREADY HERE, WHICH IS WHY IT COULD FINALLY BE WRITTEN:</p>
 *
 * <pre>
 * upstream                                        this port
 * ActiveMachineRecipe.getTick()/setTick()          TileController.getRecipeTicks()/rewindRecipeTick()
 *   called in 505 as "five absent types"             the read existed; the rewind was added at D7b, and the
 *                                                    unconditional advanceRecipe() one line away gives
 *                                                    subtracting one EXACTLY the net effect upstream has
 * recipeThread.setStatus(CraftingStatus.working(..))  NOT NEEDED: the reason string is stored on the event and
 *                                                    read by the caller, and this port own diagnostics already
 *                                                    name why a craft is blocked (describeBlockers, 521.2)
 * </pre>
 *
 * <p>SO THE STATUS CLASS IS NOT REQUIRED, and only the counter write was missing. THAT IS WHAT TURNING A NAME SEARCH
 * INTO A DEPENDENCY TRACE DID (542): a five-type blocker became a three-line addition.</p>
 *
 * <p>AND ONE BEHAVIOURAL DIFFERENCE IS STATED RATHER THAN IMPLIED: upstream tears down through
 * recipeThread.setActiveRecipe(null).setContext(null).getSemiPermanentModifiers().clear(). THIS PORT HAS NO THREAD,
 * NO CONTEXT ON A THREAD, AND NO MODIFIER LIST TO CLEAR, so destruction here is controller.abandonRecipe() -- the
 * same operation the port own failure path uses. WHAT A HANDLER OBSERVES IS THE SAME (the craft is dropped); what it
 * CANNOT DO is clear state that does not exist.</p>
 */
@com.blamejared.crafttweaker.api.annotation.ZenRegister
@org.openzen.zencode.java.ZenCodeType.Name("mods.modularmachinery.RecipeTickEvent")
public class RecipeTickEvent extends RecipeEvent {

    public final Phase phase;
    private boolean isFailure = false;
    private boolean destructRecipe = false;
    private boolean preventProgressing = false;
    private String failureReason = null;

    public RecipeTickEvent(TileController controller, RecipeCraftingContext context, Phase phase) {
        super(controller, context);
        this.phase = phase;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("phase")
    public Phase getRecipePhase() { return phase; }

    @Override
    public void postEvent() {
        super.postEvent();

        if (preventProgressing) {
            // Upstream: if (activeRecipe.getTick() > 0) activeRecipe.setTick(getTick() - 1);
            // The floor guard lives in rewindRecipeTick, carried over from upstream rather than reinvented.
            controller.rewindRecipeTick();
            return;
        }

        if (isFailure && destructRecipe) {
            // Upstream clears the thread state here; this port has none to clear, so the craft is simply dropped --
            // the same operation the port own failure path performs.
            controller.abandonRecipe();
        }
    }

    /** Holds the craft back one tick. Sets the cancel flag as well, matching upstream U57. */
    @org.openzen.zencode.java.ZenCodeType.Method
    public void preventProgressing(String reason) {
        this.preventProgressing = true;
        this.failureReason = reason;
        setCanceled(true);
    }

    /** Fails the craft, optionally destroying it. Sets the cancel flag as well, matching upstream U65. */
    @org.openzen.zencode.java.ZenCodeType.Method
    public void setFailed(boolean destructRecipe, String reason) {
        this.isFailure = true;
        this.destructRecipe = destructRecipe;
        this.failureReason = reason;
        setCanceled(true);
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("failure")
    public boolean isFailure() {
        return isFailure;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("preventProgressing")
    public boolean isPreventProgressing() {
        return preventProgressing;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("destructRecipe")
    public boolean isDestructRecipe() {
        return destructRecipe;
    }

    @org.openzen.zencode.java.ZenCodeType.Getter("failureReason")
    public String getFailureReason() {
        return failureReason;
    }
}
