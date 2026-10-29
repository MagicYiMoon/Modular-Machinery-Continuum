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
