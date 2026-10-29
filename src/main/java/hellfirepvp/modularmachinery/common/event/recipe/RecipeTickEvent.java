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
 * Brackets an ordinary recipe tick. PRE runs before continuous IO; edits to the live thread's
 * modifiers are consumed by that tick. Event context/progress observations retain their original values.
 * preventProgressing follows the source one-tick rewind; setFailed may destroy the craft and its
 * semi-permanent modifiers. Permanent thread and controller modifiers survive recipe completion.
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
            // Dropping the craft also clears its semi-permanent thread modifiers.
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
